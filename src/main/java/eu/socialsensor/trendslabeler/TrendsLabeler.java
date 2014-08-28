/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package eu.socialsensor.trendslabeler;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.util.JSON;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.process.DocumentPreprocessor;
import edu.stanford.nlp.process.PTBTokenizer.PTBTokenizerFactory;
import edu.stanford.nlp.util.CoreMap;
import eu.socialsensor.documentpivot.preprocessing.StopWords;
import eu.socialsensor.framework.client.dao.MediaItemDAO;
import eu.socialsensor.framework.client.dao.StreamUserDAO;
import eu.socialsensor.framework.client.dao.impl.MediaItemDAOImpl;
import eu.socialsensor.framework.client.dao.impl.StreamUserDAOImpl;

import eu.socialsensor.framework.common.domain.Item;
import eu.socialsensor.framework.common.domain.MediaItem;
import eu.socialsensor.framework.common.domain.StreamUser;
import eu.socialsensor.framework.common.domain.WebPage;
import eu.socialsensor.framework.common.domain.dysco.Dysco;
import eu.socialsensor.framework.common.domain.dysco.Entity;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;

import java.io.Reader;
import java.io.StringReader;
import java.lang.String;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;


import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import org.htmlparser.Node;
import org.htmlparser.Parser;
import org.htmlparser.filters.OrFilter;
import org.htmlparser.filters.TagNameFilter;
import org.htmlparser.util.NodeList;
import org.htmlparser.util.ParserException;
import org.htmlparser.util.SimpleNodeIterator;

import uk.ac.shef.wit.simmetrics.similaritymetrics.AbstractStringMetric;
import uk.ac.shef.wit.simmetrics.similaritymetrics.Levenshtein;

public class TrendsLabeler {
	
	
	//	public static final String[] BREAKING_ARRAY = new String[] { 
	//		"6017542", "5402612", "428333", 
	//		"23484039", "15108702", "18767649",
	//		"18424289", "87416722", "384438102", 
	//		"7587032", "361501426", "612473", 
	//		"14138785", "15012486", "11014272",
	//		"14569869", "354267800", "48833593", 
	//		"807095", "7587032", "113050195", 
	//		"15110357", "7905122", "16672510", 
	//		"788524", "10977192", "14138785", 
	//		"138387125", "19656220", "19536881"
	//	};

	
    static MediaItemDAO miDAO;
    static StreamUserDAO suDAO;
    static{
        try {    
            miDAO=new MediaItemDAOImpl("socialmdb1.atc.gr","MediaItemsDB","MediaItems");
            suDAO=new StreamUserDAOImpl("socialmdb1.atc.gr","StreamUsersDB","StreamUsers");
        } catch (Exception ex) {
            Logger.getRootLogger().info("TRENDS LABELLER. Could not create mediaitemdao object");
            java.util.logging.Logger.getLogger(TrendsLabeler.class.getName()).log(Level.SEVERE, null, ex);
        }
        System.setProperty ("sun.net.client.defaultReadTimeout", "7000");
        System.setProperty ("sun.net.client.defaultConnectTimeout", "7000");
    }
    
    public static final String[] BREAKING_ARRAY = new String[]{
		"BreakingNews", "BBCBreaking", "cnnbrk", 
		"WSJbreakingnews", "ReutersLive", "CBSTopNews", 
		"AJELive", "SkyNewsBreak", "ABCNewsLive",
		"SkyNews", "BreakingNewsUK", "BBCNews", 
		"TelegraphNews", "CBSNews", "ftfinancenews", 
		"Channel4News", "5_News", "24HOfNews", 
		"nytimes", "SkyNews", "skynewsbiz", 
		"ReutersBiz", "guardiantech", "mediaguardian", 
		"guardiannews", "fttechnews", "telegraphnews", 
		"telegraphsport", "telegraphbooks", "telefinance" 
	};

    public static final String titleSeparators="[|-]";
    public static final double thresholdMediaName=0.3;
    
    
    public static final Set<String> BREAKING_ACCOUNTS = new HashSet<String>(Arrays.asList(BREAKING_ARRAY));    
    public static double url_threshold_similarity=0.2;

    public static void main(String[] args){
//        String input="20th #commonwealthgames set to #start today (I am #veryhappy). #blaaa [underway this evening] #cfc #29028 #we ";
//        String input="Qpr supporting bald bloke, slightly past use by date but dont let that put you off http://a...";
//        String input="Qpr supporting bald bloke, slightly past use by date but dont let that put you off...";
        String input="5th anniversary of Sir Bobby Robson's passing today. A gentle man, a great football man, never forgotten. Love to his family and friends.";
        String currentTitleRGUb=getCleanedTitleMR(input);
        System.out.println(currentTitleRGUb);
    }
   /* 
    public static void main( String[] args )
    {
        System.out.println("Getting items");
        //List<Item> items=itemDAO.getLatestItems(10000);
        String filename="D:\\twitter_data\\facup\\influencers_per_topic_all\\5_4_2012_16_16.json";
        List<Item> items=loadItemsFromFile(filename);
        Dysco newDysco=new Dysco();
        newDysco.setItems(items);
        System.out.println("no of items :"+newDysco.getItems().size());
        
        newDysco.setTitle(findPopularTitleRGU(newDysco));
        
        System.out.println("Title: "+newDysco.getTitle());
        System.out.println("Author: "+newDysco.getAuthor());
        System.out.println("Media url: "+newDysco.getMainMediaUrl());
        System.out.println("Story type: "+newDysco.getStoryType());
        
    }
*/    
    
    /*public static void main(String[] args){
        ItemDAO itemdao=null;
        try {
            itemdao = new ItemDAOImpl("social1.atc.gr");
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(TrendsLabeler.class.getName()).log(Level.SEVERE, null, ex);
        }
        List<Item> items=itemdao.getLatestItems(1000);
        Set<String> entities=new HashSet<String>();
        for(Item item:items){
            List<String> sentences=getSentences1(item.getTitle(),entities);
            //for(String sentence:sentences)
                //System.out.println(sentence);
        }
        
    }*/
    
    
    /*
    public static void main(String[] args) {
        
        DyscoDAO dyscoDAO=null;
        try {
            dyscoDAO = new DyscoDAOImpl("social1.atc.gr","dyscos","items","MediaItems");
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(TrendsLabeler.class.getName()).log(Level.SEVERE, null, ex);
        }
//        DyscoDAO dyscoDAO = new DyscoDAOImpl("social1.atc.gr"); 
        try{
//        DyscoDAO dyscoDAO = new DyscoDAOImpl("social1.atc.gr","dyscos","items","MediaItems");        try{
            BufferedWriter bw=new BufferedWriter(new FileWriter("D:\\topicTitlesChanges.txt"));
            int n_to_process=1;
//            int n_to_process=dyscoIds.length;
            String title=null;
            for(int i=0;i<n_to_process;i++){

                Dysco dysco=dyscoDAO.findDysco(dyscoIds[i]);
                List<Entity> ents=dysco.getEntities();
                bw.append("");
                bw.append("-------------------------------\n");
                bw.append("Dysco ID :\n\t"+dyscoIds[i]);
                bw.newLine();
                bw.append("Old title is: \n\t"+dysco.getTitle());
                bw.newLine();
                
                System.out.println("----------------");
                System.out.println("Old title is: \n"+dysco.getTitle());

                String new_title_no_urls=findPopularTitle(dysco);
                title=new_title_no_urls;
                String new_title_urls=findPopularTitleCERTHIncludeURLs(dysco);
                dysco.setTitle(new_title_no_urls);
                dyscoDAO.updateDysco(dysco);
                System.out.println("New title (no URLS):\n"+new_title_no_urls);
                System.out.println("New title (with URLS):\n"+new_title_urls);
                System.out.println("");
                bw.append("New title (no URLS):\n\t"+new_title_no_urls);
                bw.newLine();
                bw.append("New title (with URLS):\n\t"+new_title_urls);
                bw.newLine();
                bw.newLine();
                List<Item> items=dysco.getItems();

                System.out.println("----------");
                for(Item tmp_item:items){
                    System.out.println(tmp_item.getTitle());
                }

                bw.append("List of tweets: \n");
                for(Item tmp_item:items){
                    bw.append("\t"+tmp_item.getTitle()+"\n");
                }
                bw.newLine();
                
                
                System.out.println("Entities : ");
                if(ents!=null){
                    for(Entity ent:ents)
                        System.out.println(ent.getName()+" "+ent.getType());
                }
                else{
                    System.out.println("IS NULL");
                }

                bw.append("Entities: \n");
                if(ents!=null){
                    for(Entity ent:ents)
                        bw.append("\t"+ent.getName()+" - "+ent.getType()+"\n");
                }
                else{
                    bw.append("\tNULL");
                }
            }        
            bw.close();
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }
    */
    
    static Extractor extr = new Extractor();

    private final static Integer MAX_TOKEN_LENGTH = 20;

    public static double bestJaccard(ArrayList<Set<String>> sentences,Set<String> newSentence){
        double best_sim=Double.MIN_VALUE;
        for(Set<String> next_sentence:sentences){
            double sim=Jaccard(next_sentence,newSentence);
            if(sim>best_sim)
                best_sim=sim;
        }
        return best_sim;
    }
    
