package hudson.plugins.accurev;

import hudson.EnvVars;
import hudson.model.*;
import hudson.plugins.accurev.extensions.AccurevSCMExtension;
import hudson.plugins.accurev.extensions.AccurevSCMExtensionDescriptor;
import hudson.plugins.accurev.util.UniqueHelper;
import hudson.plugins.jetty.security.Password;
import hudson.scm.ChangeLogParser;
import hudson.scm.SCM;
import hudson.util.DescribableList;
import org.apache.commons.lang.StringUtils;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.io.Serializable;
import java.util.*;
import java.util.concurrent.locks.Lock;

/**
 * This is meant to keep backwards compatibility for anyone before version 1.0.0
 *
 * @author Joseph 'Casz' Petersen
 * @since 1.0.0
 */
public abstract class AccurevSCMBackwardCompatibility extends SCM implements Serializable {

// ------------------------------ FIELDS ------------------------------

    @Deprecated
    transient String serverName;
    @Deprecated
    transient String depot;
    @Deprecated
    transient String stream;
    @Deprecated
    transient boolean ignoreStreamParent;
    @Deprecated
    transient String wspaceORreftree;
    @Deprecated
    transient boolean cleanreftree;
    @Deprecated
    transient String workspace;
    @Deprecated
    transient boolean useSnapshot;
    @Deprecated
    transient boolean dontPopContent;
    @Deprecated
    transient String snapshotNameFormat;
    @Deprecated
    transient boolean synctime;
    @Deprecated
    transient String reftree;
    @Deprecated
    transient String subPath;
    @Deprecated
    transient String filterForPollSCM;
    @Deprecated
    transient String directoryOffset;
    @Deprecated
    transient boolean useReftree;
    @Deprecated
    transient boolean useWorkspace;
    @Deprecated
    transient boolean noWspaceNoReftree;
    @Deprecated
    transient String serverUUID;

    @Deprecated
    protected static final List<String> DEFAULT_VALID_STREAM_TRANSACTION_TYPES = Collections
            .unmodifiableList(Arrays.asList("chstream", "defcomp", "mkstream", "promote"));
    @Deprecated
    protected static final List<String> DEFAULT_VALID_WORKSPACE_TRANSACTION_TYPES = Collections
            .unmodifiableList(Arrays.asList("add", "chstream", "co", "defcomp", "defunct", "keep",
                    "mkstream", "move", "promote", "purge", "dispatch"));

    abstract DescribableList<AccurevSCMExtension, AccurevSCMExtensionDescriptor> getExtensions();

