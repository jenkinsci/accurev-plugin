package hudson.plugins.accurev;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.plugins.accurev.cmd.Command;
import hudson.plugins.accurev.cmd.PopulateCmd;
import hudson.plugins.accurev.cmd.SetProperty;
import hudson.util.ArgumentListBuilder;

import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.logging.Logger;

public class AccuRevWorkspaceProcessor {

	private static final Logger logger = Logger.getLogger(AccuRevWorkspaceProcessor.class.getName());
    private Date _startDateOfPopulate;       
    String _accurevWorkspace;
    String depot;
    AccurevSCM scm;
    
    AccuRevWorkspaceProcessor(AccurevSCM scm) {
 	   _accurevWorkspace = scm.getWorkspace();
 	   depot = scm.getDepot();
 	   this.scm = scm;
    }  
    
    
    Map<String, AccurevWorkspace> getWorkspaces(AccurevServer server, Map<String, String> accurevEnv, FilePath location, TaskListener listener,
          String accurevClientExePath, Launcher launcher) throws IOException, InterruptedException {
       final ArgumentListBuilder cmd = new ArgumentListBuilder();
       cmd.add(accurevClientExePath);
       cmd.add("show");
       Command.addServer(cmd, server);
       cmd.add("-fx");
       cmd.add("-p");
       cmd.add(depot);
       cmd.add("wspaces");
       final Map<String, AccurevWorkspace> workspaces = AccurevLauncher.runCommand("Show workspaces command", launcher, cmd, null, scm.getOptionalLock(),
             accurevEnv, location, listener, logger, XmlParserFactory.getFactory(), new ParseShowWorkspaces(), null);
       return workspaces;
    }

    public Date get_startDateOfPopulate() {
       return _startDateOfPopulate;
    }
    
