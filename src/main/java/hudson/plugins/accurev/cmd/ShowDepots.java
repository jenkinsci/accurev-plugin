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
import java.util.List;
import java.util.logging.Logger;

public class ShowDepots extends Command {

    public static List<String> getDepots(//
                                         final AccurevServer server,
                                         final String accurevPath,
                                         final Logger descriptorLogger
    ) {

        final List<String> depots = new ArrayList<>();
        final ArgumentListBuilder cmd = new ArgumentListBuilder();

        cmd.add(accurevPath);
        cmd.add("show");
        addServer(cmd, server);
        cmd.add("-fx");
        cmd.add("depots");

        Jenkins jenkins = Jenkins.getInstance();
        if (jenkins == null) return depots;

        StreamTaskListener listener = StreamTaskListener.fromStdout();
        Launcher launcher = jenkins.createLauncher(listener);
        depots.addAll(AccurevLauncher.runCommand("show depots command", launcher, cmd, null,
                null, jenkins.getRootPath(), listener, descriptorLogger, XmlParserFactory.getFactory(), new ParseShowDepots(), null));
        return depots;
    }
}
