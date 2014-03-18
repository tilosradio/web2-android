/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2012 William Seemann
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

package pontezit.android.tilos.com.utils;

import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;

/**
 * Asynchronous task that prepares a MusicRetriever. This asynchronous task essentially calls
 * MusicRetriever#prepare() on a  MusicRetriever, which may take some time to
 * run. Upon finishing, it notifies the indicated {@MusicRetrieverPreparedListener}.
 */
public class DetermineActionTask extends AsyncTask<Void, Void, Void> {
    
	public static final String URL_ACTION_UNDETERMINED = "undetermined";
	public static final String URL_ACTION_PLAY = "play";
	
	private Context mContext;
	private MusicRetrieverPreparedListener mListener;
	private Uri mUri;
	private String mAction;
	private long[] mList;

    public DetermineActionTask(Context context, String url, MusicRetrieverPreparedListener listener) {
        LogHelper.Log("DetermineActionTask; constructor run", 1);
    	mContext = context;
        mUri = Uri.parse(url);
        mListener = listener;
    }

	@Override
    protected Void doInBackground(Void... arg0) {
        processUri();
	    return null;
	}

	private void processUri() {
        LogHelper.Log("DetermineActionTask; processUri run", 1);
        HTTPTransport transport = new HTTPTransport(mUri);
		try {
            mAction = URL_ACTION_PLAY;
            LogHelper.Log("DetermineAction, isPotentialPlayList", 1);

            //mList = MusicUtils.getFilesInPlaylist(mContext, mUri, transport.getContentType(), transport.getConnection());

		} catch (Exception e) {
            LogHelper.Log("DetermineActionTask; processUri exception catch: " + e, 1);
			e.printStackTrace();
			mAction = URL_ACTION_UNDETERMINED;
		}
	}
	
    @Override
    protected void onPostExecute(Void result) {
        LogHelper.Log("DetermineActionTask; onPostExecute run; path:" + mUri.toString(), 1);
        mListener.onMusicRetrieverPrepared(mAction, mUri.toString());
    }
	
    /**
	 * @return the mUri
	 */
	public Uri getUri() {
		return mUri;
	}

	public interface MusicRetrieverPreparedListener {
        public void onMusicRetrieverPrepared(String action, String path);
    }
}