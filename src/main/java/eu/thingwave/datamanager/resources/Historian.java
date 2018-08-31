/*
 * (C) Copyright 2016 ThingWave AB (https://www.thingwave.eu/).
 */
package eu.thingwave.datamanager.resources;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.Properties;
import java.util.Calendar;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.sql.*;

import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;

/* mail for alarms etc. */
import javax.mail.Message;
import javax.mail.BodyPart;
import javax.mail.Multipart;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;

import java.util.Iterator;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import org.jtransforms.fft.DoubleFFT_1D;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import eu.thingwave.datamanager.resources.StorageResource;


/**
 *
 * @author Jens Eliasson <jens.eliasson@thingwave.eu>
 * @author Pablo Puñal Pereira <pablo.punal@thingwave.eu>
 */
public class Historian {
  final String DEFAULT_ROOT = "/tmp/historian";
  Logger logger = null;

  /* MySQL stuff */
  boolean enable_database = false;
  String dburl = "localhost";
  String DB_USER = "";
  String DB_DATABASE = "";
  String DB_PASSWORD = "";
  String DB_PREFIX = "";

  String JDBC_DRIVER = "com.mysql.jdbc.Driver";  
  String DB_URL = "jdbc:mysql://localhost/"; 

  Properties prop;
  String hostname = null;

  public Historian(Properties prop) {
    this.prop = prop;

    logger = LogManager.getRootLogger();

    /* get hostname */
    try(BufferedReader br = new BufferedReader(new FileReader("/etc/hostname"))) {
      StringBuilder sb = new StringBuilder();
      String line = br.readLine();

      while (line != null) {
	sb.append(line);
	sb.append(System.lineSeparator());
	line = br.readLine();
      }
      hostname = sb.toString().trim();
    } catch (Exception all) {
      all.printStackTrace();
    }

    /* check if target folder exists, otherwise create it */
    if (prop.getProperty("enable-local-fs", "false").equals("true")) {
      String base_folder = prop.getProperty("root-folder", DEFAULT_ROOT);
      File file = new File(base_folder);
      if (file.mkdirs()) {
	logger.info("New root Directory created!");
      } else {
	logger.error("Failed to create root directory!");
      }
    }


    //String db, String dbuser, String dbpassword) 
    DB_URL = "jdbc:mysql://"+dburl+"/" + prop.getProperty("database");

    if (prop.getProperty("enable-database", "false").equals("true")) {
      enable_database = true;
    } else
      logger.info("Database support disabled");

    int debug_level = Integer.parseInt(prop.getProperty("debuglevel", "-1"));
    if (debug_level > 0 && enable_database) {
      //System.out.println(prop.getProperty("database"));
      //System.out.println(prop.getProperty("dbuser"));
      //System.out.println(prop.getProperty("dbpassword"));
      //System.out.println(prop.getProperty("dbprefix"));
    } 

    dburl = prop.getProperty("dburl", "127.0.0.1:3306");
    DB_USER = prop.getProperty("dbuser");
    DB_DATABASE = prop.getProperty("database");
    DB_PASSWORD = prop.getProperty("dbpassword");
    DB_PREFIX = prop.getProperty("dbprefix");

    if (enable_database) {
      Connection conn = null;
      try {
	Class.forName("com.mysql.jdbc.Driver");
	conn = DriverManager.getConnection("jdbc:mysql://"+prop.getProperty("dburl", "localhost")+"/" + prop.getProperty("database"), prop.getProperty("dbuser"), prop.getProperty("dbpassword"));
	logger.info("Connected to MySQL database");
	checkTables(conn, DB_DATABASE);
	conn.close();
      } catch(SQLException se){
	logger.error("Failed to make connection!");
	se.printStackTrace();
      } catch(Exception e){
	logger.error("Failed to make connection!");
	e.printStackTrace();
      }
    }

  }


  /**
   * \fn public int macToID(String mac, Connection conn)
   *
   */
  public int macToID(String mac, Connection conn) {
    int id=-1;

    //System.out.println("macToID('"+mac+"')");
    Statement stmt = null;
    try {
      Class.forName("com.mysql.jdbc.Driver");

      stmt = conn.createStatement();
      String sql;
      sql = "SELECT id FROM iot_devices WHERE hwaddr='"+mac+"';";
      ResultSet rs = stmt.executeQuery(sql);

      rs.next();
      id  = rs.getInt("id");

      rs.close();
      stmt.close();
    }catch(SQLException se){
      id = -1;
      //se.printStackTrace();
    }catch(Exception e){
      id = -1;
      e.printStackTrace();
    }

    //System.out.println("macToID('"+mac+"')="+id);
    return id;
  }


