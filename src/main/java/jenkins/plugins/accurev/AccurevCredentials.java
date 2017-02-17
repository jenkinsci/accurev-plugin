package jenkins.plugins.accurev;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.Queue;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import jenkins.scm.api.SCMSourceOwner;
import org.apache.commons.lang.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * Initialized by josep on 16-02-2017.
 */
public class AccurevCredentials {
    private AccurevCredentials() {
        throw new IllegalAccessError("Utility class");
    }

    @CheckForNull
    static StandardUsernamePasswordCredentials lookupCredentials(@CheckForNull String serverUrl,
                                                                 @CheckForNull String credentialsId,
                                                                 @CheckForNull SCMSourceOwner context) {
        if (StringUtils.isNotBlank(credentialsId) && context != null) {
            return CredentialsMatchers.firstOrNull(
                    CredentialsProvider.lookupCredentials(
                            StandardUsernamePasswordCredentials.class,
                            context,
                            context instanceof Queue.Task
                                    ? Tasks.getDefaultAuthenticationOf((Queue.Task) context)
                                    : ACL.SYSTEM,
                            domainRequirementsOf(serverUrl)
                    ),
                    CredentialsMatchers.allOf(
                            CredentialsMatchers.withId(credentialsId),
                            CredentialsMatchers.anyOf(
                                    CredentialsMatchers.instanceOf(StandardUsernamePasswordCredentials.class)
                            )
                    )
            );
        }
        return null;
    }

    static List<DomainRequirement> domainRequirementsOf(@CheckForNull String serverUrl) {
        if (serverUrl == null) {
            return Collections.emptyList();
        } else {
            return URIRequirementBuilder.fromUri(serverUrl).build();
        }
    }
}
