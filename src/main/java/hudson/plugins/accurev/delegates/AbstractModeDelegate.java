package hudson.plugins.accurev.delegates;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.plugins.accurev.*;
import hudson.plugins.accurev.cmd.*;
import hudson.scm.ChangeLogSet;
import hudson.scm.EditType;
import hudson.scm.PollingResult;
import hudson.scm.SCMRevisionState;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static hudson.plugins.accurev.AccurevSCM.DESCRIPTOR;

/**
 * Performs actual SCM operations
 */
public abstract class AbstractModeDelegate {

    protected static final String ACCUREV_WORKSPACE = "ACCUREV_WORKSPACE";
    protected static final String ACCUREV_REFTREE = "ACCUREV_REFTREE";
    private static final Logger logger = Logger.getLogger(AbstractModeDelegate.class.getName());
    private static final String ACCUREV_DEPOT = "ACCUREV_DEPOT";
    private static final String ACCUREV_STREAM = "ACCUREV_STREAM";
    private static final String ACCUREV_SERVER = "ACCUREV_SERVER";
    private static final String ACCUREV_SERVER_HOSTNAME = "ACCUREV_SERVER_HOSTNAME";
    private static final String ACCUREV_SERVER_PORT = "ACCUREV_SERVER_PORT";
    private static final String ACCUREV_SUBPATH = "ACCUREV_SUBPATH";
    private static final String ACCUREV_LAST_TRANSACTION = "ACCUREV_LAST_TRANSACTION";
    private static final String ACCUREV_HOME = "ACCUREV_HOME";
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

    public AbstractModeDelegate(AccurevSCM scm) {
        this.scm = scm;
    }

    private void setup(Launcher launcher, FilePath jenkinsWorkspace, TaskListener listener) throws IOException, InterruptedException {
        this.launcher = launcher;
        this.jenkinsWorkspace = jenkinsWorkspace;
        this.listener = listener;
        server = DESCRIPTOR.getServer(scm.getServerUUID());
        accurevEnv = new HashMap<>();
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

    public PollingResult compareRemoteRevisionWith(Job<?, ?> project, Launcher launcher, FilePath jenkinsWorkspace, TaskListener listener, SCMRevisionState scmrs) throws IOException, InterruptedException {
        final Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            throw new IOException("Jenkins instance is not ready");
        }
        if (project.isInQueue()) {
            listener.getLogger().println("Project build is currently in queue.");
            return PollingResult.NO_CHANGES;
        }
        if (jenkinsWorkspace == null) {
            listener.getLogger().println("No workspace required.");

            // If we're claiming not to need a workspace in order to poll, then
            // workspace will be null.  In that case, we need to run directly
            // from the project folder on the master.
            final File projectDir = project.getRootDir();
            jenkinsWorkspace = new FilePath(projectDir);
            launcher = jenkins.createLauncher(listener);
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

    protected abstract PollingResult checkForChanges(Job<?, ?> project) throws IOException, InterruptedException;

    private boolean hasStringVariableReference(final String str) {
        return StringUtils.isNotEmpty(str) && str.startsWith("$");
    }

    protected String getPollingStream(Job<?, ?> project) {
        String parsedLocalStream;
        if (hasStringVariableReference(scm.getStream())) {
            ParametersDefinitionProperty paramDefProp = project
                    .getProperty(ParametersDefinitionProperty.class);

            if (paramDefProp == null) {
                throw new IllegalArgumentException(
                        "Polling is not supported when stream name has a variable reference '" + scm.getStream() + "'.");
            }

            Map<String, String> keyValues = new TreeMap<>();

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

    public boolean checkout(Run<?, ?> build, Launcher launcher, FilePath jenkinsWorkspace, TaskListener listener,
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

        if (StringUtils.isEmpty(scm.getDepot())) {
            listener.fatalError("Must specify a depot");
            return false;
        }

        if (StringUtils.isEmpty(scm.getStream())) {
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

        //Disable for now until toggle is available. Also do not fail if admin privileges is not provided.
        if (server.isUseColor()) {
            setStreamColor();
        }

        if (!checkout(build, changelogFile)) {
            return false;
        }

        if (!populate()) {
            return false;
        }

        return captureChangeLog(build, changelogFile, streams, environment);
    }

    private boolean captureChangeLog(Run<?, ?> build, File changelogFile, Map<String, AccurevStream> streams, EnvVars environment) throws IOException, InterruptedException {
        try {
            String changeLogStream = getChangeLogStream();

            AccurevTransaction latestTransaction = History.getLatestTransaction(scm,
                    server, accurevEnv, accurevWorkingSpace, listener, accurevPath, launcher, changeLogStream, null);
            if (latestTransaction == null) {
                throw new NullPointerException("The 'hist' command did not return a transaction. Does this stream have any history yet?");
            }
            String latestTransactionID = latestTransaction.getId();
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            String latestTransactionDate = formatter.format(latestTransaction.getDate());
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
        Run<?, ?> prevbuild = null;
        if (build != null) prevbuild = build.getPreviousBuild();
        if (prevbuild != null) startTime = prevbuild.getTimestamp();
        else {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.MONTH, -1);
            startTime = c;
        }

        AccurevStream stream = streams == null ? null : streams.get(localStream);
        if (stream == null) {
            // if there was a problem, fall back to simple stream check
            return ChangeLogCmd.captureChangelog(server, accurevEnv, accurevWorkingSpace, listener, accurevPath, launcher,
                    startDateOfPopulate, startTime.getTime(),
                    localStream, changelogFile, logger, scm);
        }

        if (!getChangesFromStreams(startTime, stream, changelogFile)) {
            return ChangeLogCmd.captureChangelog(server, accurevEnv, accurevWorkingSpace, listener, accurevPath, launcher, startDateOfPopulate,
                    startTime.getTime(), localStream, changelogFile, logger, scm);
        }
        return true;
    }

    protected String getChangeLogStream() {
        return localStream;
    }

    private boolean getChangesFromStreams(final Calendar startTime, AccurevStream stream, File changelogFile) throws IOException, InterruptedException {
        List<String> changedStreams = new ArrayList<>();
        // Capture changes in all streams and parents
        boolean capturedChangelog;
        do {
            File streamChangeLog = XmlConsolidateStreamChangeLog.getStreamChangeLogFile(changelogFile, stream);
            capturedChangelog = ChangeLogCmd.captureChangelog(server, accurevEnv, accurevWorkingSpace, listener, accurevPath, launcher,
                    startDateOfPopulate, startTime == null ? null : startTime.getTime(), stream.getName(), streamChangeLog, logger, scm);
            if (capturedChangelog) {
                changedStreams.add(streamChangeLog.getName());
            }
            stream = stream.getParent();
        } while (stream != null && stream.isReceivingChangesFromParent() && capturedChangelog && startTime != null && !scm.isIgnoreStreamParent());

        XmlConsolidateStreamChangeLog.createChangeLog(changedStreams, changelogFile, getUpdateFileName());
        return capturedChangelog;
    }

    protected String getUpdateFileName() {
        return null;
    }

    protected abstract boolean checkout(Run<?, ?> build, File changeLogFile) throws IOException, InterruptedException;

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
        } catch (IOException | InterruptedException ex) {
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

        if (server != null && server.getName() != null) {
            env.put(ACCUREV_SERVER, server.getName());
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
        ChangeLogSet<?> changeSet = build.getChangeSet();
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
