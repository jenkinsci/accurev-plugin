package hudson.plugins.accurev;

import static hudson.Util.fixEmpty;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.sf.json.JSONObject;

import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.Job;
import hudson.model.ModelObject;
import hudson.model.Node;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Run;
import hudson.model.Saveable;
import hudson.model.StringParameterValue;
import hudson.model.TaskListener;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.security.ACL;
import hudson.util.DescribableList;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;

import jenkins.plugins.accurev.Accurev;
import jenkins.plugins.accurev.AccurevClient;
import jenkins.plugins.accurev.AccurevException;
import jenkins.plugins.accurev.AccurevTool;
import jenkins.plugins.accurev.UpdateCommand;
import jenkins.plugins.accurev.util.AccurevUtils;
import hudson.plugins.accurev.delegates.AbstractModeDelegate;
import hudson.plugins.accurev.extensions.AccurevSCMExtension;
import hudson.plugins.accurev.extensions.AccurevSCMExtensionDescriptor;

/**
 * Accurev SCM plugin for Jenkins
 */
public class AccurevSCM extends AccurevSCMBackwardCompatibility {

    public static final boolean VERBOSE = Boolean.getBoolean(AccurevSCM.class.getName() + ".verbose");
    protected static final List<String> DEFAULT_VALID_STREAM_TRANSACTION_TYPES;
    protected static final List<String> DEFAULT_VALID_WORKSPACE_TRANSACTION_TYPES;
    static final Date NO_TRANS_DATE = new Date(0);
    private static final Logger LOGGER = Logger.getLogger(AccurevSCM.class.getName());

    static {
        DEFAULT_VALID_STREAM_TRANSACTION_TYPES = Collections.unmodifiableList(
            Arrays.asList(
                "chstream", "defcomp", "mkstream", "promote", "demote_to", "demote_from", "purge"
            )
        );
        DEFAULT_VALID_WORKSPACE_TRANSACTION_TYPES = Collections.unmodifiableList(
            Arrays.asList(
                "add", "chstream", "co", "defcomp", "defunct", "keep",
                "mkstream", "move", "promote", "purge", "dispatch"
            )
        );
    }

    private List<UserRemoteConfig> userRemoteConfigs;

    private DescribableList<AccurevSCMExtension, AccurevSCMExtensionDescriptor> extensions;

    @CheckForNull
    private String accurevTool = null;

    @DataBoundConstructor
    public AccurevSCM(
        List<UserRemoteConfig> userRemoteConfigs,
        List<AccurevSCMExtension> extensions
    ) {
        this.userRemoteConfigs = userRemoteConfigs;
        this.extensions = new DescribableList<>(Saveable.NOOP, Util.fixNull(extensions));
    }

    /**
     * Used for testing
     *
     * @param server The now deprecated server config
     * @param depot  values need to convert to the new user config
     * @param stream values need to convert to the new user config
     */
    @Deprecated
    public AccurevSCM(AccurevServer server, String depot, String stream) {
        this.serverUUID = server.getUuid();
        this.serverName = server.getName();
        this.depot = depot;
        this.stream = stream;
    }

    public static AccurevSCMDescriptor configuration() {
        return Jenkins.getInstance().getDescriptorByType(AccurevSCMDescriptor.class);
    }

    public List<UserRemoteConfig> getUserRemoteConfigs() {
        return userRemoteConfigs;
    }

    @Nonnull
    @Override
    public String getKey() {
        StringBuilder b = new StringBuilder("accurev");
        for (UserRemoteConfig config : userRemoteConfigs) {
            b.append(' ').append(config.getUrl());
        }
        return b.toString();
    }

    @CheckForNull
    public String getAccurevTool() {
        return accurevTool;
    }

    @DataBoundSetter
    public void setAccurevTool(String accurevTool) {
        this.accurevTool = fixEmpty(accurevTool);
    }

    /**
     * {@inheritDoc}
     *
     * @return SCMDescriptor
     */
    @Override
    public AccurevSCMDescriptor getDescriptor() {
        return (AccurevSCMDescriptor) super.getDescriptor();
    }

