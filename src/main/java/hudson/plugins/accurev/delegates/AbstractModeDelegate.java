package hudson.plugins.accurev.delegates;

import static java.nio.charset.StandardCharsets.UTF_8;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.model.AbstractBuild;
import hudson.model.Job;
import hudson.model.Run;
import hudson.plugins.accurev.AccuRevHiddenParametersAction;
import hudson.plugins.accurev.AccurevPromoteTrigger;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.AccurevStream;
import hudson.plugins.accurev.AccurevTransaction;
import hudson.plugins.accurev.GetConfigWebURL;
import hudson.plugins.accurev.XmlConsolidateStreamChangeLog;
import hudson.plugins.accurev.cmd.ChangeLogCmd;
import hudson.plugins.accurev.cmd.GetAccuRevVersion;
import hudson.model.TaskListener;
import hudson.plugins.accurev.*;
import hudson.plugins.accurev.cmd.PopulateCmd;
import hudson.plugins.accurev.cmd.SetProperty;
import hudson.plugins.accurev.cmd.ShowStreams;
import hudson.plugins.accurev.cmd.*;
import hudson.scm.PollingResult;
import hudson.scm.SCMRevisionState;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import jenkins.model.Jenkins;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;

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
    private static final String ACCUREV_LATEST_TRANSACTION_ID = "ACCUREV_LATEST_TRANSACTION_ID";
    private static final String ACCUREV_LATEST_TRANSACTION_DATE = "ACCUREV_LATEST_TRANSACTION_DATE";
    private static final String ACCUREV_HOME = "ACCUREV_HOME";
    private static final String JOBS = "jobs";
    
    private static final String ACCUREVLASTTRANSFILENAME = "AccurevLastTrans.txt";
    private static final String POPULATE_FILES = "PopulateFiles.txt";
    private static final String JENKINS_HOME = "JENKINS_HOME";
    private static final String JOB_BASE_NAME = "JOB_BASE_NAME";
    public final AccurevSCM scm;
    protected Launcher launcher;
    protected AccurevSCM.AccurevServer server;
    protected EnvVars accurevEnv;
    protected FilePath jenkinsWorkspace;
    protected TaskListener listener;
    protected FilePath accurevWorkingSpace;
    protected String localStream;
    protected Date startDateOfPopulate;
    protected String accurevTool;

    public AbstractModeDelegate(AccurevSCM scm) {
        this.scm = scm;
    }

    public void setup(Launcher launcher, FilePath jenkinsWorkspace, TaskListener listener) throws IOException, InterruptedException {
        this.launcher = launcher;
        this.jenkinsWorkspace = jenkinsWorkspace;
        this.listener = listener;
        server = scm.getServer();
        accurevEnv = new EnvVars();
        if (jenkinsWorkspace != null) {
            accurevWorkingSpace = new FilePath(jenkinsWorkspace, scm.getDirectoryOffset() == null ? "" : scm.getDirectoryOffset());
            if (!Login.ensureLoggedInToAccurev(scm, server, accurevEnv, jenkinsWorkspace, listener, launcher)) {
                throw new IllegalArgumentException("Authentication failure");
            }

            if (scm.isSynctime()) {
                listener.getLogger().println("Synchronizing clock with the server...");
                if (!Synctime.synctime(scm, server, accurevEnv, jenkinsWorkspace, listener, launcher)) {
                    throw new IllegalArgumentException("Synchronizing clock failure");
                }
            }
        }
    }

    public PollingResult compareRemoteRevisionWith(Job<?, ?> project, Launcher launcher, FilePath jenkinsWorkspace, TaskListener listener, SCMRevisionState state) throws IOException, InterruptedException {
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
            launcher = Jenkins.getInstance().createLauncher(listener);
        }
        listener.getLogger().println("Running commands from folder \"" + jenkinsWorkspace + '"');
        setup(launcher, jenkinsWorkspace, listener);

        return checkForChanges(project);
    }

    protected abstract PollingResult checkForChanges(Job<?, ?> project) throws IOException, InterruptedException;

    public boolean checkout(Run<?, ?> build, Launcher launcher, FilePath jenkinsWorkspace, TaskListener listener,
                            File changelogFile) throws IOException, InterruptedException {

        setup(launcher, jenkinsWorkspace, listener);

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
        accurevEnv.putAll(environment);
        localStream = environment.expand(scm.getStream());

        listener.getLogger().println("Getting a list of streams...");
        final Map<String, AccurevStream> streams = ShowStreams.getStreams(scm, localStream, server, accurevEnv, jenkinsWorkspace, listener,
                launcher);
        if (streams == null) {
            throw new IllegalStateException("Stream(s) not found");
        }

        if (!streams.containsKey(localStream)) {
            listener.fatalError("The specified stream, '" + localStream + "' does not appear to exist!");
            throw new IllegalStateException("Specified stream not found");
        }

        if (server.isUseColor()) {
            setStreamColor();
        }

        return checkout(build, changelogFile) && populate() && captureChangeLog(build, changelogFile, streams);

    }

    private boolean captureChangeLog(Run<?, ?> build, File changelogFile, Map<String, AccurevStream> streams) throws IOException, InterruptedException {
        try {
            AccurevTransaction latestTransaction = getLatestTransactionFromStreams(streams);
            if (latestTransaction == null) {
                throw new NullPointerException("The 'hist' command did not return a transaction. Does this stream have any history yet?");
            }
            String latestTransactionID = latestTransaction.getId();
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            String latestTransactionDate = formatter.format(latestTransaction.getDate());
            listener.getLogger().println("Latest Transaction ID: " + latestTransactionID);
            listener.getLogger().println("Latest transaction Date: " + latestTransactionDate);

            EnvVars envVars = new EnvVars();
            envVars.put(ACCUREV_LATEST_TRANSACTION_ID, latestTransactionID);
            envVars.put(ACCUREV_LATEST_TRANSACTION_DATE, latestTransactionDate);
            AccurevPromoteTrigger.setLastTransaction(build.getParent(), latestTransactionID);
            build.addAction(new AccuRevHiddenParametersAction(envVars));

        } catch (Exception e) {
            listener.error("There was a problem getting the latest transaction info from the stream.");
            e.printStackTrace(listener.getLogger());
        }

        listener.getLogger().println(
                "Calculating changelog" + (scm.isIgnoreStreamParent() ? ", ignoring changes in parent" : "") + "...");

        final Calendar startTime;
        Run<?, ?> previousBuild = null;
        if (build != null) previousBuild = build.getPreviousBuild();
        if (previousBuild != null) startTime = previousBuild.getTimestamp();
        else {
            Calendar c = Calendar.getInstance();
            c.add(Calendar.DAY_OF_YEAR, -7);
            startTime = c;
        }

        Map<String, GetConfigWebURL> webURL = ChangeLogCmd.retrieveWebURL(server, accurevEnv, accurevWorkingSpace, listener, launcher, logger, scm);
        AccurevStream stream = streams == null ? null : streams.get(localStream);
        if (stream == null) {
            // if there was a problem, fall back to simple stream check
            return ChangeLogCmd.captureChangelog(server, accurevEnv, accurevWorkingSpace, listener, launcher,
                    startDateOfPopulate, startTime.getTime(),
                    localStream, changelogFile, logger, scm, webURL);
        }

        // Too confusing reading it
        // This check is for whether to catch changes for one or multiple streams
        // ALso if it should ignore changes on Parent
        // Doing too much in too few lines to make it apparant
        // High potential for simple rewrite!
        if (!getChangesFromStreams(startTime, stream, changelogFile, webURL)) {
            return ChangeLogCmd.captureChangelog(server, accurevEnv, accurevWorkingSpace, listener, launcher, startDateOfPopulate,
                    startTime.getTime(), localStream, changelogFile, logger, scm, webURL);
        }
        return true;
    }

    protected String getChangeLogStream() {
        return localStream;
    }

    private boolean getChangesFromStreams(final Calendar startTime, AccurevStream stream, File changelogFile, Map<String, GetConfigWebURL> webURL) throws IOException {
        List<String> changedStreams = new ArrayList<>();
        // Capture changes in all streams and parents
        boolean capturedChangelog;
        do {
            File streamChangeLog = XmlConsolidateStreamChangeLog.getStreamChangeLogFile(changelogFile, stream);
            capturedChangelog = ChangeLogCmd.captureChangelog(server, accurevEnv, accurevWorkingSpace, listener, launcher,
                    startDateOfPopulate, startTime == null ? null : startTime.getTime(), stream.getName(), streamChangeLog, logger, scm, webURL);
            if (capturedChangelog) {
                changedStreams.add(streamChangeLog.getName());
            }
            stream = stream.getParent();
        }
        while (stream != null && stream.isReceivingChangesFromParent() && capturedChangelog && startTime != null && !scm.isIgnoreStreamParent());

        XmlConsolidateStreamChangeLog.createChangeLog(changedStreams, changelogFile, getUpdateFileName());
        return capturedChangelog;
    }

    private AccurevTransaction getLatestTransactionFromStreams(Map<String, AccurevStream> streams) throws Exception {
        AccurevTransaction transaction = null;
        AccurevStream stream = streams.get(getChangeLogStream());
        do {
            AccurevTransaction other = History.getLatestTransaction(scm,
                    server, accurevEnv, accurevWorkingSpace, listener, launcher, stream.getName(), null);
            if (null == transaction && null != other) transaction = other;
            else if ((null != transaction && null != other) && Integer.parseInt(other.getId()) > Integer.parseInt(transaction.getId())) {
                transaction = other;
            }
            stream = stream.getParent();
        } while (stream != null && stream.isReceivingChangesFromParent() && !scm.isIgnoreStreamParent());
        return transaction;
    }

    public EnvVars getAccurevEnv() {
        return accurevEnv;
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

    private void setStreamColor() throws IOException {
        if (isSteamColorEnabled()) {
            SetProperty.setproperty(scm, accurevWorkingSpace, listener, launcher, accurevEnv, server, getStreamColorStream(),
                    getStreamColor(), getStreamTypeParameter());
        }
    }

     * populate the whole workspace if workspace delete option selected else populate latest transactions from the jenkins
     * @param populateRequired
     * @return
     * @throws IOException
     */
    protected boolean populate(boolean populateRequired) throws IOException {
        if (populateRequired) {
            String stream = getPopulateStream();
            int lastTransaction = NumberUtils.toInt(getLastBuildTransaction(), 0);
            logger.info("Last transaction from jenkin " + lastTransaction);
            String filePath = (lastTransaction == 0 || scm.isDeleteWorkspaceBeforeBuildStarts()) ? null : getFileRevisionsToBeIncluded(
                    lastTransaction, stream);
            logger.info("populate file path " + filePath);
            PopulateCmd pop = new PopulateCmd();
            if (pop.populate(scm, launcher, listener, server, getPopulateStream(), true, getPopulateFromMessage(), accurevWorkingSpace, accurevEnv)) {
                    filePath)) {
                startDateOfPopulate = pop.get_startDateOfPopulate();
                // Delete the temporary populate file information.
                if (filePath != null) {
                    File populateFile = new File(filePath);
                    boolean deleted = populateFile.delete();
                    logger.info("temporary file deleted " + deleted);
                }
            }
            } else {
                return false;
            }
        }
        } else {
            startDateOfPopulate = new Date();
        }
        return true;

    }

    protected boolean populate() throws IOException {
        return populate(isPopulateRequired());
    }

    public void buildEnvVars(AbstractBuild<?, ?> build, Map<String, String> env) {
        try {
            setup(null, null, TaskListener.NULL);
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

        // ACCUREV_HOME is added to the build env variables
        if (System.getenv(ACCUREV_HOME) != null) {
            env.put(ACCUREV_HOME, System.getenv(ACCUREV_HOME));
        }
        buildEnvVarsCustom(build, env);
    }

    protected void buildEnvVarsCustom(AbstractBuild<?, ?> build, Map<String, String> env) {
        // override to put implementation specific values
    }

    /**
     * get color type parameter from the AccuRev Version If version less than 6 or equal to 6.0.x the color parameter will be style if
     * version greater than 6 like 6.1.x or 7 then color parameter will streamStyle
     * 
     * @return
     */
    private String getStreamTypeParameter() {
        String fullVersion = GetAccuRevVersion.getAccuRevVersion().trim();
        String partialversion = fullVersion.substring(fullVersion.indexOf(" ") + 1);
        String version = partialversion.substring(0, partialversion.indexOf(" "));
        String[] versionSplits = version.split("\\.");
        String type = ((Integer.parseInt(versionSplits[0]) < 6) || (Integer.parseInt(versionSplits[0]) == 6 && Integer
                .parseInt(versionSplits[1]) < 1)) ? "style" : "streamStyle";
        logger.info("Current AccuRev version " + fullVersion + " color type parameter " + type);
        return type;
    }

    /**
     * Get last transaction build from the jenkins for the currently running project
     * @return
     * @throws IOException
     */
    private String getLastBuildTransaction() throws IOException {
        StringBuilder path = new StringBuilder(accurevEnv.get(JENKINS_HOME)).append("\\").append(JOBS).append("\\")
                .append(accurevEnv.get(JOB_BASE_NAME));
        File f = new File(path.toString(), ACCUREVLASTTRANSFILENAME);
        if (!f.exists()) {
            return null;
        }
        try (BufferedReader br = Files.newBufferedReader(f.toPath(), UTF_8)) {
            return br.readLine();
        }
    }

    /**
     * Get list of new files to be added into the jenkins build from a given a transaction.
     * @param lastTransaction
     * @param stream
     * @return
     * @throws IOException
     */

    private String getFileRevisionsToBeIncluded(int lastTransaction, String stream) throws IOException {
        List<AccurevTransaction> transactions = History.getTransactionsAfterLastTransaction(scm, server, accurevEnv,
                accurevWorkingSpace, listener, launcher, stream, lastTransaction);
     // collect all the files from the list of transactions and remove duplicates from the list of files.
        List<String> fileRevisions = transactions.stream().filter(t -> t != null).map(t -> t.getAffectedPaths())
                                     .flatMap(Collection<String>::stream).collect(Collectors.toList())
                                     .parallelStream().distinct().collect(Collectors.toList());
        return (!fileRevisions.isEmpty()) ? getPopulateFilePath(fileRevisions) : null;
    }
    /**
     * Create a text file to keep the list of files to be populated.
     * @param fileRevisions
     * @return
     */
    private String getPopulateFilePath(List<String> fileRevisions) {
        BufferedWriter bw = null;
        File populateFile = null;
        String filepath = null;
        try {
            StringBuilder path = new StringBuilder(accurevEnv.get(JENKINS_HOME)).append("\\").append(JOBS).append("\\")
                    .append(accurevEnv.get(JOB_BASE_NAME));
            populateFile = new File(path.toString(), POPULATE_FILES);
            filepath = populateFile.getAbsolutePath();
            logger.info("populate file path is " + populateFile.getAbsolutePath());
            bw = Files.newBufferedWriter(populateFile.toPath(), UTF_8);
            for (String filePath : fileRevisions) {
                bw.write(filePath);
                bw.newLine();
            }
        }
        catch (IOException exe) {
            logger.info("Exception happend to write in a file."+exe);
        }
        finally {
            try {
                if (bw != null)
                    bw.close();
            }
            catch (IOException ex) {
                logger.info("Exception happend to close the buffered writer."+ex);
            }
        }
        return filepath;
    }
}
