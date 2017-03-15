package jenkins.plugins.accurev.util;

import hudson.FilePath;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.TaskListener;
import jenkins.model.Jenkins;
import jenkins.plugins.accurev.AccurevClient;

import java.util.Date;

/**
 * Initialized by josep on 05-03-2017.
 */
public class AccurevUtils {
    private static final long MILLIS_PER_SECOND = 1000L;
    AccurevClient accurev;
    TaskListener listener;

    public AccurevUtils(TaskListener listener, AccurevClient accurev) {
        this.accurev = accurev;
        this.listener = listener;
    }

    public static Node workspaceToNode(FilePath workspace) {
        Computer computer = workspace.toComputer();
        Node node = null;
        if (computer != null) node = computer.getNode();
        return node != null ? node : Jenkins.getInstance();

    }

    public static String getRootPath(FilePath workspace) {
        Node n = workspaceToNode(workspace);
        String path = null;
        FilePath filePath = null;
        if (null != n) filePath = n.getRootPath();
        if (null != filePath) path = filePath.getRemote();
        return path;
    }

    public static String cleanAccurevPath(String path) {
        path = path.replace("\\", "/");
        if (path.startsWith("/./")) {
            path = path.substring(3);
        }
        return path;
    }

    /**
     * Converts an Accurev timestamp into a {@link Date}
     *
     * @param timeInSeconds The accurev timestamp.
     * @return A {@link Date} set to the time for the accurev timestamp.
     */
    public static Date convertAccurevTimestamp(String timeInSeconds) {
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
