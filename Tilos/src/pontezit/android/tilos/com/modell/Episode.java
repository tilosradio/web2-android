package pontezit.android.tilos.com.modell;

import java.util.Date;

public class Episode{

    private int plannedFrom;

    private int plannedTo;

    private Date plannedFromDate;

    private Date plannedToDate;

    private Show show;

    private String m3uUrl;

    public Episode(){
    }

    public int getPlannedTo(){
        return plannedTo;
    }

    public void setPlannedTo(int plannedTo){
        this.plannedTo = plannedTo;
    }

    public int getPlannedFrom(){
        return plannedFrom;
    }

    public void setPlannedFrom(int plannedFrom){
        this.plannedFrom = plannedFrom;
    }

    public Date getPlannedToDate(){
        return new Date((long) plannedTo * 1000);
    }

    public Date getPlannedFromDate (){
        return new Date((long) plannedFrom * 1000);
    }

    public Show getShow(){
        return show;
    }

    public void setShow(Show show){
        this.show = show;
    }

    public String getM3uUrl(){
        return m3uUrl;
    }

    public void setM3uUrl(String m3uUrl){
        this.m3uUrl = m3uUrl;
    }
}
