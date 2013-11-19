package hudson.plugins.accurev;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
//import hudson.model.Cause;
import hudson.model.ModelObject;
import hudson.model.ParameterValue;
//import hudson.model.Result;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.plugins.jetty.security.Password;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.EditType;
import hudson.scm.PollingResult;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCM;
import hudson.scm.SCMRevisionState;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectStreamException;
//import java.io.OutputStream;
//import java.io.PrintStream;
//import java.io.PrintWriter;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * Created by IntelliJ IDEA.
 *
 * @author connollys
 * @since 09-Oct-2007 16:17:34
 */
public class AccurevSCM extends SCM {

// ------------------------------ FIELDS ------------------------------

    public static final SimpleDateFormat ACCUREV_DATETIME_FORMATTER = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    @Extension
    public static final AccurevSCMDescriptor DESCRIPTOR = new AccurevSCMDescriptor();
    private static final Logger logger = Logger.getLogger(AccurevSCM.class.getName());
    private static final Date NO_TRANS_DATE = new Date(0);
    private static final String DEFAULT_SNAPSHOT_NAME_FORMAT = "${JOB_NAME}_${BUILD_NUMBER}";
    private final String serverName;
    private final String depot;
    private final String stream;
    private final AccuRevWorkspaceProcessor _accurevWorkspace;
    private final boolean ignoreStreamParent;
    private final boolean useReftree;
    
    private final boolean cleanreftree;
    
    private final boolean useSnapshot;
    private final String snapshotNameFormat;
    private final boolean synctime;
    private final String reftree;
    private final String subPath;
    private final String filterForPollSCM;
    private final String directoryOffset;
    
    
    private static String globalServerName = null;
    private static String globalDepotName = null;
    private static String globalStreamName = null;
    
    
// --------------------------- CONSTRUCTORS ---------------------------

    /**
     * Our constructor.
     */
    @DataBoundConstructor
    public AccurevSCM(String serverName,
                      String depot,
                      String stream,
                      StaplerRequest req,
                      boolean useReftree,
                      String reftree,
                      String subPath,
                      String filterForPollSCM,
                      boolean synctime,
                      boolean cleanreftree,  
                      boolean useSnapshot,
                      String snapshotNameFormat,
                      String directoryOffset, 
                      boolean ignoreStreamParent) {
        super();
        this.serverName = serverName;
        this.depot = depot;
        this.stream = stream;
        
        this._accurevWorkspace = new AccuRevWorkspaceProcessor(req);

        this.useReftree = useReftree;
        this.reftree = reftree;
        this.subPath = subPath;
        this.filterForPollSCM = filterForPollSCM;
        this.synctime = synctime;
        this.cleanreftree = cleanreftree;
        this.useSnapshot = useSnapshot;
        this.snapshotNameFormat = snapshotNameFormat;
        this.ignoreStreamParent = ignoreStreamParent;
        this.directoryOffset = directoryOffset;
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    /**
     * Getter for property 'depot'.
     *
     * @return Value for property 'depot'.
     */
    public String getDepot() {
        return depot;
    }

    /**
     * Getter for property 'serverName'.
     *
     * @return Value for property 'serverName'.
     */
    public String getServerName() {
        return serverName;
    }

    /**
     * Getter for property 'stream'.
     *
     * @return Value for property 'stream'.
     */
    public String getStream() {
        return stream;
    }

    /**
     * Getter for property 'reftree'.
     *
     * @return Value for property 'reftree'.
     */
    public String getReftree() {
        return reftree;
    }

    /**
     * Getter for property 'subPath'.
     *
     * @return Value for property 'subPath'.
     */
    public String getSubPath() {
        return subPath;
    }
    
    /**
     * Getter for property 'filterForPollSCM'.
     *
     * @return Value for property 'filterForPollSCM'.
     */
    public String getFilterForPollSCM() {
        return filterForPollSCM;
    }

    /**
     * Getter for property 'snapshotNameFormat'.
     *
     * @return Value for property 'snapshotNameFormat'.
     */
    public String getSnapshotNameFormat() {
        return snapshotNameFormat;
    }

    /**
     * Getter for property 'ignoreStreamParent'.
     *
     * @return Value for property 'ignoreStreamParent'.
     */
    public boolean isIgnoreStreamParent() {
        return ignoreStreamParent;
    }

    /**
     * Getter for property 'synctime'.
     *
     * @return Value for property 'synctime'.
     */
    public boolean isSynctime() {
        return synctime;
    }

   

    /**
     * Getter for property 'useUpdate'.
     *
     * @return Value for property 'useUpdate'.
     */
    public boolean isCleanreftree() {
        return cleanreftree;
    }

    /**
     * Getter for property 'useSnapshot'.
     *
     * @return Value for property 'useSnapshot'.
     */
    public boolean isUseSnapshot() {
        return useSnapshot;
    }

    /**
     * Getter for property 'useRefTree'.
     *
     * @return Value for property 'useRefTree'.
     */
    public boolean isUseReftree() {
        return useReftree;
    }
    
    /**
     * Getter for property 'directoryOffset'.
     *
     * @return Value for property 'directoryOffset'.
     */
    public String getDirectoryOffset() {
        return directoryOffset;
    }

// ------------------------ INTERFACE METHODS ------------------------

// --------------------- Interface Describable ---------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public SCMDescriptor<?> getDescriptor() {
        return DESCRIPTOR;
    }

// -------------------------- OTHER METHODS --------------------------

    /**
     * Exposes AccuRev-specific information to the environment.
     * The following variables become available, if not null:
     * <ul>
     *  <li>ACCUREV_DEPOT - The depot name</li>
     *  <li>ACCUREV_STREAM - The stream name</li>
     *  <li>ACCUREV_SERVER - The server name</li>
     *  <li>ACCUREV_REFTREE - The workspace name</li>
     *  <li>ACCUREV_SUBPATH - The workspace subpath</li>
     *
     * </ul>
     * @since 0.6.9
     */
    @Override
    public void buildEnvVars(AbstractBuild<?,?> build, Map<String, String> env) {
        // call super even though SCM.buildEnvVars currently does nothing - this could change
        super.buildEnvVars(build, env);
        // add various accurev-specific variables to the environment
        if (depot != null)
            env.put("ACCUREV_DEPOT", depot);
        if (stream != null)
            env.put("ACCUREV_STREAM", stream);
        if (serverName != null)
            env.put("ACCUREV_SERVER", serverName);
        if (reftree != null && useReftree)
            env.put("ACCUREV_REFTREE", reftree);
        if (subPath != null)
            env.put("ACCUREV_SUBPATH", subPath);
        // grab the last promote transaction from the changelog file
        String lastTransaction = null;
        // Abstract should have this since checkout should have already run
        ChangeLogSet<AccurevTransaction> changeSet = (ChangeLogSet<AccurevTransaction>) build.getChangeSet();
        if (!changeSet.isEmptySet()) {
            // first EDIT entry should be the last transaction we want
            for (Object o : changeSet.getItems()) {
                AccurevTransaction t = (AccurevTransaction) o;
                if (t.getEditType() == EditType.EDIT) { // this means promote or chstream in AccuRev
                   lastTransaction = t.getRevision();
                   break;
                }
            }
            /*
             * in case you get a changelog with no changes (e.g. a dispatch
             * message or something I don't know about yet), set something
             * different than nothing
             */
            if (lastTransaction == null) {
                lastTransaction = "NO_EDITS";
            }
        }
        if (lastTransaction != null) {
            env.put("ACCUREV_LAST_TRANSACTION", lastTransaction);
        }
    }

    private class AccuRevWorkspaceProcessor {
       private Date _startDateOfPopulate;
       private boolean _useAccurevWorkspace;
       private boolean _useRevert;
       private String _accurevWorkspace = null;
       
       AccuRevWorkspaceProcessor(StaplerRequest req ) {
          _useRevert = ( req.getParameter("accurev.useRevert") != null );
          _useAccurevWorkspace = ( req.getParameter("accurev.useWorkspace") != null );
          _accurevWorkspace = req.getParameter("accurev.workspace");
       }
       
