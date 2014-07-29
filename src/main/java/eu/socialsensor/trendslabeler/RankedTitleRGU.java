package eu.socialsensor.trendslabeler;

import eu.socialsensor.framework.common.domain.Item;

public class RankedTitleRGU implements Comparable<RankedTitleRGU> {
	
	private String title;
        private Item item;
    private double score;

    public RankedTitleRGU(String title, Item item,double score) {
        this.title = title;
        this.score = score;
        this.item = item;
    }
    
    public RankedTitleRGU(String title, Item item){
    	this.title = title;
    	this.score = -1.0;
        this.item=item;
    }
    
    /*
    public RankedTitleRGU(){
    	this.title = "";
    	this.score = -1.0;
    }
    */
    
    public String getTitle() {
        return title;
    }

    public Item getItem() {
        return item;
    }
    
    
    public double getScore() {
        return score;
    }
    public void setTitle(String title){
    	this.title = title;
    }
    public void setScore(double score){
        this.score = score;
    }

    public void setItem(Item item){
    	this.item = item;
    }
    
    public void calculateScore(double a,
			int numberOfTermsContainedInTweet,
			int totalNumberOfUniqueTerms,
			int numberOfDuplicatesLikeCurrentTweet, int numberOfTweets) {
		
		double score = a * numberOfTermsContainedInTweet
				/ totalNumberOfUniqueTerms + (1 - a)
				* numberOfDuplicatesLikeCurrentTweet / numberOfTweets;
		setScore(score);
	}

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof RankedTitleRGU){
            return title.equals( ((RankedTitleRGU)obj).getTitle() );
        }
        return false;
    }

    @Override
    public int hashCode() {
        return title.hashCode();
    }

    public int compareTo(RankedTitleRGU o) {
        if (this.getScore() < o.getScore()){
            return -1;
        } else if (this.getScore() > o.getScore()) {
            return 1;
        } else {
            return 0;
        }
    }


}