    public static double Jaccard(Set<String> terms1,Set<String> terms2){
        Set<String> intersection = new HashSet<String>(terms1);
        intersection.retainAll(terms2);
        Set<String> union = new HashSet<String>(terms1);
        union.addAll(terms2);
        if(union.size()>0)
            return ((double) intersection.size())/((double) union.size());
        else
            return 0.0;
    }
    
    
    public static String findPopularTitleCERTHIncludeURLs(Dysco dysco){
        List<Item> items=dysco.getItems();
        Set<String> entities=new HashSet<String>();
        List<Entity> ents=dysco.getEntities();
        for(Entity ent:ents)
            entities.add(ent.getName());
        List<String> textItems=new ArrayList<String>();
        Map<String,List<String>> perURLitems=new HashMap<String,List<String>>();
        ArrayList<Set<String>> cleanedTokens=new ArrayList<Set<String>>();
        for(Item item_tmp:items){
            List<String> sentences=getSentences1_old(item_tmp.getTitle(),entities);
            textItems.addAll(sentences);
            for(String sentence:sentences){
                cleanedTokens.add(tokenizeClean(sentence));
            }
            URL[] tmp_urls=item_tmp.getLinks();
            if(tmp_urls!=null){
                for(int i=0;i<tmp_urls.length;i++){
                    String tmp_url_str=tmp_urls[i].toString();
                    if(!perURLitems.containsKey(tmp_url_str)){
                        List<String> key_elements=grabKeyElementsFromURL(tmp_url_str);
                        perURLitems.put(tmp_url_str, key_elements);
                    }
                }
            }
            List<WebPage> webpages=item_tmp.getWebPages();
            if(webpages!=null){
                for(WebPage tmp_webpage:webpages){
                    String tmp_url_str=tmp_webpage.getUrl();
                    if(!perURLitems.containsKey(tmp_url_str)){
                        List<String> key_elements=grabKeyElementsFromURL(tmp_url_str);
                        List<String> new_cands=new ArrayList<String>();
                        for(String tmp_cand:key_elements){
                            List<String> tmp_sentences=getSentences1_old(tmp_cand,entities);
                            new_cands.addAll(tmp_sentences);
                        }
                        perURLitems.put(tmp_url_str, new_cands);
                    }
                }
            }

        }
        
        for(List<String> tmp_key_elements:perURLitems.values()){
            for(String nextSentence:tmp_key_elements){
                Set<String> next_cand=tokenizeClean(nextSentence);
                double best_similarity=bestJaccard(cleanedTokens,next_cand);
                if(best_similarity>url_threshold_similarity){
                    String cleanedSentence=extractor.cleanText(nextSentence);
                    textItems.add(cleanedSentence);
                }
            }
        }
        
        String title=findPopularTitleNew(textItems);
        if(((title==null)||(title.trim().length()==0))&&(textItems.size()>0)){
            System.out.println("NULL CASE 1 : "+title+"----");
            title=extractor.cleanText(textItems.get(0));       
        }
        if(((title==null)||(title.trim().length()==0))&&(textItems.size()>0)){
            System.out.println("NULL CASE 2");
            title=textItems.get(0);
        }
        if(((title==null)||(title.trim().length()==0))&&(items.size()>0)){
            System.out.println("NULL CASE 3");
            title=items.get(0).getTitle();
        }

        
        return title;
    }
    
    public static Set<String> tokenizeClean(String sentence){
        HashSet<String> tokens=new HashSet<String>();
        String cleanedSentence=extractor.cleanText(sentence);       
        String[] parts=cleanedSentence.split("\\s");
        for(int i=0;i<parts.length;i++)
            if(!StopWords.isStopWord(parts[i]))
                tokens.add(parts[i].toLowerCase());
        return tokens;
    }
    
    
    public static String findPopularTitle_old(Dysco dysco){
        List<Item> items=dysco.getItems();
        // Logger.getRootLogger().info("Title extractor : Examining case 1 (getting title from most popular url)");
        // Case 1, there are urls that point to a webpage that has a title
        // pick the title of the most popular page.
        
        Map<String,Integer> url_counts=new HashMap<String,Integer>();
        // Logger.getRootLogger().info("Title extractor  (case 1) : finding most popular URL");
        for (Item item_tmp:items){
            URL[] tmp_urls=item_tmp.getLinks();
            // Logger.getRootLogger().info("Title extractor  (case 1) : got list of urls will now expand");
            if (tmp_urls!=null){
                for (int i=0;i<tmp_urls.length;i++){
                    String resolved=null;
					// Logger.getRootLogger().info("Title extractor  (case 1) : will now expand" + tmp_urls[i].toString());
                    resolved = URLDeshortener.expandFast(tmp_urls[i].toString());
                    resolved = tmp_urls[i].toString();
                    // Logger.getRootLogger().info("Title extractor  (case 1) : expanded"+tmp_urls[i].toString());
                    if(resolved!=null){
                        Integer count=url_counts.get(resolved);
                        if(count == null)
                            url_counts.put(resolved,1);
                        else
                            url_counts.put(resolved,count+1);
                    }
                }
            }
            // Logger.getRootLogger().info("Title extractor  (case 1) : expanded, will now count");
        }
        // Logger.getRootLogger().info("Title extractor  (case 1) : found most popular URL");
        if (url_counts.size()>0){
            int maximum=Integer.MIN_VALUE;
            String most_popular_url=null;
            for (Entry<String,Integer> tmp_entry:url_counts.entrySet()){
                if (tmp_entry.getValue()>maximum){
                    maximum=tmp_entry.getValue();
                    most_popular_url=tmp_entry.getKey();
                }
            }
            if (most_popular_url!=null){
                // Logger.getRootLogger().info("Title extractor  (case 1) : fetching title from most popular url");
                String candidate_title=grabTitleFromURL(most_popular_url);
                candidate_title=StringEscapeUtils.unescapeHtml(candidate_title);
                if ((candidate_title!=null)&&(!candidate_title.equals(""))){
                        String[] parts1=candidate_title.split("\\|");
                        int max_length=parts1[0].length();
                        candidate_title=parts1[0];
                        for(int p=1;p<parts1.length;p++)
                            if(parts1[p].length()>max_length){
                                max_length=parts1[p].length();
                                candidate_title=parts1[p];
                            }
                                
                        // Logger.getRootLogger().info("Title extractor  (case 1) : cleaning candidate title");
                        candidate_title=cleanTitleFromCommonMediaNames(candidate_title);
                        // Logger.getRootLogger().info("Title extractor  (case 1) : getting site name");
                        String mediaName=getSiteNameFromURL(most_popular_url).toLowerCase();
                        String[] titleParts=candidate_title.split(titleSeparators);
                        candidate_title="";
						
                        AbstractStringMetric metric = new Levenshtein();
                        for(int i=0;i<titleParts.length;i++){
                            String next_part=titleParts[i].trim();
                            float mediaNameSimilarity=metric.getSimilarity(mediaName, next_part.replace(" ", "").toLowerCase());
                            if(mediaNameSimilarity<thresholdMediaName){
                                candidate_title=candidate_title+next_part+" ";
                            }
                            
                        }
                        candidate_title=candidate_title.trim();
                        if ((candidate_title!=null)&&(!candidate_title.equals(""))&&(!candidate_title.toLowerCase().equals("home")))
                            return candidate_title;
                }
            }
        }
        
        
        
        // Logger.getRootLogger().info("Title extractor : Examining case 2 (message posted by listed user)");
        Set<String> entities=new HashSet<String>();
        List<Entity> ents=dysco.getEntities();
        for(Entity ent:ents)
            entities.add(ent.getName());
        for(Item item_tmp:items){
            if(BREAKING_ACCOUNTS.contains(item_tmp.getAuthorScreenName())){
                String candidate_title=item_tmp.getTitle();
                List<String> parts=getSentences1_old(candidate_title,entities);
                candidate_title="";
                for(String part:parts) 
					candidate_title=candidate_title+" "+part;
                candidate_title=candidate_title.trim();
                candidate_title=StringEscapeUtils.unescapeHtml(candidate_title);
                if(candidate_title.endsWith(":"))
                    candidate_title=candidate_title.substring(0,candidate_title.length()-2)+".";

                
                if ((candidate_title!=null)&&(!candidate_title.equals("")))
                    return candidate_title;
            }
        }
        
        // Case 3, default CERTH procedure: finding most popular sentence in all tweets
        // Logger.getRootLogger().info("Title extractor : Examining case 3 (most popular sentence)");
        String candidate_title=findPopularTitleCERTH(dysco);
        candidate_title=StringEscapeUtils.unescapeHtml(candidate_title);
        if(candidate_title.endsWith(":"))
            candidate_title=candidate_title.substring(0,candidate_title.length()-1)+".";
        return candidate_title;
    
    }

