package hudson.plugins.accurev.cmd;

import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.util.ArgumentListBuilder;
import org.apache.commons.lang.StringUtils;

import java.nio.charset.Charset;

public class Command {
    /**
     * Adds the server reference to the Arguments list.
     *
     * @param cmd    The accurev command line.
     * @param server The Accurev server details.
     */
    public static void addServer(ArgumentListBuilder cmd, AccurevServer server) {
        if (null != server && null != server.getHost() && StringUtils.isNotBlank(server.getHost())) {
            cmd.add("-H");
            if (server.getPort() != 0) {
                cmd.add(server.getHost() + ":" + server.getPort());
            } else {
                cmd.add(server.getHost());
            }
        }
    }
}
