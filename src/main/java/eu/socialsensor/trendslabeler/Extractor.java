package eu.socialsensor.trendslabeler;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.*;

import org.languagetool.JLanguageTool;
import org.languagetool.language.BritishEnglish;
import org.languagetool.rules.RuleMatch;

/**
 * A class to extract usernames, lists, hashtags and URLs from Tweet text.
 */
public class Extractor {

    private static JLanguageTool langTool;
    private static Pattern ptrnPunc = Pattern.compile("\\p{Punct}");
    static{        
        try {
            langTool = new JLanguageTool(new BritishEnglish());
        } catch (IOException ex) {
            Logger.getLogger(Extractor.class.getName()).log(Level.SEVERE, null, ex);
        }
        //langTool.activateDefaultPatternRules();
    }
    
    
    public static class Entity {

        public enum Type {

            URL, HASHTAG, MENTION, CASHTAG
        }
        protected int start;
        protected int end;
        protected final String value;
        // listSlug is used to store the list portion of @mention/list.
        protected final String listSlug;
        protected final Type type;
        protected String displayURL = null;
        protected String expandedURL = null;

        public Entity(int start, int end, String value, String listSlug, Type type) {
            this.start = start;
            this.end = end;
            this.value = value;
            this.listSlug = listSlug;
            this.type = type;
        }

        public Entity(int start, int end, String value, Type type) {
            this(start, end, value, null, type);
        }

        public Entity(Matcher matcher, Type type, int groupNumber) {
// Offset -1 on start index to include @, # symbols for mentions and hashtags
            this(matcher, type, groupNumber, -1);
        }

        public Entity(Matcher matcher, Type type, int groupNumber, int startOffset) {
            this(matcher.start(groupNumber) + startOffset, matcher.end(groupNumber), matcher.group(groupNumber), type);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj instanceof Entity)) {
                return false;
            }

            Entity other = (Entity) obj;

            if (this.type.equals(other.type)
                    && this.start == other.start
                    && this.end == other.end
                    && this.value.equals(other.value)) {
                return true;
            } else {
                return false;
            }
        }

        @Override
        public int hashCode() {
            return this.type.hashCode() + this.value.hashCode() + this.start + this.end;
        }

        @Override
        public String toString() {
            return value + "(" + type + ") [" + start + "," + end + "]";
        }

        public Integer getStart() {
            return start;
        }

        public Integer getEnd() {
            return end;
        }

        public String getValue() {
            return value;
        }

        public String getListSlug() {
            return listSlug;
        }

        public Type getType() {
            return type;
        }

        public String getDisplayURL() {
            return displayURL;
        }

        public void setDisplayURL(String displayURL) {
            this.displayURL = displayURL;
        }

        public String getExpandedURL() {
            return expandedURL;
        }

        public void setExpandedURL(String expandedURL) {
            this.expandedURL = expandedURL;
        }
    }
    
    public static final String urlRegExp = 
    "(?:^|[\\W])((ht|f)tp(s?):\\/\\/|www\\.)"
    + "(([\\w\\-]+\\.){1,}?([\\w\\-.~]+\\/?)*"
    + "[\\p{Alnum}.,%_=?&#\\-+()\\[\\]\\*$~@!:/{};']*)";
    

    public static final String hashtagRegExp =
    "(?:^|\\s|[\\p{Punct}&&[^/]])(#[\\p{L}0-9-_\\p{P}]+)";
	
	public static final String hashtagRegExpMR =
    "(?:^|\\s|[\\p{Punct}&&[^/]])(#[\\p{L}0-9-_\\p{P}]+(\\p{Punct}*))";

    public static final String usermentionRegExp =
    "([\\s“'`\"])*((by)|(via)|(according to)|(from))?(?:^|\\s|[\\p{Punct}&&[^/]“])(@[\\p{L}0-9-_\\p{P}]+)(['`\"“])*"; 