       Map<String, AccurevWorkspace> getWorkspaces(AccurevServer server, Map<String, String> accurevEnv, FilePath location, TaskListener listener,
             String accurevClientExePath, Launcher launcher) throws IOException, InterruptedException {
          final ArgumentListBuilder cmd = new ArgumentListBuilder();
          cmd.add(accurevClientExePath);
          cmd.add("show");
          addServer(cmd, server);
          cmd.add("-fx");
          cmd.add("-p");
          cmd.add(depot);
          cmd.add("wspaces");
          final Map<String, AccurevWorkspace> workspaces = AccurevLauncher.runCommand("Show workspaces command", launcher, cmd, null, getOptionalLock(),
                accurevEnv, location, listener, logger, XmlParserFactory.getFactory(), new ParseShowWorkspaces(), null);
          return workspaces;
       }

       public Date get_startDateOfPopulate() {
          return _startDateOfPopulate;
       }
       
       boolean checkoutWorkspace(Launcher launcher, BuildListener listener,
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

          // handle workspace relocation
          {
              boolean needsRelocation = false;
              final ArgumentListBuilder chwscmd = new ArgumentListBuilder();
              chwscmd.add(accurevClientExePath);
              chwscmd.add("chws");
              addServer(chwscmd, server);
              chwscmd.add("-w");
              chwscmd.add(_accurevWorkspace);

              if (!localStream.equals(accurevWorkspace.getStream().getParent().getName())) {
                  listener.getLogger().println("Parent stream needs to be updated.");
                  needsRelocation = true;
                  chwscmd.add("-b");
                  chwscmd.add(localStream);
              }
              if (!accurevWorkspace.getHost().equals(remoteDetails.getHostName())) {
                  listener.getLogger().println("Host needs to be updated.");
                  needsRelocation = true;
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
              if (needsRelocation) {
                  listener.getLogger().println("Relocating workspace...");
                  listener.getLogger().println("  Old host: " + accurevWorkspace.getHost());
                  listener.getLogger().println("  New host: " + remoteDetails.getHostName());
                  listener.getLogger().println("  Old storage: " + oldStorage);
                  listener.getLogger().println("  New storage: " + remoteDetails.getPath());
                  listener.getLogger().println("  Old parent stream: " + accurevWorkspace.getStream().getParent()
                          .getName());
                  listener.getLogger().println("  New parent stream: " + localStream);
                  if (!AccurevLauncher.runCommand("Workspace relocation command", launcher, chwscmd, null,
                          getOptionalLock(), accurevEnv, accurevWorkingSpace, listener, logger, true)) {
                      return false;
                  }
                  listener.getLogger().println("Relocation successfully.");
              }
          }

          if (_useRevert) {
              listener.getLogger().println("attempting to get overlaps");
              final List<String> overlaps = getOverlaps(server, accurevEnv, accurevWorkingSpace, listener, accurevClientExePath, launcher);
              if (overlaps != null && overlaps.size() > 0) {
                 accurevWorkingSpace.act(new PurgeWorkspaceOverlaps(listener, overlaps));
              }
          }

          // Update workspace to contain latest code
          {
              listener.getLogger().println("Updating workspace...");
              final ArgumentListBuilder updatecmd = new ArgumentListBuilder();
              updatecmd.add(accurevClientExePath);
              updatecmd.add("update");
              addServer(updatecmd, server);
              if (!AccurevLauncher.runCommand("Workspace update command", launcher, updatecmd, null,
                      getOptionalLock(), accurevEnv, accurevWorkingSpace, listener, logger, true)) {
                  return false;
              }
              listener.getLogger().println("Update completed successfully.");
          }

          // Now get that into local filesystem
          PopulateFiles pop = new PopulateFiles();
          if ( pop.populate(launcher, listener, server, accurevClientExePath, null, false, "from workspace", accurevWorkingSpace, accurevEnv) ) {
             _startDateOfPopulate = pop.get_startDateOfPopulate();
          } else {
             return false;
          }
          
          return true;
       }

      public boolean is_useAccurevWorkspace() {
         return _useAccurevWorkspace;
      }
    }
    
    private class AccuRevRefTreeProcessor {
       private String _reftree;
       private Date _startDateOfPopulate;
       
       AccuRevRefTreeProcessor(String reftree) {
          _reftree = reftree;
       }
       