  public String generateErrorMessage(int id) {
    Calendar cal = Calendar.getInstance();

    JSONArray reslist = new JSONArray();
    JSONObject obj = new JSONObject();
    obj.put("bn", prop.getProperty("sysName",""));
    obj.put("bt", (cal.getTimeInMillis()/1000));
    reslist.add(obj);

    switch (id) {
      case 0:
	obj = new JSONObject();
	obj.put("n", "cause");
	obj.put("sv", "Internal database error");
	reslist.add(obj);
	break;
      case 1:
	obj = new JSONObject();
	obj.put("n", "cause");
	obj.put("sv", "Unknown system");
	reslist.add(obj);
	break;
      case 20:
	obj = new JSONObject();
	obj.put("n", "cause");
	obj.put("sv", "Bad origin");
	reslist.add(obj);
	break;
      case 100:
	obj = new JSONObject();
	obj.put("n", "cause");
	obj.put("sv", "Illegal content-type");
	reslist.add(obj);
	break;
      case 101:
	obj = new JSONObject();
	obj.put("n", "cause");
	obj.put("sv", "Illegal semantics");
	reslist.add(obj);
	break;
      case 102:
	obj = new JSONObject();
	obj.put("n", "cause");
	obj.put("sv", "Empty document");
	reslist.add(obj);
	break;
      case 103:
	obj = new JSONObject();
	obj.put("n", "cause");
	obj.put("sv", "Illegal filename");
	reslist.add(obj);
	break;
      default:
	obj = new JSONObject();
	obj.put("n", "cause");
	obj.put("sv", "Unknown error");
	reslist.add(obj);
    }

    return reslist.toString();
  }



  synchronized public String getFileListfromPeer(String hwaddr, int id, Connection conn) {
    Statement stmt = null;
    String res = "";

    if ( id == -1 ) {
      return generateErrorMessage(1);
    }

    try {

      stmt = conn.createStatement();
      String sql = "SELECT id, stored, filename, len, crc32 FROM iot_files WHERE did="+id /*+" AND t > UNIX_TIMESTAMP('"+start+"') AND t < UNIX_TIMESTAMP('"+stop+"') ORDER BY t DESC LIMIT "+number */+ ";";
      System.out.println("SQL: "+sql);

      ResultSet rs = stmt.executeQuery(sql);

      while(rs.next()){	
	int _id  = rs.getInt("id");
	String stored = rs.getString("stored");
	String filename = rs.getString("filename");
	int len  = rs.getInt("len");
	long crc32  = rs.getInt("crc32");

	res += (_id + ", "+stored + ", \""+ filename + "\", "+len+", 0x"+ Long.toHexString(crc32)+"\n");
      }
      rs.close();
      stmt.close();

    } catch(SQLException se){
      se.printStackTrace();
      res = generateErrorMessage(0);
    } catch(Exception e){
      //Handle errors for Class.forName
      e.printStackTrace();
      res = generateErrorMessage(-1);
    }
    return res;
  }


  /**
   * @fn
   *
   */
  public String getLastMessagefromPeer(String hwaddr, String content_type) {
    Connection conn = null;
    Statement stmt = null;
    boolean db_ok = true;
    String res = "";
    JSONArray reslist = new JSONArray();

    try {
      Class.forName("com.mysql.jdbc.Driver");
      conn = DriverManager.getConnection("jdbc:mysql://"+prop.getProperty("dburl", "localhost")+"/" + prop.getProperty("database"), prop.getProperty("dbuser"), prop.getProperty("dbpassword"));

      int id = macToID(hwaddr, conn);
      if ( id == -1 ) {
	return generateErrorMessage(1);
      }

      stmt = conn.createStatement();
      String sql = "SELECT * FROM iot_messages WHERE did="+id+" ORDER BY ts DESC LIMIT 1;";
      ResultSet rs = stmt.executeQuery(sql);

      rs.next();
      int _id  = rs.getInt("id");
      int ts = rs.getInt("ts");
      String stored = rs.getString("stored");
      rs.close();

      res  = "[\n";
      res += "  {\"bn\": \""+hwaddr+"\", \"bt\": "+ts+"}";
      JSONObject obj = new JSONObject();
      obj.put("bn", hwaddr);
      obj.put("bt", ts);
      reslist.add(obj);

      sql = "SELECT * FROM iot_entries WHERE did="+id+" AND mid="+_id+";";
      rs = stmt.executeQuery(sql);
      while (rs.next()) {

	obj = new JSONObject();
	obj.put("n", rs.getString("n"));

	rs.getInt("t");
	if (!rs.wasNull()){
	  if ((rs.getInt("t") - ts) != 0)
	    obj.put("t", rs.getInt("t") - ts);
	}

	rs.getString("u");
	if (!rs.wasNull()) {
	  obj.put("u", rs.getString("u"));
	}

	rs.getString("sv");
	if (!rs.wasNull()) {
	  obj.put("sv", rs.getString("sv"));
	} else {
	  rs.getDouble("v");
	  if (!rs.wasNull()) {
	    obj.put("v", rs.getDouble("v"));
	  } else {
	    rs.getBoolean("bv");
	    if (!rs.wasNull()) {
	      obj.put("bv", rs.getBoolean("bv"));
	    } 
	  }
	}
	reslist.add(obj);
      }


      rs.close();
      stmt.close();
      conn.close();

    } catch(SQLException se){
      se.printStackTrace();
      return generateErrorMessage(0);
    } catch(Exception e){
      //Handle errors for Class.forName
      e.printStackTrace();
      return generateErrorMessage(-1);
    }

    return reslist.toString();
  }


