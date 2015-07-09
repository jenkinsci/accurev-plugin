package hudson.plugins.accurev;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.ModelObject;
import hudson.model.ParameterValue;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Hudson;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.plugins.accurev.cmd.ChangeLogCmd;
import hudson.plugins.accurev.cmd.Command;
import hudson.plugins.accurev.cmd.History;
import hudson.plugins.accurev.cmd.JustAccurev;
import hudson.plugins.accurev.cmd.Login;
import hudson.plugins.accurev.cmd.PopulateCmd;
import hudson.plugins.accurev.cmd.SetProperty;
import hudson.plugins.accurev.cmd.ShowDepots;
import hudson.plugins.accurev.cmd.ShowStreams;
import hudson.plugins.accurev.cmd.Synctime;
import hudson.plugins.jetty.security.Password;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.EditType;
import hudson.scm.PollingResult;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.scm.SCM;
import hudson.util.ArgumentListBuilder;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.ListBoxModel.Option;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * @author connollys
 * @since 09-Oct-2007 16:17:34
 */
public class AccurevSCM extends SCM {

// ------------------------------ FIELDS ------------------------------

    public static final SimpleDateFormat ACCUREV_DATETIME_FORMATTER = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");

    @Extension
    public static final AccurevSCMDescriptor DESCRIPTOR = new AccurevSCMDescriptor();
    private static final Logger logger = Logger.getLogger(AccurevSCM.class.getName());
    static final Date NO_TRANS_DATE = new Date(0);
    private static final String DEFAULT_SNAPSHOT_NAME_FORMAT = "${JOB_NAME}_${BUILD_NUMBER}";
    private final String serverName;
    private final String depot;
    private final String stream;   
    private final boolean ignoreStreamParent;
    private final String wspaceORreftree;
    private boolean useReftree;
    private boolean useWorkspace;
    private boolean noWspaceNoReftree;
    private final boolean cleanreftree;
    private final String workspace;
    private final boolean useSnapshot;
    private final boolean dontPopContent;
    private final String snapshotNameFormat;
    private final boolean synctime;
    private final String reftree;
    private final String subPath;
    private final String filterForPollSCM;
    private final String directoryOffset;

// --------------------------- CONSTRUCTORS ---------------------------

