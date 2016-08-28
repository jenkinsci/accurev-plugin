package hudson.plugins.accurev;

import hudson.plugins.accurev.delegates.AbstractModeDelegate;
import hudson.plugins.accurev.delegates.ReftreeDelegate;
import hudson.plugins.accurev.delegates.SnapshotDelegate;
import hudson.plugins.accurev.delegates.StreamDelegate;
import hudson.plugins.accurev.delegates.WorkspaceDelegate;

/**
 * Determines the delegate used for building
 */
public enum AccurevMode {

    WORKSPACE(true) {

                @Override
                protected AbstractModeDelegate createDelegate(AccurevSCM accurevSCM) {
                    return new WorkspaceDelegate(accurevSCM);
                }

                @Override
                protected boolean isMode(AccurevSCM accurevSCM) {
                    return "wspace".equals(accurevSCM.getWspaceORreftree());
                }

                @Override
                public boolean isWorkspace() {
                    return true;
                }

            },
    REF_TREE(true) {

                @Override
                protected AbstractModeDelegate createDelegate(AccurevSCM accurevSCM) {
                    return new ReftreeDelegate(accurevSCM);
                }

                @Override
                protected boolean isMode(AccurevSCM accurevSCM) {
                    return "reftree".equals(accurevSCM.getWspaceORreftree());
                }

                @Override
                public boolean isReftree() {
                    return true;
                }

            },
    SNAPSHOT(false) {

                @Override
                protected AbstractModeDelegate createDelegate(AccurevSCM accurevSCM) {
                    return new SnapshotDelegate(accurevSCM);
                }

                @Override
                protected boolean isMode(AccurevSCM accurevSCM) {
                    return !WORKSPACE.isMode(accurevSCM)
                    && !REF_TREE.isMode(accurevSCM)
                    && accurevSCM.isUseSnapshot();
                }

                public boolean isNoWorkspaceOrRefTree() {
                    return true;
                }

            },
    STREAM(false) {

                @Override
                protected AbstractModeDelegate createDelegate(AccurevSCM accurevSCM) {
                    return new StreamDelegate(accurevSCM);
                }

                @Override
                protected boolean isMode(AccurevSCM accurevSCM) {
                    return !WORKSPACE.isMode(accurevSCM)
                    && !REF_TREE.isMode(accurevSCM)
                    && !accurevSCM.isUseSnapshot();
                }

                @Override
                public boolean isNoWorkspaceOrRefTree() {
                    return true;
                }

            };

    private final boolean requiresWorkspace;

    AccurevMode(boolean requiresWorkspace) {
        this.requiresWorkspace = requiresWorkspace;
    }

    public boolean isRequiresWorkspace() {
        return requiresWorkspace;
    }

    public boolean isWorkspace() {
        return false;
    }

    public boolean isReftree() {
        return false;
    }

    public boolean isNoWorkspaceOrRefTree() {
        return false;
    }

    protected abstract boolean isMode(AccurevSCM accurevSCM);

    protected abstract AbstractModeDelegate createDelegate(AccurevSCM accurevSCM);

    public static AbstractModeDelegate findDelegate(AccurevSCM accurevSCM) {
        AccurevMode accurevMode = findMode(accurevSCM);
        AbstractModeDelegate delegate = accurevMode.createDelegate(accurevSCM);
        return delegate;
    }

    public static AccurevMode findMode(AccurevSCM accurevSCM) {
        AccurevMode retVal = null;
        for (AccurevMode accurevMode : values()) {
            if (accurevMode.isMode(accurevSCM)) {
                retVal = accurevMode;
                break;
            }
        }
        return retVal;
    }

}