  public String getCSVDatafromPeer(String hwaddr, int results, String[] signals, String[] conditions, String start, String stop) {
    Connection conn = null;
    Statement stmt = null;
    boolean db_ok = true;
    String res = "";

    try {
      Class.forName("com.mysql.jdbc.Driver");
      conn = DriverManager.getConnection("jdbc:mysql://"+prop.getProperty("dburl", "localhost")+"/" + prop.getProperty("database"), prop.getProperty("dbuser"), prop.getProperty("dbpassword"));

      int id = macToID(hwaddr, conn);
      if ( id == -1 ) {
	return generateErrorMessage(1);
      }

      stmt = conn.createStatement();
      String sql = "SELECT id, ts FROM iot_messages WHERE did="+id+" AND ts > UNIX_TIMESTAMP('"+start+"') AND ts < UNIX_TIMESTAMP('"+stop+"') ORDER BY ts DESC LIMIT "+results+";";
      //System.out.println("SQL: "+sql);
      ResultSet rsm = stmt.executeQuery(sql);

      /* generate CSV message */
      res = "t, n, v, u\n";
      /*res = "% @Generated-by: ThingWave historian\n";
	res += "% @Generated-at: 2017-01-09T13:04:49\n";
	res += "% @bn: "+hwaddr+"\n";
	res += "% @Signals: t, n, v, u\n";*/

      int mid;
      while(rsm.next() && results > 0){
	mid = rsm.getInt("id");
	int bt = rsm.getInt("ts");
	//System.out.println("Message id (mid): "+mid);

	stmt = conn.createStatement();
	if (signals.length == 0) {
	  sql = "SELECT * FROM iot_entries WHERE did="+id+" AND mid="+mid+" ORDER BY t DESC LIMIT " + results + ";";
	} else {
	  StringBuilder sb = new StringBuilder();
	  for (int i = 0; i < signals.length - 1; i++) {
	    sb.append("'"+signals[i]+"'");
	    sb.append(", ");
	  }
	  sb.append("'"+signals[signals.length - 1].trim()+"'");
	  String signalcond = sb.toString();
	  sql = "SELECT * FROM iot_entries WHERE did="+id+" AND mid="+mid+" AND n IN ("+signalcond+") ORDER BY t DESC LIMIT " + results + ";";
	}
	//System.out.println("SQL: "+sql);

	ResultSet rs = stmt.executeQuery(sql);

	while(rs.next() && results > 0){	
	  int _id  = rs.getInt("id");
	  int t  = rs.getInt("t");
	  String _dev = rs.getString("did");
	  String n = rs.getString("n");
	  String _unit = rs.getString("u");
	  String _value = rs.getString("v");

	  res += (t)+", "+ n + ", "+_value+", "+_unit+"\n";
	  results--;
	}
	rs.close();
      }
      rsm.close();

      stmt.close();
      conn.close();

    } catch(SQLException se){
      se.printStackTrace();
      res = generateErrorMessage(0);
    } catch(Exception e){
      //Handle errors for Class.forName
      e.printStackTrace();
      res = generateErrorMessage(-1);
    }

    return res;
  }


