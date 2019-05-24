package hudson.plugins.accurev;

import static hudson.Util.fixEmpty;
import static hudson.Util.fixNull;
import static jenkins.plugins.accurev.util.AccurevUtils.workspaceToNode;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Job;
import hudson.model.ModelObject;
import hudson.model.Node;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Run;
import hudson.model.StringParameterValue;
import hudson.model.TaskListener;
import hudson.plugins.accurev.cmd.Login;
import hudson.plugins.accurev.delegates.AbstractModeDelegate;
import hudson.scm.ChangeLogParser;
import hudson.scm.PollingResult;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.scm.SCMRevisionState;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.plugins.accurev.AccurevTool;
import jenkins.plugins.accurev.util.UUIDUtils;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

/** Accurev SCM plugin for Jenkins */
public class AccurevSCM extends SCM {
  protected static final List<String> DEFAULT_VALID_STREAM_TRANSACTION_TYPES =
      Collections.unmodifiableList(
          Arrays.asList(
              "chstream", "defcomp", "mkstream", "promote", "demote_to", "demote_from", "purge"));
  protected static final List<String> DEFAULT_VALID_WORKSPACE_TRANSACTION_TYPES =
      Collections.unmodifiableList(
          Arrays.asList(
              "add",
              "chstream",
              "co",
              "defcomp",
              "defunct",
              "keep",
              "mkstream",
              "move",
              "promote",
              "purge",
              "dispatch"));

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
  private boolean subPathOnly;
  private String filterForPollSCM;
  private String directoryOffset;
  private boolean useReftree;
  private boolean useWorkspace;
  private boolean noWspaceNoReftree;
  private String serverUUID;
  @CheckForNull private String accurevTool = null;
  private Job<?, ?> activeProject;

  @Deprecated
  public AccurevSCM(String serverName, String depot, String stream) {
    this(serverName, null, depot, stream);
  }

  @Deprecated
  public AccurevSCM(String serverName, String serverUUID, String depot, String stream) {
    this(serverName, serverUUID, depot, stream, "none");
  }

  @DataBoundConstructor
  public AccurevSCM(
      String serverName, String serverUUID, String depot, String stream, String wspaceORreftree) {
    this.depot = depot;
    this.stream = stream;
    this.serverName = serverName;
    this.serverUUID = serverUUID;
    this.wspaceORreftree = wspaceORreftree;
    updateMode();
  }

  public static AccurevSCMDescriptor configuration() {
    return Jenkins.get().getDescriptorByType(AccurevSCMDescriptor.class);
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

  @DataBoundSetter
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
    this.reftree = fixEmpty(reftree);
  }

  public String getWorkspace() {
    return workspace;
  }

  @DataBoundSetter
  public void setWorkspace(String workspace) {
    this.workspace = fixEmpty(workspace);
  }

  @CheckForNull
  public String getAccurevTool() {
    return accurevTool;
  }

  @DataBoundSetter
  public void setAccurevTool(String accurevTool) {
    this.accurevTool = fixEmpty(accurevTool);
  }

  public String getSubPath() {
    return subPath;
  }

  @DataBoundSetter
  public void setSubPath(String subPath) {
    this.subPath = fixEmpty(subPath);
  }

  public boolean getSubPathOnly() {
    return subPathOnly;
  }

  @DataBoundSetter
  public void setSubPathOnly(boolean subPathOnly) {
    this.subPathOnly = subPathOnly;
  }

  public String getFilterForPollSCM() {
    return filterForPollSCM;
  }

  @DataBoundSetter
  public void setFilterForPollSCM(String filterForPollSCM) {
    this.filterForPollSCM = fixEmpty(filterForPollSCM);
  }

  public String getSnapshotNameFormat() {
    return snapshotNameFormat;
  }

  @DataBoundSetter
  public void setSnapshotNameFormat(String snapshotNameFormat) {
    this.snapshotNameFormat = fixEmpty(snapshotNameFormat);
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
    this.directoryOffset = fixEmpty(directoryOffset);
  }

