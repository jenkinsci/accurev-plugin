package hudson.plugins.accurev;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Logger;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

/**
 *
 */
public class XmlConsolidateStreamChangeLog {

	private static final Logger logger = Logger.getLogger(AccurevSCM.class.getName());
	private static final Map<Class<XMLOutputFactory>, XMLOutputFactory> OUTPUT_FACTORY_CACHE = new WeakHashMap<Class<XMLOutputFactory>, XMLOutputFactory>(1);

	static XMLOutputFactory getFactory() {
		synchronized (OUTPUT_FACTORY_CACHE) {
			final XMLOutputFactory existingFactory = OUTPUT_FACTORY_CACHE.get(XMLOutputFactory.class);
			if (existingFactory != null) {
				return existingFactory;
			}
			XMLOutputFactory newFactory = XMLOutputFactory.newInstance();
			OUTPUT_FACTORY_CACHE.put(XMLOutputFactory.class, newFactory);
			return newFactory;
		}

	}

	public static File getStreamChangeLogFile(File changelogFile, AccurevStream stream) {
		File dir = changelogFile.getParentFile();
		File retVal = new File(dir, stream.getName() + "_" + changelogFile.getName());
		return retVal;
	}

	public static void createChangeLog(List<String> streamFiles, File changeLogFile) {
		FileOutputStream changeLogStream = null;
		try {
			changeLogStream = new FileOutputStream(changeLogFile);
			XMLOutputFactory outputFactory = getFactory();
			XMLStreamWriter streamWriter = outputFactory.createXMLStreamWriter(changeLogStream);
			streamWriter.writeStartDocument();
			streamWriter.writeStartElement("ChangeLogs");
			for (String streamFile : streamFiles) {
				streamWriter.writeStartElement("ChangeLog");
				streamWriter.writeCharacters(streamFile);
				streamWriter.writeEndElement();
			}
			streamWriter.writeEndElement();
			streamWriter.writeEndDocument();
		} catch (FileNotFoundException ex) {
			AccurevLauncher.logException("Unable to create consolidated changelog " + XmlConsolidateStreamChangeLog.class.getSimpleName(), ex,
					logger, null);
		} catch (XMLStreamException ex) {
			AccurevLauncher.logException("Unable to create consolidated changelog " + XmlConsolidateStreamChangeLog.class.getSimpleName(), ex,
					logger, null);
		} finally {
			try {
				if (changeLogStream != null) {
					changeLogStream.close();
				}
			} catch (IOException ex) {
				AccurevLauncher.logException("Unable to create consolidated changelog " + XmlConsolidateStreamChangeLog.class.getSimpleName(), ex,
						logger, null);
			}
		}

	}
}