  /**
   * \fn public String getSenMLDatafromPeer(String hwaddr, String format, int results, String[] channels, String[] conditions, String start, String stop)
   * \brief Returns JSON or XML encoded SenML
   *
   */
  public String getSenMLDatafromPeer(String hwaddr, String format, int results, String[] channels, String[] conditions, String start, String stop) {
    Connection conn = null;
    Statement stmt = null;
    boolean db_ok = true;
    String res = "";

    try {
      Class.forName("com.mysql.jdbc.Driver");
      conn = DriverManager.getConnection("jdbc:mysql://"+prop.getProperty("dburl", "localhost")+"/" + prop.getProperty("database"), prop.getProperty("dbuser"), prop.getProperty("dbpassword"));

      int id = macToID(hwaddr, conn);
      if ( id == -1 ) {
	return generateErrorMessage(1);
      }

      stmt = conn.createStatement();
      String sql = "SELECT id, ts FROM iot_messages WHERE did="+id+" AND ts > UNIX_TIMESTAMP('"+start+"') AND ts < UNIX_TIMESTAMP('"+stop+"') ORDER BY ts DESC LIMIT "+results+";";
      //System.out.println("SQL: "+sql);
      ResultSet rsm = stmt.executeQuery(sql);

      /* generate XML message */
      boolean header_sent = false;

      //BUG use XML and JSON serializers instead
      long BT = -1;
      int mid;

      DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
      DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
      Document doc = docBuilder.newDocument();
      doc.setXmlStandalone(true);
      Element rootElement = null;
      JSONArray reslist = new JSONArray();
      JSONObject obj;
      while(rsm.next() && results > 0){	
	mid = rsm.getInt("id");
	int bt = rsm.getInt("ts");
	//System.out.println("Message id (mid): "+mid);

	if (header_sent == false) {
	  header_sent = true;
	  if (format.equals("xml")) {
	    //res = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
	    //res += "<senml xmlns=\"urn:ietf:params:xml:ns:senml\" bn=\""+hwaddr+"\"\n\tbt=\""+bt+"\" ver=\"1\">\n";
	    rootElement = doc.createElement("sensml");
	    doc.appendChild(rootElement);
	    Attr attr = doc.createAttribute("xmlns");
	    attr.setValue("urn:ietf:params:xml:ns:senml");
	    rootElement.setAttributeNode(attr);

	    Element etag = doc.createElement("senml");
	    rootElement.appendChild(etag);
	    attr = doc.createAttribute("bn");
	    attr.setValue(hwaddr);
	    etag.setAttributeNode(attr);
	    attr = doc.createAttribute("bt");
	    attr.setValue(bt + "");
	    etag.setAttributeNode(attr);

	    BT = bt;
	  } else if (format.equals("json")) {
	    //res = "[{\"bn\": \""+hwaddr+"\", \"bt\": "+bt+",\n";
	    obj = new JSONObject();

	    BT = bt;
	    //res += "  \"bver\": 5},\n";
	    obj.put("bn", hwaddr);
	    obj.put("bt", bt);
	    obj.put("bver", 5);
	    reslist.add(obj);
	  }
	}

	stmt = conn.createStatement();
	sql = "SELECT * FROM iot_entries WHERE did="+id+" AND mid="+mid+" ORDER BY t DESC LIMIT "+results+";";
	//System.out.println("SQL: "+sql);

	ResultSet rs = stmt.executeQuery(sql);

	while(rs.next() && results > 0){	
	  int _id  = rs.getInt("id");
	  long t  = rs.getInt("t");
	  String _dev = rs.getString("did");
	  String n = rs.getString("n");
	  String _unit = rs.getString("u");
	  String _value = rs.getString("v");

	  if (format.equals("xml")) {
	    //res += "  <e n=\""+n+"\" u=\""+_unit+"\" t=\""+(t-BT)+"\" v=\""+_value+"\"/>\n";
	    Element etag = doc.createElement("senml");
	    rootElement.appendChild(etag);
	    Attr attr = doc.createAttribute("n");
	    attr.setValue(n);
	    etag.setAttributeNode(attr);
	    attr = doc.createAttribute("t");
	    attr.setValue("" + (t-BT));
	    etag.setAttributeNode(attr);
	    attr = doc.createAttribute("v");
	    attr.setValue(_value);
	    etag.setAttributeNode(attr);
	    attr = doc.createAttribute("u");
	    attr.setValue(_unit);
	    etag.setAttributeNode(attr);
	  } else if (format.equals("json")) {
	    //res += "  { \"n\": \""+n+"\", \"u\": \""+_unit+"\", \"t\": "+(t-BT)+",\"v\": "+_value +" },\n";
	    obj = new JSONObject();
	    obj.put("n", n);
	    obj.put("u", _unit);
	    obj.put("t", t-BT);
	    obj.put("v", _value);
	    reslist.add(obj);
	  }
	  results--;
	}
	rs.close();
      }
      rsm.close();

      if (format.equals("xml")) {
	//res += "</senml>";
	DOMSource domSource = new DOMSource(doc);
	StringWriter writer = new StringWriter();
	StreamResult result = new StreamResult(writer);
	TransformerFactory tf = TransformerFactory.newInstance();
	Transformer transformer = tf.newTransformer();
	transformer.transform(domSource, result);
	res = writer.toString();
	//res = doc.toString();
      } else if (format.equals("json")) {
	res = reslist.toString().replace("\\/", "/"); //change stupid JSON serialization characters
      }

      stmt.close();
      conn.close();

    } catch(SQLException se){
      se.printStackTrace();
      return generateErrorMessage(0);
    } catch(Exception e){
      //Handle errors for Class.forName
      e.printStackTrace();
      return generateErrorMessage(-1);
    }

    return res;
  }

