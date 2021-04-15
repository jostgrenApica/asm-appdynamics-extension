package com.apicasystems.integration.appdynamics;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ContainerFactory;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
// import com.apicasystems.integration.appdynamics.ISO8601;

@SuppressWarnings("deprecation")
public class ApicaCommunicator {
 
  private Logger logger;
  private Map<String, String> taskArguments;
  HashMap<Integer, String> checkResultTimeStamps;
  
  public ApicaCommunicator(Map<String, String> taskArguments,
    HashMap<Integer, String> checkResultTimeStamps, Logger logger) {
    this.taskArguments = taskArguments;
    this.checkResultTimeStamps = checkResultTimeStamps;
   
    this.logger = logger;
  }
  
  public void populate(Map<String, Object> metrics) {
    getChecks(metrics);
  }
  
  @SuppressWarnings("rawtypes")
  private void getChecks(Map<String, Object> metrics) {
    try {
      HttpClient httpclient = getHttpClient();
      String uri = this.taskArguments.get("BaseApiUrl") + "/checks?auth_ticket=" +  this.taskArguments.get("AuthTicket") + "&enabled=true";
      HttpGet httpget = new HttpGet(uri);
      httpget.addHeader("Accept-Charset", "UTF-8");
      HttpResponse response;
      response = httpclient.execute(httpget);
      Integer statusCode = response.getStatusLine().getStatusCode();
      if (statusCode != 200) {
        String message = statusCode.toString() + " : " + response.getStatusLine().getReasonPhrase();
        throw new org.apache.http.HttpException(message);
      }
      String tagName =  this.taskArguments.get("TagName");
      HttpEntity entity = response.getEntity();
      // read http response
      String result = IOUtils.toString(entity.getContent(), "UTF-8");
      // parse to json
      try {
        JSONParser parser = new JSONParser();
        ContainerFactory containerFactory = new ContainerFactory() {
          public List creatArrayContainer() {
            return new LinkedList();
          }
          
          public Map createObjectContainer() {
            return new LinkedHashMap();
          }
        };
        // retrieving the metrics and populating HashMap
        JSONArray array = (JSONArray) parser.parse(result);
        for (Object checkObj : array) {
          JSONObject check = (JSONObject) checkObj;
          Map json = (Map) parser.parse(check.toJSONString(), containerFactory);
          if (!json.containsKey("name") || !json.containsKey("value")
              || !json.containsKey("severity") || !json.containsKey("timestamp_utc")
              || !json.containsKey("id")) {
            continue;
          }
          // CHECK ID
          Integer checkId = Integer.parseInt(json.get("id").toString());
          // LOCATION
          String location = json.get("location") + "";
          location = location.replaceAll(",", ".");
          // TIMESTAMP_UTC
          Object fetchedTimeStamp = json.get("timestamp_utc");
          String previousTimeStamp = checkResultTimeStamps.get(checkId);
          if (fetchedTimeStamp == null || fetchedTimeStamp.toString().equals(previousTimeStamp)) {
            continue;
          }
          // DEBUG: System.out.println("checkId: " + checkId + ", fetchedTimeStamp: " +
          // fetchedTimeStamp +" differed from previous: " + previousTimeStamp);
          String tagValue = null;
          if (tagName != null && json.containsKey("tags")) {
            @SuppressWarnings("unchecked")
            LinkedHashMap<String, LinkedList> tags =
                (LinkedHashMap<String, LinkedList>) json.get("tags");
            if (tags != null) {
              LinkedList values = tags.get(tagName);
              if (values != null && values.size() > 0)
                tagValue = values.get(0).toString();
            }
            if (tagValue == null)
              continue;
          }
          CheckResultHashMapHelper.AddOrUpdate(checkResultTimeStamps, checkId,
              fetchedTimeStamp.toString());
          // NAME.
          // String metricName = "Checks|" + json.get("name") + "_" + globalCounter.toString() +
          // "_|";
          // String metricName = "Checks|" + json.get("name") + " (" + checkId.toString() + ")" +
          // "|";
          String metricName = "Checks|";
          if (tagValue != null)
            metricName += tagValue + "|";
          metricName += json.get("name") + " (" + location + ")" + "|";
          // VALUE
          // Not sure if AppDynamics accept 0 as value. I some times get this in machine-agent.log:
          // "MetricRegistrationException" <execution-output>Received zeros metrics to
          // registration</execution-output>
          Object jsonVal = json.get("value");
          if (jsonVal == null) {
            continue;
          } else {
            metrics.put(metricName + "value", jsonVal);
          }
          //* Calculate SLA fulfillment
          Object target_sla = json.get("target_sla"); 
          if(target_sla != null) {
            Object sla_percent_month = json.get("sla_percent_current_month"); 
            if(sla_percent_month != null)  {
              try {
                long sla_fulfillment=Math.round(Double.parseDouble(sla_percent_month.toString())/Double.parseDouble(target_sla.toString()) *100.0);
                metrics.put(metricName + "sla_fulfillment", new Long(sla_fulfillment));
                
              } catch (Exception ne) {
                ;
              }
            }
            
          }
            
          
          
          // SEVERITY
          String severity = json.get("severity").toString();
          if (severity != null) {
            if (severity.equals("F")) {
              metrics.put(metricName + "status", 0);
            } else if (severity.equals("U")) {
              metrics.put(metricName + "status", 0);
            } else if (severity.equals("E")) {
              metrics.put(metricName + "status", 1);
            } else if (severity.equals("W")) {
              metrics.put(metricName + "status", 2);
            } else if (severity.equals("I")) {
              metrics.put(metricName + "status", 3);
            } else {
              logger.error("getChecks(): Error parsing metric value for " + metricName
                  + "severity: Unknown severity '" + severity + "'");
            }
          } else {
            logger.error("getChecks(): Error parsing metric value for " + metricName + "status");
          }
        }
      } catch (ParseException e) {
        logger.error("getChecks(): JSON Parsing error: " + e.getMessage(), e);
      } catch (Throwable e) {
        logger.error("getChecks(): JSON Unexpected exception: " + e.getMessage(), e);
        e.printStackTrace();
      }
      // parse header in the end to get the Req-Limits
      // Header[] responseHeaders = response.getAllHeaders();
      // getLimits(metrics, responseHeaders);
    } catch (IOException e1) {
      logger.error("getChecks(): IOException: " + e1.getMessage(), e1);
    } catch (Throwable t) {
      logger.error("getChecks(): Unexpected exception: " + t.getMessage(), t);
    }
  }
  
  @SuppressWarnings("deprecation")
  private HttpClient getHttpClient() {
    // HttpClient httpclient = new DefaultHttpClient();
    HttpClientBuilder builder=HttpClientBuilder.create();
    String proxyHost = this.taskArguments.get("ProxyHost");
    String proxyPort = this.taskArguments.get("ProxyPort");
   
    if (proxyHost != null && proxyPort != null) {
      String proxyScheme =this.taskArguments.get("ProxyScheme"); 
      if(proxyScheme == null )
        proxyScheme = HttpHost.DEFAULT_SCHEME_NAME;
      
      int port = Integer.parseInt(proxyPort);
      HttpHost proxy = new HttpHost(proxyHost, port, proxyScheme);
      
      builder=builder.setProxy(proxy);
 
    }
    HttpClient httpclient= builder.build();
    return httpclient;
  }
}