    public static String findPopularTitle(Dysco dysco) throws MalformedURLException {
		
		char firstChar;
		
		List<Item> items = dysco.getItems();
		Extractor extractor = new Extractor();
		
		for (Item item : items) {
			URL[] itemURL = item.getLinks();
			if (itemURL.length == 0) {
				String itemText = item.getTitle();
	
				// preprocess to text so as to remove urls with &hellip; at the
				// end --> incomplete URLS
				itemText = StringEscapeUtils.escapeHtml(itemText);
				itemText = itemText.replaceAll("http\\:*.*&hellip;\\z", "");
				itemText = itemText.replaceAll("-\\s*\\z", "");
				itemText = itemText.replaceAll("&hellip;\\z", "");
				itemText = StringEscapeUtils.unescapeHtml(itemText);
	
				extractor.setExtractURLWithoutProtocol(false); // in order to
																// not extract
																// URLs without
																// protocol
				List<String> extractedURLs = extractor.extractURLs(itemText);
				if (!extractedURLs.isEmpty()) {
					itemURL = new URL[extractedURLs.size()];
					int index = 0;
					for (String extractedURL : extractedURLs) {
						URL extrURL = new URL(extractedURL);
						itemURL[index] = extrURL;
						index++;
					}
				}
				item.setLinks(itemURL);
			}
		}
		extractor.setExtractURLWithoutProtocol(true);
		
		
		// Case 1
		// Logger.getRootLogger()
				// .info("Title extractor : Examining case 1 (getting title from most popular url)"); 
		Map<String, Integer> url_counts = new HashMap<String, Integer>();
		// Logger.getRootLogger().info(
				// "Title extractor  (case 1) : finding most popular URL");
		
		long lStartTime = System.currentTimeMillis();
		
		int urlCounter = 0;
		
		for (Item item_tmp : items) {
			URL[] tmp_urls = item_tmp.getLinks();
			// Logger.getRootLogger()
					// .info("Title extractor  (case 1) : got list of urls will now expand");
			if (tmp_urls != null) {
				urlCounter++;
				for (int i = 0; i < tmp_urls.length; i++) {
					String resolved = null;
//					Logger.getRootLogger().info(
//							"Title extractor  (case 1) : will now expand "
//									+ tmp_urls[i].toString());
//					resolved = URLDeshortener
//							.expandFast(tmp_urls[i].toString()); //URL expansion should be disabled
					resolved = tmp_urls[i].toString();
					
					if (resolved != null) {
//						Logger.getRootLogger().info(
//								"Title extractor  (case 1) : expanded "
//										+ resolved.toString());
						Integer count = url_counts.get(resolved);
						if (count == null)
							url_counts.put(resolved, 1);
						else
							url_counts.put(resolved, count + 1);
					}
				}
			}
//			Logger.getRootLogger().info(
//					"Title extractor  (case 1) : expanded, will now count");
		}
//		Logger.getRootLogger().info(
//				"Title extractor  (case 1) : found most popular URL");		
		
		long lEndTime = System.currentTimeMillis();
		long difference = lEndTime - lStartTime;
		
//		System.out.println("Time elapsed while " + urlCounter + " no of URLs were expanded          : "
//				+ difference);
		
		String fall_back_case1 = null;
		
		if (url_counts.size() > 0) {
			int maximum = Integer.MIN_VALUE;
			String most_popular_url = null;
			for (Entry<String, Integer> tmp_entry : url_counts.entrySet()) {
				if (tmp_entry.getValue() > maximum) {
					maximum = tmp_entry.getValue();
					most_popular_url = tmp_entry.getKey();
				}
			}

			if (most_popular_url != null) {
//				Logger.getRootLogger()
//						.info("Title extractor  (case 1) : fetching title from most popular url");	
				
				String candidate_title = grabTitleFromURL(most_popular_url);
				candidate_title = StringEscapeUtils
						.unescapeHtml(candidate_title);

				if ((candidate_title != null) && (!candidate_title.equals(""))) {
					candidate_title = candidate_title.replaceAll("(^(T|t)witter\\s\\/\\s.{1,}?\\:)", ""); //MR added : cases related to twitter titles from URLs

					candidate_title = extr.removeMultiplePunctuation(candidate_title); //added by MR
					candidate_title = candidate_title
							.replaceAll("\\s{2,}", " "); //added by MR
					
					String[] parts1 = candidate_title.split("\\|");
					int max_length = parts1[0].length();
					candidate_title = parts1[0]; // splits title via | and keeps
													// the longest sentence from
													// split
					for (int p = 1; p < parts1.length; p++)
						if (parts1[p].length() > max_length) {
							max_length = parts1[p].length();
							candidate_title = parts1[p];
						}

//					Logger.getRootLogger()
//							.info("Title extractor  (case 1) : cleaning candidate title");
					candidate_title = cleanTitleFromCommonMediaNames(candidate_title);
//					Logger.getRootLogger().info(
//							"Title extractor  (case 1) : getting site name"); // remove
																				// media
																				// name
																				// from
																				// title
					String mediaName = getSiteNameFromURL(most_popular_url)
							.toLowerCase();
					String[] titleParts = candidate_title
							.split(titleSeparators); // sometimes "-" exists as 
														// non-separator.. MR
					candidate_title = "";
					AbstractStringMetric metric = new Levenshtein();

					for (int i = 0; i < titleParts.length; i++) {
						String next_part = titleParts[i].trim();
						// float mediaNameSimilarity = 0.0f; // MR change it to
						// something BIG in order not to take it into account
						float mediaNameSimilarity = metric.getSimilarity(
								mediaName, next_part.replace(" ", "")
										.toLowerCase());
						if (mediaNameSimilarity < thresholdMediaName) {
							candidate_title = candidate_title + next_part + " ";
						}
					}

					candidate_title = candidate_title.trim();
					candidate_title = removeNameDots(candidate_title);
					
					fall_back_case1 = candidate_title;
					candidate_title = TrendsLabeler
							.getCleanedTitleMR(candidate_title);
							
					if ((candidate_title != null) // if title is short 
							&& (!candidate_title.equals(""))
							&& (!candidate_title.toLowerCase().equals("home"))
							&& (candidate_title.length() > 7)) // if title is
																// too short //added by MR
					{
						if (!candidate_title.matches(".*[\\W]$")) //if there is no symbol at the end, adds "."
							candidate_title = candidate_title + ".";
						
						// System.out.print("Case 1 selected | ");
						firstChar = candidate_title.charAt(0);
						return Character.toUpperCase(firstChar) + candidate_title.substring(1);
						
					}
						
				}//end of if candidate_title != null
			}//end of if most_popular_url =! null
		}//end of if url.count has values

		// case 2 : if tweet comes from Breaking_accounts
		// Logger.getRootLogger()
		// .info("Title extractor : Examining case 2 (message posted by listed user)");
		Set<String> entities = new HashSet<String>();
		List<Entity> ents = dysco.getEntities();
		for (Entity ent : ents)
			entities.add(ent.getName());
		for (Item item_tmp : items) {
			if (BREAKING_ACCOUNTS.contains(item_tmp.getAuthorScreenName())) {
				String candidate_title = item_tmp.getTitle();
				List<String> parts = getSentences1(candidate_title, entities);
				candidate_title = "";
				for (String part : parts)
					candidate_title = candidate_title + " " + part;
				candidate_title = candidate_title.trim();
				candidate_title = StringEscapeUtils
						.unescapeHtml(candidate_title);
				if (candidate_title.endsWith(":"))
					candidate_title = candidate_title.substring(0,
							candidate_title.length() - 2) + ".";
				
				if (!candidate_title.matches(".*[\\W]$")) //if there is no symbol at the end, adds "."
					candidate_title = candidate_title + ".";

				if ((candidate_title != null) && (!candidate_title.equals(""))){
					// System.out.print("Case 2 selected | ");
					firstChar = candidate_title.charAt(0);
					return Character.toUpperCase(firstChar) + candidate_title.substring(1);
				}

			}
		}

		// Case 3, default certh procedure: finding most popular sentence in all
		// tweets
		// Logger.getRootLogger().info(
		// "Title extractor : Examining case 3 (most popular sentence)");
		String candidate_title = findPopularTitleCERTH(dysco);
		//here we have to apply cleaning in case of title derived from item[0]
		candidate_title = TrendsLabeler.getCleanedTitleMR(candidate_title);
		candidate_title = candidate_title.replaceAll(Extractor.urlRegExp, "");
//		candidate_title = candidate_title.replaceAll("^[^\\w(\\[]+", "").replaceAll("[^\\w\\)\\]\\?]+$", "").replaceAll("\\s{2,}","");
		

		if ((candidate_title.length()<7) || candidate_title == null)
			candidate_title = fall_back_case1; //case 1 without cleaning - title from URL as it is
		
		if (candidate_title == null || candidate_title == ""){ //means that fall_back_case = null
			Random randomGenerator = new Random();
			candidate_title = dysco.getItems()
					.get(randomGenerator.nextInt(dysco.getItems().size()))
					.getTitle();
			// candidate_title = dysco.getItems().get(0).getTitle(); // text from first Item without cleaning.
			
		}
		
		
		candidate_title = StringEscapeUtils.unescapeHtml(candidate_title);
		
		if (candidate_title.endsWith(":"))
			candidate_title = candidate_title.substring(0,
					candidate_title.length() - 1)
					+ ".";
		
		if (!candidate_title.matches(".*[\\W]$")) //if there is no symbol at the end, adds "."
			candidate_title = candidate_title + ".";
		
		
		// System.out.print("Case 3 selected | ");
		firstChar = candidate_title.charAt(0);
		return Character.toUpperCase(firstChar) + candidate_title.substring(1);



	}	
	
    public static String findPopularTitleCERTH_old(Dysco dysco){
        List<Item> items=dysco.getItems();
        List<String> textItems=new ArrayList<String>();
        Set<String> entities=new HashSet<String>();
        List<Entity> ents=dysco.getEntities();
        for(Entity ent:ents)
            entities.add(ent.getName());
//        Logger.getRootLogger().info("Title extractor (case 3): Getting candidate sentences");
        for(Item item_tmp:items){
            List<String> sentences=getSentences1_old(item_tmp.getTitle(),entities);
            textItems.addAll(sentences);
        }
            
//        Logger.getRootLogger().info("Title extractor (case 3): Finding most popular sentence");
        String title=findPopularTitleNew(textItems);
        if(((title==null)||(title.trim().length()==0))&&(textItems.size()>0))
            title=extractor.cleanText(textItems.get(0));       
        if(((title==null)||(title.trim().length()==0))&&(textItems.size()>0))
            title=textItems.get(0);
        if(((title==null)||(title.trim().length()==0))&&(items.size()>0))
            title=items.get(0).getTitle();
        return title.replaceAll("\\s{2,}", " ");
        
    }


