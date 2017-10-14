package hudson.plugins.accurev;

import java.io.Serializable;
import java.util.Map;

import hudson.scm.SCMRevisionState;

public class AccurevSCMRevisionState extends SCMRevisionState implements Serializable {

    private static final long serialVersionUID = 1L;
    private final Map<String, Long> transactions;

    /* package */ AccurevSCMRevisionState(Map<String, Long> transactions) {
        this.transactions = transactions;
    }

    public long getTransaction(String location) {
        if (transactions == null || !transactions.containsKey(location)) return 1L;
        return transactions.get(location);
    }

    @Override
    public String toString() {
        return "AccurevRevisionState" + transactions;
    }
}
