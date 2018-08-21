package eu.thingwave.datamanager.resources;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.*;
import java.text.SimpleDateFormat;
import java.util.regex.Pattern;

import java.sql.*;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.ParseException;
import org.json.simple.parser.JSONParser;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.Utils;

import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.CRC32;

import org.jtransforms.fft.DoubleFFT_1D;

//import eu.thingwave.tentacle.Tentacle;

public class StorageResource extends CoapResource {
  boolean enable_database = false;
  boolean enable_local_fs = false;
  String dburl = "localhost";
  String master_key = "";
  private Historian historian = null;
  private Forwarder forwarder = null;
  ArrayList devices, devresources;
  boolean db_ok = false;

  Properties prop = null;

  public void setForwarder(Forwarder forwarder) {
    this.forwarder = forwarder;
  }

  public Forwarder getForwarder(Forwarder forwarder) {
    return forwarder;
  }

  public void setHistorian(Historian historian) {
    this.historian = historian;
  }


  public Historian getHistorian() {
    return historian;
  }


  public StorageResource(Properties prop, /*Tentacle tentacle, */String name) {
    super(name);
    this.prop = prop;
    //  this.tentacle = tentacle;
    getAttributes().setTitle("ThingWave::Historian");
    getAttributes().addResourceType("_historian-coap._udp");

    if (prop.getProperty("enable-database", "false").equals("true")) {
      enable_database = true;
    }
    if (prop.getProperty("enable-local-fs", "false").equals("true")) {
      enable_local_fs = true;
    }
    dburl = prop.getProperty("dburl", "127.0.0.1:3306");
    master_key = prop.getProperty("master-key", "");
    

    if (enable_database ) {
      Connection conn = null;
      Statement stmt = null;
      db_ok = true;

      try {
	Class.forName("com.mysql.jdbc.Driver");
	conn = DriverManager.getConnection("jdbc:mysql://"+prop.getProperty("dburl", "localhost")+"/" + prop.getProperty("database"), prop.getProperty("dbuser"), prop.getProperty("dbpassword"));

	stmt = conn.createStatement();
	String sql;
	sql = "SELECT * FROM iot_devices";
	ResultSet rs = stmt.executeQuery(sql); 

	devices = new ArrayList();
	devresources = new ArrayList();
	while(rs.next()){
	  int id  = rs.getInt("id");
	  String mac = rs.getString("hwaddr");

	  devices.add(mac);
	  DeviceStorageResource dsr = new DeviceStorageResource(mac, id, this);
	  this.add(dsr);
	  devresources.add(mac);
	}
      } catch(SQLException se){
	db_ok = false;
	se.printStackTrace();
      } catch(Exception e){
	db_ok = false;
	e.printStackTrace();
      }
    }
  }

  private int checkMasterKey(CoapExchange exchange) {
  
    if (master_key.equals("") == true)	
      return 0;

    Request request = exchange.advanced().getRequest();
    OptionSet os = request.getOptions();
    System.out.println(">>"+os.getUriQueryString()+"<<");
    String[] arguments = request.getOptions().getUriQueryString().split("&");
//    String[] arguments = parts[1].split("&");

    for(int j=0; j<arguments.length; j++) {
      //System.out.println(">"+arguments[j]+"<");
      int pos = arguments[j].indexOf('=');
      if (pos != -1) {
	String name = arguments[j].substring(0, pos);
	String value = arguments[j].substring(pos+1);
	//System.out.println("NAME:'"+name+"', VALUE: '"+value+"'");
	if (name.equals("_mkey") && value.equals(master_key))
	  return 1;
      }
    }

    try {
      Thread.sleep(200);
    } catch (InterruptedException ie) {
    }

    return -1;
  }

