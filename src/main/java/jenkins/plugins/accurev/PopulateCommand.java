package jenkins.plugins.accurev;

import java.util.Set;

public interface PopulateCommand extends AccurevCommand {
    PopulateCommand stream(String stream);

    PopulateCommand overwrite(boolean overwrite);

    PopulateCommand timespec(String timespec);

    PopulateCommand elements(Set<String> elements);
}
