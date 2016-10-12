package hudson.plugins.accurev;

import hudson.FilePath;
import hudson.plugins.accurev.cmd.JustAccurev;
import hudson.remoting.VirtualChannel;
import org.jenkinsci.remoting.RoleChecker;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

@SuppressWarnings("serial")
public final class FindAccurevClientExe implements FilePath.FileCallable<String> {

    private static final Logger logger = Logger.getLogger(FindAccurevClientExe.class.getName());

    /**
     * The default search paths for Windows clients.
     */
    private static final List<String> DEFAULT_WIN_CMD_LOCATIONS = Arrays.asList(
            "C:\\opt\\accurev\\bin\\accurev.exe",
            "C:\\Program Files\\AccuRev\\bin\\accurev.exe",
            "C:\\Program Files (x86)\\AccuRev\\bin\\accurev.exe");
    /**
     * The default search paths for *nix clients
     */
    private static final List<String> DEFAULT_NIX_CMD_LOCATIONS = Arrays.asList(
            "/usr/local/bin/accurev",
            "/usr/bin/accurev",
            "/bin/accurev",
            "/local/bin/accurev",
            "/opt/accurev/bin/accurev",
            "/Applications/AccuRev/bin/accurev");

    public FindAccurevClientExe() {}

    private static String getExistingPath(List<String> paths) {
        for (final String path : paths) {
            if (new File(path).exists()) {
                return path;
            }
        }
        return "";
    }

    /**
     * {@inheritDoc}
     */
    public String invoke(File f, VirtualChannel channel) throws IOException {
        String accurevPath;
        String accurevBin = "";
        String accurevBinName = "accurev";

        if (System.getenv("ACCUREV_BIN") != null) {
            accurevBin = System.getenv("ACCUREV_BIN");
            if (new File(accurevBin).exists() && new File(accurevBin).isDirectory()) {
                logger.fine("The ACCUREV_BIN environment variable was set to: " + accurevBin);
            } else {
                throw new FileNotFoundException("The ACCUREV_BIN environment variable was set but the path it was set to does not exist OR it is not a directory. Please correct the path or unset the variable. ACCUREV_BIN was set to: " + accurevBin);
            }
        }

        if (System.getProperty("accurev.bin") != null) {
            accurevBin = System.getProperty("accurev.bin");
            if (new File(accurevBin).exists() && new File(accurevBin).isDirectory()) {
                logger.fine("The accurev.bin system property was set to: " + accurevBin);
            } else {
                throw new FileNotFoundException("The accurev.bin system property was set but the path it was set to does not exist OR it is not a directory. Please correct the path or unset the property. 'accurev.bin' was set to: " + accurevBin);
            }
        }

        if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
            // we are running on windows
            accurevBinName = "accurev.exe";
            if (!JustAccurev.justAccuRev(accurevBin + File.separator + accurevBinName)) {
                accurevPath = getExistingPath(DEFAULT_WIN_CMD_LOCATIONS);
            } else {
                accurevPath = accurevBin + File.separator + accurevBinName;
            }
        } else {
            // we are running on *nix
            if (!JustAccurev.justAccuRev(accurevBin + File.separator + accurevBinName)) {
                accurevPath = getExistingPath(DEFAULT_NIX_CMD_LOCATIONS);
            } else {
                accurevPath = accurevBin + File.separator + accurevBinName;
            }
        }

        if (accurevPath.isEmpty()) {
            // if we still don't have a path to the accurev client let's try the system path
            if (JustAccurev.justAccuRev(accurevBinName)) {
                logger.fine("Using the AccuRev client we found on the system path.");
                accurevPath = accurevBinName;
            } else {
                throw new RuntimeException("AccuRev binary is not found or not set in the environment's path.");
            }
        }

        logger.fine("Path to the AccuRev client: " + accurevPath);
        return accurevPath;
    }

    @Override
    public void checkRoles(RoleChecker roleChecker) throws SecurityException {
        //TODO: Implement Role check
    }
}