//    private static final String usermentionRegExp =
//    "(?:^|\\s|[\\p{Punct}&&[^/]])(@[\\p{L}0-9-_\\p{P}]+)";     
    
    private static final Pattern urlPattern = Pattern.compile(
    urlRegExp,
    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);

    private static final Pattern hashtagPattern =
    Pattern.compile(hashtagRegExp);

    private static final Pattern usermentionPattern =
    Pattern.compile(usermentionRegExp);  
 
	private static Pattern unicodeOutliers = Pattern.compile("[^\\x00-\\x7F]",
        Pattern.UNICODE_CASE | Pattern.CANON_EQ
        | Pattern.CASE_INSENSITIVE);	
    
    
    /*
    private static final Pattern urlPattern = Pattern.compile(
    "(?:^|[\\W])((ht|f)tp(s?):\\/\\/|www\\.)"
    + "(([\\w\\-]+\\.){1,}?([\\w\\-.~]+\\/?)*"
    + "[\\p{Alnum}.,%_=?&#\\-+()\\[\\]\\*$~@!:/{};']*)",
    Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL);

    private static final Pattern hashtagPattern =
    Pattern.compile("(?:^|\\s|[\\p{Punct}&&[^/]])(#[\\p{L}0-9-_]+)");

    private static final Pattern usermentionPattern =
    Pattern.compile("(?:^|\\s|[\\p{Punct}&&[^/]])(@[\\p{L}0-9-_]+)");     
    */
    
    protected boolean extractURLWithoutProtocol = true;

    /**
     * Create a new extractor.
     */
    public Extractor() {
    }

    private void removeOverlappingEntities(List<Entity> entities) {
// sort by index
        Collections.<Entity>sort(entities, new Comparator<Entity>() {

            public int compare(Entity e1, Entity e2) {
                return e1.start - e2.start;
            }
        });

// Remove overlapping entities.
// Two entities overlap only when one is URL and the other is hashtag/mention
// which is a part of the URL. When it happens, we choose URL over hashtag/mention
// by selecting the one with smaller start index.
        if (!entities.isEmpty()) {
            Iterator<Entity> it = entities.iterator();
            Entity prev = it.next();
            while (it.hasNext()) {
                Entity cur = it.next();
                if (prev.getEnd() > cur.getStart()) {
                    it.remove();
                } else {
                    prev = cur;
                }
            }
        }
    }

    /**
     * Extract URLs, @mentions, lists and #hashtag from a given text/tweet.
     *
     * @param text text of tweet
     * @return list of extracted entities
     */
    public List<Entity> extractEntitiesWithIndices(String text) {
        List<Entity> entities = new ArrayList<Entity>();
        entities.addAll(extractURLsWithIndices(text));
        entities.addAll(extractHashtagsWithIndices(text, false));
        entities.addAll(extractMentionsOrListsWithIndices(text));
        entities.addAll(extractCashtagsWithIndices(text));

        removeOverlappingEntities(entities);
        return entities;
    }

    /**
     * Extract @username references from Tweet text. A mention is an occurance
     * of @username anywhere in a Tweet.
     *     
* @param text of the tweet from which to extract usernames
     * @return List of usernames referenced (without the leading @ sign)
     */
    public List<String> extractMentionedScreennames(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> extracted = new ArrayList<String>();
        for (Entity entity : extractMentionedScreennamesWithIndices(text)) {
            extracted.add(entity.value);
        }
        return extracted;
    }

    /**
     * Extract @username references from Tweet text. A mention is an occurance
     * of @username anywhere in a Tweet.
     *     
* @param text of the tweet from which to extract usernames
     * @return List of usernames referenced (without the leading @ sign)
     */
    public List<Entity> extractMentionedScreennamesWithIndices(String text) {
        List<Entity> extracted = new ArrayList<Entity>();
        for (Entity entity : extractMentionsOrListsWithIndices(text)) {
            if (entity.listSlug == null) {
                extracted.add(entity);
            }
        }
        return extracted;
    }

    public List<Entity> extractMentionsOrListsWithIndices(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

// Performance optimization.
// If text doesn't contain @/@ at all, the text doesn't
// contain @mention. So we can simply return an empty list.
        boolean found = false;
        for (char c : text.toCharArray()) {
            if (c == '@' || c == '@') {
                found = true;
                break;
            }
        }
        if (!found) {
            return Collections.emptyList();
        }

        List<Entity> extracted = new ArrayList<Entity>();
        Matcher matcher = Regex.VALID_MENTION_OR_LIST.matcher(text);
        while (matcher.find()) {
            String after = text.substring(matcher.end());
            if (!Regex.INVALID_MENTION_MATCH_END.matcher(after).find()) {
                if (matcher.group(Regex.VALID_MENTION_OR_LIST_GROUP_LIST) == null) {
                    extracted.add(new Entity(matcher, Entity.Type.MENTION, Regex.VALID_MENTION_OR_LIST_GROUP_USERNAME));
                } else {
                    extracted.add(new Entity(matcher.start(Regex.VALID_MENTION_OR_LIST_GROUP_USERNAME) - 1,
                            matcher.end(Regex.VALID_MENTION_OR_LIST_GROUP_LIST),
                            matcher.group(Regex.VALID_MENTION_OR_LIST_GROUP_USERNAME),
                            matcher.group(Regex.VALID_MENTION_OR_LIST_GROUP_LIST),
                            Entity.Type.MENTION));
                }
            }
        }
        return extracted;
    }

    /**
     * Extract a @username reference from the beginning of Tweet text. A reply
     * is an occurance of @username at the beginning of a Tweet, preceded by 0
     * or more spaces.
     *     
* @param text of the tweet from which to extract the replied to username
     * @return username referenced, if any (without the leading @ sign). Returns
     * null if this is not a reply.
     */
    public String extractReplyScreenname(String text) {
        if (text == null) {
            return null;
        }

        Matcher matcher = Regex.VALID_REPLY.matcher(text);
        if (matcher.find()) {
            String after = text.substring(matcher.end());
            if (Regex.INVALID_MENTION_MATCH_END.matcher(after).find()) {
                return null;
            } else {
                return matcher.group(Regex.VALID_REPLY_GROUP_USERNAME);
            }
        } else {
            return null;
        }
    }
	
	public String extractReplyScreennameByMR(String text) {
		if (text == null) {
			return null;
		}
				
		Matcher matcher = Regex.VALID_RETWEET_MODIFIED_TWEET_BY_MR.matcher(text);
		if (matcher.find()) {
			String newText = matcher.replaceAll("");
			return newText.replaceAll("\\s{2,}"," ");
		} else {
			return text;
		}
	}
	
	public String keepUsernamesWithApostrophe(String text){
    	if (text == null)
    		return null;
    	
    	Pattern p = Pattern.compile("\\s*(\\@|\\#)\\w+\\s{0,1}\\'(s|\\s)"); //but this may keep usernames/hashtags included in single apostrophes
		Matcher matcher = p.matcher(text);
		while (matcher.find()) {
			String textToKeep = matcher.group().replaceAll("\\s", "");
			if (textToKeep.contains("@"))
				textToKeep = textToKeep.replaceAll("@", "");
			else if (textToKeep.contains("#"))
				textToKeep = textToKeep.replaceAll("#", "");
			text = matcher.replaceFirst(" " + textToKeep + " ");
			matcher = p.matcher(text);
		}
		return text.replaceAll("\\s{2,}", " ");
    }
	
	public String keepUsernamesAndHashtagsWithPrepositions(String text) {
    	if (text == null)
    		return null;
    	
    	String[] prepositions = {"in", "for", "on", "at", "to", "the", "about", "over"}; //some additional that cause problems are by, from

    	
    	for (String preposition: prepositions){
    		preposition = "(" + preposition + "|" + preposition.substring(0, 1).toUpperCase() + preposition.substring(1) + ")";
    		Pattern p = Pattern.compile("\\s" + preposition + "\\s(\\@|\\#)\\w+(\\s|\\p{Punct})");
    		Matcher matcher = p.matcher(text);
    		while (matcher.find()) {
    			String textToKeep = matcher.group().replaceAll("(\\s|\\p{Punct})", " ").trim();
//    			if (textToKeep.contains("@"))
//    				textToKeep = textToKeep.replaceAll("@", " ");
//    			else if (textToKeep.contains("#"))
//    				textToKeep = textToKeep.replaceAll("#", " ");
    			String punctAll = matcher.group();
    			String punctEnd = punctAll.substring(punctAll.length()-1, punctAll.length());
    			if (punctEnd!=" ")
    				text = matcher.replaceFirst(" " + textToKeep + punctEnd);
    			else
    				text = matcher.replaceFirst(" " + textToKeep + " ");
    			matcher = p.matcher(text);
    		}
    	}	
    	String[] additional_prepositions = {"by", "from", "of"};
    	
    	for (String preposition: additional_prepositions){
    		preposition = "(" + preposition + "|" + preposition.substring(0, 1).toUpperCase() + preposition.substring(1) + ")";
    		Pattern p = Pattern.compile("\\s" + preposition + "\\s(\\#)\\w+(\\s|\\p{Punct})");
    		Matcher matcher = p.matcher(text);
    		while (matcher.find()) {
    			String textToKeep = matcher.group().replaceAll("(\\s|\\p{Punct})", " ").trim();
    			String punctAll = matcher.group();
    			String punctEnd = punctAll.substring(punctAll.length()-1, punctAll.length());
    			if (punctEnd!=" ")
    				text = matcher.replaceFirst(" " + textToKeep + punctEnd);
    			else
    				text = matcher.replaceFirst(" " + textToKeep + " ");
    			matcher = p.matcher(text);
    		}
    	}	  	
    	
    	
		return text.replaceAll("\\s{2,}", " ");
	}


    /**
     * Extract URL references from Tweet text.
     *     
* @param text of the tweet from which to extract URLs
     * @return List of URLs referenced.
     */
    public List<String> extractURLs(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> urls = new ArrayList<String>();
        for (Entity entity : extractURLsWithIndices(text)) {
            urls.add(entity.value);
        }
        return urls;
    }

    /**
     * Extract URL references from Tweet text.
     *     
* @param text of the tweet from which to extract URLs
     * @return List of URLs referenced.
     */
    public List<Entity> extractURLsWithIndices(String text) {
        if (text == null || text.isEmpty()
                || (extractURLWithoutProtocol ? text.indexOf('.') : text.indexOf(':')) == -1) {
            // Performance optimization.
            // If text doesn't contain '.' or ':' at all, text doesn't contain URL,
            // so we can simply return an empty list.
            return Collections.emptyList();
        }

        List<Entity> urls = new ArrayList<Entity>();

        Matcher matcher = Regex.VALID_URL.matcher(text);
        while (matcher.find()) {
            if (matcher.group(Regex.VALID_URL_GROUP_PROTOCOL) == null) {
                // skip if protocol is not present and 'extractURLWithoutProtocol' is false
                // or URL is preceded by invalid character.
                if (!extractURLWithoutProtocol
                        || Regex.INVALID_URL_WITHOUT_PROTOCOL_MATCH_BEGIN.matcher(matcher.group(Regex.VALID_URL_GROUP_BEFORE)).matches()) {
                    continue;
                }
            }
            String url = matcher.group(Regex.VALID_URL_GROUP_URL);
            int start = matcher.start(Regex.VALID_URL_GROUP_URL);
            int end = matcher.end(Regex.VALID_URL_GROUP_URL);
            Matcher tco_matcher = Regex.VALID_TCO_URL.matcher(url);
            if (tco_matcher.find()) {
                // In the case of t.co URLs, don't allow additional path characters.
                url = tco_matcher.group();
                end = start + url.length();
            }

            urls.add(new Entity(start, end, url, Entity.Type.URL));
        }

        return urls;
    }

    /**
     * Extract #hashtag references from Tweet text.
     *     
* @param text of the tweet from which to extract hashtags
     * @return List of hashtags referenced (without the leading # sign)
     */
    public List<String> extractHashtags(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> extracted = new ArrayList<String>();
        for (Entity entity : extractHashtagsWithIndices(text)) {
            extracted.add(entity.value);
        }

        return extracted;
    }

    /**
     * Extract #hashtag references from Tweet text.
     *     
* @param text of the tweet from which to extract hashtags
     * @return List of hashtags referenced (without the leading # sign)
     */
    public List<Entity> extractHashtagsWithIndices(String text) {
        return extractHashtagsWithIndices(text, true);
    }

    /**
     * Extract #hashtag references from Tweet text.
     *     
* @param text of the tweet from which to extract hashtags
     * @param checkUrlOverlap if true, check if extracted hashtags overlap URLs
     * and remove overlapping ones
     * @return List of hashtags referenced (without the leading # sign)
     */
    private List<Entity> extractHashtagsWithIndices(String text, boolean checkUrlOverlap) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