    /**
     * Exposes AccuRev-specific information to the environment. The following
     * variables become available, if not null:
     * <ul>
     * <li>ACCUREV_DEPOT - The depot name</li>
     * <li>ACCUREV_STREAM - The stream name</li>
     * <li>ACCUREV_SERVER - The server name</li>
     * <li>ACCUREV_REFTREE - The workspace name</li>
     * <li>ACCUREV_SUBPATH - The workspace subpath</li>
     * </ul>
     *
     * @param build build
     * @param env   enviroments
     * @since 0.6.9
     */
    // TODO: 2.60+ Delete this override.
    @Override
    public void buildEnvVars(AbstractBuild<?, ?> build, Map<String, String> env) {
        buildEnvironment(build, env);
    }

    // TODO: 2.60+ - add @Override.
    public void buildEnvironment(Run<?, ?> build, Map<String, String> env) {
        AbstractModeDelegate delegate = AccurevMode.findDelegate(this);
        delegate.buildEnvVars(build, env);
    }

    /**
     * {@inheritDoc}
     *
     * @param build         build
     * @param launcher      launcher
     * @param workspace     jenkins workspace
     * @param listener      listener
     * @param changelogFile change log file
     * @param scmrs         SCMRevisionState
     * @throws IOException          on failing IO
     * @throws InterruptedException on failing interrupt
     */

    public void checkout(@Nonnull Run<?, ?> build, @Nonnull Launcher launcher, @Nonnull FilePath workspace,
                         @Nonnull TaskListener listener, @CheckForNull File changelogFile,
                         @CheckForNull SCMRevisionState scmrs) throws IOException, InterruptedException {

        Run<?, ?> lastBuild = build.getPreviousBuild();
        AccurevSCMRevisionState baseline;
        if (scmrs instanceof AccurevSCMRevisionState)
            baseline = (AccurevSCMRevisionState) scmrs;
        else if (lastBuild != null)
            baseline = (AccurevSCMRevisionState) calcRevisionsFromBuild(lastBuild, workspace, launcher, listener);
        else
            baseline = new AccurevSCMRevisionState(null); // Accurev specifies transaction start from one

        EnvVars environment = build.getEnvironment(listener);
        Map<String, Long> state = new HashMap<>();
        for (UserRemoteConfig config : userRemoteConfigs) {
            AccurevClient accurev = createClient(listener, environment, build, workspace, config);
            AccurevStreams streams = new AccurevStreams();

            StreamsCommand streamsCommand = accurev.streams().depot(config.getDepot()).toStreams(streams);
            for (AccurevSCMExtension ext : extensions) {
                ext.decorateStreamsCommand(this, config, build, accurev, listener, streamsCommand);
            }
            streamsCommand.execute();

            long latestTransaction = baseline.getTransaction(config.toString());
            AccurevTransactions transactions = new AccurevTransactions();
            HistCommand now = accurev.hist().depot(config.getDepot()).timespec("now").count(1).toTransactions(transactions);
            for (AccurevSCMExtension ext : extensions) {
                ext.decorateHistCommand(this, config, build, accurev, listener, now);
            }
            now.execute();

            AccurevTransaction transaction = transactions.get(0);
            long actualTransaction = transaction.getTransaction();
            if (actualTransaction != 0L) latestTransaction = actualTransaction;
            state.put(config.toString(), latestTransaction);

            // Check if stream is NOT fetched from Accurev.
            if (!streams.containsKey(config.getStream()))
                throw new AccurevException("Stream does not exists in the fetched result");

            PopulateCommand pop = accurev.populate().stream(config.getStream());
            for (AccurevSCMExtension ext : extensions) {
                ext.decoratePopulateCommand(this, config, build, accurev, listener, pop);
            }
            pop.execute();
        }
        build.addAction(new AccurevSCMRevisionState(state));
    }

    private AccurevClient createClient(TaskListener listener, EnvVars environment, Run<?, ?> build, FilePath workspace, UserRemoteConfig config) throws IOException, InterruptedException {
        FilePath ws = workingDirectory(workspace, environment, config.getLocalDir());

        if (ws != null) {
            ws.mkdirs();
        }
        return createClient(listener, environment, build.getParent(), AccurevUtils.workspaceToNode(workspace), ws, config);
    }

