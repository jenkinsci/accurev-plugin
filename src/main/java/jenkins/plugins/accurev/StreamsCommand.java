package jenkins.plugins.accurev;

import hudson.plugins.accurev.AccurevStreams;

public interface StreamsCommand extends AccurevCommand {
    StreamsCommand depot(String depot);

    StreamsCommand stream(String stream);

    StreamsCommand restricted();

    StreamsCommand toStreams(AccurevStreams streams);
}
