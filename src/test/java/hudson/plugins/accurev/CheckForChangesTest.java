package hudson.plugins.accurev;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Collection;

import org.junit.Test;

/**
 * Initialized by josep on 29-01-2017.
 */
public class CheckForChangesTest {

    @Test
    public void testPathWithMatch() {
        Collection<String> serverPaths = new ArrayList<>();
        serverPaths.add("/home/joseph/test/hi-lib-dal-mongo/somefile.java");
        Collection<String> filters = new ArrayList<>();
        filters.add("*/hi-lib-dal-mongo/*");
        assertTrue(CheckForChanges.changesMatchFilter(serverPaths, filters));
    }

    @Test
    public void testPathMatcher() {
        assertTrue(CheckForChanges.pathMatcher("/home/joseph/some.java", "*.java"));
        assertTrue(CheckForChanges.pathMatcher("/home/joseph/test/hi-lib-dal-mongo/test/somefile.java", "*/joseph/*/hi-lib-dal-mongo*"));
    }

    @Test
    public void testStringWithoutWildcard() {
        Collection<String> serverPaths = new ArrayList<>();
        serverPaths.add("/home/joseph/test/hi-lib-dal-mongo/somefile.java");
        Collection<String> filters = new ArrayList<>();
        filters.add("hi-lib-dal");
        assertFalse(CheckForChanges.changesMatchFilter(serverPaths, filters));
    }

    @Test
    public void testStringWithFileWildcard() {
        Collection<String> serverPaths = new ArrayList<>();
        serverPaths.add("/home/joseph/test/hi-lib-dal-mongo/somefile.java");
        Collection<String> filters = new ArrayList<>();
        filters.add("*.java");
        assertTrue(CheckForChanges.changesMatchFilter(serverPaths, filters));
    }
}
