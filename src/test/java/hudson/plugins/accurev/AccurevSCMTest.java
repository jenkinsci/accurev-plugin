package hudson.plugins.accurev;

import hudson.model.AbstractBuild;
import hudson.model.Run;
import org.junit.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AccurevSCMTest {

    @SuppressWarnings("deprecation")
    @Test
    public void shouldSetEnvironmentVariablesWithAccurevSCM() throws IOException {
        AccurevSCM scm = mockSCMForBuildEnvVars();

        AccurevSCM.AccurevServer server = new AccurevSCM.AccurevServer(null, "test", "test", 5050, "user", "pass");
        when(scm.getServer()).thenReturn(server);

        when(scm.getStream()).thenReturn("testStream");

        AbstractBuild build = mock(AbstractBuild.class);
        Map<String, String> environment = new HashMap<>();
        scm.buildEnvVars(build, environment);

        assertThat(environment.get("ACCUREV_SERVER_HOSTNAME"), is("test"));
        assertThat(environment.get("ACCUREV_SERVER_PORT"), is("5050"));
        assertThat(environment.get("ACCUREV_STREAM"), is("testStream"));
    }

    private AccurevSCM mockSCMForBuildEnvVars() {
        AccurevSCM scm = mock(AccurevSCM.class);
        doCallRealMethod().when(scm).buildEnvVars(any(AbstractBuild.class), anyMap());
        doCallRealMethod().when(scm).buildEnvironment(any(Run.class), anyMap());
        return scm;
    }
}
