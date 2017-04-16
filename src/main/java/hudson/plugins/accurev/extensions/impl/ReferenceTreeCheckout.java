package hudson.plugins.accurev.extensions.impl;

import hudson.Extension;
import hudson.model.Job;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.extensions.AccurevSCMExtension;
import hudson.plugins.accurev.extensions.AccurevSCMExtensionDescriptor;
import jenkins.plugins.accurev.AccurevClient;
import jenkins.plugins.accurev.AccurevException;
import jenkins.plugins.accurev.UpdateCommand;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.io.IOException;

public class ReferenceTreeCheckout extends AccurevSCMExtension {

    private String referenceTree;
    private boolean cleanReferenceTree;

    @DataBoundConstructor
    public ReferenceTreeCheckout(String referenceTree) {
        this.referenceTree = referenceTree;
    }

    @Override
    public boolean requiresWorkspaceForPolling() {
        return true;
    }

    public String referenceTree() {
        return referenceTree;
    }

    public boolean isCleanReferenceTree() {
        return cleanReferenceTree;
    }

    @DataBoundSetter
    public void setCleanReferenceTree(boolean cleanReferenceTree) {
        this.cleanReferenceTree = cleanReferenceTree;
    }

    @Override
    public void decorateUpdateCommand(AccurevSCM scm, Job project, AccurevClient accurev, TaskListener listener, UpdateCommand cmd) throws IOException, InterruptedException, AccurevException {

    }

    @Extension
    public static class DescriptorImpl extends AccurevSCMExtensionDescriptor {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "Use Reference Tree";
        }
    }
}
