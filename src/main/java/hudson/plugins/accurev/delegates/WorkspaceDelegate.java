package hudson.plugins.accurev.delegates;

import hudson.model.Job;
import hudson.model.Run;
import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.AccurevStream;
import hudson.plugins.accurev.AccurevWorkspace;
import hudson.plugins.accurev.RemoteWorkspaceDetails;
import hudson.plugins.accurev.XmlParserFactory;
import hudson.plugins.accurev.cmd.Command;
import hudson.plugins.accurev.cmd.ShowStreams;
import hudson.plugins.accurev.delegates.Relocation.RelocationOption;
import hudson.plugins.accurev.parsers.xml.ParseShowWorkspaces;
import hudson.scm.PollingResult;
import hudson.util.ArgumentListBuilder;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.apache.commons.lang.StringUtils;
import org.xmlpull.v1.XmlPullParserFactory;

/** @author raymond */
public class WorkspaceDelegate extends ReftreeDelegate {

  private static final Logger logger = Logger.getLogger(WorkspaceDelegate.class.getName());

  public WorkspaceDelegate(AccurevSCM scm) {
    super(scm);
  }

  @Override
  public String getSCMRefTree() {
    return null;
  }

  @Override
  protected PollingResult checkForChanges(Job<?, ?> project)
      throws IOException, InterruptedException, IllegalArgumentException {
    localStream = scm.getPollingStream(project, listener);
    return super.checkForChanges(project);
  }

  @Override
  protected Relocation checkForRelocation() throws IOException, InterruptedException {
    String depot = scm.getDepot();
    String _accurevWorkspace = scm.getWorkspace();
    Map<String, AccurevWorkspace> workspaces = getWorkspaces();
    Map<String, AccurevStream> streams =
        ShowStreams.getStreams(
            scm, _accurevWorkspace, server, accurevEnv, jenkinsWorkspace, listener, launcher);

    if (workspaces == null) {
      throw new IllegalArgumentException("Cannot determine workspace configuration information");
    }
    if (!workspaces.containsKey(_accurevWorkspace)) {
      throw new IllegalArgumentException("The specified workspace does not appear to exist!");
    }
    AccurevWorkspace accurevWorkspace = workspaces.get(_accurevWorkspace);
    if (!depot.equals(accurevWorkspace.getDepot())) {
      throw new IllegalArgumentException(
          "The specified workspace, "
              + _accurevWorkspace
              + ", is based in the depot "
              + accurevWorkspace.getDepot()
              + " not "
              + depot);
    }

    if (scm.isIgnoreStreamParent()) {
      if (!streams.isEmpty()) {
        AccurevStream workspaceStream = streams.values().iterator().next();
        accurevWorkspace.setStream(workspaceStream);
        String workspaceBasisStream = workspaceStream.getBasisName();
        if (streams.containsKey(workspaceBasisStream)) {
          workspaceStream.setParent(streams.get(workspaceBasisStream));
        } else {
          Map<String, AccurevStream> workspaceBasis =
              ShowStreams.getStreams(
                  scm,
                  workspaceBasisStream,
                  server,
                  accurevEnv,
                  jenkinsWorkspace,
                  listener,
                  launcher);
          if (workspaceBasis == null) {
            throw new IllegalArgumentException("Could not determine the workspace basis stream");
          }
          workspaceStream.setParent(workspaceBasis.get(workspaceBasisStream));
        }
      } else {
        throw new IllegalArgumentException("Workspace stream not found " + _accurevWorkspace);
      }
    } else {
      for (AccurevStream accurevStream : streams.values()) {
        if (accurevWorkspace.getStreamNumber().equals(accurevStream.getNumber())) {
          accurevWorkspace.setStream(accurevStream);
          break;
        }
      }
    }

    final RemoteWorkspaceDetails remoteDetails = getRemoteWorkspaceDetails();

    List<RelocationOption> relocationOptions = new ArrayList<>();
    for (WorkspaceRelocation workspaceRelocationvalue : WorkspaceRelocation.values()) {
      if (workspaceRelocationvalue.isRequired(accurevWorkspace, remoteDetails, localStream)) {
        relocationOptions.add(workspaceRelocationvalue);
      }
    }
    return new Relocation(
        relocationOptions,
        remoteDetails.getHostName(),
        accurevWorkingSpace.getRemote(),
        localStream);
  }