  @Override
    public void handleGET(CoapExchange exchange) {
      int validated = checkMasterKey(exchange);
      if (validated == 0 || validated == 1) {
	System.out.println("Storage::GET\n");
      } else {
	System.out.println("Storage::GET not validated\n");
	exchange.respond(ResponseCode.UNAUTHORIZED, "Not validated", MediaTypeRegistry.TEXT_PLAIN);
	return;
      }

      //String remoteTicket = exchange.getRequestOptions().getTicket();
      //JSONObject json = tentacle.checkRemoteTicket(remoteTicket, exchange.getSourceAddress().getHostAddress());
      //json.put("response", "valid");
      //exchange.respond(ResponseCode.CONTENT, json.toJSONString(), MediaTypeRegistry.APPLICATION_JSON);

      //if( json != null)
      //return;

      Request request = exchange.advanced().getRequest();
      String res = new String("");

      OptionSet os = request.getOptions();
      System.out.println(os.getUriQueryString());
      String[] parts = request.getOptions().getUriQueryString().split("\\?");
      String[] path = parts[0].split("/");

      for(int j=0; j<path.length; j++) {
        System.out.println(path[j]+"#");
      }
      System.out.println("<"+parts[0]+">");

      if(parts[0].startsWith("device/")) {
	String[] dev = parts[0].split("/");
	String mac = dev[1];

      } else if(parts[0].equals("devices/all")) {
	res = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
	res += "<historian xmlns=\"http://www.arrowhead.eu/core/sys/historian\">\n";
	res += " <info>\n";
	res += "  <database online=\""+db_ok+"\"/>\n";
	res += " </info>\n";
	res += " <devices>\n";
	for(int i=0; i< devices.size(); i++) {
	  res += ( "  <dev id=\""+devices.get(i)+"\"/>\n");
	}
	res += " </devices>\n";
	res += "</historian>";
	exchange.respond(ResponseCode.CONTENT, res, MediaTypeRegistry.APPLICATION_XML);
	return;

      } else if(parts[0].equals("devices/today")) {
      } else if(parts[0].equals("help")) {
	res = "The following commands are supported:\n?devices/all\n?devices/recent\n?devices/today\n?help\n?wadl\n";
      } else {
	res = "The following commands are supported:\n?devices/all\n?devices/recent\n?devices/today\n?help\n?wadl\n";
      }

      exchange.respond(res);
    }


  @Override
    public void handlePUT(CoapExchange exchange) {
      int validated = checkMasterKey(exchange);
      if (validated == 0 || validated == 1) {
	System.out.println("Storage::PUT\n");
      } else {
	System.out.println("Storage::PUT not validated\n");
	exchange.respond(ResponseCode.UNAUTHORIZED, "Not validated", MediaTypeRegistry.TEXT_PLAIN);
	return;
      }

      String content = exchange.getRequestText();
      String res = "";

      if (enable_database ) {
	Connection conn = null;
	Statement stmt = null;
	try {
	  Class.forName("com.mysql.jdbc.Driver");

	  //STEP 3: Open a connection
	  System.out.println("Connecting to database...");
	  //conn = DriverManager.getConnection("jdbc:mysql://localhost/" + prop.getProperty("database"), prop.getProperty("dbuser"), prop.getProperty("dbpassword"));
	  conn = DriverManager.getConnection("jdbc:mysql://"+prop.getProperty("dburl", "localhost")+"/" + prop.getProperty("database"), prop.getProperty("dbuser"), prop.getProperty("dbpassword"));

	  //STEP 4: Execute a query
	  System.out.println("Creating statement...");
	  stmt = conn.createStatement();
	  String sql;

	  int id = historian.macToID(content, conn);
	  if ( id == -1) {

	    sql = "INSERT INTO iot_devices(hwaddr, name, last_update) VALUES(\""+content+"\", \""+content+"\", NOW());";
	    //System.out.println("SQL: "+sql);
	    stmt.execute(sql);
	    id = historian.macToID(content, conn);
	    DeviceStorageResource devres = new DeviceStorageResource(content, id, this);
	    this.add(devres);

	    stmt.close();
	    conn.close();

	    res = "OK";
	    exchange.respond(ResponseCode.BAD_REQUEST, res, MediaTypeRegistry.TEXT_PLAIN);
	    return;
	  }

	} catch(SQLException se){
	  db_ok = false;
	  System.out.println("SQL error!");
	  //Handle errors for JDBC
	  se.printStackTrace();
	  exchange.respond(ResponseCode.BAD_REQUEST, "error"); //XXX: SigML here
	  return;
	} catch(Exception e){
	  //Handle errors for Class.forName
	  e.printStackTrace();
	  exchange.respond(ResponseCode.BAD_REQUEST, "error"); //XXX: SigML here
	  return;
	}
      } else {
	DeviceStorageResource devres = new DeviceStorageResource(content, -1, this); // -1 means no dB
	this.add(devres);
      }

      Calendar cal = Calendar.getInstance();
      res = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
      res += "<sigml xmlns=\"urn:ietf:params:xml:ns:sigml\"\n";
      //res += "       bn=\"" + "urn:sys:name:historian.test.arrowhead.eu" + "\"\n";  XXX: what is the NAME??
      res += "       bt=\""+(cal.getTimeInMillis()/1000)+"\">\n";
      res += "  <x n=\"cause\" sv=\"Unknown device\"/>\n";
      res += "</sigml>";

      exchange.respond(ResponseCode.BAD_REQUEST, res, MediaTypeRegistry.APPLICATION_XML);
      return;
    }



