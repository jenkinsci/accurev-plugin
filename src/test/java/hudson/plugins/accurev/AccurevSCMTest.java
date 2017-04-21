package hudson.plugins.accurev;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.model.FreeStyleProject;
import hudson.plugins.accurev.AccurevSCM.AccurevSCMDescriptor;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class AccurevSCMTest {
    @org.junit.Rule
    public JenkinsRule rule = new JenkinsRule();

    private AccurevSCMDescriptor descriptor;
    private AccurevSCM scm;
    private AccurevServer server;

    @SuppressWarnings("deprecation")
    @Before
    public void setUp() throws Exception {
        server = new AccurevServer(null,
            "accurevOldServer", "accurevbox.example.org",
            5050, "bob", "OBF:1rwf1x1b1rwf");
        scm = new AccurevSCM(server, "test", "test");
        FreeStyleProject accurevTest = rule.createFreeStyleProject("accurevTest");
        accurevTest.setScm(scm);
        descriptor = scm.getDescriptor();
        List<AccurevServer> servers = new ArrayList<>();
        servers.add(server);
        descriptor.setServers(servers);
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
