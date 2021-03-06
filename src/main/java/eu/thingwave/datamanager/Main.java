/*
 *  Copyright (c) 2018 ThingWave AB
 *
 *  This work is part of the Productive 4.0 innovation project, which receives grants from the
 *  European Commissions H2020 research and innovation programme, ECSEL Joint Undertaking
 *  (project no. 737459), the free state of Saxony, the German Federal Ministry of Education and
 *  national funding authorities from involved countries.
 */

package eu.thingwave.datamanager;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.eclipse.californium.core.CoapServer;

import eu.thingwave.datamanager.resources.Login;
import eu.thingwave.datamanager.resources.StorageResource;
import eu.thingwave.datamanager.resources.StorageHTTPResource;
import eu.thingwave.datamanager.resources.ProxyHTTPResource;
import eu.thingwave.datamanager.resources.StatusHTTPResource;
import eu.thingwave.datamanager.resources.DiskResource;
import eu.thingwave.datamanager.resources.Historian;
import eu.thingwave.datamanager.resources.Forwarder;

/* Arrowhead */
import eu.thingwave.datamanager.ServiceRegistryClient;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

/**
 *
 * @author Jens Eliasson <jens.eliasson@thingwave.eu>
 */
public class Main {
  Properties prop = null;
  org.eclipse.jetty.server.Server jettyserver;
  ServiceRegistryClient src = null;
  String[] args;
 
  Logger logger = null;

  
  public Main(String[] args)  {
    this.args = args;
    logger = LogManager.getRootLogger(); 
  }


  public boolean execute() {
    logger.info("DataManager system starting up"); 

    prop = new Properties();
    InputStream input = null;

    try {
      input = new FileInputStream("datamanager.properties");
      prop.load(input);
    } catch (IOException ex) {
      ex.printStackTrace();
      System.exit(-1);
    } finally {
      if (input != null) {
	try {
	  input.close();
	} catch (IOException e) {
	  e.printStackTrace();
	  System.exit(-1);
	}
      }
    }


    /* Specific methods for Historian */
    int port = Integer.parseInt(prop.getProperty("CoAP-port", "61616"));
    CoapServer server = null;

    /* Arrowhead Framework configuration */
    String ahfsrUrl = prop.getProperty("ahf-sr", "");
    boolean srok =  false;
    if (!ahfsrUrl.equals("")) {
      src = new ServiceRegistryClient(ahfsrUrl);
      logger.info("Arrowhead enabled, using ServiceRegistry: " + ahfsrUrl); 
      //String srresp = src.remove(prop.getProperty("SysName", ""), "http://127.0.0.1:4001/storage", "_historian._http._tcp");
      //srresp = src.register(prop.getProperty("SysName", ""), "http://127.0.0.1", "storage", Integer.parseInt(prop.getProperty("HTTP-port", "0")), "_historian._http._tcp");
      String myendpint = "http://172.16.210.182:4001/storage"; //BUG, fix this
      String srresp = src.remove(prop.getProperty("SysName", ""), "http://172.16.210.182:4001/storage", "_ahfc-dm-historian._http._tcp");
      srresp = src.register(prop.getProperty("SysName", ""), "http://arrowhead.ddns.net" /*"http://172.16.210.182"*/, "storage", Integer.parseInt(prop.getProperty("HTTP-port", "0")), "_ahfc-dm-historian._http._tcp");
      if (srresp != null) {
	srok = true;
      }
    }

    /* start CoAP server */
    logger.info("Starting CoAP server"); 
    server = new CoapServer(port);
    Historian historian = new Historian(prop);	

    StorageResource sr = new StorageResource(prop, "storage");
    DiskResource diskres = new DiskResource(prop, "disk");

    sr.setHistorian(historian);
    Forwarder forwarder = new Forwarder(prop);	
    forwarder.start();
    sr.setForwarder(forwarder);
    server.add(sr);
    server.add(diskres);
    server.start();

    Runtime.getRuntime().addShutdownHook(new ShutdownThread(this));


    try {
      port = Integer.parseInt(prop.getProperty("HTTP-port", "4001"));
      ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
      jettyserver = new org.eclipse.jetty.server.Server(port);
      ResourceHandler resource_handler = new ResourceHandler();
      resource_handler.setDirectoriesListed(true);
      resource_handler.setWelcomeFiles(new String[]{ "index.html" });
      resource_handler.setResourceBase("www");

      HandlerList handlers = new HandlerList();
      handlers.setHandlers(new Handler[] { resource_handler, context, new DefaultHandler() }); //BUG, fix so that a user cannot get web pages without a login session
      jettyserver.setHandler(handlers);
      //jettyserver.setHandler(context);

      Login login = new Login(prop);
      context.addServlet(new ServletHolder(login),"/login.php");

      StorageHTTPResource hsr = new StorageHTTPResource(prop);
      hsr.setHistorian(historian);
      context.addServlet(new ServletHolder(hsr),"/storage/*");

      ProxyHTTPResource psr = new ProxyHTTPResource(prop);
      psr.setHistorian(historian);
      context.addServlet(new ServletHolder(psr),"/proxy/*");

      StatusHTTPResource stsr = new StatusHTTPResource(prop);
      context.addServlet(new ServletHolder(stsr),"/status");
      stsr.setStatus("SR", srok);

      jettyserver.start();
    } catch (Exception e) {
      logger.error("Could not start Jetty HTTP server on port "+port);
      return false;
    }

    return true;
  }

  public boolean shutDown() throws Exception {
    String srresp = src.remove(prop.getProperty("SysName", ""), "http://arrowhead.ddns.net/storage52", "_historian._http._tcp");
    if (srresp == null)
      logger.error("Error: could not deregister");
    jettyserver.stop();

    return true;
  }

  public static void main (String[] args) {
    Main m = new Main(args);
    m.execute();
  }



  static private class ShutdownThread extends Thread {
    Main parent;
    public ShutdownThread(Main p) {
      super();
      this.parent = p;
    }
    public void run() {
      Logger logger = LogManager.getRootLogger();
      try {
	Thread.sleep(50);
	logger.info("Shutting down ...");
	parent.shutDown();
	//server.stop();

      } catch (InterruptedException e) {
	e.printStackTrace();
      } catch (Exception e) {
      }
    }
  }
}
