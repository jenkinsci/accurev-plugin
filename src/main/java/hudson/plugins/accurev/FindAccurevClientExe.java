package hudson.plugins.accurev;

import hudson.FilePath;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.remoting.VirtualChannel;

import java.io.File;
import java.io.IOException;

@SuppressWarnings("serial")
final class FindAccurevClientExe implements FilePath.FileCallable<String> {

    private final AccurevServer server;

    public FindAccurevClientExe(AccurevServer server) {
        this.server = server;
    }

    private static String getExistingPath(String[] paths, String fallback) {
        for (final String path : paths) {
            if (new File(path).exists()) {
                return path;
            }
        }
        // just hope it's on the environment's path
        return fallback;
    }

    /**
     * {@inheritDoc}
     */
    public String invoke(File f, VirtualChannel channel) throws IOException {
        if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
            // we are running on windows
            return getExistingPath(server.getWinCmdLocations(), "accurev.exe");
        } else {
            // we are running on *nix
            return getExistingPath(server.getNixCmdLocations(), "accurev");
        }
    }
}
