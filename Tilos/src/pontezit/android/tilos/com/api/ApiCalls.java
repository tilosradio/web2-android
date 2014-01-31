package pontezit.android.tilos.com.api;



import android.os.Handler;

import com.loopj.android.http.AsyncHttpResponseHandler;

import java.util.ArrayList;
import flexjson.JSONDeserializer;
import pontezit.android.tilos.com.modell.Episode;



public class ApiCalls{

    public ArrayList<Episode> episodeList = null;

    public ApiCalls(){
        return;
    }

    /*
    public Episode getCurrentEpisode(Context context){

        TilosRestClient.get("api/v0/episode", null, new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(String episodes) {
                episodeList = new JSONDeserializer<ArrayList<Episode>>().deserialize(episodes);
            }
        });

        return episodeList.get(0);
    }
    */


}
