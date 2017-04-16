package hudson.plugins.accurev.extensions.impl;

import hudson.Extension;
import hudson.plugins.accurev.extensions.AccurevSCMExtension;
import hudson.plugins.accurev.extensions.AccurevSCMExtensionDescriptor;

import javax.annotation.Nonnull;

public class AccurevWorkspaceCheckout extends AccurevSCMExtension {

    private String workspace;

    public AccurevWorkspaceCheckout(String workspace) {
        this.workspace = workspace;
    }

    @Override
    public boolean requiresWorkspaceForPolling() {
        return true;
    }

    public String getWorkspace() {
        return workspace;
    }

    @Extension
    public static class DescriptorImpl extends AccurevSCMExtensionDescriptor {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "Use Accurev Workspace";
        }
    }
}
