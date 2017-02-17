package jenkins.plugins.accurev;

import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.TaskListener;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import jenkins.scm.api.*;
import jenkins.scm.impl.ChangeRequestSCMHeadCategory;
import jenkins.scm.impl.TagSCMHeadCategory;
import jenkins.scm.impl.UncategorizedSCMHeadCategory;
import org.kohsuke.stapler.DataBoundSetter;

import java.io.IOException;

/**
 * Initialized by josep on 16-02-2017.
 */
public class AccurevSCMSource extends SCMSource {

    private final String host;
    private final String credentialsId;
    private int port = 5050;

    /**
     * Constructor.
     *
     * @param id            the id or {@code null}.
     * @param host          the accurev server hostname
     * @param credentialsId credentials for logging into accurev
     */
    protected AccurevSCMSource(@CheckForNull String id, String host, String credentialsId) {
        super(id);
        this.host = host;
        this.credentialsId = credentialsId;
    }

    @Override
    protected void retrieve(@CheckForNull SCMSourceCriteria criteria, @NonNull SCMHeadObserver observer,
                            @CheckForNull SCMHeadEvent<?> event, @NonNull TaskListener listener)
            throws IOException, InterruptedException {
        StandardUsernamePasswordCredentials credentials = getCredentials();
    }

    @NonNull
    @Override
    public SCM build(@NonNull SCMHead head, @CheckForNull SCMRevision revision) {
        return new NullSCM(); // Need to Implement
    }


    @CheckForNull
    /* package */ StandardUsernamePasswordCredentials getCredentials() {
        return AccurevCredentials.lookupCredentials(
                host,
                credentialsId,
                getOwner()
        );
    }

    @CheckForNull
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

    @Extension
    public static class DescriptorImpl extends SCMSourceDescriptor {
        public String getPronoun() {
            return Messages.AccurevSCMSource_RepositoryPronoun();
        }

        @NonNull
        @Override
        protected SCMHeadCategory[] createCategories() {
            return new SCMHeadCategory[]{
                    new UncategorizedSCMHeadCategory(
                            Messages._AccurevSCMSource_StreamHeadCategory() // type: normal
                    ),
                    new UncategorizedSCMHeadCategory(
                            Messages._AccurevSCMSource_WorkspaceHeadCategory() // type: workspace
                    ),
                    new UncategorizedSCMHeadCategory(
                            Messages._AccurevSCMSource_GatedStreamHeadCategory() // type: gated
                    ),
                    new ChangeRequestSCMHeadCategory(
                            Messages._AccurevSCMSource_StagingStreamHeadCategory() // type: staging
                    ),
                    new TagSCMHeadCategory(
                            Messages._AccurevSCMSource_SnapshotHeadCategory() // type: snapshot
                    )
            };
        }
    }
}
