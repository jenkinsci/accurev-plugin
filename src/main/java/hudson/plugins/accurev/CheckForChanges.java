package hudson.plugins.accurev;

import hudson.FilePath;
import hudson.Launcher;
import hudson.model.TaskListener;
import hudson.plugins.accurev.AccurevSCM.AccurevServer;
import hudson.plugins.accurev.cmd.History;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CheckForChanges {
	
	//checkStreamForChanges is overloaded method
	/**
    *
    * @param server
    * @param accurevEnv
    * @param workspace
    * @param listener
    * @param accurevPath
    * @param launcher
    * @param stream
    * @param buildDate
    * @return if there are any new transactions in the stream since the last build was done
    * @throws IOException
    * @throws InterruptedException
    */
  //stream param is of type String
   public static boolean checkStreamForChanges(AccurevServer server,
                                         Map<String, String> accurevEnv,
                                         FilePath workspace,
                                         TaskListener listener,
                                         String accurevPath,
                                         Launcher launcher,
                                         String stream,
                                         Date buildDate,
                                         Logger logger,
                                         AccurevSCM scm)
           throws IOException, InterruptedException {
	  
       AccurevTransaction latestCodeChangeTransaction = new AccurevTransaction();
       String filterForPollSCM = scm.getFilterForPollSCM();
       String subPath = scm.getSubPath();
       latestCodeChangeTransaction.setDate(AccurevSCM.NO_TRANS_DATE);

       //query AccuRev for the latest transactions of each kind defined in transactionTypes using getTimeOfLatestTransaction
       String[] validTransactionTypes = AccurevServer.DEFAULT_VALID_WORKSPACE_TRANSACTION_TYPES.split(AccurevServer.VTT_DELIM);
      

       listener.getLogger().println(//
               "Checking transactions of type " + Arrays.asList(validTransactionTypes) + //
                       " in stream [" + stream + "]");
     
       Iterator<String> affectedPaths;
       Collection<String> serverPaths;

       final String FFPSCM_DELIM = ",";
      
       Collection<String> Filter_For_Poll_SCM = null;
       
       if(filterForPollSCM != null && !(filterForPollSCM.isEmpty())){
	        String FFPSCM_LIST = filterForPollSCM;
	        FFPSCM_LIST = FFPSCM_LIST.replace(", ", ",");
	        Filter_For_Poll_SCM = new ArrayList<String>(Arrays.asList(FFPSCM_LIST.split(FFPSCM_DELIM)));
       }else{
       	if(subPath != null && !(subPath.isEmpty())){
       		String FFPSCM_LIST = subPath;
   	        FFPSCM_LIST = FFPSCM_LIST.replace(", ", ",");
   	        Filter_For_Poll_SCM = new ArrayList<String>(Arrays.asList(FFPSCM_LIST.split(FFPSCM_DELIM)));
       	}
       }
       for (final String transactionType : validTransactionTypes) {
           try {
               final AccurevTransaction tempTransaction = History.getLatestTransaction(scm, server, accurevEnv, workspace,
                       listener, accurevPath, launcher, stream, transactionType);
               if (tempTransaction != null) {
                   listener.getLogger().println(
                           "Last transaction of type [" + transactionType + "] is " + tempTransaction);
                 
                   if (latestCodeChangeTransaction.getDate().before(tempTransaction.getDate())) {
                   	//check the affected
                   	serverPaths = tempTransaction.getAffectedPaths();
                   	if(tempTransaction.getAffectedPaths().size()>0){
                   		listener.getLogger().println("This transaction seems to have happened after the latest build!!!");
                   		if(filterForPollSCM != null && !(filterForPollSCM.isEmpty())){
		                    		boolean buildRequired = false;
		                    		for(String filterPath: Filter_For_Poll_SCM){
		                        		affectedPaths = serverPaths.iterator();
		                                while(affectedPaths.hasNext()){
			                                	
			                                	if(affectedPaths.next().contains(filterPath)){
			                                		buildRequired = true;
			                                		break;
			                                	}
		                                	
		                                }
		                            }
		                    		if(buildRequired){
		                         	   
		                            }else{
		                         	   return false;
		                            }
	                    		}else{
	                    			if(subPath != null && !(subPath.isEmpty())){

			                    		boolean buildRequired = false;
			                    		for(String filterPath: Filter_For_Poll_SCM){
			                        		affectedPaths = serverPaths.iterator();
			                                while(affectedPaths.hasNext()){
				                                	
				                                	if(affectedPaths.next().contains(filterPath)){
				                                		buildRequired = true;
				                                		break;
				                                	}
			                                	
			                                }
			                            }
			                    		if(buildRequired){
			                         	   
			                            }else{
			                         	   return false;
			                            }		                    		
	                    			}
	                    		}
                   	}
                   }
                   latestCodeChangeTransaction = tempTransaction;
                   
                   //log last transaction information if retrieved
                   if (latestCodeChangeTransaction.getDate().equals(AccurevSCM.NO_TRANS_DATE)) {
                       listener.getLogger().println("No last transaction found.");
                   }
                   else {
                       
                   }
                   if(buildDate.before(latestCodeChangeTransaction.getDate())){
                	   listener.getLogger().println("Last valid trans " + latestCodeChangeTransaction);
                	   return true;
                   }                  
                      
              }
              
           else {
                   listener.getLogger().println("No transactions of type [" + transactionType + "]");
               }
           }
           catch (Exception e) {
               final String msg = "getLatestTransaction failed when checking the stream " + stream + " for changes with transaction type " + transactionType;
               listener.getLogger().println(msg);
               e.printStackTrace(listener.getLogger());
               logger.log(Level.WARNING, msg, e);
           }
       }
       return false;

      
   }
   
    /**
     *
     * @param server
     * @param accurevEnv
     * @param workspace
     * @param listener
     * @param accurevPath
     * @param launcher
     * @param stream
     * @param buildDate
     * @return if there are any new transactions in the stream since the last build was done
     * @throws IOException
     * @throws InterruptedException
     */
   //stream param is of type AccurevStream
   public static boolean checkStreamForChanges(AccurevServer server,
                                          Map<String, String> accurevEnv,
                                          FilePath workspace,
                                          TaskListener listener,
                                          String accurevPath,
                                          Launcher launcher,
                                          AccurevStream stream,
                                          Date buildDate,                                          
                                          Logger logger,
                                          AccurevSCM scm)
            throws IOException, InterruptedException {
        AccurevTransaction latestCodeChangeTransaction = new AccurevTransaction();
        String filterForPollSCM = scm.getFilterForPollSCM();
        String subPath = scm.getSubPath();
        latestCodeChangeTransaction.setDate(AccurevSCM.NO_TRANS_DATE);

        //query AccuRev for the latest transactions of each kind defined in transactionTypes using getTimeOfLatestTransaction
        String[] validTransactionTypes = null;
        if(stream.getType().name().equalsIgnoreCase("workspace")){
        	validTransactionTypes = AccurevServer.DEFAULT_VALID_WORKSPACE_TRANSACTION_TYPES.split(AccurevServer.VTT_DELIM);
        	listener.getLogger().println(//
                    "Checking transactions of type " + Arrays.asList(validTransactionTypes) + //
                            " in workspace [" + stream.getName() + "]");
        }else{
        	
            validTransactionTypes = AccurevServer.DEFAULT_VALID_STREAM_TRANSACTION_TYPES.split(AccurevServer.VTT_DELIM);
            listener.getLogger().println(//
                    "Checking transactions of type " + Arrays.asList(validTransactionTypes) + //
                            " in stream [" + stream.getName() + "]");
        }
        boolean isTransLatestThanBuild = false;

        
      
        Iterator<String> affectedPaths;
        Collection<String> serverPaths;

        final String FFPSCM_DELIM = ",";
       
        Collection<String> Filter_For_Poll_SCM = null;
        
        if(filterForPollSCM != null && !(filterForPollSCM.isEmpty())){
	        String FFPSCM_LIST = filterForPollSCM;
	        FFPSCM_LIST = FFPSCM_LIST.replace(", ", ",");
	        Filter_For_Poll_SCM = new ArrayList<String>(Arrays.asList(FFPSCM_LIST.split(FFPSCM_DELIM)));
        }else{
        	if(subPath != null && !(subPath.isEmpty())){
        		String FFPSCM_LIST = subPath;
    	        FFPSCM_LIST = FFPSCM_LIST.replace(", ", ",");
    	        Filter_For_Poll_SCM = new ArrayList<String>(Arrays.asList(FFPSCM_LIST.split(FFPSCM_DELIM)));
        	}
        }
        for (final String transactionType : validTransactionTypes) {
            try {
                final AccurevTransaction tempTransaction = History.getLatestTransaction(scm, server, accurevEnv, workspace,
                        listener, accurevPath, launcher, stream.getName(), transactionType);
                if (tempTransaction != null) {
                    listener.getLogger().println(
                            "Last transaction of type [" + transactionType + "] is " + tempTransaction);
                  
                    if (latestCodeChangeTransaction.getDate().before(tempTransaction.getDate())) {
                    	//check the affected
                    	serverPaths = tempTransaction.getAffectedPaths();
                    	if(tempTransaction.getAffectedPaths().size()>0){                    		
                    		if(filterForPollSCM != null && !(filterForPollSCM.isEmpty())){
		                    		boolean buildRequired = false;
		                    		for(String filterPath: Filter_For_Poll_SCM){
		                        		affectedPaths = serverPaths.iterator();
		                                while(affectedPaths.hasNext()){			                                	
			                                	if(affectedPaths.next().contains(filterPath)){
			                                		buildRequired = true;
			                                		break;
			                                	}		                                	
		                                }
		                            }
		                    		if(buildRequired){
		                         	   
		                            }else{
		                         	   return false;
		                            }
	                    		}else{
	                    			if(subPath != null && !(subPath.isEmpty())){

			                    		boolean buildRequired = false;
			                    		for(String filterPath: Filter_For_Poll_SCM){
			                        		affectedPaths = serverPaths.iterator();
			                                while(affectedPaths.hasNext()){
				                                	
				                                	if(affectedPaths.next().contains(filterPath)){
				                                		buildRequired = true;
				                                		break;
				                                	}
			                                	
			                                }
			                            }
			                    		if(buildRequired){
			                         	   
			                            }else{
			                         	   return false;
			                            }		                    		
	                    			}
	                    		}
                    	}
                    }
                    latestCodeChangeTransaction = tempTransaction;  
                    if (latestCodeChangeTransaction.getDate().equals(AccurevSCM.NO_TRANS_DATE)) {
                        listener.getLogger().println("No last transaction found.");
                    }
                    //log last transaction information if retrieved
                    if(buildDate != null && buildDate.before(latestCodeChangeTransaction.getDate())){
                 	   listener.getLogger().println("Last valid trans " + latestCodeChangeTransaction);
                 	   isTransLatestThanBuild = true;                 	   
                    }  
                       
                    }
               
            else {
                    listener.getLogger().println("No transactions of type [" + transactionType + "]");
                }
            }
            catch (Exception e) {
                final String msg = "getLatestTransaction failed when checking the stream " + stream.getName() + " for changes with transaction type " + transactionType;
                listener.getLogger().println(msg);
                e.printStackTrace(listener.getLogger());
                logger.log(Level.WARNING, msg, e);
            }
        }
        if(isTransLatestThanBuild)
        	return true;
        else        
        	return false;
    }
}
