/*
 * Licensed to Laurent Broudoux (the "Author") under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. Author licenses this
 * file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.github.lbroudoux.microcks.util;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/**
 * This is a helper for extracting and building dispatch criteria from many sources.
 * @author laurent
 */
public class DispatchCriteriaHelper{

   /**
    * Extract a dispatch rule string from URI parameters (specified using example values)
    * @param uri The URI containing parameters
    * @return A string representing dispatch rules for the corresponding incoming request.
    */
   public static String extractParamsFromURI(String uri){
      if (uri.contains("?") && uri.contains("=")){
         String parameters = uri.substring(uri.indexOf("?") + 1);
         StringBuilder params = new StringBuilder();

         for (String parameter : parameters.split("&")){
            String[] pair = parameter.split("=");
            try {
               String key = URLDecoder.decode(pair[0], "UTF-8");
               String value = URLDecoder.decode(pair[1], "UTF-8");
               if (params.length() > 0) {
                  params.append(" && ");
               }
               params.append(key);
            } catch (UnsupportedEncodingException e) {
               e.printStackTrace();
            }
         }
         return params.toString();
      }
      return "";
   }

   /**
    * Extract the common prefix between a set of URIs
    * @param uris A set of URIs that are expected to share a common prefix
    * @return A string representing the common prefix of given URIs
    */
   public static String extractCommonPrefix(List<String> uris){
      String commonURIPath = uris.get(0);

      // 1st pass on collection: find a common prefix.
      for (int prefixLen = 0; prefixLen < uris.get(0).length(); prefixLen++) {
         char c = uris.get(0).charAt(prefixLen);
         for (int i = 1; i < uris.size(); i++) {
            if ( prefixLen >= uris.get(i).length() ||
                  uris.get(i).charAt(prefixLen) != c ) {
               // Mismatch found.
               String commonString = uris.get(i).substring(0, prefixLen);
               commonURIPath = commonString.substring(0, commonString.lastIndexOf('/'));
               break;
            }
         }
      }
      return commonURIPath;
   }

   /**
    * Extract from given URIs a dispatching rule representing the number of variable parts
    * in this different URIs. For example, given 'http://s/r/f//d/m/s' and 'http://s/r/f/d', method
    * will detect 2 variable parts ('m' and 's'). Because it does not anything about the semantics of this
    * parts, it produces a generic dispatch rule 'part1 && part2' telling that URIs can be templatized
    * like 'http://s/r/f/d/{part1}/{part2} and that this 2 parts should be taken into account when
    * disoatching request to response.
    * @param uris A set of URIs that are expected to share a common prefix
    * @return A string representing dispatch rules for the corresponding incoming request.
    */
   public static String extractPartsFromURIs(List<String> uris){
      // 1st pass on collection: find a common prefix.
      String commonURIPath = extractCommonPrefix(uris);

      // 2nd pass on collection: guess the max number of part.
      int partsLen = 0;
      for (String uri : uris) {
         String parts = uri.substring(commonURIPath.length() + 1);
         int numOfParts = parts.split("/").length;
         if (numOfParts > partsLen) {
            partsLen = numOfParts;
         }
      }

      if (partsLen > 0) {
         StringBuilder parts = new StringBuilder();
         for (int i = 0; i < partsLen; i++) {
            parts.append("part").append(i + 1);
            if (i < partsLen - 1) {
               parts.append(" && ");
            }
         }
         return parts.toString();
      }
      return "";
   }

   /**
    * Extract a dispatch rule string from URI pattern (containing variable parts within
    * {}) in order to explain which parts are variables.
    * @param pattern The URI pattern containing variables parts ({})
    * @return A string representing dispatch rules for the corresponding incoming request.
    */
   public static String extractPartsFromURIPattern(String pattern){
      if (pattern.contains("{") && pattern.contains("}")){
         // Build a pattern for extracting parts from pattern.
         String partsPattern = pattern.replaceAll("(\\{[^\\}]+\\})", "\\\\{(.+)\\\\}");
         Pattern partsP = Pattern.compile(partsPattern);
         Matcher partsM = partsP.matcher(pattern);
         
         if (partsM.matches()){
            StringBuilder parts = new StringBuilder();
            for (int i=1; i<partsM.groupCount()+1; i++){
               parts.append(partsM.group(i));
               if (i<partsM.groupCount()){
                  parts.append(" && ");
               }
            }
            return parts.toString();
         }
      }
      return "";
   }
   
   /**
    * Extract and build a dispatch criteria string from URI pattern (containing variable parts within
    * {}), projected onto a real instanciated URI.
    * @param pattern The URI pattern containing variables parts ({})
    * @param realURI The real URI that should match pattern.
    * @return A string representing dispatch criteria for the corresponding incoming request.
    */
   public static String extractFromURIPattern(String pattern, String realURI){
      Map<String, String> criteriaMap = new TreeMap<String, String>();
      
      // Build a pattern for extracting parts from pattern.
      String partsPattern = pattern.replaceAll("(\\{[^\\}]+\\})", "\\\\{(.+)\\\\}");
      Pattern partsP = Pattern.compile(partsPattern);
      Matcher partsM = partsP.matcher(pattern);
      
      // Build a pattern for extracting values from realURI.
      String valuesPattern = pattern.replaceAll("(\\{[^\\}]+\\})", "(.+)");
      Pattern valuesP = Pattern.compile(valuesPattern);
      Matcher valuesM = valuesP.matcher(realURI);
      
      // Both should match and have the same group count.
      if (valuesM.matches() && partsM.matches() 
            && valuesM.groupCount() == partsM.groupCount()){
         for (int i=1; i<partsM.groupCount()+1; i++){
            criteriaMap.put(partsM.group(i), valuesM.group(i));
         }
      }
      
      // Just appends sorted entries, separating them with /.
      StringBuilder result = new StringBuilder();
      for (String criteria : criteriaMap.keySet()){
         result.append("/").append(criteria).append("=").append(criteriaMap.get(criteria));
      }
      return result.toString();
   }

   /**
    * Extract and build a dispatch criteria string from URI parameters
    * @param paramsRule The dispatch rules referencing parameters to consider
    * @param uri The URI from which we should build a specific dispatch criteria
    * @return A string representing a dispatch criteria for the corresponding incoming request.
    */
   public static String extractFromURIParams(String paramsRule, String uri){
      Map<String, String> criteriaMap = new TreeMap<String, String>();

      if (uri.contains("?") && uri.contains("=")) {
         String parameters = uri.substring(uri.indexOf("?") + 1);
         StringBuilder params = new StringBuilder();

         for (String parameter : parameters.split("&")){
            String[] pair = parameter.split("=");
            try {
               String key = URLDecoder.decode(pair[0], "UTF-8");
               String value = URLDecoder.decode(pair[1], "UTF-8");
               criteriaMap.put(key, value);
            } catch (UnsupportedEncodingException e) {
               e.printStackTrace();
            }
         }

         // Just appends sorted entries, separating them with /.
         StringBuilder result = new StringBuilder();
         for (String criteria : criteriaMap.keySet()){
            if (paramsRule.contains(criteria)) {
               result.append("?").append(criteria).append("=").append(criteriaMap.get(criteria));
            }
         }
         return result.toString();
      }
      return "";
   }
}
