package hudson.plugins.accurev.extensions;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import hudson.plugins.accurev.AccurevSCM;
import jenkins.model.Jenkins;

public abstract class AccurevSCMExtensionDescriptor extends Descriptor<AccurevSCMExtension> {
    public static DescriptorExtensionList<AccurevSCMExtension, AccurevSCMExtensionDescriptor> all() {
        return Jenkins.getInstance().getDescriptorList(AccurevSCMExtension.class);
    }

    public boolean isApplicable(Class<? extends AccurevSCM> type) {
        return true;
    }
}
