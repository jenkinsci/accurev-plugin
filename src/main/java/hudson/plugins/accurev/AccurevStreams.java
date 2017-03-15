package hudson.plugins.accurev;

import jenkins.plugins.accurev.AccurevException;
import jenkins.plugins.accurev.util.AccurevUtils;
import jenkins.plugins.accurev.util.Parser;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;

public class AccurevStreams extends HashMap<String, AccurevStream> {
    public AccurevStreams(String result) {
        parse(result);
    }

    private void parse(String result) {
        try {
            XmlPullParser parser = Parser.parse(result);
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.getEventType() == XmlPullParser.START_TAG && "stream".equalsIgnoreCase(parser.getName())) {
                    final String streamName = parser.getAttributeValue("", "name");
                    final String basisStreamName = parser.getAttributeValue("", "basis");
                    final String basisStreamNumberStr = parser.getAttributeValue("", "basisStreamNumber");
                    final String depotNameStr = parser.getAttributeValue("", "depotName");
                    final String streamNumberStr = parser.getAttributeValue("", "streamNumber");
                    final String streamIsDynamic = parser.getAttributeValue("", "isDynamic");
                    final String streamTypeStr = parser.getAttributeValue("", "type");
                    final String streamTimeString = parser.getAttributeValue("", "time");
                    final Date streamTime = streamTimeString == null ? null : AccurevUtils.convertAccurevTimestamp(streamTimeString);
                    final String streamStartTimeString = parser.getAttributeValue("", "startTime");
                    final Date streamStartTime = streamTimeString == null ? null : AccurevUtils.convertAccurevTimestamp(streamStartTimeString);
                    try {
                        final Long streamNumber = streamNumberStr == null ? null : Long.valueOf(streamNumberStr);
                        final Long basisStreamNumber = basisStreamNumberStr == null ? null : Long.valueOf(basisStreamNumberStr);
                        final AccurevStream.StreamType streamType = AccurevStream.StreamType.parseStreamType(streamTypeStr);
                        final boolean isDynamic = streamIsDynamic != null && Boolean.parseBoolean(streamIsDynamic);
                        final AccurevStream stream = new AccurevStream(//
                            streamName, //
                            streamNumber, //
                            depotNameStr, //
                            basisStreamName, //
                            basisStreamNumber, //
                            isDynamic, //
                            streamType, //
                            streamTime, //
                            streamStartTime);
                        this.put(streamName, stream);
                    } catch (NumberFormatException e) {
                        throw new AccurevException("Cannot parse numbers from accurev", e);
                    }
                }
            }
        } catch (XmlPullParserException | IOException e) {
            throw new AccurevException("Failed to get streams", e);
        }
    }
}
