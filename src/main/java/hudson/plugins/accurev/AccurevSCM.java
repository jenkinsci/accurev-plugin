package hudson.plugins.accurev;

import static hudson.Util.fixEmpty;
import static hudson.Util.fixEmptyAndTrim;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;

import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.Job;
import hudson.model.ModelObject;
import hudson.model.Node;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Project;
import hudson.model.Run;
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

    static final Date NO_TRANS_DATE = new Date(0);
    private static final Logger LOGGER = Logger.getLogger(AccurevSCM.class.getName());
    private String url;
    private String depot;
    private String stream;
    private String credentialsId;
    private DescribableList<AccurevSCMExtension, AccurevSCMExtensionDescriptor> extensions;

    @CheckForNull
    private String accurevTool = null;

    @DataBoundConstructor
    public AccurevSCM(
        String url, String depot, String stream, String credentialsId
    ) {
        this.url = url;
        this.depot = depot;
        this.stream = stream;
        this.credentialsId = credentialsId;
    }

    public AccurevSCM(AccurevServer server, String depot, String stream) {
        super(server);
        this.depot = depot;
        this.stream = stream;
    }

    public static AccurevSCMDescriptor configuration() {
        return Jenkins.getInstance().getDescriptorByType(AccurevSCMDescriptor.class);
    }

    public String getUrl() {
        return url;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getDepot() {
        return depot;
    }

    @Nonnull
    @Override
    public String getKey() {
        StringBuilder b = new StringBuilder("accurev");
        // TODO should handle multiple repos
        b.append(' ').append(getUrl());
        return b.toString();
    }

    public String getStream() {
        return stream;
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
     * @throws java.io.IOException            on failing IO
     * @throws java.lang.InterruptedException on failing interrupt
     */

    public void checkout(@Nonnull Run<?, ?> build, @Nonnull Launcher launcher, @Nonnull FilePath workspace,
                         @Nonnull TaskListener listener, @CheckForNull File changelogFile,
                         @CheckForNull SCMRevisionState scmrs) throws IOException, InterruptedException {

        String depot = fixEmptyAndTrim(this.depot);
        String stream = fixEmptyAndTrim(this.stream);

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

//        Use decorateStreamsCommand to handle extensions :)
        AccurevStreams streams;
        if (isIgnoreStreamParent()) streams = accurev.getStream(stream);
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
            baseline = new AccurevSCMRevisionState(1); // Accurev specifies transaction start from one

        if (project.getLastBuild() == null || baseline == null) {
            listener.getLogger().println("[poll] No previous build, so lets start the build.");
            return PollingResult.NO_CHANGES;
        }

        Node node;
        if (workspace != null && !getDescriptor().isPollOnMaster()) {
            node = AccurevUtils.workspaceToNode(workspace);
        } else
            node = Jenkins.getInstance();

        EnvVars environment = project.getEnvironment(node, listener);

        AccurevClient accurev = createClient(listener, environment, project, node, workspace);

        // Run update command - check using reference tree or stream name
        int latestTransaction = baseline.getTransaction();
        int actualTransaction = accurev.getLatestTransaction(getDepot()).getTransaction();
        if (actualTransaction != 0) latestTransaction = actualTransaction;

        List<String> paths = new ArrayList<>();

        UpdateCommand update = accurev.update()
            .stream(getStream())
            .preview(paths)
            .range(latestTransaction, baseline.getTransaction());

        for (AccurevSCMExtension ext : getExtensions()) {
            ext.decorateUpdateCommand(this, project, accurev, listener, update);
        }

        update.execute();

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

    @SuppressWarnings("deprecation")
    @Override
    public void migrate(Project p) {
        AccurevServer server = getServer();
        if (server != null) {
            url = server.getUrl();
            credentialsId = server.getCredentialsId();
        }
        super.migrate(p);
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

    public static final class AccurevServer extends AccurevServerBackwardCompatibility {

        // public static final String DEFAULT_VALID_TRANSACTION_TYPES = "add,chstream,co,defcomp,defunct,keep,mkstream,move,promote,purge,dispatch";
        protected static final List<String> DEFAULT_VALID_STREAM_TRANSACTION_TYPES = Collections
            .unmodifiableList(Arrays.asList("chstream", "defcomp", "mkstream", "promote", "demote_to", "demote_from", "purge"));
        protected static final List<String> DEFAULT_VALID_WORKSPACE_TRANSACTION_TYPES = Collections
            .unmodifiableList(Arrays.asList("add", "chstream", "co", "defcomp", "defunct", "keep",
                "mkstream", "move", "promote", "purge", "dispatch"));
        // keep all transaction types in a set for validation
        private static final String[] VTT_LIST = {"chstream", "defcomp", "mkstream", "promote", "demote_to", "demote_from", "purge"};
        private static final Set<String> VALID_TRANSACTION_TYPES = new HashSet<>(Arrays.asList(VTT_LIST));

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
