/*
 *  Copyright (c) 2018 ThingWave AB
 *
 *  This work is part of the Productive 4.0 innovation project, which receives grants from the
 *  European Commissions H2020 research and innovation programme, ECSEL Joint Undertaking
 *  (project no. 737459), the free state of Saxony, the German Federal Ministry of Education and
 *  national funding authorities from involved countries.
 */

package eu.thingwave.datamanager.resources;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.*;
import java.text.SimpleDateFormat;

import javax.servlet.*;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.*;

import java.io.*;
import java.io.InputStream;
import javax.servlet.*;
import javax.servlet.http.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.ParseException;
import org.json.simple.parser.JSONParser;

import java.util.regex.Pattern;

public class StorageHTTPResource extends HttpServlet
{
  Historian historian = null;
  Properties prop = null;
  Logger logger = null;

  public StorageHTTPResource(Properties prop){
    this.prop = prop;
    logger = LogManager.getRootLogger();
  }

  public void setHistorian(Historian historian) {
    this.historian = historian;
  }


  /**
   *
   *
   */
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
  {
    boolean db_ok = false;
    String res = "", filename=null, format="json";
    int results = 1;

    String end_date="2037-12-31T23:59:59", start_date = "1970-01-01T01:00:00"; //BUG: 2038 32-bit UNIX timestamp overflow. Change to DATETIME instead
    List<String> signals = new ArrayList();

    response.setStatus(HttpServletResponse.SC_OK);
    response.addHeader("Access-Control-Allow-Origin", "*");

    if (request.getParameter("wadl") != null) {
      response.setContentType("application/xml");
      response.setStatus(HttpServletResponse.SC_OK);
      response.getWriter().println(historian.getHistorianWADL());
      return;
    }

    //logger.info("\nPath: "+ request.getRequestURI()+"?"+request.getQueryString());

    if (request.getQueryString() != null) {
      //System.out.println("Parameters:");
      //System.out.println("number: " + request.getParameter("number"));
      if (request.getParameter("results") != null) {
	results = Integer.parseInt(request.getParameter("results").toString());
      }
      //System.out.println("start: " + request.getParameter("start"));
      //System.out.println("stop: " + request.getParameter("stop"));
      //System.out.println("filename: " + request.getParameter("f"));
      //System.out.println("format: " + request.getParameter("format"));
      filename = request.getParameter("f");
      if (request.getParameter("format") != null)
	format = request.getParameter("format");

      int id=0;
      while (request.getParameter("sig"+id) != null) {
	System.out.println("sig"+id+": " + request.getParameter("sig"+id));
	signals.add(request.getParameter("sig"+id));
	id++;
      }
    }

    String path = (request.getRequestURI()).replaceAll("/storage", "");
    if (path.equals("") || path.equals("/")) {
      //System.out.println("GET Generic Historian requested");

      if (request.getParameter("devices/all") != null) {
	res = generateDeviceList(0);
	response.getWriter().println(res);
	return;
      } else if (request.getParameter("devices/recent") != null) {
	res = generateDeviceList(2);
	response.getWriter().println(res);
	return;
      } else if (request.getParameter("devices/today") != null) {
	res = generateDeviceList(1);
	response.getWriter().println(res);
	return;
      }
      response.setStatus(HttpServletResponse.SC_OK);
      res = "The following commands are supported:\n?devices/all\n?devices/recent\n?devices/today\n?help\n?wadl";
      response.setContentType("text/plain");
      response.getWriter().println(res);
      return;
    }

    /* specfic dev/sys was requested */
    path = path.replaceAll("/", "");
    //logger.info("Historian for path '"+path+"'requested");

    if (results == 1) { // get latest data
      String ct = new String();
      if (filename != null) {
	if (filename.equals("/") || filename.charAt(filename.length() - 1) == '/') {
	  //res = historian.getFileListfromPeer(path); //BUG
	} else {
	  //res = historian.getFilefromPeer(path, filename); //BUG 
	  response.setContentType(historian.getContentType(filename));
	  response.addHeader("Content-disposition", "attachment; filename="+filename);
	}

      } else {
	res = historian.getLastMessagefromPeer(path, ct);
	//System.out.println("GET: "+res);
      }

      if ( res == null) {
	res = "";
      }
      response.getWriter().println(res);

      // auto detect content type XML, BUG: store all data in container class with data, mime type, etc
      if (res.contains("<?xml ")) {
	try {
	  DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
	  InputSource is = new InputSource();
	  is.setCharacterStream(new StringReader(res));

	  Document doc = db.parse(is);
	  NodeList nodes = doc.getElementsByTagName("senml");
	  if (nodes.getLength() == 1)
	    response.setContentType("application/senml+xml");
	  else
	    response.setContentType("application/xml");
	} catch (Exception e) {}
      }

      //auto detect content type  JSON, BUG: store all data in container class with data, mime type, etc 
      JSONParser parser = new JSONParser();
      try{
	Object obj = parser.parse(res);
	response.setContentType("application/json");
      } catch (Exception e) {}

    } else { //results > 1, get historical data

      if (format.equals("xml") || format.equals("json")) {
	response.addHeader("Content-type", "application/"+format);
	res = historian.getSenMLDatafromPeer(path, format, results, null, null, start_date, end_date); // add content format plus binary
	response.getWriter().println(res);

      } else if (format.equals("csv")) {
	response.addHeader("Content-type", "application/force-download");
	response.addHeader("Content-type", "text/csv");
	response.addHeader("Content-type", "application/download");
	response.addHeader("Content-Disposition", "attachment;filename=file.csv");

	String[] sigs = signals.toArray(new String[signals.size()]);
	res = historian.getCSVDatafromPeer(path, results, sigs, null, start_date, end_date);
	response.getWriter().println(res);

      } else if (format.equals("excel")) {
	response.addHeader("Content-type", "application/force-download");
	//response.addHeader("Content-type", "application/octet-stream");
	response.addHeader("Content-type", "application/download");
	response.addHeader("Content-type", "application/vnd.ms-excel");
	response.addHeader("Content-Disposition", "attachment;filename=file.xls");
	response.addHeader("Content-Transfer-Encoding", "binary");

	String[] sigs = signals.toArray(new String[signals.size()]);
	byte[] doc = historian.getExcelDatafromPeer(path, results, sigs, null, start_date, end_date);
	if (doc != null) {
	  OutputStream os = response.getOutputStream();
	  os.write(doc, 0, doc.length);
	} else { //generate error 
	}
      }
    }

    return;
  }


