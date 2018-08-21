package eu.thingwave.datamanager.resources;

import java.util.*;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.LinkFormat;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.MediaTypeRegistry;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.server.resources.CoapExchange;
import org.eclipse.californium.core.CoapResource;
import org.eclipse.californium.core.Utils;

import java.io.File;


public class DiskResource extends CoapResource {
  Properties prop = null;
  final String DEFAULT_ROOT = "/tmp/historian";

  public DiskResource(Properties prop, /*Tentacle tentacle, */String name) {
    super(name);
    this.prop = prop;
  }

  @Override
    public void handleGET(CoapExchange exchange) {
      Calendar cal = Calendar.getInstance();
      System.out.println("Disk::GET\n");

      File file = new File(prop.getProperty("root-folder", DEFAULT_ROOT));
      long total = file.getTotalSpace() / (1024 * 1024); 
      long free = file.getUsableSpace()  / (1024 * 1024);

      String res = "[{\"bt\": "+(cal.getTimeInMillis()/1000)+"},\n";
      res += "  {\"n\": \"total\", \"u\": \"MB\", \"v\": "+total+"},\n";
      res += "  {\"n\": \"free\", \"u\": \"MB\", \"v\": "+free+"}\n";
      res += "]";

      exchange.respond(ResponseCode.CONTENT, res, MediaTypeRegistry.APPLICATION_JSON);
    }

}
