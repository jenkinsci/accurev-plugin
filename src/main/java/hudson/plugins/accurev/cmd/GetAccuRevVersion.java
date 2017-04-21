package hudson.plugins.accurev.cmd;

import org.apache.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class GetAccuRevVersion {

    private static final Logger logger = Logger.getLogger(GetAccuRevVersion.class.getName());

    /**
     * Get current accuRev version by invoking the AccuRev CLI command
     *
     * @return String accuRevVersion
     */
    public static String getAccuRevVersion() {
        InputStreamReader input = null;
        BufferedReader reader = null;
        String accuRevVersion = null;
        try {
            Process process = Runtime.getRuntime().exec("accurev");
            process.waitFor();
            input = new InputStreamReader(process.getInputStream(), "UTF-8");
            reader = new BufferedReader(input);
            accuRevVersion = reader.readLine();
        } catch (InterruptedException e) {
            logger.info("InterruptedException occured to invoke accurev version command. " + e);
        } catch (IOException exe) {
            logger.info("IOException occured to invoke accurev version command. " + exe);
        } finally {
            try {
                if (input != null)
                    input.close();
                if (reader != null)
                    reader.close();
            } catch (IOException e) {
                logger.info("IOException occured close the resources . " + e);
            }

        }
        return accuRevVersion;
    }

}
