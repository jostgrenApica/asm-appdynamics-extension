/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.apicasystems.integration.appdynamics;

import java.util.HashMap;

/**
 *
 * @author rickard.robin
 */
public final class CheckResultHashMapHelper {
    
    

    public static void AddOrUpdate(HashMap<Integer, String> source, Integer number, String text) {
        if (source.containsKey(number)) {
            source.remove(number);
        }
        source.put(number, text);

    }
}
