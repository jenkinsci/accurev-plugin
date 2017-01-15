package hudson.plugins.accurev;

import hudson.init.Initializer;

import java.util.logging.Logger;

import static hudson.init.InitMilestone.COMPLETED;
import static hudson.init.InitMilestone.JOB_LOADED;

/**
 * @author josp
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
    @Initializer(after = JOB_LOADED, before = COMPLETED)
    public static void initializers() throws Exception {
        // Possible Migration
    }
}