	public static String findPopularTitleCERTH(Dysco dysco) {
		List<Item> items = dysco.getItems();
		
		// get titles from items and add them in a List<String>
		List<String> dyscoText = new ArrayList<String>(items.size());
		for (Item item : items) {
			dyscoText.add(item.getTitle());
		}
//		System.out.println("Total number of Items in Dysco : "
//				+ dyscoText.size());
//		HashMap<String, Integer> uniqueTitlesMap = DuplicateTextSearcher.findDuplicateItems(dyscoText);
		HashMap<String, Integer> uniqueTitlesMap = new HashMap<String, Integer>();
		for (String text : dyscoText) {
			Integer count = uniqueTitlesMap.get(text);
			if (count == null)
				uniqueTitlesMap.put(text, 1);
			else
				uniqueTitlesMap.put(text, count + 1);
		}
		
		List<String> textItems = new ArrayList<String>(); // keeps all sentences
															// per item | no
															// separation per
															// item
		Set<String> entities = new HashSet<String>();
		List<Entity> ents = dysco.getEntities();
		for (Entity ent : ents)
			entities.add(ent.getName());
		// Logger.getRootLogger().info(
		// "Title extractor (case 3): Getting candidate sentences");
		
		//utilize unique items
		for (Map.Entry<String, Integer> entry : uniqueTitlesMap.entrySet()) {
			String text = entry.getKey();
			Integer frequency = entry.getValue();
			List<String> sentences = getSentences1(text, entities);
			for (int i = 0; i < frequency; i++) {
				textItems.addAll(sentences);
			}
		}
		
		// Logger.getRootLogger().info(
		// "Title extractor (case 3): Finding most popular sentence");
		String title = findPopularTitleNew(textItems);

		if (((title == null) || (title.trim().length() == 0))
				&& (textItems.size() > 0))
			title = extr.cleanText(textItems.get(0));
		if (((title == null) || (title.trim().length() == 0))
				&& (textItems.size() > 0))
			title = textItems.get(0);
		if (((title == null) || (title.trim().length() == 0))
				&& (items.size() > 0))
			title = items.get(0).getTitle();
		
		// return title.replaceAll("\\s{2,}"," ");
		if ((StringUtils.countMatches(title, "\"") == 1)) // if only one (") is
															// included in the
															// title then it
															// should be removed
			title = title.replaceAll("\"", "");
		return title.replaceAll("^[^\\w(\\[]+", "")
				.replaceAll("[^\\w\\.\\!\\?\\)\\]]+$", "")
				.replaceAll("\\s{2,}", " ");

	}

	
    private static String findPopularTitleNew(List<String> textItems) {
        List<String> titles = textItems;
        
        List<String> filteredTitles = getFilteredTitles(titles);
        if(filteredTitles.size() == 0) {
            return null;
        }

//        List<String> combinations = new ArrayList<String>();
        HashMap<String,Integer> combinationsCounts = new HashMap<String,Integer>();
        List<String> filteredTitlesFinal = new ArrayList<String>();
        
        for(String title : filteredTitles) {
            String[] titleTokens = title.trim().split("[\\s]+");

            List<String> tokens = Arrays.asList(titleTokens);
            String str_concat="";
            for(String tmp_str:tokens)
                str_concat=str_concat+tmp_str+" ";
            str_concat=str_concat.trim();
			// keeps filtered titles as final in corresponding variable
            filteredTitlesFinal.add(str_concat);
			// stores combinations (n-grams) from each title and counts
			// frequencies
            addCombinationsCounts(str_concat,combinationsCounts);
        }

        if(combinationsCounts.size() == 0){
            return null;
        }
		// adds frequencies to every same occurrence of title or subtitle
        Map<String, RankedTitle> titlesFrequencies = new HashMap<String, RankedTitle>();
        for(Entry<String,Integer> combination : combinationsCounts.entrySet()){
//        for(String combination : combinations){
            RankedTitle rankTitle = titlesFrequencies.get(combination.getKey());
            if (rankTitle == null){
                titlesFrequencies.put(combination.getKey(), new RankedTitle(combination.getKey(), combination.getValue()));
            }else{
                rankTitle.setFrequency(rankTitle.getFrequency()+combination.getValue());
            }
        }

        List<RankedTitle> listOfRankedTitles = new ArrayList<RankedTitle>();
        for (Entry<String, RankedTitle> entry2 : titlesFrequencies.entrySet()){
            listOfRankedTitles.add(entry2.getValue());
            
        }

        //candidates and final selection
        List<String> finalSelectedTitles = getFinalTitles(listOfRankedTitles, filteredTitles.size());
        if(finalSelectedTitles.size()>0){
            String best_phrase=finalSelectedTitles.get(0);
//            System.out.println("Best phrase : "+best_phrase);
            Map<String,Integer> counts=new HashMap<String,Integer>();
            for(String tmp_str:textItems){
                if((tmp_str.contains(best_phrase.trim()))){
                    Integer cc=counts.get(tmp_str);
					// puts whole sentence that includes best_phrase
                    if(cc==null)
                        counts.put(tmp_str,1);
                    else
                        counts.put(tmp_str,cc+1);
                }
            }
			// counts is a map that keeps filtered titles and freq. of
			// occurrences in whole items
            String best_sentence="";
            int best_count=-1;
			// gets the most occurred sentence that includes best phrase
            for(Entry<String,Integer> tmp_entry:counts.entrySet())
                if(tmp_entry.getValue()>best_count){
                    best_count=tmp_entry.getValue();
                    best_sentence=tmp_entry.getKey();
                }
            best_sentence=extr.cleanText(best_sentence);
            return best_sentence;
        }
        else {
            return null;
        }

    }
    
    
    public static void addCountsToCounts(HashMap<String,Integer> addIt,HashMap<String,Integer> addTarget){
        for(Entry<String,Integer> entry_tmp:addIt.entrySet()){
            Integer count_tmp=addTarget.get(entry_tmp.getKey());
            if(count_tmp==null)
                addTarget.put(entry_tmp.getKey(), entry_tmp.getValue());
            else
                addTarget.put(entry_tmp.getKey(), count_tmp+entry_tmp.getValue());
        }
    }
    
    private static Extractor extractor = new Extractor();

    public static List<String> getClusterTitles(List<String> textItems) {
            List<String> titles = new ArrayList<String>();
            for(String tmp_text : textItems) {
                String title = extractor.cleanText(tmp_text);
                titles.add(title);
            }
            return titles;
    }

    public static List<String> getFilteredTitles(List<String> titles) {
        List<String> filteredTitles = new ArrayList<String>();

        for(String title : titles){
            int numberOfLetters = 0;
            if(title == null){
                continue;
            }
            for(int i = 0; i < title.length(); i++){
                if(Character.isLetter(title.charAt(i))){
                    numberOfLetters++;
                }
            }
            //more than 4 letters
            if(numberOfLetters <= 13){
                continue;
            }
            if(hasHighDigitToletterRatio(title, 0.5)){
                continue;
            }
            filteredTitles.add(title);
        }

        return filteredTitles;
    }

    public static List<String> getFinalTitles(List<RankedTitle> listOfRankedTitles, int numberOfPhotos) {

        for(RankedTitle title : listOfRankedTitles){
            int titleLength = title.getTitle().split(" ").length;
            title.setFrequency(title.getFrequency() * titleLength);
        }

        //rerankTitlesByNumberOfPhotos(listOfRankedTitles, numberOfPhotos);

        rerankRedundantSingleTokenTitles(listOfRankedTitles);
        rerankMaximumTokenLengthWithinATitle(listOfRankedTitles);
        Collections.sort(listOfRankedTitles, Collections.reverseOrder());

        List<RankedTitle> highRankedTokens = filterLowRankTokens(listOfRankedTitles);
        
        if(highRankedTokens.size() > 0){
//            return filterByLevensteinSimilarity(highRankedTokens);
            List<String> cands=new ArrayList<String>();
            for(RankedTitle cand:highRankedTokens)
                cands.add(cand.getTitle());
            return cands;
//            return highRankedTokens;
        }else{
            return new ArrayList<String>();
        }

    }

    public static void rerankMaximumTokenLengthWithinATitle(List<RankedTitle> titles) {

        for(RankedTitle title : titles){
            int maxLength = 0;
            String[] parts = title.getTitle().split(" ");
            for(String token : parts){
                if(token.length() > maxLength){
                    maxLength = token.length();
                }
            }
            if(maxLength < 4){
                title.setFrequency(-2);
            }
        }

    }

    public static List<String> filterByLevensteinSimilarity(List<RankedTitle> tokenTitles) {

        if(tokenTitles.size() == 0){
            throw new IllegalArgumentException("Cannot process an empty list");
        }


        List<String> finalTitles = new ArrayList<String>();
        finalTitles.add(tokenTitles.get(0).getTitle());
        if(tokenTitles.size() < 2){
            return finalTitles;
        }
        
        
        

        AbstractStringMetric metric = new Levenshtein();
        for(int i = 1; i < tokenTitles.size(); i++){
            boolean reject = false;
            for(String title : finalTitles){
                float result = metric.getSimilarity(title, tokenTitles.get(i).getTitle());
                if (result >= 0.7){
                    reject = true;
                    break;
                }
            }
            if(!reject){
                finalTitles.add(tokenTitles.get(i).getTitle());
            }
        }

        return finalTitles;
    }