    boolean checkoutWorkspace(AccurevSCM scm, Launcher launcher, BuildListener listener,
          AccurevServer server, Map<String, String> accurevEnv, 
          FilePath jenkinsWorkspace, 
          String accurevClientExePath, FilePath accurevWorkingSpace,
          Map<String, AccurevStream> streams,
          String localStream ) throws IOException, InterruptedException {
       listener.getLogger().println("Getting a list of workspaces...");
       final Map<String, AccurevWorkspace> workspaces = getWorkspaces(server, accurevEnv, jenkinsWorkspace, listener, accurevClientExePath, launcher);

       if (workspaces == null) {
           listener.fatalError("Cannot determine workspace configuration information");
           return false;
       }
       if (!workspaces.containsKey(_accurevWorkspace)) {
           listener.fatalError("The specified workspace does not appear to exist!");
           return false;
       }
       AccurevWorkspace accurevWorkspace = workspaces.get(_accurevWorkspace);
       if (!depot.equals(accurevWorkspace.getDepot())) {
           listener.fatalError("The specified workspace, " + _accurevWorkspace + ", is based in the depot " + accurevWorkspace.getDepot() + " not " + depot);
           return false;
       }

       for (AccurevStream accurevStream : streams.values()) {
           if (accurevWorkspace.getStreamNumber().equals(accurevStream.getNumber())) {
               accurevWorkspace.setStream(accurevStream);
               break;
           }
       }

       final RemoteWorkspaceDetails remoteDetails;
       try {
           remoteDetails = jenkinsWorkspace.act(new DetermineRemoteHostname(jenkinsWorkspace.getRemote()));
       } catch (IOException e) {
           listener.fatalError("Unable to validate workspace host.");
           e.printStackTrace(listener.getLogger());
           return false;
       }
       
       /*Change the background color of the workspace to yellow as default, this background color can be optionally changed by the users to green/red upon build success/failure
       *using post build action plugins.
       */
       {
    	   //For AccuRev 6.0.x versions
    	   SetProperty.setproperty(scm, accurevWorkingSpace, listener, accurevClientExePath, launcher, accurevEnv, server, _accurevWorkspace, "#FFEBB4", "style");
    	   
           //For AccuRev 6.1.x onwards
           SetProperty.setproperty(scm, accurevWorkingSpace, listener, accurevClientExePath, launcher, accurevEnv, server, _accurevWorkspace, "#FFEBB4", "streamStyle");
          
       }
       // handle workspace relocation and update and pop on the workspace accordingly
       {
           boolean needsReparenting = false;
           boolean needsRelocation = false;
           boolean needsHostUpdation = false;
           
           final ArgumentListBuilder chwscmd = new ArgumentListBuilder();
           chwscmd.add(accurevClientExePath);
           chwscmd.add("chws");
           Command.addServer(chwscmd, server);
           chwscmd.add("-w");
           chwscmd.add(_accurevWorkspace);

           if (!localStream.equals(accurevWorkspace.getStream().getParent().getName())) {
               listener.getLogger().println("Parent stream needs to be updated.");
               needsReparenting = true;
               chwscmd.add("-b");
               chwscmd.add(localStream);
           }
           if (!accurevWorkspace.getHost().equals(remoteDetails.getHostName())) {
               listener.getLogger().println("Host needs to be updated.");
               needsHostUpdation = true;
               chwscmd.add("-m");
               chwscmd.add(remoteDetails.getHostName());
           }
           final String oldStorage = accurevWorkspace.getStorage()
                   .replace("/", remoteDetails.getFileSeparator())
                   .replace("\\", remoteDetails.getFileSeparator());
           if (!oldStorage.equals(remoteDetails.getPath())) {
               listener.getLogger().println("Storage needs to be updated.");
               needsRelocation = true;
               chwscmd.add("-l");
               chwscmd.add(accurevWorkingSpace.getRemote());
           }
           //Depending on the above needs, determine the workflow
           if(needsRelocation || needsHostUpdation){

         	  listener.getLogger().println("Relocating workspace...");
         	  if(needsHostUpdation){
         		  listener.getLogger().println("  Old host: " + accurevWorkspace.getHost());
                   listener.getLogger().println("  New host: " + remoteDetails.getHostName());
         	  }
         	  if(needsRelocation){
         		  listener.getLogger().println("  Old storage: " + oldStorage);
                   listener.getLogger().println("  New storage: " + remoteDetails.getPath());
         	  }
         	  if(needsReparenting){
         		  listener.getLogger().println("Reparenting the workspace...");
         		  listener.getLogger().println("  Old parent stream: " + accurevWorkspace.getStream().getParent().getName());
                   listener.getLogger().println("  New parent stream: " + localStream);
         	  }
         	  if (!AccurevLauncher.runCommand("Workspace relocation command", launcher, chwscmd, null,
                       scm.getOptionalLock(), accurevEnv, accurevWorkingSpace, listener, logger, true)) {
                   return false;
               }
         	  if(needsReparenting){
         		  listener.getLogger().println("Reparenting successful.");
         	  }
         	  listener.getLogger().println("Relocation successful.");
         	  //update -9 for workspace
         	  listener.getLogger().println("Running update -9 to ensure the workspace is up to date transaction wise");
               final ArgumentListBuilder updatecmd = new ArgumentListBuilder();
               updatecmd.add(accurevClientExePath);
               updatecmd.add("update");
               Command.addServer(updatecmd, server);
               updatecmd.add("-9");
               if (!AccurevLauncher.runCommand("Workspace update -9 command", launcher, updatecmd, null,
                       scm.getOptionalLock(), accurevEnv, accurevWorkingSpace, listener, logger, true)) {
                   return false;
               }
               listener.getLogger().println("Update completed successfully.");
         	  //pop -O -R .
               // Now get that into local filesystem
               PopulateCmd pop = new PopulateCmd();
               if ( pop.populate(scm, launcher, listener, server, accurevClientExePath, null, true, "from workspace", accurevWorkingSpace, accurevEnv) ) {
                  _startDateOfPopulate = pop.get_startDateOfPopulate();
               } else {
                  return false;
               }           	  
         	  
           
           }else{
         	  //if the backing stream is changed
         	  if(needsReparenting){
	            	  listener.getLogger().println("Reparenting the workspace...");
	            	  if (!AccurevLauncher.runCommand("Workspace reparenting command", launcher, chwscmd, null,
	                          scm.getOptionalLock(), accurevEnv, accurevWorkingSpace, listener, logger, true)) {
	                      return false;
	                  }
	                  listener.getLogger().println("Reparenting successfull.");
         	  }
               //Run update on workspace
               listener.getLogger().println("Running update on the workspace to make sure that it contains the correct version of each element");
               final ArgumentListBuilder updatecmd = new ArgumentListBuilder();
               updatecmd.add(accurevClientExePath);
               updatecmd.add("update");
               Command.addServer(updatecmd, server);                  
               if (!AccurevLauncher.runCommand("Workspace update command", launcher, updatecmd, null,
                       scm.getOptionalLock(), accurevEnv, accurevWorkingSpace, listener, logger, true)) {
                   return false;
               }
               listener.getLogger().println("Update completed successfully.");
               //pop is not required, just set the _startDateOfPopulate
               _startDateOfPopulate = new Date();
           }
           
       }
     
              
       return true;
    }

 }
