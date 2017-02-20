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
import hudson.plugins.accurev.cmd.Login;
import hudson.plugins.accurev.cmd.ShowDepots;
import hudson.plugins.accurev.cmd.ShowStreams;
import hudson.plugins.accurev.delegates.AbstractModeDelegate;
import hudson.scm.*;
import hudson.security.ACL;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import jenkins.plugins.accurev.AccurevTool;
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
import java.io.Serializable;
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

    @Extension
    public static final AccurevSCMDescriptor DESCRIPTOR = new AccurevSCMDescriptor();
    static final Date NO_TRANS_DATE = new Date(0);
    private static final Logger LOGGER = Logger.getLogger(AccurevSCM.class.getName());
    private String serverName;
    private String depot;
    private String stream;
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
    @CheckForNull
    private String accurevTool = null;
    private Job<?, ?> activeProject;

// --------------------------- CONSTRUCTORS ---------------------------

    @DataBoundConstructor
    public AccurevSCM(
            String serverUUID
    ) {
        this.serverUUID = serverUUID;
        AccurevServer server = DESCRIPTOR.getServer(serverUUID);
        if (server != null) serverName = server.getName();
        updateMode();
    }

// --------------------- GETTER / SETTER METHODS ---------------------

    public String getDepot() {
        return depot;
    }

    @DataBoundSetter
    public void setDepot(String depot) {
        this.depot = depot;
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

    /**
     * Getter for Accurev server
     *
     * @return AccurevServer based on serverUUID (or serverName if serverUUID is null)
     */
    @CheckForNull
    public AccurevServer getServer() {
        AccurevServer server;
        if (serverUUID == null) {
            if (serverName == null) {
                // No fallback
                LOGGER.severe("AccurevSCM.getServer called but serverName and serverUUID are NULL!");
                return null;
            }
            LOGGER.warning("Getting server by name (" + serverName + "), because UUID is not set.");
            server = DESCRIPTOR.getServer(serverName);
            if (server != null) {
                this.setServerUUID(server.getUUID());
                DESCRIPTOR.save();
            }
        } else {
            server = DESCRIPTOR.getServer(serverUUID);
        }
        return server;
    }

    public String getStream() {
        return stream;
    }

    @DataBoundSetter
    public void setStream(String stream) {
        this.stream = stream;
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

// ------------------------ INTERFACE METHODS ------------------------
// --------------------- Interface Describable ---------------------

    /**
     * {@inheritDoc}
     *
     * @return SCMDescriptor
     */
    @Override
    public SCMDescriptor<?> getDescriptor() {
        return DESCRIPTOR;
    }

// -------------------------- OTHER METHODS --------------------------

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
     * @param baseline      SCMRevisionState
     * @throws java.io.IOException            on failing IO
     * @throws java.lang.InterruptedException on failing interrupt
     */

    public void checkout(@Nonnull Run<?, ?> build, @Nonnull Launcher launcher, @Nonnull FilePath workspace,
                         @Nonnull TaskListener listener, @CheckForNull File changelogFile,
                         @CheckForNull SCMRevisionState baseline) throws IOException, InterruptedException {
//        TODO: Implement SCMRevisionState?
        boolean checkout = AccurevMode.findDelegate(this).checkout(build, launcher, workspace, listener, changelogFile);
        if (checkout) listener.getLogger().println("Checkout done");
        else listener.getLogger().println("Checkout failed");
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
        boolean requiresWorkspace = AccurevMode.findMode(this).isRequiresWorkspace();
        if (DESCRIPTOR.isPollOnMaster() && !requiresWorkspace) {
            // Does not require workspace if Poll On Master is enabled; unless build is using workspace
            return false;
        }

        if (activeProject != null && !activeProject.isBuilding()) {
            // Check if project is no longer active.
            activeProject = null;
        }

        // Return true if activeProject null and it does require a workspace, otherwise false.
        return requiresWorkspace && activeProject == null;

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
//        TODO: Implement SCMRevisionState?
        return SCMRevisionState.NONE;
    }

    @Override
    public PollingResult compareRemoteRevisionWith(@Nonnull Job<?, ?> project, @Nullable Launcher launcher,
                                                   @Nullable FilePath workspace, @Nonnull TaskListener listener,
                                                   @Nonnull SCMRevisionState baseline) throws IOException, InterruptedException {
//        TODO: Implement SCMRevisionState?
        if (activeProject != null && activeProject.isBuilding()) {
            // Skip polling while there is an active project.
            // This will prevent waiting for the workspace to become available.
            return PollingResult.NO_CHANGES;
        }
        activeProject = project;

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
    public static final class AccurevSCMDescriptor extends SCMDescriptor<AccurevSCM> implements ModelObject {

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
        public String getDisplayName() {
            return "AccuRev";
        }

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
            this._servers = req.bindJSONToList(AccurevServer.class, formData.get("server"));
            pollOnMaster = req.getParameter("descriptor.pollOnMaster") != null;
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
                if (UUIDUtils.isValid(uuid) && uuid.equals(server.getUUID())) {
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
        public ListBoxModel doFillServerUUIDItems(@QueryParameter String serverUUID) {
            ListBoxModel s = new ListBoxModel();
            if (this._servers == null) {
                DESCRIPTORLOGGER.warning("Failed to find AccuRev server. Add Server under AccuRev section in the Manage Jenkins > Configure System page.");
                return s;
            }
            for (AccurevServer server : this._servers) {
                s.add(server.getName(), server.getUUID());
            }

            return s;
        }

        @SuppressWarnings("unused") // Used by stapler
        public ListBoxModel doFillDepotItems(@QueryParameter String serverUUID, @QueryParameter String depot) throws IOException, InterruptedException {
            if (StringUtils.isBlank(serverUUID) && !getServers().isEmpty()) serverUUID = getServers().get(0).getUUID();
            final AccurevServer server = getServer(serverUUID);

            if (server == null) {
                return new ListBoxModel();
            }

            List<String> depots = new ArrayList<>();

            // Execute the login command first & upon success of that run show depots
            // command. If any of the command's exitvalue is 1 proper error message is
            // logged
            if (Login.accurevLoginFromGlobalConfig(server)) {
                depots = ShowDepots.getDepots(server, DESCRIPTORLOGGER);
            }

            ListBoxModel d = new ListBoxModel();
            for (String dname : depots) {
                d.add(dname, dname);
            }
            // Below while loop is for to retain the selected item when you open the
            // Job to reconfigure
            d.stream().filter(o -> depot.equals(o.name)).forEach(o -> o.selected = true);
            return d;
        }

        // Populating the streams
        @SuppressWarnings("unused") // Used by stapler
        public ComboBoxModel doFillStreamItems(@QueryParameter String serverUUID, @QueryParameter String depot) throws IOException, InterruptedException {
            if (StringUtils.isBlank(serverUUID) && !getServers().isEmpty()) serverUUID = getServers().get(0).getUUID();
            final AccurevServer server = getServer(serverUUID);
            if (server == null) {
                return new ComboBoxModel();
            }
            ComboBoxModel cbm = new ComboBoxModel();
            if (Login.accurevLoginFromGlobalConfig(server)) {
                if (StringUtils.isBlank(depot)) {
                    depot = Util.fixNull(ShowDepots.getDepots(server, DESCRIPTORLOGGER)).get(0);
                }
                cbm = ShowStreams.getStreamsForGlobalConfig(server, depot, cbm);
            }
            return cbm;
        }

        @SuppressWarnings("unused") // Used by stapler
        public ListBoxModel doFillAccurevToolItems() {
            ListBoxModel r = new ListBoxModel();
            for (AccurevTool accurev : getAccurevTools()) {
                r.add(accurev.getName());
            }
            return r;
        }

        @SuppressWarnings("unused")
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
    }

    // --------------------------- Inner Class ---------------------------------------------------
    public static final class AccurevServer implements Serializable {

        // public static final String DEFAULT_VALID_TRANSACTION_TYPES = "add,chstream,co,defcomp,defunct,keep,mkstream,move,promote,purge,dispatch";
        protected static final List<String> DEFAULT_VALID_STREAM_TRANSACTION_TYPES = Collections
                .unmodifiableList(Arrays.asList("chstream", "defcomp", "mkstream", "promote"));
        protected static final List<String> DEFAULT_VALID_WORKSPACE_TRANSACTION_TYPES = Collections
                .unmodifiableList(Arrays.asList("add", "chstream", "co", "defcomp", "defunct", "keep",
                        "mkstream", "move", "promote", "purge", "dispatch"));
        private static final long serialVersionUID = 3270850408409304611L;
        // keep all transaction types in a set for validation
        private static final String[] VTT_LIST = {"chstream", "defcomp", "mkstream", "promote"};
        private static final Set<String> VALID_TRANSACTION_TYPES = new HashSet<>(Arrays.asList(VTT_LIST));
        private transient static final String __OBFUSCATE = "OBF:";
        private final String name;
        private final String host;
        private final int port;
        transient String username;
        transient String password;
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
                             String host, //
                             int port, //
                             String credentialsId, //
                             String validTransactionTypes, //
                             boolean syncOperations, //
                             boolean minimiseLogins, //
                             boolean useNonexpiringLogin, //
                             boolean useRestrictedShowStreams,
                             boolean useColor,
                             boolean usePromoteListen) {
            if (StringUtils.isEmpty(uuid)) this.uuid = UUID.randomUUID();
            else this.uuid = UUID.fromString(uuid);
            this.name = name;
            this.host = host;
            this.port = port;
            this.credentialsId = credentialsId;
            this.validTransactionTypes = validTransactionTypes;
            this.syncOperations = syncOperations;
            this.minimiseLogins = minimiseLogins;
            this.useNonexpiringLogin = useNonexpiringLogin;
            this.useRestrictedShowStreams = useRestrictedShowStreams;
            this.useColor = useColor;
            this.usePromoteListen = usePromoteListen;
            AccurevPromoteTrigger.validateListeners();
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
        public String getUUID() {
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

        /**
         * Getter for property 'credentialsId'.
         *
         * @return Value for property 'credentialsId'.
         */
        public String getCredentialsId() {
            return credentialsId;
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

        public boolean isUseColor() {
            return useColor;
        }

        public void setUseColor(boolean useColor) {
            this.useColor = useColor;
        }

        public boolean isUsePromoteListen() {
            return usePromoteListen;
        }

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
    }

    // -------------------------- INNER CLASSES --------------------------

    /**
     * Class responsible for parsing change-logs recorded by the builds. If this
     * is renamed or moved it'll break data-compatibility with old builds.
     */
    private static final class AccurevChangeLogParser extends ParseChangeLog {
    }
}
