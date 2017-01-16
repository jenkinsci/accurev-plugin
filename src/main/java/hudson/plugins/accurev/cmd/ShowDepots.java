package hudson.plugins.accurev.cmd;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.XmlParserFactory;
import hudson.plugins.accurev.config.AccurevServerConfig;
import hudson.plugins.accurev.extensions.impl.AccurevDepot;
import hudson.plugins.accurev.parsers.xml.ParseShowDepots;
import hudson.util.ArgumentListBuilder;
import jenkins.model.Jenkins;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.util.Map;
import java.util.logging.Logger;

public class ShowDepots extends Command {
    public static final Logger LOGGER = Logger.getLogger(ShowDepots.class.getName());

    public static Map<String, AccurevDepot> getDepots(final AccurevServerConfig server) throws IOException {

        final ArgumentListBuilder cmd = new ArgumentListBuilder();

        cmd.add("show");
        addServer(cmd, server);
        cmd.add("-fx");
        cmd.add("depots");

        Jenkins jenkins = Jenkins.getInstance();
        TaskListener listener = TaskListener.NULL;
        Launcher launcher = jenkins.createLauncher(listener);
        EnvVars accurevEnv = new EnvVars();
        XmlPullParserFactory parser = XmlParserFactory.getFactory();
        if (parser == null) throw new IOException("No XML Parser");
        return AccurevLauncher.runCommand("show depots command", launcher, cmd, null,
                accurevEnv, jenkins.getRootPath(), listener, LOGGER, parser, new ParseShowDepots(), server);
    }
}
