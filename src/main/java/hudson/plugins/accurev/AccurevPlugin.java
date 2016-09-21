package hudson.plugins.accurev;

import hudson.Plugin;
import hudson.init.Initializer;
import hudson.model.Project;
import jenkins.model.Jenkins;

import java.io.IOException;

import static hudson.init.InitMilestone.COMPLETED;
import static hudson.init.InitMilestone.JOB_LOADED;

/**
 * Created by josp on 21/09/16.
 */
public class AccurevPlugin extends Plugin {
    /**
     * Launches migration after plugin and jobs already initialized.
     * Expected milestone: @Initializer(after = JOB_LOADED)
     *
     * @throws Exception Exceptions?
     * @see #initializers()
     */
    public static void runMigrator() throws Exception {
        final Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            throw new IOException("Jenkins instance is not ready");
        }
        AccurevSCM.AccurevSCMDescriptor descriptor = jenkins.getDescriptorByType(AccurevSCM.AccurevSCMDescriptor.class);
        if (descriptor.getMigrate() != 1) {
            for (Project<?, ?> p : jenkins.getAllItems(Project.class)) {
                if (p.getScm() instanceof AccurevSCM) {
                    AccurevSCM scm = (AccurevSCM) p.getScm();
                    if (UUIDUtils.isNotValid(scm.getServerName())) {
                        String uuid = descriptor.getServer(scm.getServerName()).getUUID();
                        scm.setServerName(uuid);
                        p.save();
                    }
                }
            }
            descriptor.setMigrate(1);
            descriptor.save();
        }

    }

    /**
     * We need ensure that migrator will run after jobs are loaded
     *
     * @throws Exception Exceptions?
     */
    @Initializer(after = JOB_LOADED, before = COMPLETED)
    public static void initializers() throws Exception {
        runMigrator();
    }
}
