package hudson.plugins.accurev;

import hudson.plugins.accurev.AccurevLauncher.ICmdOutputXmlParser;
import hudson.plugins.accurev.AccurevLauncher.UnhandledAccurevCommandOutput;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

final class ParseHistory implements ICmdOutputXmlParser<Boolean, List<AccurevTransaction>> {
	private List<String> ignoreFilePatterns;
	private boolean includeMode;
	private static final Logger logger = Logger.getLogger(ParseHistory.class.getName());
	
	public ParseHistory(List<String> ignoreFilesPatterns, boolean includeMode) {
		this.ignoreFilePatterns = ignoreFilesPatterns;
		this.includeMode = includeMode;
	}

	public Boolean parse(XmlPullParser parser, List<AccurevTransaction> context) throws UnhandledAccurevCommandOutput,
            IOException, XmlPullParserException {
		List<AccurevTransaction> tempList = new ArrayList();
        AccurevTransaction resultTransaction = null;
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            if (parser.getEventType() == XmlPullParser.START_TAG) {
                if (parser.getName().equalsIgnoreCase("transaction")) {
                	resultTransaction = new AccurevTransaction();
                	tempList.add(resultTransaction);
                    // parse transaction-values
                    resultTransaction.setId((Integer.parseInt(parser.getAttributeValue("", "id"))));
                    resultTransaction.setAction(parser.getAttributeValue("", "type"));
                    resultTransaction.setDate(ParseChangeLog.convertAccurevTimestamp(parser.getAttributeValue("",
                            "time")));
                    resultTransaction.setUser(parser.getAttributeValue("", "user"));
                } else if (parser.getName().equalsIgnoreCase("comment")) {
                    // parse comments
                    resultTransaction.setMsg(parser.nextText());
                } else if (parser.getName().equalsIgnoreCase("version")) {
					String path = parser.getAttributeValue("", "path");
					if (path != null) {
						path = path.replace("\\", "/");
						if (path.startsWith("/./")) {
							path = path.substring(3);
						}
					}
					resultTransaction.addAffectedPath(path);
				}
            }
        }

        if (ignoreFilePatterns != null) {
        	for (AccurevTransaction trans : tempList) {
        		if (isTransactionAcceptableThroughFilter(trans.getAffectedPaths())) {
        			context.add(trans);
        		}
        	}
        } else {
        	context.addAll(tempList);
        }
       
        return context.size() != 0;
    }
	
	protected boolean isTransactionAcceptableThroughFilter(Collection<String> filePaths) {
		if (filePaths == null || filePaths.isEmpty()) {
			return true;  //Could be chstream chws or any other transaction that has potential of many silent file changes
		}
		
		try {
    		for (String pathName : filePaths) {
    			if (isAcceptable(pathName)) {
    				return true; //if any one file is good then the whole commits gotta be considered.
    			}
    		}
    		logger.info("Found the following paths but all are actively ignored for polling, ignoring this transaction: "+filePaths);
    		return false;
    	} catch (Exception e) {
    		//If your regex is all derped up, lets err on side of more builds and assume the transaction contains valid files.
    		logger.warning("Regular expression filtering caused exception, "+e.getMessage()+ " :::  marking transaction as acceptable, dumping inputs!");
    		logger.info("File paths: "+filePaths);
    		logger.info("Regexes: "+ignoreFilePatterns);
    		return true;
    	}
	}
	
	private boolean isAcceptable(String pathName) throws Exception {
		for (String regex: ignoreFilePatterns) {
    		if (!includeMode && pathName.matches(regex)) {
    			return false;
    		} else if (includeMode && pathName.matches(regex)) {
    			return true;
    		}
    	}
		return !includeMode;
	}
}