    public static List<RankedTitle> filterLowRankTokens(List<RankedTitle> tokenTitles){
        List<RankedTitle> filteredTitles = new ArrayList<RankedTitle>();
        for(RankedTitle title : tokenTitles){
            if(title.getFrequency() < 0){
                continue;
            }
            filteredTitles.add(title);
        }
        return filteredTitles;
    }

    public static void rerankRedundantSingleTokenTitles(List<RankedTitle> tokens) {

        for(int i = 0; i < tokens.size(); i++){
            for(int j = 0; j < tokens.size(); j++){
                if(i == j){
                    continue;
                }
 if(tokens.get(i).getTitle().contains(tokens.get(j).getTitle())){
                    int freq1 = tokens.get(i).getFrequency();
                    int freq2 = tokens.get(j).getFrequency();

                    if(freq2 < 3 * freq1){
                        tokens.get(j).setFrequency(-1);
                    }

                }
            }
        }
    }

    public static void rerankTitlesByNumberOfPhotos(List<RankedTitle> listOfRankedTitles, int numberOfPhotos) {

        int threshold = Math.max( (int) Math.floor((double)numberOfPhotos / (double)8) , 3);
        for(RankedTitle title : listOfRankedTitles){
            if(title.getFrequency() < threshold){
                title.setFrequency(0);
            }
        }
    }

    public static String upperCaseWords(String line)
    {
//        line = line.trim().toLowerCase();
        String data[] = line.split("\\s");
        line = "";
        for(int i =0;i< data.length;i++)
        {
            if(data[i].length()>1)
                line = line + data[i].substring(0,1).toUpperCase()+data[i].substring(1)+" ";
            else
                line = line + data[i].toUpperCase();
        }
        return line.trim();
    }

    public static void addCombinationsCounts(String sentence,HashMap<String,Integer> combinationsCounts) {
            String[] parts = sentence.split("\\s");
            String nextSentence="";
            int length = 2;
            while (length <= MAX_TOKEN_LENGTH && length <= parts.length ){
                for(int i = 0; i < (parts.length - length) + 1 ; i++){
                    nextSentence=getOneCombination(parts, i, length);
                    Integer count=combinationsCounts.get(nextSentence);
                    if(count==null)
                        combinationsCounts.put(nextSentence, 1);
                    else
                        combinationsCounts.put(nextSentence, count+1);
                }
                length++;
//            }
        }
            
    }

    public static String getOneCombination(String[] parts, int start, int length) {
        StringBuffer buf = new StringBuffer();
        for(int i = 0; i < length; i++){
            buf.append(parts[start + i] + " ");
        }
        return buf.substring(0, buf.length()-1).toString();
    }
 

    public static List<String> extractTokens(String[] titleTokens, List<Integer> uselessWordsIndexes) {
        List<String> tokens = new ArrayList<String>();
        if(titleTokens.length == 0){
            return tokens;
        }

        if(uselessWordsIndexes.size() == 0){
            tokens.add(extractTitleFromTokens(titleTokens, 0, titleTokens.length-1));
            return tokens;
        }

        for (int i = 1; i < uselessWordsIndexes.size(); i++){
            int index1 = uselessWordsIndexes.get(i-1)+1;
            int index2 =  uselessWordsIndexes.get(i)-1;
            if (index1 > index2){
                continue;
            }
            tokens.add(extractTitleFromTokens(titleTokens, index1, index2));
        }

        return tokens;
    }

    protected static String extractTitleFromTokens(String[] tokens, int index0, int index1) {
        if (index0 < 0 || index1 > tokens.length || index1 < index0){
            throw new IllegalArgumentException("Inappropriate input arguments: " + index0 + ", " + index1);
        }
        StringBuffer buf = new StringBuffer();
        for (int i = index0; i < index1; i++){
            buf.append(tokens[i] + " ");
        }
        buf.append(tokens[index1]);

        return buf.toString();
    }


    public static List<Integer> findUselessWordsInTitle(String[] titleTokens) {
        List<Integer> indexes = new ArrayList<Integer>();
        indexes.add(-1);
        for(int i = 0; i < titleTokens.length; i++){
            if(!acceptTag(titleTokens[i])){
                indexes.add(i);
            }
        }
        indexes.add(titleTokens.length);
        return indexes;
    }


    public static boolean acceptTag(String tag){
      
        if (isNumeric(tag)){
            return false;
        }
        if(isOneLetterLength(tag)){
            return false;
        }
        
        if(containsStrangeCharacterSequences(tag)){
            return false;
        }
        if(hasMoreNumbersThanLetters(tag)){
            return false;
        }
        if(isScreenname(tag)) {
            return false;
        }
        return true;
    }

        public static boolean isOneLetterLength(String tag) {
        if(tag.length() > 1){
            return false;
        }
        return true;
    }

        public static boolean isScreenname(String tag) {
            if(tag.startsWith("@")){
                return true;
            }
            return false;
        }

    public static boolean isNumeric(String tag){
        for (int x = 0; x < tag.length(); x++){
            if (!Character.isDigit(tag.charAt(x))){
                return false;
            }
        }
        return true;
    }

    public static boolean containsStrangeCharacterSequences(String tag){
        for(int i = 0; i < strangeSequencesIntoTags.length; i++){
            if(tag.startsWith(strangeSequencesIntoTags[i])){
                return true;
            }
        }
        return false;
    }

    private static final String[] strangeSequencesIntoTags = {
        "img_",
        "img-",
        "dmc-",
        "dmc",
        "img",
        "finepix",
        "dsc",
        "jpg",
        "jpeg"
    };
 
    public static boolean hasMoreNumbersThanLetters(String tag){
        int numberOfDigits = 0;
        int numberOfLetters = 0;
        for(int i = 0; i < tag.length(); i++){
            if(Character.isDigit(tag.charAt(i))){
                numberOfDigits++;
            }else{
                numberOfLetters++;
            }
        }
        if(numberOfDigits > numberOfLetters){
            return true;
        }
        return false;
    }

    public static boolean hasHighDigitToletterRatio(String tag, double threshold){
        int numberOfDigits = 0;
        int numberOfLetters = 0;
        for(int i = 0; i < tag.length(); i++){
            if(Character.isDigit(tag.charAt(i))){
                numberOfDigits++;
            }else if (Character.isLetter(tag.charAt(i))){
                numberOfLetters++;
            }
        }
        if(numberOfDigits > threshold * numberOfLetters){
            return true;
        }
        return false;
    }

    public static String grabTitleFromURL(String url){
        Parser parser;
        String result=null;
        try {
            parser = new Parser(url);
//            TagNameFilter[] tagNamesFilter = { new TagNameFilter("title"), new TagNameFilter("h1"), new TagNameFilter("h2"), new TagNameFilter("h3")  };
            TagNameFilter[] tagNamesFilter = { new TagNameFilter("title") };
            OrFilter orTagNameFilter = new OrFilter(tagNamesFilter);
            NodeList list = parser.parse(orTagNameFilter);
            SimpleNodeIterator nodeElements = list.elements();
            Node node;
            if(nodeElements.hasMoreNodes()){
                node = nodeElements.nextNode();
                result=node.toPlainTextString().trim();
            }
        } catch (ParserException ex) {
        }
        return result;
    }

    public static List<String> grabKeyElementsFromURL(String url){
        Parser parser;
        List<String> result=new ArrayList<String>();
        try {
            parser = new Parser(url);
//            TagNameFilter[] tagNamesFilter = { new TagNameFilter("title"), new TagNameFilter("h1"), new TagNameFilter("h2"), new TagNameFilter("h3")  };
            TagNameFilter[] tagNamesFilter = { new TagNameFilter("title"), new TagNameFilter("h1"), new TagNameFilter("h2") };
            OrFilter orTagNameFilter = new OrFilter(tagNamesFilter);
            NodeList list = parser.parse(orTagNameFilter);
            SimpleNodeIterator nodeElements = list.elements();
            Node node;
            while (nodeElements.hasMoreNodes())
            {
                    node = nodeElements.nextNode();
                    result.add(node.toPlainTextString().trim());
            }
        } catch (ParserException ex) {
        }
        return result;
    }
    
