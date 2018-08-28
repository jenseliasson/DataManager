/*
 * (C) Copyright 2018 ThingWave AB (https://www.thingwave.eu/).
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

public class StatusHTTPResource extends HttpServlet
{
  Properties prop = null;
  boolean srok = false, drok = false, orchok=false;


  public StatusHTTPResource(Properties prop){
    this.prop = prop;
  }


  public void setStatus(String name, boolean status){

    System.out.println("setStatus: "+name+ " to "+status);

    if (name.equals("SR"))
      srok = status;
    if (name.equals("DR"))
      drok = status;
    if (name.equals("ORCH"))
      orchok = status;
  }


  /**
   *
   *
   */
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
  {
    JSONArray reslist = new JSONArray();

    if (!prop.getProperty("ahf-sr", "").equals("")) {
      JSONObject obj = new JSONObject();
      obj.put("name", "_ahf._sr.tcp.xxx");
      obj.put("type", "ServiceRegistry");
      obj.put("URI", prop.getProperty("ahf-sr", ""));
      obj.put("status", srok);
      reslist.add(obj);
    }
    if (!prop.getProperty("ahf-dr", "").equals("")) {
      JSONObject obj = new JSONObject();
      obj.put("name", "_ahf._dr.tcp.xxx");
      obj.put("type", "DeviceRegistry");
      obj.put("URI", prop.getProperty("ahf-dr", ""));
      obj.put("status", drok);
      reslist.add(obj);
    }
    if (!prop.getProperty("ahf-orch", "").equals("")) {
      JSONObject obj = new JSONObject();
      obj.put("name", "_ahf._orch.tcp.xxx");
      obj.put("type", "Orchestration");
      obj.put("URI", prop.getProperty("ahf-orch", ""));
      obj.put("status", orchok);
      reslist.add(obj);
    } 

    response.getWriter().println(reslist);

    return;
  }

}

