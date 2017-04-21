package hudson.plugins.accurev;

import static jenkins.plugins.accurev.AccurevToolTest.createTool;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.CredentialsStore;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;

import hudson.model.AbstractBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.security.ACL;
import jenkins.model.Jenkins;

import jenkins.plugins.accurev.AccurevTool;
import hudson.plugins.accurev.AccurevSCM.AccurevSCMDescriptor;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;

public class AccurevSCMTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    private AccurevSCM scm;
    private AccurevServer oldServer;
    private FreeStyleProject accurevTest;
    private AccurevSCMDescriptor descriptor;
    private CredentialsStore store;

    @SuppressWarnings("deprecation")
    @Before
    public void setUp() throws Exception {
        store = CredentialsProvider.lookupStores(rule.jenkins).iterator().next();
        oldServer = new AccurevServer(null,
            "accurevOldServer", "accurevbox.example.org",
            5050, "bob", "OBF:1rwf1x1b1rwf");
        descriptor = rule.get(AccurevSCMDescriptor.class);
        List<AccurevServer> servers = new ArrayList<>();
        servers.add(oldServer);
        descriptor.setServers(servers);
        scm = new AccurevSCM(oldServer, "test", "test");
        accurevTest = rule.createFreeStyleProject("accurevTest");
        accurevTest.setScm(scm);
    }

    @SuppressWarnings("deprecation")
    @Test
    public void shouldSetEnvironmentVariablesWithAccurevSCM() throws IOException {
        AccurevSCM scm = mockSCMForBuildEnvVars();

        when(scm.getServer()).thenReturn(oldServer);

        when(scm.getStream()).thenReturn("testStream");

        AbstractBuild build = mock(AbstractBuild.class);
        Map<String, String> environment = new HashMap<>();
        scm.buildEnvVars(build, environment);

        assertThat(environment.get("ACCUREV_SERVER_HOSTNAME"), is("accurevbox.example.org"));
        assertThat(environment.get("ACCUREV_SERVER_PORT"), is("5050"));
        assertThat(environment.get("ACCUREV_STREAM"), is("testStream"));
    }

    private AccurevSCM mockSCMForBuildEnvVars() {
        AccurevSCM scm = mock(AccurevSCM.class);
        doCallRealMethod().when(scm).buildEnvVars(any(AbstractBuild.class), anyMap());
        doCallRealMethod().when(scm).buildEnvironment(any(Run.class), anyMap());
        return scm;
    }

    @Test
    public void testConfigRoundtrip() throws Exception {
        store.addCredentials(
            Domain.global(),
            new UsernamePasswordCredentialsImpl(
                CredentialsScope.GLOBAL,
                "accurev",
                "Test Accurev credential",
                "bob",
                "test"
            )
        );

        // setup global config - add another server to test that part
        List<AccurevServer> serverList = descriptor.getServers();
        serverList.add(new AccurevServer(null, "otherServerName", "otherServerHost", 5050, "accurev"));
        descriptor.setServers(serverList);

        accurevTest.setScm(scm);
        rule.configRoundtrip(accurevTest);
        rule.assertEqualDataBoundBeans(scm, accurevTest.getScm());
    }

    @Test
    public void testConfigAccurevToolRoundtrip() throws Exception {
        AccurevTool.DescriptorImpl tools = rule.jenkins.getDescriptorByType(AccurevTool.DescriptorImpl.class);
        AccurevTool test1 = createTool("test1");
        AccurevTool test2 = createTool("test2");
        tools.setInstallations(
            test1,
            test2
        );

        scm.setAccurevTool("test2");
        accurevTest.setScm(scm);
        rule.configRoundtrip(accurevTest);
        rule.assertEqualDataBoundBeans(scm, accurevTest.getScm());
    }

    @Test
    public void testDataCompatibility() throws Exception {
        FreeStyleProject p = (FreeStyleProject) rule.jenkins.createProjectFromXML("bar", getClass().getResourceAsStream("AccurevSCMTest/freestyleold1.xml"));
        AccurevSCM oldAccurev = (AccurevSCM) p.getScm();
        assertEquals(1, oldAccurev.getExtensions().size());
        assertEquals("accurev accurevbox.example.org:5050", oldAccurev.getKey());
        assertTrue(StringUtils.isNotBlank(oldAccurev.getCredentialsId()));
    }

    @Test
    public void testMigrateCredential() throws Exception {
        AccurevServer server = AccurevSCM.configuration().getServers().get(0);
        boolean migrated = server.migrateCredentials();
        StandardUsernamePasswordCredentials credentials = CredentialsMatchers.firstOrNull(
            CredentialsProvider
                .lookupCredentials(StandardUsernamePasswordCredentials.class,
                    Jenkins.getInstance(), ACL.SYSTEM,
                    URIRequirementBuilder.fromUri("").withHostnamePort("localhost", 5050).build()),
            CredentialsMatchers.withUsername("bob"));
        assertNotNull(credentials);
        assertTrue(migrated);
        assertNotNull(server.getCredentialsId());
        assertNotNull(server.getCredentials());
        assertEquals(server.getCredentials().getUsername(), credentials.getUsername());
        assertEquals(server.getCredentials().getPassword(), credentials.getPassword());
        assertNull(server.getUsername());
        assertNull(server.getPassword());
    }

    @Test
    public void testMigrationFromGlobalConfigToJobConfigOfServer() throws Exception {
        AccurevServer server = AccurevSCM.configuration().getServers().get(0);
        boolean migrated = server.migrateCredentials();
        scm.migrate();
        assertTrue(migrated);
        assertEquals(scm.getUrl(), server.getUrl());
        assertTrue(StringUtils.isNotBlank(scm.getCredentialsId()));
        assertEquals(scm.getCredentialsId(), server.getCredentialsId());
    }

}
