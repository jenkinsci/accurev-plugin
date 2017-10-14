package hudson.plugins.accurev.parsers.xml;


import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import hudson.plugins.accurev.AccurevLauncher.ICmdOutputXmlParser;
import hudson.plugins.accurev.AccurevLauncher.UnhandledAccurevCommandOutput;
import hudson.plugins.accurev.AccurevStream;
import hudson.plugins.accurev.AccurevStream.StreamType;
import hudson.plugins.accurev.ParseChangeLog;

public final class ParseShowStreams implements ICmdOutputXmlParser<Map<String, AccurevStream>, String> {
    public Map<String, AccurevStream> parse(XmlPullParser parser, String depot) throws UnhandledAccurevCommandOutput,
        IOException, XmlPullParserException {
        final Map<String, AccurevStream> streams = new HashMap<>();
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG && "stream".equalsIgnoreCase(parser.getName())) {
                final String streamName = parser.getAttributeValue("", "name");

                final String streamNumberStr = parser.getAttributeValue("", "streamNumber");
                final String basisStreamName = parser.getAttributeValue("", "basis");
                final String basisStreamNumberStr = parser.getAttributeValue("", "basisStreamNumber");
                final String streamTypeStr = parser.getAttributeValue("", "type");
                final String streamIsDynamic = parser.getAttributeValue("", "isDynamic");
                final String streamTimeString = parser.getAttributeValue("", "time");
                final Date streamTime = streamTimeString == null ? null : ParseChangeLog.convertAccurevTimestamp(streamTimeString);
                final String streamStartTimeString = parser.getAttributeValue("", "startTime");
                final Date streamStartTime = streamTimeString == null ? null : ParseChangeLog.convertAccurevTimestamp(streamStartTimeString);
                try {
                    final Long streamNumber = streamNumberStr == null ? null : Long.valueOf(streamNumberStr);
                    final Long basisStreamNumber = basisStreamNumberStr == null ? null : Long.valueOf(basisStreamNumberStr);
                    final StreamType streamType = AccurevStream.StreamType.parseStreamType(streamTypeStr);
                    final boolean isDynamic = streamIsDynamic != null && Boolean.parseBoolean(streamIsDynamic);
                    final AccurevStream stream = new AccurevStream(//
                        streamName, //
                        streamNumber, //
                        depot, //
                        basisStreamName, //
                        basisStreamNumber, //
                        isDynamic, //
                        streamType, //
                        streamTime, //
                        streamStartTime);
                    streams.put(streamName, stream);
                } catch (NumberFormatException e) {
                    throw new UnhandledAccurevCommandOutput(e);
                }
            }
        }
        return streams;
    }
}
