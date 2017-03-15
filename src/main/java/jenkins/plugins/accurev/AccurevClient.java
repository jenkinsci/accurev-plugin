package jenkins.plugins.accurev;

import hudson.plugins.accurev.AccurevDepots;
import hudson.plugins.accurev.AccurevStreams;

/**
 * Initialized by josep on 04-03-2017.
 */
public interface AccurevClient {
    boolean verbose = Boolean.getBoolean(AccurevClient.class.getName() + ".verbose");

    UpdateCommand update();

    LoginCommand login();

    HistCommand hist();

    AccurevDepots getDepots() throws AccurevException, InterruptedException;

    AccurevStreams getStreams() throws AccurevException, InterruptedException;

    AccurevStreams getStreams(String depot) throws AccurevException, InterruptedException;

    String getVersion() throws AccurevException, InterruptedException;

    void syncTime() throws AccurevException, InterruptedException;
}
