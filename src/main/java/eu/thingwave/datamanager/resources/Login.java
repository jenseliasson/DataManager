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

import java.io.*;
import java.io.InputStream;
import javax.servlet.*;
import javax.servlet.http.*;

import org.json.simple.JSONObject;
import org.json.simple.JSONArray;
import org.json.simple.parser.ParseException;
import org.json.simple.parser.JSONParser;

import java.util.regex.Pattern;

public class Login extends HttpServlet
{
  Properties prop = null;

  public Login(Properties prop){
    this.prop = prop;
  }



  /**
    *
    *
    */
  protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
  {
    System.out.println("\nPath: "+ request.getRequestURI()+"?"+request.getQueryString());

    if (request.getQueryString() != null) {
      response.setContentType("application/json");
    }

  }

  protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
  {
/*String out = "";
    response.setContentType("text/plain");

    Enumeration<String> parameterNames = request.getParameterNames();
    while (parameterNames.hasMoreElements()) {
      String paramName = parameterNames.nextElement();
      out += (paramName);
      out += ("\n");

      String[] paramValues = request.getParameterValues(paramName);
      for (int i = 0; i < paramValues.length; i++) {
	String paramValue = paramValues[i];
	out += ("t" + paramValue);
	out +=("\n");
      }

    }

    response.getWriter().println(out);*/

    
       String res = "doPost";

       String username = request.getParameter("username");
       String password = request.getParameter("password");
    //User user = userService.find(username, password);
    System.out.println("login-doPost:\n "+ username + ", "+password);


    if (username != null && password != null) {
    if (username.equals(prop.getProperty("webuser", "test")) && password.equals(prop.getProperty("webpassword", "test"))) {
      if (request.getSession() != null)
	      response.sendRedirect("main2.html");
      else {
	     request.getSession().setAttribute("user", username);
	     response.sendRedirect("main.html");
      }
    } else {
      request.setAttribute("error", "Unknown user, please try again");
      request.getRequestDispatcher("/index.html").forward(request, response);
      }
    }

    System.out.println("login-doPost:\nPath: "+ request.getRequestURI()+"?"+request.getQueryString());

    response.getWriter().println(res);
  }


  protected void doOptions(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
  {
    String res = "";
    response.addHeader("Access-Control-Allow-Origin", "*");
    response.addHeader("Access-Control-Allow-Methods", "GET, POST");
    response.addHeader("Access-Control-Allow-Headers", "X-Requested-With");

    response.getWriter().println(res);
  }

}
