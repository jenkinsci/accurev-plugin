package hudson.plugins.accurev;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.TaskListener;

import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.plugins.accurev.cmd.Command;
import hudson.plugins.accurev.cmd.PopulateCmd;
import hudson.util.ArgumentListBuilder;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Logger;

public class AccuRevRefTreeProcessor {

	private static final Logger logger = Logger.getLogger(AccuRevRefTreeProcessor.class.getName());
    private String _reftree;
    private Date _startDateOfPopulate;
    AccurevSCM scm;
    
    
    AccuRevRefTreeProcessor(AccurevSCM scm) {
       this.scm = scm;
       _reftree = scm.getReftree();      
    }
    private Map<String, AccurevReferenceTree> getReftrees(AccurevServer server,
            Map<String, String> accurevEnv,
            FilePath workspace,
            TaskListener listener,
            String accurevPath,
            Launcher launcher)
		throws IOException, InterruptedException {
		final ArgumentListBuilder cmd = new ArgumentListBuilder();
		cmd.add(accurevPath);
		cmd.add("show");
		Command.addServer(cmd, server);
		cmd.add("-fx");        
		cmd.add("refs");
		final Map<String, AccurevReferenceTree> reftrees = AccurevLauncher.runCommand("Show ref trees command",
		launcher, cmd, null, scm.getOptionalLock(), accurevEnv, workspace, listener, logger,
		XmlParserFactory.getFactory(), new ParseShowReftrees(), null);
		return reftrees;
    }
    
    //Get status of reference tree files
    private Map<String, RefTreeExternalFile> getReftreesStatus(AccurevServer server,
                                                        Map<String, String> accurevEnv,
                                                        FilePath workspace,
                                                        TaskListener listener,
                                                        String accurevPath,
                                                        Launcher launcher)
            throws IOException, InterruptedException {
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(accurevPath);
        cmd.add("stat");
        Command.addServer(cmd, server);
        cmd.add("*");
        cmd.add("-fxa"); 
        cmd.add("-x");
        cmd.add("-R");
        final Map<String, RefTreeExternalFile> externalFiles = AccurevLauncher.runCommand("Files with external file status command",
                launcher, cmd, null, scm.getOptionalLock(), accurevEnv, workspace, listener, logger,
                XmlParserFactory.getFactory(), new ParseRefTreeExternalFile(), null);
        return externalFiles;
    }
    
