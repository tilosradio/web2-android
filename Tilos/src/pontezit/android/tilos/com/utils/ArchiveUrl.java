package pontezit.android.tilos.com.utils;

import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class ArchiveUrl{

    public static String getUrl(int unixTime){
        Date date = new Date((long) unixTime*1000);
        SimpleDateFormat slashFormatter = new SimpleDateFormat("yyyy/MM/dd");
        SimpleDateFormat hyphenFormatter = new SimpleDateFormat("yyyyMMdd-kkmm");
        String url = "http://archive.tilos.hu/online/"+slashFormatter.format(date)+"/tilosradio-"+hyphenFormatter.format(date)+".mp3";
        LogHelper.Log("ArchiveUrl; getUrl; return value: " + url);
        return url;
    }

    public static String getNextUrl(String nowUrl){



        Date date = parseUrlToDate(nowUrl);
        long nextTimeStamp = date.getTime() + 1800000;
        Date nowDate = new Date();
        Date nextDate = new Date(nextTimeStamp);
        if(nextDate.after(nowDate))
            return Finals.getLiveHiUrl();

        return getUrl((int) (nextTimeStamp/1000));
    }

    public static String getPrevUrl(String nowUrl){
        if(nowUrl == Finals.getLiveHiUrl()){
            Calendar calendar = Calendar.getInstance();;
            if(calendar.get(Calendar.MINUTE) < 30){
                calendar.set(Calendar.MINUTE, 30);
                calendar.add(Calendar.HOUR, -1);
            }else{
                calendar.set(Calendar.MINUTE, 0);
            }
            return getUrl((int) ((calendar.getTime().getTime())/1000));
        }else{
            Date date = parseUrlToDate(nowUrl);
            long nextTimeStamp = date.getTime() - 1800000;
            return getUrl((int) (nextTimeStamp/1000));
        }

    }

    public static Date parseUrlToDate(String nowUrl){
        Date date = new Date();
        try{
            int length = nowUrl.length();
            String min  = nowUrl.substring(length - 6, length - 4);
            String hour = nowUrl.substring(length - 8, length - 6);
            String day = nowUrl.substring(length - 11, length - 9);
            String month = nowUrl.substring(length - 13, length - 11);
            String year = nowUrl.substring(length - 17, length - 13);

            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd;kk:mm");
            LogHelper.Log("ArchiveUrl; parsedValues:"+ year + "-" + month + "-" + day + ";" + hour + ":" + min, 1);
            try{
                date = formatter.parse(year + "-" + month + "-" + day + ";" + hour + ":" + min);
            }catch(ParseException e){
                e.printStackTrace();
            }

            SimpleDateFormat hyphenFormatter = new SimpleDateFormat("yyyyMMdd-kkmm");
            LogHelper.Log("ArchiveUrl; parsedDate: " + hyphenFormatter.format(date), 3);
        }catch(NullPointerException e){
            LogHelper.Log("parseUrlToDate, NullPointerException", 1);
        }
        return date;
    }

}