  /**
   * \fn public byte[] getExcelDatafromPeer(String hwaddr, int results, String[] channels, String[] conditions, String start, String stop)
   * \brief Returns Excel encoded data
   *
   */
  public byte[] getExcelDatafromPeer(String hwaddr, int results, String[] channels, String[] conditions, String start, String stop) {
    Connection conn = null;
    Statement stmt = null;
    boolean db_ok = true;
    String res = "";

    XSSFWorkbook workbook = new XSSFWorkbook();
    XSSFSheet sheet = workbook.createSheet("Sheet1");
    logger.info("Generating Excel file");

    try {
      Class.forName("com.mysql.jdbc.Driver");
      conn = DriverManager.getConnection("jdbc:mysql://"+prop.getProperty("dburl", "localhost")+"/" + prop.getProperty("database"), prop.getProperty("dbuser"), prop.getProperty("dbpassword"));

      int id = macToID(hwaddr, conn);
      if ( id == -1 ) {
	return null; //generateErrorMessage(1); throw Ex
      }

      stmt = conn.createStatement();
      String sql = "SELECT id, ts FROM iot_messages WHERE did="+id+" AND ts > UNIX_TIMESTAMP('"+start+"') AND ts < UNIX_TIMESTAMP('"+stop+"') ORDER BY ts DESC LIMIT "+results+";";
      System.out.println("SQL: "+sql);
      ResultSet rsm = stmt.executeQuery(sql);

      int mid;
      int maxrows=0;
      int current_column = 0;

      Row row = sheet.createRow(0);
      Cell cell = row.createCell(current_column);
      cell.setCellValue((String) "t");
      cell = row.createCell(current_column + 1);
      cell.setCellValue((String) "n");
      cell = row.createCell(current_column + 2);
      cell.setCellValue((String) "v");
      cell = row.createCell(current_column + 3);
      cell.setCellValue((String) "u");


      int rowid = 1;
      while(rsm.next() && results > 0) {
	mid = rsm.getInt("id");
	int bt = rsm.getInt("ts");
	System.out.println("Message id (mid): "+mid);

	stmt = conn.createStatement();
	sql = "SELECT * FROM iot_entries WHERE did="+id+" AND mid="+mid+" ORDER BY t DESC LIMIT "+results+";";
	System.out.println("SQL: "+sql);

	ResultSet rs = stmt.executeQuery(sql);

	while(rs.next()){	
	  int _id  = rs.getInt("id");
	  long t  = rs.getInt("t");
	  String _dev = rs.getString("did");
	  String n = rs.getString("n");
	  String _unit = rs.getString("u");
	  String _value = rs.getString("v");

	  //Row row = null;
	  if (rowid >= maxrows) {
	    row = sheet.createRow(rowid);
	    maxrows = rowid;
	  } else {
	    row = sheet.getRow(rowid);
	  }
	  System.out.println("Row: "+rowid);
	  cell = row.createCell(current_column + 0);
	  cell.setCellValue((Long) t);
	  cell = row.createCell(current_column + 1);
	  cell.setCellValue((String) n);
	  cell = row.createCell(current_column + 2);
	  cell.setCellValue((String) _value);
	  cell = row.createCell(current_column + 3);
	  cell.setCellValue((String) _unit);

	  rowid++;
	}

      }
      current_column += 4;

    } catch(SQLException se){
      se.printStackTrace();
      return null; //generateErrorMessage(0); throw exception instead
    } catch(Exception e){
      //Handle errors for Class.forName
      e.printStackTrace();
      return null; //generateErrorMessage(-1);
    }




    try {
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      //bos.write(something);
      workbook.write(bos);
      workbook.close();
      bos.close();
      byte[] arr = bos.toByteArray();
      return arr;
    } catch (IOException ioe) {
      System.err.println("Excel error: "+ioe.toString());
      return null;
    }
  }


  synchronized public byte[] getFilefromPeer(String hwaddr, int id, String filename, Connection conn) {
    Statement stmt = null;
    //    boolean db_ok = true;
    byte[] content = null;
    //    String res = null;

    if ( id == -1 ) {
      return null; //generateErrorMessage(1);
    }

    try {
      stmt = conn.createStatement();
      String sql = "SELECT * FROM iot_files WHERE did="+id+" AND filename=\""+filename+"\";";
      System.out.println("SQL: "+sql);

      ResultSet rs = stmt.executeQuery(sql);
      rs.next();	

      Blob blob = rs.getBlob("content");
      content = blob.getBytes(1, (int) blob.length());

      rs.close();
      stmt.close();

    } catch(SQLException se){
      //res = generateErrorMessage(0);
      //se.printStackTrace();
    } catch(Exception e){
      e.printStackTrace();
      //res = generateErrorMessage(-1);
    }

    return content;
  }


  /**
   *
   *
   */
  public String getHistorianWADL() {
    String content = null;
    File file = new File(prop.getProperty("WADL-file", ""));
    try {
      FileReader reader = new FileReader(file);
      char[] chars = new char[(int) file.length()];
      reader.read(chars);
      content = new String(chars);
      reader.close();
    } catch (IOException e) {
      e.printStackTrace();
      return null;
    }
    return content;
  }