    private FilePath workingDirectory(FilePath workspace, EnvVars env, String localDir) {
        if (workspace == null) {
            return null;
        }
        if (localDir == null) localDir = ".";
        return workspace.child(env.expand(localDir));
    }

    private AccurevClient createClient(TaskListener listener, EnvVars environment, Job project, Node node, FilePath ws, UserRemoteConfig config) throws InterruptedException, IOException {

        String accurevExe = getAccurevExe(node, listener);
        Accurev accurev = Accurev.with(listener, environment).in(ws).using(accurevExe).on(environment.expand(config.getUrl()));

        // TODO extension decorate scm and client;

        // Set ACCUREV_HOME to Node's root path
        if (!environment.containsKey("ACCUREV_HOME")) {
            String path = AccurevUtils.getRootPath(ws);
            if (StringUtils.isNotBlank(path))
                environment.put("ACCUREV_HOME", path);
        }

        AccurevClient c = accurev.getClient();

        String credentialsId = config.getCredentialsId();
        if (credentialsId != null) {
            StandardUsernamePasswordCredentials cred = CredentialsMatchers.firstOrNull(
                CredentialsProvider
                    .lookupCredentials(StandardUsernamePasswordCredentials.class,
                        project, ACL.SYSTEM,
                        URIRequirementBuilder.fromUri(config.getUrl()).build()
                    ),
                CredentialsMatchers.withId(credentialsId));
            c.login().username(cred.getUsername()).password(cred.getPassword()).execute();
        }
        return c;
    }

    private String getAccurevExe(Node node, TaskListener listener) {
        return getAccurevExe(node, null, listener);
    }

    private String getAccurevExe(Node node, EnvVars env, TaskListener listener) {
        AccurevTool tool = resolveAccurevTool(listener);
        if (node != null) {
            try {
                tool = tool.forNode(node, listener);
            } catch (InterruptedException | IOException e) {
                listener.getLogger().println("Failed to get Accurev Executable");
            }
        }
        if (env != null) {
            tool = tool.forEnvironment(env);
        }
        return tool.getHome();
    }

    private AccurevTool resolveAccurevTool(TaskListener listener) {
        if (StringUtils.isBlank(accurevTool)) return AccurevTool.getDefaultInstallation();
        AccurevTool accurev = AccurevTool.configuration().getInstallation(accurevTool);
        if (accurev == null) {
            listener.getLogger().println("Selected Accurev installation does not exist. Using Default");
            accurev = AccurevTool.getDefaultInstallation();
        }
        return accurev;
    }

    /**
     * {@inheritDoc}
     *
     * @return ChangeLogParser
     */
    public ChangeLogParser createChangeLogParser() {
        return new AccurevChangeLogParser();
    }

    @Override
    public boolean requiresWorkspaceForPolling() {
        return getExtensions().stream()
            .anyMatch(AccurevSCMExtension::requiresWorkspaceForPolling);
    }

    public DescribableList<AccurevSCMExtension, AccurevSCMExtensionDescriptor> getExtensions() {
        return extensions;
    }

    @DataBoundSetter
    public void setExtensions(List<AccurevSCMExtension> extensions) {
        this.extensions = new DescribableList<>(Saveable.NOOP, Util.fixNull(extensions));
    }

