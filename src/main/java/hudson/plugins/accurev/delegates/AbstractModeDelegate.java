package hudson.plugins.accurev.delegates;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterValue;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccuRevHiddenParametersAction;
import hudson.plugins.accurev.AccurevSCM;
import static hudson.plugins.accurev.AccurevSCM.ACCUREV_DATETIME_FORMATTER;
import static hudson.plugins.accurev.AccurevSCM.DESCRIPTOR;
import hudson.plugins.accurev.AccurevStream;
import hudson.plugins.accurev.AccurevTransaction;
import hudson.plugins.accurev.FindAccurevClientExe;
import hudson.plugins.accurev.XmlConsolidateStreamChangeLog;
import hudson.plugins.accurev.cmd.ChangeLogCmd;
import hudson.plugins.accurev.cmd.History;
import hudson.plugins.accurev.cmd.Login;
import hudson.plugins.accurev.cmd.PopulateCmd;
import hudson.plugins.accurev.cmd.SetProperty;
import hudson.plugins.accurev.cmd.ShowStreams;
import hudson.plugins.accurev.cmd.Synctime;
import hudson.scm.ChangeLogSet;
import hudson.scm.EditType;
import hudson.scm.PollingResult;
import hudson.scm.SCMRevisionState;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Performs actual SCM operations
 *
 */
public abstract class AbstractModeDelegate {

    protected final AccurevSCM scm;
    protected Launcher launcher;
    protected AccurevSCM.AccurevServer server;
    protected Map<String, String> accurevEnv;
    protected FilePath jenkinsWorkspace;
    protected TaskListener listener;
    protected String accurevPath;
    protected FilePath accurevWorkingSpace;
    protected String localStream;
    protected Date startDateOfPopulate;

    private static final Logger logger = Logger.getLogger(AbstractModeDelegate.class.getName());
    private static final String ACCUREV_DEPOT = "ACCUREV_DEPOT";
    private static final String ACCUREV_STREAM = "ACCUREV_STREAM";
    private static final String ACCUREV_SERVER = "ACCUREV_SERVER";
    private static final String ACCUREV_SERVER_HOSTNAME = "ACCUREV_SERVER_HOSTNAME";
    private static final String ACCUREV_SERVER_PORT = "ACCUREV_SERVER_PORT";
    protected static final String ACCUREV_WORKSPACE = "ACCUREV_WORKSPACE";
    protected static final String ACCUREV_REFTREE = "ACCUREV_REFTREE";
    private static final String ACCUREV_SUBPATH = "ACCUREV_SUBPATH";
    private static final String ACCUREV_LAST_TRANSACTION = "ACCUREV_LAST_TRANSACTION";
    private static final String ACCUREV_HOME = "ACCUREV_HOME";

    public AbstractModeDelegate(AccurevSCM scm) {
        this.scm = scm;
    }

    private void setup(Launcher launcher, FilePath jenkinsWorkspace, TaskListener listener) throws IOException, InterruptedException {
        this.launcher = launcher;
        this.jenkinsWorkspace = jenkinsWorkspace;
        this.listener = listener;
        server = DESCRIPTOR.getServer(scm.getServerName());
        accurevEnv = new HashMap<String, String>();
        if (jenkinsWorkspace != null) {
            accurevPath = jenkinsWorkspace.act(new FindAccurevClientExe(server));
            accurevWorkingSpace = new FilePath(jenkinsWorkspace, scm.getDirectoryOffset() == null ? "" : scm.getDirectoryOffset());
            if (!Login.ensureLoggedInToAccurev(server, accurevEnv, jenkinsWorkspace, listener, accurevPath, launcher)) {
                throw new IllegalArgumentException("Authentication failure");
            }

            if (scm.isSynctime()) {
                listener.getLogger().println("Synchronizing clock with the server...");
                if (!Synctime.synctime(scm, server, accurevEnv, jenkinsWorkspace, listener, accurevPath, launcher)) {
                    throw new IllegalArgumentException("Synchronizing clock failure");
                }
            }
        }
    }

    public PollingResult compareRemoteRevisionWith(AbstractProject<?, ?> project, Launcher launcher, FilePath jenkinsWorkspace, TaskListener listener, SCMRevisionState scmrs) throws IOException, InterruptedException {
        if (project.isInQueue()) {
            listener.getLogger().println("Project build is currently in queue.");
            return PollingResult.NO_CHANGES;
        }
        if (jenkinsWorkspace == null) {
            // If we're claiming not to need a workspace in order to poll, then
            // workspace will be null.  In that case, we need to run directly
            // from the project folder on the master.
            final File projectDir = project.getRootDir();
            jenkinsWorkspace = new FilePath(projectDir);
            launcher = Jenkins.getInstance().createLauncher(listener);
        }
        listener.getLogger().println("Running commands from folder \"" + jenkinsWorkspace + '"');
        try {
            setup(launcher, jenkinsWorkspace, listener);
        } catch (IllegalArgumentException ex) {
            listener.fatalError(ex.getMessage());
            return PollingResult.NO_CHANGES;
        }

        return checkForChanges(project);
    }

