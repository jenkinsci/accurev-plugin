package hudson.plugins.accurev.extensions.impl;

import hudson.Extension;
import hudson.plugins.accurev.extensions.AccurevSCMExtension;
import hudson.plugins.accurev.extensions.AccurevSCMExtensionDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;

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

    @Extension
    public static class DescriptorImpl extends AccurevSCMExtensionDescriptor {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "Use Reference Tree";
        }
    }
}
