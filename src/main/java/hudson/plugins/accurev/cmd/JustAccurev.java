package hudson.plugins.accurev.cmd;

import hudson.util.ArgumentListBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Logger;

public class JustAccurev {

    private static final Logger logger = Logger.getLogger(JustAccurev.class.getName());

    public static boolean justAccuRev(String osSpecificValue) {
        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add(osSpecificValue);

        ProcessBuilder processBuilder = new ProcessBuilder(cmd.toList());
        processBuilder.redirectErrorStream(true);

        Process accurevprocess = null;
        try {
            accurevprocess = processBuilder.start();
        } catch (IOException e) {
            return false;
        }
        try (InputStream stdout = accurevprocess.getInputStream()) {
            accurevprocess.waitFor();
            return accurevprocess.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }

    }
}
