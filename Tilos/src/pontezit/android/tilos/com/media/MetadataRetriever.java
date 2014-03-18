/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2013 William Seemann
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pontezit.android.tilos.com.media;

import flexjson.JSONDeserializer;
import pontezit.android.tilos.com.R;
import pontezit.android.tilos.com.modell.Episode;
import pontezit.android.tilos.com.modell.Show;
import pontezit.android.tilos.com.provider.Media;
import pontezit.android.tilos.com.utils.ArchiveUrl;
import pontezit.android.tilos.com.utils.Finals;
import pontezit.android.tilos.com.utils.LogHelper;
import pontezit.android.tilos.com.utils.PreferenceConstants;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseArray;

import com.androidquery.AQuery;
import com.androidquery.callback.AjaxCallback;
import com.androidquery.callback.AjaxStatus;

import java.text.SimpleDateFormat;
import java.util.ArrayList;

public class MetadataRetriever{
	
	private boolean mIsCancelled;

	private Context mContext = null;
    private String path;
    private Metadata metadata;
	private MetadataRetrieverListener mListener;
    private AQuery aq;
    public boolean finished = true;
	public MetadataRetriever(Context context, String path) {
		mIsCancelled = false;
		this.path = path;
		mContext = context;

		// Verify that the host activity implements the callback interface
	    try {
	    	// Instantiate the MetadataRetrieverListener so we can send events to the host
	        mListener = (MetadataRetrieverListener) context;
	    } catch (ClassCastException e) {
	        // The activity doesn't implement the interface, throw exception
	        throw new ClassCastException(context.toString()
	        	+ " must implement MetadataRetrieverListener");
	    }
	}

    public void getMetadata(){
        LogHelper.Log("MetadataRetriever; getMetadata run", 1);
        metadata = new Metadata();
        mListener.onMetadataParsed(metadata);
        aq = new AQuery(mContext);
        finished = false;
        int before = (int) getCurrentPlayUnixTime()-(60*60*4);
        String url = Finals.API_BASE_URL + "episode?start=" + before;
        aq.ajax(url, String.class, new AjaxCallback<String>(){
            @Override
            public void callback(String url, String episodesString, AjaxStatus status){
                LogHelper.Log("MetadataRetriever; ajaxQuery callback", 1);
                if(episodesString != null){
                    ArrayList<Episode> episodeList = new JSONDeserializer<ArrayList<Episode>>().use(null, ArrayList.class)
                                                                                               .use("values", Episode.class)
                                                                                               .use("values.show", Show.class)
                                                                                               .deserialize(episodesString);

                    try{
                        long unixTime = getCurrentPlayUnixTime();
                        for(Episode episode : episodeList){
                            LogHelper.Log(episode.getPlannedFrom() + "<" + unixTime + ";" + episode.getPlannedTo() + ">" + unixTime + ", " + episode.getShow().getName(), 3);
                            if(episode.getPlannedFrom() < unixTime && episode.getPlannedTo() >= unixTime){

                                if(path == Finals.getLiveHiUrl()){
                                    LogHelper.Log("MetadataRetriever; ajaxQuery callback; live tree", 1);
                                    SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");
                                    String from = dateFormat.format(episode.getPlannedFromDate());
                                    String to = dateFormat.format(episode.getPlannedToDate());
                                    metadata.setLive(true);
                                    metadata.setReadableTime(from + " - " + to);
                                    metadata.setShowName(episode.getShow().getName());
                                    mListener.onMetadataParsed(metadata);
                                }else{
                                    LogHelper.Log("MetadataRetriever; ajaxQuery callback; archive tree", 1);
                                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                                    String from = dateFormat.format(getCurrentPlayUnixTime()*1000);
                                    metadata.setLive(false);
                                    metadata.setReadableTime(from);
                                    metadata.setShowName(episode.getShow().getName());
                                    mListener.onMetadataParsed(metadata);
                                }

                            }

                        }


                    }catch(NullPointerException e){
                        LogHelper.Log("adásinfo failure; NullpointerException", 1);
                    }

                }else{
                    LogHelper.Log("adásinfo failure", 1);
                }
                finished = true;
            }
        });

        return;
    }

    public long getCurrentPlayUnixTime(){
        long unixTime;
        LogHelper.Log("MetadataRetriever; getCurrentPlayUnixTime;");

        if(path != Finals.getLiveHiUrl()){
            unixTime = ArchiveUrl.parseUrlToDate(path).getTime()/1000L;
            unixTime += 12L;
        }else{
            unixTime = System.currentTimeMillis() / 1000L;
        }


        return unixTime;
    }

    public void stopRetriever(){
        aq.ajaxCancel();
    }



}
