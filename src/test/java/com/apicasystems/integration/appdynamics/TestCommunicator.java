/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.apicasystems.integration.appdynamics;

import java.io.IOException;
import java.io.InputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ContainerFactory;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import com.apicasystems.integration.appdynamics.ISO8601;

/**
 *
 * @author rickard.robin
 */
public class TestCommunicator {
    private String username;
    private String password;
    private String baseApiUrl;
    private Logger logger;
    
    public TestCommunicator(String username, String password, String baseApiUrl, Logger logger){
        logger.error("RR: TestCommunicor Success!!!");

        this.username = username;
        this.password = password;
        this.baseApiUrl = baseApiUrl;
        this.logger = logger;
    }
    
	public void populate(Map<String, Integer> metrics) {
                logger.error("populate() called.");
		// getCredits(metrics);

		// getChecks also has getLimits, to save time and requests it
		// takes the necessary information from its given header
		getChecks(metrics);

	}

	private void getChecks(Map<String, Integer> metrics){
            logger.error("getChecks() called.");
		try {
			HttpClient httpclient = new DefaultHttpClient();

		} catch (Throwable t) {
			logger.error("getChecks(): Unexpected exception: " + t.getMessage());
                        throw t;
		}
	}

            
    
}
