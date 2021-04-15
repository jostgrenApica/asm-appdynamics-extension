/*
 * To change this template, choose Tools | Templates and open the template in the editor.
 */
package com.apicasystems.integration.appdynamics;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.apache.log4j.Logger;
import org.apache.log4j.Level;
import com.singularity.ee.agent.systemagent.api.AManagedMonitor;
import com.singularity.ee.agent.systemagent.api.MetricWriter;
import com.singularity.ee.agent.systemagent.api.TaskExecutionContext;
import com.singularity.ee.agent.systemagent.api.TaskOutput;
import com.singularity.ee.agent.systemagent.api.exception.TaskExecutionException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.text.ParseException;

public class ApicaMonitor extends AManagedMonitor {
  // When this is true, every call to the API will look at the checks, save the timestamps
  // and either save it to file or mem depending on the variable: useFilebasedDiffCache.
  // Set to false to disable caching entirely.
  private Boolean useTimestampDiffCache = true;
  // If using Periodic execution-style, this could be set to true in order to persist
  // time-stamp comparing to/from files.
  // Continuous execution keeps the program running indef. so then it's better to use
  // in-memory comparing (set this to false)
  private Boolean useFilebasedDiffCache = true;
  // Used when execution-style is expected to be continuous, since that signifies a loop that run
  // every 60th second.
  private Boolean useLoopingProcess = false;
  // Write stuff to StdOut so that debug is easier.
  private Boolean useOutputDebugStuff = false;
  // Metrics will be fetched and put heere.
  private Map<String, Object> metrics;
  private Logger logger;
  // The variable containing timestamnps for when checks had new data lastly.
  // Timestamps comes from the API. This is in order to see what data is new.
  HashMap<Integer, String> checkResultTimeStamps = new HashMap<>();
  // Set from an argument to the program.
  private String authTicket = "";
  // Set from an argument to the program.
  private String baseApiUrl = "";
  // Set from an argument to the program.
  private String tagName = "";
  // Set from an argument to the program.
  private String metricPath = "Custom Metrics|Apica|";
  // Does the work of communicating with Apica's API.
  ApicaCommunicator apicaCommunicator;
  
  /**
   * For testing purpose is Main() executed. To see if connection works, run this java file alone
   * with arguments: AuthTicket, URI.
   *
   * Prints all metric names and their value from one round of REST calls to StdOut.
   */
  public static void main(String[] args) throws ParseException {
    if (args.length < 2 || args.length > 3) {
      System.err.println("2-3 arguments needed: authTicket,baseApiUrl and optionally tagName");
      return;
    }
    String authTicket = args[0];
    String baseApiUrl = args[1];
    String tagName = args.length == 3 ? args[2] : null;
    ApicaMonitor pm = new ApicaMonitor();
    pm.logger = Logger.getLogger(ApicaMonitor.class);
    pm.authTicket = authTicket;
    pm.baseApiUrl = baseApiUrl;
    pm.checkResultTimeStamps = new HashMap<>();
    // Debug Option 1. Start the actual thread that will do the loop for us
    // In this case you probably want to write to stdout, so remove those comments.
    Map<String, String> taskArgs;
    taskArgs = new HashMap<String, String>();
    taskArgs.put("AuthTicket", authTicket);
    taskArgs.put("BaseApiUrl", baseApiUrl);
    if (tagName != null)
      taskArgs.put("TagName", tagName);
    try {
      pm.execute(taskArgs, null);
    } catch (TaskExecutionException ex) {
      pm.logger.fatal("Task exection error", ex);
    }
  }
  
  private void setLogLevel() {
    Map<String, String> env = System.getenv();
    String logLevel = env.get("LOG_LEVEL");
    if (logLevel != null) {
      logLevel = logLevel.toUpperCase();
    } else {
      return;
    }
    if (logLevel.equals("DEBUG")) {
      logger.setLevel(Level.DEBUG);
      this.useOutputDebugStuff = true;
    } else if (logLevel.equals("INFO")) {
      logger.setLevel(Level.INFO);
    } else if (logLevel.equals("TRACE")) {
      logger.setLevel(Level.ALL);
      this.useOutputDebugStuff = true;
    }
  }
  
  private String getSerialFilePath() {
    String property = "java.io.tmpdir";
    String tempDir = System.getProperty(property, "./");
    String fileSeparator = File.separator;
    String pathName = tempDir + fileSeparator + "timpstamps.ser";
    return pathName;
  }
  
