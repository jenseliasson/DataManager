/*
 * (C) Copyright 2016 ThingWave AB (https://www.thingwave.eu/).
 */
package eu.thingwave.datamanager;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;

import org.json.simple.parser.JSONParser;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class ServiceRegistryClient {
  private final String USER_AGENT = "Mozilla/5.0";
  String endpoint = null;

  public ServiceRegistryClient(String endpoint) {
    this.endpoint = endpoint;

    System.out.println("Starting ServiceRegistryClient");
  }

  public String deregister(){

    return null;
  }

  /**
   * \fn public String register(String sysName, String endpoint, String serviceType)
   *
  */
  public String register(String sysName, String address, String serviceuri, int port, String serviceType){
    String response = null;

    String p = new String("");
    p+= "{\n";
    p+= "  \"providedService\": {\n";
    p+= "    \"serviceDefinition\": \""+serviceType+"\",\n";
    p+= "    \"interfaces\": [\n";
    p+= "      \"json\"\n";
    p+= "    ],\n";
    p+= "    \"serviceMetadata\": {\n";
    //p+= "      \"unit": "celsius\"\n";
    p+= "    }\n";
    p+= "  },\n";
    p+= "  \"provider\": {\n";
    p+= "    \"systemName\": \""+sysName+"\",\n";
    p+= "    \"address\": \""+address+"\",\n";
    p+= "    \"port\": "+port+"\n";
    p+= "  },\n";
    p+= "  \"serviceURI\": \""+serviceuri+"\"\n";
    p+="}\n";
    System.out.println(p);
    try {
      response = this.sendRequest("POST", endpoint + "/register", null, p);
      System.out.println("AH/SR response: " + response);
    } catch(Exception e){
      System.out.println("AH/SR: register error: " + e.toString());
    }

    return response;
  }


  public String remove(String sysName, String serviceurl, String serviceType){
    String response = null;

    String p = new String("");
p+= "{\n";
p+= "  \"providedService\": {\n";
p+= "    \"serviceDefinition\": \""+serviceType+"\",\n";
p+= "    \"interfaces\": [\n";
p+= "      \"json\"\n";
p+= "    ],\n";
p+= "    \"serviceMetadata\": {\n";
//p+= "      \"unit": "celsius\"\n";
p+= "    }\n";
p+= "  },\n";
p+= "  \"provider\": {\n";
p+= "    \"systemName\": \""+sysName+"\",\n";
p+= "    \"address\": \"endpoint\"\n";
//    "port": 8454
p+= "  },\n";
p+= "  \"serviceURI\": \""+serviceurl+"\"\n";
p+="}\n";
System.out.println(p);
    try {
      response = this.sendRequest("PUT", endpoint + "/remove", null, p);
      System.out.println("AH/SR response: " + response);
    } catch(Exception e){
      System.out.println("AH/SR: remove error: " + e.toString());
    }

    return response;
  }


  // HTTP GET request
  private String sendGet(String url, String parameters) throws Exception {
    URL obj = new URL(url);
    HttpURLConnection con = (HttpURLConnection) obj.openConnection();

    // optional default is GET
    con.setRequestMethod("GET");

    //add request header
    con.setRequestProperty("User-Agent", USER_AGENT);

    int responseCode = con.getResponseCode();
    System.out.println("\nSending 'GET' request to URL : " + url);
    System.out.println("Response Code : " + responseCode);

    BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
    String inputLine;
    StringBuffer response = new StringBuffer();

    while ((inputLine = in.readLine()) != null) {
      response.append(inputLine);
    }
    in.close();

    //print result
    //System.out.println(response.toString());
    return response.toString();
  }

  // HTTP REQUEST
  private String sendRequest(String method, String url, String parameters, String payload) throws Exception {
    URL obj = new URL(url);
    HttpURLConnection con = (HttpURLConnection) obj.openConnection();

    // optional default is GET
    con.setDoOutput(true);
    con.setRequestMethod(method);

    //add request header
    con.setRequestProperty("User-Agent", USER_AGENT);
    con.setRequestProperty("Content-Type", "application/json");
    con.setRequestProperty("Accept", "application/json");

    con.connect();
    OutputStreamWriter wr = new OutputStreamWriter(con.getOutputStream());
    wr.write(payload);
    wr.flush();
    //wr.close();

    int responseCode = con.getResponseCode();
    System.out.println("\nSending '"+method+"' request to URL : " + url);
    System.out.println("Response Code : " + responseCode);

    BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
    String inputLine;
    StringBuffer response = new StringBuffer();

    while ((inputLine = in.readLine()) != null) {
      response.append(inputLine);
    }
    in.close();

    //print result
    JSONParser parser = new JSONParser(); 
    JSONObject json = (JSONObject)parser.parse(response.toString());

    return response.toString();
  }

}
