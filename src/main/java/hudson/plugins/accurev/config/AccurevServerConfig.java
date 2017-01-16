package hudson.plugins.accurev.config;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.google.common.cache.Cache;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.plugins.accurev.AccurevStream;
import hudson.plugins.accurev.cmd.Login;
import hudson.plugins.accurev.cmd.ShowDepots;
import hudson.plugins.accurev.cmd.ShowStreams;
import hudson.plugins.accurev.extensions.impl.AccurevDepot;
import hudson.plugins.accurev.util.UniqueHelper;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.scm.provider.accurev.AccuRevException;
import org.apache.maven.scm.provider.accurev.cli.AccuRevCommandLine;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.apache.commons.lang.ObjectUtils.defaultIfNull;

/**
 * @author josp
 */
@XStreamAlias("accurev-server-config")
public class AccurevServerConfig extends AbstractDescribableImpl<AccurevServerConfig> {
    private static final Logger LOGGER = Logger.getLogger(AccurevServerConfig.class.getName());

    private final String credentialsId;
    private final String id;
    private String name;
    private String host;
    private int port;
    private transient boolean loggedIn;
    private boolean usePromoteListener;
    private boolean useMinimiseLogin;
    private boolean useRestrictedShowStreams;
    private boolean useColor;
    private CacheConfiguration cache;
    private final Cache<String, Map<String, AccurevDepot>> accurevDepots;
    private final Cache<String, Map<String, AccurevStream>> accurevStreams;

    @DataBoundConstructor
    public AccurevServerConfig(String credentialsId, @CheckForNull String id, String name, String host, int port, boolean usePromoteListener, boolean useMinimiseLogin, boolean useRestrictedShowStreams, boolean useColor) {
        this.credentialsId = credentialsId;
        if (UniqueHelper.isNotValid(id)) this.id = UniqueHelper.randomUUID();
        else this.id = id;
        this.name = name;
        this.host = host;
        this.port = port;
        this.usePromoteListener = usePromoteListener;
        this.useMinimiseLogin = useMinimiseLogin;
        this.useRestrictedShowStreams = useRestrictedShowStreams;
        this.useColor = useColor;

        if (this.cache == null) {
            this.cache = new CacheConfiguration(0, 0);
        }

        // On startup userCache and groupCache are not created and cache is different from null
        if (cache.getAccurevDepots() == null || cache.getAccurevStreams() == null) {
            this.cache = new CacheConfiguration(cache.getSize(), cache.getTtl());
        }

        this.accurevDepots = cache.getAccurevDepots();
        this.accurevStreams = cache.getAccurevStreams();
    }

    public Map<String, AccurevDepot> retrieveDepots() {
        AccurevServerConfig config = this;
        try {
            return accurevDepots.get(getHostPort(), () -> ShowDepots.getDepots(config));
        } catch (ExecutionException e) {
            LOGGER.log(Level.SEVERE, "Failed to ache depots", e);
        }
        return Collections.emptyMap();
    }

    public Map<String, AccurevStream> retrieveStreams(final AccurevDepot depot) {
        try {
            return accurevStreams.get(depot.getHostPortDepot(), new Callable<Map<String, AccurevStream>>() {
                @Override
                public Map<String, AccurevStream> call() throws Exception {
                    return ShowStreams.getStreams(depot);
                }
            });
        } catch (ExecutionException e) {
            LOGGER.log(Level.SEVERE, "Failed to ache depots", e);
        }
        return Collections.emptyMap();
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public String getHostPort() {
        return host + ":" + port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isUsePromoteListener() {
        return usePromoteListener;
    }

    public void setUsePromoteListener(boolean usePromoteListener) {
        this.usePromoteListener = usePromoteListener;
    }

    public boolean isUseMinimiseLogin() {
        return useMinimiseLogin;
    }

    public void setUseMinimiseLogin(boolean useMinimiseLogin) {
        this.useMinimiseLogin = useMinimiseLogin;
    }

    public boolean isUseRestrictedShowStreams() {
        return useRestrictedShowStreams;
    }

    public boolean isUseColor() {
        return useColor;
    }

    public StandardUsernamePasswordCredentials getCredentails() {
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

    public String getUsername() {
        StandardUsernamePasswordCredentials credentials = getCredentails();
        return credentials == null ? "jenkins" : credentials.getUsername();
    }

    public String getPassword() {
        StandardUsernamePasswordCredentials credentials = getCredentails();
        return credentials == null ? "" : Secret.toString(credentials.getPassword());
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<AccurevServerConfig> {

        @Override public String getDisplayName() {
            return "AccuRev Server";
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

        @SuppressWarnings("unused")
        public FormValidation doVerifyCredentials(
                @QueryParameter String host,
                @QueryParameter int port,
                @QueryParameter String credentialsId) throws IOException {

            StandardUsernamePasswordCredentials usernamePasswordCredentials = null;
            try {
                if (Login.validateCredentials(usernamePasswordCredentials)) {
                    return FormValidation.ok("Credentials verified for user");
                } else {
                    return FormValidation.error("Failed to validate credentials");
                }
            } catch (AccuRevException e) {
                return FormValidation.error(e, "Failed to validate credentials");
            }
        }

    }
}
