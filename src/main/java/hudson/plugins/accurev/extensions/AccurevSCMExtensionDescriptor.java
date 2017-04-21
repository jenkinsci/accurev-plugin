package hudson.plugins.accurev.extensions;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import jenkins.model.Jenkins;

import hudson.plugins.accurev.AccurevSCM;

public abstract class AccurevSCMExtensionDescriptor extends Descriptor<AccurevSCMExtension> {
    public static DescriptorExtensionList<AccurevSCMExtension, AccurevSCMExtensionDescriptor> all() {
        return Jenkins.getInstance().getDescriptorList(AccurevSCMExtension.class);
    }

    public boolean isApplicable(Class<? extends AccurevSCM> type) {
        return true;
    }
}
