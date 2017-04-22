package hudson.plugins.accurev;

import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Logger;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * Utility class that provides {@link XmlPullParserFactory}s.
 */
@Deprecated
public class XmlParserFactory {
    private static final Logger logger = Logger.getLogger(AccurevSCM.class.getName());
    private static final Map<Object, XmlPullParserFactory> PARSER_FACTORY_CACHE = new WeakHashMap<>(
        1);

    /**
     * Gets a new {@link XmlPullParser} configured for parsing Accurev XML
     * files.
     *
     * @return a new {@link XmlPullParser} configured for parsing Accurev XML
     * files.
     * @throws XmlPullParserException when things go wrong/
     * @throws IOException            Handle it above
     */
    static XmlPullParser newParser() throws XmlPullParserException, IOException {
        XmlPullParserFactory factory = null;
        XmlPullParser parser = null;
        if (null != getFactory()) factory = getFactory();
        if (null != factory) parser = factory.newPullParser();
        return parser;
    }

    /**
     * Gets a new {@link XmlPullParserFactory} configured for parsing Accurev
     * XML files.
     *
     * @return a new {@link XmlPullParserFactory} configured for parsing Accurev
     * XML files, or <code>null</code> if things go wrong.
     * @throws IOException Handle it above
     */
    public static XmlPullParserFactory getFactory() throws IOException {
        synchronized (PARSER_FACTORY_CACHE) {
            final XmlPullParserFactory existingFactory = PARSER_FACTORY_CACHE.get(XmlPullParserFactory.class);
            if (existingFactory != null) {
                return existingFactory;
            }
            XmlPullParserFactory newFactory;
            try {
                newFactory = XmlPullParserFactory.newInstance();
            } catch (XmlPullParserException ex) {
                AccurevLauncher.logException("Unable to create new " + XmlPullParserFactory.class.getSimpleName(), ex,
                    logger, null);
                return null;
            }
            newFactory.setNamespaceAware(false);
            newFactory.setValidating(false);
            PARSER_FACTORY_CACHE.put(XmlPullParserFactory.class, newFactory);
            return newFactory;
        }
    }
}