  private class DeviceStorageResource extends CoapResource {
    String mac;
    int id;
    StorageResource parent;

    public DeviceStorageResource(String name, int id, StorageResource parent) {
      super(name);
      this.parent = parent;

      mac = new String(name);
      this.id = id;
      getAttributes().setTitle(mac);
      setObservable(true) ;
    }

    public String getMAC() {
      return mac;
    }

    private int checkMasterKey(CoapExchange exchange) {

      if (master_key.equals("") == true)	
	return 0;

      Request request = exchange.advanced().getRequest();
      OptionSet os = request.getOptions();
      System.out.println(">>"+os.getUriQueryString()+"<<");
      String[] arguments = request.getOptions().getUriQueryString().split("&");
      //    String[] arguments = parts[1].split("&");

      for(int j=0; j<arguments.length; j++) {
	//System.out.println(">"+arguments[j]+"<");
	int pos = arguments[j].indexOf('=');
	if (pos != -1) {
	  String name = arguments[j].substring(0, pos);
	  String value = arguments[j].substring(pos+1);
	  //System.out.println("NAME:'"+name+"', VALUE: '"+value+"'");
	  if (name.equals("_mkey") && value.equals(master_key))
	    return 1;
	}

      }

      try {
	Thread.sleep(200);
      } catch (InterruptedException ie) {
      }

      return -1;
    }