    public static String getCleanedTitleMR(String text) {
		if (text != null) {
                        boolean endsHellip=false;
                        if((text.endsWith("&hellip;"))||(text.endsWith("..."))) endsHellip=true;
			// System.out.println("Text before     --> " + text);
			text = text.trim(); // removes redundant white spaces
			text = StringEscapeUtils.escapeHtml(text); // makes it as html

                        String textT=text;
			text = text.replaceAll("(http|https):*(//)*\\S+&hellip;\\z", "");
                        if(!textT.equals(text)) endsHellip=false;
			// text = text.replaceAll("http\\:*.*&hellip;\\z", "");
//			text = text.replaceAll("\\S+&hellip;\\z", ""); // removes 3 dots at the
														// end
														// of string
//			text = text.replaceAll("(\\S[^\\p{Punct}\\s])+&hellip;\\z", "");
			text = text.replaceAll("(((\\s|\\p{Punct})+)\\S+)&hellip;\\z", "");
			text = text.replaceAll("&mdash;", "-");
			text = StringEscapeUtils.unescapeHtml(text); // gets true text again

			text = text.replaceAll("\\s*\\&amp\\;\\s*", " & ")
					.replaceAll("\\&gt\\;", " > ")
					.replaceAll("\\&lt\\;", " < ");

			text = text.replaceAll("\\’", "'"); // so as not to remove this
												// apostrophe after the next
												// replacement
			text = text.replaceAll("\\‘", "'");
			text = extr.keepUsernamesWithApostrophe(text); // if @username is
															// followed by 's
                        											// then
															// keep username and
															// remove @
			text = extr.keepUsernamesAndHashtagsWithPrepositions(text); //"by @" remains, "via @" remains 
			text = text.replaceAll("\\“", "\"");
			text = text.replaceAll("\\”", "\"");
			//text = text.replaceAll("[^\\x00-\\x7F]+", " "); // remove
															// non-US-ASCII
															// characters

			text = extr.extractReplyScreennameByMR(text);
//			text = extr.removeHashtags(text);
			text = extr.processHashtags(text);
                        text = removeParentheses(text);
                        
			text = extr.removeMultiplePunctuation(text);
			// System.out.println("Removing usernames and cashtags..");
			text = extr.removeScreennames(text); // removes @usernames
			List<String> cashtags = extr.extractCashtags(text);
			if (!cashtags.isEmpty()) {
				for (String cashtag : cashtags) {
                                    System.out.println("Cashtag: "+cashtag);
					text = text.replaceAll("\\$" + cashtag, " ");
				}
				text = text.replaceAll("\\s{2,}", " ");
			}
			//remove "via http://"
			text = text.replaceAll("via\\s{0,1}http:*(//)*\\S+", "");
			
			// System.out.println("Removing symbols at the beginning of the sentence..");
			text = text.replaceAll("^[^\\w(\\[]+", "");
			// System.out.println("Removing symbols at the end of the sentence..");
			text = text.replaceAll("[^\\w\\?\\'\\\"\\)\\]]+$", "");

			// text = text.replaceAll("(\\p{Punct}\\s\\p{Punct})", " "); //to
			// keep if text is Π‘ΠΎΡ�ΠΊΡƒ Π±Ρ‹ ΠµΠΌΡƒ
			
			//remove single parenthesis
			if (text.contains("(")&&(!text.contains(")")))
				text = text.replace("(", "");
			if (text.contains(")") && (!text.contains("(")))
				text = text.replace(")", "");
			//replace empty ()
			text = text.replaceAll("\\(\\s*\\)", "");

			// text = text.replaceAll("([^\\w\\/\\,\\;\\.\\&\\%\\+\\:\\$]\\s)+"," ");
			// text = text.replaceAll("([^\\w\\/](\\s))\\1{2,}", " ");

			// System.out.println(text);
			text = text.replaceAll("\\s{2,}", " ").replaceAll("-\\s*\\z", "");
                        
                        String currentTitleRGUb=text;
                        if ((currentTitleRGUb !=null) || (currentTitleRGUb !=""))
                                currentTitleRGUb = currentTitleRGUb.replaceAll(Extractor.urlRegExp, "");

                        if (currentTitleRGUb.endsWith(":")||currentTitleRGUb.endsWith("-"))
                                currentTitleRGUb = currentTitleRGUb.trim().substring(0,
                                                currentTitleRGUb.trim().length() - 1) + ".";
                        currentTitleRGUb = currentTitleRGUb.replaceAll("[^\\w\\'\\\"\\?\\)\\]]+$", "");

                        if (!currentTitleRGUb.matches(".*[\\W]$")) // if there is no symbol at
                                // the end, adds "."
                                currentTitleRGUb = currentTitleRGUb.trim() + ".";
                        currentTitleRGUb = currentTitleRGUb.trim();

                        if ((StringUtils.countMatches(currentTitleRGUb, "\"") == 1))
                                currentTitleRGUb = currentTitleRGUb.replaceAll("\"", "");
                        Character firstChar = currentTitleRGUb.charAt(0);
                        currentTitleRGUb = Character.toUpperCase(firstChar) + currentTitleRGUb.substring(1);
                        
                        text=currentTitleRGUb=text;
                        
                        if(endsHellip) text=text+"...";
                        //System.out.println("CC: "+text);
		}
		return text;
	}
	
    public static List<String> getSentences1_old(String text, Set<String> entities){
        text=text.trim();
        text=StringEscapeUtils.escapeHtml(text);
        text=text.replaceAll("http:.*&hellip;\\z","");
        String[] toMatch={"\\ART\\s+@\\S+", "\\AMT\\s+@\\S+"};
        for(String t:toMatch){
                Pattern pattern = Pattern.compile(t, Pattern.CASE_INSENSITIVE);
                String newTweet = text.trim();
                text="";
                while(!newTweet.equals(text)){         //each loop will cut off one "RT @XXX" or "#XXX"; may need a few calls to cut all hashtags etc.
                        text=newTweet;
                        Matcher matcher = pattern.matcher(text);
                        newTweet = matcher.replaceAll("");
                        newTweet =newTweet.trim();
                }
        }
        text=text.replaceAll("-\\s*\\z","");
        text=text.replaceAll("&hellip;\\z","");
        text=StringEscapeUtils.unescapeHtml(text);
        text=text.trim();
        String[] parts=text.split(Extractor.urlRegExp);
        List<String> sentences=new ArrayList<String>();
        
//        for(int i=0;i<parts.length;i++){
        int limit=10;
        if(limit>parts.length) 
			limit=parts.length;
        for(int i=0;i<limit;i++){
//            parts[i]=text.replace("http://*&hellip;","");
            String text_cleaned=extractor.cleanText(parts[i]);
//            List<String> sentences_tmp=new ArrayList<String>();
            Reader reader = new StringReader(text_cleaned);
            DocumentPreprocessor dp = new DocumentPreprocessor(reader);
            dp.setTokenizerFactory(PTBTokenizerFactory.newWordTokenizerFactory("ptb3Escaping=false,untokenizable=noneDelete"));
                    //prop.setProperty("tokenizerOptions", "untokenizable=noneDelete");

            Iterator<List<HasWord>> it = dp.iterator();
            while (it.hasNext()) {
                StringBuilder sentenceSb = new StringBuilder();
                List<HasWord> sentence = it.next();
                boolean last_keep=false;
                for (HasWord token : sentence) {
                    if((!token.word().matches("[,:!.;?)]"))&&(!token.word().contains("'"))&&!last_keep){
                        sentenceSb.append(" ");
                    }
                    last_keep=false;
                    if(token.word().matches("[(\\[]"))
                            last_keep=true;
                    String next_word=token.toString();
                      
                    if((next_word.toUpperCase().equals(next_word))&&(!next_word.equals("I"))&&(!entities.contains(next_word)))
                        next_word=next_word.toLowerCase();
                    if(next_word.equals("i")) next_word="I";
                    sentenceSb.append(next_word);
                }
                String new_sentence=sentenceSb.toString().trim();
                Character fc=new_sentence.charAt(0);
                new_sentence=fc.toString().toUpperCase()+new_sentence.substring(1);
                if(new_sentence.endsWith(":"))
                    text=text.substring(0,text.length()-3)+".";

                sentences.add(new_sentence);
            }
  //          sentences.addAll(sentences_tmp);
        }
        return sentences;
    }
    
	public static List<String> getSentences1(String text, Set<String> entities) {
//		System.out.println("   Text as it is    :   " + text);
		text = TrendsLabeler.getCleanedTitleMR(text);

		String[] parts = text.split(Extractor.urlRegExp);
		List<String> sentences = new ArrayList<String>();

		// for(int i=0;i<parts.length;i++){
		int limit = 10;
		if (limit > parts.length)
			limit = parts.length;
		for (int i = 0; i < limit; i++) {
			String text_cleaned = extr.cleanText(parts[i]);
			// List<String> sentences_tmp=new ArrayList<String>();
			Reader reader = new StringReader(text_cleaned);
			DocumentPreprocessor dp = new DocumentPreprocessor(reader);
			dp.setTokenizerFactory(PTBTokenizerFactory
					.newWordTokenizerFactory("ptb3Escaping=false, untokenizable=noneDelete"));
			// dp.setTokenizerFactory(PTBTokenizerFactory.newWordTokenizerFactory("untokenizable=noneDelete"));

			Iterator<List<HasWord>> it = dp.iterator();
			while (it.hasNext()) {
				StringBuilder sentenceSb = new StringBuilder();
				List<HasWord> sentence = it.next();
				boolean last_keep = false;
				for (HasWord token : sentence) {
					if ((!token.word().matches("[,:!.;?)]"))
							&& (!token.word().contains("'")) && !last_keep) {
						sentenceSb.append(" ");
					}
					last_keep = false;
					if (token.word().matches("[(\\[]"))
						last_keep = true;
					String next_word = token.toString();

					if ((next_word.toUpperCase().equals(next_word))
							&& (!next_word.equals("I"))
							&& (!entities.contains(next_word)))
						next_word = next_word.toLowerCase();
					if (next_word.equals("i"))
						next_word = "I";
					sentenceSb.append(next_word);
				}
				String new_sentence = sentenceSb.toString().trim();
				Character fc = new_sentence.charAt(0);
				new_sentence = fc.toString().toUpperCase()
						+ new_sentence.substring(1);
				if (new_sentence.endsWith(":"))
					text = text.substring(0, text.length() - 3) + "."; 

				sentences.add(new_sentence);
			}
			// sentences.addAll(sentences_tmp);
		}
		return sentences;
	}

	
    public static List<String> getSentences2(String text){
        String[] parts=text.split(Extractor.urlRegExp);
        List<String> sentences=new ArrayList<String>();
        for(int i=0;i<parts.length;i++){
            String text_cleaned=extractor.cleanText(parts[i]);
            Properties props = new Properties();
            props.put("annotators", "tokenize, ssplit");
            StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
            // create an empty Annotation just with the given text
            Annotation document = new Annotation(text_cleaned);
            // run all Annotators on this text
            pipeline.annotate(document);
            // these are all the sentences in this document
            // a CoreMap is essentially a Map that uses class objects as keys and has values with custom types
            List<CoreMap> sentences_c = document.get(SentencesAnnotation.class);

            for(CoreMap sentence: sentences_c) {
            // traversing the words in the current sentence
            // a CoreLabel is a CoreMap with additional token-specific methods
                String new_sentence="";
                for (CoreLabel token: sentence.get(TokensAnnotation.class)) {
                    if((!token.word().matches("[\\p{Punct}]"))&&(!token.word().contains("'"))){
                        new_sentence=new_sentence+" ";
                    }
                    String next_word=token.word();
                    if (next_word.toUpperCase().equals(next_word))
                        next_word=next_word.toLowerCase();
                    new_sentence=new_sentence+token;

                }
                new_sentence=new_sentence.trim();
                Character fc=new_sentence.charAt(0);
                new_sentence=fc.toString().toUpperCase()+new_sentence.substring(1);
                sentences.add(new_sentence);
            }
//            sentences.addAll(sentences_tmp);
        }        
        return sentences;
    }
    
