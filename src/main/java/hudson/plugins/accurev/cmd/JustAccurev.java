package hudson.plugins.accurev.cmd;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class JustAccurev {

    private static final Logger logger = Logger.getLogger(JustAccurev.class.getName());

    public static boolean justAccuRev(String osSpecificValue) {
        List<String> accurevCommand = new ArrayList<>();
        accurevCommand.add(osSpecificValue);

        ProcessBuilder processBuilder = new ProcessBuilder(accurevCommand);
        processBuilder.redirectErrorStream(true);

        Process accurevprocess;
        InputStream stdout = null;
        try {
            accurevprocess = processBuilder.start();
            stdout = accurevprocess.getInputStream();
            accurevprocess.waitFor();
            return accurevprocess.exitValue() == 0;
        } catch (InterruptedException e) {
            return false;
        } catch (IOException e) {
            return false;
        } finally {
            try {
                if (stdout != null) {
                    stdout.close();
                }

            } catch (IOException e) {
                logger.warning("Failed to close std out");
            }
        }


    }
}
