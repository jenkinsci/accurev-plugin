package hudson.plugins.accurev.delegates;

import java.util.List;

import hudson.util.ArgumentListBuilder;

/**
 * @author raymond
 */
@Deprecated
public class Relocation {

    private final List<RelocationOption> relocationOptions;
    private final String newHost;
    private final String newPath;
    private final String newParent;

    public Relocation(List<RelocationOption> relocationOptions, String newHost, String newPath, String newParent) {
        this.relocationOptions = relocationOptions;
        this.newHost = newHost;
        this.newPath = newPath;
        this.newParent = newParent;
    }

    public boolean isRelocationRequired() {
        return !relocationOptions.isEmpty();
    }

    public String getNewParent() {
        return newParent;
    }

    public String getNewPath() {
        return newPath;
    }

    public String getNewHost() {
        return newHost;
    }

    public boolean isPopRequired() {
        for (RelocationOption relocationOption : relocationOptions) {
            if (relocationOption.isPopRequired()) {
                return true;
            }
        }
        return false;
    }

    public void appendCommands(ArgumentListBuilder relocateCommand) {
        for (RelocationOption relocationOption : relocationOptions) {
            relocationOption.appendCommand(relocateCommand, this);
        }
    }

    public interface RelocationOption {

        boolean isPopRequired();

        void appendCommand(ArgumentListBuilder cmd, Relocation relocation);
    }
}
