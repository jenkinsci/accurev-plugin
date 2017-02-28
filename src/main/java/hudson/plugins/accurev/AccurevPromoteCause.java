package hudson.plugins.accurev;

import hudson.model.Cause;

/**
 * Initialized by josp on 16/09/16.
 */
public class AccurevPromoteCause extends Cause {

    private final String author;
    private final String stream;

    public AccurevPromoteCause(String author, String stream) {
        this.author = author;
        this.stream = stream;
    }

    @Override
    public String getShortDescription() {
        return "Triggered by " + author + " on " + stream + " via Accurev Promote";
    }
}
