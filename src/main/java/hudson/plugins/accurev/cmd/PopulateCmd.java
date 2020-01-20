package hudson.plugins.accurev.cmd;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.plugins.accurev.parsers.output.ParsePopulate;
import hudson.util.ArgumentListBuilder;
import java.io.IOException;
import java.util.Date;
import java.util.StringTokenizer;
import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;

public class PopulateCmd extends Command {

  private static final Logger logger = Logger.getLogger(PopulateCmd.class.getName());

  private Date _startDateOfPopulate;

  /** @return Date */
  public Date get_startDateOfPopulate() {
    return (Date) _startDateOfPopulate.clone();
  }

  /**
   * @param scm Accurev SCm
   * @param launcher launcher
   * @param listener listener
   * @param server server
   * @param streamName stream Name
   * @param overwrite overwrite
   * @param fromMessage from Messge
   * @param workspace Accurev Workspace
   * @param accurevEnv Accurev Environment
   * @param files list of files to be populated
   * @return boolean
   * @throws IOException Handle it above
   */
  public boolean populate(
      AccurevSCM scm,
      Launcher launcher,
      TaskListener listener,
      AccurevServer server,
      String streamName,
      boolean overwrite,
      String fromMessage,
      FilePath workspace,
      EnvVars accurevEnv,
      FilePath files)
      throws IOException {
    listener.getLogger().println("Populating " + fromMessage + "...");
    final ArgumentListBuilder cmd = new ArgumentListBuilder();
    cmd.add("pop");
    addServer(cmd, server);

    if (streamName != null) {
      cmd.add("-v");
      cmd.add(streamName);

      if (scm.getSubPathOnly()) {
        cmd.add("-D");
      }
    }

    cmd.add("-L");
    cmd.add(workspace.getRemote());

    // Add the file containing list to be populated
    if (files != null) {
      cmd.add("-l");
      cmd.add(files.getRemote());
    }

    if (overwrite) {
      cmd.add("-O");
    }

    cmd.add("-R");
    if (StringUtils.isBlank(scm.getSubPath())) {
      cmd.add(".");
    } else {
      final StringTokenizer st = new StringTokenizer(scm.getSubPath(), ",");
      while (st.hasMoreElements()) {
        cmd.add(st.nextToken().trim());
      }
    }
    _startDateOfPopulate = new Date();
    final Boolean success =
        AccurevLauncher.runCommand(
            "Populate " + fromMessage + " command",
            scm.getAccurevTool(),
            launcher,
            cmd,
            scm.getOptionalLock(workspace),
            accurevEnv,
            workspace,
            listener,
            logger,
            new ParsePopulate(),
            listener.getLogger());
    if (success == null || !success) {
      return false;
    }
    listener.getLogger().println("Populate completed successfully.");
    return true;
  }
}
