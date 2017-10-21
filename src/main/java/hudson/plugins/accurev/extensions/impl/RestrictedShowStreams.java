package hudson.plugins.accurev.extensions.impl;

import javax.annotation.Nonnull;

import org.kohsuke.stapler.DataBoundConstructor;

import hudson.Extension;
import hudson.model.Run;
import hudson.model.TaskListener;

import jenkins.plugins.accurev.AccurevClient;
import jenkins.plugins.accurev.StreamsCommand;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.UserRemoteConfig;
import hudson.plugins.accurev.extensions.AccurevSCMExtension;
import hudson.plugins.accurev.extensions.AccurevSCMExtensionDescriptor;

public class RestrictedShowStreams extends AccurevSCMExtension {
    @DataBoundConstructor
    public RestrictedShowStreams() {
    }

    @Override
    public void decorateStreamsCommand(AccurevSCM scm, UserRemoteConfig config, Run<?, ?> build, AccurevClient accurev, TaskListener listener, StreamsCommand cmd) {
        cmd.restricted().stream(config.getStream());
    }

    @Extension
    public static class DescriptorImpl extends AccurevSCMExtensionDescriptor {
        @Override
        @Nonnull
        public String getDisplayName() {
            return "Show one stream at a time";
        }
    }
}
