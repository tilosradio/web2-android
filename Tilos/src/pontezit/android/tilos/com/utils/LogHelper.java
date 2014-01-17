package pontezit.android.tilos.com.utils;

import android.util.Log;

public class LogHelper{

	public static void Log(String logValue){
		if(Finals.LOG_ENABLED)
			Log.v(Finals.LOG_TAG, logValue);
		else
			return;
	}
	
	public static void Log(String logValue, int logLevel){
		if(Finals.LOG_ENABLED && logLevel <= Finals.LOG_LEVEL)
			Log.v(Finals.LOG_TAG, logValue);
		else
			return;
	}
	
	public static void Log(String logTag, String logValue){
		if(Finals.LOG_ENABLED)
			Log.v(logTag, logValue);
		else
			return;
	}
	
	public static void Log(String logTag, String logValue, int logLevel){
		if(Finals.LOG_ENABLED && logLevel <= Finals.LOG_LEVEL)
			Log.v(logTag, logValue);
		else
			return;
	}
}