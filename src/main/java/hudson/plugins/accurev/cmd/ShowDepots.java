package hudson.plugins.accurev.cmd;

import hudson.Launcher;
import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.plugins.accurev.XmlParserFactory;
import hudson.plugins.accurev.parsers.xml.ParseShowDepots;
import hudson.util.ArgumentListBuilder;
import hudson.util.StreamTaskListener;
import jenkins.model.Jenkins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class ShowDepots extends Command {

    public static List<String> getDepots(//
                                         final AccurevServer server,
                                         final Logger descriptorLogger
    ) {

        final ArgumentListBuilder cmd = new ArgumentListBuilder();

        cmd.add("show");
        addServer(cmd, server);
        cmd.add("-fx");
        cmd.add("depots");

        Jenkins jenkins = Jenkins.getActiveInstance();
        StreamTaskListener listener = StreamTaskListener.fromStdout();
        Launcher launcher = jenkins.createLauncher(listener);
        Map<String, String> accurevEnv = new HashMap<>();
        return AccurevLauncher.runCommand("show depots command", launcher, cmd, null,
                accurevEnv, jenkins.getRootPath(), listener, descriptorLogger, XmlParserFactory.getFactory(), new ParseShowDepots(), null);
    }
}
