package jenkins.plugins.accurev;


import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.Util;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMNavigator;
import jenkins.scm.api.SCMNavigatorDescriptor;
import jenkins.scm.api.SCMSourceCategory;
import jenkins.scm.api.SCMSourceObserver;
import jenkins.scm.impl.UncategorizedSCMSourceCategory;
import org.apache.commons.lang.StringUtils;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;

/**
 * Initialized by josep on 16-02-2017.
 */
public class AccurevSCMNavigator extends SCMNavigator {

    private final String host;
    private final String credentialsId;
    private int port = 5050;

    @DataBoundConstructor
    public AccurevSCMNavigator(String host, String credentialsId) {
        this.host = host;
        this.credentialsId = Util.fixEmpty(credentialsId);
    }

    public String getHost() {
        return host;
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    public int getPort() {
        return port;
    }

    @DataBoundSetter
    public void setPort(int port) {
        this.port = port;
    }

    public String getUrl() {
        return host + ":" + port;
    }

    @NonNull
    @Override
    protected String id() {
        return getUrl();
    }

    @Override
    public void visitSources(@NonNull SCMSourceObserver observer) throws IOException, InterruptedException {
        TaskListener listener = observer.getListener();

        // Check credentials is given
        if (StringUtils.isBlank(credentialsId)) {
            throw new AbortException("Must specify credentials");
        }

        StandardUsernamePasswordCredentials credentials = AccurevCredentials.lookupCredentials(
                getUrl(),
                credentialsId,
                observer.getContext()
        );

        if (credentials == null) {
            throw new AbortException("Credentials not located");
        }

        Jenkins jenkins = Jenkins.getInstance();
        EnvVars env = new EnvVars();

        String accurevExe = AccurevTool.getDefaultInstallation().getHome();
        Accurev accurev = Accurev.with(listener, env).in(jenkins.getRootPath()).using(accurevExe);
    }

    @Extension
    public static class DescriptorImpl extends SCMNavigatorDescriptor {

        public String getPronoun() {
            return Messages.AccurevSCMNavigator_RepositoryPronoun();
        }

        @NonNull
        @Override
        protected SCMSourceCategory[] createCategories() {
            return new SCMSourceCategory[]{
                    new UncategorizedSCMSourceCategory(
                            Messages._AccurevSCMNavigator_DepotSourceCategory()
                    )
            };
        }

        @Override
        public SCMNavigator newInstance(@CheckForNull String host) {
            return new AccurevSCMNavigator(host, "");
        }
    }
}
