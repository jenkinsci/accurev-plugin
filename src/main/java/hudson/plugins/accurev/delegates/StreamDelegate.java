package hudson.plugins.accurev.delegates;

import hudson.model.Job;
import hudson.model.Run;
import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.AccurevStream;
import hudson.plugins.accurev.CheckForChanges;
import hudson.plugins.accurev.cmd.ShowStreams;
import hudson.plugins.accurev.parsers.output.ParseAccuRevVersion;
import hudson.scm.PollingResult;
import hudson.util.ArgumentListBuilder;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.Map;
import java.util.logging.Logger;

/** @author raymond */
public class StreamDelegate extends AbstractModeDelegate {

  private static final Logger logger = Logger.getLogger(StreamDelegate.class.getName());

  public StreamDelegate(AccurevSCM scm) {
    super(scm);
  }

  @Override
  protected boolean checkout(Run<?, ?> build, File changeLogFile)
      throws IOException, InterruptedException {
    return true;
  }

  @Override
  protected String getPopulateFromMessage() {
    return "from stream";
  }

  @Override
  protected String getPopulateStream() {
    return localStream;
  }

  @Override
  protected boolean isSteamColorEnabled() {
    return true;
  }

  @Override
  protected String getStreamColorStream() {
    return localStream;
  }

  @Override
  protected String getStreamColor() {
    return "#FFFFFF";
  }

  @Override
  protected PollingResult checkForChanges(Job<?, ?> project)
      throws IOException, InterruptedException {
    final Run<?, ?> lastBuild = project.getLastBuild();
    if (lastBuild == null) {
      listener.getLogger().println("Project has never been built");
      return PollingResult.BUILD_NOW;
    }
    final Date buildDate = lastBuild.getTimestamp().getTime();
    try {
      localStream = scm.getPollingStream(project, listener);
    } catch (IllegalArgumentException ex) {
      listener.getLogger().println(ex.getMessage());
      return PollingResult.NO_CHANGES;
    }
    final Map<String, AccurevStream> streams =
        ShowStreams.getStreams(
            scm, localStream, server, accurevEnv, jenkinsWorkspace, listener, launcher);
    if (streams == null) {
      listener
          .getLogger()
          .println("Could not retrieve any Streams from AccuRev, please check credentials");
      return PollingResult.NO_CHANGES;
    }
    AccurevStream stream = streams.get(localStream);

    if (stream == null) {
      listener
          .getLogger()
          .println("Tried to find '" + localStream + "' Stream, could not found it.");
      return PollingResult.NO_CHANGES;
    }
    // command to get version of accurev
    final ArgumentListBuilder cmd = new ArgumentListBuilder();
    String ACCUREV_VERSION =
        AccurevLauncher.runCommand(
            "Accurev version command",
            scm.getAccurevTool(),
            launcher,
            cmd,
            null,
            accurevEnv,
            jenkinsWorkspace,
            listener,
            logger,
            new ParseAccuRevVersion(),
            null);
    listener
        .getLogger()
        .println( //
            "Accurev Client Version: " + ACCUREV_VERSION);
    int version = Integer.parseInt(ACCUREV_VERSION.substring(0, ACCUREV_VERSION.indexOf(".")));
    if (version < 7) {
      listener
          .getLogger()
          .println( //
              "Upgrade AccuRev Client for improved performance");
    }
    // There may be changes in a parent stream that we need to factor in.
    do {
      if (CheckForChanges.checkStreamForChanges(
          server,
          accurevEnv,
          jenkinsWorkspace,
          listener,
          launcher,
          stream,
          buildDate,
          logger,
          scm,
          version)) {
        return PollingResult.BUILD_NOW;
      }
      stream = stream.getParent();
    } while (stream != null
        && stream.isReceivingChangesFromParent()
        && !scm.isIgnoreStreamParent());
    return PollingResult.NO_CHANGES;
  }
}