  private static String getBody(HttpServletRequest request) throws IOException {
    String body = null;
    StringBuilder stringBuilder = new StringBuilder();
    BufferedReader bufferedReader = null;

    try {
      InputStream inputStream = request.getInputStream();
      if (inputStream != null) {
	bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
	char[] charBuffer = new char[128];
	int bytesRead = -1;
	while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
	  stringBuilder.append(charBuffer, 0, bytesRead);
	}
      } else {
	stringBuilder.append("");
      }
    } catch (IOException ex) {
      throw ex;
    } finally {
      if (bufferedReader != null) {
	try {
	  bufferedReader.close();
	} catch (IOException ex) {
	  throw ex;
	}
      }
    }

    body = stringBuilder.toString();
    return body;
  }


  protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
  {
    String res = "kind of ok";

    //System.out.println("\nPath: "+ request.getRequestURI()+"?"+request.getQueryString());

    Connection conn = null;
    Statement stmt = null;
    boolean db_ok = true;

    String data = getBody(request);
    System.out.println("##\n"+data+"\n##");

    String mac = request.getRequestURI().replaceAll("/storage/", "");
    System.out.println("MAC="+mac);      

    try {
      Class.forName("com.mysql.jdbc.Driver");

      //System.out.println("Connecting to database...");
      conn = DriverManager.getConnection("jdbc:mysql://"+prop.getProperty("dburl", "localhost")+"/" + prop.getProperty("database"), prop.getProperty("dbuser"), prop.getProperty("dbpassword"));

      stmt = conn.createStatement();

      JSONParser parser = new JSONParser();
      try{
	Object obj = parser.parse(data);
	JSONArray json = (JSONArray)obj;

	//System.out.println("The 2nd element of array");
	//System.out.println(json.get(1));
	//System.out.println();

	boolean result = historian.insertSenML(mac, json, conn);
	if (result == false) {
	  String sql = "INSERT INTO iot_devices(hwaddr, name, last_update) VALUES(\""+mac+"\", \""+mac+"\", NOW());";
	  //System.out.println("SQL: "+sql);
	  stmt.execute(sql);
	  stmt.close();
	  //int id = historian.macToID(mac, conn);
	  historian.insertSenML(mac, json, conn);
	  conn.close();
	}

      } catch(ParseException pe) {
	//System.out.println("position: " + pe.getPosition());
	System.out.println(pe);
      }


    } catch(SQLException se){
      se.printStackTrace();
      res = historian.generateErrorMessage(0);
    } catch(Exception e){
      //Handle errors for Class.forName
      e.printStackTrace();
      res = historian.generateErrorMessage(-1);
    }

    response.addHeader("Access-Control-Allow-Origin", "*");
    response.addHeader("Access-Control-Allow-Methods", "GET, PUT, POST");
    response.getWriter().println(res);
  }


  protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
  {
    String res = null;
    response.addHeader("Access-Control-Allow-Origin", "*");
    response.addHeader("Access-Control-Allow-Methods", "GET, PUT, POST");
    response.addHeader("Access-Control-Allow-Headers", "X-Requested-With");

    response.getWriter().println(res);
  }


  /**
   * \fn private String generateDeviceList(int mode) 
   * \brief
   * \param mode 0 for all devices. 1 for 
   * \return returns a HTML-string
   */
  private String generateDeviceList(int mode) {
    Connection conn = null;
    Statement stmt = null;
    boolean db_ok = true;
    String res = "";

    try {
      Class.forName("com.mysql.jdbc.Driver");

      //STEP 3: Open a connection
      //System.out.println("Connecting to database...");
      conn = DriverManager.getConnection("jdbc:mysql://"+prop.getProperty("dburl", "localhost")+"/" + prop.getProperty("database"), prop.getProperty("dbuser"), prop.getProperty("dbpassword"));

      stmt = conn.createStatement();
      //String sql = "SELECT hwaddr, last_update FROM iot_devices WHERE last_update > '"+compdate+" 00:00:00';";
      //ResultSet rs = stmt.executeQuery(sql);

      String sql = "";
      Calendar cal;
      SimpleDateFormat sdf;
      String compdate;

      switch (mode) {
	case 0: //all
	  sql = "SELECT hwaddr, last_update FROM iot_devices;";
	  break;
	case 1: //today
	  cal = Calendar.getInstance();
	  cal.getTime();
	  sdf = new SimpleDateFormat("yyyy:MM:dd");
	  compdate = sdf.format(cal.getTime());

	  sql = "SELECT hwaddr, last_update FROM iot_devices WHERE last_update > '"+compdate+" 00:00:00';";
	  break;
	case 2: //recent
	  cal = Calendar.getInstance();
	  long now = cal.getTimeInMillis();
	  long recent = ((now/1000) - 60*60*24*3) * 1000; // 3 days back
	  java.util.Date d = new java.util.Date();
	  d.setTime(recent);
	  cal.setTime(d);

	  sdf = new SimpleDateFormat("yyyy:MM:dd");
	  compdate = sdf.format(cal.getTime());

	  sql = "SELECT hwaddr, last_update FROM iot_devices WHERE last_update > '"+compdate+" 00:00:00';";
	  break;
	default:
	  sql = "SELECT hwaddr, last_update FROM iot_devices;";
      }
      //System.out.println("SQL: "+sql);

      ResultSet rs = stmt.executeQuery(sql);
      /*res = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
	res += "<sigml xmlns=\"http://www.arrowhead.eu/core/sys/historian\">\n";
	res += " <info>\n";
	res += "  <database online=\""+db_ok+"\"/>\n";
	res += " </info>\n";
	res += " <devices>\n";*/

      res = "[\n";

      /*for(int i=0; i< devices.size(); i++) {
	res += ( "  <dev id=\""+devices.get(i)+"\"/>\n");
	}*/
      if(rs.next()){ 
	String _dev = rs.getString("hwaddr");
	//res += ( "  <dev id=\""+_dev+"\"/>\n");
	res += ("  {\"devId\": \""+_dev+"\"}");
      }
      while(rs.next()){ 
	String _dev = rs.getString("hwaddr");
	//res += ( "  <dev id=\""+_dev+"\"/>\n");
	res += (",\n  {\"devId\": \""+_dev+"\"}");
      }

      //res += " </devices>\n";
      //res += "</sigml>";
      res += "]\n";
      rs.close();
      stmt.close();
      conn.close();

    } catch(SQLException se){
      res = historian.generateErrorMessage(0);
    } catch(Exception e){
      //Handle errors for Class.forName
      e.printStackTrace();
      res = historian.generateErrorMessage(-1);
    }

    //System.out.println(res);
    return res;
  }



}
