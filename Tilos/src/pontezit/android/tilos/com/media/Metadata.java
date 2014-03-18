package pontezit.android.tilos.com.media;

public class Metadata {

	private boolean isLive;
	private String readableTime;
	private String showName;

	/**
	 * Default constructor
	 */
	public Metadata() {
		
	}

    public boolean isLive(){
        return isLive;
    }

    public void setLive(boolean isLive){
        this.isLive = isLive;
    }

    public String getReadableTime(){
        return readableTime;
    }

    public void setReadableTime(String readableTime){
        this.readableTime = readableTime;
    }

    public String getShowName(){
        return showName;
    }

    public void setShowName(String showName){
        this.showName = showName;
    }
}
