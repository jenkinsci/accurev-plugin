package hudson.plugins.accurev;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Item;
import hudson.model.Queue;
import hudson.model.queue.Tasks;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.export.ExportedBean;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import static hudson.Util.fixEmpty;
import static hudson.Util.fixEmptyAndTrim;

@ExportedBean
public class UserRemoteConfig extends AbstractDescribableImpl<UserRemoteConfig> implements Serializable {

    private String url;
    private String credentialsId;
    private String depot;
    private String stream;
    private String localDir = ".";

    @DataBoundConstructor
    public UserRemoteConfig(String url, String credentialsId, String depot, String stream, String localDir) {
        this.url = fixEmptyAndTrim(url);
        this.credentialsId = fixEmpty(credentialsId);
        this.depot = fixEmpty(depot);
        this.stream = fixEmpty(stream);
        this.localDir = fixEmptyAndTrim(localDir);
        if (this.localDir == null) this.localDir = ".";
    }

    public String getUrl() {
        return url;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public void setCredentialsId(String credentialsId) {
        this.credentialsId = credentialsId;
    }

    public String getDepot() {
        return depot;
    }

    public String getStream() {
        return stream;
    }

    public String getLocalDir() {
        return localDir;
    }

    public String toString() {
        return getUrl() + " => " + getDepot() + " => " + getStream();
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<UserRemoteConfig> {
        @Nonnull
        @Override
        public String getDisplayName() {
            return "";
        }

        @SuppressWarnings("unused") // Used by stapler
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item context,
                                                     @QueryParameter String url,
                                                     @QueryParameter String credentialsId) {
            if (context == null && !Jenkins.getActiveInstance().hasPermission(Jenkins.ADMINISTER) ||
                context != null && !context.hasPermission(Item.EXTENDED_READ)) {
                return new StandardListBoxModel().includeCurrentValue(credentialsId);
            }
            List<DomainRequirement> domainRequirements;
            String remote = fixEmptyAndTrim(url.trim());
            if (remote == null) {
                domainRequirements = Collections.emptyList();
            } else {
                domainRequirements = URIRequirementBuilder.fromUri(remote).build();
            }
            return new StandardListBoxModel()
                .includeEmptyValue()
                .includeMatchingAs(
                    context instanceof Queue.Task
                        ? Tasks.getAuthenticationOf((Queue.Task) context)
                        : ACL.SYSTEM,
                    context,
                    StandardUsernamePasswordCredentials.class,
                    domainRequirements,
                    CredentialsMatchers.always()
                );
        }
    }
}
