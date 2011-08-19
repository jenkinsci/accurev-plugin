package hudson.plugins.accurev;

import hudson.model.AbstractBuild;
import hudson.scm.ChangeLogSet;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 *
 * @author connollys
 * @since 10-Oct-2007 13:12:40
 */
final class AccurevChangeLogSet extends ChangeLogSet<AccurevTransaction> {
    private final List<AccurevTransaction> transactions;

    AccurevChangeLogSet(AbstractBuild build, List<AccurevTransaction> transactions) {
        super(build);
        if (transactions == null) {
            throw new NullPointerException("Cannot have a null transaction list");
        }
        this.transactions = Collections.unmodifiableList(transactions);
        for (AccurevTransaction transaction : transactions) {
            transaction.setParent(this);
        }
    }

    public boolean isEmptySet() {
        return transactions.isEmpty();
    }

    public Iterator<AccurevTransaction> iterator() {
        return transactions.iterator();
    }
}
