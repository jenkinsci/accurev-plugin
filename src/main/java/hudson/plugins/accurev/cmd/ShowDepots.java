package hudson.plugins.accurev.cmd;

import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.util.ArgumentListBuilder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class ShowDepots extends Command {	
	
	public static List<String> getDepots(//
			 final AccurevServer server,
	         final String accurevPath,
	         final Logger descriptorlogger
	         ) {
		
		  final List<String> depots = new ArrayList<String>();
		  final ArgumentListBuilder cmd = new ArgumentListBuilder();
		  List<String> showDepotsCmd = new ArrayList<String>();
		  
		  cmd.add(accurevPath);
          cmd.add("show");
          addServer(cmd, server);
          cmd.add("-fx");
          cmd.add("depots");
          
          showDepotsCmd = cmd.toList();
          ProcessBuilder processBuilder = new ProcessBuilder(showDepotsCmd);
          processBuilder.redirectErrorStream(true);
          InputStream stdout = null;
          
          Process depotprocess;
		try {
			descriptorlogger.info(cmd.toStringWithQuote());
			depotprocess = processBuilder.start();
			stdout = depotprocess.getInputStream();
	          String showcmdoutputdata = convertStreamToString(stdout);
	          depotprocess.waitFor();
	          if (depotprocess.exitValue() == 0) {
	             DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
	             DocumentBuilder parser;
	             parser = factory.newDocumentBuilder();
	             Document doc = parser.parse(new ByteArrayInputStream(showcmdoutputdata.getBytes()));
	             doc.getDocumentElement().normalize();
	             NodeList nList = doc.getElementsByTagName("Element");
	             for (int i = 0; i < nList.getLength(); i++) {
	                Node nNode = nList.item(i);
	                if (nNode.getNodeType() == Node.ELEMENT_NODE) {
	                   Element eElement = (Element) nNode;
	                   depots.add(eElement.getAttribute("Name"));

	                }
	             }

	          } else {
	             descriptorlogger.warning("AccuRev Server: "+server.getName()+". "+showcmdoutputdata);
	          }
		} catch (IOException | ParserConfigurationException | InterruptedException e) {
	    	  descriptorlogger.log(Level.WARNING, "AccuRev Server: "+server.getName()+". "+"Could not populate depot list.", e.getCause());
	          e.printStackTrace();
	       } catch (SAXException e) {
	          e.printStackTrace();
	       } finally {
	          try {
	         	 if(stdout!=null){
	         		 stdout.close();
	         	 }
	         	 
	          } catch (IOException e) {
	          }
	       }
                    	     
	      return depots;
	   }
}
