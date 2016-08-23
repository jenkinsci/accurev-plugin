package hudson.plugins.accurev.cmd;

import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.util.ArgumentListBuilder;

import java.nio.charset.Charset;

public class Command {
   /**
    * Adds the server reference to the Arguments list.
    *
    * @param cmd    The accurev command line.
    * @param server The Accurev server details.
    */
   public static void addServer(ArgumentListBuilder cmd, AccurevServer server) {
       if (null != server && null != server.getHost() && !"".equals(server.getHost())) {
           cmd.add("-H");
           if (server.getPort() != 0) {
               cmd.add(server.getHost() + ":" + server.getPort());
           } else {
               cmd.add(server.getHost());
           }
       }
   }
   
   /**
    * Convert the output stream to string -- when the command is executed through ProcessBuilder from the Global config page of jenkins
    *
    * @param is stream where the command output is written when executed using the ProcessBuilder
    * @return String
    */
   public static String convertStreamToString(java.io.InputStream is) {
	      String stream = "";
	      java.util.Scanner s = new java.util.Scanner(is, Charset.defaultCharset().name()).useDelimiter("\\A");
	      stream = s.hasNext() ? s.next() : "";
	      s.close();
	      return stream;
   }
}
