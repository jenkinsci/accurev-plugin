package hudson.plugins.accurev;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

import io.jenkins.plugins.casc.ConfigurationAsCode;
import io.jenkins.plugins.casc.ConfiguratorException;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class ConfigurationAsCodeTest {

  @Rule public JenkinsRule r = new JenkinsRule();

  @Test
  public void shouldSupportConfigurationAsCode() throws Exception {
    configureJenkins("accurev.yaml");

    AccurevSCM.AccurevSCMDescriptor desc = AccurevSCM.configuration();

    assertThat(desc.isPollOnMaster(), is(true));
    assertThat(desc.getServers().size(), is(1));

    AccurevSCM.AccurevServer server1 = desc.getServers().get(0);

    assertThat(server1.getName(), is("testserver1"));
    assertThat(server1.getHost(), is("testhost"));
    assertThat(server1.getPort(), is(1234));
    assertThat(server1.getCredentialsId(), is("abc123"));
    assertThat(server1.isSyncOperations(), is(true));
    assertThat(server1.isMinimiseLogins(), is(true));
    assertThat(server1.isUseNonexpiringLogin(), is(true));
    assertThat(server1.isUseRestrictedShowStreams(), is(true));
    assertThat(server1.isUseColor(), is(true));
    assertThat(server1.isUsePromoteListen(), is(true));
    assertThat(server1.isServerDisabled(), is(true));
  }

  private void configureJenkins(final String fileName) throws ConfiguratorException {
    ConfigurationAsCode.get()
        .configure(ConfigurationAsCodeTest.class.getResource(fileName).toString());
  }
}