    boolean checkoutRefTree(AccurevSCM scm, Launcher launcher, BuildListener listener,
          AccurevServer server, Map<String, String> accurevEnv, 
          FilePath jenkinsWorkspace, 
          String accurevClientExePath, FilePath accurevWorkingSpace,
          Map<String, AccurevStream> streams ) throws IOException, InterruptedException {
       listener.getLogger().println("Getting a list of reference trees...");
       final Map<String, AccurevReferenceTree> reftrees = getReftrees(server, accurevEnv, jenkinsWorkspace, listener, accurevClientExePath, launcher);

       if (reftrees == null) {
           listener.fatalError("Cannot determine reference tree configuration information");
           return false;
       }
       if (!reftrees.containsKey(this._reftree)) {
           listener.fatalError("The specified reference tree does not appear to exist!");
           return false;
       }
       AccurevReferenceTree accurevReftree = reftrees.get(this._reftree);
       if (!scm.getDepot().equals(accurevReftree.getDepot())) {
           listener.fatalError("The specified reference tree, " + this._reftree + ", is based in the depot " + accurevReftree.getDepot() + " not " + scm.getDepot());
           return false;
       }

       for (AccurevStream accurevStream : streams.values()) {
           if (accurevReftree.getStreamNumber().equals(accurevStream.getNumber())) {
             accurevReftree.setStream(accurevStream);
               break;
           }
       }

       final RemoteWorkspaceDetails remoteDetails;
       try {
           remoteDetails = jenkinsWorkspace.act(new DetermineRemoteHostname(accurevWorkingSpace.getRemote()));
       } catch (IOException e) {
           listener.fatalError("Unable to validate reference tree host.");
           e.printStackTrace(listener.getLogger());
           return false;
       }

       // handle reference tree relocation
       {
           boolean needsRelocation = false;
           final ArgumentListBuilder chwscmd = new ArgumentListBuilder();
           chwscmd.add(accurevClientExePath);
           chwscmd.add("chref");
           Command.addServer(chwscmd, server);
           chwscmd.add("-r");
           chwscmd.add(this._reftree);
          
           if (!accurevReftree.getHost().equals(remoteDetails.getHostName())) {
               listener.getLogger().println("Host needs to be updated.");
               needsRelocation = true;
               chwscmd.add("-m");
               chwscmd.add(remoteDetails.getHostName());
           }
           final String oldStorage = accurevReftree.getStorage()
                   .replace("/", remoteDetails.getFileSeparator())
                   .replace("\\", remoteDetails.getFileSeparator());
           if (!oldStorage.equals(remoteDetails.getPath())) {
               listener.getLogger().println("Storage needs to be updated.");
               needsRelocation = true;
               chwscmd.add("-l");
               chwscmd.add(accurevWorkingSpace.getRemote());
           }
           if (needsRelocation) {
               listener.getLogger().println("Relocating Reference Tree...");
               listener.getLogger().println("  Old host: " + accurevReftree.getHost());
               listener.getLogger().println("  New host: " + remoteDetails.getHostName());
               listener.getLogger().println("  Old storage: " + oldStorage);
               listener.getLogger().println("  New storage: " + remoteDetails.getPath());
               
               if (!AccurevLauncher.runCommand("Reference tree relocation command", launcher, chwscmd, null,
                       scm.getOptionalLock(), accurevEnv, accurevWorkingSpace, listener, logger, true)) {
                   return false;
               }
               listener.getLogger().println("Relocation successfully.");
           }
       }

       // Update reference tree to contain latest code
       {
           listener.getLogger().println("Updating reference tree...");
           final ArgumentListBuilder updatecmd = new ArgumentListBuilder();
           updatecmd.add(accurevClientExePath);
           updatecmd.add("update");
           Command.addServer(updatecmd, server);
           updatecmd.add("-r");
           updatecmd.add(this._reftree);
           if ((scm.getSubPath() == null) || (scm.getSubPath().trim().length() == 0)) {

           } else {
               final StringTokenizer st = new StringTokenizer(scm.getSubPath(), ",");
               while (st.hasMoreElements()) {
                   updatecmd.add(st.nextToken().trim());
               }
           }
           
           if (AccurevLauncher.runCommand("Reference tree update command", launcher, updatecmd, null,
                   scm.getOptionalLock(), accurevEnv, accurevWorkingSpace, listener, logger, true)) {
               listener.getLogger().println("Update completed successfully.");
             // Now get that into local filesystem
               PopulateCmd pop = new PopulateCmd();
               if ( pop.populate(scm, launcher, listener, server, accurevClientExePath, null, true, "from reftree", accurevWorkingSpace, accurevEnv) ) {
                  _startDateOfPopulate = pop.get_startDateOfPopulate();
               } else {
                  return false;
               }
               if(scm.isCleanreftree()){
                final Map<String, RefTreeExternalFile> externalFiles = getReftreesStatus(server, accurevEnv, accurevWorkingSpace, listener, accurevClientExePath, launcher);
                File toBeDeleted;
                listener.getLogger().println("externalFiles size -"+externalFiles.size());
                Collection<RefTreeExternalFile> extObjects = externalFiles.values();
                for (RefTreeExternalFile o : extObjects)
                {
                   listener.getLogger().println("External File path -"+o.getLocation());
                   toBeDeleted= new File(o.getLocation());
                   if(toBeDeleted.exists())
                   {
                      toBeDeleted.delete();
                   }
                }               
                                                                   
               }
           } else {
                      
               {
             listener.getLogger().println("Update failed...");
             listener.getLogger().println("Run update -9 along with -r option");
               final ArgumentListBuilder update9cmd = new ArgumentListBuilder();
               update9cmd.add(accurevClientExePath);
               update9cmd.add("update");
               Command.addServer(update9cmd, server);
               update9cmd.add("-r");
               update9cmd.add(this._reftree);
               update9cmd.add("-9");
               if (!AccurevLauncher.runCommand("Reference tree update -9 command", launcher, update9cmd, null,
                       scm.getOptionalLock(), accurevEnv, accurevWorkingSpace, listener, logger, true)) {
                return false;
               }
               else{
                // Now get that into local filesystem
                  PopulateCmd pop = new PopulateCmd();
                  if ( pop.populate(scm, launcher, listener, server, accurevClientExePath, null, true, "from re-pop reftree", accurevWorkingSpace, accurevEnv) ) {
                     _startDateOfPopulate = pop.get_startDateOfPopulate();
                  } else {
                     return false;
                  }
                  
                   if(scm.isCleanreftree()){
                      final Map<String, RefTreeExternalFile> externalFiles = getReftreesStatus(server, accurevEnv, accurevWorkingSpace, listener, accurevClientExePath, launcher);
                      File toBeDeleted;
                      for(int i=0; i<externalFiles.size(); i++)
                      {
                         RefTreeExternalFile extFileObject = externalFiles.get(i);
                         listener.getLogger().println("Update failed -- External File path -"+extFileObject.getLocation());
                         toBeDeleted= new File(extFileObject.getLocation());
                         if(toBeDeleted.exists())
                         {
                            toBeDeleted.delete();
                         }
                      }                                                     
                   }
               }
               }
           }
       }
       
       return true;
    }

   public Date get_startDateOfPopulate() {
      return _startDateOfPopulate;
   }
 
}
