package hudson.plugins.accurev;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.StringReader;
import java.io.ObjectStreamException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletException;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.ModelObject;
import hudson.model.ParameterValue;
import hudson.model.StringParameterValue;
import hudson.model.ParameterDefinition;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.plugins.jetty.security.Password;
import hudson.remoting.Callable;
import hudson.remoting.VirtualChannel;
import hudson.scm.ChangeLogParser;
import hudson.scm.ChangeLogSet;
import hudson.scm.EditType;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.IOException2;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.xml.sax.SAXException;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import net.sf.json.JSONObject;

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
    private static final long MILLIS_PER_SECOND = 1000L;
    private static final String DEFAULT_VALID_TRANSACTION_TYPES = "add,chstream,co,defcomp,defunct,keep,mkstream,move,promote,purge";
    private static final String VTT_DELIM = ",";
    // keep all transaction types in a set for validation
    private static final String VTT_LIST = "add,chstream,co,defcomp,defunct,keep,mkstream,move,promote,purge,dispatch";
    private static final Set<String> VALID_TRANSACTION_TYPES = 
        new HashSet<String>(Arrays.asList(VTT_LIST.split(VTT_DELIM))); 
    private static final Date NO_TRANS_DATE = new Date(0);
    private static final String DEFAULT_SNAPSHOT_NAME_FORMAT = "${JOB_NAME}_${BUILD_NUMBER}";
    private final String serverName;
    private final String depot;
    private final String stream;
    private final boolean useWorkspace;
    private final boolean usePurgeIfLastFailed;
    private final boolean useUpdate;
    private final boolean useRevert;
    private final boolean useSnapshot;
    private final String snapshotNameFormat;
    private final boolean synctime;
    private final String workspace;
    private final String workspaceSubPath;

