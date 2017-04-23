package hudson.plugins.accurev;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;

import org.apache.commons.lang.StringUtils;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import hudson.Util;
import hudson.scm.SCM;
import hudson.security.ACL;
import hudson.util.DescribableList;
import hudson.util.Secret;
import jenkins.model.Jenkins;

import hudson.plugins.accurev.extensions.AccurevSCMExtension;
import hudson.plugins.accurev.extensions.AccurevSCMExtensionDescriptor;
import hudson.plugins.accurev.extensions.impl.AccurevWorkspaceCheckout;
import hudson.plugins.accurev.extensions.impl.IgnoreParentStream;
import hudson.plugins.accurev.extensions.impl.ReferenceTreeCheckout;
import hudson.plugins.accurev.extensions.impl.RestrictedShowStreams;
import hudson.plugins.accurev.extensions.impl.SkipPopulate;
import hudson.plugins.accurev.extensions.impl.SnapshotCheckout;
import hudson.plugins.accurev.extensions.impl.SparseCheckout;
import hudson.plugins.accurev.extensions.impl.SyncOperation;
import hudson.plugins.accurev.extensions.impl.UseColor;

@SuppressWarnings({"deprecation", "unused"})
public abstract class AccurevSCMBackwardCompatibility extends SCM {
    static final Logger LOGGER = Logger.getLogger(AccurevSCM.class.getName());
    transient String serverName;
    transient String depot;
    transient String stream;
    transient boolean ignoreStreamParent;
    transient String wspaceORreftree;
    transient boolean useReftree;
    transient boolean useWorkspace;
    transient boolean noWspaceNoReftree;
    transient boolean cleanreftree;
    transient String workspace;
    transient boolean useSnapshot;
    transient boolean dontPopContent;
    transient String snapshotNameFormat;
    transient boolean synctime;
    transient String reftree;
    transient String subPath;
    transient String filterForPollSCM;
    transient String directoryOffset;
    transient String serverUUID;

    @Deprecated
    public String getDepot() {
        return depot;
    }

    @Deprecated
    public String getStream() {
        return stream;
    }

    @Deprecated
    public boolean isSynctime() {
        return synctime;
    }

    @Deprecated
    public String getDirectoryOffset() {
        return directoryOffset;
    }

    @Deprecated
    public String getServerName() {
        return serverName;
    }

    @Deprecated
    public String getServerUUID() {
        return serverUUID;
    }

    @Deprecated
    public String getWspaceORreftree() {
        return wspaceORreftree;
    }

    @Deprecated
    public String getReftree() {
        return reftree;
    }

    @Deprecated
    public String getWorkspace() {
        return workspace;
    }

    @Deprecated
    public String getSubPath() {
        return subPath;
    }

    @Deprecated
    public String getFilterForPollSCM() {
        return filterForPollSCM;
    }

    @Deprecated
    public String getSnapshotNameFormat() {
        return snapshotNameFormat;
    }

    @Deprecated
    public boolean isIgnoreStreamParent() {
        return ignoreStreamParent;
    }

    @Deprecated
    public boolean isDontPopContent() {
        return dontPopContent;
    }

    @Deprecated
    public boolean isCleanreftree() {
        return cleanreftree;
    }

    @Deprecated
    public boolean isUseSnapshot() {
        return useSnapshot;
    }

    @Deprecated
    public boolean isUseReftree() {
        return useReftree;
    }

    @Deprecated
    public boolean isUseWorkspace() {
        return useWorkspace;
    }

    @Deprecated
    public boolean isNoWspaceNoReftree() {
        return noWspaceNoReftree;
    }

    /**
     * Getter for Accurev server
     *
     * @return AccurevServer based on serverUUID (or serverName if serverUUID is null)
     */
    @CheckForNull
    @Deprecated
    public AccurevSCM.AccurevServer getServer() {
        AccurevSCM.AccurevServer server;
        AccurevSCM.AccurevSCMDescriptor descriptor = AccurevSCM.configuration();
        if (serverUUID == null) {
            if (serverName == null) {
                // No fallback
                LOGGER.severe("AccurevSCM.getServer called but serverName and serverUUID are NULL!");
                return null;
            }
            LOGGER.warning("Getting server by name (" + getServerName() + "), because UUID is not set.");
            server = descriptor.getServer(getServerName());
        } else {
            server = descriptor.getServer(getServerUUID());
        }
        return server;
    }

