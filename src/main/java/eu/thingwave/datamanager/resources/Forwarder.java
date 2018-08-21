/*
 * (C) Copyright 2016 ThingWave AB (https://www.thingwave.eu/).
 */
package eu.thingwave.datamanager.resources;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.Calendar;
import java.util.Vector;
import java.util.ArrayList;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.californium.core.CoapClient;
import org.eclipse.californium.core.CoapResponse;
import org.eclipse.californium.core.Utils;

import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;

/**
 * @brief Manages all forwarding to external services (like ThingSpeak, Byteport, Historian, FTP, ...)
 * @author Jens Eliasson <jens.eliasson@thingwave.eu>
 */
public class Forwarder extends Thread {
  Vector queue = null;
  boolean keeprunning = true;

  ArrayList<ForwardRule> flist = null; 

  public Forwarder(Properties prop) {
    queue = new Vector();
  
    /* read in rules files */
    String rulefile = prop.getProperty("forward-rules-file", "forwardrules.xml");

    flist = new ArrayList<ForwardRule>(); 

    try {	
      File inputFile = new File(rulefile);
      DocumentBuilderFactory dbFactory 	= DocumentBuilderFactory.newInstance();
      DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
      Document doc = dBuilder.parse(inputFile);
      doc.getDocumentElement().normalize();
      System.out.println("Root element :" + doc.getDocumentElement().getNodeName());
      NodeList nList = doc.getElementsByTagName("rule");
      System.out.println("----------------------------");
      for (int temp = 0; temp < nList.getLength(); temp++) {
	Node nNode = nList.item(temp);
	System.out.println("\nCurrent Element :" + nNode.getNodeName());
	if (nNode.getNodeName().equals("rule") && nNode.getNodeType() == Node.ELEMENT_NODE) {
	  Element eElement = (Element) nNode;
	  System.out.println("Type : " + eElement.getAttribute("type"));
	  System.out.println("url: "  + eElement.getElementsByTagName("url").item(0).getTextContent());
	  System.out.println("enabled : "  + eElement.getElementsByTagName("enabled").item(0).getTextContent());

	  switch (eElement.getAttribute("type")) {
	    case "TW::Historian":
	      System.out.println("ThingWave::Historian at "+ eElement.getElementsByTagName("url").item(0).getTextContent()+"\n");
	      HistorianForwardRule hfr = new HistorianForwardRule(eElement.getElementsByTagName("url").item(0).getTextContent());
	      boolean ok = hfr.check();
	      if (ok )
		flist.add(hfr);

	    break;
	    default:
	    System.out.println("Unknown forward rule");
	  }

	}
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    /* store rules */
  }


  public void run() {
    while (keeprunning) {
      //System.out.println("Checking queue ("+queue.size()+" elements)");
      try {
	processQueue();
	Thread.sleep(1000);
      } catch (InterruptedException ie) {
      }
    }
  }

  public int processQueue() {

    /* check queue */
    if (queue.isEmpty())
      return 0;

    ForwardQueueItem fqi = (ForwardQueueItem)queue.firstElement();

    /* apply each rule for the device/message */

    /* get rules */

    /* if transmission failed, add to dB and remove from queue */

    return -1;
  }

  public int addQueue(String hwaddr, String filename, int cf, byte[] content, long crc32value) {
    ForwardQueueItem fqi = new ForwardQueueItem(hwaddr, filename, cf, content);
    queue.add(fqi);

    return -1;
  }

  private class ForwardQueueItem {
    private String device, filename;
    int cf;
    byte[] payload;

    boolean in_db;

    public ForwardQueueItem(String device, String filename, int cf, byte[] payload) {

    }

  }


  abstract class ForwardRule {
  
    public ForwardRule() {
    }

    public abstract boolean check();
    
  }

  class HistorianForwardRule extends ForwardRule {
    String url;
    URI uri = null;
    CoapClient client = null; 

    public HistorianForwardRule(String url) {
      super();
      this.url = url;
    }
      
    public boolean check() {
      System.out.println("\ncheck()\n");
    
      String tmp = url.replace("$DEV_NAME", "");
      try {
	uri = new URI("coap://iot.eclipse.org:5683/obs");
      } catch (URISyntaxException e) {
	System.err.println("Invalid URI: " + e.getMessage());
	return false;
      }
      client = new CoapClient(uri);
    
      CoapResponse response = client.get();
      System.out.println("GET");

      if (response != null) {

	System.out.println(response.getCode());
	System.out.println(response.getOptions());
	System.out.println(response.getResponseText());

	System.out.println("\nADVANCED\n");
	// access advanced API with access to more details through .advanced()
	System.out.println(Utils.prettyPrint(response));
	return true;

      } else {
	System.out.println("No response received.");
      }

      return false;
    }

  }

}


