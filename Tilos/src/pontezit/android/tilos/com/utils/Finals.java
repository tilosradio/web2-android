package pontezit.android.tilos.com.utils;

import android.os.Build;

public final class Finals {
	/* LOG settings; LOG LEVELS: 1. IMPORTANT, 2. INFORMATIVE, 3. SPAM */
    public static final String LOG_TAG      = "Tilos";
    public static final boolean LOG_ENABLED = true;
    public static final int LOG_LEVEL       = 1;

    /* URL */
    //public static final String LIVE_HI_URL = "http://stream.tilos.hu:80/tilos";

    public static String getLiveHiUrl(){
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.ICE_CREAM_SANDWICH)
            return "http://stream.tilos.hu:80/tilos";
        else
            return "http://stream.tilos.hu:80/tilos_high.ogg";
    }


    public static final String API_BASE_URL = "http://tilos.hu/api/v0/";
    public final static String CALL_NO = "tel:+3612153773";

    /* Preferences */
    public static final String PREFS_SHARED = "SharedPrefs";
    public static final String PREFS_FAVORITES = "FavortiePrefs";
}