    void migrate(AccurevSCM.AccurevServer server) {
        try {
            if (server != null) {
                if (server.isSyncOperations()) {
                    addIfMissing(new SyncOperation());
                }
                if (server.isUseColor()) {
                    addIfMissing(new UseColor());
                }
                if (server.isUseRestrictedShowStreams()) {
                    addIfMissing(new RestrictedShowStreams());
                }
            }
            if (useReftree && StringUtils.isNotBlank(reftree)) {
                ReferenceTreeCheckout referenceTreeCheckout = new ReferenceTreeCheckout(reftree);
                referenceTreeCheckout.setCleanReferenceTree(cleanreftree);
                addIfMissing(referenceTreeCheckout);
            } else if (useWorkspace && StringUtils.isNotBlank(workspace)) {
                addIfMissing(new AccurevWorkspaceCheckout(workspace));
            }
            if (useSnapshot && StringUtils.isNotBlank(snapshotNameFormat)) {
                addIfMissing(new SnapshotCheckout(snapshotNameFormat));
            }
            if (dontPopContent) {
                addIfMissing(new SkipPopulate());
            }
            if (ignoreStreamParent) {
                addIfMissing(new IgnoreParentStream());
            }
            if (StringUtils.isNotBlank(subPath)) {
                String paths = subPath.replaceAll(",", "\n");
                addIfMissing(new SparseCheckout(paths));
            }
        } catch (IOException e) {
            throw new AssertionError(e); // since our extensions don't have any real Saveable
        }
    }

    abstract DescribableList<AccurevSCMExtension, AccurevSCMExtensionDescriptor> getExtensions();

    private void addIfMissing(AccurevSCMExtension ext) throws IOException {
        if (getExtensions().get(ext.getClass()) == null)
            getExtensions().add(ext);
    }

    public abstract static class AccurevServerBackwardCompatibility {
        private transient static final String __OBFUSCATE = "OBF:";
        private transient String username;
        private transient String password;
        private transient String name;
        private transient String host;
        private transient int port = 5050;
        private transient String credentialsId;
        private transient UUID uuid;
        private transient boolean syncOperations;
        private transient boolean useRestrictedShowStreams;
        private transient boolean useColor;

        public AccurevServerBackwardCompatibility(String uuid, String name, String host, int port, String username, String password) {
            this.uuid = StringUtils.isBlank(uuid) ? null : UUID.fromString(uuid);
            this.name = name;
            this.host = host;
            this.port = port;
            this.username = username;
            this.password = password;
        }

        public AccurevServerBackwardCompatibility(String uuid, String name, String host, int port, String credentialsId) {
            this.uuid = StringUtils.isBlank(uuid) ? UUID.randomUUID() : UUID.fromString(uuid);
            this.name = name;
            this.host = host;
            this.port = port;
            this.credentialsId = credentialsId;
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
        @Deprecated
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
            return username;
        }

        /**
         * Getter for property 'password'.
         *
         * @return Value for property 'password'.
         */
        public String getPassword() {
            return password;
        }

        public Object readResolve() throws IOException {
            migrateCredentials();
            return this;
        }

        boolean migrateCredentials() throws IOException {
            if (username != null && password != null && credentialsId == null) {
                LOGGER.info("Migrating to credentials");
                String secret = deobfuscate(password);
                String credentialsId = "";
                List<DomainRequirement> domainRequirements = Util.fixNull(URIRequirementBuilder
                    .fromUri(getUrl())
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
                    SystemCredentialsProvider.getInstance().save();
                    return true;
                } else {
                    LOGGER.severe("Migration failed");
                }
            }
            return false;
        }

        public String getUrl() {
            return getHost() + ":" + getPort();
        }

        public boolean isSyncOperations() {
            return syncOperations;
        }

        public boolean isUseRestrictedShowStreams() {
            return useRestrictedShowStreams;
        }

        public boolean isUseColor() {
            return useColor;
        }
    }
}
