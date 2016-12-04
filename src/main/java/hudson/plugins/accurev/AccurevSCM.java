package hudson.plugins.accurev;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.plugins.accurev.cmd.Login;
import hudson.plugins.accurev.cmd.ShowDepots;
import hudson.plugins.accurev.cmd.ShowStreams;
import hudson.plugins.accurev.delegates.AbstractModeDelegate;
import hudson.plugins.jetty.security.Password;
import hudson.scm.*;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
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
    private static final Logger logger = Logger.getLogger(AccurevSCM.class.getName());
    private final String serverName;
    private final String depot;
    private final String stream;
    private final boolean ignoreStreamParent;
    private final String wspaceORreftree;
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
    private String serverUUID;
    private boolean useReftree;
    private boolean useWorkspace;
    private boolean noWspaceNoReftree;
    private Job<?, ?> activeProject;

// --------------------------- CONSTRUCTORS ---------------------------

    /**
     * Our constructor.
     *
     * @param serverUUID         Unique identifier for server
     * @param serverName         name for the server
     * @param depot              depot
     * @param stream             stream
     * @param wspaceORreftree    workspace or reftree
     * @param workspace          workspace
     * @param reftree            reftree
     * @param subPath            subPath
     * @param filterForPollSCM   filterForPollSCM
     * @param synctime           synctime
     * @param cleanreftree       cleanreftree
     * @param useSnapshot        useSnapshot
     * @param dontPopContent     Do not populate content
     * @param snapshotNameFormat snapshot name format
     * @param directoryOffset    directory offset
     * @param ignoreStreamParent ignore Parent Stream
     */
    @DataBoundConstructor
    public AccurevSCM(
            String serverUUID,
            String serverName,
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
        this.serverUUID = serverUUID;
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
        AccurevMode accurevMode = AccurevMode.findMode(this);
        useReftree = accurevMode.isReftree();
        useWorkspace = accurevMode.isWorkspace();
        noWspaceNoReftree = accurevMode.isNoWorkspaceOrRefTree();
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
     * Getter for property 'serverUUID'.
     *
     * @return Value for property 'serverUUID'.
     */
    public String getServerUUID() {
        return serverUUID;
    }

    /**
     * Setter for property 'serverUUID'.
     *
     * @param uuid Value for property 'serverUUID'
     */
    public void setServerUUID(String uuid) {
        serverUUID = uuid;
    }

    /**
     * Getter for Accurev server
     *
     * @return AccurevServer based on serverUUID (or serverName if serverUUID is null)
     */
    public AccurevServer getServer() {
        AccurevServer server;
        if (serverUUID == null) {
            if (serverName == null) {
                // No fallback
                logger.severe("AccurevSCM.getServer called but serverName and serverUUID are NULL!");
                return null;
            }
            logger.warning("Getting server by name (" + serverName + "), because UUID is not set.");
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
        AccurevMode.findDelegate(this).checkout(build, launcher, workspace, listener, changelogFile);
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

        if (requiresWorkspace && activeProject == null) {
            return true;
        }

        return false;
    }

    /**
     * Gets the lock to be used on "normal" accurev commands, or
     * <code>null</code> if command synchronization is switched off.
     *
     * @return See above.
     */
    public Lock getOptionalLock() {
        final AccurevServer server = getServer();
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

    //--------------------------- Inner Class - DescriptorImplementation ----------------------------
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
         * {@inheritDoc}
         *
         * @param req      request is non-null but annotated as CheckForNull due to compatibility
         * @param formData json object
         * @return SCM
         * @throws hudson.model.Descriptor.FormException if form data is incorrect/incomplete
         * @see <a href="http://javadoc.jenkins-ci.org/hudson/model/Descriptor.html#newInstance(org.kohsuke.stapler.StaplerRequest)">newInstance</a>
         */
        @SuppressFBWarnings("NP_PARAMETER_MUST_BE_NONNULL_BUT_MARKED_AS_NULLABLE")
        @Override
        public SCM newInstance(@CheckForNull StaplerRequest req, @Nonnull JSONObject formData) throws FormException {
            String serverUUID = req.getParameter("_.serverUUID");
            String serverName;
            AccurevServer server = getServer(serverUUID);
            if (null == server) {
                throw new FormException("No server selected. Please add/select a server", "_.serverUUID");
            } else {
                serverName = server.getName();
            }
            return new AccurevSCM( //
                    serverUUID, //
                    serverName, //
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
            if (uuid == null) {
                return null;
            }
            if (this._servers != null) {
              for (AccurevServer server : this._servers) {
                  if (UUIDUtils.isValid(uuid) && uuid.equals(server.getUUID())) {
                      return server;
                  } else if (uuid.equals(server.getName())) {
                      // support old server name
                      return server;
                  }
              }
            }
            return null;
        }

        // This method will populate the servers in the select box
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

        // This method will populate the depots in the select box depending upon the
        // server selected.
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
        public ComboBoxModel doFillStreamItems(@QueryParameter String serverUUID, @QueryParameter String depot) throws IOException, InterruptedException {
            if (StringUtils.isBlank(serverUUID) && !getServers().isEmpty()) serverUUID = getServers().get(0).getUUID();
            final AccurevServer server = getServer(serverUUID);

            if (server == null || StringUtils.isBlank(depot)) {
                //DESCRIPTORLOGGER.warning("Failed to find server.");
                return new ComboBoxModel();
            }
            // Execute the login command first & upon success of that run show streams
            // command. If any of the command's exitvalue is 1 proper error message is
            // logged
            ComboBoxModel cbm = new ComboBoxModel();
            if (Login.accurevLoginFromGlobalConfig(server)) {
                cbm = ShowStreams.getStreamsForGlobalConfig(server, depot, cbm);
            }
            return cbm;
        }
    }

    // --------------------------- Inner Class ---------------------------------------------------
    public static final class AccurevServer implements Serializable {

        public static final String VTT_DELIM = ",";
        // public static final String DEFAULT_VALID_TRANSACTION_TYPES = "add,chstream,co,defcomp,defunct,keep,mkstream,move,promote,purge,dispatch";
        public static final String DEFAULT_VALID_STREAM_TRANSACTION_TYPES = "chstream,defcomp,mkstream,promote";
        public static final String DEFAULT_VALID_WORKSPACE_TRANSACTION_TYPES = "add,chstream,co,defcomp,defunct,keep,mkstream,move,promote,purge,dispatch";
        private static final long serialVersionUID = 3270850408409304611L;
        // keep all transaction types in a set for validation
        private static final String VTT_LIST = "chstream,defcomp,mkstream,promote";
        private static final Set<String> VALID_TRANSACTION_TYPES = new HashSet<>(Arrays.asList(VTT_LIST
                .split(VTT_DELIM)));
        private UUID uuid;
        private String name;
        private String host;
        private int port;
        private String username;
        private String password;
        private String validTransactionTypes;
        private boolean syncOperations;
        private boolean minimiseLogins;
        private boolean useNonexpiringLogin;
        private boolean useRestrictedShowStreams;
        private boolean useColor;

        @DataBoundConstructor
        public AccurevServer(//
                             String uuid,
                             String name, //
                             String host, //
                             int port, //
                             String username, //
                             String password, //
                             String validTransactionTypes, //
                             boolean syncOperations, //
                             boolean minimiseLogins, //
                             boolean useNonexpiringLogin, //
                             boolean useRestrictedShowStreams,
                             boolean useColor) {
            if (StringUtils.isEmpty(uuid)) this.uuid = UUID.randomUUID();
            else this.uuid = UUID.fromString(uuid);
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
            this.useColor = useColor;
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
     * Class responsible for parsing change-logs recorded by the builds. If this
     * is renamed or moved it'll break data-compatibility with old builds.
     */
    private static final class AccurevChangeLogParser extends ParseChangeLog {
    }
}
