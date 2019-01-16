package jenkins.plugins.accurev;

import static hudson.init.InitMilestone.COMPLETED;
import static hudson.init.InitMilestone.EXTENSIONS_AUGMENTED;
import static hudson.init.InitMilestone.JOB_LOADED;

import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import hudson.init.Initializer;
import hudson.model.Project;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.AccurevSCM.AccurevSCMDescriptor;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import jenkins.plugins.accurev.util.UUIDUtils;

/** Initialized by josp on 21/09/16. */
@SuppressWarnings("unused") // Used for initialization/migration purpose
public class AccurevPlugin {

  private static final Logger LOGGER = Logger.getLogger(AccurevPlugin.class.getName());

  /**
   * We need ensure that migrator will run after jobs are loaded Launches migration after plugin and
   * jobs already initialized. Expected milestone: @Initializer(after = JOB_LOADED)
   *
   * @throws Exception Exceptions
   */
  @Initializer(after = JOB_LOADED, before = COMPLETED)
  public static void migrateJobsToServerUUID() throws Exception {
    final Jenkins jenkins = Jenkins.get();
    boolean changed = false;
    AccurevSCMDescriptor descriptor = jenkins.getDescriptorByType(AccurevSCMDescriptor.class);
    for (Project<?, ?> p : jenkins.getAllItems(Project.class)) {
      if (p.getScm() instanceof AccurevSCM) {
        AccurevSCM scm = (AccurevSCM) p.getScm();
        String serverUUID = scm.getServerUUID();
        if (UUIDUtils.isNotValid(serverUUID) || descriptor.getServer(serverUUID) == null) {
          AccurevServer server = descriptor.getServer(scm.getServerName());
          if (server == null) {
            LOGGER.warning(
                "No server found with that name, Project: "
                    + p.getName()
                    + " Server Name: "
                    + scm.getServerName());
          } else {
            changed = true;
            String uuid = server.getUuid();
            scm.setServerUUID(uuid);
            p.save();
          }
        }
      }
    }
    if (changed) {
      descriptor.save();
    }
  }

  /**
   * We need ensure that migrator will after Extensions are augmented Launches migration if servers
   * still uses username and password Expected milestone: @Initializer(after = EXTENSIONS_AUGMENTED)
   *
   * @throws Exception Exceptions
   */
  @Initializer(after = EXTENSIONS_AUGMENTED)
  public static void migrateServersToCredentials() throws Exception {
    boolean changed = false;
    AccurevSCMDescriptor descriptor = AccurevSCM.configuration();
    boolean migratedCredentials = false;
    for (AccurevServer server : descriptor.getServers()) {
      if (server.migrateCredentials()) {
        changed = true;
      }
    }
    if (changed) {
      descriptor.save();
      SystemCredentialsProvider.getInstance().save();
    }
  }
}
