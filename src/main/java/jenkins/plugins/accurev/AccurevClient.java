package jenkins.plugins.accurev;

import hudson.plugins.accurev.AccurevDepots;
import hudson.plugins.accurev.AccurevStreams;

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