    /**
     * Gets the lock to be used on "normal" accurev commands, or
     * <code>null</code> if command synchronization is switched off.
     *
     * @return See above.
     */
    public Lock getOptionalLock() {
        final AccurevServer server = getServer();
        final boolean shouldLock = server != null && server.isSyncOperations();
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
    public SCMRevisionState calcRevisionsFromBuild(@Nonnull Run<?, ?> build, @Nullable FilePath workspace,
                                                   @Nullable Launcher launcher, @Nonnull TaskListener listener) throws IOException, InterruptedException {
        AccurevSCMRevisionState scmrs = build.getAction(AccurevSCMRevisionState.class);
        if (scmrs == null) {
            for (Run<?, ?> b = build; b != null; b = b.getPreviousBuild()) {
                scmrs = b.getAction(AccurevSCMRevisionState.class);
                if (scmrs != null) {
                    break;
                }
            }
        }
        return scmrs == null ? new AccurevSCMRevisionState(null) : scmrs;
    }

    @Override
    public PollingResult compareRemoteRevisionWith(@Nonnull Job<?, ?> project, @Nullable Launcher launcher,
                                                   @Nullable FilePath workspace, @Nonnull TaskListener listener,
                                                   @Nonnull SCMRevisionState scmrs) throws IOException, InterruptedException {
        // If workspace is required (then workspace is not null) and project is building then please stop
        // Accurev requires workspace and built being stopped cause otherwise it might break another build running.
        if (workspace != null && (project.isBuilding() || project.isInQueue())) {
            listener.getLogger().println("[poll] Build requires workspace and is currently building. Halting poll.");
            return PollingResult.NO_CHANGES;
        }

        final AccurevSCMRevisionState baseline;

        if (scmrs instanceof AccurevSCMRevisionState)
            baseline = (AccurevSCMRevisionState) scmrs;
        else if (project.getLastBuild() != null)
            baseline = (AccurevSCMRevisionState) calcRevisionsFromBuild(project.getLastBuild(),
                launcher != null ? workspace : null, launcher, listener);
        else
            baseline = new AccurevSCMRevisionState(null); // Accurev specifies transaction start from one

        if (project.getLastBuild() == null || baseline == null) {
            listener.getLogger().println("[poll] No previous build, so lets start the build.");
            return PollingResult.NO_CHANGES;
        }

        Node node;
        if (workspace != null && !getDescriptor().isPollOnMaster())
            node = AccurevUtils.workspaceToNode(workspace);
        else
            node = Jenkins.getInstance();

        EnvVars environment = project.getEnvironment(node, listener);

        for (UserRemoteConfig config : userRemoteConfigs) {
            AccurevClient accurev = createClient(listener, environment, project, node, workspace, config);

            long latestTransaction = baseline.getTransaction(config.toString());
            AccurevTransactions transactions = new AccurevTransactions();
            HistCommand now = accurev.hist().depot(config.getDepot()).timespec("now").count(1).toTransactions(transactions);
            for (AccurevSCMExtension ext : extensions) {
                ext.decorateHistCommand(this, config, project, accurev, listener, now);
            }
            now.execute();

            List<String> paths = new ArrayList<>();

            UpdateCommand update = accurev.update()
                .stream(config.getStream())
                .preview(paths)
                .range(latestTransaction, baseline.getTransaction(config.toString()));

            for (AccurevSCMExtension ext : getExtensions()) {
                ext.decorateUpdateCommand(this, project, accurev, listener, update);
            }

            update.execute();
        }
        return PollingResult.NO_CHANGES;
    }

    public boolean hasStringVariableReference(final String str) {
        return StringUtils.isNotEmpty(str) && str.startsWith("$");
    }

    /**
     * @param project  Running build
     * @param listener Listener that it runs the build on
     * @return Stream name tries to expand Variable reference to a String
     * @throws IllegalArgumentException Thrown when Variable reference is not supported or cannot expand
     */
    public String getPollingStream(Job<?, ?> project, TaskListener listener) throws IllegalArgumentException {
        String parsedLocalStream;
        if (hasStringVariableReference(stream)) {
            ParametersDefinitionProperty paramDefProp = project
                .getProperty(ParametersDefinitionProperty.class);

            if (paramDefProp == null) {
                throw new IllegalArgumentException(
                    "Polling is not supported when stream name has a variable reference '" + stream + "'.");
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
            parsedLocalStream = environment.expand(getStream());
            listener.getLogger().println("... expanded '" + stream + "' to '" + parsedLocalStream + "'.");
        } else {
            parsedLocalStream = stream;
        }

        if (hasStringVariableReference(parsedLocalStream)) {
            throw new IllegalArgumentException(
                "Failed to expand variable reference '" + stream + "'.");
        }
        return parsedLocalStream;
    }

    public Object readResolve() throws IOException {
        migrate();
        return this;
    }

    @SuppressWarnings("deprecation")
    void migrate() throws IOException {
        // Migrate data
        AccurevServer server = getServer();
        if (server != null && userRemoteConfigs == null) {
            server.migrateCredentials();
            userRemoteConfigs = new ArrayList<>();
            userRemoteConfigs.add(new UserRemoteConfig(server.getUrl(), server.getCredentialsId(), depot, stream, directoryOffset));
            LOGGER.info("Migrated server and old config to userRemoteConfig successfully");
        }

        if (extensions == null)
            extensions = new DescribableList<>(Saveable.NOOP);

        migrate(server);
    }

    @Extension
    public static class AccurevSCMDescriptor extends SCMDescriptor<AccurevSCM> implements ModelObject {

        /**
         * The accurev server has been known to crash if more than one copy of
         * the accurev has been run concurrently on the local machine. <br>
         * Also, the accurev client has been known to complain that it's not
         * logged in if another client on the same machine logs in again.
         */
        transient static final Lock ACCUREV_LOCK = new ReentrantLock();
        private static final Logger DESCRIPTORLOGGER = Logger.getLogger(AccurevSCMDescriptor.class.getName());
        private transient List<AccurevServer> _servers;
        // The servers field is here for backwards compatibility.
        // The transient modifier means it won't be written to the config file
        private transient List<AccurevServer> servers;
        private boolean pollOnMaster;

        /**
         * Constructs a new AccurevSCMDescriptor.
         */
        public AccurevSCMDescriptor() {
            super(AccurevSCM.class, null);
            load();
        }

        public static void lock() {
            ACCUREV_LOCK.lock();
        }

        public static void unlock() {
            ACCUREV_LOCK.unlock();
        }

        public List<AccurevSCMExtensionDescriptor> getExtensionDescriptors() {
            return AccurevSCMExtensionDescriptor.all();
        }

        @Override
        @Nonnull
        public String getDisplayName() {
            return "AccuRev";
        }

        @SuppressWarnings("unused") // used by stapler
        public boolean showAccurevToolOptions() {
            return AccurevTool.configuration().getInstallations().length > 1;
        }

        @SuppressWarnings("unused") // Used by stapler
        public ListBoxModel doFillAccurevToolItems() {
            ListBoxModel r = new ListBoxModel();
            for (AccurevTool accurev : AccurevTool.configuration().getInstallations()) {
                r.add(accurev.getName());
            }
            return r;
        }

        /**
         * {@inheritDoc}
         *
         * @param req      request
         * @param formData json object
         * @return boolean
         * @throws hudson.model.Descriptor.FormException if form data is incorrect/incomplete
         */
        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) throws FormException {
            req.bindJSON(this, formData);
            save();
            return true;
        }

        @CheckForNull
        public AccurevServer getServer(String uuid) {
            if (uuid == null || this._servers == null) {
                LOGGER.fine("No server found. - getServer(NULL)");
                return null;
            }
            for (AccurevServer server : this._servers) {
                if (uuid.equals(server.getUuid())) {
                    return server;
                } else if (uuid.equals(server.getName())) {
                    // support old server name
                    return server;
                }
            }
            LOGGER.fine("No server found.");
            return null;
        }

        @Override
        public boolean isApplicable(Job project) {
            return true;
        }

        public boolean isPollOnMaster() {
            return pollOnMaster;
        }

        public void setPollOnMaster(boolean pollOnMaster) {
            this.pollOnMaster = pollOnMaster;
        }

        @Deprecated
        @Nonnull
        public List<AccurevServer> getServers() {
            if (this._servers == null) {
                this._servers = new ArrayList<>();
            }
            // We put this here to maintain backwards compatibility
            // because we changed the name of the 'servers' field to '_servers'
            if (this.servers != null) {
                this._servers.addAll(servers);
                servers = null;
            }
            return this._servers;
        }

        @Deprecated
        public void setServers(List<AccurevServer> servers) {
            this._servers = servers;
        }
    }

    public static final class AccurevServer extends AccurevServerBackwardCompatibility {

        public AccurevServer(String uuid, String name, String host, int port, String credentialsId) {
            super(uuid, name, host, port, credentialsId);
        }

        /* Used for testing migration */
        public AccurevServer(String uuid, String name, String host, int port, String username, String password) {
            super(uuid, name, host, port, username, password);
        }
    }

    /**
     * Class responsible for parsing change-logs recorded by the builds. If this
     * is renamed or moved it'll break data-compatibility with old builds.
     */
    private static final class AccurevChangeLogParser extends ParseChangeLog {
    }
}
