package hudson.plugins.accurev;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import hudson.*;
import hudson.model.*;
import hudson.plugins.accurev.delegates.AbstractModeDelegate;
import hudson.plugins.accurev.extensions.AccurevSCMExtension;
import hudson.plugins.accurev.extensions.AccurevSCMExtensionDescriptor;
import hudson.scm.*;
import hudson.security.ACL;
import hudson.util.DescribableList;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import jenkins.plugins.accurev.Accurev;
import jenkins.plugins.accurev.AccurevClient;
import jenkins.plugins.accurev.AccurevException;
import jenkins.plugins.accurev.AccurevTool;
import jenkins.plugins.accurev.util.AccurevUtils;
import jenkins.plugins.accurev.util.UUIDUtils;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

/**
 * @author connollys
 * @since 09-Oct-2007 16:17:34
 */
public class AccurevSCM extends SCM {

// ------------------------------ FIELDS ------------------------------

    public static final boolean VERBOSE = Boolean.getBoolean(AccurevSCM.class.getName() + ".verbose");
    static final Date NO_TRANS_DATE = new Date(0);
    private static final Logger LOGGER = Logger.getLogger(AccurevSCM.class.getName());
    private final String depot;
    private final String stream;
    private String serverName;
    private boolean ignoreStreamParent;
    private String wspaceORreftree;
    private boolean cleanreftree;
    private String workspace;
    private boolean useSnapshot;
    private boolean dontPopContent;
    private String snapshotNameFormat;
    private boolean synctime;
    private String reftree;
    private String subPath;
    private String filterForPollSCM;
    private String directoryOffset;
    private boolean useReftree;
    private boolean useWorkspace;
    private boolean noWspaceNoReftree;
    private String serverUUID;

    private DescribableList<AccurevSCMExtension, AccurevSCMExtensionDescriptor> extensions;

    @CheckForNull
    private String accurevTool = null;

    @DataBoundConstructor
    public AccurevSCM(
        String serverUUID, String depot, String stream
    ) {
        this.serverUUID = serverUUID;
        this.depot = depot;
        this.stream = stream;
        AccurevServer server = getDescriptor().getServer(serverUUID);
        if (server != null) serverName = server.getName();
        updateMode();
    }

    public static AccurevSCMDescriptor configuration() {
        return Jenkins.getInstance().getDescriptorByType(AccurevSCMDescriptor.class);
    }

    public String getDepot() {
        return depot;
    }

    public String getServerName() {
        return serverName;
    }

    @DataBoundSetter
    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getServerUUID() {
        return serverUUID;
    }

    public void setServerUUID(String uuid) {
        serverUUID = uuid;
    }

    @Nonnull
    @Override
    public String getKey() {
        StringBuilder b = new StringBuilder("accurev");
        // TODO should handle multiple repos
        AccurevServer server = getServer();
        if (server != null)
            b.append(' ').append(server.getUrl());
        return b.toString();
    }

    /**
     * Getter for Accurev server
     *
     * @return AccurevServer based on serverUUID (or serverName if serverUUID is null)
     */
    @CheckForNull
    public AccurevServer getServer() {
        AccurevServer server;
        AccurevSCMDescriptor descriptor = getDescriptor();
        if (serverUUID == null) {
            if (serverName == null) {
                // No fallback
                LOGGER.severe("AccurevSCM.getServer called but serverName and serverUUID are NULL!");
                return null;
            }
            LOGGER.warning("Getting server by name (" + serverName + "), because UUID is not set.");
            server = descriptor.getServer(serverName);
            if (server != null) {
                this.setServerUUID(server.getUuid());
                descriptor.save();
            }
        } else {
            server = descriptor.getServer(serverUUID);
        }
        return server;
    }

    public String getStream() {
        return stream;
    }

    public String getWspaceORreftree() {
        return wspaceORreftree;
    }

    @DataBoundSetter
    public void setWspaceORreftree(String wspaceORreftree) {
        this.wspaceORreftree = wspaceORreftree;
        updateMode();
    }

    public String getReftree() {
        return reftree;
    }

    @DataBoundSetter
    public void setReftree(String reftree) {
        this.reftree = reftree;
    }

    public String getWorkspace() {
        return workspace;
    }

    @DataBoundSetter
    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    @CheckForNull
    public String getAccurevTool() {
        return accurevTool;
    }