  /**
   * Builds a command which gets executed and retrieves the following return data
   *
   * @return Map with Workspace name as key and Workspace Object as value.
   * @throws IOException Failed to execute command or Parse data.
   */
  private Map<String, AccurevWorkspace> getWorkspaces() throws IOException {
    listener.getLogger().println("Getting a list of workspaces...");
    String depot = scm.getDepot();
    final ArgumentListBuilder cmd = new ArgumentListBuilder();
    cmd.add("show");
    Command.addServer(cmd, server);
    cmd.add("-fx");
    cmd.add("-p");
    cmd.add(depot);
    cmd.add("wspaces");
    XmlPullParserFactory parser = XmlParserFactory.getFactory();
    if (parser == null) {
      throw new IOException("No XML Parser");
    }
    return AccurevLauncher.runCommand(
        "Show workspaces command",
        scm.getAccurevTool(),
        launcher,
        cmd,
        scm.getOptionalLock(jenkinsWorkspace),
        accurevEnv,
        jenkinsWorkspace,
        listener,
        logger,
        parser,
        new ParseShowWorkspaces(),
        null);
  }

  @Override
  protected boolean validateCheckout(Run<?, ?> build) {
    String workspace = scm.getWorkspace();
    if (StringUtils.isEmpty(workspace)) {
      listener.fatalError("Must specify a workspace");
      return false;
    }
    return true;
  }

  @Override
  protected String getPopulateFromMessage() {
    return "from workspace";
  }

  @Override
  protected ArgumentListBuilder getRelocateCommand() {
    ArgumentListBuilder chwscmd = new ArgumentListBuilder();
    chwscmd.add("chws");
    Command.addServer(chwscmd, server);
    chwscmd.add("-w");
    chwscmd.add(scm.getWorkspace());
    return chwscmd;
  }

  @Override
  protected boolean isSteamColorEnabled() {
    return true;
  }

  @Override
  protected String getStreamColorStream() {
    return scm.getWorkspace();
  }

  @Override
  protected String getStreamColor() {
    return "#FFEBB4";
  }

  @Override
  protected String getChangeLogStream() {
    return scm.getWorkspace();
  }

  @Override
  protected void buildEnvVarsCustom(Run<?, ?> build, Map<String, String> env) {
    env.put("ACCUREV_WORKSPACE", scm.getWorkspace());
  }

  private enum WorkspaceRelocation implements RelocationOption {
    HOST {
      @Override
      protected boolean isRequired(
          AccurevWorkspace accurevWorkspace,
          RemoteWorkspaceDetails remoteDetails,
          String localStream) {
        return !accurevWorkspace.getHost().equalsIgnoreCase(remoteDetails.getHostName());
      }

      public void appendCommand(ArgumentListBuilder cmd, Relocation relocation) {
        cmd.add("-m");
        cmd.add(relocation.getNewHost());
      }
    },
    STORAGE {
      @Override
      protected boolean isRequired(
          AccurevWorkspace accurevWorkspace,
          RemoteWorkspaceDetails remoteDetails,
          String localStream) {
        String oldStorage =
            accurevWorkspace
                .getStorage()
                .replace("/", remoteDetails.getFileSeparator())
                .replace("\\", remoteDetails.getFileSeparator());
        return !new File(oldStorage).equals(new File(remoteDetails.getPath()));
      }

      public void appendCommand(ArgumentListBuilder cmd, Relocation relocation) {
        cmd.add("-l");
        cmd.add(relocation.getNewPath());
      }
    },
    REPARENT {
      @Override
      protected boolean isRequired(
          AccurevWorkspace accurevWorkspace,
          RemoteWorkspaceDetails remoteDetails,
          String localStream) {
        return !localStream.equals(accurevWorkspace.getStream().getParent().getName());
      }

      @Override
      public boolean isPopRequired() {
        return false;
      }

      public void appendCommand(ArgumentListBuilder cmd, Relocation relocation) {
        cmd.add("-b");
        cmd.add(relocation.getNewParent());
      }
    };

    public boolean isPopRequired() {
      return true;
    }

    protected abstract boolean isRequired(
        AccurevWorkspace accurevWorkspace,
        RemoteWorkspaceDetails remoteDetails,
        String localStream);
  }
}
