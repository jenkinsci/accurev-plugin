package jenkins.plugins.accurev;

import hudson.plugins.accurev.AccurevDepots;
import hudson.plugins.accurev.AccurevStream;
import hudson.plugins.accurev.AccurevStreams;
import hudson.plugins.accurev.AccurevTransaction;

/**
 * Initialized by josep on 04-03-2017.
 */
public interface AccurevClient {
    boolean verbose = Boolean.getBoolean(AccurevClient.class.getName() + ".verbose");

    UpdateCommand update();

    LoginCommand login();

    HistCommand hist();

    AccurevDepots getDepots() throws InterruptedException;

    AccurevStreams getStream(String stream) throws InterruptedException;

    AccurevStreams getStreams() throws InterruptedException;

    AccurevStreams getStreams(String depot) throws InterruptedException;

    String getVersion() throws InterruptedException;

    void syncTime() throws InterruptedException;

    AccurevTransaction getLatestTransaction(String depot) throws InterruptedException;
}