    /**
     * Our constructor.
     */
    @DataBoundConstructor
    public AccurevSCM(String serverName,
                      String depot,
                      String stream,                     
                      String wspaceORreftree,
                      String workspace,                                           
                      String reftree,                      
                      String subPath,
                      String filterForPollSCM,
                      boolean synctime,
                      boolean cleanreftree,  
                      boolean useSnapshot,
                      boolean dontPopContent,
                      String snapshotNameFormat,
                      String directoryOffset, 
                      boolean ignoreStreamParent) {
        super();
        this.serverName = serverName;
        this.depot = depot;
        this.stream = stream;        
        this.wspaceORreftree = wspaceORreftree;
        this.workspace = workspace;        
        this.reftree = reftree;
        this.subPath = subPath;
        this.filterForPollSCM = filterForPollSCM;
        this.synctime = synctime;
        this.cleanreftree = cleanreftree;
        this.useSnapshot = useSnapshot;
        this.dontPopContent = dontPopContent;
        this.snapshotNameFormat = snapshotNameFormat;
        this.ignoreStreamParent = ignoreStreamParent;
        this.directoryOffset = directoryOffset;
        isWspaceORreftree();
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
     * Getter for property 'wspaceORreftree'.
     *
     * @return Value for property 'wspaceORreftree'.
     */
    public String getWspaceORreftree() {    	
        return wspaceORreftree;
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
     * Getter for property 'workspace'.
     *
     * @return Value for property 'workspace'.
     */
    public String getWorkspace() {
        return workspace;
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
     * Getter for property 'synctime'.
     *
     * @return Value for property 'synctime'.
     */
    
    
    public void isWspaceORreftree() {
    	String wspaceORreftree = getWspaceORreftree();
    	if(wspaceORreftree!=null && !wspaceORreftree.isEmpty()){
	    	if(wspaceORreftree.equals("wspace"))
	    	{
	    		useReftree = false;
	    	    useWorkspace = true;
	    	    noWspaceNoReftree = false;    		
	    	}else if(wspaceORreftree.equals("reftree")){
	    		useReftree = true;
	    	    useWorkspace = false;
	    	    noWspaceNoReftree = false;
	    	}else{
	    		useReftree = false;
	    	    useWorkspace = false;
	    	    noWspaceNoReftree = true;
	    	}
    	}else{
    		useReftree = false;
    	    useWorkspace = false;
    	    noWspaceNoReftree = true;
    	}
    	
    }
  
	public boolean isDontPopContent() {
		return dontPopContent;
	}

	/**
     * Getter for property 'cleanreftree'.
     *
     * @return Value for property 'cleanreftree'.
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
     * Getter for property 'useWorkspace'.
     *
     * @return Value for property 'useWorkspace'.
     */
    public boolean isUseWorkspace() {
        return useWorkspace;
    }
    
    /**
     * Getter for property 'noWspaceNoReftree'.
     *
     * @return Value for property 'noWspaceNoReftree'.
     */
    public boolean isNoWspaceNoReftree() {
        return noWspaceNoReftree;
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
        AccurevServer acserver = DESCRIPTOR.getServer(serverName);
        // add various accurev-specific variables to the environment
        if (depot != null)
            env.put("ACCUREV_DEPOT", depot);
        else
        	env.put("ACCUREV_DEPOT", "");
        
        if (stream != null)
            env.put("ACCUREV_STREAM", stream);
        else
        	env.put("ACCUREV_STREAM", "");
        
        if (serverName != null)
            env.put("ACCUREV_SERVER", serverName);
        else
        	env.put("ACCUREV_SERVER", "");
        
        if (acserver != null && acserver.getHost() != null)
        	env.put("ACCUREV_SERVER_HOSTNAME", acserver.getHost());
        else
        	env.put("ACCUREV_SERVER_HOSTNAME", "");
        
        if (acserver != null && acserver.getPort() > 0) 
        	env.put("ACCUREV_SERVER_PORT", Integer.toString(acserver.getPort()));
        else
        	env.put("ACCUREV_SERVER_PORT", "");
        
        if (useWorkspace && workspace != null)
            env.put("ACCUREV_WORKSPACE", workspace);
        else
        	env.put("ACCUREV_WORKSPACE", "");
        
        if (reftree != null && useReftree)
            env.put("ACCUREV_REFTREE", reftree);
        else
        	env.put("ACCUREV_REFTREE", "");
        
        if (subPath != null)
            env.put("ACCUREV_SUBPATH", subPath);
        else
        	env.put("ACCUREV_SUBPATH", "");
        
        // grab the last promote transaction from the changelog file
        String lastTransaction = null;
        // Abstract should have this since checkout should have already run
        ChangeLogSet<AccurevTransaction> changeSet = (ChangeLogSet<AccurevTransaction>) build.getChangeSet();
        if (!changeSet.isEmptySet()) {
            // first EDIT entry should be the last transaction we want
            for (Object o : changeSet.getItems()) {
                AccurevTransaction t = (AccurevTransaction) o;
                if (t.getEditType() == EditType.EDIT) { // this means promote or chstream in AccuRev
                   lastTransaction = t.getId();
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
      } else {
         env.put("ACCUREV_LAST_TRANSACTION", "");
      }

      // ACCUREV_HOME is added to the build env variables
      if (System.getenv("ACCUREV_HOME") != null)
         env.put("ACCUREV_HOME", System.getenv("ACCUREV_HOME"));
    }
    
    /**
     * Starting with a given stream, walk the hierarchy back looking for the first stream with changes. Once this stream is found
     * add it's changes and any changes in its parent streams to the list of changes.
     *  
     * @param server
     * @param accurevEnv
     * @param accurevWorkingSpace
     * @param listener
     * @param accurevClientExePath
     * @param launcher
     * @param startDateOfPopulate
     * @param startTime
     * @param stream
     * @param changelogFile
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    private boolean getChangesFromStreams(final AccurevServer server, final Map<String, String> accurevEnv, 
          final FilePath accurevWorkingSpace, BuildListener listener, final String accurevClientExePath, Launcher launcher, final Date startDateOfPopulate, 
          final Calendar startTime, AccurevStream stream, File changelogFile) throws IOException, InterruptedException {
       // There may be changes in a parent stream that we need to factor in.
       boolean foundChange = false;
       List<String> changedStreams = new ArrayList<String>();

       // Walk stream hierarchy trying to find first stream with changes...
       do {
         foundChange = CheckForChanges.checkStreamForChanges(server, accurevEnv, accurevWorkingSpace, listener, accurevClientExePath, launcher, stream,
               startTime == null ? null : startTime.getTime(), logger, this);

         if ( !foundChange ) stream = stream.getParent();
       } while (!foundChange && stream != null && stream.isReceivingChangesFromParent());

       if ( !foundChange ) return false; // No changes were found.
       
       // Found 1st stream with change, Now get changes and loop all parents
       boolean capturedChangelog = false;
       do {
           File streamChangeLog = XmlConsolidateStreamChangeLog.getStreamChangeLogFile(changelogFile, stream);
           capturedChangelog = ChangeLogCmd.captureChangelog(server, accurevEnv, accurevWorkingSpace, listener, accurevClientExePath, launcher,
                   startDateOfPopulate, startTime == null ? null : startTime.getTime(), stream.getName(), streamChangeLog, logger, this);
           if (capturedChangelog) {
               changedStreams.add(streamChangeLog.getName());
           }
           stream = stream.getParent();
       } while (stream != null && stream.isReceivingChangesFromParent() && capturedChangelog && startTime != null);
       
      XmlConsolidateStreamChangeLog.createChangeLog(changedStreams, changelogFile);
      return capturedChangelog;
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

        if (!Login.ensureLoggedInToAccurev(server, accurevEnv, jenkinsWorkspace, listener, accurevClientExePath,
                launcher)) {
            return false;
        }

        if (synctime) {
            listener.getLogger().println("Synchronizing clock with the server...");
            if (!Synctime.synctime(this, server, accurevEnv, jenkinsWorkspace, listener, accurevClientExePath, launcher)) {
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
        environment.put("ACCUREV_CLIENT_PATH", accurevClientExePath);

        String localStream = environment.expand(stream);

        listener.getLogger().println("Getting a list of streams...");
        final Map<String, AccurevStream> streams = ShowStreams.getStreams(this, localStream, server, accurevEnv, jenkinsWorkspace, listener, accurevClientExePath,
                launcher);

        if (streams != null && !streams.containsKey(localStream)) {
            listener.fatalError("The specified stream, '" + localStream + "' does not appear to exist!");
            return false;
        }
        if (useReftree && (this.reftree == null || "".equals(this.reftree))) {
            listener.fatalError("Must specify a reference tree");
            return false;
        }
     
        Date  startDateOfPopulate = null;
        if (useWorkspace){// _accurevWorkspace != null && _accurevWorkspace.is_useAccurevWorkspace() ) {
        	AccuRevWorkspaceProcessor acWspace = new AccuRevWorkspaceProcessor(this);
           boolean result = acWspace.checkoutWorkspace( this, launcher, listener, server, accurevEnv, jenkinsWorkspace, accurevClientExePath, accurevWorkingSpace, streams, localStream );
           if (!result) return result;
           startDateOfPopulate = acWspace.get_startDateOfPopulate(); 
           listener.getLogger().println("Calculating latest transaction info for workspace: " + acWspace._accurevWorkspace + ".");
        } else if (useReftree) {
           AccuRevRefTreeProcessor rTree = new AccuRevRefTreeProcessor(this);
           boolean result = rTree.checkoutRefTree( this, launcher, listener, server, accurevEnv, jenkinsWorkspace, accurevClientExePath, accurevWorkingSpace, streams );
           if (!result) return result;
           startDateOfPopulate = rTree.get_startDateOfPopulate();           
        }  else if ( isUseSnapshot() ) {
            final String snapshotName = calculateSnapshotName(build, listener);
            listener.getLogger().println("Creating snapshot: " + snapshotName + "...");
            build.getEnvironment(listener).put("ACCUREV_SNAPSHOT", snapshotName);
            // snapshot command: accurev mksnap -H <server> -s <snapshotName> -b <backing_stream> -t now
            final ArgumentListBuilder mksnapcmd = new ArgumentListBuilder();
            mksnapcmd.add(accurevClientExePath);
            mksnapcmd.add("mksnap");
            Command.addServer(mksnapcmd, server);
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
            
         if ( !isDontPopContent() ) {
            PopulateCmd pop = new PopulateCmd();
            if ( pop.populate(this, launcher, listener, server, accurevClientExePath, snapshotName, true, "from workspace", accurevWorkingSpace, accurevEnv) ) {
               startDateOfPopulate = pop.get_startDateOfPopulate();
             } else {
               return false;
             } 
          } else{
        	  startDateOfPopulate = new Date();
          }
            listener.getLogger().println("Calculating latest transaction info for stream: " + localStream + ".");
        } else {
        	/*Change the background color of the stream to white as default, this background color can be optionally changed by the users to green/red upon build success/failure
             *using post build action plugins.
             */
             {
          	   //For AccuRev 6.0.x versions
          	   SetProperty.setproperty(this, accurevWorkingSpace, listener, accurevClientExePath, launcher, accurevEnv, server, localStream, "#FFFFFF", "style");
          	   
               //For AccuRev 6.1.x onwards
               SetProperty.setproperty(this, accurevWorkingSpace, listener, accurevClientExePath, launcher, accurevEnv, server, localStream, "#FFFFFF", "streamStyle");                
             }
        
         listener.getLogger().println("Don't Pop Content checkbox is :"+isDontPopContent());   
        if ( !isDontPopContent() ) {
           PopulateCmd pop = new PopulateCmd();
           if ( pop.populate(this, launcher, listener, server, accurevClientExePath, localStream, true, "from jenkins workspace", accurevWorkingSpace, accurevEnv) ) {
              startDateOfPopulate = pop.get_startDateOfPopulate();
             } else {
              return false;
            }  
          }else{
        	   startDateOfPopulate = new Date();        	  
          }
        
           listener.getLogger().println("Calculating latest transaction info for stream: " + localStream + ".");
        }
        
        
      try {
         if (useWorkspace) {
            localStream = this.workspace;
         }
        
         AccurevTransaction latestTransaction = History.getLatestTransaction(this, 
               server, accurevEnv, accurevWorkingSpace, listener, accurevClientExePath, launcher, localStream, null);
         if (latestTransaction == null) {
            throw new NullPointerException("The 'hist' command did not return a transaction. Does this stream have any history yet?");
         }
         String latestTransactionID = latestTransaction.getId();
         String latestTransactionDate = ACCUREV_DATETIME_FORMATTER.format(latestTransaction.getDate());
         latestTransactionDate = latestTransactionDate == null ? "1970/01/01 00:00:00" : latestTransactionDate;
         listener.getLogger().println("Latest Transaction ID: " + latestTransactionID);
         listener.getLogger().println("Latest transaction Date: " + latestTransactionDate);

         {
            environment.put("ACCUREV_LATEST_TRANSACTION_ID", latestTransactionID);
            environment.put("ACCUREV_LATEST_TRANSACTION_DATE", latestTransactionDate);

            build.addAction(new AccuRevHiddenParametersAction(environment));
         }

      } catch (Exception e) {
         listener.error("There was a problem getting the latest transaction info from the stream.");
         e.printStackTrace(listener.getLogger());
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
                
        AccurevStream stream = streams == null ? null : streams.get(localStream);
        if (stream == null) {
            // if there was a problem, fall back to simple stream check
            return ChangeLogCmd.captureChangelog(server, accurevEnv, accurevWorkingSpace, listener, accurevClientExePath, launcher,
            		startDateOfPopulate, startTime == null ? null : startTime.getTime(),
                    localStream, changelogFile, logger, this);
        }
        
        if (!getChangesFromStreams(server, accurevEnv, accurevWorkingSpace, listener, accurevClientExePath, launcher,
        		startDateOfPopulate, startTime, stream, changelogFile)) {
           return ChangeLogCmd.captureChangelog(server, accurevEnv, accurevWorkingSpace, listener, accurevClientExePath, launcher, startDateOfPopulate,
                 startTime == null ? null : startTime.getTime(), localStream, changelogFile, logger, this);
        }
        return true;
    }
    
	private String calculateSnapshotName(final AbstractBuild<?, ?> build,
            final BuildListener listener) throws IOException, InterruptedException {
        final String actualFormat = (snapshotNameFormat == null || snapshotNameFormat
                .trim().isEmpty()) ? DEFAULT_SNAPSHOT_NAME_FORMAT : snapshotNameFormat.trim();
        final EnvVars environment = build.getEnvironment(listener);
        final String snapshotName = environment.expand(actualFormat);
        return snapshotName;
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
   
    /**
     * Gets the lock to be used on "normal" accurev commands, or
     * <code>null</code> if command synchronization is switched off.
     *
     * @return See above.
     */
    public Lock getOptionalLock() {
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

        if (!Login.ensureLoggedInToAccurev(server, accurevEnv, workspace, listener, accurevPath, launcher)) {
            listener.getLogger().println("Authentication failure");
            return PollingResult.NO_CHANGES;
        }

        if (synctime) {
            listener.getLogger().println("Synchronizing clock with the server...");
            if (!Synctime.synctime(this, server, accurevEnv, workspace, listener, accurevPath, launcher)) {
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
        if(!useWorkspace){
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

            Map<String, String> keyValues = new TreeMap<String, String>();
    
            /* Scan for all parameter with an associated default values */
            for (ParameterDefinition paramDefinition : paramDefProp.getParameterDefinitions()) {
                
                ParameterValue defaultValue = paramDefinition.getDefaultParameterValue();

                if (defaultValue instanceof StringParameterValue) {
                    StringParameterValue strdefvalue = (StringParameterValue) defaultValue;
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
        } else{
        	localStream = this.workspace;
        }
        final Map<String, AccurevStream> streams = this.ignoreStreamParent ? null : ShowStreams.getStreams(this, localStream, server,
                accurevEnv, workspace, listener, accurevPath, launcher);
        AccurevStream stream = streams == null ? null : streams.get(localStream);

        if (stream == null) {
            // if there was a problem, fall back to simple stream check
            if (CheckForChanges.checkStreamForChanges(server, accurevEnv, workspace, listener, accurevPath, launcher, localStream,
                    buildDate,logger,this)){
                return PollingResult.BUILD_NOW;
            }else{
                return PollingResult.NO_CHANGES;
            }
        }
        // There may be changes in a parent stream that we need to factor in.
        do {
            if (CheckForChanges.checkStreamForChanges(server, accurevEnv, workspace, listener, accurevPath, launcher, stream,
                    buildDate,logger,this)) {
                return PollingResult.BUILD_NOW;
            }
            stream = stream.getParent();
        } while (stream != null && stream.isReceivingChangesFromParent());
        return PollingResult.NO_CHANGES;
    }
    
    //--------------------------- Inner Class - DescriptorImplementation ----------------------------
    
    public static final class AccurevSCMDescriptor extends SCMDescriptor<AccurevSCM> implements ModelObject {

 	   /**
 	    * The accurev server has been known to crash if more than one copy of the
 	    * accurev has been run concurrently on the local machine. <br>
 	    * Also, the accurev client has been known to complain that it's not logged
 	    * in if another client on the same machine logs in again.
 	    */
 	   transient static final Lock ACCUREV_LOCK = new ReentrantLock();
 	   private List<AccurevServer> _servers;
 	   // The servers field is here for backwards compatibility.
 	   // The transient modifier means it won't be written to the config file
 	   private transient List<AccurevServer> servers;

 	   private static final Logger descriptorlogger = Logger.getLogger(AccurevSCMDescriptor.class.getName());

 	   private boolean pollOnMaster;  
 	   private String accurevPath;

 	   /**
 	    * Constructs a new AccurevSCMDescriptor.
 	    */
 	   public AccurevSCMDescriptor() {
 	      super(AccurevSCM.class,null);
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
 	      this._servers = req.bindJSONToList(AccurevServer.class, formData.get("server"));
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
 	            req.getParameter("_.serverName"), //
 	            req.getParameter("_.depot"), //
 	            req.getParameter("_.stream"), //           
 	            req.getParameter("accurev.wspaceORreftree"),//           
 	            req.getParameter("accurev.workspace"),//
 	            req.getParameter("accurev.reftree"), //
 	            req.getParameter("accurev.subPath"), //
 	            req.getParameter("accurev.filterForPollSCM"), //
 	            req.getParameter("accurev.synctime") != null, //
 	            req.getParameter("accurev.cleanreftree") != null, //
 	            req.getParameter("accurev.useSnapshot") != null, //
 	            req.getParameter("accurev.dontPopContent") != null,
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
 	      if (this._servers == null) {
 	         this._servers = new ArrayList<AccurevServer>();
 	      }
 	      // We put this here to maintain backwards compatibility
 	      // because we changed the name of the 'servers' field to '_servers'
 	      if (this.servers != null) {
 	         this._servers.addAll(servers);
 	         servers = null;
 	      }
 	      return this._servers;
 	   }

 	   /**
 	    * Setter for property 'servers'.
 	    * 
 	    * @param servers
 	    *           Value to set for property 'servers'.
 	    */
 	   public void setServers(List<AccurevServer> servers) {
 	      this._servers = servers;
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
 	    * @param servers
 	    *           Value to set for property 'pollOnMaster'.
 	    */
 	   public void setPollOnMaster(boolean pollOnMaster) {
 	      this.pollOnMaster = pollOnMaster;
 	   }

 	   public AccurevServer getServer(String name) {
 	      if (name == null) {
 	         return null;
 	      }
 	      for (AccurevServer server : this._servers) {
 	         if (name.equals(server.getName())) {
 	            return server;
 	         }
 	      }
 	      return null;
 	   }

 	   // This method will populate the servers in the select box
 	   public ListBoxModel doFillServerNameItems(@QueryParameter String serverName) {	  
 		  ListBoxModel s = new ListBoxModel();
 		  if (this._servers == null)
 		  {
 			  descriptorlogger.warning("Failed to find AccuRev server. Add Server under AccuRev section in the Manage Jenkins > Configure System page.");
 		      return s;
 		  }
 		      for (AccurevServer server : this._servers) {
 		         s.add(server.getName(), server.getName());
 		      }
 	       return s;
 	   }

 	   private static String getExistingPath(String[] paths) {
 	      for (final String path : paths) {
 	         if (new File(path).exists()) {
 	            return path;
 	         }
 	      }
 	      return "";
 	      
 	   }

      private AccurevServer getServerAndPath(String serverName) {
         final AccurevServer server = getServer(serverName);
         boolean envAccurevBin;
         String accurevBinName = "accurev";

         if (server == null) {
            return null;
         }

         String accurevBinDir = getEnvBinDir();

         if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
            // we are running on windows
            accurevBinName = "accurev.exe";
            if (!JustAccurev.justAccuRev(accurevBinDir + File.separator + accurevBinName)) {
               this.accurevPath = getExistingPath(server.getWinCmdLocations());
            } else {
               this.accurevPath = accurevBinDir + File.separator + accurevBinName;
            }
         } else {
            // we are running on *nix
            if (!JustAccurev.justAccuRev(accurevBinDir + File.separator + accurevBinName)) {
               this.accurevPath = getExistingPath(server.getNixCmdLocations());
            } else {
               this.accurevPath = accurevBinDir + File.separator + accurevBinName;
            }
         }

         if (this.accurevPath.isEmpty()) {
            // if we still don't have a path to the accurev client let's try the
            // system path
            if (JustAccurev.justAccuRev(accurevBinName)) {
               logger.info("Using the AccuRev client we found on the system path.");
               this.accurevPath = accurevBinName;
            } else {
               throw new RuntimeException("AccuRev binary is not found or not set in the environment's path.");
            }
         }

         return server;
      }

      private String getEnvBinDir() {
         String accurevBinDir = "";

         if (System.getenv("ACCUREV_BIN") != null) {
            accurevBinDir = System.getenv("ACCUREV_BIN");
            if (new File(accurevBinDir).exists() && new File(accurevBinDir).isDirectory()) {
               logger.info("The ACCUREV_BIN environment variable was set to: " + accurevBinDir);
               return accurevBinDir;
            } else {
               try {
                  throw new FileNotFoundException(
                        "The ACCUREV_BIN environment variable was set but the path it was set to does not exist OR it is not a directory. Please correct the path or unset the variable. ACCUREV_BIN was set to: "
                              + accurevBinDir);
               } catch (FileNotFoundException e) {
                  e.printStackTrace();
               }
            }
         } 
         
         if (System.getProperty("accurev.bin") != null) {
            accurevBinDir = System.getProperty("accurev.bin");
            if (new File(accurevBinDir).exists() && new File(accurevBinDir).isDirectory()) {
               logger.info("The accurev.bin system property was set to: " + accurevBinDir);
               return accurevBinDir;
            } else {
               try {
                  throw new FileNotFoundException(
                        "The accurev.bin system property was set but the path it was set to does not exist OR it is not a directory. Please correct the path or unset the property. 'accurev.bin' was set to: "
                              + accurevBinDir);
               } catch (FileNotFoundException e) {
                  e.printStackTrace();
               }
            }
         }     
         
         return accurevBinDir;
      }
      
 	   // This method will populate the depots in the select box depending upon the
 	   // server selected.
 	   public ListBoxModel doFillDepotItems(@QueryParameter String serverName, @QueryParameter String depot) {
 		   
 	      final AccurevServer server = getServerAndPath(serverName);
 	      if (server == null) {        
 	         return new ListBoxModel();
 	      }

 	      ListBoxModel d = null;
 	      Option temp = null;
 	      List<String> depots = new ArrayList<String>();
 	      
 	      // Execute the login command first & upon success of that run show depots
 	      // command. If any of the command's exitvalue is 1 proper error message is
 	      // logged
 	      try {
 				if (Login.accurevLoginfromGlobalConfig(server, accurevPath, descriptorlogger)) {
 					depots = ShowDepots.getDepots(server, accurevPath, descriptorlogger);
 				 } 
 			} catch (IOException e) { 				
 				
 			} catch (InterruptedException e) {
 				
 			}

 	      d = new ListBoxModel();
 	      for (String dname : depots) {
 	         d.add(dname, dname);
 	      }
 	      // Below while loop is for to retain the selected item when you open the
 	      // Job to reconfigure
 	      Iterator<Option> depotsIter = d.iterator();
 	      while (depotsIter.hasNext()) {
 	         temp = depotsIter.next();
 	         if (depot.equals(temp.name)) {
 	            temp.selected = true;
 	         }
 	      }
 	      return d;
 	   }

 	   // Populating the streams
 	   public ComboBoxModel doFillStreamItems(@QueryParameter String serverName, @QueryParameter String depot) {
 	      ComboBoxModel cbm = new ComboBoxModel();
 	      final AccurevServer server = getServerAndPath(serverName);
 	      if (server == null) {
 	         //descriptorlogger.warning("Failed to find server.");
 	         return new ComboBoxModel();
 	      }     
 	      // Execute the login command first & upon success of that run show streams
 	      // command. If any of the command's exitvalue is 1 proper error message is
 	      // logged      
 	      try {         
 	    	  	if (Login.accurevLoginfromGlobalConfig(server, accurevPath, descriptorlogger)) {           
 	    	  	cbm = ShowStreams.getStreamsForGlobalConfig(server, depot, accurevPath, cbm, descriptorlogger);            
 	         } 

 	      } catch (IOException e) {			
 				
 			} catch (InterruptedException e) {			
 				
 			}
 	      return cbm;
 	   }

 	   public static void lock() {
 	      ACCUREV_LOCK.lock();
 	   }

 	   public static void unlock() {
 	      ACCUREV_LOCK.unlock();
 	   }
 	}
    
    // --------------------------- Inner Class ---------------------------------------------------
    @SuppressWarnings("serial")
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
                "C:\\opt\\accurev\\bin\\accurev.exe", //
                "C:\\Program Files\\AccuRev\\bin\\accurev.exe", //
                "C:\\Program Files (x86)\\AccuRev\\bin\\accurev.exe");

        /**
         * The default search paths for *nix clients
         */
        private static final List<String> DEFAULT_NIX_CMD_LOCATIONS = Arrays.asList(//
                "/usr/local/bin/accurev", //
                "/usr/bin/accurev", //
                "/bin/accurev", //
                "/local/bin/accurev",
                "/opt/accurev/bin/accurev");

        public static final String VTT_DELIM = ",";
        // keep all transaction types in a set for validation
        private static final String VTT_LIST = "chstream,defcomp,mkstream,promote";
        private static final Set<String> VALID_TRANSACTION_TYPES = new HashSet<String>(Arrays.asList(VTT_LIST
                .split(VTT_DELIM)));
       // public static final String DEFAULT_VALID_TRANSACTION_TYPES = "add,chstream,co,defcomp,defunct,keep,mkstream,move,promote,purge,dispatch";
        public static final String DEFAULT_VALID_STREAM_TRANSACTION_TYPES = "chstream,defcomp,mkstream,promote";
        public static final String DEFAULT_VALID_WORKSPACE_TRANSACTION_TYPES = "add,chstream,co,defcomp,defunct,keep,mkstream,move,promote,purge,dispatch";
        

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
                    return FormValidation.error("Invalid transaction type [" + formValidType + "]. Valid types are: " + VTT_LIST);
                }
            }
            return FormValidation.ok();
        }

    }

      
    // -------------------------- INNER CLASSES --------------------------
    /**
     * Class responsible for parsing change-logs recorded by the builds.
     * If this is renamed or moved it'll break data-compatibility with old builds.
     */
    private static final class AccurevChangeLogParser extends ParseChangeLog {
    }
}