  public int getCfFromFilename(String filename) {
    String extension = filename;

    int i = filename.lastIndexOf('.');
    if (i > 0) {
      extension = filename.substring(i);
    }

    switch (extension) {
      case ".txt":
	return MediaTypeRegistry.TEXT_PLAIN;
      case ".json":
	return MediaTypeRegistry.APPLICATION_JSON;
      case ".xml":
	return MediaTypeRegistry.APPLICATION_XML;
      case ".cbor":
	return MediaTypeRegistry.APPLICATION_CBOR;
      default:
	return MediaTypeRegistry.APPLICATION_OCTET_STREAM;
    }
  }


  private String getIPSOSmartObjectsUnit(String n) {
    if (n == null)
      return "";

    if (n.equals(""))
      return "";

    if (n.startsWith("3303/")) {
      return "Cel";
    }

    return ""; 
  }

  /**
   * @fn boolean insertSenML(String mac, JSONArray json, Connection conn)
   *
   */
  public boolean insertSenML(String mac, JSONArray json, Connection conn) {
    //System.out.println("insertSenML::");
    //String mac = null; 
    String bn = null;
    long bt = 0; 
    String bu = null; 
    String sql;
    Calendar cal = Calendar.getInstance();
    bt = cal.getTimeInMillis()/1000;

    for (Object object : json) {
      JSONObject record = (JSONObject) object;
      if((String) record.get("bn") != null) // Is this OK????? security risk!
	mac = (String)record.get("bn");
      if( record.get("bt") != null)
	bt = Integer.parseInt(record.get("bt").toString());
      if( record.get("bu") != null)
	bu= (String)record.get("bu");
      if( record.get("bn") != null)
	bn= (String)record.get("bn");
    }

    /* check for false injections XXX to be supported i.e. UUID, on-behalf-of, etc. */
    if (bn != null) {
      if (!bn.equals(mac)) {
	logger.warn("insertSenML:: Injection attempt");
	return false;
      }
    }

    /*if ( bt == null) {
      Calendar cal = Calendar.getInstance();
      bt = (cal.getTimeInMillis() / 1000);
      } else if (bt.equals("")) {
      Calendar cal = Calendar.getInstance();
      bt = cal.getTimeInMillis()/1000;
      }*/

    try {

      int id = macToID(mac, conn);
      if ( id == -1 ) {
	return false; //generateErrorMessage(1);
      }

      Statement stmt = conn.createStatement();
      sql = "INSERT INTO iot_messages(did, ts, stored) VALUES("+id+", "+bt+", NOW());";
      //System.out.println(sql);
      int mid = stmt.executeUpdate(sql, Statement.RETURN_GENERATED_KEYS);
      ResultSet rs = stmt.getGeneratedKeys();
      rs.next();
      mid = rs.getInt(1);
      rs.close();
      //System.out.println(mid + "= "+sql);

      /* extracte very sensor value in e-tags*/
      for (Object object : json) {
	JSONObject record= (JSONObject) object;
	if((String)record.get("n") != null){
	  String v = null;
	  String vs = null;
	  String u = null;
	  Boolean vb = null;
	  byte[] vd = null;
	  long t = bt; //XXX: should be double!

	  if(record.get("t") != null) {
	    try {
	      t = bt+Integer.parseInt(record.get("t").toString());
	      //System.out.println("'t': " + t);
	    } catch(NumberFormatException e) {
	      // what to do?
	    }
	  }
	  if(record.get("u") != null) {
	    u = record.get("u").toString();
	  } else {
	    u = bu;
	  }
	  if (bu == null && u == null) {
	    u = this.getIPSOSmartObjectsUnit(record.get("n").toString());
	  }

	  if(record.get("v") != null) {
	    v = record.get("v").toString();
	  }
	  if(record.get("vs") != null) {
	    vs = record.get("vs").toString();
	  }

	  if (v != null) {
	    if (u != null)
	      sql = "INSERT INTO iot_entries(did, mid, n, t, u, v) VALUES("+id+", "+mid+", \""+record.get("n")+"\", "+t+", \""+u+"\", "+v+");";
	    else
	      sql = "INSERT INTO iot_entries(did, mid, n, t, v) VALUES("+id+", "+mid+", \""+record.get("n")+"\", "+t+", "+v+");";
	  } 
	  if (vs != null) {
	    if (u != null)
	      sql = "INSERT INTO iot_entries(did, mid, n, t, u, vs) VALUES("+id+", "+mid+", \""+record.get("n")+"\", "+t+", \""+u+"\", \""+vs+"\");";
	    else
	      sql = "INSERT INTO iot_entries(did, mid, n, t, vs) VALUES("+id+", "+mid+", \""+record.get("n")+"\", "+t+", \""+vs+"\");";
	  }
	  //System.out.println(sql);
	  stmt.execute(sql);
	}

	//NOT COMPLETED YET! EXTRACT AND STORE EVERY FIELD
      }
      stmt.close();

    } catch(SQLException se){
      se.printStackTrace();
      return false;
    }

    return true;
  }