  @Override
  public TaskOutput execute(Map<String, String> taskArguments, TaskExecutionContext taskContext)
      throws TaskExecutionException {
    logger = Logger.getLogger(ApicaMonitor.class);
    setLogLevel();
    metrics = new HashMap<String, Object>();
    if (!taskArguments.containsKey("AuthTicket") || !taskArguments.containsKey("BaseApiUrl")) {
      logger.error("monitor.xml must contain task arguments 'AuthTicket' and 'BaseApiUrl'"
          + " Terminating Monitor.");
      return null;
    }
    
    logger.info("Execute Started");
    // setting the custom metric path, if there is one in monitor.xml
    if (taskArguments.containsKey("Metric-Path") && taskArguments.get("Metric-Path") != "") {
      metricPath = taskArguments.get("Metric-Path");
      if (!metricPath.endsWith("|")) {
        metricPath += "|";
      }
    }
    // Try get the diffcache from file
    if (useTimestampDiffCache && useFilebasedDiffCache) {
      checkResultTimeStamps = LoadTimestampDiffsFromFile();
    } else {
    }
    apicaCommunicator =
        new ApicaCommunicator(taskArguments, checkResultTimeStamps, logger);
    if (!useLoopingProcess) {
      if (!useTimestampDiffCache) {
        checkResultTimeStamps.clear();
      }
      apicaCommunicator.populate(metrics);
      DumpToStdOut(metrics);
      writeAllMetrics();
      if (useTimestampDiffCache && useFilebasedDiffCache) {
        SaveTimestampDiffsToFile(checkResultTimeStamps);
      }
      metrics.clear();
    } else {
      // Using a sepaarate thread in a loop:
      while (true) {
        (new PrintMetricsClearHashmapThread()).start();
        try {
          Thread.sleep(60000);
        } catch (InterruptedException e) {
          logger.error("Apica Monitor interrupted. Quitting Apica Monitor: " + e.getMessage());
          return null;
        }
      }
    }
    return null;
  }
  
  /**
   * Write all metrics to the AppDyn Controller.
   */
  private void writeAllMetrics() {
    for (String key : metrics.keySet()) {
      String m = metricPath + key + ":" + String.valueOf(metrics.get(key));
      // logger.debug(m);
      // See:
      // http://docs.appdynamics.com/display/PRO13S/Build+a+Monitoring+Extension+Using+Java#BuildaMonitoringExtensionUsingJava-YourMonitoringExtensionClass
      try {
        MetricWriter metricWriter =
            getMetricWriter(metricPath + key, MetricWriter.METRIC_AGGREGATION_TYPE_OBSERVATION, // Last
                                                                                                // reported
                                                                                                // value
                MetricWriter.METRIC_TIME_ROLLUP_TYPE_AVERAGE, // I changed from Sum to Average.
                MetricWriter.METRIC_CLUSTER_ROLLUP_TYPE_INDIVIDUAL); // No idea what this is
        metricWriter.printMetric(String.valueOf(metrics.get(key)));
      } catch (Exception ex) {
        logger.error(m, ex);
      }
    }
  }
  
  private void SaveTimestampDiffsToFile(HashMap<Integer, String> hashMap) {
    try (OutputStream file = new FileOutputStream(this.getSerialFilePath());
        OutputStream buffer = new BufferedOutputStream(file);
        ObjectOutput output = new ObjectOutputStream(buffer);) {
      output.writeObject(hashMap);
    } catch (IOException ex) {
      logger.error("Cannot serialize object: " + ex.getMessage());
    }
  }
  
  private HashMap<Integer, String> LoadTimestampDiffsFromFile() {
    try (InputStream file = new FileInputStream(this.getSerialFilePath());
        InputStream buffer = new BufferedInputStream(file);
        ObjectInput input = new ObjectInputStream(buffer);) {
      // deserialize the List
      @SuppressWarnings("unchecked")
      HashMap<Integer, String> hashMap = (HashMap<Integer, String>) input.readObject();
      return hashMap;
    } catch (ClassNotFoundException ex) {
      logger.error("Cannot deserialize object. Class not found." + ex.getMessage());
    } catch (IOException ex) {
      logger.error("Cannot deserialize object: " + ex.getMessage());
    }
    return new HashMap<>();
  }
  
  private void DumpToStdOut(Map<String, Object> someMap) {
    if (!useOutputDebugStuff) {
      return;
    }
    String message;
    Map<String, Object> treeMap = new TreeMap<String, Object>(someMap);
    for (String key : treeMap.keySet()) {
      String val = treeMap.get(key).toString();
      message = String.format("%s=%s", key, val);
      System.out.println(message);
      logger.info(message);
    }
    message = "run() completed with: [" + treeMap.size() + "] outputted metrics. ThreadId: "
        + Thread.currentThread().getId();
    System.out.println(message);
    logger.info(message);
  }
  
  private class PrintMetricsClearHashmapThread extends Thread {
    public void run() {
      // The following line nullifies timestamp-diff-cache.
      if (!useTimestampDiffCache) {
        apicaCommunicator.checkResultTimeStamps.clear();
      }
      apicaCommunicator.populate(metrics);
      DumpToStdOut(metrics);
      writeAllMetrics();
      metrics.clear();
    }
  }
}
