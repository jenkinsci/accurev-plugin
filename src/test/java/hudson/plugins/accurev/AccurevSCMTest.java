package hudson.plugins.accurev;

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.security.ACL;
import jenkins.model.Jenkins;
import org.junit.Before;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.*;

public class AccurevSCMTest {
    @org.junit.Rule
    public JenkinsRule j = new JenkinsRule();

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testMigrateCredential() throws Exception {
        AccurevServer server = new AccurevServer("test", "localhost", 5050, "bob", "OBF:1rwf1x1b1rwf");
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
}