  public int checkTables(Connection conn, String database) {

    if ( enable_database == false)
      return -1;

    String sql = "CREATE DATABASE IF NOT EXISTS "+database;
    try {
      Statement stmt = conn.createStatement();
      stmt.execute(sql);
    } catch(SQLException se){
      return -1;
    }

    sql = "CREATE TABLE IF NOT EXISTS iot_devices (\n" 
      + "id INT NOT NULL PRIMARY KEY AUTO_INCREMENT,\n" 
      + "hwaddr varchar(64),\n" 
      + "name varchar(64) NOT NULL UNIQUE,\n" 
      + "alias varchar(64),\n" 
      + "last_update datetime" 
      + ")\n";

    try {
      Statement stmt = conn.createStatement();
      stmt.execute(sql);
    } catch(SQLException se){
      return -1;
    }

    sql = "CREATE TABLE IF NOT EXISTS iot_files (\n"
      + "id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,\n"
      + "did INT NOT NULL,\n"
      + "fid INT,\n"
      + "stored datetime NOT NULL,\n"
      + "cf int,\n"
      + "content blob,\n"
      +" filename varchar(64) NOT NULL,\n"
      + "len int,\n"
      + "crc32 int,\n"
      + "FOREIGN KEY(did) REFERENCES iot_devices(id) ON DELETE CASCADE"
      + ")\n";

    try {
      Statement stmt = conn.createStatement();
      stmt.execute(sql);
    } catch(SQLException se){
      return -2;
    }

    sql = "CREATE TABLE IF NOT EXISTS iot_messages (\n"
      + "id INT NOT NULL PRIMARY KEY AUTO_INCREMENT,\n"
      + "did INT(8) NOT NULL,\n"
      + "ts BIGINT UNSIGNED NOT NULL,\n"
      + "stored datetime,\n"
      + "FOREIGN KEY(did) REFERENCES iot_devices(id) ON DELETE CASCADE"
      + ")\n";

    try {
      Statement stmt = conn.createStatement();
      stmt.execute(sql);
    } catch(SQLException se){
      se.printStackTrace();
      return -2;
    }

    sql = "CREATE TABLE IF NOT EXISTS iot_entries (\n"
      + "id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,\n"
      + "did INT NOT NULL,\n"
      + "mid INT NOT NULL,\n"
      + "n varchar(32) NOT NULL,\n"
      + "t BIGINT UNSIGNED NOT NULL,\n"
      + "u varchar(32) NOT NULL,\n"
      + "v  DOUBLE,\n"
      + "sv varchar(32),\n"
      + "bv BOOLEAN,\n"
      + "FOREIGN KEY(did) REFERENCES iot_devices(id) ON DELETE CASCADE,\n"
      + "FOREIGN KEY(mid) REFERENCES iot_messages(id) ON DELETE CASCADE"
      + ")\n";

    try {
      Statement stmt = conn.createStatement();
      stmt.execute(sql);
      stmt.close();
    } catch(SQLException se){
      se.printStackTrace();
      return -2;
    }

    return 0;
  }


  /** Common TW methods. */
  public String getVersion() {
    return "1.0.0"; // TODO: improve this
  }

  public String getSoftwareBuild() {
    String build = "error";
    try {
      Properties prop = new Properties();
      InputStream input = new FileInputStream("buildNumber.properties");
      prop.load(input);
      build = prop.getProperty("buildNumber");
    } catch (IOException ex) {
      //LOG.error("reading buildNumber: "+ex.getMessage());
    }
    return build;
  }

  public String getSoftwareBuildDate() {
    try {
      // Construct BufferedReader from FileReader
      BufferedReader br = new BufferedReader(new FileReader(new File("buildNumber.properties")));
      String line;
      int i = 0;
      while ((line = br.readLine()) != null) {
	if (i==1)
	  return line.substring(1);
	i++;
      }
      br.close();
    } catch (IOException ex) {
      //LOG.error("reading buildDate: "+ex.getMessage());
    }
    return "error";
  }



  public String getContentType(String filename) {

    if (filename.endsWith(".txt"))
      return "text/plain";
    if (filename.endsWith(".html") || filename.endsWith(".htm"))
      return "text/html";
    if (filename.endsWith(".js"))
      return "application/javascript";
    if (filename.endsWith(".css"))
      return "text/css";

    if (filename.endsWith(".pdf"))
      return "application/pdf";
    if (filename.endsWith(".json"))
      return "application/json";
    if (filename.endsWith(".xlm"))
      return "application/xml";

    if (filename.endsWith(".csv"))
      return "text/csv";
    if (filename.endsWith(".xls"))
      return "application/vnd.ms-excel";
    if (filename.endsWith(".mat"))
      return "application/x-matlab-dat";

    if (filename.endsWith(".zip"))
      return "application/zip";
    if (filename.endsWith(".bz"))
      return "application/x-bzip";
    if (filename.endsWith(".bz2"))
      return "application/x-bzip2";

    /* no match, return default (unknown) */
    return "application/octet-stream";
  }


