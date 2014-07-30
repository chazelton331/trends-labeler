package eu.socialsensor.trendslabeler;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.log4j.Logger;

import eu.socialsensor.framework.common.domain.MediaItem;
import eu.socialsensor.framework.common.domain.StreamUser;
import eu.socialsensor.framework.common.domain.WebPage;
import eu.socialsensor.framework.retrievers.socialmedia.SocialMediaRetriever;
import eu.socialsensor.framework.retrievers.socialmedia.dailymotion.DailyMotionRetriever;
import eu.socialsensor.framework.retrievers.socialmedia.facebook.FacebookRetriever;
import eu.socialsensor.framework.retrievers.socialmedia.instagram.InstagramRetriever;
import eu.socialsensor.framework.retrievers.socialmedia.twitpic.TwitpicRetriever;
import eu.socialsensor.framework.retrievers.socialmedia.vimeo.VimeoRetriever;
import eu.socialsensor.framework.retrievers.socialmedia.youtube.YoutubeRetriever;

public class MediaURLProcessor {

    /**
	 * 
	 */
	private static final long serialVersionUID = -2548434425109192911L;

	private static final String SUCCESS = "success";
	private static final String FAILED = "failed";

	private static String MEDIA_STREAM = "media";
	private static String WEBPAGE_STREAM = "webpage";

	private Logger logger;

	private static String instagramClientId = "50fca9eded824679934a71cfa8dda880";
	//private static String instagramToken = "";
	//private static String instagramSecret = "";

	private static String facebookToken = "260504214011769|jATWKceE7aVH4jxsB4DBuNjKBRc";

	private static String youtubeClientId = "manosetro";
	private static String youtubeDevKey = "AI39si6DMfJRhrIFvJRv0qFubHHQypIwjkD-W7tsjLJArVKn9iR-QoT8t-UijtITl4TuyHzK-cxqDDCkCBoJB-seakq1gbt1iQ";

	private static Pattern instagramPattern 	= 	Pattern.compile("https*://instagram.com/p/([\\w\\-]+)/");
	private static Pattern youtubePattern 		= 	Pattern.compile("https*://www.youtube.com/watch?.*v=([a-zA-Z0-9_\\-]+)(&.+=.+)*");
	private static Pattern vimeoPattern 		= 	Pattern.compile("https*://vimeo.com/([0-9]+)/*$");
	private static Pattern twitpicPattern 		= 	Pattern.compile("https*://twitpic.com/([A-Za-z0-9]+)/*.*$");
	private static Pattern dailymotionPattern 	= 	Pattern.compile("https*://www.dailymotion.com/video/([A-Za-z0-9]+)_.*$");
	private static Pattern facebookPattern 		= 	Pattern.compile("https*://www.facebook.com/photo.php?.*fbid=([a-zA-Z0-9_\\-]+)(&.+=.+)*");

	private static Map<String, SocialMediaRetriever> retrievers = new HashMap<String, SocialMediaRetriever>();


        static{
//            logger = Logger.getLogger(MediaURLProcessor.class);
            retrievers.put("instagram", new InstagramRetriever(instagramClientId));
            retrievers.put("youtube", new YoutubeRetriever(youtubeClientId, youtubeDevKey));
            retrievers.put("vimeo", new VimeoRetriever());
            retrievers.put("twitpic", new TwitpicRetriever());
            retrievers.put("dailymotion", new DailyMotionRetriever());
            retrievers.put("facebook", new FacebookRetriever(facebookToken));
	}

