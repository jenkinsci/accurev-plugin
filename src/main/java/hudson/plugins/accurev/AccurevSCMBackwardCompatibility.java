package hudson.plugins.accurev;

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
import hudson.util.FormValidation;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.CheckForNull;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public abstract class AccurevSCMBackwardCompatibility extends SCM implements Serializable {
    private static final Logger LOGGER = Logger.getLogger(AccurevSCM.class.getName());

    private transient String serverName;
    private transient boolean ignoreStreamParent;
    private transient String wspaceORreftree;
    private transient boolean cleanreftree;
    private transient String workspace;
    private transient boolean useSnapshot;
    private transient boolean dontPopContent;
    private transient String snapshotNameFormat;
    private transient String reftree;
    private transient String subPath;
    private transient String filterForPollSCM;
    private transient String directoryOffset;
    private transient boolean useReftree;
    private transient boolean useWorkspace;
    private transient boolean noWspaceNoReftree;
    private transient String serverUUID;
    private static final long serialVersionUID = 1L;

    public String getServerName() {
        return serverName;
    }

    public String getServerUUID() {
        return serverUUID;
    }

    public String getWspaceORreftree() {
        return wspaceORreftree;
    }

    public String getReftree() {
        return reftree;
    }

    public String getWorkspace() {
        return workspace;
    }

    public String getSubPath() {
        return subPath;
    }

    public String getFilterForPollSCM() {
        return filterForPollSCM;
    }

    public String getSnapshotNameFormat() {
        return snapshotNameFormat;
    }

    public boolean isIgnoreStreamParent() {
        return ignoreStreamParent;
    }

    public boolean isDontPopContent() {
        return dontPopContent;
    }

    public boolean isCleanreftree() {
        return cleanreftree;
    }

    public boolean isUseSnapshot() {
        return useSnapshot;
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
        if (getServerUUID() == null) {
            if (getServerName() == null) {
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

    public abstract static class AccurevServerBackwardCompatibility {
        private transient static final String __OBFUSCATE = "OBF:";
        private transient String name;
        private transient String host;
        transient String username;
        transient String password;
        private transient int port = 5050;
        private transient String credentialsId;
        private transient UUID uuid;
        private transient String validTransactionTypes;
        private transient boolean syncOperations;
        private transient boolean minimiseLogins;
        private transient boolean useNonexpiringLogin;
        private transient boolean useRestrictedShowStreams;
        private transient boolean useColor;
        private transient boolean usePromoteListen;

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

        public boolean isSyncOperations() {
            return syncOperations;
        }

        public boolean isMinimiseLogins() {
            return minimiseLogins;
        }

        public boolean isUseNonexpiringLogin() {
            return useNonexpiringLogin;
        }

        public boolean isUseRestrictedShowStreams() {
            return useRestrictedShowStreams;
        }

        public boolean isUseColor() {
            return useColor;
        }

        public boolean isUsePromoteListen() {
            return usePromoteListen;
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

        public String getUrl() {
            return getHost() + ":" + getPort();
        }
    }
}
