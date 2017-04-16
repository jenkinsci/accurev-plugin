package jenkins.plugins.accurev;

import static hudson.init.InitMilestone.COMPLETED;
import static hudson.init.InitMilestone.EXTENSIONS_AUGMENTED;
import static hudson.init.InitMilestone.JOB_LOADED;

import java.util.logging.Logger;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;

import hudson.init.Initializer;
import hudson.model.Project;
import jenkins.model.Jenkins;

import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.AccurevSCM.AccurevSCMDescriptor;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;

/**
 * Initialized by josp on 21/09/16.
 */
@SuppressWarnings("unused") // Used for initialization/migration purpose
public class AccurevPlugin {
    private static final Logger LOGGER = Logger.getLogger(AccurevPlugin.class.getName());

    /**
     * We need ensure that migrator will after Extensions are augmented
     * Launches migration if servers still uses username and password
     * Expected milestone: @Initializer(after = EXTENSIONS_AUGMENTED)
     *
     * @throws Exception Exceptions
     */
    @Initializer(after = EXTENSIONS_AUGMENTED, before = JOB_LOADED)
    public static void migrateServersToCredentials() throws Exception {
        boolean changed = false;
        AccurevSCMDescriptor descriptor = AccurevSCM.configuration();
        for (AccurevServer server : descriptor.getServers()) {
            if (server.migrateCredentials()) changed = true;
        }
        if (changed) {
            descriptor.save();
            SystemCredentialsProvider.getInstance().save();
        }
    }

    /**
     * We need ensure that migrator will run after jobs are loaded
     * Launches migration after plugin and jobs already initialized.
     * Expected milestone: @Initializer(after = JOB_LOADED)
     *
     * @throws Exception Exceptions
     */
    @SuppressWarnings("deprecation")
    @Initializer(after = JOB_LOADED, before = COMPLETED)
    public static void migrateServersToJobs() throws Exception {
        final Jenkins jenkins = Jenkins.getInstance();
        boolean changed = false;
        AccurevSCMDescriptor descriptor = AccurevSCM.configuration();
        for (Project<?, ?> p : jenkins.getAllItems(Project.class)) {
            if (p.getScm() instanceof AccurevSCM) {
                AccurevSCM scm = (AccurevSCM) p.getScm();
                AccurevServer server = scm.getServer();
                if (server != null) {
                    changed = true;
                    scm.migrate(p);
                    p.save();
                }
            }
        }
        if (changed) {
            descriptor.setServers(null);
            descriptor.save();
        }
    }
}