    @DataBoundSetter
    public void setAccurevTool(String accurevTool) {
        this.accurevTool = accurevTool;
    }

    public String getSubPath() {
        return subPath;
    }

    @DataBoundSetter
    public void setSubPath(String subPath) {
        this.subPath = subPath;
    }

    public String getFilterForPollSCM() {
        return filterForPollSCM;
    }

    @DataBoundSetter
    public void setFilterForPollSCM(String filterForPollSCM) {
        this.filterForPollSCM = filterForPollSCM;
    }

    public String getSnapshotNameFormat() {
        return snapshotNameFormat;
    }

    @DataBoundSetter
    public void setSnapshotNameFormat(String snapshotNameFormat) {
        this.snapshotNameFormat = snapshotNameFormat;
    }

    public boolean isIgnoreStreamParent() {
        return ignoreStreamParent;
    }

    @DataBoundSetter
    public void setIgnoreStreamParent(boolean ignoreStreamParent) {
        this.ignoreStreamParent = ignoreStreamParent;
    }

    public boolean isSynctime() {
        return synctime;
    }

    @DataBoundSetter
    public void setSynctime(boolean synctime) {
        this.synctime = synctime;
    }

    public boolean isDontPopContent() {
        return dontPopContent;
    }

    @DataBoundSetter
    public void setDontPopContent(boolean dontPopContent) {
        this.dontPopContent = dontPopContent;
    }

    public boolean isCleanreftree() {
        return cleanreftree;
    }

    @DataBoundSetter
    public void setCleanreftree(boolean cleanreftree) {
        this.cleanreftree = cleanreftree;
    }

    public boolean isUseSnapshot() {
        return useSnapshot;
    }

    @DataBoundSetter
    public void setUseSnapshot(boolean useSnapshot) {
        this.useSnapshot = useSnapshot;
        if (!useSnapshot) {
            snapshotNameFormat = "";
        }
    }

    public boolean isUseReftree() {
        return useReftree;
    }

    public boolean isUseWorkspace() {
        return useWorkspace;
    }

    public boolean isNoWspaceNoReftree() {
        return noWspaceNoReftree;
    }

    public String getDirectoryOffset() {
        return directoryOffset;
    }

// ------------------------ INTERFACE METHODS ------------------------
// --------------------- Interface Describable ---------------------

    @DataBoundSetter
    public void setDirectoryOffset(String directoryOffset) {
        this.directoryOffset = directoryOffset;
    }