    protected abstract PollingResult checkForChanges(AbstractProject<?, ?> project) throws IOException, InterruptedException;

    private boolean hasStringVariableReference(final String str) {
        return str != null && str.contains("${");
    }

    protected String getPollingStream(AbstractProject<?, ?> project) {
        String parsedLocalStream;
        if (hasStringVariableReference(scm.getStream())) {
            ParametersDefinitionProperty paramDefProp = project
                    .getProperty(ParametersDefinitionProperty.class);

            if (paramDefProp == null) {
                throw new IllegalArgumentException(
                        "Polling is not supported when stream name has a variable reference '" + scm.getStream() + "'.");
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
            parsedLocalStream = environment.expand(scm.getStream());
            listener.getLogger().println("... expanded '" + scm.getStream() + "' to '" + parsedLocalStream + "'.");
        } else {
            parsedLocalStream = scm.getStream();
        }

        if (hasStringVariableReference(parsedLocalStream)) {
            throw new IllegalArgumentException(
                    "Polling is not supported when stream name has a variable reference '" + scm.getStream() + "'.");
        }
        return parsedLocalStream;
    }

    public boolean checkout(AbstractBuild<?, ?> build, Launcher launcher, FilePath jenkinsWorkspace, BuildListener listener,
            File changelogFile) throws IOException, InterruptedException {

        try {
            setup(launcher, jenkinsWorkspace, listener);
        } catch (IllegalArgumentException ex) {
            listener.fatalError(ex.getMessage());
            return false;
        }

        if (!accurevWorkingSpace.exists()) {
            accurevWorkingSpace.mkdirs();
        }

        if (scm.getDepot() == null || scm.getDepot().isEmpty()) {
            listener.fatalError("Must specify a depot");
            return false;
        }

        if (scm.getStream() == null || scm.getStream().isEmpty()) {
            listener.fatalError("Must specify a stream");
            return false;
        }

        final EnvVars environment = build.getEnvironment(listener);
        environment.put("ACCUREV_CLIENT_PATH", accurevPath);

        localStream = environment.expand(scm.getStream());

        listener.getLogger().println("Getting a list of streams...");
        final Map<String, AccurevStream> streams = ShowStreams.getStreams(scm, localStream, server, accurevEnv, jenkinsWorkspace, listener, accurevPath,
                launcher);

        if (streams != null && !streams.containsKey(localStream)) {
            listener.fatalError("The specified stream, '" + localStream + "' does not appear to exist!");
            return false;
        }

        setStreamColor();

        if (!checkout(build, changelogFile)) {
            return false;
        }

        if (!populate()) {
            return false;
        }

        return captureChangeLog(build, changelogFile, streams, environment);
    }

    private boolean captureChangeLog(AbstractBuild<?, ?> build, File changelogFile, Map<String, AccurevStream> streams, EnvVars environment) throws IOException, InterruptedException {
        try {
            String changeLogStream = getChangeLogStream();

            AccurevTransaction latestTransaction = History.getLatestTransaction(scm,
                    server, accurevEnv, accurevWorkingSpace, listener, accurevPath, launcher, changeLogStream, null);
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
                "Calculating changelog" + (scm.isIgnoreStreamParent() ? ", ignoring changes in parent" : "") + "...");

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
            return ChangeLogCmd.captureChangelog(server, accurevEnv, accurevWorkingSpace, listener, accurevPath, launcher,
                    startDateOfPopulate, startTime == null ? null : startTime.getTime(),
                    localStream, changelogFile, logger, scm);
        }

        if (!getChangesFromStreams(startTime, stream, changelogFile)) {
            return ChangeLogCmd.captureChangelog(server, accurevEnv, accurevWorkingSpace, listener, accurevPath, launcher, startDateOfPopulate,
                    startTime == null ? null : startTime.getTime(), localStream, changelogFile, logger, scm);
        }
        return true;
    }

    protected String getChangeLogStream() {
        return localStream;
    }

    private boolean getChangesFromStreams(final Calendar startTime, AccurevStream stream, File changelogFile) throws IOException, InterruptedException {
        List<String> changedStreams = new ArrayList<String>();
        // Capture changes in all streams and parents
        boolean capturedChangelog = false;
        do {
            File streamChangeLog = XmlConsolidateStreamChangeLog.getStreamChangeLogFile(changelogFile, stream);
            capturedChangelog = ChangeLogCmd.captureChangelog(server, accurevEnv, accurevWorkingSpace, listener, accurevPath, launcher,
                    startDateOfPopulate, startTime == null ? null : startTime.getTime(), stream.getName(), streamChangeLog, logger, scm);
            if (capturedChangelog) {
                changedStreams.add(streamChangeLog.getName());
            }
            stream = stream.getParent();
        } while (stream != null && stream.isReceivingChangesFromParent() && capturedChangelog && startTime != null);

        XmlConsolidateStreamChangeLog.createChangeLog(changedStreams, changelogFile, getUpdateFileName());
        return capturedChangelog;
    }

