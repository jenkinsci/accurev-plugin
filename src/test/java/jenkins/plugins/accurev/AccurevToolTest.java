package jenkins.plugins.accurev;

import static org.junit.Assert.assertEquals;

import hudson.EnvVars;
import hudson.model.Node;
import hudson.model.TaskListener;
import hudson.slaves.DumbSlave;
import hudson.tools.CommandInstaller;
import hudson.tools.InstallSourceProperty;
import hudson.tools.ToolInstallation;
import hudson.tools.ToolInstaller;
import hudson.tools.ToolProperty;
import hudson.util.StreamTaskListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/** Initialized by josep on 22-01-2017. */
public class AccurevToolTest {

  @org.junit.Rule public JenkinsRule j = new JenkinsRule();

  private AccurevTool accurevTool;

  public static AccurevTool createTool(String name) throws IOException {
    List<ToolInstaller> installers = new ArrayList<ToolInstaller>();
    installers.add(new CommandInstaller(null, "accurev", "./"));

    List<ToolProperty<ToolInstallation>> properties =
        new ArrayList<ToolProperty<ToolInstallation>>();
    properties.add(new InstallSourceProperty(installers));

    return new AccurevTool(name, "./", properties);
  }

  @Before
  public void setUp() throws Exception {
    AccurevTool.onLoaded();
    accurevTool = AccurevTool.getDefaultInstallation();
  }

  @Test
  public void testForNode() throws Exception {
    DumbSlave slave = j.createSlave();
    slave.setMode(Node.Mode.EXCLUSIVE);
    TaskListener log = StreamTaskListener.fromStdout();
    AccurevTool newTool = accurevTool.forNode(slave, log);
    assertEquals(accurevTool.getHome(), newTool.getHome());
  }

  @Test
  public void testForEnvironment() throws Exception {
    EnvVars environment = new EnvVars();
    AccurevTool newTool = accurevTool.forEnvironment(environment);
    assertEquals(accurevTool.getHome(), newTool.getHome());
  }

  @Test
  public void testGetDescriptor() throws Exception {
    AccurevTool.DescriptorImpl descriptor = accurevTool.getDescriptor();
    assertEquals("AccuRev", descriptor.getDisplayName());
  }

  @Test
  public void testGetInstallationFromDescriptor() throws Exception {
    AccurevTool.DescriptorImpl descriptor = accurevTool.getDescriptor();
    assertEquals(null, descriptor.getInstallation(""));
    assertEquals(null, descriptor.getInstallation("not-a-valid-accurev-install"));
  }

  @Test
  public void testGetDefaultInstallationFromDescriptor() throws Exception {
    AccurevTool newTool = AccurevTool.getDefaultInstallation();
    assertEquals(accurevTool.getHome(), newTool.getHome());
    assertEquals(accurevTool.getName(), newTool.getName());
  }
}
