package hudson.plugins.accurev;

import hudson.model.Run;
import hudson.scm.ChangeLogSet;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 *
 * @author connollys
 * @since 10-Oct-2007 13:12:40
 */
@ExportedBean(defaultVisibility = 999)
public final class AccurevChangeLogSet extends ChangeLogSet<AccurevTransaction> {
    private final List<AccurevTransaction> transactions;

    AccurevChangeLogSet(Run build, List<AccurevTransaction> transactions) {
        // TODO: Implement RepositoryBrowser?
        super(build, null);
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

    public Collection<AccurevTransaction> getLogs() {
        return transactions;
    }

    public java.lang.Object[] toArray() {
        if (transactions == null) {
            return null;
        }

        return transactions.toArray();
    }

    @Exported
    public String getKind() {
        return "accurev";
    }
}
