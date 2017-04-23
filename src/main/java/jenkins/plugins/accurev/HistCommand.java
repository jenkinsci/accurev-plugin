package jenkins.plugins.accurev;

import hudson.plugins.accurev.AccurevTransactions;

public interface HistCommand extends AccurevCommand {
    HistCommand depot(String depot);

    HistCommand stream(String stream);

    HistCommand timespec(String timespec);

    HistCommand count(int count);

    HistCommand toTransactions(AccurevTransactions transactions);
}
