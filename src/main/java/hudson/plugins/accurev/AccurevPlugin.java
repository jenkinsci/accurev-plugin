package hudson.plugins.accurev;

import hudson.Plugin;
import hudson.init.Initializer;
import hudson.model.Project;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.logging.Logger;

import static hudson.init.InitMilestone.COMPLETED;
import static hudson.init.InitMilestone.JOB_LOADED;

/**
 * Created by josp on 21/09/16.
 */
public class AccurevPlugin extends Plugin {
    private static final Logger LOGGER = Logger.getLogger(AccurevPlugin.class.getName());

    /**
     * Launches migration after plugin and jobs already initialized.
     * Expected milestone: @Initializer(after = JOB_LOADED)
     *
     * @throws Exception Exceptions?
     * @see #initializers()
     */
    public static void runMigration() throws Exception {
        final Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) {
            throw new IOException("Jenkins instance is not ready");
        }
        boolean changed = false;
        AccurevSCM.AccurevSCMDescriptor descriptor = jenkins.getDescriptorByType(AccurevSCM.AccurevSCMDescriptor.class);
        for (Project<?, ?> p : jenkins.getAllItems(Project.class)) {
            if (p.getScm() instanceof AccurevSCM) {
                AccurevSCM scm = (AccurevSCM) p.getScm();
                String serverUUID = scm.getServerUUID();
                if (UUIDUtils.isNotValid(serverUUID) || descriptor.getServer(serverUUID) == null) {
                    AccurevSCM.AccurevServer server = descriptor.getServer(scm.getServerName());
                    if (server == null) {
                        LOGGER.warning("No server found with that name, Project: " + p.getName() + " Server Name: " + scm.getServerName());
                    } else {
                        changed = true;
                        String uuid = server.getUUID();
                        scm.setServerUUID(uuid);
                        p.save();
                    }
                }
            }
        }
        if (changed) descriptor.save();

    }

    /**
     * We need ensure that migrator will run after jobs are loaded
     *
     * @throws Exception Exceptions?
     */
    @Initializer(after = JOB_LOADED, before = COMPLETED)
    public static void initializers() throws Exception {
        runMigration();
    }
}