    private void updateMode() {
        AccurevMode accurevMode = AccurevMode.findMode(this);
        useReftree = accurevMode.isReftree();
        useWorkspace = accurevMode.isWorkspace();
        noWspaceNoReftree = accurevMode.isNoWorkspaceOrRefTree();
    }

// -------------------------- OTHER METHODS --------------------------

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
    @Override
    public void buildEnvVars(AbstractBuild<?, ?> build, Map<String, String> env) {
        // call super even though SCM.buildEnvVars currently does nothing - this could change
        super.buildEnvVars(build, env);
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
     * @throws java.io.IOException            on failing IO
     * @throws java.lang.InterruptedException on failing interrupt
     */

    public void checkout(@Nonnull Run<?, ?> build, @Nonnull Launcher launcher, @Nonnull FilePath workspace,
                         @Nonnull TaskListener listener, @CheckForNull File changelogFile,
                         @CheckForNull SCMRevisionState scmrs) throws IOException, InterruptedException {

        String depot = Util.fixEmptyAndTrim(this.depot);
        String stream = Util.fixEmptyAndTrim(this.stream);

        if (depot == null)
            throw new AccurevException("Depot must be specified");
        if (stream == null)
            throw new AccurevException("Stream must be specified");

        Run<?, ?> lastBuild = build.getPreviousBuild();
        AccurevSCMRevisionState baseline;
        if (scmrs instanceof AccurevSCMRevisionState)
            baseline = (AccurevSCMRevisionState) scmrs;
        else if (lastBuild != null)
            baseline = (AccurevSCMRevisionState) calcRevisionsFromBuild(lastBuild, workspace, launcher, listener);
        else
            baseline = new AccurevSCMRevisionState(1); // Accurev specifies transaction start from one

        if (baseline == null) {
            throw new AccurevException("Baseline cannot be null");
        }
        EnvVars environment = build.getEnvironment(listener);
        AccurevClient accurev = createClient(listener, environment, build, workspace);

        int latestTransaction = baseline.getTransaction();
        int actualTransaction = accurev.getLatestTransaction(getDepot()).getTransaction();
        if (actualTransaction != 0) latestTransaction = actualTransaction;

        accurev.update().stream(getStream()).range(latestTransaction, baseline.getTransaction()).execute();

        AccurevStreams streams;
        if (ignoreStreamParent) streams = accurev.getStream(stream);
        else streams = accurev.getStreams(depot);

        if (streams == null)
            throw new AccurevException("Stream(s) not found");

        // Check if stream is NOT fetched from Accurev.
        if (!streams.containsKey(stream))
            throw new AccurevException("Stream does not exists in the fetched result");

//
//        boolean checkout = AccurevMode.findDelegate(this).checkout(build, launcher, workspace, listener, changelogFile);
//        if (checkout) listener.getLogger().println("Checkout done");
//        else listener.getLogger().println("Checkout failed");
//
        build.addAction(new AccurevSCMRevisionState(latestTransaction));
    }

    private AccurevClient createClient(TaskListener listener, EnvVars environment, Run<?, ?> build, FilePath workspace) throws IOException, InterruptedException {
        FilePath ws = workingDirectory(build.getParent(), workspace, environment, listener);

        if (ws != null) {
            ws.mkdirs();
        }
        return createClient(listener, environment, build.getParent(), AccurevUtils.workspaceToNode(workspace), ws);
    }

    private FilePath workingDirectory(Job<?, ?> parent, FilePath workspace, EnvVars environment, TaskListener listener) {
        if (workspace == null) {
            return null;
        }

        // TODO extension effecting workspace
        return workspace;
    }

    private AccurevClient createClient(TaskListener listener, EnvVars environment, Job parent, Node node, FilePath ws) throws InterruptedException, IOException {
        AccurevServer server = getServer();
        if (server == null) {
            throw new AccurevException("No server, selected");
        }
        StandardUsernamePasswordCredentials credentials = server.getCredentials();
        if (credentials == null) {
            throw new AccurevException("No credentials provided");
        }
        String accurevExe = getAccurevExe(node, listener);
        Accurev accurev = Accurev.with(listener, environment).in(ws).using(accurevExe).on(server.getUrl());

        // TODO extension decorate scm and client;

        AccurevClient c = accurev.getClient();
        c.login().username(credentials.getUsername()).password(credentials.getPassword()).execute();
        if (this.isSynctime()) {
            c.syncTime();
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
        return scmrs == null ? new AccurevSCMRevisionState(1) : scmrs;
    }

    @Override
    public PollingResult compareRemoteRevisionWith(@Nonnull Job<?, ?> project, @Nullable Launcher launcher,
                                                   @Nullable FilePath workspace, @Nonnull TaskListener listener,
                                                   @Nonnull SCMRevisionState scmrs) throws IOException, InterruptedException {
        if (project.isInQueue()) {
            listener.getLogger().println("[poll] Build is currently in queue.");
            return PollingResult.NO_CHANGES;
        }
        // If workspace is required and project is building then please stop
        if (workspace != null && project.isBuilding()) {
            listener.getLogger().println("[poll] Build requires workspace and is currently building. Halting poll.");
            return PollingResult.NO_CHANGES;
        } else {
            workspace = new FilePath(project.getRootDir());
            launcher = Jenkins.getInstance().createLauncher(listener);
        }

        final AccurevSCMRevisionState baseline;
        Run<?, ?> lastBuild = project.getLastBuild();

        if (scmrs instanceof AccurevSCMRevisionState)
            baseline = (AccurevSCMRevisionState) scmrs;
        else if (lastBuild != null)
            baseline = (AccurevSCMRevisionState) calcRevisionsFromBuild(lastBuild, workspace, launcher, listener);
        else
            baseline = new AccurevSCMRevisionState(1); // Accurev specifies transaction start from one

        if (lastBuild == null || baseline == null) {
            listener.getLogger().println("[poll] No previous build, so lets start the build.");
            return PollingResult.NO_CHANGES; // TODO remove after testing
        }

        final Node node = AccurevUtils.workspaceToNode(workspace);
        final EnvVars environment = project.getEnvironment(node, listener);

        AccurevClient accurev = createClient(listener, environment, project, node, workspace);

        // Run update command - check using reference tree or stream name
        // command.update(scm, client)

        // Filter ignored file changes

        List<String> paths = new ArrayList<>();
        accurev.update().preview(paths);

        if (isUseReftree()) {
            // build if any changes
        }


        // Filter parent changes - use hist command to see if transaction
        if (isIgnoreStreamParent()) {

        }

        AbstractModeDelegate delegate = AccurevMode.findDelegate(this);
        return delegate.compareRemoteRevisionWith(project, launcher, workspace, listener, baseline);
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

    //--------------------------- Inner Class - DescriptorImplementation ----------------------------
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
        private List<AccurevServer> _servers;
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

        /**
         * {@inheritDoc}
         *
         * @return String
         */
        @Override
        @Nonnull
        public String getDisplayName() {
            return "AccuRev";
        }

        @SuppressWarnings("unused") // used by stapler
        public boolean showAccurevToolOptions() {
            return Jenkins.getInstance().getDescriptorByType(AccurevTool.DescriptorImpl.class).getInstallations().length > 1;
        }

        /**
         * Lists available tool installations.
         *
         * @return list of available accurev tools
         */
        public List<AccurevTool> getAccurevTools() {
            AccurevTool[] accurevToolInstallations = Jenkins.getInstance().getDescriptorByType(AccurevTool.DescriptorImpl.class).getInstallations();
            return Arrays.asList(accurevToolInstallations);
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

        /**
         * Getter for property 'servers'.
         *
         * @return Value for property 'servers'.
         */
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

        /**
         * Setter for property 'servers'.
         *
         * @param servers Value to set for property 'servers'.
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
         * @param pollOnMaster poll on aster
         */
        public void setPollOnMaster(boolean pollOnMaster) {
            this.pollOnMaster = pollOnMaster;
        }

        @CheckForNull
        public AccurevServer getServer(String uuid) {
            if (uuid == null || this._servers == null) {
                LOGGER.fine("No server found. - getServer(NULL)");
                return null;
            }
            for (AccurevServer server : this._servers) {
                if (UUIDUtils.isValid(uuid) && uuid.equals(server.getUuid())) {
                    return server;
                } else if (uuid.equals(server.getName())) {
                    // support old server name
                    return server;
                }
            }
            LOGGER.fine("No server found.");
            return null;
        }

        @SuppressWarnings("unused") // Used by stapler
        public ListBoxModel doFillAccurevToolItems() {
            ListBoxModel r = new ListBoxModel();
            for (AccurevTool accurev : getAccurevTools()) {
                r.add(accurev.getName());
            }
            return r;
        }

        @SuppressWarnings("unused") // Used by stapler
        public ListBoxModel doFillCredentialsIdItems(@QueryParameter String host, @QueryParameter int port, @QueryParameter String credentialsId) {
            if (!Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER)) {
                return new StandardListBoxModel().includeCurrentValue(credentialsId);
            }
            return new StandardListBoxModel()
                .includeEmptyValue()
                .includeMatchingAs(ACL.SYSTEM,
                    Jenkins.getInstance(),
                    StandardUsernamePasswordCredentials.class,
                    URIRequirementBuilder.fromUri("").withHostnamePort(host, port).build(),
                    CredentialsMatchers.always()
                );
        }

        @Override
        public boolean isApplicable(Job project) {
            return true;
        }
    }

    // -------------------------- INNER CLASSES --------------------------

    // --------------------------- Inner Class ---------------------------------------------------
    public static final class AccurevServer extends AbstractDescribableImpl<AccurevServer> {

        // public static final String DEFAULT_VALID_TRANSACTION_TYPES = "add,chstream,co,defcomp,defunct,keep,mkstream,move,promote,purge,dispatch";
        protected static final List<String> DEFAULT_VALID_STREAM_TRANSACTION_TYPES = Collections
            .unmodifiableList(Arrays.asList("chstream", "defcomp", "mkstream", "promote", "demote_to", "demote_from", "purge"));
        protected static final List<String> DEFAULT_VALID_WORKSPACE_TRANSACTION_TYPES = Collections
            .unmodifiableList(Arrays.asList("add", "chstream", "co", "defcomp", "defunct", "keep",
                "mkstream", "move", "promote", "purge", "dispatch"));
        // keep all transaction types in a set for validation
        private static final String[] VTT_LIST = {"chstream", "defcomp", "mkstream", "promote", "demote_to", "demote_from", "purge"};
        private static final Set<String> VALID_TRANSACTION_TYPES = new HashSet<>(Arrays.asList(VTT_LIST));
        private transient static final String __OBFUSCATE = "OBF:";
        private final String name;
        private final String host;
        transient String username;
        transient String password;
        private int port = 5050;
        private String credentialsId;
        private UUID uuid;
        private String validTransactionTypes;
        private boolean syncOperations;
        private boolean minimiseLogins;
        private boolean useNonexpiringLogin;
        private boolean useRestrictedShowStreams;
        private boolean useColor;
        private boolean usePromoteListen;

        @DataBoundConstructor
        public AccurevServer(//
                             String uuid,
                             String name, //
                             String host) {
            if (StringUtils.isEmpty(uuid)) this.uuid = UUID.randomUUID();
            else this.uuid = UUID.fromString(uuid);
            this.name = name;
            this.host = host;
        }

        /* Used for testing */
        public AccurevServer(String uuid, String name, String host, int port, String username, String password) {
            this.uuid = StringUtils.isEmpty(uuid) ? null : UUID.fromString(uuid);
            this.name = name;
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
        }

        private static String deobfuscate(String s) {
            if (s.startsWith(__OBFUSCATE))
                s = s.substring(__OBFUSCATE.length());
            if (StringUtils.isEmpty(s)) return "";
            byte[] b = new byte[s.length() / 2];
            int l = 0;
            for (int i = 0; i < s.length(); i += 4) {
                String x = s.substring(i, i + 4);
                int i0 = Integer.parseInt(x, 36);
                int i1 = (i0 / 256);
                int i2 = (i0 % 256);
                b[l++] = (byte) ((i1 + i2 - 254) / 2);
            }
            return new String(b, 0, l, StandardCharsets.UTF_8);
        }

        /**
         * When f:repeatable tags are nestable, we can change the advances page
         * of the server config to allow specifying these locations... until
         * then this hack!
         *
         * @return This.
         */
        private Object readResolve() {
            if (uuid == null) {
                uuid = UUID.randomUUID();
            }
            return this;
        }

        /**
         * Getter for property 'uuid'.
         * If value is null generate random UUID
         *
         * @return Value for property 'uuid'.
         */
        public String getUuid() {
            if (uuid == null) {
                uuid = UUID.randomUUID();
            }
            return uuid.toString();
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

        @DataBoundSetter
        public void setPort(int port) {
            this.port = port;
        }

        /**
         * Getter for property 'credentialsId'.
         *
         * @return Value for property 'credentialsId'.
         */
        public String getCredentialsId() {
            return credentialsId;
        }

        @DataBoundSetter
        public void setCredentialsId(String credentialsId) {
            this.credentialsId = credentialsId;
        }

        /**
         * Getter for property 'credentials'.
         *
         * @return Value for property 'credentials'.
         */
        @CheckForNull
        public StandardUsernamePasswordCredentials getCredentials() {
            if (StringUtils.isBlank(credentialsId)) return null;
            else {
                return CredentialsMatchers.firstOrNull(
                    CredentialsProvider
                        .lookupCredentials(StandardUsernamePasswordCredentials.class,
                            Jenkins.getInstance(), ACL.SYSTEM,
                            URIRequirementBuilder.fromUri("").withHostnamePort(host, port).build()),
                    CredentialsMatchers.withId(credentialsId)
                );
            }
        }

        /**
         * Getter for property 'username'.
         *
         * @return Value for property 'username'.
         */
        public String getUsername() {
            StandardUsernamePasswordCredentials credentials = getCredentials();
            return credentials == null ? "jenkins" : credentials.getUsername();
        }

        /**
         * Getter for property 'password'.
         *
         * @return Value for property 'password'.
         */
        public String getPassword() {
            StandardUsernamePasswordCredentials credentials = getCredentials();
            return credentials == null ? "" : Secret.toString(credentials.getPassword());
        }

        /**
         * @return returns the currently set transaction types that are seen as
         * valid for triggering builds and whos authors get notified when a
         * build fails
         */
        public String getValidTransactionTypes() {
            return validTransactionTypes;
        }

        /**
         * @param validTransactionTypes the currently set transaction types that
         *                              are seen as valid for triggering builds and whos authors get notified
         *                              when a build fails
         */
        @DataBoundSetter
        public void setValidTransactionTypes(String validTransactionTypes) {
            this.validTransactionTypes = validTransactionTypes;
        }

        public boolean isSyncOperations() {
            return syncOperations;
        }

        @DataBoundSetter
        public void setSyncOperations(boolean syncOperations) {
            this.syncOperations = syncOperations;
        }

        public boolean isMinimiseLogins() {
            return minimiseLogins;
        }

        @DataBoundSetter
        public void setMinimiseLogins(boolean minimiseLogins) {
            this.minimiseLogins = minimiseLogins;
        }

        public boolean isUseNonexpiringLogin() {
            return useNonexpiringLogin;
        }

        @DataBoundSetter
        public void setUseNonexpiringLogin(boolean useNonexpiringLogin) {
            this.useNonexpiringLogin = useNonexpiringLogin;
        }

        public boolean isUseRestrictedShowStreams() {
            return useRestrictedShowStreams;
        }

        @DataBoundSetter
        public void setUseRestrictedShowStreams(boolean useRestrictedShowStreams) {
            this.useRestrictedShowStreams = useRestrictedShowStreams;
        }

        public boolean isUseColor() {
            return useColor;
        }

        @DataBoundSetter
        public void setUseColor(boolean useColor) {
            this.useColor = useColor;
        }

        public boolean isUsePromoteListen() {
            return usePromoteListen;
        }

        @DataBoundSetter
        public void setUsePromoteListen(boolean usePromoteListen) {
            this.usePromoteListen = usePromoteListen;
        }

        public FormValidation doValidTransactionTypesCheck(@QueryParameter String value)//
        {
            final String[] formValidTypes = value.split(",");
            for (final String formValidType : formValidTypes) {
                if (!VALID_TRANSACTION_TYPES.contains(formValidType)) {
                    return FormValidation.error("Invalid transaction type [" + formValidType + "]. Valid types are: " + Arrays.toString(VTT_LIST));
                }
            }
            return FormValidation.ok();
        }

        public boolean migrateCredentials() {
            if (username != null) {
                LOGGER.info("Migrating to credentials");
                String secret = deobfuscate(password);
                String credentialsId = "";
                List<DomainRequirement> domainRequirements = Util.fixNull(URIRequirementBuilder
                    .fromUri("")
                    .withHostnamePort(host, port)
                    .build());
                List<StandardUsernamePasswordCredentials> credentials = CredentialsMatchers.filter(
                    CredentialsProvider.lookupCredentials(
                        StandardUsernamePasswordCredentials.class,
                        Jenkins.getInstance(), ACL.SYSTEM, domainRequirements),
                    CredentialsMatchers.withUsername(username)
                );
                for (StandardUsernamePasswordCredentials cred : credentials) {
                    if (StringUtils.equals(secret, Secret.toString(cred.getPassword()))) {
                        // If some credentials have the same username/password, use those.
                        credentialsId = cred.getId();
                        this.credentialsId = credentialsId;
                        break;
                    }
                }
                if (StringUtils.isBlank(credentialsId)) {
                    // If we couldn't find any existing credentials,
                    // create new credentials with the principal and secret and use it.
                    StandardUsernamePasswordCredentials newCredentials = new UsernamePasswordCredentialsImpl(
                        CredentialsScope.SYSTEM, null, "Migrated by Accurev Plugin", username, secret);
                    SystemCredentialsProvider.getInstance().getCredentials().add(newCredentials);
                    credentialsId = newCredentials.getId();
                    this.credentialsId = credentialsId;
                }
                if (StringUtils.isNotEmpty(this.credentialsId)) {
                    LOGGER.info("Migrated successfully to credentials");
                    username = null;
                    password = null;
                    return true;
                } else {
                    LOGGER.severe("Migration failed");
                }
            }
            return false;
        }

        @Override
        public DescriptorImpl getDescriptor() {
            return (DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(getClass());
        }

        public String getUrl() {
            return getHost() + ":" + getPort();
        }

        @Extension
        public static class DescriptorImpl extends Descriptor<AccurevServer> {

            @Nonnull
            @Override
            public String getDisplayName() {
                return "AccuRev Server";
            }

        }
    }

    /**
     * Class responsible for parsing change-logs recorded by the builds. If this
     * is renamed or moved it'll break data-compatibility with old builds.
     */
    private static final class AccurevChangeLogParser extends ParseChangeLog {
    }
}
