package hudson.plugins.accurev;

import hudson.scm.SCMRevisionState;

import java.util.Objects;

/**
 * Initialized by josep on 05-03-2017.
 */
public class AccurevSCMRevisionState extends SCMRevisionState {

    private final int transaction;

    /* package */ AccurevSCMRevisionState(int transaction) {
        this.transaction = transaction;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof AccurevSCMRevisionState) {
            AccurevSCMRevisionState comp = (AccurevSCMRevisionState) o;
            return comp.transaction == this.transaction;
        } else
            return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(transaction);
    }

    public int getTransaction() {
        return transaction;
    }

}
