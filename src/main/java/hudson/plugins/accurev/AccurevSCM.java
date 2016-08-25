package hudson.plugins.accurev;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.*;
import hudson.plugins.accurev.cmd.JustAccurev;
import hudson.plugins.accurev.cmd.Login;
import hudson.plugins.accurev.cmd.ShowDepots;
import hudson.plugins.accurev.cmd.ShowStreams;
import hudson.plugins.accurev.delegates.AbstractModeDelegate;
import hudson.plugins.jetty.security.Password;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.scm.SCM;
import hudson.util.ComboBoxModel;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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

    @Extension
    public static final AccurevSCMDescriptor DESCRIPTOR = new AccurevSCMDescriptor();
    private static final Logger logger = Logger.getLogger(AccurevSCM.class.getName());
    static final Date NO_TRANS_DATE = new Date(0);
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
     *
     * @param serverName name for the server
     * @param depot depot
     * @param stream stream
     * @param wspaceORreftree workspace or reftree
     * @param workspace workspace
     * @param reftree reftree
     * @param subPath subPath
     * @param filterForPollSCM filterForPollSCM
     * @param synctime synctime
     * @param cleanreftree cleanreftree
     * @param useSnapshot useSnapshot
     * @param dontPopContent Do not populate content
     * @param snapshotNameFormat snapshot name format
     * @param directoryOffset directory offset
     * @param ignoreStreamParent ignore Parent Stream
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
     *
     * </ul>
     *
     * @param build build
     * @param env enviroments
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
     * @param build            build
     * @param launcher         launcher
     * @param workspace        jenkins workspace
     * @param listener         listener
     * @param changelogFile    change log file
     * @param baseline SCMRevisionState
     * @throws java.io.IOException            on failing IO
     * @throws java.lang.InterruptedException on failing interrupt
     */

    public void checkout(@Nonnull Run<?,?> build, @Nonnull Launcher launcher, @Nonnull FilePath workspace,
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
        final boolean needSlaveForPolling = !DESCRIPTOR.isPollOnMaster();
        AccurevMode accurevMode  = AccurevMode.findMode(this);
        return accurevMode.isRequiresWorkspace() || needSlaveForPolling;
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
    public SCMRevisionState calcRevisionsFromBuild(@Nonnull Run<?,?> build, @Nullable FilePath workspace,
                                                   @Nullable Launcher launcher, @Nonnull TaskListener listener) throws IOException, InterruptedException {
//        TODO: Implement SCMRevisionState?
        return SCMRevisionState.NONE;
    }

    @Override
    public PollingResult compareRemoteRevisionWith(@Nonnull Job<?,?> project, @Nullable Launcher launcher,
                                                   @Nullable FilePath workspace, @Nonnull TaskListener listener,
                                                   @Nonnull SCMRevisionState baseline) throws IOException, InterruptedException {
//        TODO: Implement SCMRevisionState?
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
            super(AccurevSCM.class, null);
            load();
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
         * @param req      request
         * @param formData json object
         * @return SCM
         * @throws hudson.model.Descriptor.FormException if form data is incorrect/incomplete
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
            if (this._servers == null) {
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
                    logger.log(Level.INFO, "The ACCUREV_BIN environment variable was set to: {0}", accurevBinDir);
                    return accurevBinDir;
                } else {
                    try {
                        throw new FileNotFoundException(
                                "The ACCUREV_BIN environment variable was set but the path it was set to does not exist OR it is not a directory. Please correct the path or unset the variable. ACCUREV_BIN was set to: "
                                + accurevBinDir);
                    } catch (FileNotFoundException e) {
                        logger.log(Level.SEVERE, "getEnvBinDir", e);
                    }
                }
            }

            if (System.getProperty("accurev.bin") != null) {
                accurevBinDir = System.getProperty("accurev.bin");
                if (new File(accurevBinDir).exists() && new File(accurevBinDir).isDirectory()) {
                    logger.log(Level.INFO, "The accurev.bin system property was set to: {0}", accurevBinDir);
                    return accurevBinDir;
                } else {
                    try {
                        throw new FileNotFoundException(
                                "The accurev.bin system property was set but the path it was set to does not exist OR it is not a directory. Please correct the path or unset the property. 'accurev.bin' was set to: "
                                + accurevBinDir);
                    } catch (FileNotFoundException e) {
                        logger.log(Level.SEVERE, "getEnvBinDir", e);
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

            ListBoxModel d;
            List<String> depots = new ArrayList<>();

            // Execute the login command first & upon success of that run show depots
            // command. If any of the command's exitvalue is 1 proper error message is
            // logged
            try {
                if (Login.accurevLoginfromGlobalConfig(server, accurevPath, descriptorlogger)) {
                    depots = ShowDepots.getDepots(server, accurevPath, descriptorlogger);
                }
            } catch (IOException | InterruptedException e) {
                logger.warning(e.getMessage());
            }

            d = new ListBoxModel();
            for (String dname : depots) {
                d.add(dname, dname);
            }
            // Below while loop is for to retain the selected item when you open the
            // Job to reconfigure
            d.stream().filter(o -> depot.equals(o.name)).forEachOrdered(o -> o.selected = true);
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

            } catch (IOException | InterruptedException e) {
                logger.warning(e.getMessage());
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
        private static final long serialVersionUID = 3270850408409304611L;

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
        private static final Set<String> VALID_TRANSACTION_TYPES = new HashSet<>(Arrays.asList(VTT_LIST
                .split(VTT_DELIM)));
        // public static final String DEFAULT_VALID_TRANSACTION_TYPES = "add,chstream,co,defcomp,defunct,keep,mkstream,move,promote,purge,dispatch";
        public static final String DEFAULT_VALID_STREAM_TRANSACTION_TYPES = "chstream,defcomp,mkstream,promote";
        public static final String DEFAULT_VALID_WORKSPACE_TRANSACTION_TYPES = "add,chstream,co,defcomp,defunct,keep,mkstream,move,promote,purge,dispatch";

        /**
         * Constructs a new AccurevServer.
         */
        public AccurevServer() {
            this.winCmdLocations = new ArrayList<>(DEFAULT_WIN_CMD_LOCATIONS);
            this.nixCmdLocations = new ArrayList<>(DEFAULT_NIX_CMD_LOCATIONS);
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
         * When f:repeatable tags are nestable, we can change the advances page
         * of the server config to allow specifying these locations... until
         * then this hack!
         *
         * @return This.
         */
        private Object readResolve() {
            if (winCmdLocations == null) {
                winCmdLocations = new ArrayList<>(DEFAULT_WIN_CMD_LOCATIONS);
            }
            if (nixCmdLocations == null) {
                nixCmdLocations = new ArrayList<>(DEFAULT_NIX_CMD_LOCATIONS);
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
         * valid for triggering builds and whos authors get notified when a
         * build fails
         */
        public String getValidTransactionTypes() {
            return validTransactionTypes;
        }

        /**
         *
         * @param validTransactionTypes the currently set transaction types that
         * are seen as valid for triggering builds and whos authors get notified
         * when a build fails
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
     * Class responsible for parsing change-logs recorded by the builds. If this
     * is renamed or moved it'll break data-compatibility with old builds.
     */
    private static final class AccurevChangeLogParser extends ParseChangeLog {
    }
}