    protected String getUpdateFileName() {
        return null;
    }

    protected abstract boolean checkout(AbstractBuild<?, ?> build, File changeLogFile) throws IOException, InterruptedException;

    protected abstract String getPopulateFromMessage();

    protected abstract String getPopulateStream();

    protected boolean isPopulateRequired() {
        return !scm.isDontPopContent();
    }

    protected boolean isSteamColorEnabled() {
        return false;
    }

    protected String getStreamColor() {
        return "";
    }

    protected String getStreamColorStream() {
        return null;
    }

    private void setStreamColor() {
        if (isSteamColorEnabled()) {
            //For AccuRev 6.0.x versions
            SetProperty.setproperty(scm, accurevWorkingSpace, listener, accurevPath, launcher, accurevEnv, server, getStreamColorStream(), getStreamColor(), "style");

            //For AccuRev 6.1.x onwards
            SetProperty.setproperty(scm, accurevWorkingSpace, listener, accurevPath, launcher, accurevEnv, server, getStreamColorStream(), getStreamColor(), "streamStyle");
        }
    }

    protected boolean populate(boolean populateRequired) {
        if (populateRequired) {
            PopulateCmd pop = new PopulateCmd();
            if (pop.populate(scm, launcher, listener, server, accurevPath, getPopulateStream(), true, getPopulateFromMessage(), accurevWorkingSpace, accurevEnv)) {
                startDateOfPopulate = pop.get_startDateOfPopulate();
            } else {
                return false;
            }
        } else {
            startDateOfPopulate = new Date();
        }
        return true;
        
    }
    protected boolean populate() {
        return populate(isPopulateRequired());
    }

    public void buildEnvVars(AbstractBuild<?, ?> build, Map<String, String> env) {
        try {
            setup(null, null, null);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "buildEnvVars", ex);
        } catch (InterruptedException ex) {
            logger.log(Level.SEVERE, "buildEnvVars", ex);
        }

        if (scm.getDepot() != null) {
            env.put(ACCUREV_DEPOT, scm.getDepot());
        } else {
            env.put(ACCUREV_DEPOT, "");
        }

        if (scm.getStream() != null) {
            env.put(ACCUREV_STREAM, scm.getStream());
        } else {
            env.put(ACCUREV_STREAM, "");
        }

        if (scm.getServerName() != null) {
            env.put(ACCUREV_SERVER, scm.getServerName());
        } else {
            env.put(ACCUREV_SERVER, "");
        }

        if (server != null && server.getHost() != null) {
            env.put(ACCUREV_SERVER_HOSTNAME, server.getHost());
        } else {
            env.put(ACCUREV_SERVER_HOSTNAME, "");
        }

        if (server != null && server.getPort() > 0) {
            env.put(ACCUREV_SERVER_PORT, Integer.toString(server.getPort()));
        } else {
            env.put(ACCUREV_SERVER_PORT, "");
        }

        env.put(ACCUREV_WORKSPACE, "");
        env.put(ACCUREV_REFTREE, "");
        
        if (scm.getSubPath() != null) {
            env.put(ACCUREV_SUBPATH, scm.getSubPath());
        } else {
            env.put(ACCUREV_SUBPATH, "");
        }

        // grab the last promote transaction from the changelog file
        String lastTransaction = null;
        // Abstract should have this since checkout should have already run
        ChangeLogSet<?> changeSet = (ChangeLogSet<AccurevTransaction>) build.getChangeSet();
        if (!changeSet.isEmptySet()) {
            // first EDIT entry should be the last transaction we want
            for (Object o : changeSet.getItems()) {
                if (o instanceof AccurevTransaction) {
                    AccurevTransaction t = (AccurevTransaction) o;
                    if (t.getEditType() == EditType.EDIT) { // this means promote or chstream in AccuRev
                        lastTransaction = t.getId();
                        break;
                    }
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
            env.put(ACCUREV_LAST_TRANSACTION, lastTransaction);
        } else {
            env.put(ACCUREV_LAST_TRANSACTION, "");
        }

        // ACCUREV_HOME is added to the build env variables
        if (System.getenv(ACCUREV_HOME) != null) {
            env.put(ACCUREV_HOME, System.getenv(ACCUREV_HOME));
        }
        
        buildEnvVarsCustom(build, env);
    }
    
    protected void buildEnvVarsCustom(AbstractBuild<?, ?> build, Map<String, String> env) {
        // override to put implementation specific values
    }
}