    public static String getSiteNameFromURL(String url_str){
        String media=null;
        try {
            URL url=new URL(url_str);
            String host=url.getHost();
            String[] parts=host.split("\\.");
            for(int i=0;i<parts.length;i++)
                if((!parts[i].equals("www"))&&(!parts[i].equals("en"))) 
					return parts[i];
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        }
        return media;
        
    }
    
    private static String cleanTitleFromCommonMediaNames(String title){
        title = title.trim();
		return title
				.replace("- ITV News", "")
				.replace("- Yahoo News", "")
				.replace("- Bloomberg", "")
				.replace("| Facebook", "")
				.replace("- CNN.com", "")
				.replace(": People.com", "")
				.replace("- People.com", "")
				.replace("| News | Pitchfork", "")
				.replace("- The Next Web", "")
				.replace("- CBS News Video", "")
				.replace(" CBS News", "")
				.replace("- NYTimes.com", "")
				.replace(" - YouTube", "")
				.replace("- WSJ.com", "")
				.replace(": Pressparty", "")
				.replace(" :: Beatport Play", "")
				.replace(" [VIDEO]", "")
				.replace(": The New Yorker", "")
				.replace("(Video)", "")
				.replace(
						"Business News & Financial News - The Wall Street Journal - Wsj.com",
						"").replace("| TIME.com", "")
				.replace("NME Magazine", "");
	}
	
	private static String removeNameDots(String title) {
		title = title.trim();
		if (title != null && !title.equals("")) {
			// Pattern nameDotPattern = Pattern.compile("[a-zA-z]+" +
			// Regex.URL_VALID_GTLD);
			Matcher nameDotMatcher = Regex.NAME_DOT_GTLD_PATTERN.matcher(title);
			title = nameDotMatcher.replaceAll("");
			nameDotMatcher = Regex.NAME_DOT_CCTLD_PATTERN.matcher(title);
			title = nameDotMatcher.replaceAll("");
		}
		return title.trim();
	}
    
    static String[] dyscoIds=new String[]{
"c28c26dd-f373-4975-8fcb-d7600c6a1979",
"dd6ba020-408f-415b-b803-879971aa90ef",
"1747f8e6-dc8d-45b9-8d29-05eac42864fa",
"0cc3a5f1-4963-4e21-a47c-bad176be0f16"};   

	private static HashMap<String, Integer> getUniqueTerms(Dysco dysco) {
		

		// get corresponding values from dysco
		Map<String, Double> keywords = dysco.getKeywords();
		List<Entity> entities = dysco.getEntities();
		Map<String, Double> hashtags = dysco.getHashtags();

		List<String> allNgrams = new ArrayList<String>(keywords.size()
				+ entities.size() + hashtags.size());
		// add n-grams to allNgrams
		for (Map.Entry<String, Double> entry : keywords.entrySet())
			allNgrams.add(entry.getKey());
		for (Entity entity : entities)
			allNgrams.add(entity.getName());
		for (Map.Entry<String, Double> entry : hashtags.entrySet())
			allNgrams.add(entry.getKey());

		HashMap<String, Integer> uniqueTopicTerms = new HashMap<String, Integer>();
		Integer count;

		for (String unigrams : allNgrams) {
			String[] unigram = unigrams.split(" ");
			for (String term : unigram) {
				// remove symbols, make lowercase, remove non-Unicode
				term = term.replaceAll("\\p{Punct}+", " ")
						.replaceAll("\\s{2,}", " ").trim();
//				term = term.replaceAll("[^\\x00-\\x7F]", "");
				if ((term != null) && (!term.equals("")) && (term.length() > 3)) {
					count = uniqueTopicTerms.get(term.toLowerCase());
					if (count == null)
						uniqueTopicTerms.put(term.toLowerCase(), 1);
					else
						uniqueTopicTerms.put(term.toLowerCase(), count + 1);
				}
			}
		}
		return uniqueTopicTerms;
	}

	public static List<RankedTitleRGU>getTweetScores(Dysco dysco) {
		
		List<Item> items = dysco.getItems();
		List<String> allText = new ArrayList<String>(items.size());
                HashMap<String, Item> itemsMap=new HashMap<String,Item>();
		for (Item item : items) {
			allText.add(item.getTitle());
                        itemsMap.put(item.getTitle(), item);
		}
		HashMap<String, Integer> uniqueTexts = new HashMap<String, Integer>();
		for (String text : allText) {
			Integer count = uniqueTexts.get(text);
			if (count == null)
				uniqueTexts.put(text, 1);
			else
				uniqueTexts.put(text, count + 1);
		}

		HashMap<String, Integer> uniqueTerms = getUniqueTerms(dysco); //combine keywords, entities, hashtags

		// other parameters
		int numberOfTopicTerms = uniqueTerms.size();
		double a = 0.8;
		int numberOfTweets = dysco.getItems().size();

		//variable to fill in 
		List<RankedTitleRGU> tweetScores = new ArrayList<RankedTitleRGU>(numberOfTweets);
		
		for (String title : allText){
			RankedTitleRGU rankedTitle = new RankedTitleRGU(title,itemsMap.get(title));
			int numberOfDuplicatesLikeCurrentTweet = uniqueTexts.get(title);
			int numberOfTermsContainedInTweet = calculateNumberOfTermsContainedInTweet(uniqueTerms, title);
			rankedTitle.calculateScore(a, numberOfTermsContainedInTweet, numberOfTopicTerms, numberOfDuplicatesLikeCurrentTweet, numberOfTweets);
			tweetScores.add(rankedTitle);
		}

		return tweetScores;

	}

	private static int calculateNumberOfTermsContainedInTweet(
			HashMap<String, Integer> uniqueTerms, String title) {
		int countOccurence= 0;		
		
		for (Map.Entry<String, Integer> entry : uniqueTerms.entrySet()){
			String term = entry.getKey();
			//for each term
			Pattern p = Pattern.compile(term);
			Matcher m = p.matcher(title.toLowerCase());
			while (m.find()){
				countOccurence +=1;
		    }
		}		
		return countOccurence;
	}

