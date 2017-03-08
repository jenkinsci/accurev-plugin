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
import jenkins.plugins.accurev.AccurevPlugin;
import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class MigrateIDAndCredentialTest {
    @org.junit.Rule
    public JenkinsRule j = new JenkinsRule();

    private AccurevSCMDescriptor descriptor;
    private AccurevSCM scm;

    @Before
    public void setUp() throws Exception {
        AccurevServer server = new AccurevServer(null,
                "test", "localhost",
                5050, "bob", "OBF:1rwf1x1b1rwf");
        scm = new AccurevSCM(null, "test", "test");
        scm.setServerName("test");
        FreeStyleProject accurevTest = j.createFreeStyleProject("accurevTest");
        accurevTest.setScm(scm);
        descriptor = scm.getDescriptor();
        List<AccurevServer> servers = new ArrayList<>();
        servers.add(server);
        descriptor.setServers(servers);
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
        assertNull(server.username);
        assertNull(server.password);
    }

    @Test
    public void testMigrateToServerUUID() throws Exception {
        AccurevPlugin.migrateJobsToServerUUID();
        AccurevServer server = AccurevSCM.configuration().getServers().get(0);
        assertTrue(StringUtils.equals(server.getUuid(), scm.getServerUUID()));
        assertNotNull(descriptor.getServer(scm.getServerUUID()));
        assertNotNull(scm.getServer());
        assertEquals(descriptor.getServer(scm.getServerUUID()), scm.getServer());
    }


}
