package hudson.plugins.accurev.config;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Extension;
import hudson.plugins.accurev.extensions.AccurevSCMExtension;
import hudson.plugins.accurev.extensions.AccurevSCMExtensionDescriptor;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * @author josp
 */
public class AccurevServerConfig extends AccurevSCMExtension {
    private static final Logger LOGGER = LoggerFactory.getLogger(AccurevServerConfig.class);

    private final String credentialsId;
    private final String id;
    private String host;
    private int port;
    private boolean promoteListen;
    private boolean minimiseLogin;
    private transient List<AccurevDepot> depots;

    public AccurevServerConfig(String credentialsId) {
        this.credentialsId = credentialsId;
        this.id = null;
    }

    @DataBoundConstructor
    public AccurevServerConfig(String credentialsId, String id, String host, int port, boolean promoteListen, boolean minimiseLogin) {
        this.credentialsId = credentialsId;
        if (StringUtils.isBlank(id)) this.id = UniqueHelper.randomUUID();
        else this.id = id;
        this.host = host;
        this.port = port;
        this.promoteListen = promoteListen;
        this.minimiseLogin = minimiseLogin;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public String getId() {
        return id;
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

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isPromoteListen() {
        return promoteListen;
    }

    public void setPromoteListen(boolean promoteListen) {
        this.promoteListen = promoteListen;
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

    public boolean isMinimiseLogin() {
        return minimiseLogin;
    }

    @Extension
    public static class DescriptorImpl extends AccurevSCMExtensionDescriptor {

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
            AccurevServerConfig config = new AccurevServerConfig(credentialsId);
            AccuRevCommandLine cl = new AccuRevCommandLine();
            cl.setServer(config.getHost(), config.getPort());

            try {
                if (cl.login(config.getUsername(), config.getPassword())) {
                    return FormValidation.ok("Credentials verified for user %s", config.getUsername());
                } else {
                    return FormValidation.error("Failed to validate credentials");
                }
            } catch (AccuRevException e) {
                return FormValidation.error(e, "Failed to validate credentials");
            }
        }

    }
}
