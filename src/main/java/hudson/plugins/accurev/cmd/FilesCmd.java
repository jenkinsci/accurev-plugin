package hudson.plugins.accurev.cmd;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevElement;
import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.XmlParserFactory;
import hudson.plugins.accurev.parsers.xml.ParseFiles;
import hudson.util.ArgumentListBuilder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import org.xmlpull.v1.XmlPullParserFactory;

public class FilesCmd extends Command {

  private static final Logger logger = Logger.getLogger(Login.class.getName());

  public static List<AccurevElement> checkFiles(
      final AccurevSCM scm,
      final AccurevSCM.AccurevServer server,
      final EnvVars accurevEnv,
      final FilePath workspace,
      final TaskListener listener,
      final Launcher launcher,
      FilePath file)
      throws IOException {
    final String commandDescription = "files command";
    final ArgumentListBuilder cmd = new ArgumentListBuilder();
    cmd.add("files");
    addServer(cmd, server);
    cmd.add("-fx");
    cmd.add("-s", scm.getStream());
    cmd.add("-l", file.getRemote());

    // returns username
    final ArrayList<AccurevElement> list = new ArrayList<>(1);
    XmlPullParserFactory parser = XmlParserFactory.getFactory();
    if (parser == null) {
      throw new IOException("No XML Parser");
    }
    final Boolean filesFound =
        AccurevLauncher.runCommand(
            commandDescription,
            scm.getAccurevTool(),
            launcher,
            cmd,
            scm.getOptionalLock(workspace),
            accurevEnv,
            workspace,
            listener,
            logger,
            parser,
            new ParseFiles(),
            list);
    if (filesFound == null) {
      throw new IOException("FilesCmd command failed.");
    }
    return list;
  }
}
