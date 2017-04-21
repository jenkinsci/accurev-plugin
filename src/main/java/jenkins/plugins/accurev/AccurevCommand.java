package jenkins.plugins.accurev;

/**
 * Initialized by josep on 07-03-2017.
 */
public interface AccurevCommand {

    void execute() throws AccurevException, InterruptedException;
}
