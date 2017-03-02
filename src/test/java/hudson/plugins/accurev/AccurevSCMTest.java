package hudson.plugins.accurev;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;

import hudson.model.FreeStyleProject;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import jenkins.plugins.accurev.AccurevPlugin;

public class AccurevSCMTest {
    @org.junit.Rule
    public JenkinsRule j = new JenkinsRule();

    private AccurevServer server;
    private AccurevSCM.AccurevSCMDescriptor descriptor;
    private AccurevSCM scm;

    @Before
    public void setUp() throws Exception {
        server = new AccurevServer("test", "localhost", 5050, "bob", "OBF:1rwf1x1b1rwf");
        scm = new AccurevSCM(null, "test", "test", "test", "none", "", null, "", "", "", "", false, false, false, false, "", "", false);
        FreeStyleProject accurevTest = j.createFreeStyleProject("accurevTest");
        accurevTest.setScm(scm);
        descriptor = (AccurevSCM.AccurevSCMDescriptor) scm.getDescriptor();
        List<AccurevServer> servers = new ArrayList<>();
        servers.add(server);
        descriptor.setServers(servers);
    }

    @Test
    public void testMigrateCredential() throws Exception {
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
        assertNull(server.username);
        assertNull(server.password);
    }

    @Test
    public void testMigrateToServerUUID() throws Exception {
        AccurevPlugin.migrateJobsToServerUUID();
        assertTrue(StringUtils.equals(server.getUUID(), scm.getServerUUID()));
        assertNotNull(descriptor.getServer(scm.getServerUUID()));
        assertNotNull(scm.getServer());
        assertEquals(descriptor.getServer(scm.getServerUUID()), scm.getServer());
    }


}
