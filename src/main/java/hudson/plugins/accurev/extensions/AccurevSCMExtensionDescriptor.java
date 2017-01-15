package hudson.plugins.accurev.extensions;

import hudson.DescriptorExtensionList;
import hudson.model.Descriptor;
import hudson.plugins.accurev.AccurevSCM;
import jenkins.model.Jenkins;

/**
 * @author Joseph Petersen
 */
public class AccurevSCMExtensionDescriptor extends Descriptor<AccurevSCMExtension> {
    @SuppressWarnings("unused")
    public boolean isApplicable(Class<? extends AccurevSCM> type) {
        return true;
    }

    public static DescriptorExtensionList<AccurevSCMExtension,AccurevSCMExtensionDescriptor> all() {
        return Jenkins.getInstance().getDescriptorList(AccurevSCMExtension.class);
    }
}