	public static String getMediaItemsURL(String url) {
		SocialMediaRetriever retriever = null;
		String mediaId = null;
		String source = null;
		Matcher matcher;
		if((matcher = instagramPattern.matcher(url)).matches()) {
			mediaId = matcher.group(1);
			retriever = retrievers.get("instagram");
			source = "instagram";
		}
		else if((matcher = youtubePattern.matcher(url)).matches()) {
			mediaId = matcher.group(1);
			retriever = retrievers.get("youtube");
			source = "youtube";
		}
		else if((matcher = vimeoPattern.matcher(url)).matches()){
			mediaId = matcher.group(1);
			retriever = retrievers.get("vimeo");
			source = "vimeo";
		}
		else if((matcher = twitpicPattern.matcher(url)).matches()) {
			mediaId = matcher.group(1);
			retriever = retrievers.get("twitpic");
			source = "twitpic";
		}
		else if((matcher = dailymotionPattern.matcher(url)).matches()) {
			mediaId = matcher.group(1);
			retriever = retrievers.get("dailymotion");
			source = "dailymotion";
		}
		else if((matcher = facebookPattern.matcher(url)).matches()) {
			mediaId = matcher.group(1);
			retriever = retrievers.get("facebook");
			source = "facebook";
		}
		else {
//			logger.error(url + " matches nothing.");
			return null;
		}

		if(mediaId == null || retriever == null) {
			return null;
		}

		try {
			MediaItem mediaItem = retriever.getMediaItem(mediaId);
			if(mediaItem == null) {
//				logger.info(mediaId + " from " + source + " is null");
				return null;
			}

			mediaItem.setPageUrl(url);

			StreamUser streamUser = mediaItem.getUser();
			String userid = mediaItem.getUserId();

                        /*
			if(streamUser == null || userid == null) {
				streamUser = retriever.getStreamUser(userid);
				if(streamUser == null) {
					throw new Exception("Missing " + mediaItem.getStreamId() + " user: " + userid);
				}
				mediaItem.setUser(streamUser);
				mediaItem.setUserId(streamUser.getId());
			}*/
                        if(mediaItem==null)
                            return null;
                        else
                        {   
                            String res=mediaItem.getUrl();
                            if(res==null) res="empty";
                            return res;
                        }
		}
		catch(Exception e) {
			//logger.error(e);
                    return null;
		}
	}

	public static String getMediaItemsType(String url) {
		SocialMediaRetriever retriever = null;
		String mediaId = null;
		String source = null;
		Matcher matcher;
		if((matcher = instagramPattern.matcher(url)).matches()) {
			mediaId = matcher.group(1);
			return "image";
		}
		else if((matcher = youtubePattern.matcher(url)).matches()) {
			mediaId = matcher.group(1);
			return "video";
		}
		else if((matcher = vimeoPattern.matcher(url)).matches()){
			mediaId = matcher.group(1);
			return "video";
		}
		else if((matcher = twitpicPattern.matcher(url)).matches()) {
			mediaId = matcher.group(1);
			return "image";
		}
		else if((matcher = dailymotionPattern.matcher(url)).matches()) {
			mediaId = matcher.group(1);
			return "video";
		}
		else if((matcher = facebookPattern.matcher(url)).matches()) {
			mediaId = matcher.group(1);
			return "image";
		}
		else {
			return null;
		}
	}
        
        
        //http:\/\/twitter.com\/thefadotcom\/status\/198807827765145600\/photo\/1
        public static void main(String[] args){
            String url="http://www.youtube.com/watch?v=Xfv1EcQ532w";
//            String url="http://instagram.com/p/proAoHlYWJ/";
//          String url="http://vimeo.com/93052180";
//            String url="http://twitpic.com/e919fu";
//            String url="http://www.dailymotion.com/video/x22b3wb_footage-of-cooling-towers-being-demolished-at-didcot-power-station_lifestyle/";
//            String url="https://www.facebook.com/photo.php?fbid=7674026193";
            String mediaUrl=MediaURLProcessor.getMediaItemsURL(url);
            String mediaType=MediaURLProcessor.getMediaItemsType(url);
            
            System.out.println("Original url: "+url);
            System.out.println("Media url: "+mediaUrl);
            System.out.println("Media type: "+mediaType);
        }
        
}