    @Override
    public void handleGET(CoapExchange exchange) {
      int validated = checkMasterKey(exchange);
      if (validated == 0 || validated == 1) {
	System.out.println("DeviceStorage::GET\n");
      } else {
	System.out.println("DeviceStorage::GET not validated\n");
	exchange.respond(ResponseCode.UNAUTHORIZED, "Not validated", MediaTypeRegistry.TEXT_PLAIN);
	return;
      }

      String res = null;
      Connection conn = null;
      Statement stmt = null;
      boolean db_ok = true;

      int results = 1;
      String end_date="2100-01-01T23:59:59", start_date = "1900-01-01T00:00:00", signal="", filename="";
      String format="senml+json";

      /* check paramaters */
      Pattern results_pat = Pattern.compile("results=\\d*");
      Pattern start_pat = Pattern.compile("start=\\S+");
      Pattern end_pat = Pattern.compile("end=\\S+");

      Pattern signal_pat = Pattern.compile("signal=\\S+");
      Pattern format_pat = Pattern.compile("format=\\S+");
      Pattern filename_pat = Pattern.compile("f=\\S+");


      try {
	List<String> queries = exchange.getRequestOptions().getUriQuery();
	for (String query:queries) {
	  if (results_pat.matcher(query).matches()) {
	    results = Integer.parseInt(query.split("=")[1]);
	    if (results > 8000)
	      results = 8000;
	  }
	  if (start_pat.matcher(query).matches()) {
	    start_date = query.split("=")[1];
	    //System.out.println("start="+start_date);
	  }
	  if (end_pat.matcher(query).matches()) {
	    end_date = query.split("=")[1];
	    //System.out.println("end="+start_date);
	  }
	  if (signal_pat.matcher(query).matches()) {
	    signal = query.split("=")[1];
	    //System.out.println("signal="+signal);
	  }
	  if (format_pat.matcher(query).matches()) {
	    format = query.split("=")[1];
	    //System.out.println("format="+format);
	  }
	  if (filename_pat.matcher(query).matches()) {
	    filename = query.split("=")[1];
	    System.out.println("filename="+format);
	  }
	}
      } catch (Exception e) {
	e.printStackTrace();
	exchange.respond(ResponseCode.BAD_REQUEST, e.getMessage());
	return;
      }

      if ( results < 0) {
	exchange.respond(ResponseCode.BAD_REQUEST, "Illegal parameter");
	return;
      }

      System.out.println("results="+results);
      System.out.println("start="+start_date);
      System.out.println("end="+end_date);
      System.out.println("signal="+signal);
      System.out.println("format="+format);
      System.out.println("filename="+filename);

      /* handle database */
      try {
	Class.forName("com.mysql.jdbc.Driver");

	//STEP 3: Open a connection
	//System.out.println("Connecting to database...");
	conn = DriverManager.getConnection("jdbc:mysql://"+prop.getProperty("dburl", "localhost")+"/" + prop.getProperty("database"), prop.getProperty("dbuser"), prop.getProperty("dbpassword"));

	int devid = historian.macToID(this.mac, conn);
	if (devid != -1) { 

	  if (filename.equals("/")) {  // download directory listing
	    System.out.println("Directory listning: ");
	    res = historian.getFileListfromPeer(mac, devid, conn);
	    System.out.println("##\n"+res+"}\n##");
	    exchange.respond(ResponseCode.CONTENT, res, MediaTypeRegistry.TEXT_PLAIN);
	    return;

	  } else if (!filename.equals("")) { // download file
	    byte data[] = historian.getFilefromPeer(mac, devid, filename, conn);
	    if (data != null) {
	      int retcf = historian.getCfFromFilename(filename);
	      exchange.respond(ResponseCode.CONTENT, data, retcf);
	      return;
	    } else {
	      exchange.respond(ResponseCode.INTERNAL_SERVER_ERROR, "File not found");
	      return;
	    }

	  } else {
	    /* respond with newest message */
	    exchange.respond(ResponseCode.INTERNAL_SERVER_ERROR, "NOT IMPLEMENTED");

	  }


	}

      } catch(SQLException se) {
	db_ok = false;
	//Handle errors for JDBC
	se.printStackTrace();
      } catch(Exception e){
	//Handle errors for Class.forName
	e.printStackTrace();
      }

    }

