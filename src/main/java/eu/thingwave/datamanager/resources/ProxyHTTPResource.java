/*
 * (C) Copyright 2016 ThingWave AB (https://www.thingwave.eu/).
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

import java.sql.*;

import java.io.*;
import java.io.InputStream;
import javax.servlet.*;
import javax.servlet.http.*;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.ParseException;
import org.json.simple.parser.JSONParser;

import java.util.regex.Pattern;

public class ProxyHTTPResource extends HttpServlet
{
  Historian historian = null;
  Properties prop = null;

  HashMap<String, String> map;


  /**
   * \fn
   * \brief 
   */
  public ProxyHTTPResource(Properties prop){
    this.prop = prop;
    map = new HashMap<String, String>(); // perhaps write and read this from file/data base is case of a restart of this service? Good or bad?
  }


  /**
   * \fn
   * \brief 
   */
  public void setHistorian(Historian historian) {
    this.historian = historian;
  }


  /**
   * \fn
   * \brief 
   */
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
  {
    String res = "", filename=null, format="json";

    response.setStatus(HttpServletResponse.SC_OK);
    response.addHeader("Access-Control-Allow-Origin", "*");

    if (request.getParameter("wadl") != null) {
      response.setContentType("application/xml");
      response.setStatus(HttpServletResponse.SC_OK);
      response.getWriter().println(historian.getHistorianWADL()); // I think is would be best to support an own Proxy WADL instead?
      return;
    }

    //System.out.println("\nPath: "+ request.getRequestURI()+"?"+request.getQueryString());

    if (request.getQueryString() != null) {
      //System.out.println("Parameters:");
      //System.out.println("format: " + request.getParameter("format"));
      filename = request.getParameter("f");
      if (request.getParameter("format") != null)
	format = request.getParameter("format");
    }

    String path = (request.getRequestURI()).replaceAll("/proxy", "");
    if (path.equals("") || path.equals("/")) {
      //System.out.println("GET Generic Proxy requested");

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

    path = path.replaceAll("/", "");
    //System.out.println("GET Proxy for path '"+path+"' requested");

    /* return last message. BUG: support binary payloads as well! */
    if (map.containsKey(path)) {
      res = map.get(path);
    }

    /* auto detect content type, BUG bad approach. Save this as metadata later on */
    if (res.contains("<?xml ")) {
      response.setContentType("application/xml");
    } else if (res.contains("{")) {
      response.setContentType("application/json");
    } else {
      response.setContentType("text/plain");
    }

    if ( res == null) {
      res = "null"; // Generate SigML+JSON/XML error response here
    }
    response.getWriter().println(res);

    return;
  }


  /**
   * \fn
   * \brief
   */
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
    String res = "";

    System.out.println("\nPath: "+ request.getRequestURI()+"?"+request.getQueryString());

    String data = getBody(request);
    //InputStream body = request.getInputStream();
    System.out.println("##\n"+data+"\n##");

    String mac = request.getRequestURI().replaceAll("/proxy/", "");
    System.out.println("MAC="+mac);      

    /* store message in Proxy storage here */
    if (!map.containsKey(mac)) {
      System.out.println("Creating proxy instance for '"+mac+"'");
      map.put(mac, data);
    }

    /* send response */
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
