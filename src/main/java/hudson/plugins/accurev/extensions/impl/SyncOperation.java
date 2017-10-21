package hudson.plugins.accurev.extensions.impl;

import javax.annotation.Nonnull;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;

import hudson.plugins.accurev.extensions.AccurevSCMExtension;
import hudson.plugins.accurev.extensions.AccurevSCMExtensionDescriptor;

public class SyncOperation extends AccurevSCMExtension {
    @DataBoundConstructor
    public SyncOperation() {
    }

    @Extension
    public static class DescriptorImpl extends AccurevSCMExtensionDescriptor {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "Synchronize AccuRev CLI Operations";
        }
    }
}
