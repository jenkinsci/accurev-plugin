package hudson.plugins.accurev.extensions.impl;

import hudson.Extension;
import hudson.plugins.accurev.extensions.AccurevSCMExtension;
import hudson.plugins.accurev.extensions.AccurevSCMExtensionDescriptor;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;

public class SnapshotCheckout extends AccurevSCMExtension {

    private String snapshotNameFormat;

    @DataBoundConstructor
    public SnapshotCheckout(String snapshotNameFormat) {
        this.snapshotNameFormat = snapshotNameFormat;
    }

    public String getSnapshotNameFormat() {
        return snapshotNameFormat;
    }

    @Extension
    public static class DescriptorImpl extends AccurevSCMExtensionDescriptor {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "Create and Checkout from Snapshot";
        }
    }
}
