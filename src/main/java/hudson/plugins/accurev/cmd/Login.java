package hudson.plugins.accurev.cmd;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;

import hudson.plugins.accurev.AccurevSCM.AccurevSCMDescriptor;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.parsers.output.ParseInfoToLoginName;

public class Login extends Command {
   private static final Logger logger = Logger.getLogger(Login.class.getName());
      
   /**
    * @return The currently logged in user "Principal" name, which may be
    *         "(not logged in)" if not logged in.<br>
    *         Returns null on failure.
    */
   private static String getLoggedInUsername(//
           final AccurevServer server, //
           final Map<String, String> accurevEnv, //
           final FilePath workspace, //
           final TaskListener listener, //
           final String accurevPath, //
           final Launcher launcher) {
       final String commandDescription = "info command";
       final ArgumentListBuilder cmd = new ArgumentListBuilder();
       cmd.add(accurevPath);
       cmd.add("info");
       addServer(cmd, server);
       final String username = AccurevLauncher.runCommand(commandDescription, launcher, cmd, null, null, accurevEnv,
               workspace, listener, logger, new ParseInfoToLoginName(), null);
       return username;
   }

   public static boolean ensureLoggedInToAccurev(AccurevServer server, Map<String, String> accurevEnv, FilePath pathToRunCommandsIn, TaskListener listener,
         String accurevPath, Launcher launcher) throws IOException, InterruptedException {
     
      if (server == null) {
         return false;
      }
      final String requiredUsername = server.getUsername();
      if (requiredUsername == null || requiredUsername.trim().length() == 0) {
         listener.getLogger().println("Authentication failure");
         return false;
      }
      AccurevSCMDescriptor.lock();
      try {
         final boolean loginRequired;
         if (server.isMinimiseLogins()) {
            final String currentUsername = getLoggedInUsername(server, accurevEnv, pathToRunCommandsIn, listener, accurevPath, launcher);
            if (currentUsername == null) {
               loginRequired = true;
               listener.getLogger().println("Not currently authenticated with Accurev server");
            } else {
               loginRequired = !currentUsername.equals(requiredUsername);
               listener.getLogger().println(
                     "Currently authenticated with Accurev server as '" + currentUsername + (loginRequired ? "', login required" : "', not logging in again."));
            }
         } else {
            loginRequired = true;
         }
         if (loginRequired) {
            return accurevLogin(server, accurevEnv, pathToRunCommandsIn, listener, accurevPath, launcher);
         }
      } finally {
         AccurevSCMDescriptor.unlock();
      }
      return true;
   }

   private static boolean accurevLogin(//
         final AccurevServer server, //
         final Map<String, String> accurevEnv, //
         final FilePath workspace, //
         final TaskListener listener, //
         final String accurevPath, //
         final Launcher launcher) throws IOException, InterruptedException {
      listener.getLogger().println("Authenticating with Accurev server...");
      final boolean[] masks;
      final ArgumentListBuilder cmd = new ArgumentListBuilder();
      cmd.add(accurevPath);
      cmd.add("login");
      addServer(cmd, server);
      if (server.isUseNonexpiringLogin()) {
         cmd.add("-n");
      }
      cmd.add(server.getUsername());
      if (server.getPassword() == null || "".equals(server.getPassword())) {
         cmd.addQuoted("");
         masks = new boolean[cmd.toCommandArray().length];
      } else {
         cmd.add(server.getPassword());
         masks = new boolean[cmd.toCommandArray().length];
         masks[masks.length - 1] = true;
      }
      final boolean success = AccurevLauncher.runCommand("login", launcher, cmd, masks, null, accurevEnv, workspace, listener, logger);
      if (success) {
         listener.getLogger().println("Authentication completed successfully.");
         return true;
      } else {
         return false;
      }
   }
   
   
   /**
    * This method is called from dofillstreams and dofilldepots while configuring the job
    *         
    */
   public static boolean accurevLoginfromGlobalConfig(//
	         final AccurevServer server,
	         final String accurevPath,
	         final Logger descriptorlogger) throws IOException, InterruptedException {
	   
	   	  final ArgumentListBuilder cmd = new ArgumentListBuilder();
	   	  List<String> logincommand = new ArrayList<>();
	   	  cmd.add(accurevPath);
	   	  cmd.add("login");
	      addServer(cmd, server);

	      if (server.isUseNonexpiringLogin()) {
	    	  cmd.add("-n");
	      }
	      cmd.add(server.getUsername());
	      // If the password is blank, "" should be passed
	      cmd.add(server.getPassword().length() == 0 ? "\"\"" : server.getPassword());
	      logincommand = cmd.toList();
	      ProcessBuilder processBuilder = new ProcessBuilder(logincommand);
	      processBuilder.redirectErrorStream(true);

	      Process loginprocess;
	      InputStream stdout = null;
	      	      
	      try {
	          loginprocess = processBuilder.start();
	          stdout = loginprocess.getInputStream();
	          String logincmdoutputdata = convertStreamToString(stdout);
	          loginprocess.waitFor();
	          if (loginprocess.exitValue() == 0) {
	        	  return true;
	          }
	          else{
	        	  descriptorlogger.warning("AccuRev Server: "+server.getName()+". "+logincmdoutputdata);	              
	        	  return false;
	          }
	      }
	      finally {
	          try {
	         	 if(stdout!=null){
	         		 stdout.close();
	         	 }
	          } catch (IOException e) {
	          }
	       }
   }
}