	public static String findPopularTitleRGU(Dysco dysco) {
		
		List<RankedTitleRGU> tweetScoresRGU = TrendsLabeler.getTweetScores(dysco);
		Collections.sort(tweetScoresRGU, Collections.reverseOrder());
		RankedTitleRGU bestRankedTitle = tweetScoresRGU.get(0);
                Item selItem=bestRankedTitle.getItem();
                
                if(selItem!=null){
                    Logger.getRootLogger().info("TRENDS LABELLER. Text of original tweet used for title: "+selItem.getTitle());
                    
                    System.out.println("SELECTED ID: "+selItem.getId());
                    System.out.println("SELECTED text original: "+selItem.getTitle());
                    
                    String author_id=selItem.getUserId();
                    System.out.println("SELECTED author id: "+author_id);
                    
                    /*
                    String author=null;
                    StreamUser su=selItem.getStreamUser();
                    if(su!=null){
                        author=su.getName();
                        if((author==null)||(author.trim().equals("")))
                            author=su.getUsername();
                    }
                    */
//                    if((author==null)||(author.trim().equals("")))
  //                      author=selItem.getAuthorScreenName();
                    String author=null;
                    if((author_id!=null)&&(suDAO!=null)){
                        StreamUser s_user=suDAO.getStreamUser(author_id);
                        if(s_user!=null)
                            author=s_user.getName();
                        else
                            System.out.println("User returned from StreamUserDAO was null.");
                    }
                    dysco.setAuthor(author);
                    System.out.println("SELECTED author: "+author);
                    URL[] urls=selItem.getLinks();
                    String mainURL=null;
                    String storyType=null;
                    if((urls!=null)&&(urls.length>0)){
                        String url_original=urls[0].toString();
                        Logger.getRootLogger().info("TRENDS LABELLER. URL considered for fetching image / video: "+url_original);
                        String expandedURL=null;
                        mainURL=url_original;
                        //mainURL=URLDeshortener.expandFromManos(mainURL);

                        //First check using the content type http header
                        int redirects = 0;
                        int max_redirects=5;
                        HttpURLConnection connection;
                        while(true && redirects < max_redirects) {
                            try {
                                URL url = new URL(mainURL);
                                connection = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);
                                connection.setInstanceFollowRedirects(false);
                                connection.setReadTimeout(2000);
                                connection.connect();
                                expandedURL = connection.getHeaderField("Location");
                                if(expandedURL == null) {
                                    storyType=connection.getContentType();
                                    break;
                                }
                                else {
                                    mainURL = expandedURL;
                                    redirects++;
                                }
                            }
                            catch(Exception e) {
                                return null;
                            }
                        }
                        
                        Logger.getRootLogger().info("TRENDS LABELLER. Will now examine the direct case");
                        if(storyType!=null){
                            if((!storyType.startsWith("image"))&&(!storyType.startsWith("video")))
                                storyType=null;
                            else if(storyType.startsWith("image")) 
                                storyType="image";
                            else if(storyType.startsWith("video")) 
                                storyType="video";
                        }
                        //If the content type header fails, we use the retrievers provided by Manos
                        String mainURLtmp=mainURL;
                        if((storyType==null) && (mainURL!=null)) {
                            Logger.getRootLogger().info("TRENDS LABELLER. No direct links to images / videos, trying indirectly (via page content).");
                            mainURL=MediaURLProcessor.getMediaItemsURL(mainURLtmp);
                            if(mainURL!=null) storyType=MediaURLProcessor.getMediaItemsType(mainURLtmp);
                            
                        }                                
                        if((storyType==null) && (selItem.getMediaIds()!=null) && (selItem.getMediaIds().size()>0)) {
                            Logger.getRootLogger().info("TRENDS LABELLER. Will now check for attached images / videos.");
                            String miId=selItem.getMediaIds().get(0);
                            Logger.getRootLogger().info("TRENDS LABELLER. Media item id: "+miId);
                            if(miDAO!=null){
                                MediaItem mi=miDAO.getMediaItem(miId);
                                if(mi!=null){
                                Logger.getRootLogger().info("TRENDS LABELLER. Getting media url and story type from embedded media item.");
                                    mainURL=mi.getUrl();
                                    storyType=mi.getType();
                                }
                            }
                        }                                

                        Logger.getRootLogger().info("TRENDS LABELLER. Main media url: "+ mainURL);
                        Logger.getRootLogger().info("TRENDS LABELLER. Story type: "+ storyType);
                        if(storyType==null) mainURL=null;
                        if(mainURL==null) storyType=null;
                                
                        
                    }
                    
                    dysco.setMainMediaUrl(mainURL);
                    dysco.setStoryType(storyType);
                }            
                
		String currentTitleRGU = bestRankedTitle.getTitle();
		//apply some cleaning
//		String currentTitleRGUa = Extractor.cleanTextRGU(currentTitleRGU);
//		System.out.println("This is RGU cleaned     : " + currentTitleRGUa);
//		System.out.println("This is dominant tweet  : " + currentTitleRGU);
		
		String currentTitleRGUb = TrendsLabeler.getCleanedTitleMR(currentTitleRGU);
		
		if ((currentTitleRGUb !=null) || (currentTitleRGUb !=""))
			currentTitleRGUb = currentTitleRGUb.replaceAll(Extractor.urlRegExp, "");
		
		if (currentTitleRGUb.endsWith(":")||currentTitleRGUb.endsWith("-"))
			currentTitleRGUb = currentTitleRGUb.trim().substring(0,
					currentTitleRGUb.trim().length() - 1) + ".";
		currentTitleRGUb = currentTitleRGUb.replaceAll("[^\\w\\'\\\"\\?\\)\\]]+$", "");

                /*
		if (!currentTitleRGUb.matches(".*[\\W]$")) // if there is no symbol at
			// the end, adds "."
			currentTitleRGUb = currentTitleRGUb.trim() + ".";
		currentTitleRGUb = currentTitleRGUb.trim();
		
		if ((StringUtils.countMatches(currentTitleRGUb, "\"") == 1))
			currentTitleRGUb = currentTitleRGUb.replaceAll("\"", "");
		Character firstChar = currentTitleRGUb.charAt(0);
		currentTitleRGUb = Character.toUpperCase(firstChar) + currentTitleRGUb.substring(1);
		
		if (currentTitleRGUb.length()<3)
			currentTitleRGUb = currentTitleRGU; //without any cleaning
		System.out.println("CC: "+currentTitleRGUb);
                * */
//		System.out.println("This is RGU cleaned MR  : " + currentTitleRGUb + "\n");		
		return currentTitleRGUb;
	} 
 
    public static String removeParentheses(String str){
        String ret_str=str;
        ret_str.trim();
        int originalsize=str.length();
        int pos1_1=ret_str.lastIndexOf("(");
        int pos1_2=ret_str.lastIndexOf(")");
//        System.out.println(pos1_1+" / "+pos1_2);
//        while((pos1_1>-1) && (pos1_2>-1) && (pos1_1<(originalsize/2))){
        while((pos1_1>-1) && (pos1_2>-1)){
//        if((pos1_1>-1) && (pos1_2>-1)){
            String str1=ret_str.substring(pos1_1,pos1_2+1);
            ret_str=ret_str.replace(str1, "");
            pos1_1=ret_str.lastIndexOf("(");
            pos1_2=ret_str.lastIndexOf(")");
        }
        
        int pos2_1=ret_str.lastIndexOf("[");
        int pos2_2=ret_str.lastIndexOf("]");
//        if((pos2_1>-1) && (pos2_2>-1)){
        while((pos2_1>-1) && (pos2_2>-1)){
            String str2=ret_str.substring(pos2_1,pos2_2+1);
            ret_str=ret_str.replace(str2, "");
            pos2_1=ret_str.lastIndexOf("[");
            pos2_2=ret_str.lastIndexOf("]");
        }
        
        return ret_str;
    }
        
 
    public static List<Item> loadItemsFromFile(String filename){
        List<Item> items=new ArrayList<Item>();
        try{
            BufferedReader br = new BufferedReader(new InputStreamReader(
			new FileInputStream(filename), "UTF8"));
            //BufferedReader br=new BufferedReader(new FileReader(filename));
            String line=null;
            while((line=br.readLine())!=null){
                if(line.trim()!=""){
//                    Item new_item=ItemFactory.create(line);
                    Item new_item=new Item();
                    DBObject dbObject = (DBObject) JSON.parse(line);
                    String id=(String) dbObject.get("id_str");
                    new_item.setId(id);
                    String text=(String) dbObject.get("text");
                    new_item.setTitle(text);
                    String reply_id=(String) dbObject.get("in_reply_to_status_id_str");
                    new_item.setInReply(reply_id);
//                    if(reply_id!=null) System.out.println("GOT REPLYYYYYYYYYYYY " +reply_id );
                    DBObject tmp_obj=(DBObject) dbObject.get("user");
                    String uploader=(String) tmp_obj.get("screen_name");
                    new_item.setAuthorScreenName(uploader);
                    String uploader_full=(String) tmp_obj.get("full_name");
                    new_item.setAuthorFullName(uploader_full);
                    
                    DBObject objEntities=(DBObject) dbObject.get("entities");
                    BasicDBList urls = (BasicDBList) objEntities.get("urls");
                    BasicDBObject[] urlsArr=urls.toArray(new BasicDBObject[0]);
                    List<String> urlsList=new ArrayList<String>();
                    for(int pp=0;pp<urlsArr.length;pp++){
                        String nextURL=(String) urlsArr[pp].get("expanded_url");
                        if(nextURL==null) nextURL=(String) urlsArr[pp].get("url");
                        if(nextURL!=null) urlsList.add(nextURL);
                    }
                    String[] links=urlsList.toArray(new String[0]);
                    new_item.setList(links);
 
                    URL[] urlsObjs=new URL[links.length];
                    for(int s=0;s<links.length;s++){
                        urlsObjs[s]=new URL(links[s]);
                    }
                    new_item.setLinks(urlsObjs);
                    
                    BasicDBList hashtagsList = (BasicDBList) objEntities.get("hashtags");
                    if(hashtagsList!=null){
                        BasicDBObject[] hashtagsArr=hashtagsList.toArray(new BasicDBObject[0]);
                        List<String> hashtags=new ArrayList<String>();
                        for(int pp=0;pp<hashtagsArr.length;pp++){
                            String nextHashtag=(String) hashtagsArr[pp].get("text");
                            hashtags.add(nextHashtag);
                        }
                        new_item.setKeywords(hashtags);
                    }
                    
                    BasicDBList mediaList = (BasicDBList) objEntities.get("media");
                    if(mediaList!=null){
                        BasicDBObject[] mediaArr=mediaList.toArray(new BasicDBObject[0]);
                        List<String> media=new ArrayList<String>();
                        for(int pp=0;pp<mediaArr.length;pp++){
                            String nextMedium=(String) mediaArr[pp].get("media_url");
                            media.add(nextMedium);
                        }
                        new_item.setMediaIds(media);
                    }                    
                    
                    
                    items.add(new_item);
                }
            }
            br.close();
        }
        catch(Exception ex){
            ex.printStackTrace();
        }
        return items;
    }
    
    
    
}