// Performance optimization.
// If text doesn't contain #/# at all, text doesn't contain
// hashtag, so we can simply return an empty list.
        boolean found = false;
        for (char c : text.toCharArray()) {
            if (c == '#' || c == '#') {
                found = true;
                break;
            }
        }
        if (!found) {
            return Collections.emptyList();
        }

        List<Entity> extracted = new ArrayList<Entity>();
        Matcher matcher = Regex.VALID_HASHTAG.matcher(text);

        while (matcher.find()) {
            String after = text.substring(matcher.end());
            if (!Regex.INVALID_HASHTAG_MATCH_END.matcher(after).find()) {
                extracted.add(new Entity(matcher, Entity.Type.HASHTAG, Regex.VALID_HASHTAG_GROUP_TAG));
            }
        }

        if (checkUrlOverlap) {
// extract URLs
            List<Entity> urls = extractURLsWithIndices(text);
            if (!urls.isEmpty()) {
                extracted.addAll(urls);
// remove overlap
                removeOverlappingEntities(extracted);
// remove URL entities
                Iterator<Entity> it = extracted.iterator();
                while (it.hasNext()) {
                    Entity entity = it.next();
                    if (entity.getType() != Entity.Type.HASHTAG) {
                        it.remove();
                    }
                }
            }
        }

        return extracted;
    }

    /**
     * Extract $cashtag references from Tweet text.
     *     
* @param text of the tweet from which to extract cashtags
     * @return List of cashtags referenced (without the leading $ sign)
     */
    public List<String> extractCashtags(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> extracted = new ArrayList<String>();
        for (Entity entity : extractCashtagsWithIndices(text)) {
            extracted.add(entity.value);
        }

        return extracted;
    }

    /**
     * Extract $cashtag references from Tweet text.
     *     
* @param text of the tweet from which to extract cashtags
     * @return List of cashtags referenced (without the leading $ sign)
     */
    public List<Entity> extractCashtagsWithIndices(String text) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

