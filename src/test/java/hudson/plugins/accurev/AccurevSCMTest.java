package hudson.plugins.accurev;

import static jenkins.plugins.accurev.AccurevToolTest.createTool;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsStore;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.plugins.accurev.AccurevSCM.AccurevSCMDescriptor;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jenkins.plugins.accurev.AccurevTool;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class AccurevSCMTest {

  @Rule public JenkinsRule rule = new JenkinsRule();

  private AccurevSCM scm;
  private AccurevServer oldServer;
  private FreeStyleProject accurevTest;
  private AccurevSCMDescriptor descriptor;
  private CredentialsStore store;

  @SuppressWarnings("deprecation")
  @Before
  public void setUp() throws Exception {
    store = CredentialsProvider.lookupStores(rule.jenkins).iterator().next();
    oldServer = new AccurevServer(null, "accurevOldServer", "accurevbox.example.org");
    oldServer.setUsername("bob");
    oldServer.setPassword("OBF:1rwf1x1b1rwf");
    descriptor = rule.get(AccurevSCMDescriptor.class);
    List<AccurevServer> servers = new ArrayList<>();
    servers.add(oldServer);
    descriptor.setServers(servers);
    scm = new AccurevSCM("accurevOldServer", "test", "test");
    accurevTest = rule.createFreeStyleProject("accurevTest");
    accurevTest.setScm(scm);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void shouldSetEnvironmentVariablesWithAccurevSCM() throws IOException {
    AccurevSCM scm = mockSCMForBuildEnvVars();

    when(scm.getServer()).thenReturn(oldServer);

    when(scm.getStream()).thenReturn("testStream");

    Run run = mock(Run.class);
    Map<String, String> environment = new HashMap<>();
    scm.buildEnvironment(run, environment);

    assertThat(environment.get("ACCUREV_SERVER_HOSTNAME"), is("accurevbox.example.org"));
    assertThat(environment.get("ACCUREV_SERVER_PORT"), is("5050"));
    assertThat(environment.get("ACCUREV_STREAM"), is("testStream"));
  }

  private AccurevSCM mockSCMForBuildEnvVars() {
    AccurevSCM scm = mock(AccurevSCM.class);
    doCallRealMethod().when(scm).buildEnvironment(any(Run.class), anyMap());
    return scm;
  }

  @Test
  public void testConfigRoundtrip() throws Exception {

    // setup global config - add another server to test that part
    List<AccurevServer> serverList = descriptor.getServers();
    serverList.add(new AccurevServer(null, "otherServerName", "otherServerHost"));
    descriptor.setServers(serverList);

    accurevTest.setScm(scm);
    rule.configRoundtrip(accurevTest);
    rule.assertEqualDataBoundBeans(scm, accurevTest.getScm());
  }

  @Test
  public void testConfigAccurevToolRoundtrip() throws Exception {
    AccurevTool.DescriptorImpl tools =
        rule.jenkins.getDescriptorByType(AccurevTool.DescriptorImpl.class);
    AccurevTool test1 = createTool("test1");
    AccurevTool test2 = createTool("test2");
    tools.setInstallations(test1, test2);

    scm.setAccurevTool("test2");
    accurevTest.setScm(scm);
    rule.configRoundtrip(accurevTest);
    rule.assertEqualDataBoundBeans(scm, accurevTest.getScm());
  }

  @Test
  public void testConfigWspaceRoundtrip() throws Exception {

    scm.setWspaceORreftree("wspace");
    scm.setWorkspace("testWorkspace1");

    accurevTest.setScm(scm);
    rule.configRoundtrip(accurevTest);
    rule.assertEqualDataBoundBeans(scm, accurevTest.getScm());
  }

  @Test
  public void testConfigReftreeRoundtrip() throws Exception {

    scm.setWspaceORreftree("reftree");
    scm.setReftree("testRefTree1");

    accurevTest.setScm(scm);
    rule.configRoundtrip(accurevTest);
    rule.assertEqualDataBoundBeans(scm, accurevTest.getScm());
  }
}
