package hudson.plugins.accurev.browser;

import hudson.plugins.accurev.AccurevTransaction;
import hudson.scm.RepositoryBrowser;

import java.io.IOException;
import java.net.URL;

/**
 * @author josp
 */
public class AccurevRepositoryBrowser extends RepositoryBrowser<AccurevTransaction> {

    @Override
    public URL getChangeSetLink(AccurevTransaction changeSet) throws IOException {
        return null;
    }
}
