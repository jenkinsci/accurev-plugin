package hudson.plugins.accurev;

import hudson.plugins.accurev.AccurevLauncher.ICmdOutputXmlParser;
import hudson.plugins.accurev.AccurevLauncher.UnhandledAccurevCommandOutput;
import hudson.plugins.accurev.AccurevStream.StreamType;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

final class ParseShowStreams implements ICmdOutputXmlParser<Map<String, AccurevStream>, String> {
    public Map<String, AccurevStream> parse(XmlPullParser parser, String depot) throws UnhandledAccurevCommandOutput,
            IOException, XmlPullParserException {
        final Map<String, AccurevStream> streams = new HashMap<String, AccurevStream>();
        while (true) {
            switch (parser.next()) {
            case XmlPullParser.START_DOCUMENT:
                break;
            case XmlPullParser.END_DOCUMENT:
                // build the tree
                for (AccurevStream stream : streams.values()) {
                    if (stream.getBasisName() != null) {
                        stream.setParent(streams.get(stream.getBasisName()));
                    }
                }
                return streams;
            case XmlPullParser.START_TAG:
                final String tagName = parser.getName();
                if ("stream".equalsIgnoreCase(tagName)) {
                    final String streamName = parser.getAttributeValue("", "name");
                    final String streamNumberStr = parser.getAttributeValue("", "streamNumber");
                    final String basisStreamName = parser.getAttributeValue("", "basis");
                    final String basisStreamNumberStr = parser.getAttributeValue("", "basisStreamNumber");
                    final String streamTypeStr = parser.getAttributeValue("", "type");
                    final String streamIsDynamic = parser.getAttributeValue("", "isDynamic");
                    final String streamTimeString = parser.getAttributeValue("", "time");
                    final Date streamTime = streamTimeString == null ? null : ParseChangeLog
                            .convertAccurevTimestamp(streamTimeString);
                    final String streamStartTimeString = parser.getAttributeValue("", "startTime");
                    final Date streamStartTime = streamTimeString == null ? null : ParseChangeLog
                            .convertAccurevTimestamp(streamStartTimeString);
                    try {
                        final Long streamNumber = streamNumberStr == null ? null : Long.valueOf(streamNumberStr);
                        final Long basisStreamNumber = basisStreamNumberStr == null ? null : Long
                                .valueOf(basisStreamNumberStr);
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
                break;
            case XmlPullParser.END_TAG:
                break;
            case XmlPullParser.TEXT:
                break;
            }
        }
    }
}
