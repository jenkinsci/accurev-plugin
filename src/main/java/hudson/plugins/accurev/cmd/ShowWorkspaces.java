package hudson.plugins.accurev.cmd;

import hudson.EnvVars;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.plugins.accurev.AccurevStream;
import hudson.plugins.accurev.AccurevStream.StreamType;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import jenkins.model.Jenkins;

import org.apache.commons.lang.StringUtils;

/**
 * Class will be used for to get all workspaces for a given depot and also will retrieve the stream name from the workspace.
 * 
 * @author DLenka
 *
 */
public class ShowWorkspaces extends Command {


    public static List<AccurevStream> getAllWorkspaces(AccurevServer server, String depot) throws IOException {
        if (StringUtils.isEmpty(depot))
            return null;
        Jenkins jenkins = Jenkins.getInstance();
        return ShowStreams.getAllStreams(null, server, depot, null, new EnvVars(), jenkins.getRootPath(), TaskListener.NULL,
                        jenkins.createLauncher(TaskListener.NULL))
                .values()
                .stream()
                .filter(s -> s.getType().equals(StreamType.WORKSPACE))
                .collect(Collectors.toList());
    }

    public static String getParentStreamOfWorkspace(AccurevServer server, String depot, String workspace) throws IOException {
        List<AccurevStream> accurevStreams = getAllWorkspaces(server, depot);
        return accurevStreams
                .stream()
                .collect(Collectors.toMap(AccurevStream::getName, AccurevStream::getBasisName))
                .get(workspace);
    }
}
