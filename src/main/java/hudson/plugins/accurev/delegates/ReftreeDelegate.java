package hudson.plugins.accurev.delegates;

import hudson.model.Job;
import hudson.model.Run;
import hudson.plugins.accurev.AccurevLauncher;
import hudson.plugins.accurev.AccurevReferenceTree;
import hudson.plugins.accurev.AccurevSCM;
import hudson.plugins.accurev.DetermineRemoteHostname;
import hudson.plugins.accurev.RemoteWorkspaceDetails;
import hudson.plugins.accurev.XmlConsolidateStreamChangeLog;
import hudson.plugins.accurev.XmlParserFactory;
import hudson.plugins.accurev.cmd.Command;
import hudson.plugins.accurev.cmd.Update;
import hudson.plugins.accurev.delegates.Relocation.RelocationOption;
import hudson.plugins.accurev.parsers.xml.ParseShowReftrees;
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
public class ReftreeDelegate extends AbstractModeDelegate {

  private static final Logger logger = Logger.getLogger(ReftreeDelegate.class.getName());
  protected boolean popRequired = false;
  private File updateLogFile;

  public ReftreeDelegate(AccurevSCM scm) {
    super(scm);
  }

  public String getSCMRefTree() {
    return scm.getReftree();
  }

  @Override
  protected PollingResult checkForChanges(Job<?, ?> project)
      throws IOException, InterruptedException {
    try {
      Relocation relocation = checkForRelocation();
      if (relocation.isRelocationRequired()) {
        listener.getLogger().println("Relocation required triggering build");
        return PollingResult.BUILD_NOW;
      } else {
        if (Update.hasChanges(
            scm, server, accurevEnv, accurevWorkingSpace, listener, launcher, getSCMRefTree())) {
          return PollingResult.BUILD_NOW;
        } else {
          return PollingResult.NO_CHANGES;
        }
      }
    } catch (IllegalArgumentException ex) {
      listener.fatalError(ex.getMessage());
      return PollingResult.NO_CHANGES;
    }
  }

  protected Relocation checkForRelocation() throws IOException, InterruptedException {
    final Map<String, AccurevReferenceTree> reftrees = getReftrees();
    String reftree = scm.getReftree();
    if (reftrees == null) {
      throw new IllegalArgumentException(
          "Cannot determine reference tree configuration information");
    }
    if (!reftrees.containsKey(reftree)) {
      throw new IllegalArgumentException("The specified reference tree does not appear to exist!");
    }
    AccurevReferenceTree accurevReftree = reftrees.get(reftree);
    if (!scm.getDepot().equals(accurevReftree.getDepot())) {
      throw new IllegalArgumentException(
          "The specified reference tree, "
              + reftree
              + ", is based in the depot "
              + accurevReftree.getDepot()
              + " not "
              + scm.getDepot());
    }

    RemoteWorkspaceDetails remoteDetails = getRemoteWorkspaceDetails();

    List<RelocationOption> relocationOptions = new ArrayList<>();

    for (RefTreeRelocation refTreeRelocation : RefTreeRelocation.values()) {
      if (refTreeRelocation.isRequired(accurevReftree, remoteDetails)) {
        relocationOptions.add(refTreeRelocation);
      }
    }
    return new Relocation(
        relocationOptions, remoteDetails.getHostName(), accurevWorkingSpace.getRemote(), null);
  }

  protected RemoteWorkspaceDetails getRemoteWorkspaceDetails() throws InterruptedException {
    try {
      return jenkinsWorkspace.act(new DetermineRemoteHostname(accurevWorkingSpace.getRemote()));
    } catch (IOException e) {
      e.printStackTrace(listener.getLogger());
      throw new IllegalArgumentException("Unable to validate reference tree host.");
    }
  }

  /**
   * Builds a command which gets executed and retrieves the following return data
   *
   * @return Map with Reference Tree name as key and Reference Tree Object as value.
   * @throws IOException Failed to execute command or Parse data.
   */
  private Map<String, AccurevReferenceTree> getReftrees() throws IOException {
    listener.getLogger().println("Getting a list of reference trees...");
    final ArgumentListBuilder cmd = new ArgumentListBuilder();
    cmd.add("show");
    Command.addServer(cmd, server);
    cmd.add("-fx");
    cmd.add("refs");
    XmlPullParserFactory parser = XmlParserFactory.getFactory();
    if (parser == null) {
      throw new IOException("No XML Parser");
    }
    return AccurevLauncher.runCommand(
        "Show ref trees command",
        scm.getAccurevTool(),
        launcher,
        cmd,
        scm.getOptionalLock(jenkinsWorkspace),
        accurevEnv,
        jenkinsWorkspace,
        listener,
        logger,
        parser,
        new ParseShowReftrees(),
        null);
  }