  private void updateMode() {
    AccurevMode accurevMode = AccurevMode.findMode(this);
    useReftree = accurevMode.isReftree();
    useWorkspace = accurevMode.isWorkspace();
    noWspaceNoReftree = accurevMode.isNoWorkspaceOrRefTree();
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
   * Exposes AccuRev-specific information to the environment. The following variables become
   * available, if not null:
   *
   * <ul>
   *   <li>ACCUREV_DEPOT - The depot name
   *   <li>ACCUREV_STREAM - The stream name
   *   <li>ACCUREV_SERVER - The server name
   *   <li>ACCUREV_REFTREE - The workspace name
   *   <li>ACCUREV_SUBPATH - The workspace subpath
   * </ul>
   *
   * @param build build
   * @param env enviroments
   * @since 0.6.9
   */
  @Override
  public void buildEnvironment(Run<?, ?> build, Map<String, String> env) {
    AbstractModeDelegate delegate = AccurevMode.findDelegate(this);
    delegate.buildEnvVars(build, env);
  }

  /**
   * {@inheritDoc}
   *
   * @param build build
   * @param launcher launcher
   * @param workspace jenkins workspace
   * @param listener listener
   * @param changelogFile change log file
   * @param baseline SCMRevisionState
   * @throws java.io.IOException on failing IO
   * @throws java.lang.InterruptedException on failing interrupt
   */
  public void checkout(
      @NonNull Run<?, ?> build,
      @NonNull Launcher launcher,
      @NonNull FilePath workspace,
      @NonNull TaskListener listener,
      @CheckForNull File changelogFile,
      @CheckForNull SCMRevisionState baseline)
      throws IOException, InterruptedException {
    //        TODO: Implement SCMRevisionState?
    boolean checkout =
        AccurevMode.findDelegate(this)
            .checkout(build, launcher, workspace, listener, changelogFile);
    if (checkout) {
      listener.getLogger().println("Checkout done");
    } else {
      listener.getLogger().println("Checkout failed");
    }
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
    if (getDescriptor().isPollOnMaster() && !requiresWorkspace) {
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
   * Gets the lock to be used on "normal" accurev commands, or <code>null</code> if command
   * synchronization is switched off.
   *
   * @param workspace The workspace the command will run in
   * @return See above.
   */
  public synchronized ReentrantLock getOptionalLock(FilePath workspace) {
    final AccurevServer server = getServer();
    final boolean shouldLock = server != null && server.isSyncOperations();
    if (shouldLock) {
      return getMandatoryLock(workspace);
    } else {
      return null;
    }
  }

  /**
   * The accurev server has been known to crash if more than one copy of the accurev has been run
   * concurrently on the local machine. <br>
   * Also, the accurev client has been known to complain that it's not logged in if another client
   * on the same machine logs in again.
   */
  public static final ReentrantLock MASTER_LOCK = new ReentrantLock(true);

  private static final Map<String, ReentrantLock> nodeLockMap =
      new HashMap<String, ReentrantLock>();

  /**
   * Gets the lock to be used on accurev commands where synchronization is mandatory.
   *
   * @param workspace The workspace the command will run in
   * @return See above.
   */
  public synchronized ReentrantLock getMandatoryLock(FilePath workspace) {
    Node node = workspaceToNode(workspace);
    String nodeName = (node != null) ? node.getNodeName() : "";

    if (nodeName.isEmpty()) {
      return MASTER_LOCK;
    } else {
      ReentrantLock lock = nodeLockMap.get(nodeName);

      if (lock == null) {
        nodeLockMap.put(nodeName, lock = new ReentrantLock(true));
      }

      return lock;
    }
  }

  @Override
  public SCMRevisionState calcRevisionsFromBuild(
      @NonNull Run<?, ?> build,
      @Nullable FilePath workspace,
      @Nullable Launcher launcher,
      @NonNull TaskListener listener)
      throws IOException, InterruptedException {
    //        TODO: Implement SCMRevisionState?
    return SCMRevisionState.NONE;
  }

  @Override
  public PollingResult compareRemoteRevisionWith(
      @NonNull Job<?, ?> project,
      @Nullable Launcher launcher,
      @Nullable FilePath workspace,
      @NonNull TaskListener listener,
      @NonNull SCMRevisionState baseline)
      throws IOException, InterruptedException {
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
   * @param project Running build
   * @param listener Listener that it runs the build on
   * @return Stream name tries to expand Variable reference to a String
   * @throws IllegalArgumentException Thrown when Variable reference is not supported or cannot
   *     expand
   */
  public String getPollingStream(Job<?, ?> project, TaskListener listener)
      throws IllegalArgumentException {
    String parsedLocalStream;
    if (hasStringVariableReference(stream)) {
      ParametersDefinitionProperty paramDefProp =
          project.getProperty(ParametersDefinitionProperty.class);

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
          Object v = strdefvalue.getValue();

          keyValues.put(defaultValue.getName(), (v != null) ? v.toString() : "");
        }
      }

      final EnvVars environment = new EnvVars(keyValues);
      parsedLocalStream = environment.expand(getStream());
      listener.getLogger().println("... expanded '" + stream + "' to '" + parsedLocalStream + "'.");
    } else {
      parsedLocalStream = stream;
    }

    if (hasStringVariableReference(parsedLocalStream)) {
      throw new IllegalArgumentException("Failed to expand variable reference '" + stream + "'.");
    }
    return parsedLocalStream;
  }

  @Extension
  @Symbol("accurev")
  public static class AccurevSCMDescriptor extends SCMDescriptor<AccurevSCM>
      implements ModelObject {

    private static final Logger DESCRIPTORLOGGER =
        Logger.getLogger(AccurevSCMDescriptor.class.getName());
    private List<AccurevServer> _servers;
    // The servers field is here for backwards compatibility.
    // The transient modifier means it won't be written to the config file
    private transient List<AccurevServer> servers;
    private boolean pollOnMaster;

    /** Constructs a new AccurevSCMDescriptor. */
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
    @NonNull
    public String getDisplayName() {
      return "AccuRev";
    }

    @SuppressWarnings("unused") // used by stapler
    public boolean showAccurevToolOptions() {
      return Jenkins.get()
              .getDescriptorByType(AccurevTool.DescriptorImpl.class)
              .getInstallations()
              .length
          > 1;
    }

    /**
     * Lists available tool installations.
     *
     * @return list of available accurev tools
     */
    public List<AccurevTool> getAccurevTools() {
      AccurevTool[] accurevToolInstallations =
          Jenkins.get().getDescriptorByType(AccurevTool.DescriptorImpl.class).getInstallations();
      return Arrays.asList(accurevToolInstallations);
    }

    /**
     * {@inheritDoc}
     *
     * @param req request
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
    @NonNull
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
     * @param pollOnMaster poll on master
     */
    @DataBoundSetter
    public void setPollOnMaster(boolean pollOnMaster) {
      this.pollOnMaster = pollOnMaster;
    }

    @CheckForNull
    public AccurevServer getServer(String uuid) {
      if (uuid == null || getServers().isEmpty()) {
        LOGGER.fine("No server found. - getServer(NULL)");
        return null;
      }
      for (AccurevServer server : getServers()) {
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
    public ListBoxModel doFillServerNameItems() {
      ListBoxModel s = new ListBoxModel();
      if (getServers().isEmpty()) {
        DESCRIPTORLOGGER.warning(
            "Failed to find AccuRev server. Add Server under AccuRev section in the Manage Jenkins > Configure System page.");
        return s;
      }
      for (AccurevServer server : getServers()) {
        s.add(server.getName());
      }

      return s;
    }

    @SuppressWarnings("unused") // Used by stapler
    public ListBoxModel doFillAccurevToolItems() {
      ListBoxModel r = new ListBoxModel();
      for (AccurevTool accurev : getAccurevTools()) {
        r.add(accurev.getName());
      }
      return r;
    }

    @Override
    public boolean isApplicable(Job project) {
      return true;
    }

    public FormValidation doCheckServerName(@QueryParameter String value) throws IOException {
      if (StringUtils.isBlank(value) && !getServers().isEmpty()) {
        value = getServers().get(0).getUuid();
      }
      if (null != value) {
        AccurevServer server = getServer(value);
        if (null != server && server.isServerDisabled()) {
          return FormValidation.error("This server is disabled");
        }
      }
      return FormValidation.ok();
    }
  }

  public static final class AccurevServer extends AbstractDescribableImpl<AccurevServer> {

    private static final transient String __OBFUSCATE = "OBF:";
    private final String name;
    private final String host;
    @Deprecated transient String username;
    @Deprecated transient String password;
    private int port = 5050;
    private String credentialsId;
    private UUID uuid;
    private boolean syncOperations;
    private boolean minimiseLogins;
    private boolean useNonexpiringLogin;
    private boolean useRestrictedShowStreams;
    private boolean useColor;
    private boolean usePromoteListen;
    private boolean serverDisabled;

    @DataBoundConstructor
    public AccurevServer( //
        String uuid,
        String name, //
        String host) {
      if (StringUtils.isEmpty(uuid)) {
        this.uuid = UUID.randomUUID();
      } else {
        this.uuid = UUID.fromString(uuid);
      }
      this.name = name;
      this.host = host;
    }

    private static String deobfuscate(String s) {
      if (s.startsWith(__OBFUSCATE)) {
        s = s.substring(__OBFUSCATE.length());
      }
      if (StringUtils.isEmpty(s)) {
        return "";
      }
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
     * When f:repeatable tags are nestable, we can change the advances page of the server config to
     * allow specifying these locations... until then this hack!
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
     * Getter for property 'uuid'. If value is null generate random UUID
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
      if (StringUtils.isBlank(credentialsId)) {
        return null;
      } else {
        return CredentialsMatchers.firstOrNull(
            CredentialsProvider.lookupCredentials(
                StandardUsernamePasswordCredentials.class,
                Jenkins.get(),
                ACL.SYSTEM,
                URIRequirementBuilder.fromUri("").withHostnamePort(host, port).build()),
            CredentialsMatchers.withId(credentialsId));
      }
    }

    public String getUsername() {
      StandardUsernamePasswordCredentials credentials = getCredentials();
      return credentials == null ? "jenkins" : credentials.getUsername();
    }

    @Deprecated
    public void setUsername(String username) {
      this.username = username;
    }

    public String getPassword() {
      StandardUsernamePasswordCredentials credentials = getCredentials();
      return credentials == null ? "" : Secret.toString(credentials.getPassword());
    }

    @Deprecated
    public void setPassword(String password) {
      this.password = password;
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

    public boolean isServerDisabled() {
      return serverDisabled;
    }

    @DataBoundSetter
    public void setServerDisabled(boolean serverDisabled) {
      this.serverDisabled = serverDisabled;
    }

    public boolean migrateCredentials() {
      if (username != null) {
        LOGGER.info("Migrating to credentials");
        String secret = deobfuscate(password);
        String credentialsId = "";
        List<DomainRequirement> domainRequirements =
            fixNull(URIRequirementBuilder.fromUri("").withHostnamePort(host, port).build());
        List<StandardUsernamePasswordCredentials> credentials =
            CredentialsMatchers.filter(
                CredentialsProvider.lookupCredentials(
                    StandardUsernamePasswordCredentials.class,
                    Jenkins.get(),
                    ACL.SYSTEM,
                    domainRequirements),
                CredentialsMatchers.withUsername(username));
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
          StandardUsernamePasswordCredentials newCredentials =
              new UsernamePasswordCredentialsImpl(
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
      return (DescriptorImpl) Jenkins.get().getDescriptorOrDie(getClass());
    }

    @Extension
    @Symbol("accurevServer")
    public static class DescriptorImpl extends Descriptor<AccurevServer> {

      public DescriptorImpl() {
        load();
      }

      @NonNull
      @Override
      public String getDisplayName() {
        return "AccuRev Server";
      }

      @SuppressWarnings("unused")
      public ListBoxModel doFillCredentialsIdItems(
          @QueryParameter String host,
          @QueryParameter int port,
          @QueryParameter String credentialsId) {
        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
          return new StandardListBoxModel().includeCurrentValue(credentialsId);
        }
        return new StandardListBoxModel()
            .includeEmptyValue()
            .includeMatchingAs(
                ACL.SYSTEM,
                Jenkins.get(),
                StandardUsernamePasswordCredentials.class,
                URIRequirementBuilder.fromUri("").withHostnamePort(host, port).build(),
                CredentialsMatchers.always());
      }

      @SuppressWarnings("unused")
      @RequirePOST
      public FormValidation doTest(
          @QueryParameter String name,
          @QueryParameter String host,
          @QueryParameter int port,
          @QueryParameter String credentialsId) {
        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
          return FormValidation.ok();
        }
        if (null == host || host.isEmpty()) {
          return FormValidation.warning("No Host Provided");
        }

        AccurevServer server = new AccurevServer("", name, host);
        server.setPort(port);
        server.setCredentialsId(credentialsId);
        try {
          if (Login.accurevLoginFromGlobalConfig(server)) {
            return FormValidation.ok("SUCCESS");
          }
          return FormValidation.error("FAILURE");
        } catch (Exception e) {
          return FormValidation.error("FAILURE : " + e.getMessage());
        }
      }
    }
  }

  /**
   * Class responsible for parsing change-logs recorded by the builds. If this is renamed or moved
   * it'll break data-compatibility with old builds.
   */
  private static final class AccurevChangeLogParser extends ParseChangeLog {}
}