// Performance optimization.
// If text doesn't contain $, text doesn't contain
// cashtag, so we can simply return an empty list.
        if (text.indexOf('$') == -1) {
            return Collections.emptyList();

        }

        List<Entity> extracted = new ArrayList<Entity>();
        Matcher matcher = Regex.VALID_CASHTAG.matcher(text);

        while (matcher.find()) {
            extracted.add(new Entity(matcher, Entity.Type.CASHTAG, Regex.VALID_CASHTAG_GROUP_CASHTAG));
        }

        return extracted;
    }

    public void setExtractURLWithoutProtocol(boolean extractURLWithoutProtocol) {
        this.extractURLWithoutProtocol = extractURLWithoutProtocol;
    }

    public boolean isExtractURLWithoutProtocol() {
        return extractURLWithoutProtocol;
    }

    /*
     * Modify Unicode-based indices of the entities to UTF-16 based indices.
     *     
* In UTF-16 based indices, Unicode supplementary characters are counted as
     * two characters.
     *     
* This method requires that the list of entities be in ascending order by
     * start index.
     *     
* @param text original text @param entities entities with Unicode based
     * indices
     */
    public void modifyIndicesFromUnicodeToUTF16(String text, List<Entity> entities) {
        IndexConverter convert = new IndexConverter(text);

        for (Entity entity : entities) {
            entity.start = convert.codePointsToCodeUnits(entity.start);
            entity.end = convert.codePointsToCodeUnits(entity.end);
        }
    }

    /*
     * Modify UTF-16-based indices of the entities to Unicode-based indices.
     *     
* In Unicode-based indices, Unicode supplementary characters are counted as
     * single characters.
     *     
* This method requires that the list of entities be in ascending order by
     * start index.
     *     
* @param text original text @param entities entities with UTF-16 based
     * indices
     */
    public void modifyIndicesFromUTF16ToToUnicode(String text, List<Entity> entities) {
        IndexConverter convert = new IndexConverter(text);

        for (Entity entity : entities) {
            entity.start = convert.codeUnitsToCodePoints(entity.start);
            entity.end = convert.codeUnitsToCodePoints(entity.end);
        }
    }

    public String removeUrls(String text) {
        List<String> urls = extractURLs(text);
        for (String url : urls) {
            text = text.replace(url, " ");
        }
        return text;
    }
	
    public String removeHashtags(String text) {
    	
    	if (text!=null)
    		text = text.replaceAll(hashtagRegExpMR, "");
//		List<String> hashtags = extractHashtags(text);
//		for (String hashtag : hashtags) {
//			text = text.replace("#" + hashtag, "");
//		}
		return text;
	}

    public String processHashtags(String text) {
    	String text_tmp=text;
    	if (text!=null){
            //text = text.replaceAll(hashtagRegExpMR, "");
            List<String> hashtags = extractHashtags(text);
            for (String hashtag : hashtags) {
                int posFirstStop=text.indexOf(".");
                if(posFirstStop==-1) posFirstStop=text.length();
                int posFirstComma=text.indexOf(",");
                if(posFirstComma==-1) posFirstComma=text.length();
                int posFirst=posFirstStop;
                if(posFirstComma<posFirst) posFirst=posFirstComma;
                int posHash=text.indexOf(hashtag);
                
                if(posHash>posFirst)
                    text = text.replace("#" + hashtag, "");
/*
                else{
                    String bestRep=getBestHashtagReplacement(hashtag);
                    System.out.println("Pre rep: "+text);
                    int possp=bestRep.indexOf(" ");
                    System.out.println("Pos : "+possp);
                    text = text.replaceAll("#" + hashtag, bestRep);
                    System.out.println("After rep: "+text);
                    System.out.println("Best match: "+bestRep);
                }
                */
            }
            
            
            
            
            String[] parts=text.split("\\p{Space}");
            text_tmp="";
            boolean inHashTagRegion=true;
            for(int i=parts.length-1;i>=0;i--){
                String next=parts[i];
                if((next.startsWith("#")) && inHashTagRegion ){
                }
                else {
                    if(next.startsWith("#")){
                        String best=getBestHashtagReplacement(next.substring(1));
                        String[] pps=best.split(" ");
                        for(int o=pps.length-1;o>=0;o--){
                            text_tmp=pps[o]+" "+text_tmp;
                        }
                    }
                    else{
                        text_tmp=next+" "+text_tmp;
                        if(inHashTagRegion)
                            inHashTagRegion=false;
                    }
                }
            }
            
            text=text_tmp.trim();
            
            return text;
	}
        return text_tmp;
    }
    
    
    public String removeScreennames(String text) {
        List<String> screen_names = this.extractMentionedScreennames(text);
        for (String screen_name : screen_names) {
            text = text.replace("@" + screen_name, " ");
        }
        return text;
    }
	
	public String removeMultiplePunctuation(String text){
//		String rx = "(([^a-zA-Z0-9@#$\\s]){2,})";
		String rx = "([^\\w\\s\\/])\\1+"; //any repeated same (\\1 - group) symbol except \s and // (for http)
//		String rx = "([^\\w\\/])\\1+"; //any repeated same (\\1 - group) symbol except \s and // (for http)

		Pattern pattern = Pattern.compile(rx);
		Matcher matcher = pattern.matcher(text);
		
		String currentSymbol = null;
		String replacement = null;
		
//		System.out.println("before:" + text);
		
		while (matcher.find()) {
			String extractingSymbols = matcher.group().replaceAll("\\s", "");
//			System.out.println("Extracting symbols " + extractingSymbols);
			currentSymbol = extractingSymbols.substring(
					extractingSymbols.length() - 2,
					extractingSymbols.length() - 1);
			replacement = currentSymbol + " ";
			if (currentSymbol.equals("$")) // either wise it throws Illegal Argument Exception 
				replacement = " ";
			text = matcher.replaceFirst(replacement);
			matcher = matcher.reset(text);
		}
		text = text.replaceAll("\\s+", " ");
//		System.out.println("after:" + text);
		return text; 	
    }

    public String cleanText(String text) {

        if (text != null && !text.equals("")) {

//            text = removeUrls(text);
//            text = removeScreennames(text);

            //text = TumblerStringUtils.decamelise(text);

            //text = text.toLowerCase();
//            text = text.replaceAll("_", " ").replaceAll("#", " ").replaceAll("@", " ").replaceAll("tiff53", " ").replaceAll("\\ART", "").replaceAll("&amp;", " ").replaceAll("http://", " ");
//            text=text.replaceAll(urlRegExp,"").replaceAll(hashtagRegExp,"").replaceAll(usermentionRegExp,"").replaceAll("_"," ").replaceAll("\\ART", "").replaceAll("&amp;", " ");
            text=text.replaceAll("\\ART", "").replaceAll(usermentionRegExp,"").replaceAll(urlRegExp,"").replaceAll(hashtagRegExp,"").replaceAll("_"," ").replaceAll("&amp;", " ");
            text=text.replaceAll("[\\.]+", ".");
            text=text.replaceAll("[\\!]+", "!");
            text=text.replaceAll("[-]+", "-");
//            text=text.replaceAll(hashtagRegExp,"").replaceAll("_"," ").replaceAll("\\ART", "").replaceAll("&amp;", " ");
        }
        return text.trim();
    }

    /**
     * An efficient converter of indices between code points and code units.
     */
    private static final class IndexConverter {

        protected final String text;
// Keep track of a single corresponding pair of code unit and code point
// offsets so that we can re-use counting work if the next requested
// entity is near the most recent entity.
        protected int codePointIndex = 0;
        protected int charIndex = 0;

        IndexConverter(String text) {
            this.text = text;
        }

        /**
         * @param charIndex Index into the string measured in code units.
         * @return The code point index that corresponds to the specified
         * character index.
         */
        int codeUnitsToCodePoints(int charIndex) {
            if (charIndex < this.charIndex) {
                this.codePointIndex -= text.codePointCount(charIndex, this.charIndex);
            } else {
                this.codePointIndex += text.codePointCount(this.charIndex, charIndex);
            }
            this.charIndex = charIndex;

// Make sure that charIndex never points to the second code unit of a
// surrogate pair.
            if (charIndex > 0 && Character.isSupplementaryCodePoint(text.codePointAt(charIndex - 1))) {
                this.charIndex -= 1;
            }
            return this.codePointIndex;
        }

        /**
         * @param codePointIndex Index into the string measured in code points.
         * @return the code unit index that corresponds to the specified code
         * point index.
         */
        int codePointsToCodeUnits(int codePointIndex) {
// Note that offsetByCodePoints accepts negative indices.
            this.charIndex = text.offsetByCodePoints(this.charIndex, codePointIndex - this.codePointIndex);
            this.codePointIndex = codePointIndex;
            return this.charIndex;
        }
    }
        
    private String getBestHashtagReplacement(String hashtag){
        try {
            if(hashtag.startsWith("#"))
                hashtag=hashtag.substring(1);
            Matcher mtchrPunc = ptrnPunc.matcher(hashtag);    
            String punct="";
            if(mtchrPunc.find()){
                int pos_punct=mtchrPunc.start();
                if(pos_punct>-1)
                    punct=hashtag.substring(pos_punct);
            }
            hashtag=hashtag.replaceAll("\\p{Punct}","");
            List<RuleMatch> matches = langTool.check(hashtag);
            String bestMatch=hashtag;
            int largestBit=1;
            
            
            for (RuleMatch match : matches) {
                List<String> replacements=match.getSuggestedReplacements();
                for(String str:replacements){
                    String[] parts=str.split(" ");
                    String new_str=str.replaceAll(" ","");
                    if((new_str.equals(hashtag))&&(parts.length>largestBit)){
                        largestBit=parts.length;
                        bestMatch=str;
                    }
                }
            }
            return bestMatch+punct;
        } catch (IOException ex) {
            Logger.getLogger(Extractor.class.getName()).log(Level.SEVERE, null, ex);
            return hashtag;
        }
    }
    
    
}