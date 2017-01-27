package jenkins.plugins.accurev;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.init.Initializer;
import hudson.model.Project;
import hudson.plugins.accurev.AccurevSCM;
import jenkins.model.Jenkins;
import jenkins.plugins.accurev.util.UUIDUtils;

import java.util.logging.Logger;

import static hudson.init.InitMilestone.COMPLETED;
import static hudson.init.InitMilestone.EXTENSIONS_AUGMENTED;

/**
 * Initialized by josp on 21/09/16.
 */
@SuppressWarnings("unused") // Used for initialization/migration purpose
public class AccurevPlugin {
    private static final Logger LOGGER = Logger.getLogger(AccurevPlugin.class.getName());

    /**
     * We need ensure that migrator will run after jobs are loaded
     * Launches migration after plugin and jobs already initialized.
     * Expected milestone: @Initializer(after = JOB_LOADED)
     *
     * @throws Exception Exceptions
     */
    @Initializer(after = EXTENSIONS_AUGMENTED, before = COMPLETED)
    public static void migrateJobsToServerUUID() throws Exception {
        final Jenkins jenkins = Jenkins.getInstance();
        boolean changed = false;
        AccurevSCM.AccurevSCMDescriptor descriptor = Jenkins.getInstance().getDescriptorByType(AccurevSCM.AccurevSCMDescriptor.class);
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
     * We need ensure that migrator will after Extensions are augmented
     * Launches migration if servers still uses username and password
     * Expected milestone: @Initializer(after = EXTENSIONS_AUGMENTED)
     *
     * @throws Exception Exceptions
     */
    @Initializer(after = EXTENSIONS_AUGMENTED)
    public static void migrateServersToCredentials() throws Exception {
        final Jenkins jenkins = Jenkins.getInstance();
        boolean changed = false;
        AccurevSCM.AccurevSCMDescriptor descriptor = Jenkins.getInstance().getDescriptorByType(AccurevSCM.AccurevSCMDescriptor.class);
        boolean migratedCredentials = false;
        for (AccurevSCM.AccurevServer server : descriptor.getServers()) {
            if (server.migrateCredentials()) changed = true;
        }
        if (changed) SystemCredentialsProvider.getInstance().save();
    }
}
