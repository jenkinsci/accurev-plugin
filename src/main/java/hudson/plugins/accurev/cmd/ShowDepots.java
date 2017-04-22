package hudson.plugins.accurev.cmd;

import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;

import org.xmlpull.v1.XmlPullParserFactory;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.util.ArgumentListBuilder;
import jenkins.model.Jenkins;

import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.plugins.accurev.XmlParserFactory;
import hudson.plugins.accurev.parsers.xml.ParseShowDepots;

@Deprecated
public class ShowDepots extends Command {

    public static List<String> getDepots(//
                                         final AccurevServer server,
                                         final Logger descriptorLogger
    ) throws IOException {


        Jenkins jenkins = Jenkins.getInstance();
        TaskListener listener = TaskListener.NULL;
        Launcher launcher = jenkins.createLauncher(listener);
        EnvVars accurevEnv = new EnvVars();

        final ArgumentListBuilder cmd = new ArgumentListBuilder();
        cmd.add("show");
        addServer(cmd, server);
        cmd.add("-fx");
        cmd.add("depots");

        XmlPullParserFactory parser = XmlParserFactory.getFactory();
        if (parser == null) throw new IOException("No XML Parser");
        return AccurevLauncher.runCommand("show depots command", "", launcher, cmd, null,
            accurevEnv, jenkins.getRootPath(), listener, descriptorLogger, parser, new ParseShowDepots(), null);
    }
}
