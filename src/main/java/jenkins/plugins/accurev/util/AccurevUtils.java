package jenkins.plugins.accurev.util;

import static org.apache.commons.lang.time.DateUtils.MILLIS_PER_SECOND;

import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Node;
import java.util.Date;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;

public class AccurevUtils {

  public static String cleanAccurevPath(String str) {
    return str.replace("\\", "/").replaceFirst("^/[.]/", "");
  }

  @CheckForNull
  public static Node workspaceToNode(FilePath workspace) {
    Computer computer = workspace.toComputer();
    Node node = null;
    if (null != computer) {
      node = computer.getNode();
    }
    return null != node ? node : Jenkins.getInstance();
  }

  public static String getRootPath(FilePath workspace) {
    Node n = workspaceToNode(workspace);
    String path = null;
    FilePath filePath = null;
    if (null != n) {
      filePath = n.getRootPath();
    }
    if (null != filePath) {
      path = filePath.getRemote();
    }
    return path;
  }

  /**
   * Converts an Accurev timestamp into a {@link Date}
   *
   * @param timeInSeconds The accurev timestamp.
   * @return A {@link Date} set to the time for the accurev timestamp.
   */
  public static Date convertAccurevTimestamp(@CheckForNull String timeInSeconds) {
    if (timeInSeconds == null) {
      return null;
    }
    try {
      final long time = Long.parseLong(timeInSeconds);
      final long date = time * MILLIS_PER_SECOND;
      return new Date(date);
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