// --------------------------- CONSTRUCTORS ---------------------------

    /**
     * Our constructor.
     */
    @DataBoundConstructor
    public AccurevSCM(String serverName,
                      String depot,
                      String stream,
                      boolean useWorkspace,
                      String workspace,
                      String workspaceSubPath,
                      boolean synctime,
                      boolean useUpdate,
                      boolean usePurgeIfLastFailed,
                      boolean useRevert,
                      boolean useSnapshot,
                      String snapshotNameFormat) {
        super();
        this.serverName = serverName;
        this.depot = depot;
        this.stream = stream;
        this.useWorkspace = useWorkspace;
        this.workspace = workspace;
        this.workspaceSubPath = workspaceSubPath;
        this.synctime = synctime;
        this.useUpdate = useUpdate;
        this.usePurgeIfLastFailed = usePurgeIfLastFailed;
        this.useRevert = useRevert;
        this.useSnapshot = useSnapshot;
        this.snapshotNameFormat = snapshotNameFormat;
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
     * Getter for property 'workspace'.
     *
     * @return Value for property 'workspace'.
     */
    public String getWorkspace() {
        return workspace;
    }

    /**
     * Getter for property 'workspaceSubPath'.
     *
     * @return Value for property 'workspaceSubPath'.
     */
    public String getWorkspaceSubPath() {
        return workspaceSubPath;
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
     * Getter for property 'synctime'.
     *
     * @return Value for property 'synctime'.
     */
    public boolean isSynctime() {
        return synctime;
    }

    /**
     * Getter for property 'usePurgeIfLastFailed'.
     *
     * @return Value for property 'usePurgeIfLastFailed'.
     */
    public boolean isUsePurgeIfLastFailed() {
        return usePurgeIfLastFailed;
    }
    
    
    /**
     * Getter for property 'useUpdate'.
     *
     * @return Value for property 'useUpdate'.
     */
    public boolean isUseUpdate() {
        return useUpdate;
    }

    /**
     * Getter for property 'useRevert'.
     *
     * @return Value for property 'useRevert'.
     */
    public boolean isUseRevert() {
        return useRevert;
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
     * Getter for property 'useWorkspace'.
     *
     * @return Value for property 'useWorkspace'.
     */
    public boolean isUseWorkspace() {
        return useWorkspace;
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
     *  <li>ACCUREV_WORKSPACE - The workspace name</li>
     *  <li>ACCUREV_SUBPATH - The workspace subpath</li>
     *
     * </ul>
     * @since 0.6.9
     */
    @Override
    public void buildEnvVars(AbstractBuild build, Map<String, String> env) {
        // call super even though SCM.buildEnvVars currently does nothing - this could change
        super.buildEnvVars(build, env);
        // add various accurev-specific variables to the environment
        if (depot != null)
            env.put("ACCUREV_DEPOT", depot);
        if (stream != null)
            env.put("ACCUREV_STREAM", stream);
        if (serverName != null)
            env.put("ACCUREV_SERVER", serverName);
        if (workspace != null && useWorkspace)
            env.put("ACCUREV_WORKSPACE", workspace);
        if (workspaceSubPath != null)
            env.put("ACCUREV_SUBPATH", workspaceSubPath);
        // grab the last promote transaction from the changelog file
        String lastTransaction = null;
        // Abstract should have this since checkout should have already run
        ChangeLogSet<AccurevTransaction> changeSet = build.getChangeSet();
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

    /**
     * {@inheritDoc}
     */
    public boolean checkout(AbstractBuild build, Launcher launcher, FilePath workspace, BuildListener listener,
                            File changelogFile) throws IOException, InterruptedException {

        AccurevServer server = DESCRIPTOR.getServer(serverName);

        final String accurevPath = workspace.act(new FindAccurevHome(server));

        if (!useWorkspace
                || !useUpdate
                || 
                	(
                	 usePurgeIfLastFailed &&
                	 build.getPreviousBuild() != null &&
                	 build.getPreviousBuild().getResult().isWorseThan(Result.UNSTABLE)
                	 )
            ) {
            workspace.act(new PurgeWorkspaceContents(listener));
        }

        Map<String, String> accurevEnv = new HashMap<String, String>();

        if (!ensureLoggedInToAccurev(server, accurevEnv, workspace, listener, accurevPath, launcher)) {
            return false;
        }

        if (synctime) {
            listener.getLogger().println("Synchronizing clock with the server...");
            if (!synctime(server, accurevEnv, workspace, listener, accurevPath, launcher)) {
                return false;
            }
        }

        listener.getLogger().println("Getting a list of streams...");
        final Map<String, AccurevStream> streams = getStreams(server, accurevEnv, workspace, listener, accurevPath,
                launcher);

        if (depot == null || "".equals(depot)) {
            listener.fatalError("Must specify a depot");
            return false;
        }
		
		
		
        if (stream == null || "".equals(stream)) {
            listener.fatalError("Must specify a stream");
            return false;
        }
		
		EnvVars environment = build.getEnvironment(listener);
		
		String localStream = environment.expand(stream);
		
        if (streams != null && !streams.containsKey(localStream)) {
            listener.fatalError("The specified stream does not appear to exist!");
            return false;
        }
        if (useWorkspace && (this.workspace == null || "".equals(this.workspace))) {
            listener.fatalError("Must specify a workspace");
            return false;
        }

        final Date startDateOfPopulate;

        if (useWorkspace) {
            listener.getLogger().println("Getting a list of workspaces...");
            Map<String, AccurevWorkspace> workspaces = getWorkspaces(server, accurevEnv, workspace, listener, accurevPath, launcher);
            
            if (workspaces == null) {
                listener.fatalError("Cannot determine workspace configuration information");
                return false;
            }
            if (!workspaces.containsKey(this.workspace)) {
                listener.fatalError("The specified workspace does not appear to exist!");
                return false;
            }
            AccurevWorkspace accurevWorkspace = workspaces.get(this.workspace);
            if (!depot.equals(accurevWorkspace.getDepot())) {
                listener.fatalError("The specified workspace, " + this.workspace + ", is based in the depot " + accurevWorkspace.getDepot() + " not " + depot);
                return false;
            }

            for (AccurevStream accurevStream : streams.values()) {
                if (accurevWorkspace.getStreamNumber().equals(accurevStream.getNumber())) {
                    accurevWorkspace.setStream(accurevStream);
                    break;
                }
            }

            RemoteWorkspaceDetails remoteDetails;
            try {
                remoteDetails = workspace.act(new DetermineRemoteHostname(workspace.getRemote()));
            } catch (IOException e) {
                listener.fatalError("Unable to validate workspace host.");
                e.printStackTrace(listener.getLogger());
                return false;
            }

            boolean needsRelocation = false;
            ArgumentListBuilder cmd = new ArgumentListBuilder();
            cmd.add(accurevPath);
            cmd.add("chws");
            addServer(cmd, server);
            cmd.add("-w");
            cmd.add(this.workspace);

            if (!localStream.equals(accurevWorkspace.getStream().getParent().getName())) {
                listener.getLogger().println("Parent stream needs to be updated.");
                needsRelocation = true;
                cmd.add("-b");
                cmd.add(localStream);
            }
            if (!accurevWorkspace.getHost().equals(remoteDetails.getHostName())) {
                listener.getLogger().println("Host needs to be updated.");
                needsRelocation = true;
                cmd.add("-m");
                cmd.add(remoteDetails.getHostName());
            }
            final String oldStorage = accurevWorkspace.getStorage()
                    .replace("/", remoteDetails.getFileSeparator())
                    .replace("\\", remoteDetails.getFileSeparator());
            if (!oldStorage.equals(remoteDetails.getPath())) {
                listener.getLogger().println("Storage needs to be updated.");
                needsRelocation = true;
                cmd.add("-l");
                cmd.add(workspace.getRemote());
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
                listener.getLogger().println(cmd.toStringWithQuote());

                final int rv;
                rv = launchAccurev(launcher, cmd, accurevEnv, null, listener.getLogger(), workspace);
                if (rv != 0) {
                    logCommandFailure(cmd, "Workspace relocation command", rv, null, listener);
                    return false;
                }
                listener.getLogger().println("Relocation successfully.");
            }

            
            if (useRevert){
            	listener.getLogger().println("attempting to get overlaps");
            	List<String> overlaps = getOverlaps(server, accurevEnv, workspace, listener, accurevPath, launcher);
            	if (overlaps != null && overlaps.size()>0)
            		workspace.act(new PurgeWorkspaceOverlaps(listener, overlaps));
            }
            
            
            listener.getLogger().println("Updating workspace...");
            cmd = new ArgumentListBuilder();
            cmd.add(accurevPath);
            cmd.add("update");
            addServer(cmd, server);
            int rv;
            rv = launchAccurev(launcher, cmd, accurevEnv, null, listener.getLogger(), workspace);
            if (rv != 0) {
                logCommandFailure(cmd, "Workspace update command", rv, null, listener);
                return false;
            }
            listener.getLogger().println("Update completed successfully.");

            listener.getLogger().println("Populating workspace...");
            cmd = new ArgumentListBuilder();
            cmd.add(accurevPath);
            cmd.add("pop");
            addServer(cmd, server);
            cmd.add("-R");
            if ((workspaceSubPath == null) || (workspaceSubPath.trim().length() == 0)) {
                cmd.add(".");
            } else {
                cmd.add(workspaceSubPath);
            }
            startDateOfPopulate = new Date();
            rv = launchAccurev(launcher, cmd, accurevEnv, null, listener.getLogger(), workspace);
            if (rv != 0) {
                logCommandFailure(cmd, "Populate workspace command", rv, null, listener);
                return false;
            }
            listener.getLogger().println("Populate completed successfully.");
        } else if ( isUseSnapshot() ) {
            final String snapshotName = calculateSnapshotName(build, listener);
            listener.getLogger().println("Creating snapshot: " + snapshotName + "...");
            build.getEnvironment(listener).put("ACCUREV_SNAPSHOT", snapshotName);
            // snapshot command: accurev mksnap -H <server> -s <snapshotName> -b <backing_stream> -t now
            ArgumentListBuilder cmd = new ArgumentListBuilder();
            cmd.add(accurevPath);
            cmd.add("mksnap");
            addServer(cmd, server);
            cmd.add("-s");
            cmd.add(snapshotName);
            cmd.add("-b");
            cmd.add(localStream);
            cmd.add("-t");
            cmd.add("now");
            int rv;
            rv = launchAccurev(launcher, cmd, accurevEnv, null, listener.getLogger(), workspace);
            if (rv != 0) {
                logCommandFailure(cmd, "Create snapshot command", rv, null, listener);
                return false;
            }
            listener.getLogger().println("Snapshot created successfully.");
            listener.getLogger().println("Populating workspace from snapshot...");
            cmd = new ArgumentListBuilder();
            cmd.add(accurevPath);
            cmd.add("pop");
            addServer(cmd, server);
            cmd.add("-v");
            cmd.add(snapshotName);
            cmd.add("-L");
            cmd.add(workspace.getRemote());
            cmd.add("-R");
            if ((workspaceSubPath == null) || (workspaceSubPath.trim().length() == 0)) {
                cmd.add(".");
            } else {
                cmd.add(workspaceSubPath);
            }
            startDateOfPopulate = new Date();
            rv = launchAccurev(launcher, cmd, accurevEnv, null, listener.getLogger(), workspace);
            if (rv != 0) {
                logCommandFailure(cmd, "Populate from snapshot command", rv, null, listener);
                return false;
            }
            listener.getLogger().println("Populate completed successfully.");
        } else {
            listener.getLogger().println("Populating workspace...");
            ArgumentListBuilder cmd = new ArgumentListBuilder();
            cmd.add(accurevPath);
            cmd.add("pop");
            addServer(cmd, server);
            cmd.add("-v");
            cmd.add(localStream);
            cmd.add("-L");
            cmd.add(workspace.getRemote());
            cmd.add("-R");
            if ((workspaceSubPath == null) || (workspaceSubPath.trim().length() == 0)) {
                cmd.add(".");
            } else {
                cmd.add(workspaceSubPath);
            }
            int rv;
            startDateOfPopulate = new Date();
            rv = launchAccurev(launcher, cmd, accurevEnv, null, listener.getLogger(), workspace);
            if (rv != 0) {
                logCommandFailure(cmd, "Populate command", rv, null, listener);
                return false;
            }
            listener.getLogger().println("Populate completed successfully.");
        }

        listener.getLogger().println("Calculating changelog...");

        Calendar startTime = null;
        if (null == build.getPreviousBuild()) {
            listener.getLogger().println("Cannot find a previous build to compare against. Computing all changes.");
        } else {
            startTime = build.getPreviousBuild().getTimestamp();
        }

        {
            AccurevStream stream = streams == null ? null : streams.get(localStream);

            if (stream == null) {
                // if there was a problem, fall back to simple stream check
                return captureChangelog(server, accurevEnv, workspace, listener, accurevPath, launcher,
                        startDateOfPopulate, startTime == null ? null : startTime.getTime(),
                        localStream, changelogFile);
            }
            // There may be changes in a parent stream that we need to factor in.
            // TODO produce a consolidated list of changes from the parent streams
            do {
                // This is a best effort to get as close to the changes as possible
                if (checkStreamForChanges(server, accurevEnv, workspace, listener, accurevPath, launcher,
                        stream.getName(), startTime == null ? null : startTime.getTime())) {
                    return captureChangelog(server, accurevEnv, workspace, listener, accurevPath, launcher,
                            startDateOfPopulate, startTime == null ? null : startTime
                            .getTime(), stream.getName(), changelogFile);
                }
                stream = stream.getParent();
            } while (stream != null && stream.isReceivingChangesFromParent());
        }
        return captureChangelog(server, accurevEnv, workspace, listener, accurevPath, launcher,
                startDateOfPopulate, startTime == null ? null : startTime.getTime(), localStream,
                changelogFile);
    }

    private String calculateSnapshotName(final AbstractBuild build,
            final BuildListener listener) throws IOException, InterruptedException {
        final String actualFormat = (snapshotNameFormat == null || snapshotNameFormat
                .trim().isEmpty()) ? DEFAULT_SNAPSHOT_NAME_FORMAT : snapshotNameFormat.trim();
        final EnvVars environment = build.getEnvironment(listener);
        final String snapshotName = environment.expand(actualFormat);
        return snapshotName;
    }

    private Map<String, AccurevWorkspace> getWorkspaces(AccurevServer server,
                                                        Map<String, String> accurevEnv,
                                                        FilePath workspace,
                                                        TaskListener listener,
                                                        String accurevPath,
                                                        Launcher launcher)
            throws IOException, InterruptedException {
        Map<String, AccurevWorkspace> workspaces = new HashMap<String, AccurevWorkspace>();
        ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(accurevPath);
        cmd.add("show");
        addServer(cmd, server);
        cmd.add("-fx");
        cmd.add("-p");
        cmd.add(depot);
        cmd.add("wspaces");
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        int rv;
        if (0 != (rv = launchAccurev(launcher, cmd, accurevEnv, null, stdout, workspace))) {
            logCommandFailure(cmd, "Show workspaces command", rv, stdout, listener);
            return null;
        }

        try {
            XmlPullParser parser = newPullParser();
            parser.setInput(new StringReader(stdout.toString()));
            while (true) {
                switch (parser.next()) {
                    case XmlPullParser.START_DOCUMENT:
                        break;
                    case XmlPullParser.END_DOCUMENT:
                        return workspaces;
                    case XmlPullParser.START_TAG:
                        final String tagName = parser.getName();
                        if ("Element".equalsIgnoreCase(tagName)) {
                            String name = parser.getAttributeValue("", "Name");
                            String storage = parser.getAttributeValue("", "Storage");
                            String host = parser.getAttributeValue("", "Host");
                            String streamNumber = parser.getAttributeValue("", "Stream");
                            String depot = parser.getAttributeValue("", "depot");
                            try {
                                workspaces.put(name, new AccurevWorkspace(
                                        depot,
                                        streamNumber == null ? null : Long.valueOf(streamNumber),
                                        name,
                                        host,
                                        storage));
                            } catch (NumberFormatException e) {
                                e.printStackTrace(listener.getLogger());
                            }
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        break;
                    case XmlPullParser.TEXT:
                        break;
                }
            }
        } catch (XmlPullParserException e) {
            e.printStackTrace(listener.getLogger());
            logger.warning(e.getMessage());
            return null;
        }
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
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        final int rv = launchAccurev(launcher, cmd, accurevEnv, null, stdout, workspace);
        if (rv != 0) {
            logCommandFailure(cmd, "Changelog command", rv, stdout, listener);
            return false;
        }
        final FileOutputStream os = new FileOutputStream(changelogFile);
        try {
            stdout.writeTo(os);
        } finally {
            os.close();
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

    /**
     * {@inheritDoc}
     */
    public boolean pollChanges(AbstractProject project, Launcher launcher, FilePath workspace, TaskListener listener)
            throws IOException, InterruptedException {
        if( project.isInQueue()) {
            listener.getLogger().println("Project build is currently in queue.");
            return false;
        }
        AccurevServer server = DESCRIPTOR.getServer(serverName);

        final String accurevPath = workspace.act(new FindAccurevHome(server));

        final Map<String, String> accurevEnv = new HashMap<String, String>();

        if (!ensureLoggedInToAccurev(server, accurevEnv, workspace, listener, accurevPath, launcher)) {
            return false;
        }

        if (synctime) {
            listener.getLogger().println("Synchronizing clock with the server...");
            if (!synctime(server, accurevEnv, workspace, listener, accurevPath, launcher)) {
                return false;
            }
        }

        final Run lastBuild = project.getLastBuild();
        if (lastBuild == null) {
            listener.getLogger().println("Project has never been built");
            return true;
        }
        final Date buildDate = lastBuild.getTimestamp().getTime();

        listener.getLogger().println("Last build on " + buildDate);

        final Map<String, AccurevStream> streams = getStreams(server, accurevEnv, workspace, listener, accurevPath, launcher);
		
		EnvVars environment = null;
				
		if(hasStringVariableReference(this.stream)){
			ParametersDefinitionProperty paramDefProp = (ParametersDefinitionProperty) project.getProperty(ParametersDefinitionProperty.class);
			
			if(paramDefProp == null) {
				listener.getLogger().println("Polling is not supported when stream name has a variable reference '" + this.stream + "'.");
				
				// as we don't know which stream to check we just state that there is no changes
				return false;
			}
			
			listener.getLogger().println("logout of parameter definitions ...");
			
			Map<String, String> keyValues = new TreeMap<String, String>();

			/* Scan for all parameter with an associated default values */
			for(ParameterDefinition paramDefinition : paramDefProp.getParameterDefinitions())
			{
				//listener.getLogger().println("parameter definition for '" + paramDefinition.getName() + "':");
				
				ParameterValue defaultValue  = paramDefinition.getDefaultParameterValue();
				
				if(defaultValue instanceof StringParameterValue){
					StringParameterValue strdefvalue = (StringParameterValue) defaultValue;
					
					//listener.getLogger().println("parameter default value for '" + defaultValue.getName() + " / " + defaultValue.getDescription() + "' is '" + strdefvalue.value + "'.");
					
					keyValues.put(defaultValue.getName(), strdefvalue.value);				
				}
			}

			environment = new EnvVars(keyValues); 
		}
		
		if(environment == null){
			return false;
		}
		
		String localStream = environment.expand(this.stream);
		
		if(hasStringVariableReference(localStream)){
				listener.getLogger().println("Polling is not supported when stream name has a variable reference '" + this.stream + "'.");
				
				// as we don't know which stream to check we just state that there is no changes
				return false;
		}
		
		
		listener.getLogger().println("... expanded '" + this.stream + "' to '" + localStream + "'.");

        AccurevStream stream = streams == null ? null : streams.get(localStream);

        if (stream == null) {
            // if there was a problem, fall back to simple stream check
            return checkStreamForChanges(server, accurevEnv, workspace, listener, accurevPath, launcher,localStream,
                    buildDate);
        }
        // There may be changes in a parent stream that we need to factor in.
        do {
            if (checkStreamForChanges(server, accurevEnv, workspace, listener, accurevPath, launcher, stream.getName(),
                    buildDate)) {
                return true;
            }
            stream = stream.getParent();
        } while (stream != null && stream.isReceivingChangesFromParent());
        return false;
    }

    private boolean ensureLoggedInToAccurev(
            AccurevServer server,
            Map<String, String> accurevEnv,
            FilePath workspace,
            TaskListener listener,
            String accurevPath,
            Launcher launcher)
            throws IOException, InterruptedException {
        accurevEnv.put("ACCUREV_HOME", workspace.getParent().getRemote());
        if (server == null) {
            return true;
        }
        final String requiredUsername = server.getUsername();
        if( requiredUsername==null || requiredUsername.trim().length()==0 ) {
            return true;
        }
        DESCRIPTOR.ACCUREV_LOCK.lock();
        try {
            final boolean loginRequired;
            if (server.isMinimiseLogins()) {
                final String currentUsername = getLoggedInUsername(server,
                        accurevEnv, workspace, listener, accurevPath,
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
                return accurevLogin(server, accurevEnv, workspace,
                        listener, accurevPath, launcher);
            }
        } finally {
            DESCRIPTOR.ACCUREV_LOCK.unlock();
        }
        return true;
    }

    private boolean accurevLogin(AccurevServer server,
            Map<String, String> accurevEnv,
            FilePath workspace,
            TaskListener listener,
            String accurevPath,
            Launcher launcher)
            throws IOException, InterruptedException {
        listener.getLogger().println("Authenticating with Accurev server...");
        final boolean[] masks;
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(accurevPath);
        cmd.add("login");
        addServer(cmd, server);
        if( server.isUseNonexpiringLogin() ) {
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
        final String resp;
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        int rv = launcher.launch().cmds(cmd).masks(masks).envs(accurevEnv).stdout(stdout).pwd(workspace).join();
        if (rv == 0) {
            resp = null;
        } else {
            resp = stdout.toString();
        }
        if (null == resp || "".equals(resp)) {
            listener.getLogger().println("Authentication completed successfully.");
            return true;
        } else {
            listener.fatalError("Authentication failed: " + resp);
            return false;
        }
    }

    private boolean synctime(AccurevServer server,
                             Map<String, String> accurevEnv,
                             FilePath workspace,
                             TaskListener listener,
                             String accurevPath,
                             Launcher launcher)
            throws IOException, InterruptedException {
        ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(accurevPath);
        cmd.add("synctime");
        addServer(cmd, server);
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        int rv;
        if (0 != (rv = launchAccurev(launcher, cmd, accurevEnv, null, stdout, workspace))) {
            logCommandFailure(cmd, "Synctime command", rv, stdout, listener);
            return false;
        }
        return true;
    }

    private Map<String, AccurevStream> getStreams(AccurevServer server,
                                                  Map<String, String> accurevEnv,
                                                  FilePath workspace,
                                                  TaskListener listener,
                                                  String accurevPath,
                                                  Launcher launcher)
            throws IOException, InterruptedException {
        Map<String, AccurevStream> streams = new HashMap<String, AccurevStream>();
        ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(accurevPath);
        cmd.add("show");
        addServer(cmd, server);
        cmd.add("-fx");
        cmd.add("-p");
        cmd.add(depot);
        cmd.add("streams");
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        int rv;
        if (0 != (rv = launchAccurev(launcher, cmd, accurevEnv, null, stdout, workspace))) {
            logCommandFailure(cmd, "Show streams command", rv, stdout, listener);
            return null;
        }

        try {
            XmlPullParser parser = newPullParser();
            parser.setInput(new StringReader(stdout.toString()));
            while (true) {
                switch (parser.next()) {
                    case XmlPullParser.START_DOCUMENT:
                        break;
                    case XmlPullParser.END_DOCUMENT:
                        // build the tree
                        for (AccurevStream stream : streams.values()) {
                            if (stream.getBasisName() != null) {
                                stream.setParent(streams.get(stream.getBasisName()));
                            }
                        }
                        return streams;
                    case XmlPullParser.START_TAG:
                        final String tagName = parser.getName();
                        if ("stream".equalsIgnoreCase(tagName)) {
                            final String streamName = parser.getAttributeValue("", "name");
                            final String streamNumber = parser.getAttributeValue("", "streamNumber");
                            final String basisStreamName = parser.getAttributeValue("", "basis");
                            final String basisStreamNumber = parser.getAttributeValue("", "basisStreamNumber");
                            final String streamType = parser.getAttributeValue("", "type");
                            final String streamIsDynamic = parser.getAttributeValue("", "isDynamic");
                            final String streamTimeString = parser.getAttributeValue("", "time");
                            final Date streamTime =
                                    streamTimeString == null ? null : convertAccurevTimestamp(streamTimeString);
                            final String streamStartTimeString = parser.getAttributeValue("", "startTime");
                            final Date streamStartTime =
                                    streamTimeString == null ? null : convertAccurevTimestamp(streamStartTimeString);
                            try {
                                final AccurevStream stream = new AccurevStream(streamName,
                                        streamNumber == null ? null : Long.valueOf(streamNumber),
                                        depot,
                                        basisStreamName,
                                        basisStreamNumber == null ? null : Long.valueOf(basisStreamNumber),
                                        streamIsDynamic != null && Boolean.parseBoolean(streamIsDynamic),
                                        AccurevStream.StreamType.parseStreamType(streamType),
                                        streamTime,
                                        streamStartTime);
                                streams.put(streamName, stream);
                            } catch (NumberFormatException e) {
                                e.printStackTrace(listener.getLogger());
                            }
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        break;
                    case XmlPullParser.TEXT:
                        break;
                }
            }
        } catch (XmlPullParserException e) {
            e.printStackTrace(listener.getLogger());
            logger.warning(e.getMessage());
            return null;
        }

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
	private List<String> getOverlaps(AccurevServer server,
			Map<String, String> accurevEnv, FilePath workspace,
			TaskListener listener, String accurevPath, Launcher launcher)
			throws IOException, InterruptedException {
		
		List<String> overlaps = new ArrayList<String>();
		
		ArgumentListBuilder cmd = new ArgumentListBuilder();
		cmd.add(accurevPath);
		cmd.add("stat");
        addServer(cmd, server);		
		cmd.add("-fx");
		cmd.add("-o");
		final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
		int rv;
		if (0 != (rv = launchAccurev(launcher, cmd, accurevEnv, null, stdout, workspace))) {
            logCommandFailure(cmd, "Stat overlaps command", rv, stdout, listener);
			return null;
		}

		try {
			XmlPullParser parser = newPullParser();
			parser.setInput(new StringReader(stdout.toString()));
			while (true) {
				switch (parser.next()) {
				case XmlPullParser.START_DOCUMENT:
					break;
				case XmlPullParser.END_DOCUMENT:
					// build the tree
					return overlaps;
				case XmlPullParser.START_TAG:
					final String tagName = parser.getName();
					logger.warning("Parsing tag name: " + tagName);
					
					if ("element".equalsIgnoreCase(tagName)) {
						String filename = parser.getAttributeValue("", "location");
						String dir = parser.getAttributeValue("","dir");	// yes or no
							
						if ("no".equalsIgnoreCase(dir)){
							listener.getLogger().println("Adding file to overlap list: " + filename);
							overlaps.add(filename);
						}else{
							// don't add dirs to overlap list
						}
						
					}

					break;
				case XmlPullParser.END_TAG:
					break;
				case XmlPullParser.TEXT:
					break;
				}
			}
		} catch (XmlPullParserException e) {
			e.printStackTrace(listener.getLogger());
			logger.warning(e.getMessage());
			return null;
		}

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
        String[] validTransactionTypes = null;
        if (server.getValidTransactionTypes() != null) {
        	validTransactionTypes = server.getValidTransactionTypes().split(VTT_DELIM);
            // if this is still empty, use default list
        	if (validTransactionTypes.length == 0) {
        		validTransactionTypes = DEFAULT_VALID_TRANSACTION_TYPES.split(VTT_DELIM);
        	}
        }
        else {
        	validTransactionTypes = DEFAULT_VALID_TRANSACTION_TYPES.split(VTT_DELIM);
        }

        for (String transactionType : validTransactionTypes) {
            AccurevTransaction tempTransaction;

            try {
                tempTransaction = getLatestTransaction(server, accurevEnv, workspace, listener, accurevPath, launcher, stream, transactionType);
                if (tempTransaction != null) {
                	if (latestCodeChangeTransaction.getDate().before(tempTransaction.getDate())) {
                		latestCodeChangeTransaction = tempTransaction;
                	}
                }
                else {
                    listener.getLogger().println("No transactions of type [" + transactionType + "] in stream [" + stream + "]");        	
                }
            }
            catch (Exception e) {
                final String msg = "getTimeOfLatestTransaction failed when checking the stream " + stream + " for changes with transaction type " + transactionType;
                listener.getLogger().println(msg);
                e.printStackTrace(listener.getLogger());
                logger.log(Level.WARNING, msg, e);
            }
        }

        //log last transaction information if retrieved
        if (latestCodeChangeTransaction.getDate().equals(NO_TRANS_DATE)) {
            listener.getLogger().println("No last transaction found for stream [" + stream + "]");
        }
        else {
            listener.getLogger().println("Last valid trans id [" + latestCodeChangeTransaction.getId() 
            		+ "] date [" + latestCodeChangeTransaction.getDate() 
            		+ "] author [" + latestCodeChangeTransaction.getAuthor()
            		+ "] action [" + latestCodeChangeTransaction.getAction()
            		+ "] msg [" + ((latestCodeChangeTransaction.getMsg() != null) 
            				? latestCodeChangeTransaction.getMsg() : "" + "]"));
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
    private AccurevTransaction getLatestTransaction(AccurevServer server,
                                              Map<String, String> accurevEnv,
                                              FilePath workspace,
                                              TaskListener listener,
                                              String accurevPath,
                                              Launcher launcher,
                                              String stream,
                                              String transactionType)
                throws Exception {
            //initialize code that extracts the latest transaction of a certain type using -k flag
            ArgumentListBuilder cmd = new ArgumentListBuilder();
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
            final ByteArrayOutputStream stdout = new ByteArrayOutputStream();

            //execute code that extracts the latest transaction
            int rv = launchAccurev(launcher, cmd, accurevEnv, null, stdout, workspace);
            if (0 != rv) {
                logCommandFailure(cmd, "History command", rv, stdout, listener);
                throw new Exception("History command failed with exit code " + rv + " when trying to get the latest transaction of type " + transactionType);
            }

            //parse the result from the transaction-query
            XmlPullParser parser = newPullParser();
            parser.setInput(new StringReader(stdout.toString()));

            AccurevTransaction resultTransaction = null;

            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG) {
                    if(parser.getName().equalsIgnoreCase("transaction")) {
                    	resultTransaction = new AccurevTransaction();
                        //parse transaction-values
                        resultTransaction.setId( ( Integer.parseInt( parser.getAttributeValue("", "id"))));
                        resultTransaction.setAction( parser.getAttributeValue("","type"));
                        resultTransaction.setDate( convertAccurevTimestamp(parser.getAttributeValue("", "time")));
                        resultTransaction.setUser( parser.getAttributeValue("", "user"));
                    } else if (parser.getName().equalsIgnoreCase("comment")) {
                        //parse comments
                        resultTransaction.setMsg(parser.nextText());
                    }
                }
            }
            return resultTransaction;
        }    
    /**
     * Helper method to retrieve include/exclude rules for a given stream.
     *
     * @return HashMap key: String path , val: String (enum) incl/excl rule type
     */
    private HashMap<String, String> getIncludeExcludeRules(AccurevServer server,
                                                           Map<String, String> accurevEnv,
                                                           FilePath workspace,
                                                           TaskListener listener,
                                                           String accurevPath,
                                                           Launcher launcher,
                                                           String stream)
            throws IOException, InterruptedException {
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
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        int rv;
        if (0 != (rv = launchAccurev(launcher, cmd, accurevEnv, null, stdout, workspace))) {
            logCommandFailure(cmd, "lsrules command", rv, stdout, listener);
            return null;
        }

        // Parse the 'accurev lsrules' command, and build up the include/exclude rules map
        HashMap<String, String> locationToKindMap = new HashMap<String, String>();
        //key: String location, val: String kind (incl / excl / incldo)
        try {
            XmlPullParser parser = newPullParser();
            parser.setInput(new StringReader(stdout.toString()));
            boolean parsingComplete = false;
            while (!parsingComplete) {
                switch (parser.next()) {
                    case XmlPullParser.START_DOCUMENT:
                        break;
                    case XmlPullParser.START_TAG:
                        final String tagName = parser.getName();
                        if ("element".equalsIgnoreCase(tagName)) {
                            String kind = parser.getAttributeValue("", "kind");
                            String location = parser.getAttributeValue("", "location");
                            if (location != null && kind != null) {
                                locationToKindMap.put(location, kind);
                            }
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        break;
                    case XmlPullParser.TEXT:
                        break;
                    case XmlPullParser.END_DOCUMENT:
                        parsingComplete = true;
                        break;
                }
            }
        }
        catch (XmlPullParserException e) {
            e.printStackTrace(listener.getLogger());
            logger.warning(e.getMessage());
            return null;
        }

        for (String location : locationToKindMap.keySet()) {
            String kind = locationToKindMap.get(location);
            listener.getLogger().println("Found rule: " + kind + " for: " + location);
        }

        return locationToKindMap;
    }

    /**
     * @return The currently logged in user "Principal" name, which may be
     *         "(not logged in)" if not logged in.<br>
     *         Returns null on failure.
     */
    private static String getLoggedInUsername(
            AccurevServer server,
            Map<String, String> accurevEnv,
            FilePath workspace,
            TaskListener listener,
            String accurevPath,
            Launcher launcher)
            throws IOException, InterruptedException {
        final String commandDescription = "info command";
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(accurevPath);
        cmd.add("info");
        addServer(cmd, server);
        final String cmdOutput;
        final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        try {
            final int rv = launcher.launch().cmds(cmd).envs(accurevEnv).stdout(stdout).stderr(stdout).pwd(workspace).join();
            if (0 != rv) {
                logCommandFailure(cmd, commandDescription, rv, stdout, listener);
                return null;
            }
            cmdOutput = stdout.toString();
        } finally {
            stdout.close();
        }
        listener.getLogger().println(cmdOutput);
        return getUsernameFromAccurevInfo(cmdOutput, commandDescription,
				listener);
    }


    private static String getUsernameFromAccurevInfo(final String cmdOutput,
            final String commandDescription, TaskListener listener)
            throws IOException {
        final String usernameHeading = "Principal:";
        final String controlCharsOrSpaceRegex = "[ \\x00-\\x1F\\x7F]+";
        final StringReader stringReader = new StringReader(cmdOutput);
        final BufferedReader lineReader = new BufferedReader(stringReader);
        String line;
        try {
            line = lineReader.readLine();
            while (line != null) {
                final String[] parts = line.split(controlCharsOrSpaceRegex);
                for (int i = 0; i < parts.length; i++) {
                    final String part = parts[i];
                    if (usernameHeading.equals(part)) {
                        if ((i + 1) < parts.length) {
                            final String username = parts[i + 1];
                            return username;
                        }
                    }
                }
                line = lineReader.readLine();
            }
        } finally {
            lineReader.close();
        }
        final String msg = commandDescription
                + " returned output which did not contain " + usernameHeading
                + " " + controlCharsOrSpaceRegex + " <username>: " + cmdOutput;
        logger.warning(msg);
        listener.error(msg);
        return null;
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

    private int launchAccurev(Launcher launcher,
                              ArgumentListBuilder cmd,
                              Map<String, String> env,
                              InputStream in,
                              OutputStream os,
                              FilePath workspace) throws IOException, InterruptedException {
        final int rv;
        // need server to know if it syncs CLI operations
        AccurevServer server = DESCRIPTOR.getServer(serverName);
        final boolean shouldLock = server.isSyncOperations();
		if (shouldLock) {
        	DESCRIPTOR.ACCUREV_LOCK.lock();
        }
        try {
            rv = launcher.launch().cmds(cmd).envs(env).stdin(in).stdout(os).stderr(os).pwd(workspace).join();
        } finally {
        	if (shouldLock) {
        		DESCRIPTOR.ACCUREV_LOCK.unlock();
        	}
        }
        return rv;
    }

    private static void logCommandFailure(final ArgumentListBuilder command,
            final String commandDescription,
            final int commandExitCode,
            final ByteArrayOutputStream commandStderrOrNull,
            final TaskListener taskListener) {
        final String msg = commandDescription + " ("
                + command.toStringWithQuote() + ")" + " failed with exit code "
                + commandExitCode;
        logger.warning(msg);
        if (commandStderrOrNull != null && commandStderrOrNull.size() > 0) {
            final String stderr = commandStderrOrNull.toString();
            logger.info(stderr);
            taskListener.fatalError(stderr);
        }
        taskListener.fatalError(msg);
    }

    /**
     * Gets a new {@link org.xmlpull.v1.XmlPullParser} configured for parsing Accurev XML files.
     *
     * @return a new {@link org.xmlpull.v1.XmlPullParser} configured for parsing Accurev XML files.
     *
     * @throws XmlPullParserException when things go wrong/
     */
    private static XmlPullParser newPullParser() throws XmlPullParserException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        factory.setNamespaceAware(false);
        factory.setValidating(false);
        return factory.newPullParser();
    }

    /**
     * Converts an Accurev timestamp into a {@link Date}
     *
     * @param transactionTime The accurev timestamp.
     *
     * @return A {@link Date} set to the time for the accurev timestamp.
     */
    private static Date convertAccurevTimestamp(String transactionTime) {
        if (transactionTime == null) {
            return null;
        }
        try {
            final long time = Long.parseLong(transactionTime);
            final long date = time * MILLIS_PER_SECOND;
            return new Date(date);
        } catch (NumberFormatException e) {
            return null;
        }
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
            return "Accurev";
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            servers = req.bindJSONToList(AccurevServer.class, formData.get("server"));
            save();
            return true;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public SCM newInstance(StaplerRequest req, JSONObject formData) throws FormException {
			return new AccurevSCM( //
					req.getParameter("accurev.serverName"), 
					req.getParameter("accurev.depot"), 
					req.getParameter("accurev.stream"), 
					req.getParameter("accurev.useWorkspace") != null,
					req.getParameter("accurev.workspace"), 
					req.getParameter("accurev.workspaceSubPath"),
					req.getParameter("accurev.synctime") != null,
					req.getParameter("accurev.useUpdate") != null,
					req.getParameter("accurev.usePurgeIfLastFailed") != null,
					req.getParameter("accurev.useRevert") != null, 
					req.getParameter("accurev.useSnapshot") != null,
					req.getParameter("accurev.snapshotNameFormat"));
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

        /**
         * Getter for property 'serverNames'.
         *
         * @return Value for property 'serverNames'.
         */
        public String[] getServerNames() {
            String[] result = new String[servers.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = servers.get(i).getName();
            }
            return result;
        }

    }

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

        /**
         * The default search paths for Windows clients.
         */
        private static final List<String> DEFAULT_WIN_CMD_LOCATIONS = Arrays.asList(
                "C:\\Program Files\\AccuRev\\bin\\accurev.exe",
                "C:\\Program Files (x86)\\AccuRev\\bin\\accurev.exe");

        /**
         * The default search paths for *nix clients
         */
        private static final List<String> DEFAULT_NIX_CMD_LOCATIONS = Arrays.asList(
                "/usr/local/bin/accurev",
                "/usr/bin/accurev",
                "/bin/accurev",
                "/local/bin/accurev");

        /**
         * Constructs a new AccurevServer.
         */
        public AccurevServer() {
            winCmdLocations = new ArrayList<String>(DEFAULT_WIN_CMD_LOCATIONS);
            nixCmdLocations = new ArrayList<String>(DEFAULT_NIX_CMD_LOCATIONS);
        }

        @DataBoundConstructor
        public AccurevServer(
                String name,
                String host,
                int port,
                String username,
                String password,
                String validTransactionTypes,
                boolean syncOperations,
                boolean minimiseLogins,
                boolean useNonexpiringLogin) {
            this.name = name;
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = Password.obfuscate(password);
            winCmdLocations = new ArrayList<String>(DEFAULT_WIN_CMD_LOCATIONS);
            nixCmdLocations = new ArrayList<String>(DEFAULT_NIX_CMD_LOCATIONS);
            this.validTransactionTypes = validTransactionTypes;
            this.syncOperations = syncOperations;
            this.minimiseLogins = minimiseLogins;
            this.useNonexpiringLogin = useNonexpiringLogin;
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
         * @return returns the currently set transaction types that are seen as valid for triggering builds and whos authors get notified when a build fails
         */
        public String getValidTransactionTypes() {
            return validTransactionTypes;
        }

        /**
         *
         * @param validTransactionTypes the currently set transaction types that are seen as valid for triggering builds and whos authors get notified when a build fails 
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

        public FormValidation doValidTransactionTypesCheck(@QueryParameter String value)
		throws IOException, ServletException {
        	String[] formValidTypes = value.split(VTT_DELIM);
        	for (String formValidType : formValidTypes) {
        		if (!VALID_TRANSACTION_TYPES.contains(formValidType)) {
        			return FormValidation.error("Invalid transaction type [" + formValidType + "]. Valid types are: " + VTT_LIST);
        		}
        	}

        	return FormValidation.ok();
        }

    }

    private static final class PurgeWorkspaceContents implements FilePath.FileCallable<Boolean> {

        private final TaskListener listener;

        public PurgeWorkspaceContents(TaskListener listener) {
            this.listener = listener;
        }

        /**
         * {@inheritDoc}
         */
        public Boolean invoke(File ws, VirtualChannel channel) throws IOException {
            listener.getLogger().println("Purging workspace...");
            System.runFinalization();
            System.gc();
            System.runFinalization();
            Util.deleteContentsRecursive(ws);
            listener.getLogger().println("Workspace purged.");
            return Boolean.TRUE;
        }
    }

    
    private static final class PurgeWorkspaceOverlaps implements FilePath.FileCallable<Boolean> {

    	private final List<String> filelist;
        private final TaskListener listener;

        public PurgeWorkspaceOverlaps(TaskListener listener, List<String> filelist) {
        	this.filelist = filelist;
            this.listener = listener;
        }

        /**
         * {@inheritDoc}
         */
        public Boolean invoke(File ws, VirtualChannel channel) throws IOException {
    		listener.getLogger().println("Purging overlaps...");
        	for (String filename: filelist){
        		File toPurge = new File(ws, filename);
        		Util.deleteFile(toPurge);
        		listener.getLogger().println("... " + toPurge.getAbsolutePath());
        	}
    		listener.getLogger().println("Overlaps purged.");
            return Boolean.TRUE;
        }

    }
    
    
    private static final class FindAccurevHome implements FilePath.FileCallable<String> {

        private final AccurevServer server;

        public FindAccurevHome(AccurevServer server) {
            this.server = server;
        }

        private static String getExistingPath(List<String> paths, String fallback) {
            for (String path: paths) {
                if (new File(path).exists()) {
                    return path;
                }
            }
            // just hope it's on the environment's path
            return fallback;
        }

        /**
         * {@inheritDoc}
         */
        public String invoke(File f, VirtualChannel channel) throws IOException {
            if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
                // we are running on windows
                return getExistingPath(server.winCmdLocations, "accurev.exe");
            } else {
                // we are running on *nix
                return getExistingPath(server.nixCmdLocations, "accurev");
            }
        }

    }

    private static final class AccurevChangeLogParser extends ChangeLogParser {

        /**
         * {@inheritDoc}
         */
        public ChangeLogSet<AccurevTransaction> parse(AbstractBuild build, File changelogFile)
                throws IOException, SAXException {
            List<AccurevTransaction> transactions = null;
            try {
                XmlPullParser parser = newPullParser();
                FileReader fis = null;
                BufferedReader bis = null;
                try {
                    fis = new FileReader(changelogFile);
                    bis = new BufferedReader(fis);
                    parser.setInput(bis);
                    transactions = parseTransactions(parser);
                } finally {
                    if (bis != null) {
                        bis.close();
                    }
                    if (fis != null) {
                        fis.close();
                    }
                }
            } catch (XmlPullParserException e) {
                throw new IOException2(e);
            }

            logger.info("transactions size = " + transactions.size());
            return new AccurevChangeLogSet(build, transactions);
        }

        private List<AccurevTransaction> parseTransactions(XmlPullParser parser)
                throws IOException, XmlPullParserException {
            List<AccurevTransaction> transactions = new ArrayList<AccurevTransaction>();
            AccurevTransaction currentTransaction = null;
            boolean inComment = false;
            while (true) {
                switch (parser.next()) {
                    case XmlPullParser.START_DOCUMENT:
                        break;
                    case XmlPullParser.END_DOCUMENT:
                        return transactions;
                    case XmlPullParser.START_TAG:
                        final String tagName = parser.getName();
                        inComment = "comment".equalsIgnoreCase(tagName);
                        if ("transaction".equalsIgnoreCase(tagName)) {
                            currentTransaction = new AccurevTransaction();
                            transactions.add(currentTransaction);
                            currentTransaction.setRevision(parser.getAttributeValue("", "id"));
                            currentTransaction.setUser(parser.getAttributeValue("", "user"));
                            currentTransaction.setDate(convertAccurevTimestamp(parser.getAttributeValue("", "time")));
                            currentTransaction.setAction(parser.getAttributeValue("", "type"));
                        } else if ("version".equalsIgnoreCase(tagName) && currentTransaction != null) {
                            String path = parser.getAttributeValue("", "path");
                            if (path != null) {
                                path = path.replace("\\", "/");
                                if (path.startsWith("/./")) {
                                    path = path.substring(3);
                                }
                            }
                            currentTransaction.addAffectedPath(path);
                        }
                        break;
                    case XmlPullParser.END_TAG:
                        inComment = false;
                        break;
                    case XmlPullParser.TEXT:
                        if (inComment && currentTransaction != null) {
                            currentTransaction.setMsg(parser.getText());
                        }
                        break;
                }
            }
        }

    }

    private static class RemoteWorkspaceDetails implements Serializable {

        private final String hostName;
        private final String path;
        private final String fileSeparator;

        public RemoteWorkspaceDetails(String hostName, String path, String fileSeparator) {
            this.hostName = hostName;
            this.path = path;
            this.fileSeparator = fileSeparator;
        }

        /**
         * Getter for property 'hostName'.
         *
         * @return Value for property 'hostName'.
         */
        public String getHostName() {
            return hostName;
        }

        /**
         * Getter for property 'path'.
         *
         * @return Value for property 'path'.
         */
        public String getPath() {
            return path;
        }

        /**
         * Getter for property 'fileSeparator'.
         *
         * @return Value for property 'fileSeparator'.
         */
        public String getFileSeparator() {
            return fileSeparator;
        }

    }

    private static class DetermineRemoteHostname implements Callable<RemoteWorkspaceDetails, UnknownHostException> {

        private final String path;

        public DetermineRemoteHostname(String path) {
            this.path = path;
        }

        /**
         * {@inheritDoc}
         */
        public RemoteWorkspaceDetails call() throws UnknownHostException {
            InetAddress addr = InetAddress.getLocalHost();
            File f = new File(path);
            String path;
            try {
                path = f.getCanonicalPath();
            } catch (IOException e) {
                path = f.getAbsolutePath();
            }

            return new RemoteWorkspaceDetails(addr.getCanonicalHostName(), path, File.separator);
        }

    }

}