  @Override
  protected boolean checkout(Run<?, ?> build, File changeLogFile)
      throws IOException, InterruptedException {
    if (!validateCheckout(build)) {
      return false;
    }
    Relocation relocation = checkForRelocation();
    if (relocation.isRelocationRequired()) {
      if (!relocate(relocation)) {
        return false;
      }
      if (!populate(build, popRequired)) {
        return false;
      }
    }

    return doUpdate(changeLogFile);
  }

  private boolean doUpdate(File changeLogFile) throws IOException {
    updateLogFile = XmlConsolidateStreamChangeLog.getUpdateChangeLogFile(changeLogFile);
    return Update.performUpdate(
        scm,
        server,
        accurevEnv,
        accurevWorkingSpace,
        listener,
        launcher,
        getSCMRefTree(),
        updateLogFile);
  }

  @Override
  protected String getUpdateFileName() {
    return updateLogFile.getName();
  }

  protected String getPopulateFromMessage() {
    return "from reftree";
  }

  @Override
  protected String getPopulateStream() {
    return null;
  }

  private boolean relocate(Relocation relocation) throws IOException, InterruptedException {
    ArgumentListBuilder relocateCommand = getRelocateCommand();
    popRequired = relocation.isPopRequired();
    if (popRequired) {
      listener.getLogger().println("Clearing path: " + accurevWorkingSpace.getRemote());
      accurevWorkingSpace.deleteContents();
    }
    relocation.appendCommands(relocateCommand);

    return AccurevLauncher.runCommand(
        "relocation command",
        scm.getAccurevTool(),
        launcher,
        relocateCommand,
        scm.getOptionalLock(accurevWorkingSpace),
        accurevEnv,
        accurevWorkingSpace,
        listener,
        logger,
        true);
  }

  protected ArgumentListBuilder getRelocateCommand() {
    ArgumentListBuilder chrefcmd = new ArgumentListBuilder();
    chrefcmd.add("chref");
    Command.addServer(chrefcmd, server);
    chrefcmd.add("-r");
    chrefcmd.add(getSCMRefTree());
    return chrefcmd;
  }

  protected boolean validateCheckout(Run<?, ?> build) {
    String reftree = getSCMRefTree();
    if (StringUtils.isEmpty(reftree)) {
      listener.fatalError("Must specify a reference tree");
      return false;
    }
    return true;
  }

  @Override
  protected void buildEnvVarsCustom(Run<?, ?> build, Map<String, String> env) {
    env.put("ACCUREV_REFTREE", scm.getReftree());
  }

  private enum RefTreeRelocation implements RelocationOption {
    HOST {
      @Override
      protected boolean isRequired(
          AccurevReferenceTree accurevReftree, RemoteWorkspaceDetails remoteDetails) {
        return !accurevReftree.getHost().equals(remoteDetails.getHostName());
      }

      public void appendCommand(ArgumentListBuilder cmd, Relocation relocation) {
        cmd.add("-m");
        cmd.add(relocation.getNewHost());
      }
    },
    STORAGE {
      @Override
      protected boolean isRequired(
          AccurevReferenceTree accurevReftree, RemoteWorkspaceDetails remoteDetails) {
        String oldStorage =
            accurevReftree
                .getStorage()
                .replace("/", remoteDetails.getFileSeparator())
                .replace("\\", remoteDetails.getFileSeparator());
        return !new File(oldStorage).equals(new File(remoteDetails.getPath()));
      }

      public void appendCommand(ArgumentListBuilder cmd, Relocation relocation) {
        cmd.add("-l");
        cmd.add(relocation.getNewPath());
      }
    };

    public boolean isPopRequired() {
      return true;
    }

    protected abstract boolean isRequired(
        AccurevReferenceTree accurevReftree, RemoteWorkspaceDetails remoteDetails);
  }
}