    void migrateThyLegacy() {
        try {
            AccurevSCM.AccurevSCMDescriptor descriptor = getDescriptor();
            if (descriptor != null) {
                List<AccurevServer> servers = descriptor.servers;

            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private void addIfMissing(AccurevSCMExtension ext) throws IOException {
        if (getExtensions().get(ext.getClass()) == null)
            getExtensions().add(ext);
    }

    /**
     * {@inheritDoc}
     *
     * @return ChangeLogParser
     */
    public ChangeLogParser createChangeLogParser() {
        return new AccurevChangeLogParser();
    }

    @Deprecated
    public boolean isUseSnapshot() {
        return useSnapshot;
    }

    @Deprecated
    public String getFilterForPollSCM() {
        return filterForPollSCM;
    }

    @Deprecated
    public String getSubPath() {
        return subPath;
    }

    @Deprecated
    public String getWspaceORreftree() {
        return wspaceORreftree;
    }

    @Deprecated
    public String getDepot() {
        return depot;
    }

    @Deprecated
    public Lock getOptionalLock() {
        return null;
    }

    @Deprecated
    public String getStream() {
        return stream;
    }

    @Deprecated
    public AccurevServer getServer() {
        AccurevServer server;
        if (serverUUID == null) {
            if (serverName == null) {
                // No fallback
                return null;
            }
            server = getServer(serverName);
        } else {
            server = getServer(serverUUID);
        }
        return server;
    }

        /**
         * Getter for property 'servers'.
         *
         * @return Value for property 'servers'.
         */
        @Deprecated
        @CheckForNull
        public List<AccurevServer> getServers() {
            AccurevSCM.AccurevSCMDescriptor descriptor = getDescriptor();

            if (descriptor._servers == null) {
                descriptor._servers = new ArrayList<>();
            }
            // We put this here to maintain backwards compatibility
            // because we changed the name of the 'servers' field to '_servers'
            if (descriptor.servers != null) {
                descriptor._servers.addAll(descriptor.servers);
                descriptor.servers = null;
            }
            return descriptor._servers;
        }

        @Deprecated
        @CheckForNull
        public AccurevServer getServer(String uuid) {
            List<AccurevServer> servers = getServers();
            if (uuid == null || getServers() == null) {
                return null;
            }
            for (AccurevServer server : servers) {
                if (UniqueHelper.isValid(uuid) && uuid.equals(server.getUUID())) {
                    return server;
                } else if (uuid.equals(server.getName())) {
                    // support old server name
                    return server;
                }
            }
            return null;
        }

    @Deprecated
    public void setServerUUID(String serverUUID) {
        this.serverUUID = serverUUID;
    }

    @Deprecated
    public boolean isIgnoreStreamParent() {
        return ignoreStreamParent;
    }

    @Deprecated
    public boolean hasStringVariableReference(final String str) {
        return StringUtils.isNotEmpty(str) && str.startsWith("$");
    }

    @Deprecated
    public String getPollingStream(Job<?, ?> project, TaskListener listener) {
        String parsedLocalStream;
        if (hasStringVariableReference(getStream())) {
            ParametersDefinitionProperty paramDefProp = project
                    .getProperty(ParametersDefinitionProperty.class);

            if (paramDefProp == null) {
                throw new IllegalArgumentException(
                        "Polling is not supported when stream name has a variable reference '" + getStream() + "'.");
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
            listener.getLogger().println("... expanded '" + getStream() + "' to '" + parsedLocalStream + "'.");
        } else {
            parsedLocalStream = getStream();
        }

        if (hasStringVariableReference(parsedLocalStream)) {
            throw new IllegalArgumentException(
                    "Polling is not supported when stream name has a variable reference '" + getStream() + "'.");
        }
        return parsedLocalStream;
    }

    public String getWorkspace() {
        return workspace;
    }

    public String getSnapshotNameFormat() {
        return snapshotNameFormat;
    }

    public String getReftree() {
        return reftree;
    }

    public String getDirectoryOffset() {
        return directoryOffset;
    }

    public boolean isSynctime() {
        return synctime;
    }

    public boolean isDontPopContent() {
        return dontPopContent;
    }

    @Override
    public AccurevSCM.AccurevSCMDescriptor getDescriptor() {
        return (AccurevSCM.AccurevSCMDescriptor) super.getDescriptor();
    }

    // --------------------------- Inner Class ---------------------------------------------------
    public static final class AccurevServer implements Serializable {

        @Deprecated
        private transient String name;
        @Deprecated
        private transient String host;
        @Deprecated
        private transient int port;
        @Deprecated
        private transient String username;
        @Deprecated
        private transient String password;
        @Deprecated
        private transient String uuid;
        @Deprecated
        private transient String validTransactionTypes;
        @Deprecated
        private transient boolean syncOperations;
        @Deprecated
        private transient boolean minimiseLogins;
        @Deprecated
        private transient boolean useNonexpiringLogin;
        @Deprecated
        private transient boolean useRestrictedShowStreams;
        @Deprecated
        private transient boolean useColor;
        @Deprecated
        private transient boolean usePromoteListen;

        @Deprecated
        public String getHost() {
            return host;
        }

        public boolean isUsePromoteListen() {
            return usePromoteListen;
        }

        @Deprecated
        public String getUUID() {
            return uuid;
        }

        @Deprecated
        public String getName() {
            return name;
        }

        @Deprecated
        public boolean isUseRestrictedShowStreams() {
            return useRestrictedShowStreams;
        }

        public boolean isUseColor() {
            return useColor;
        }

        public String getUsername() {
            return username;
        }

        public int getPort() {
            return port;
        }

        public boolean isMinimiseLogins() {
            return minimiseLogins;
        }

        public boolean isUseNonexpiringLogin() {
            return useNonexpiringLogin;
        }

        public String getPassword() {
            return Password.obfuscate(password);
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