    @Override
      public void handlePUT(CoapExchange exchange) {
	int validated = checkMasterKey(exchange);
	if (validated == 0 || validated == 1) {
	  System.out.println("DeviceStorage::PUT\n");
	} else {
	  System.out.println("DeviceStorage::PUT not validated\n");
	  exchange.respond(ResponseCode.UNAUTHORIZED, "Not validated", MediaTypeRegistry.TEXT_PLAIN);
	  return;
	}
	String content = exchange.getRequestText();

	Calendar cal = Calendar.getInstance();

	Pattern format_pat = Pattern.compile("format=\\S+");
	Pattern filename_pat = Pattern.compile("f=\\S+");
	String format="", filename="";
	String res;

	boolean db_store_ok = false, fs_store_ok = false;

	try {
	  List<String> queries = exchange.getRequestOptions().getUriQuery();
	  for (String query:queries) {
	    if (format_pat.matcher(query).matches()) {
	      format = query.split("=")[1];
	      //System.out.println("format=\""+format+"\"");
	    }
	    if (filename_pat.matcher(query).matches()) {
	      filename = query.split("=")[1];
	      //System.out.println("filename=\""+filename+"\"");
	    }
	  }
	} catch (Exception e) {
	  e.printStackTrace();
	  exchange.respond(ResponseCode.BAD_REQUEST, e.getMessage());
	  return;
	}

	System.out.println("format="+format);
	System.out.println("filename="+filename);

	CRC32 crc = new CRC32();
	crc.update(content.getBytes());
	long crc32value = crc.getValue(); // tested with https://www.tools4noobs.com/online_php_functions/crc32/


	/* check content format */
	int cf = -1;
	if (exchange.getRequestOptions().hasContentFormat()) {
	  cf = exchange.getRequestOptions().getContentFormat();
	} else {
	  //exchange.respond(BAD_REQUEST, "Content-Format not set");
	  //return;
	}
	System.out.println("cf="+cf);
	
	forwarder.addQueue(mac, filename, cf, content.getBytes(), crc32value);

	Connection conn = null;
	//Statement stmt = null;

	String bt = null;


	if (cf == 50 || content.toString().indexOf("{") != -1) { // JSON+SenML

	  JSONParser parser = new JSONParser();

	  try {
	    JSONArray json = (JSONArray)parser.parse(content);
	    for (Object object : json) {
	      JSONObject record= (JSONObject) object;
	      //if((String) record.get("bn") != null)  XXX: this is the old meaning of bn
		//mac = (String)record.get("bn");
	      if( record.get("bt") != null)
		bt= "" + record.get("bt");
	    }

	    if (bt == null){
	      cal = Calendar.getInstance();
	      bt = "" + (cal.getTimeInMillis() / 1000);
	    }

	    if ( enable_database ) {
	      try {
		Class.forName("com.mysql.jdbc.Driver");
		conn = DriverManager.getConnection("jdbc:mysql://"+prop.getProperty("dburl", "localhost")+"/" + prop.getProperty("database"), prop.getProperty("dbuser"), prop.getProperty("dbpassword"));
	      historian.insertSenML(mac, json, conn);
	    } catch(Exception se){
	      System.out.println("SQL error!");
	      conn = null;
	    }
	  }

	  if ( !filename.equals("") && enable_database && conn != null)
	    db_store_ok = historian.putFilefromPeer(mac, filename, cf, content.getBytes(), crc32value);

	      if ( !filename.equals("") && enable_local_fs )
		fs_store_ok = historian.storeFilefromPeer(mac, filename, content.getBytes());

	    } catch (ParseException pe){
	      System.out.println("JSON error: "+pe.toString());
	    }

	  } else if (cf == 41) { // XML
	    System.out.println("XMl::"+content);

	  } else if (cf == 2) { // CSV
	    //	  System.out.println("CSV::"+content);

	    StringTokenizer tok = new StringTokenizer(content, "\n");
	    int lineLen = 0, n=0;
	    while (tok.hasMoreTokens()) {
	      n++;
	      String s = tok.nextToken();
	      System.out.println("["+n+"]: "+s);
	    }

	    tok = new StringTokenizer(content, "\n");
	    double data[] = new double[n * 2];
	    int i=0;
	    while (tok.hasMoreTokens()) {
	      String line = tok.nextToken();
	      double value = Double.parseDouble(line);
	      data[i++] = value;

	      System.out.println(value);
	    }
	    System.out.println("FFT of "+ n+" samples");
	    DoubleFFT_1D fft = new DoubleFFT_1D(n);
	    fft.realForward(data);
	    double fftresult[] = new double[n];
	    for (i = 0; i < n; i += 2) {
	      data[i] = data[i]/n;
	      fftresult[i] = Math.sqrt(data[i] *data[i]) + Math.sqrt(data[i+1] * data[i+1]);
	      System.out.println(fftresult[i]);
	    }

	    /* save FFT data, perform analysis and alarm generation? Move to CalculationEngine.java */

	  } else {    // unknown content format

	  }

	  res = "[{\"bt\": "+(cal.getTimeInMillis() / 1000)+"},\n";
	  if ( !filename.equals("") && enable_database)
	    res += " {\"n\":\"db-store\",\"bv\":\""+db_store_ok+"\"},\n";
	  if ( !filename.equals("") && enable_local_fs )
	    res += " {\"n\":\"filesys-store\",\"bv\":\""+fs_store_ok+"\"},\n";
	  res += " {\"n\":\"CRC32\",\"sv\": \""+("0x"+Integer.toHexString((int)crc32value))+"\"}\n";
	  res += "]\n";


	  if (conn != null) {
	    try {
	      conn.close();
	    } catch (SQLException sqle) {
	    }
	  }

	  exchange.respond(ResponseCode.CONTENT, res, MediaTypeRegistry.APPLICATION_JSON);

	} //method

      } //class
  }//class
