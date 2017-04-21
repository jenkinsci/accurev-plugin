package jenkins.plugins.accurev.util;

import java.io.StringReader;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

public class Parser {
    public static XmlPullParser createDefaultParser() throws XmlPullParserException {
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        return factory.newPullParser();
    }

    public static XmlPullParser parse(String xml) throws XmlPullParserException {
        return parse(new StringReader(xml));
    }

    private static XmlPullParser parse(StringReader reader) throws XmlPullParserException {
        XmlPullParser parser = createDefaultParser();
        parser.setInput(reader);
        return parser;
    }

//    TODO remove if not used
//    private static XmlPullParser parse(InputStream in, String encoding) throws XmlPullParserException {
//        XmlPullParser parser = createDefaultParser();
//        parser.setInput(in, encoding);
//        return parser;
//    }
}
