package jenkins.plugins.accurev;

import java.util.List;

public interface UpdateCommand extends AccurevCommand {

    /**
     * the stream/workspace update command
     *
     * @param stream a {@link hudson.plugins.accurev.AccurevStream} object
     * @return a {@link jenkins.plugins.accurev.UpdateCommand} object
     */
    UpdateCommand stream(String stream);

    /**
     * accurev transaction range to get a list of file changes, in reverse since accurev behaves that way.
     *
     * @param latestTransaction   the latest transaction found in accurev at runtime
     * @param previousTransaction the previous transaction jenkins built
     * @return a {@link jenkins.plugins.accurev.UpdateCommand} object
     */
    UpdateCommand range(int latestTransaction, int previousTransaction);

    /**
     * A sort of dry run for the update command to show changes
     *
     * @param output a boolean
     * @return a {@link jenkins.plugins.accurev.UpdateCommand} object
     */
    UpdateCommand preview(List<String> output);
}
