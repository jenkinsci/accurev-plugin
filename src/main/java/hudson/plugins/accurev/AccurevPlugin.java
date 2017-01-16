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
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.plugins.accurev.AccurevSCM.AccurevSCMDescriptor;
import hudson.plugins.accurev.config.AccurevPluginConfig;
import hudson.plugins.accurev.config.AccurevServerConfig;
import hudson.security.ACL;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang.ObjectUtils.defaultIfNull;

/**
 * @author josp
 */
@SuppressWarnings("unused") // Used for initialization/migration purpose
public class AccurevPlugin {
    private static final Logger LOGGER = Logger.getLogger(AccurevPlugin.class.getName());

    /**
     * We need ensure that migrator will run after jobs are loaded
     * Launches migration after plugin and jobs already initialized.
     * Expected milestone: @Initializer(after = JOB_LOADED)
     *
     * @throws Exception Exceptions
     */
    @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED, before = InitMilestone.JOB_LOADED)
    public static void initializers() throws Exception {
        AccurevSCMDescriptor scmDescriptor = Jenkins.getInstance().getDescriptorByType(AccurevSCMDescriptor.class);
        scmDescriptor.load();

        try {
            if (scmDescriptor._servers == null) {
                scmDescriptor._servers = new ArrayList<>();
            }
            // We put this here to maintain backwards compatibility
            // because we changed the name of the 'servers' field to '_servers'
            if (scmDescriptor.servers != null) {
                scmDescriptor._servers.addAll(scmDescriptor.servers);
                scmDescriptor.servers = null;
            }
            List<AccurevSCM.AccurevServer> servers = scmDescriptor._servers;
            if (isNotEmpty(servers)) {
                LOGGER.info("Migrating");
                List<AccurevServerConfig> configs = AccurevPlugin.configuration().getConfigs();
                for (AccurevSCM.AccurevServer server : Util.fixNull(servers)) {
                    String credentialsId = "";
                    String secret = server.getPassword();
                    server.noise();
                    List<DomainRequirement> domainRequirements = Util.fixNull(URIRequirementBuilder
                            .fromUri("")
                            .withHostnamePort(server.host, server.port)
                            .build());
                    List<StandardUsernamePasswordCredentials> credentials = CredentialsMatchers.filter(
                            CredentialsProvider.lookupCredentials(
                                    StandardUsernamePasswordCredentials.class,
                                    Jenkins.getInstance(), ACL.SYSTEM, domainRequirements),
                            CredentialsMatchers.withUsername(server.username)
                    );
                    for (StandardUsernamePasswordCredentials cred : credentials) {
                        if (StringUtils.equals(secret, Secret.toString(cred.getPassword()))) {
                            // If some credentials have the same username/password, use those.
                            credentialsId = cred.getId();
                            break;
                        }
                    }
                    if (StringUtils.isBlank(credentialsId)) {
                        // If we couldn't find any existing credentials,
                        // create new credentials with the principal and secret and use it.
                        StandardUsernamePasswordCredentials newCredentials = new UsernamePasswordCredentialsImpl(
                                CredentialsScope.SYSTEM, null, "Migrated by Accurev Plugin", server.username, secret);
                        SystemCredentialsProvider.getInstance().getCredentials().add(newCredentials);
                        credentialsId = newCredentials.getId();
                    }
                    AccurevServerConfig config = new AccurevServerConfig(
                            credentialsId,
                            server.uuid,
                            server.name,
                            server.host,
                            server.port,
                            false,
                            false,
                            false,
                            false);
                    configs.add(config);
                }
                scmDescriptor._servers = null;
                scmDescriptor.servers = null;
                scmDescriptor.save();
                AccurevPlugin.configuration().save();
            } else {
                LOGGER.info("No servers");
            }
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Nonnull
    public static AccurevPluginConfig configuration() {
        return (AccurevPluginConfig) defaultIfNull(AccurevPluginConfig.all().get(AccurevPluginConfig.class),
                AccurevPluginConfig.EMPTY_CONFIG);
    }
}
