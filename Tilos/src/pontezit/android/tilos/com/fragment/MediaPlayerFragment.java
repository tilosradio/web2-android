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

package pontezit.android.tilos.com.fragment;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import java.text.SimpleDateFormat;
import java.util.ArrayList;

import flexjson.JSONDeserializer;
import pontezit.android.tilos.com.R;
import pontezit.android.tilos.com.api.TilosRestClient;
import pontezit.android.tilos.com.bean.UriBean;
import pontezit.android.tilos.com.button.RepeatingImageButton;
import pontezit.android.tilos.com.dbutils.TilosDatabase;
import pontezit.android.tilos.com.modell.Episode;
import pontezit.android.tilos.com.modell.Show;
import pontezit.android.tilos.com.service.IMediaPlaybackService;
import pontezit.android.tilos.com.service.MediaPlaybackService;
import pontezit.android.tilos.com.transport.AbsTransport;
import pontezit.android.tilos.com.transport.TransportFactory;
import pontezit.android.tilos.com.utils.DetermineActionTask;
import pontezit.android.tilos.com.utils.LogHelper;
import pontezit.android.tilos.com.utils.MusicUtils;
import pontezit.android.tilos.com.utils.MusicUtils.ServiceToken;
import pontezit.android.tilos.com.utils.PreferenceConstants;