  /**
   * 
   * \brief saves a file to the local filesystem
   */
  public boolean storeFilefromPeer(String hwaddr, String filename, byte[] content) {
    //System.out.println("Storing file '"+filename+"' of length "+content.length+" from "+hwaddr + " to filesystem\n\t"+prop.getProperty("root-folder", "/tmp")+"/"+hwaddr+"/"+filename);

    File file = null;

    /* check if target folder exists, otherwise create it */
    if (prop.getProperty("enable-local-fs", "false").equals("true")) {
      String base_folder = prop.getProperty("root-folder", DEFAULT_ROOT);
      file = new File(base_folder+"/"+hwaddr);
      if (!file.exists() ) {
	if (file.mkdirs()) {
	  logger.info("New device directory created!");
	} else {
	  logger.error("Failed to create device directory! (\""+(base_folder+"/"+hwaddr)+"\")");
	  return false;
	}
      }
    }

    file = new File(prop.getProperty("root-folder", DEFAULT_ROOT)+"/"+hwaddr+"/"+filename);

    try (FileOutputStream fop = new FileOutputStream(file)) {

      if (!file.exists()) {
	file.createNewFile();
      }

      fop.write(content);
      fop.close();

    } catch (IOException e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }


  /**
   * \n synchronized public void putFilefromPeer(String hwaddr, String filename, int cf, byte[] content, long crc32value)
   * \brief stores a file to the database
   * 
   */
  public boolean putFilefromPeer(String hwaddr, String filename, int cf, byte[] content, long crc32value) {
    boolean ret = false;
    String sql;
    //System.out.println("Storing file '"+filename+"' with cf="+cf+" of length "+content.length+" from "+hwaddr + " in database");

    Connection conn = null;
    Statement qstmt = null;
    PreparedStatement stmt = null;
    boolean db_ok = true;
    String res = "";
    //HistRes ret = null;

    try {
      Class.forName("com.mysql.jdbc.Driver");

      //STEP 3: Open a connection
      conn = DriverManager.getConnection("jdbc:mysql://"+prop.getProperty("dburl", "localhost")+"/" + prop.getProperty("database"), prop.getProperty("dbuser"), prop.getProperty("dbpassword"));

      int id = macToID(hwaddr, conn);
      if ( id == -1 ) {
	//ret = new HistRes(1, generateErrorMessage(1));
	return ret;
      }


      /* check if file already exists */
      int no_files=0;
      qstmt = conn.createStatement();
      sql = "SELECT count(id) FROM iot_files WHERE did="+id+" AND filename=\""+filename+"\"";
      ResultSet rs = qstmt.executeQuery(sql);
      rs.next();

      no_files = rs.getInt("count(id)");
      qstmt.close();

      /* file already exists, create new file name */
      if (no_files != 0) {
	int filenameid = 0;
	String ext = "";
	String name = "";
	String[] parts = filename.split("\\.(?=[^\\.]+$)");
	name = parts[0];
	ext = parts[1];
	String filenamecopy = filename;

	do {
	  filenamecopy = name + " (Copy "+filenameid+")."+ext;
	  qstmt = conn.createStatement();
	  sql = "SELECT count(id) FROM iot_files WHERE did="+id+" AND filename=\""+filenamecopy+"\"";
	  rs = qstmt.executeQuery(sql);
	  rs.next();

	  no_files = rs.getInt("count(id)");
	  //System.out.println("Number of files already in system is "+no_files);
	  qstmt.close();
	  filenameid++;
	} while (no_files != 0);
	filename = filenamecopy;
	//System.out.println("New filename: '"+filename+"'");
      }

      /* add file */
      sql = "INSERT INTO iot_files(did, fid, stored, cf, content, filename, len, crc32) VALUES(?, ?, NOW(), ?, ?, ?, ?, ?)";
      stmt = conn.prepareStatement(sql);
      stmt.setInt(1, id);
      stmt.setInt(2, -1);
      stmt.setInt(3, cf);
      stmt.setBytes(4, content);
      stmt.setString(5, filename);
      stmt.setInt(6, content.length);
      stmt.setLong(7, crc32value);
      //System.out.println("SQL: "+sql);
      stmt.executeUpdate();

      stmt.close();
      conn.close();
      ret = true;

    } catch(SQLException se){
      se.printStackTrace();
      res = generateErrorMessage(0);
      ret = false;
    } catch(Exception e){
      //Handle errors for Class.forName
      e.printStackTrace();
      res = generateErrorMessage(-1);
      ret = false;
    }

    return ret;
  } 

}
