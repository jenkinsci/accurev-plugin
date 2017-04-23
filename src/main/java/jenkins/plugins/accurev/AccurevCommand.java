package jenkins.plugins.accurev;

public interface AccurevCommand {

    void execute() throws AccurevException, InterruptedException;
}