public class MediaPlayerFragment extends Fragment implements
        DetermineActionTask.MusicRetrieverPreparedListener{
	

    private IMediaPlaybackService mService = null;
    private ServiceToken mToken;

    private RepeatingImageButton mPrevButton;
    private ImageView mPauseButton;
    private RepeatingImageButton mNextButton;

    private TextView mTrackNumber;
    private TextView mTotalTime;
    private long mDuration;

    private long mPosOverride = -1;
    private ProgressBar mProgress;
    private int seekmethod;
    private boolean mSeeking = false;
    private long mStartSeekPos = 0;
    private long mLastSeekEventTime;
    private boolean mFromTouch = false;
    private TextView mCurrentTime;

    private TextView mListeningText;
    private ArrayList<Episode> episodeList = null;

    private boolean paused;

    private static final int REFRESH = 1;
    private static final int QUIT = 2;
    private TilosDatabase mStreamdb;
    private DetermineActionTask mDetermineActionTask;

    @Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        LogHelper.Log("MediaPlayerFragment; onCreateView running", 1);
		View view = inflater.inflate(R.layout.fragment_media_player, container, false);
        mPrevButton = (RepeatingImageButton) view.findViewById(R.id.previous_button);
        mPrevButton.setOnClickListener(mPrevListener);
        mPauseButton = (ImageView) view.findViewById(R.id.buttonPlayPause);
        mPauseButton.setOnClickListener(mPauseListener);
        mNextButton = (RepeatingImageButton) view.findViewById(R.id.next_button);
        mNextButton.setOnClickListener(mNextListener);
        mTotalTime = (TextView) view.findViewById(R.id.duration_text);
        mCurrentTime = (TextView) view.findViewById(R.id.position_text);

        mProgress = (ProgressBar) view.findViewById(R.id.seek_bar);
        mListeningText = (TextView) view.findViewById(R.id.listening_now);

        seekmethod = 1;

        if (mProgress instanceof SeekBar) {
            SeekBar seeker = (SeekBar) mProgress;
            seeker.setOnSeekBarChangeListener(mSeekListener);
        }
        mProgress.setMax(1000);




		return view;
	}

    @Override
    public void onMusicRetrieverPrepared(String action, UriBean uri, long[] list) {
        LogHelper.Log("MediaPlayerActivity; onMusicRetrieverPrepared run", 1);

        if (action.equals(DetermineActionTask.URL_ACTION_UNDETERMINED)) {
            Toast.makeText(getActivity(), R.string.url_not_opened_message, Toast.LENGTH_SHORT).show();
        } else if (action.equals(DetermineActionTask.URL_ACTION_PLAY)) {
            mStreamdb.touchUri(uri);
            MusicUtils.playAll(getActivity(), list, 0);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        mStreamdb = new TilosDatabase(getActivity());
        LogHelper.Log("MediaPlayerFragment; onStart running", 1);
        mToken = MusicUtils.bindToService(getActivity(), osc);
        paused = false;
        if (mToken == null) {
            // something went wrong
            //mHandler.sendEmptyMessage(QUIT);
        }
        LogHelper.Log("MediaPlayerActivity; onStart run", 1);
        super.onStart();
        paused = false;
        processUri("http://stream.tilos.hu:80/tilos");

        mToken = MusicUtils.bindToService(getActivity(), osc);
        if (mToken == null) {
            // something went wrong
            LogHelper.Log("MediaPlayerActivity; onStart; mToken is null", 1);
            mHandler.sendEmptyMessage(QUIT);
        }

        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
        f.addAction(MediaPlaybackService.META_CHANGED);
        f.addAction(MediaPlaybackService.START_DIALOG);
        f.addAction(MediaPlaybackService.STOP_DIALOG);
        getActivity().registerReceiver(mStatusListener, new IntentFilter(f));
        long next = refreshNow();
        queueNextRefresh(next);
        updateTrackInfo();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        setPauseButtonImage();
        updateTrackInfo();
    }

    @Override
    public void onStop() {
        getActivity().unregisterReceiver(mStatusListener);
        MusicUtils.unbindFromService(mToken);
        mService = null;
        paused = true;
        mHandler.removeMessages(REFRESH);

        //unregisterReceiver(mStatusListener);
        MusicUtils.unbindFromService(mToken);
        mService = null;
        super.onStop();
        super.onStop();
    }
    
    @Override
    public void onDestroy() {

        super.onDestroy();
    }

    private void startPlayback() {
        LogHelper.Log("MediaPlayerActivity; startPlayback run", 1);
        if(mService == null)
            return;

        //updateTrackInfo();
        long next = refreshNow();
        queueNextRefresh(next);
    }

    private void updateTrackInfo(){


        //Régi:
        if (mService == null) {
            return;
        }
        try {
            String path = mService.getPath();

            mDuration = mService.duration();
            mTotalTime.setText(MusicUtils.makeTimeString(getActivity(), mDuration / 1000));
        } catch (RemoteException ex) {
            //TODO: valami ide kell
        }

        //ÚJ:
        //TODO: aktuális műsor. máshova vinni
        long unixTime = System.currentTimeMillis() / 1000L;
        RequestParams params = new RequestParams();
        int before = (int) unixTime-(60*60*4);
        params.put("start", before);
        //TODO: kivenni a kézzel beírt paramétert!
        TilosRestClient.get("episode?start=" + before, params, new AsyncHttpResponseHandler(){
            @Override
            public void onStart(){
                mListeningText.setText(getResources().getString(R.string.gettingInfo));
            }

            @Override
            public void onSuccess(String episodes){

                episodeList = new JSONDeserializer<ArrayList<Episode>>().use(null, ArrayList.class)
                        .use("values", Episode.class)
                        .use("values.show", Show.class)
                        .deserialize(episodes);

                try{
                    long unixTime = System.currentTimeMillis() / 1000L;
                    for(Episode episode : episodeList){
                        LogHelper.Log(episode.getPlannedFrom() + "<" + unixTime + ";" + episode.getPlannedTo() + ">=" + unixTime + ", " + episode.getShow().getName());
                        if(episode.getPlannedFrom() < unixTime && episode.getPlannedTo() >= unixTime){
                            SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm");
                            String from = dateFormat.format(episode.getPlannedFromDate());
                            String to = dateFormat.format(episode.getPlannedToDate());
                            mListeningText.setText(from + " - " + to + ", " + episode.getShow().getName());
                        }

                    }


                }catch(NullPointerException e){
                    mListeningText.setText(getResources().getString(R.string.noEpisodeInfo));
                }
            }

            @Override
            public void onFailure(Throwable error, String content){
                mListeningText.setText(getResources().getString(R.string.noEpisodeInfo));
                LogHelper.Log("adásinfo onFailure: " + content + " Error:" + error);
            }


        });
    }

    private void queueNextRefresh(long delay) {
        if (!paused) {
            Message msg = mHandler.obtainMessage(REFRESH);
            mHandler.removeMessages(REFRESH);
            mHandler.sendMessageDelayed(msg, delay);
        }
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REFRESH:
                    long next = refreshNow();
                    queueNextRefresh(next);
                    break;

                case QUIT:
                    // This can be moved back to onCreate once the bug that prevents
                    // Dialogs from being started from onCreate/onResume is fixed.
                    new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.service_start_error_title)
                            .setMessage(R.string.service_start_error_msg)
                            .setPositiveButton(android.R.string.ok,
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int whichButton) {
                                            getActivity().finish();
                                        }
                                    })
                            .setCancelable(false)
                            .show();
                    break;

                default:
                    break;
            }
        }
    };

    private boolean processUri(String input) {
        LogHelper.Log("MediaPlayerActivity; processUri run", 1);
        Uri uri = TransportFactory.getUri(input);

        if (uri == null) {
            return false;
        }

        UriBean uriBean = TransportFactory.findUri(mStreamdb, uri);
        if (uriBean == null) {
            uriBean = TransportFactory.getTransport(uri.getScheme()).createUri(uri);

            AbsTransport transport = TransportFactory.getTransport(uriBean.getProtocol());
            transport.setUri(uriBean);

        }

        //showDialog(LOADING_DIALOG);
        mDetermineActionTask = new DetermineActionTask(getActivity(), uriBean, this);
        mDetermineActionTask.execute();

        return true;
    }

    private long refreshNow() {
        if(mService == null)
            return 500;
        try {
            long pos = mPosOverride < 0 ? mService.position() : mPosOverride;
            if ((pos >= 0)) {
                mCurrentTime.setText(MusicUtils.makeTimeString(getActivity(), pos / 1000));
                if (mDuration > 0) {
                    mProgress.setProgress((int) (1000 * pos / mDuration));
                } else {
                    mProgress.setProgress(1000);
                }

                if (mService.isPlaying()) {
                    mCurrentTime.setVisibility(View.VISIBLE);
                } else {
                    // blink the counter
                    int vis = mCurrentTime.getVisibility();
                    mCurrentTime.setVisibility(vis == View.INVISIBLE ? View.VISIBLE : View.INVISIBLE);
                    return 500;
                }
            } else {
                mCurrentTime.setText("--:--");
                mProgress.setProgress(1000);
            }
            // calculate the number of milliseconds until the next full second, so
            // the counter can be updated at just the right time
            long remaining = 1000 - (pos % 1000);

            // approximate how often we would need to refresh the slider to
            // move it smoothly
            int width = mProgress.getWidth();
            if (width == 0) width = 320;
            long smoothrefreshtime = mDuration / width;

            if (smoothrefreshtime > remaining) return remaining;
            if (smoothrefreshtime < 20) return 20;
            return smoothrefreshtime;
        } catch (RemoteException ex) {
        }
        return 500;
    }


    private ServiceConnection osc = new ServiceConnection() {
        public void onServiceConnected(ComponentName classname, IBinder obj) {
            LogHelper.Log("MediaPlayerFragment; onServiceConnected running", 1);
            mService = IMediaPlaybackService.Stub.asInterface(obj);
            startPlayback();
            try {
                // Assume something is playing when the service says it is,
                // but also if the audio ID is valid but the service is paused.
                if (mService.getAudioId() >= 0 || mService.isPlaying() ||
                        mService.getPath() != null) {
                    // something is playing now, we're done
                    setPauseButtonImage();
                    return;
                }
            } catch (RemoteException ex) {
            }
            // Service is dead or not playing anything. Return to the previous
            // activity.
        }
        public void onServiceDisconnected(ComponentName classname) {
            LogHelper.Log("MediaPlayerFragment; onServiceDisconnected running", 1);
            mService = null;
        }
    };


    private View.OnClickListener mPauseListener = new View.OnClickListener() {
        public void onClick(View v) {
            doPauseResume();
        }
    };



    private View.OnClickListener mPrevListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (mService == null) return;
            try {
                mService.prev();
            } catch (RemoteException ex) {
            }
        }
    };

    private View.OnClickListener mNextListener = new View.OnClickListener() {
        public void onClick(View v) {
            if (mService == null) return;
            try {
                mService.next();
            } catch (RemoteException ex) {
            }
        }
    };

    private SeekBar.OnSeekBarChangeListener mSeekListener = new SeekBar.OnSeekBarChangeListener() {
        public void onStartTrackingTouch(SeekBar bar) {
            mLastSeekEventTime = 0;
            mFromTouch = true;
        }
        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
            if (!fromuser || (mService == null)) return;
            long now = SystemClock.elapsedRealtime();
            if ((now - mLastSeekEventTime) > 250) {
                mLastSeekEventTime = now;
                mPosOverride = mDuration * progress / 1000;
                try {
                    mService.seek(mPosOverride);
                } catch (RemoteException ex) {
                }

                // trackball event, allow progress updates
                if (!mFromTouch) {
                    refreshNow();
                    mPosOverride = -1;
                }
            }
        }
        public void onStopTrackingTouch(SeekBar bar) {
            mPosOverride = -1;
            mFromTouch = false;
        }
    };

    private void doPauseResume() {
        try {
            if(mService != null) {
                if (mService.isPlaying()) {
                    mService.pause();
                }else{
                    mService.play();
                }
                setPauseButtonImage();
            }
        } catch (RemoteException ex) {
        }
    }

    private void setPauseButtonImage() {
        try {
            if (mService != null && mService.isPlaying()) {
                mPauseButton.setImageResource(R.drawable.on);
            } else {
                mPauseButton.setImageResource(R.drawable.off);
            }
        } catch (RemoteException ex) {
        }
    }

    private void setSeekControls() {
        if (mService == null) {
            return;
        }

        try {
            if (mService.duration() > 0) {
                mProgress.setEnabled(true);
                //mPrevButton.setRepeatListener(mRewListener, 260);
                //mNextButton.setRepeatListener(mFfwdListener, 260);
            } else {
                mProgress.setEnabled(false);
                mPrevButton.setRepeatListener(null, -1);
                mNextButton.setRepeatListener(null, -1);
            }
        } catch (RemoteException e) {
        }
    }

    private BroadcastReceiver mStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(MediaPlaybackService.META_CHANGED)) {
                // redraw the artist/title info and
                // set new max for progress bar
                updateTrackInfo();
                setSeekControls();
                setPauseButtonImage();
            } else if (action.equals(MediaPlaybackService.PLAYSTATE_CHANGED)) {
                setPauseButtonImage();
            }
        }
    };


}