       private boolean checkoutRefTree(Launcher launcher, BuildListener listener,
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
          if (!depot.equals(accurevReftree.getDepot())) {
              listener.fatalError("The specified reference tree, " + this._reftree + ", is based in the depot " + accurevReftree.getDepot() + " not " + depot);
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
              addServer(chwscmd, server);
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
                          getOptionalLock(), accurevEnv, accurevWorkingSpace, listener, logger, true)) {
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
              addServer(updatecmd, server);
              updatecmd.add("-r");
              updatecmd.add(this._reftree);
              if ((subPath == null) || (subPath.trim().length() == 0)) {
   
              } else {
                  final StringTokenizer st = new StringTokenizer(subPath, ",");
                  while (st.hasMoreElements()) {
                      updatecmd.add(st.nextToken().trim());
                  }
              }
              
              if (AccurevLauncher.runCommand("Reference tree update command", launcher, updatecmd, null,
                      getOptionalLock(), accurevEnv, accurevWorkingSpace, listener, logger, true)) {
                  listener.getLogger().println("Update completed successfully.");
                // Now get that into local filesystem
                  PopulateFiles pop = new PopulateFiles();
                  if ( pop.populate(launcher, listener, server, accurevClientExePath, null, true, "from reftree", accurevWorkingSpace, accurevEnv) ) {
                     _startDateOfPopulate = pop.get_startDateOfPopulate();
                  } else {
                     return false;
                  }
                  if(cleanreftree){
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
                listener.getLogger().println("Run update -9");
                  final ArgumentListBuilder update9cmd = new ArgumentListBuilder();
                  update9cmd.add(accurevClientExePath);
                  update9cmd.add("update");
                  addServer(update9cmd, server);
                  update9cmd.add("-r");
                  update9cmd.add(this._reftree);
                  update9cmd.add("-9");
                  if (!AccurevLauncher.runCommand("Reference tree update -9 command", launcher, update9cmd, null,
                          getOptionalLock(), accurevEnv, accurevWorkingSpace, listener, logger, true)) {
                   return false;
                  }
                  else{
                   // Now get that into local filesystem
                     PopulateFiles pop = new PopulateFiles();
                     if ( pop.populate(launcher, listener, server, accurevClientExePath, null, true, "from re-pop reftree", accurevWorkingSpace, accurevEnv) ) {
                        _startDateOfPopulate = pop.get_startDateOfPopulate();
                     } else {
                        return false;
                     }
                     
                      if(cleanreftree){
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

    class PopulateFiles {
       private Date _startDateOfPopulate;
       
       /**
        * 
        * @return
        */
       public Date get_startDateOfPopulate() {
          return _startDateOfPopulate;
       }
       
       /**
        * 
        * @param launcher
        * @param listener
        * @param server
        * @param accurevClientExePath
        * @param streamName
        * @param fromMessage
        * @param accurevWorkingSpace
        * @param accurevEnv
        * @return
        */
      boolean populate(Launcher launcher, BuildListener listener, 
            AccurevServer server, String accurevClientExePath, 
            String streamName, 
            boolean overwrite, 
            String fromMessage,
            FilePath accurevWorkingSpace, Map<String, String> accurevEnv) {
         listener.getLogger().println("Populating " + fromMessage + "...");
         final ArgumentListBuilder popcmd = new ArgumentListBuilder();
         popcmd.add(accurevClientExePath);
         popcmd.add("pop");
         addServer(popcmd, server);
         
         if ( streamName != null ) {
            popcmd.add("-v");
            popcmd.add(streamName);
         }
         
         popcmd.add("-L");
         popcmd.add(accurevWorkingSpace.getRemote());
         
         if ( overwrite ) popcmd.add("-O");
         
         popcmd.add("-R");
         if ((subPath == null) || (subPath.trim().length() == 0)) {
            popcmd.add(".");
         } else {
            final StringTokenizer st = new StringTokenizer(subPath, ",");
            while (st.hasMoreElements()) {
               popcmd.add(st.nextToken().trim());
            }
         }
         _startDateOfPopulate = new Date();
         if (Boolean.TRUE != AccurevLauncher.runCommand("Populate " + fromMessage + " command", launcher, popcmd, null, getOptionalLock(), accurevEnv,
               accurevWorkingSpace, listener, logger, new ParsePopulate(), listener.getLogger())) {
            return false;
         }
         listener.getLogger().println("Populate completed successfully.");
         return true;
      }
    }
    
    /**
     * {@inheritDoc}
     */
    public boolean checkout(AbstractBuild<?, ?> build, Launcher launcher, FilePath jenkinsWorkspace, BuildListener listener,
                            File changelogFile) throws IOException, InterruptedException {

        final AccurevServer server = DESCRIPTOR.getServer(serverName);
        final String accurevClientExePath = jenkinsWorkspace.act(new FindAccurevClientExe(server));
        final FilePath accurevWorkingSpace = new FilePath (jenkinsWorkspace, directoryOffset == null ? "" : directoryOffset);
        final Map<String, String> accurevEnv = new HashMap<String, String>();
        
        if (!accurevWorkingSpace.exists()) accurevWorkingSpace.mkdirs();

        if (!ensureLoggedInToAccurev(server, accurevEnv, jenkinsWorkspace, listener, accurevClientExePath,
                launcher)) {
            return false;
        }

        if (synctime) {
            listener.getLogger().println("Synchronizing clock with the server...");
            if (!synctime(server, accurevEnv, jenkinsWorkspace, listener, accurevClientExePath, launcher)) {
                return false;
            }
        }

        if (depot == null || "".equals(depot)) {
            listener.fatalError("Must specify a depot");
            return false;
        }

        if (stream == null || "".equals(stream)) {
            listener.fatalError("Must specify a stream");
            return false;
        }

        final EnvVars environment = build.getEnvironment(listener);

        final String localStream = environment.expand(stream);

        listener.getLogger().println("Getting a list of streams...");
        final Map<String, AccurevStream> streams = getStreams(localStream, server, accurevEnv, jenkinsWorkspace, listener, accurevClientExePath,
                launcher);

        if (streams != null && !streams.containsKey(localStream)) {
            listener.fatalError("The specified stream does not appear to exist!");
            return false;
        }
        if (useReftree && (this.reftree == null || "".equals(this.reftree))) {
            listener.fatalError("Must specify a reference tree");
            return false;
        }

        final Date startDateOfPopulate;

        if (useReftree) {
           AccuRevRefTreeProcessor rTree = new AccuRevRefTreeProcessor(this.reftree);
           boolean result = rTree.checkoutRefTree( launcher, listener, server, accurevEnv, jenkinsWorkspace, accurevClientExePath, accurevWorkingSpace, streams );
           if (!result) return result;
           startDateOfPopulate = rTree.get_startDateOfPopulate();
        } else if ( _accurevWorkspace != null && _accurevWorkspace.is_useAccurevWorkspace() ) {
           boolean result = _accurevWorkspace.checkoutWorkspace( launcher, listener, server, accurevEnv, jenkinsWorkspace, accurevClientExePath, accurevWorkingSpace, streams, localStream );
           if (!result) return result;
           startDateOfPopulate = _accurevWorkspace.get_startDateOfPopulate();           
        } else if ( isUseSnapshot() ) {
            final String snapshotName = calculateSnapshotName(build, listener);
            listener.getLogger().println("Creating snapshot: " + snapshotName + "...");
            build.getEnvironment(listener).put("ACCUREV_SNAPSHOT", snapshotName);
            // snapshot command: accurev mksnap -H <server> -s <snapshotName> -b <backing_stream> -t now
            final ArgumentListBuilder mksnapcmd = new ArgumentListBuilder();
            mksnapcmd.add(accurevClientExePath);
            mksnapcmd.add("mksnap");
            addServer(mksnapcmd, server);
            mksnapcmd.add("-s");
            mksnapcmd.add(snapshotName);
            mksnapcmd.add("-b");
            mksnapcmd.add(localStream);
            mksnapcmd.add("-t");
            mksnapcmd.add("now");
            if (!AccurevLauncher.runCommand("Create snapshot command", launcher, mksnapcmd, null, getOptionalLock(),
                    accurevEnv, jenkinsWorkspace, listener, logger, true)) {
                return false;
            }
            listener.getLogger().println("Snapshot created successfully.");
            
            PopulateFiles pop = new PopulateFiles();
            if ( pop.populate(launcher, listener, server, accurevClientExePath, snapshotName, true, "from workspace", accurevWorkingSpace, accurevEnv) ) {
               startDateOfPopulate = pop.get_startDateOfPopulate();
            } else {
               return false;
            }            
        } else {
           PopulateFiles pop = new PopulateFiles();
           if ( pop.populate(launcher, listener, server, accurevClientExePath, localStream, true, "from jenkins workspace", accurevWorkingSpace, accurevEnv) ) {
              startDateOfPopulate = pop.get_startDateOfPopulate();
           } else {
              return false;
           }            
        }

        listener.getLogger().println(
                "Calculating changelog" + (ignoreStreamParent ? ", ignoring changes in parent" : "") + "...");

        final Calendar startTime;
        if (null == build.getPreviousBuild()) {
            listener.getLogger().println("Cannot find a previous build to compare against. Computing all changes.");
            startTime = null;
        } else {
            startTime = build.getPreviousBuild().getTimestamp();
        }

        {
            AccurevStream stream = streams == null ? null : streams.get(localStream);

            if (stream == null) {
                // if there was a problem, fall back to simple stream check
                return captureChangelog(server, accurevEnv, accurevWorkingSpace, listener, accurevClientExePath, launcher,
                        startDateOfPopulate, startTime == null ? null : startTime.getTime(),
                        localStream, changelogFile);
            }
            // There may be changes in a parent stream that we need to factor in.
            // TODO produce a consolidated list of changes from the parent streams
            do {
                // This is a best effort to get as close to the changes as possible
                if (checkStreamForChanges(server, accurevEnv, accurevWorkingSpace, listener, accurevClientExePath, launcher,
                        stream.getName(), startTime == null ? null : startTime.getTime())) {
                    return captureChangelog(server, accurevEnv, accurevWorkingSpace, listener, accurevClientExePath, launcher,
                            startDateOfPopulate, startTime == null ? null : startTime
                            .getTime(), stream.getName(), changelogFile);
                }
                stream = stream.getParent();
            } while (stream != null && stream.isReceivingChangesFromParent());
        }
        return captureChangelog(server, accurevEnv, accurevWorkingSpace, listener, accurevClientExePath, launcher,
                startDateOfPopulate, startTime == null ? null : startTime.getTime(), localStream,
                changelogFile);
    }

    private String calculateSnapshotName(final AbstractBuild<?, ?> build,
            final BuildListener listener) throws IOException, InterruptedException {
        final String actualFormat = (snapshotNameFormat == null || snapshotNameFormat
                .trim().isEmpty()) ? DEFAULT_SNAPSHOT_NAME_FORMAT : snapshotNameFormat.trim();
        final EnvVars environment = build.getEnvironment(listener);
        final String snapshotName = environment.expand(actualFormat);
        return snapshotName;
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
		addServer(cmd, server);
		cmd.add("-fx");        
		cmd.add("refs");
		final Map<String, AccurevReferenceTree> reftrees = AccurevLauncher.runCommand("Show ref trees command",
		launcher, cmd, null, getOptionalLock(), accurevEnv, workspace, listener, logger,
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
        addServer(cmd, server);
        cmd.add("*");
        cmd.add("-fxa"); 
        cmd.add("-x");
        cmd.add("-R");
        final Map<String, RefTreeExternalFile> externalFiles = AccurevLauncher.runCommand("Files with external file status command",
                launcher, cmd, null, getOptionalLock(), accurevEnv, workspace, listener, logger,
                XmlParserFactory.getFactory(), new ParseRefTreeExternalFile(), null);
        return externalFiles;
    }
    
   

    private boolean captureChangelog(AccurevServer server,
                                     Map<String, String> accurevEnv,
                                     FilePath workspace,
                                     BuildListener listener,
                                     String accurevPath,
                                     Launcher launcher,
                                     Date buildDate,
                                     Date startDate,
                                     String stream,
                                     File changelogFile) throws IOException, InterruptedException {
        ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(accurevPath);
        cmd.add("hist");
        addServer(cmd, server);
        cmd.add("-fx");
        cmd.add("-a");
        cmd.add("-s");
        cmd.add(stream);
        cmd.add("-t");
        String dateRange = ACCUREV_DATETIME_FORMATTER.format(buildDate);
        if (startDate != null) {
            dateRange += "-" + ACCUREV_DATETIME_FORMATTER.format(startDate);
        } else {
            dateRange += ".100";
        }
        cmd.add(dateRange); // if this breaks windows there's going to be fun
        final String commandDescription = "Changelog command";
        final Boolean success = AccurevLauncher.runCommand(commandDescription, launcher, cmd, null, getOptionalLock(),
                accurevEnv, workspace, listener, logger, new ParseOutputToFile(), changelogFile);
        if (success != Boolean.TRUE) {
            return false;
        }
        listener.getLogger().println("Changelog calculated successfully.");
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public ChangeLogParser createChangeLogParser() {
        return new AccurevChangeLogParser();
    }

    private static boolean hasStringVariableReference(final String str) {
        return str != null && str.indexOf("${") != -1;
    }

    @Override
    public boolean requiresWorkspaceForPolling() {
        final boolean needSlaveForPolling = !DESCRIPTOR.isPollOnMaster();
        return needSlaveForPolling;
    }

    private boolean ensureLoggedInToAccurev(
            AccurevServer server,
            Map<String, String> accurevEnv,
            FilePath pathToRunCommandsIn,
            TaskListener listener,
            String accurevPath,
            Launcher launcher)
            throws IOException, InterruptedException {
        final String accurevHomeEnvVar = "ACCUREV_HOME";
        if (!accurevEnv.containsKey(accurevHomeEnvVar)) {
            final String accurevHome = pathToRunCommandsIn.getParent().getRemote();
            accurevEnv.put(accurevHomeEnvVar, accurevHome);
            listener.getLogger().println("Setting " + accurevHomeEnvVar + " to \"" + accurevHome + '"');
        }
        if (server == null) {
            return false;
        }
        final String requiredUsername = server.getUsername();
        if( requiredUsername==null || requiredUsername.trim().length()==0 ) {
        	listener.getLogger().println("Authentication failure");
            return false;
        }
        AccurevSCMDescriptor.ACCUREV_LOCK.lock();
        try {
            final boolean loginRequired;
            if (server.isMinimiseLogins()) {
                final String currentUsername = getLoggedInUsername(server,
                        accurevEnv, pathToRunCommandsIn, listener, accurevPath,
                        launcher);
                if (currentUsername==null) {
                    loginRequired = true;
                    listener.getLogger().println(
                            "Not currently authenticated with Accurev server");
                } else {
                    loginRequired = !currentUsername
                            .equals(requiredUsername);
                    listener.getLogger().println(
                            "Currently authenticated with Accurev server as '"
                                    + currentUsername
                                    + (loginRequired ? "', login required"
                                            : "', not logging in again."));
                }
            } else {
                loginRequired = true;
            }
            if (loginRequired) {
                return accurevLogin(server, accurevEnv, pathToRunCommandsIn,
                        listener, accurevPath, launcher);
            }
        } finally {
            AccurevSCMDescriptor.ACCUREV_LOCK.unlock();
        }
        return true;
    }

    private boolean accurevLogin(//
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
        final boolean success = AccurevLauncher.runCommand("login", launcher, cmd, masks, null, accurevEnv, workspace,
                listener, logger);
        if (success) {
            listener.getLogger().println("Authentication completed successfully.");
            return true;
        } else {
            return false;
        }
    }

    private boolean synctime(//
            final AccurevServer server, //
            final Map<String, String> accurevEnv, //
            final FilePath workspace, //
            final TaskListener listener, //
            final String accurevPath, //
            final Launcher launcher) throws IOException, InterruptedException {
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(accurevPath);
        cmd.add("synctime");
        addServer(cmd, server);
        final boolean success = AccurevLauncher.runCommand("Synctime command", launcher, cmd, null, getOptionalLock(),
                accurevEnv, workspace, listener, logger);
        return success;
    }

    private Map<String, AccurevStream> getStreams(//
            final String nameOfStreamRequired, //
            final AccurevServer server, //
            final Map<String, String> accurevEnv, //
            final FilePath workspace, //
            final TaskListener listener, //
            final String accurevPath, //
            final Launcher launcher) throws IOException, InterruptedException {
        final Map<String, AccurevStream> streams;
        if( server.useRestrictedShowStreams)
        {
            streams = getAllAncestorStreams(nameOfStreamRequired, server, accurevEnv, workspace, listener, accurevPath,
                    launcher);
        } else {
            if (this.ignoreStreamParent) {
                streams = getOneStream(nameOfStreamRequired, server, accurevEnv, workspace, listener, accurevPath,
                        launcher);
            } else {
                streams = getAllStreams(server, accurevEnv, workspace, listener, accurevPath, launcher);
            }
        }
        return streams;
    }

    private Map<String, AccurevStream> getAllStreams(//
            final AccurevServer server, //
            final Map<String, String> accurevEnv, //
            final FilePath workspace, //
            final TaskListener listener, //
            final String accurevPath, //
            final Launcher launcher) {
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(accurevPath);
        cmd.add("show");
        addServer(cmd, server);
        cmd.add("-fx");
        cmd.add("-p");
        cmd.add(depot);
        cmd.add("streams");
        final Map<String, AccurevStream> streams = AccurevLauncher.runCommand("Show streams command", launcher, cmd,
                null, getOptionalLock(), accurevEnv, workspace, listener, logger, XmlParserFactory.getFactory(),
                new ParseShowStreams(), depot);
        return streams;
    }

    private Map<String, AccurevStream> getAllAncestorStreams(//
            final String nameOfStreamRequired, //
            final AccurevServer server, //
            final Map<String, String> accurevEnv, //
            final FilePath workspace, //
            final TaskListener listener, //
            final String accurevPath, //
            final Launcher launcher) {
        final Map<String, AccurevStream> streams = new HashMap<String, AccurevStream>();
        String streamName = nameOfStreamRequired;
        while (streamName != null && !streamName.isEmpty()) {
            final Map<String, AccurevStream> oneStream = getOneStream(streamName, server, accurevEnv, workspace,
                    listener, accurevPath, launcher);
            final AccurevStream theStream = oneStream == null ? null : oneStream.get(streamName);
            streamName = null;
            if (theStream != null) {
                if (theStream.getBasisName() != null) {
                    streamName = theStream.getBasisName();
                } else if (theStream.getBasisNumber() != null) {
                    streamName = theStream.getBasisNumber().toString();
                }
                streams.putAll(oneStream);
            }
        }
        return streams;
    }

    private Map<String, AccurevStream> getOneStream(//
            final String streamName, //
            final AccurevServer server, //
            final Map<String, String> accurevEnv, //
            final FilePath workspace, //
            final TaskListener listener, //
            final String accurevPath, //
            final Launcher launcher) {
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(accurevPath);
        cmd.add("show");
        addServer(cmd, server);
        cmd.add("-fx");
        cmd.add("-p");
        cmd.add(depot);
        cmd.add("-s");
        cmd.add(streamName);
        cmd.add("streams");
        final Map<String, AccurevStream> oneStream = AccurevLauncher.runCommand("Restricted show streams command",
                launcher, cmd, null, getOptionalLock(), accurevEnv, workspace, listener, logger,
                XmlParserFactory.getFactory(), new ParseShowStreams(), depot);
        return oneStream;
    }

    /**
     * get
     *
     * @param server
     * @param accurevEnv
     * @param workspace
     * @param listener
     * @param accurevPath
     * @param launcher
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    private List<String> getOverlaps(//
            final AccurevServer server, //
            final Map<String, String> accurevEnv, //
            final FilePath workspace, //
            final TaskListener listener, //
            final String accurevPath, //
            final Launcher launcher) throws IOException, InterruptedException {
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(accurevPath);
        cmd.add("stat");
        addServer(cmd, server);
        cmd.add("-fx");
        cmd.add("-o");
        final List<String> overlaps = AccurevLauncher.runCommand("Stat overlaps command", launcher, cmd, null,
                getOptionalLock(), accurevEnv, workspace, listener, logger, XmlParserFactory.getFactory(),
                new ParseStatOverlaps(), null);
        if (overlaps != null) {
            for (final String filename : overlaps) {
                listener.getLogger().println("Adding file to overlap list: " + filename);
            }
        }
        return overlaps;
    }

    /**
     *
     * @param server
     * @param accurevEnv
     * @param workspace
     * @param listener
     * @param accurevPath
     * @param launcher
     * @param stream
     * @param buildDate
     * @return if there are any new transactions in the stream since the last build was done
     * @throws IOException
     * @throws InterruptedException
     */
    private boolean checkStreamForChanges(AccurevServer server,
                                          Map<String, String> accurevEnv,
                                          FilePath workspace,
                                          TaskListener listener,
                                          String accurevPath,
                                          Launcher launcher,
                                          String stream,
                                          Date buildDate)
            throws IOException, InterruptedException {
        AccurevTransaction latestCodeChangeTransaction = new AccurevTransaction();
        latestCodeChangeTransaction.setDate(NO_TRANS_DATE);

        //query AccuRev for the latest transactions of each kind defined in transactionTypes using getTimeOfLatestTransaction
        String[] validTransactionTypes;
        if (server.getValidTransactionTypes() != null) {
            validTransactionTypes = server.getValidTransactionTypes().split(AccurevServer.VTT_DELIM);
            // if this is still empty, use default list
            if (validTransactionTypes.length == 0) {
                validTransactionTypes = AccurevServer.DEFAULT_VALID_TRANSACTION_TYPES.split(AccurevServer.VTT_DELIM);
            }
        }
        else {
            validTransactionTypes = AccurevServer.DEFAULT_VALID_TRANSACTION_TYPES.split(AccurevServer.VTT_DELIM);
        }

        listener.getLogger().println(//
                "Checking transactions of type " + Arrays.asList(validTransactionTypes) + //
                        " in stream [" + stream + "]");
      
        Iterator<String> affectedPaths;
        Collection<String> serverPaths;

        final String FFPSCM_DELIM = ",";
       
        Collection<String> Filter_For_Poll_SCM = null;
        
        if(filterForPollSCM != null && !(filterForPollSCM.isEmpty())){
	        String FFPSCM_LIST = filterForPollSCM;
	        FFPSCM_LIST = FFPSCM_LIST.replace(", ", ",");
	        Filter_For_Poll_SCM = new ArrayList<String>(Arrays.asList(FFPSCM_LIST.split(FFPSCM_DELIM)));
        }else{
        	if(subPath != null && !(subPath.isEmpty())){
        		String FFPSCM_LIST = subPath;
    	        FFPSCM_LIST = FFPSCM_LIST.replace(", ", ",");
    	        Filter_For_Poll_SCM = new ArrayList<String>(Arrays.asList(FFPSCM_LIST.split(FFPSCM_DELIM)));
        	}
        }
        for (final String transactionType : validTransactionTypes) {
            try {
                final AccurevTransaction tempTransaction = getLatestTransaction(server, accurevEnv, workspace,
                        listener, accurevPath, launcher, stream, transactionType);
                if (tempTransaction != null) {
                    listener.getLogger().println(
                            "Last transaction of type [" + transactionType + "] is " + tempTransaction);
                  
                    if (latestCodeChangeTransaction.getDate().before(tempTransaction.getDate())) {
                    	//check the affected
                    	serverPaths = tempTransaction.getAffectedPaths();
                    	if(tempTransaction.getAffectedPaths().size()>0){
                    		if(filterForPollSCM != null && !(filterForPollSCM.isEmpty())){
		                    		boolean buildRequired = false;
		                    		for(String filterPath: Filter_For_Poll_SCM){
		                        		affectedPaths = serverPaths.iterator();
		                                while(affectedPaths.hasNext()){
			                                	
			                                	if(affectedPaths.next().contains(filterPath)){
			                                		buildRequired = true;
			                                		break;
			                                	}
		                                	
		                                }
		                            }
		                    		if(buildRequired){
		                         	   
		                            }else{
		                         	   return false;
		                            }
	                    		}else{
	                    			if(subPath != null && !(subPath.isEmpty())){

			                    		boolean buildRequired = false;
			                    		for(String filterPath: Filter_For_Poll_SCM){
			                        		affectedPaths = serverPaths.iterator();
			                                while(affectedPaths.hasNext()){
				                                	
				                                	if(affectedPaths.next().contains(filterPath)){
				                                		buildRequired = true;
				                                		break;
				                                	}
			                                	
			                                }
			                            }
			                    		if(buildRequired){
			                         	   
			                            }else{
			                         	   return false;
			                            }		                    		
	                    			}
	                    		}
                    	}
                    }
                    latestCodeChangeTransaction = tempTransaction;
                       
                    }
               
            else {
                    listener.getLogger().println("No transactions of type [" + transactionType + "]");
                }
            }
            catch (Exception e) {
                final String msg = "getLatestTransaction failed when checking the stream " + stream + " for changes with transaction type " + transactionType;
                listener.getLogger().println(msg);
                e.printStackTrace(listener.getLogger());
                logger.log(Level.WARNING, msg, e);
            }
        }

        //log last transaction information if retrieved
        if (latestCodeChangeTransaction.getDate().equals(NO_TRANS_DATE)) {
            listener.getLogger().println("No last transaction found.");
        }
        else {
            listener.getLogger().println("Last valid trans " + latestCodeChangeTransaction);
        }

        return buildDate == null || buildDate.before(latestCodeChangeTransaction.getDate());
    }

    /**
     *
     *
     * @param server
     * @param accurevEnv
     * @param workspace
     * @param listener
     * @param accurevPath
     * @param launcher
     * @param stream
     * @param transactionType Specify what type of transaction to search for
     * @return the latest transaction of the specified type from the selected stream
     * @throws Exception
     */
    private AccurevTransaction getLatestTransaction(//
            final AccurevServer server, //
            final Map<String, String> accurevEnv, //
            final FilePath workspace, //
            final TaskListener listener, //
            final String accurevPath, //
            final Launcher launcher, //
            final String stream, //
            final String transactionType) throws Exception {
        // initialize code that extracts the latest transaction of a certain
        // type using -k flag
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(accurevPath);
        cmd.add("hist");
        addServer(cmd, server);
        cmd.add("-fx");
        cmd.add("-p");
        cmd.add(depot);
        cmd.add("-s");
        cmd.add(stream);
        cmd.add("-t");
        cmd.add("now.1");
        cmd.add("-k");
        cmd.add(transactionType);

        // execute code that extracts the latest transaction
        final List<AccurevTransaction> transaction = new ArrayList<AccurevTransaction>(1);
        final Boolean transactionFound = AccurevLauncher.runCommand("History command", launcher, cmd, null,
                getOptionalLock(), accurevEnv, workspace, listener, logger, XmlParserFactory.getFactory(),
                new ParseHistory(), transaction);
        if (transactionFound == null) {
            final String msg = "History command failed when trying to get the latest transaction of type "
                    + transactionType;
            throw new Exception(msg);
        }
        if (transactionFound.booleanValue()) {
            return transaction.get(0);
        } else {
            return null;
        }
    }

    /**
     * Helper method to retrieve include/exclude rules for a given stream.
     *
     * @return HashMap key: String path , val: String (enum) incl/excl rule type
     */
    private HashMap<String, String> getIncludeExcludeRules(//
            final AccurevServer server, //
            final Map<String, String> accurevEnv, //
            final FilePath workspace, //
            final TaskListener listener, //
            final String accurevPath, //
            final Launcher launcher, //
            final String stream) throws IOException, InterruptedException {
        listener.getLogger().println("Retrieving include/exclude rules for stream: " + stream);
        // Build the 'accurev lsrules' command
        ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(accurevPath);
        cmd.add("lsrules");
        addServer(cmd, server);
        cmd.add("-fx");
        cmd.add("-s");
        cmd.add(stream);

        // Execute 'accurev lsrules' command and save off output
        final XmlPullParserFactory parserFactory = XmlParserFactory.getFactory();
        final HashMap<String, String> locationToKindMap = AccurevLauncher.runCommand("lsrules command", launcher, cmd,
                null, getOptionalLock(), accurevEnv, workspace, listener, logger, parserFactory, new ParseLsRules(),
                null);
        if (locationToKindMap != null) {
            for (String location : locationToKindMap.keySet()) {
                final String kind = locationToKindMap.get(location);
                listener.getLogger().println("Found rule: " + kind + " for: " + location);
            }
        }

        return locationToKindMap;
    }

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

    /**
     * Adds the server reference to the Arguments list.
     *
     * @param cmd    The accurev command line.
     * @param server The Accurev server details.
     */
    private static void addServer(ArgumentListBuilder cmd, AccurevServer server) {
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
     * Gets the lock to be used on "normal" accurev commands, or
     * <code>null</code> if command synchronization is switched off.
     *
     * @return See above.
     */
    private Lock getOptionalLock() {
        final AccurevServer server = DESCRIPTOR.getServer(serverName);
        final boolean shouldLock = server.isSyncOperations();
        if (shouldLock) {
            return getMandatoryLock();
        } else {
            return null;
        }
    }

    /**
     * Gets the lock to be used on accurev commands where synchronization is
     * mandatory.
     *
     * @return See above.
     */
    private Lock getMandatoryLock() {
        return AccurevSCMDescriptor.ACCUREV_LOCK;
    }

    @Override
    public SCMRevisionState calcRevisionsFromBuild(AbstractBuild<?, ?> ab, Launcher lnchr, TaskListener tl) throws IOException, InterruptedException {
        return SCMRevisionState.NONE;
    }

    @Override
    protected PollingResult compareRemoteRevisionWith(AbstractProject<?, ?> project, Launcher launcher, FilePath workspace, TaskListener listener, SCMRevisionState scmrs) throws IOException, InterruptedException {
        if( project.isInQueue()) {
            listener.getLogger().println("Project build is currently in queue.");
            return PollingResult.NO_CHANGES;
        }
        if (workspace == null) {
            // If we're claiming not to need a workspace in order to poll, then
            // workspace will be null.  In that case, we need to run directly
            // from the project folder on the master.
            final File projectDir = project.getRootDir();
            workspace = new FilePath(projectDir);
            launcher = Hudson.getInstance().createLauncher(listener);
        }
        listener.getLogger().println("Running commands from folder \""+workspace+'"');
        AccurevServer server = DESCRIPTOR.getServer(serverName);

        final String accurevPath = workspace.act(new FindAccurevClientExe(server));

        final Map<String, String> accurevEnv = new HashMap<String, String>();

        if (!ensureLoggedInToAccurev(server, accurevEnv, workspace, listener, accurevPath, launcher)) {
            listener.getLogger().println("Authentication failure");
            return PollingResult.NO_CHANGES;
        }

        if (synctime) {
            listener.getLogger().println("Synchronizing clock with the server...");
            if (!synctime(server, accurevEnv, workspace, listener, accurevPath, launcher)) {
                listener.getLogger().println("Synchronizing clock failure");
                return PollingResult.NO_CHANGES;
            }
        }

        final Run<?, ?> lastBuild = project.getLastBuild();
        if (lastBuild == null) {
            listener.getLogger().println("Project has never been built");
            return PollingResult.BUILD_NOW;
        }
        final Date buildDate = lastBuild.getTimestamp().getTime();

        listener.getLogger().println("Last build on " + buildDate);

        final String localStream;

        if (hasStringVariableReference(this.stream)) {
            ParametersDefinitionProperty paramDefProp = (ParametersDefinitionProperty) project
                    .getProperty(ParametersDefinitionProperty.class);

            if (paramDefProp == null) {
                listener.getLogger().println(
                        "Polling is not supported when stream name has a variable reference '" + this.stream + "'.");

                // as we don't know which stream to check we just state that
                // there is no changes
                return PollingResult.NO_CHANGES;
            }

            // listener.getLogger().println("logout of parameter definitions ...");

            Map<String, String> keyValues = new TreeMap<String, String>();
    
            /* Scan for all parameter with an associated default values */
            for (ParameterDefinition paramDefinition : paramDefProp.getParameterDefinitions()) {
                // listener.getLogger().println("parameter definition for '" +
                // paramDefinition.getName() + "':");

                ParameterValue defaultValue = paramDefinition.getDefaultParameterValue();

                if (defaultValue instanceof StringParameterValue) {
                    StringParameterValue strdefvalue = (StringParameterValue) defaultValue;

                    // listener.getLogger().println("parameter default value for '"
                    // + defaultValue.getName() + " / " +
                    // defaultValue.getDescription() + "' is '" +
                    // strdefvalue.value + "'.");

                    keyValues.put(defaultValue.getName(), strdefvalue.value);
                }
            }

            final EnvVars environment = new EnvVars(keyValues);
            localStream = environment.expand(this.stream);
            listener.getLogger().println("... expanded '" + this.stream + "' to '" + localStream + "'.");
        } else {
            localStream = this.stream;
        }

        if (hasStringVariableReference(localStream)) {
            listener.getLogger().println(
                    "Polling is not supported when stream name has a variable reference '" + this.stream + "'.");

            // as we don't know which stream to check we just state that there
            // is no changes
            return PollingResult.NO_CHANGES;
        }

        final Map<String, AccurevStream> streams = this.ignoreStreamParent ? null : getStreams(localStream, server,
                accurevEnv, workspace, listener, accurevPath, launcher);
        AccurevStream stream = streams == null ? null : streams.get(localStream);

        if (stream == null) {
            // if there was a problem, fall back to simple stream check
            if (checkStreamForChanges(server, accurevEnv, workspace, listener, accurevPath, launcher, localStream,
                    buildDate)){
                return PollingResult.BUILD_NOW;
            }else{
                return PollingResult.NO_CHANGES;
            }
        }
        // There may be changes in a parent stream that we need to factor in.
        do {
            if (checkStreamForChanges(server, accurevEnv, workspace, listener, accurevPath, launcher, stream.getName(),
                    buildDate)) {
                return PollingResult.BUILD_NOW;
            }
            stream = stream.getParent();
        } while (stream != null && stream.isReceivingChangesFromParent());
        return PollingResult.NO_CHANGES;
    }
  
// -------------------------- INNER CLASSES --------------------------

    public static final class AccurevSCMDescriptor extends SCMDescriptor<AccurevSCM> implements ModelObject {
  
    	
        /**
         * The accurev server has been known to crash if more than one copy of the accurev has been run concurrently on
         * the local machine.
         * <br>
         * Also, the accurev client has been known to complain that it's not logged in if another
         * client on the same machine logs in again.
         */
        transient static final Lock ACCUREV_LOCK = new ReentrantLock();
        private List<AccurevServer> servers;
       
        private static final Logger descriptorlogger = Logger.getLogger(AccurevSCMDescriptor.class.getName());
        
        
   
        private boolean pollOnMaster;
        
       
        AccurevServer serverforcmdexec;
        String accurevPath;
        
        /**
         * Constructs a new AccurevSCMDescriptor.
         */
        protected AccurevSCMDescriptor() {
            super(AccurevSCM.class, null);
            load();
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getDisplayName() {
            
        	return "AccuRev";
            
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            servers = req.bindJSONToList(AccurevServer.class, formData.get("server"));
            pollOnMaster = req.getParameter("descriptor.pollOnMaster") != null;
            save();
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public SCM newInstance(StaplerRequest req, JSONObject formData) throws FormException {
        	
            return new AccurevSCM( //
            		globalServerName, //
            		globalDepotName, //
            		globalStreamName, //
            		req,
                    req.getParameter("accurev.useReftree") != null, //
                    req.getParameter("accurev.reftree"), //
                    req.getParameter("accurev.subPath"), //
                    req.getParameter("accurev.filterForPollSCM"), //
                    req.getParameter("accurev.synctime") != null, //
                    req.getParameter("accurev.cleanreftree") != null, //  
                    req.getParameter("accurev.useSnapshot") != null, //
                    req.getParameter("accurev.snapshotNameFormat"), //
                    req.getParameter("accurev.directoryOffset"), //
                    req.getParameter("accurev.ignoreStreamParent") != null);
        }

        /**
         * Getter for property 'servers'.
         *
         * @return Value for property 'servers'.
         */
        public List<AccurevServer> getServers() {
            if (servers == null) {
                servers = new ArrayList<AccurevServer>();
            }
            return servers;
        }

        /**
         * Setter for property 'servers'.
         *
         * @param servers Value to set for property 'servers'.
         */
        public void setServers(List<AccurevServer> servers) {
            this.servers = servers;
        }

        /**
         * Getter for property 'pollOnMaster'.
         *
         * @return Value for property 'pollOnMaster'.
         */
        public boolean isPollOnMaster() {
            return pollOnMaster;
        }

        /**
         * Setter for property 'pollOnMaster'.
         *
         * @param servers Value to set for property 'pollOnMaster'.
         */
        public void setPollOnMaster(boolean pollOnMaster) {
            this.pollOnMaster = pollOnMaster;
        }

        public AccurevServer getServer(String name) {
            if (name == null) {
                return null;
            }
            for (AccurevServer server : servers) {
                if (name.equals(server.getName())) {
                    return server;
                }
            }
            return null;
        }

       
        //This method will populate the servers in the select box
        public ListBoxModel doFillServerNameItems(@QueryParameter String serverName) {  
            ListBoxModel s = new ListBoxModel();
            for (AccurevServer server : servers)
            {            		
            	s.add(server.getName(), server.getName());            		
            }
            globalServerName = serverName; 
            return s;
        }
        private static String getExistingPath(String[] paths, String fallback) {
            for (final String path : paths) {
                if (new File(path).exists()) {
                    return path;
                }
            }
            // just hope it's on the environment's path
            return fallback;
        }
        //Adding -H Host:Port to the command
        private static void addServer(List<String> cmd, AccurevServer server) {
            if (null != server && null != server.getHost() && !"".equals(server.getHost())) {
                cmd.add("-H");
                if (server.getPort() != 0) {                	
                    cmd.add(server.getHost() + ":" + server.getPort());
                } else {
                    cmd.add(server.getHost());
                }
            }
        }
        //Converting the InputStream object to String
        static String convertStreamToString(java.io.InputStream is) {
        	String stream = "";
    	    java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
    	    stream = s.hasNext() ? s.next() : "";
    	    s.close();
    	    return stream;
    	}
        
        //This method will populate the depots in the select box depending upon the server selected. 
        public ListBoxModel doFillDepotItems(@QueryParameter String serverName,@QueryParameter String depot) {
        	final AccurevServer server = getServer(serverName);
            
            if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
                // we are running on windows
        		accurevPath= getExistingPath(server.getWinCmdLocations(), "accurev.exe");
            } else {
                // we are running on *nix
            	accurevPath= getExistingPath(server.getNixCmdLocations(), "accurev");
            }
          
        	ListBoxModel d = null;
        	Option temp = null;
        	List<String> depots = new ArrayList<String>();
        	List<String> logincommand = new ArrayList<String>();
        	logincommand.add(accurevPath);
        	logincommand.add("login");
        	addServer(logincommand,server);         	
           
            if (server.isUseNonexpiringLogin()) {
            	logincommand.add("-n");
            }
            logincommand.add(server.getUsername());
            //If the password is blank, "" should be passed 
            logincommand.add(server.getPassword().length()==0 ? "\"\"" : server.getPassword());
        	
            ProcessBuilder processBuilder = new ProcessBuilder(logincommand);
            processBuilder.redirectErrorStream(true);
     
            Process loginprocess;
            InputStream stdout = null;
            InputStream stdout1 = null;
            //Execute the login command first & upon success of that run show depots command. If any of the command's exitvalue is 1 proper error message is logged
			try {
				loginprocess = processBuilder.start();
	            stdout1 = loginprocess.getInputStream();
	            String logincmdoutputdata = convertStreamToString(stdout1);
	            loginprocess.waitFor();
		            if(loginprocess.exitValue()==0){
			            
			            List<String> command2 = new ArrayList<String>();
			            command2.add("accurev");
			            command2.add("show");		
			            addServer(command2,server);		
			            command2.add("-fx");
			            command2.add("depots");		            
			           
			            ProcessBuilder processBuilder2 = new ProcessBuilder(command2);		   
			            processBuilder2.redirectErrorStream(true);
			           
			            Process depotprocess = processBuilder2.start();
			            stdout = depotprocess.getInputStream();
			            String showcmdoutputdata = convertStreamToString(stdout);
			            depotprocess.waitFor();
			            if(depotprocess.exitValue()==0){
			    			 DocumentBuilderFactory factory  = DocumentBuilderFactory.newInstance();
			    		        DocumentBuilder parser;
			    		        parser = factory.newDocumentBuilder();
		    					Document doc= parser.parse(new ByteArrayInputStream(showcmdoutputdata.getBytes()));
		    					doc.getDocumentElement().normalize();			 
		    					NodeList nList = doc.getElementsByTagName("Element");		 
		    					for (int i=0; i<nList.getLength(); i++) {
		    						Node nNode = nList.item(i);
		    						if (nNode.getNodeType() == Node.ELEMENT_NODE) {
		    							Element eElement = (Element) nNode;
		    							depots.add(eElement.getAttribute("Name"));
		    								 
		    						}
		    					}
			            
			            }else{
			            	descriptorlogger.info(showcmdoutputdata);
			            }
						
						
						
		            }else{
		            	descriptorlogger.info(logincmdoutputdata);
		            }
	           
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ParserConfigurationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SAXException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
				try {
					stdout.close();
					stdout1.close();
				} catch (IOException e) {}
			}
            
        	d = new ListBoxModel();
            for (String dname : depots)
            {            		
            	d.add(dname, dname);            		           		
            }          	
            //Below while loop is for to retain the selected item when you open the Job to reconfigure
            Iterator<Option> depotsIter = d.iterator();
            while (depotsIter.hasNext()){
               	temp = depotsIter.next();
            	if(depot.equals(temp.name))
            	{
            		temp.selected = true;
            	}
            }
            globalDepotName = depot;
            return d;
        }
        
        //Populating the streams 
        //This method will populate the depots in the select box depending upon the server selected. 
        public ListBoxModel doFillStreamItems(@QueryParameter String serverName,@QueryParameter String depot,@QueryParameter String stream) {
        	
        	List<PopulateStreams> streams = new ArrayList<PopulateStreams>();
        	final AccurevServer server = getServer(serverName);
            
            if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
                // we are running on windows
        		accurevPath= getExistingPath(server.getWinCmdLocations(), "accurev.exe");
            } else {
                // we are running on *nix
            	accurevPath= getExistingPath(server.getNixCmdLocations(), "accurev");
            }
          
        	ListBoxModel s = null;
        	Option preselected = null;
        	
        	List<String> logincommand = new ArrayList<String>();
        	logincommand.add(accurevPath);
        	logincommand.add("login");
        	addServer(logincommand,server);         	
           
            if (server.isUseNonexpiringLogin()) {
            	logincommand.add("-n");
            }
            logincommand.add(server.getUsername());
            //If the password is blank, "" should be passed 
            logincommand.add(server.getPassword().length()==0 ? "\"\"" : server.getPassword());
        	
            ProcessBuilder processBuilder = new ProcessBuilder(logincommand);
            processBuilder.redirectErrorStream(true);
     
            Process loginprocess;
            InputStream stdout = null;
            InputStream stdout1 = null;
            //Execute the login command first & upon success of that run show streams command. If any of the command's exitvalue is 1 proper error message is logged
			try {
				loginprocess = processBuilder.start();
	            stdout1 = loginprocess.getInputStream();
	            String logincmdoutputdata = convertStreamToString(stdout1);
	            loginprocess.waitFor();
		            if(loginprocess.exitValue()==0){
			            
			            List<String> command2 = new ArrayList<String>();
			            command2.add("accurev");
			            command2.add("show");		
			            addServer(command2,server);		
			            command2.add("-fx");
			            command2.add("-p");
			            command2.add(depot);
			            command2.add("streams");	
			            			            		            
			            ProcessBuilder processBuilder2 = new ProcessBuilder(command2);		   
			            processBuilder2.redirectErrorStream(true);
			           
			            Process streamprocess = processBuilder2.start();
			            stdout = streamprocess.getInputStream();
			            String showcmdoutputdata = convertStreamToString(stdout);
			            streamprocess.waitFor();
			            if(streamprocess.exitValue()==0){
			    			 DocumentBuilderFactory factory  = DocumentBuilderFactory.newInstance();
			    		        DocumentBuilder parser;
			    		        parser = factory.newDocumentBuilder();
		    					Document doc= parser.parse(new ByteArrayInputStream(showcmdoutputdata.getBytes()));
		    					doc.getDocumentElement().normalize();			 
		    					NodeList nList = doc.getElementsByTagName("stream");		 
		    					for (int i=0; i<nList.getLength(); i++) {
		    						Node nNode = nList.item(i);
		    						if (nNode.getNodeType() == Node.ELEMENT_NODE) {
		    							Element eElement = (Element) nNode;
		    							if(!(eElement.getAttribute("type").equals("workspace"))){
			    							PopulateStreams as=new PopulateStreams(eElement.getAttribute("name"),eElement.getAttribute("streamNumber"));		    							
			    							streams.add(as);
			    							as = null;
		    							}
		    						}
		    					}
		    					Collections.sort(streams);
			            
			            }else{
			            	descriptorlogger.info(showcmdoutputdata);
			            }
						
						
						
		            }else{
		            	descriptorlogger.info(logincmdoutputdata);
		            }
	           
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (ParserConfigurationException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (SAXException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally {
				try {
					stdout.close();
					stdout1.close();
				} catch (IOException e) {}
			}
            
        	s = new ListBoxModel();
        	for (PopulateStreams sname : streams) 
            {            		
            	s.add(sname.getName(), sname.getName());            		           		
            }          	
            //Below while loop is for to retain the selected item when you open the Job to reconfigure
            Iterator<Option> streamsIter = s.iterator();
            while (streamsIter.hasNext()){
               	preselected = streamsIter.next();
            	if(stream.equals(preselected.name))
            	{
            		preselected.selected = true;
            	}
            }
            
            globalStreamName = stream;
            return s;
        }
      
       
        
    }

    /**
     * Bean that contains the definition of an accurev server, as contained by
     * the global configuration.
     */
    public static final class AccurevServer implements Serializable {

        private String name;
        private String host;
        private int port;
        private String username;
        private String password;
        private transient List<String> winCmdLocations;
        private transient List<String> nixCmdLocations;
        private String validTransactionTypes;
        private boolean syncOperations;
        private boolean minimiseLogins;
        private boolean useNonexpiringLogin;
        private boolean useRestrictedShowStreams;

        /**
         * The default search paths for Windows clients.
         */
        private static final List<String> DEFAULT_WIN_CMD_LOCATIONS = Arrays.asList(//
                "C:\\Program Files\\AccuRev\\bin\\accurev.exe", //
                "C:\\Program Files (x86)\\AccuRev\\bin\\accurev.exe", //
                "C:\\opt\\accurev\\bin\\accurev.exe");

        /**
         * The default search paths for *nix clients
         */
        private static final List<String> DEFAULT_NIX_CMD_LOCATIONS = Arrays.asList(//
                "/usr/local/bin/accurev", //
                "/usr/bin/accurev", //
                "/bin/accurev", //
                "/local/bin/accurev", //
                "/opt/accurev/bin/accurev");

        private static final String VTT_DELIM = ",";
        // keep all transaction types in a set for validation
        private static final String VTT_LIST = "add,chstream,co,defcomp,defunct,keep,mkstream,move,promote,purge,dispatch";
        private static final Set<String> VALID_TRANSACTION_TYPES = new HashSet<String>(Arrays.asList(VTT_LIST
                .split(VTT_DELIM)));
        private static final String DEFAULT_VALID_TRANSACTION_TYPES = "add,chstream,co,defcomp,defunct,keep,mkstream,move,promote";

        /**
         * Constructs a new AccurevServer.
         */
        public AccurevServer() {
            this.winCmdLocations = new ArrayList<String>(DEFAULT_WIN_CMD_LOCATIONS);
            this.nixCmdLocations = new ArrayList<String>(DEFAULT_NIX_CMD_LOCATIONS);
        }

        @DataBoundConstructor
        public AccurevServer(//
                String name, //
                String host, //
                int port, //
                String username, //
                String password, //
                String validTransactionTypes, //
                boolean syncOperations, //
                boolean minimiseLogins, //
                boolean useNonexpiringLogin, //
                boolean useRestrictedShowStreams) {
            this();
            this.name = name;
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = Password.obfuscate(password);
            this.validTransactionTypes = validTransactionTypes;
            this.syncOperations = syncOperations;
            this.minimiseLogins = minimiseLogins;
            this.useNonexpiringLogin = useNonexpiringLogin;
            this.useRestrictedShowStreams = useRestrictedShowStreams;
        }

        /**
         * When f:repeatable tags are nestable, we can change the advances page of the server config to
         * allow specifying these locations... until then this hack!
         * @return This.
         * @throws ObjectStreamException
         */
        private Object readResolve() throws ObjectStreamException {
            if (winCmdLocations == null) {
                winCmdLocations = new ArrayList<String>(DEFAULT_WIN_CMD_LOCATIONS);
            }
            if (nixCmdLocations == null) {
                nixCmdLocations = new ArrayList<String>(DEFAULT_NIX_CMD_LOCATIONS);
            }
            return this;
        }

        /**
         * Getter for property 'name'.
         *
         * @return Value for property 'name'.
         */
        public String getName() {
            return name;
        }

        /**
         * Getter for property 'host'.
         *
         * @return Value for property 'host'.
         */
        public String getHost() {
            return host;
        }

        /**
         * Getter for property 'port'.
         *
         * @return Value for property 'port'.
         */
        public int getPort() {
            return port;
        }

        /**
         * Getter for property 'username'.
         *
         * @return Value for property 'username'.
         */
        public String getUsername() {
            return username;
        }

        /**
         * Getter for property 'password'.
         *
         * @return Value for property 'password'.
         */
        public String getPassword() {
            return Password.deobfuscate(password);
        }

        /**
         * Getter for property 'nixCmdLocations'.
         *
         * @return Value for property 'nixCmdLocations'.
         */
        public String[] getNixCmdLocations() {
            return nixCmdLocations.toArray(new String[nixCmdLocations.size()]);
        }

        /**
         * Getter for property 'winCmdLocations'.
         *
         * @return Value for property 'winCmdLocations'.
         */
        public String[] getWinCmdLocations() {
            return winCmdLocations.toArray(new String[winCmdLocations.size()]);
        }

        /**
         *
         * @return returns the currently set transaction types that are seen as
         *         valid for triggering builds and whos authors get notified
         *         when a build fails
         */
        public String getValidTransactionTypes() {
            return validTransactionTypes;
        }

        /**
         *
         * @param validTransactionTypes
         *            the currently set transaction types that are seen as valid
         *            for triggering builds and whos authors get notified when a
         *            build fails
         */
        public void setValidTransactionTypes(String validTransactionTypes) {
            this.validTransactionTypes = validTransactionTypes;
        }

        public boolean isSyncOperations() {
            return syncOperations;
        }

        public void setSyncOperations(boolean syncOperations) {
            this.syncOperations = syncOperations;
        }

        public boolean isMinimiseLogins() {
            return minimiseLogins;
        }

        public void setMinimiseLogins(boolean minimiseLogins) {
            this.minimiseLogins = minimiseLogins;
        }

        public boolean isUseNonexpiringLogin() {
            return useNonexpiringLogin;
        }

        public void setUseNonexpiringLogin(boolean useNonexpiringLogin) {
            this.useNonexpiringLogin = useNonexpiringLogin;
        }

        public boolean isUseRestrictedShowStreams() {
            return useRestrictedShowStreams;
        }

        public void setUseRestrictedShowStreams(boolean useRestrictedShowStreams) {
            this.useRestrictedShowStreams = useRestrictedShowStreams;
        }

        public FormValidation doValidTransactionTypesCheck(@QueryParameter String value)//
                throws IOException, ServletException {
            final String[] formValidTypes = value.split(VTT_DELIM);
            for (final String formValidType : formValidTypes) {
                if (!VALID_TRANSACTION_TYPES.contains(formValidType)) {
                    return FormValidation.error("Invalid transaction type [" + formValidType + "]. Valid types are: "
                            + VTT_LIST);
                }
            }
            return FormValidation.ok();
        }
    }

    /**
     * Class responsible for parsing change-logs recorded by the builds.
     * If this is renamed or moved it'll break data-compatibility with old builds.
     */
    private static final class AccurevChangeLogParser extends ParseChangeLog {
    }
}
