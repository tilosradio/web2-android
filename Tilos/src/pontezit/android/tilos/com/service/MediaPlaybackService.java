package pontezit.android.tilos.com.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.database.Cursor;
import android.media.audiofx.AudioEffect;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.Random;
import java.util.Vector;

import pontezit.android.tilos.com.R;
import pontezit.android.tilos.com.activity.MediaPlayerActivity;
import pontezit.android.tilos.com.media.Metadata;
import pontezit.android.tilos.com.media.MetadataRetriever;
import pontezit.android.tilos.com.media.MultiPlayer;
import pontezit.android.tilos.com.media.MetadataRetrieverListener;
import pontezit.android.tilos.com.media.MultiPlayer.MultiPlayerListener;
import pontezit.android.tilos.com.provider.Media;
import pontezit.android.tilos.com.receiver.ConnectivityReceiver;
import pontezit.android.tilos.com.receiver.MediaButtonIntentReceiver;
import pontezit.android.tilos.com.service.RemoteControlClientCompat.MetadataEditorCompat;
import pontezit.android.tilos.com.utils.ArchiveUrl;
import pontezit.android.tilos.com.utils.DetermineActionTask;
import pontezit.android.tilos.com.utils.Finals;
import pontezit.android.tilos.com.utils.HTTPTransport;
import pontezit.android.tilos.com.utils.LogHelper;
import pontezit.android.tilos.com.utils.PreferenceConstants;
import pontezit.android.tilos.com.utils.Utils;

/**
 * Provides "background" audio playback capabilities, allowing the
 * user to switch between activities without stopping playback.
 */
public class MediaPlaybackService extends Service implements 
		OnSharedPreferenceChangeListener,
		MetadataRetrieverListener,
		MultiPlayerListener,
		DetermineActionTask.MusicRetrieverPreparedListener {

    /** used to specify whether enqueue() should start playing
     * the new list of files right away, next or once all the currently
     * queued files have been played
     */
    public static final int NOW = 1;
    public static final int NEXT = 2;
    public static final int LAST = 3;
    public static final int PLAYBACKSERVICE_STATUS = 2;

    public static final String PLAYSTATE_CHANGED = "pontezit.android.tilos.com.playstatechanged";
    public static final String META_CHANGED = "pontezit.android.tilos.com.metachanged";
    public static final String META_RETRIEVED = "pontezit.android.tilos.com.meta_retrieved";
    public static final String ART_CHANGED = "pontezit.android.tilos.com.artchanged";
    public static final String QUEUE_CHANGED = "pontezit.android.tilos.com.queuechanged";
    private static final String AVRCP_PLAYSTATE_CHANGED = "com.android.music.playstatechanged";
    private static final String AVRCP_META_CHANGED = "com.android.music.metachanged";

    public static final String PLAYBACK_STARTED = "pontezit.android.tilos.com.playbackstarted";
    public static final String PLAYBACK_COMPLETE = "pontezit.android.tilos.com.playbackcomplete";
    public static final String START_DIALOG = "pontezit.android.tilos.com.startdialog";
    public static final String STOP_DIALOG = "pontezit.android.tilos.com.stopdialog";
    public static final String PLAYER_CLOSED = "pontezit.android.tilos.com.playerclosed";

    public static final String SERVICECMD = "pontezit.android.tilos.com.musicservicecommand";
    public static final String CMDNAME = "command";
    public static final String CMDTOGGLEPAUSE = "togglepause";
    public static final String CMDSTOP = "stop";
    public static final String CMDPAUSE = "pause";
    public static final String CMDPLAY = "play";
    public static final String CMDPREVIOUS = "previous";
    public static final String CMDNEXT = "next";
    public static final String CMDNOTIF = "buttonId";

    public static final String TOGGLEPAUSE_ACTION = "pontezit.android.tilos.com.musicservicecommand.togglepause";
    public static final String PAUSE_ACTION = "pontezit.android.tilos.com.musicservicecommand.pause";
    public static final String PREVIOUS_ACTION = "pontezit.android.tilos.com.musicservicecommand.previous";
    public static final String NEXT_ACTION = "pontezit.android.tilos.com.musicservicecommand.next";

    public static final String BLUETOOTH_DEVICE_PAIRED = "pontezit.android.tilos.com.musicservicecommand.bluetooth_device_paired";

    public static final int SLEEP_TIMER_OFF = 0;

    public static final int TRACK_ENDED = 1;
    public static final int SERVER_DIED = 3;
    private static final int FOCUSCHANGE = 4;
    private static final int FADEDOWN = 5;
    private static final int FADEUP = 6;
    public static final int TRACK_WENT_TO_NEXT = 7;
    private static final int MAX_HISTORY_SIZE = 100;

    private MultiPlayer mPlayer;
    private String mFileToPlay;
    private String mPath;
    private long [] mPlayList = null;
    private int mPlayListLen = 0;
    private Vector<Integer> mHistory = new Vector<Integer>(MAX_HISTORY_SIZE);
    private Cursor mCursor;
    private int mPlayPos = -1;
    private int mNextPlayPos = -1;
    private static final String LOGTAG = "MediaPlaybackService";
    private final Shuffler mRand = new Shuffler();
    private int mOpenFailedCounter = 0;
    String[] mCursorCols = new String[] {
            Media.MediaColumns._ID,             // index must match IDCOLIDX below
            Media.MediaColumns.URI,
            Media.MediaColumns.TITLE,
            Media.MediaColumns.ALBUM,
            Media.MediaColumns.ARTIST,
            Media.MediaColumns.DURATION
    };
    private final static int IDCOLIDX = 0;
    private int mServiceStartId = -1;
    private boolean mServiceInUse = false;
    private boolean mIsSupposedToBePlaying = false;
    private boolean mQuietMode = false;
    private AudioManager mAudioManager;
    // used to track what type of audio focus loss caused the playback to pause
    private boolean mPausedByTransientLossOfFocus = false;
    private boolean mPausedByConnectivityReceiver = false;

    private SharedPreferences mPreferences;
    // We use this to distinguish between different cards when saving/restoring playlists.
    // This will have to change if we want to support multiple simultaneous cards.

    private AppWidgetOneProvider mAppWidgetProvider = AppWidgetOneProvider.getInstance();

    // interval after which we stop the service when idle
    private static final int IDLE_DELAY = 60000;

    // our RemoteControlClient object, which will use remote control APIs available in
    // SDK level >= 14, if they're available.
    private RemoteControlClientCompat mRemoteControlClientCompat;

    // The component name of MusicIntentReceiver, for use with media button and remote control APIs
    private ComponentName mMediaButtonReceiverComponent;

    private ConnectivityReceiver mConnectivityManager;
    private boolean mRetrieveShoutCastMetadata = false;
    private MetadataRetriever mMetadataRetriever;
    private Metadata metadata;
    private Handler mMediaplayerHandler = new Handler() {
        float mCurrentVolume = 1.0f;
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case FADEDOWN:
                    mCurrentVolume -= .05f;
                    if (mCurrentVolume > .2f) {
                        mMediaplayerHandler.sendEmptyMessageDelayed(FADEDOWN, 10);
                    } else {
                        mCurrentVolume = .2f;
                    }
                    mPlayer.setVolume(mCurrentVolume);
                    break;
                case FADEUP:
                    mCurrentVolume += .01f;
                    if (mCurrentVolume < 1.0f) {
                        mMediaplayerHandler.sendEmptyMessageDelayed(FADEUP, 10);
                    } else {
                        mCurrentVolume = 1.0f;
                    }
                    mPlayer.setVolume(mCurrentVolume);
                    break;
                case TRACK_WENT_TO_NEXT:
                    mPlayPos = mNextPlayPos;
                    if (mCursor != null) {
                        mCursor.close();
                        mCursor = null;
                    }
                    mCursor = getCursorForId(mPlayList[mPlayPos]);
                    notifyChange(META_CHANGED);
                    updateNotification(false);
                    setNextTrack(mFileToPlay);
                    break;

                case FOCUSCHANGE:
                    // This code is here so we can better synchronize it with the code that
                    // handles fade-in
                    switch (msg.arg1) {
                        case AudioManager.AUDIOFOCUS_LOSS:
                            Log.v(LOGTAG, "AudioFocus: received AUDIOFOCUS_LOSS");
                            if(isPlaying()) {
                                mPausedByTransientLossOfFocus = false;
                            }
                            pause(true);
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            mMediaplayerHandler.removeMessages(FADEUP);
                            mMediaplayerHandler.sendEmptyMessage(FADEDOWN);
                            break;
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                            Log.v(LOGTAG, "AudioFocus: received AUDIOFOCUS_LOSS_TRANSIENT");
                            if(isPlaying()) {
                                mPausedByTransientLossOfFocus = true;
                            }
                            pause(true);
                            break;
                        case AudioManager.AUDIOFOCUS_GAIN:
                            Log.v(LOGTAG, "AudioFocus: received AUDIOFOCUS_GAIN");
                            if(!isPlaying() && mPausedByTransientLossOfFocus) {
                                mPausedByTransientLossOfFocus = false;
                                mCurrentVolume = 0f;
                                mPlayer.setVolume(mCurrentVolume);
                                play(); // also queues a fade-in
                            } else {
                                mMediaplayerHandler.removeMessages(FADEDOWN);
                                mMediaplayerHandler.sendEmptyMessage(FADEUP);
                            }
                            break;
                        default:
                            Log.e(LOGTAG, "Unknown audio focus change code");
                    }
                    break;

                default:
                    break;
            }
        }
    };

    @Override
	public void onPrepared(MultiPlayer mp) {
        Intent i = new Intent("android.media.action.OPEN_AUDIO_EFFECT_CONTROL_SESSION");
        i.putExtra("android.media.extra.AUDIO_SESSION", getAudioSessionId());
        i.putExtra("android.media.extra.PACKAGE_NAME", getPackageName());
        sendBroadcast(i);
    	removeStickyBroadcast(new Intent(START_DIALOG));
        sendBroadcast(new Intent(STOP_DIALOG));
        mMetadataRetriever = new MetadataRetriever(this, getPath());
        mMetadataRetriever.getMetadata();
        play();
        notifyChange(META_CHANGED);
        notifyChange(PLAYBACK_STARTED);


	}

	@Override
	public void onCompletion(MultiPlayer mp) {
        gotoNext(false);

	}

	@Override
	public void onError(MultiPlayer mp, int what, int extra) {

        LogHelper.Log("onError; what: " + what + ", extra: " + extra);
		if (what == SERVER_DIED) {
			if (mIsSupposedToBePlaying) {
				gotoNext(true);
            } else {
            	// the server died when we were idle, so just
                // reopen the same song (it will start again
                // from the beginning though when the user
                // restarts)
                openCurrentAndNext();
            }
		} else {
			handleError();
		}
	}

	@Override
	public void onInfo(MultiPlayer mp, int what, int extra) {
    	notifyChange(META_CHANGED);
	}

	/* (non-Javadoc)
	 * @see android.content.SharedPreferences.OnSharedPreferenceChangeListener#onSharedPreferenceChanged(android.content.SharedPreferences, java.lang.String)
	 */
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
  	    if (key.equals(PreferenceConstants.WIFI_LOCK)) {
			if (sharedPreferences.getBoolean(PreferenceConstants.WIFI_LOCK, true)) {
				final boolean lockingWifi = mPreferences.getBoolean(PreferenceConstants.WIFI_LOCK, true);
				mConnectivityManager.setWantWifiLock(lockingWifi);
  	        }
  	    } else if (key.equals(PreferenceConstants.RETRIEVE_METADATA)) {

  	    	if (sharedPreferences.getBoolean(PreferenceConstants.RETRIEVE_METADATA, false) &&
  	    			mPlayList != null && mPlayList.length > 0) {
  	    		mMetadataRetriever = new MetadataRetriever(this, getPath());
  	    		mMetadataRetriever.getMetadata();
  	    	}
  	    }
  	}

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String cmd = intent.getStringExtra("command");
            if (CMDNEXT.equals(cmd) || NEXT_ACTION.equals(action)) {
                gotoNext(true);
            } else if (CMDPREVIOUS.equals(cmd) || PREVIOUS_ACTION.equals(action)) {
                prev();
            } else if (CMDTOGGLEPAUSE.equals(cmd) || TOGGLEPAUSE_ACTION.equals(action)) {
            	if (intent.getBooleanExtra("from_connectivity_receiver", false) && !mPausedByConnectivityReceiver) {
            		return;
            	}

                if (isPlaying()) {
                    pause(true);
                    mPausedByTransientLossOfFocus = false;
                } else {
                    play();
                }
            } else if (CMDPAUSE.equals(cmd) || PAUSE_ACTION.equals(action)) {
            	boolean wasPlaying = mIsSupposedToBePlaying;

                pause(true);
                mPausedByTransientLossOfFocus = false;

                if (wasPlaying != mIsSupposedToBePlaying) {
                	mPausedByConnectivityReceiver = intent.getBooleanExtra("from_connectivity_receiver", false);
                }
            } else if (CMDPLAY.equals(cmd)) {
                play();
            } else if (CMDSTOP.equals(cmd)) {
                pause(true);
                mPausedByTransientLossOfFocus = false;
                seek(0);
            } else if (AppWidgetOneProvider.CMDAPPWIDGETUPDATE.equals(cmd)) {
                // Someone asked us to refresh a set of specific widgets, probably
                // because they were just added.
                int[] appWidgetIds = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS);
                //TODO Fix this!
                mAppWidgetProvider.performUpdate(MediaPlaybackService.this, appWidgetIds, "");
            }
        }
    };

    private OnAudioFocusChangeListener mAudioFocusListener = new OnAudioFocusChangeListener() {
        public void onAudioFocusChange(int focusChange) {
            mMediaplayerHandler.obtainMessage(FOCUSCHANGE, focusChange, 0).sendToTarget();
        }
    };

    public MediaPlaybackService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();

        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mMediaButtonReceiverComponent = new ComponentName(this, MediaButtonIntentReceiver.class);
        mAudioManager.registerMediaButtonEventReceiver(mMediaButtonReceiverComponent);

        // Use the remote control APIs (if available) to set the playback state
        if (mRemoteControlClientCompat == null) {
            Intent intent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            intent.setComponent(mMediaButtonReceiverComponent);
            mRemoteControlClientCompat = new RemoteControlClientCompat(
                    PendingIntent.getBroadcast(this /*context*/,
                            0 /*requestCode, ignored*/, intent /*intent*/, 0 /*flags*/));
            RemoteControlHelper.registerRemoteControlClient(mAudioManager,
                    mRemoteControlClientCompat);
        }

        mRemoteControlClientCompat.setTransportControlFlags(
        		RemoteControlClientCompat.FLAG_KEY_MEDIA_PREVIOUS |
        		RemoteControlClientCompat.FLAG_KEY_MEDIA_PLAY |
        		RemoteControlClientCompat.FLAG_KEY_MEDIA_PAUSE |
        		RemoteControlClientCompat.FLAG_KEY_MEDIA_NEXT |
        		RemoteControlClientCompat.FLAG_KEY_MEDIA_STOP);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mPreferences.registerOnSharedPreferenceChangeListener(this);

		final boolean lockingWifi = mPreferences.getBoolean(PreferenceConstants.WIFI_LOCK, true);
		mConnectivityManager = new ConnectivityReceiver(this, lockingWifi);

		mRetrieveShoutCastMetadata = mPreferences.getBoolean(PreferenceConstants.RETRIEVE_SHOUTCAST_METADATA, false);

        mMediaButtonReceiverComponent = new ComponentName(this, MediaButtonIntentReceiver.class);

        // Needs to be done in this thread, since otherwise ApplicationContext.getPowerManager() crashes.
        mPlayer = new MultiPlayer(this);

        reloadSettings();
        notifyChange(QUEUE_CHANGED);
        //notifyChange(META_CHANGED);

        IntentFilter commandFilter = new IntentFilter();
        commandFilter.addAction(SERVICECMD);
        commandFilter.addAction(TOGGLEPAUSE_ACTION);
        commandFilter.addAction(PAUSE_ACTION);
        commandFilter.addAction(NEXT_ACTION);
        commandFilter.addAction(PREVIOUS_ACTION);
        registerReceiver(mIntentReceiver, commandFilter);

        // If the service was idle, but got killed before it stopped itself, the
        // system will relaunch it. Make sure it gets stopped again in that case.
        Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
    }

    @Override
    public void onDestroy() {
        // Check that we're not being destroyed while something is still playing.
        if (isPlaying()) {
            Log.e(LOGTAG, "Service being destroyed while still playing.");
        }

        mAppWidgetProvider.notifyChange(this, PLAYER_CLOSED);

        mConnectivityManager.cleanup();

		if (mMetadataRetriever != null && !mMetadataRetriever.finished) {
	    	mMetadataRetriever.stopRetriever();
	    	mMetadataRetriever = null;
	    }

        // release all MediaPlayer resources, including the native player and wakelocks
        Intent i = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, getAudioSessionId());
        i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName());
        sendBroadcast(i);
        mPlayer.release();
        mPlayer = null;

        mAudioManager.abandonAudioFocus(mAudioFocusListener);
        RemoteControlHelper.unregisterRemoteControlClient(mAudioManager,
                mRemoteControlClientCompat);

        // make sure there aren't any other messages coming
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mMediaplayerHandler.removeCallbacksAndMessages(null);

        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }

        unregisterReceiver(mIntentReceiver);

    	Utils.deleteAllFiles();

        super.onDestroy();
    }

    private void saveSettings() {
        Editor ed = mPreferences.edit();
        //ed.putInt("repeatmode", mRepeatMode);
        //ed.putInt("shufflemode", mShuffleMode);
        ed.commit();
    }

    private void reloadSettings() {

    }

    @Override
    public IBinder onBind(Intent intent) {
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mServiceInUse = true;
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mServiceInUse = true;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mServiceStartId = startId;
        mDelayedStopHandler.removeCallbacksAndMessages(null);

        if (intent != null) {
            String action = intent.getAction();
            String cmd = intent.getStringExtra("command");

            if (CMDNEXT.equals(cmd) || NEXT_ACTION.equals(action)) {
                gotoNext(true);
            } else if (CMDPREVIOUS.equals(cmd) || PREVIOUS_ACTION.equals(action)) {
                if (position() < 2000) {
                    prev();
                } else {
                    seek(0);
                    play();
                }
            } else if (CMDTOGGLEPAUSE.equals(cmd) || TOGGLEPAUSE_ACTION.equals(action)) {
                boolean remove_status_icon =  (intent.getIntExtra(CMDNOTIF, 0) != 2);
                if (isPlaying()) {
                    pause(remove_status_icon);
                    mPausedByTransientLossOfFocus = false;
                } else {
                    play();
                }

                if (!remove_status_icon) {
                	updateNotification(true);
                }
            } else if (CMDPAUSE.equals(cmd) || PAUSE_ACTION.equals(action)) {
                pause(true);
                mPausedByTransientLossOfFocus = false;
            } else if (CMDPLAY.equals(cmd)) {
                play();
            } else if (CMDSTOP.equals(cmd)) {
                pause(true);
                mPausedByTransientLossOfFocus = false;
                seek(0);
            } else if (BLUETOOTH_DEVICE_PAIRED.equals(action)) {
        		Uri uri = HTTPTransport.getUri(intent.getStringExtra("uri"));

        		if (uri != null) {

        			HTTPTransport transport = new HTTPTransport();
        			transport.setUri(uri);

        			new DetermineActionTask(this, uri.toString(), this).execute();
        		}
            }
        }

        // make sure the service will shut down on its own if it was
        // just started but not bound to and nothing is playing
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
        return START_STICKY;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mServiceInUse = false;

        // Take a snapshot of the current playlist
        saveSettings();

        if (isPlaying() || mPausedByTransientLossOfFocus) {
            // something is currently playing, or will be playing once
            // an in-progress action requesting audio focus ends, so don't stop the service now.
            return true;
        }

        // If there is a playlist but playback is paused, then wait a while
        // before stopping the service, so that pause/resume isn't slow.
        // Also delay stopping the service if we're transitioning between tracks.
        if (mPlayListLen > 0  || mMediaplayerHandler.hasMessages(TRACK_ENDED)) {
            Message msg = mDelayedStopHandler.obtainMessage();
            mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
            return true;
        }

        // No active playlist, OK to stop the service right now
        stopSelf(mServiceStartId);
        return true;
    }

    private Handler mDelayedStopHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // Check again to make sure nothing is playing right now
            if (isPlaying() || mPausedByTransientLossOfFocus || mServiceInUse
                    || mMediaplayerHandler.hasMessages(TRACK_ENDED)) {
                return;
            }
            // save the queue again, because it might have changed
            // since the user exited the music app (because of
            // party-shuffle or because the play-position changed)
            stopSelf(mServiceStartId);
        }
    };

    /**
     * Notify the change-receivers that something has changed.
     * The intent that is sent contains the following data
     * for the currently playing track:
     * "id" - Integer: the database row ID
     * "artist" - String: the name of the artist
     * "album" - String: the name of the album
     * "track" - String: the name of the track
     * The intent has an action that is one of
     * "com.android.music.metachanged"
     * "com.android.music.queuechanged",
     * "com.android.music.playbackcomplete"
     * "com.android.music.playstatechanged"
     * respectively indicating that a new track has
     * started playing, that the playback queue has
     * changed, that playback has stopped because
     * the last file in the list has been played,
     * or that the play-state changed (paused/resumed).
     */
    private void notifyChange(String what) {

        Intent i = new Intent(what);
        i.putExtra("id", Long.valueOf(getAudioId()));
        i.putExtra("streamType", getStreamType());
        i.putExtra("readableTime",getReadableTime());
        i.putExtra("showName", getShowName());
        i.putExtra("playing", isPlaying());
        sendStickyBroadcast(i);


        if (what.equals(PLAYSTATE_CHANGED)) {
            mRemoteControlClientCompat.setPlaybackState(isPlaying() ?
            		RemoteControlClientCompat.PLAYSTATE_PLAYING : RemoteControlClientCompat.PLAYSTATE_PAUSED);

        } else if (what.equals(META_CHANGED)) {
            // Update the remote controls
            MetadataEditorCompat metadataEditor = mRemoteControlClientCompat.editMetadata(true);
            metadataEditor.putString(2, getShowName());
            metadataEditor.putString(1, getStreamType());
            metadataEditor.putString(7, getReadableTime());

            metadataEditor.apply();
        }

        // Share this notification directly with our widgets
        mAppWidgetProvider.notifyChange(this, what);
    }




    /**
     * Returns the current play list
     * @return An array of integers containing the IDs of the tracks in the play list
     */
    public long [] getQueue() {
        synchronized (this) {
            int len = mPlayListLen;
            long [] list = new long[len];
            for (int i = 0; i < len; i++) {
                list[i] = mPlayList[i];
            }
            return list;
        }
    }

    private Cursor getCursorForId(long lid) {
        String id = String.valueOf(lid);

        Cursor c = getContentResolver().query(
                Media.MediaColumns.CONTENT_URI,
                mCursorCols, "_id=" + id , null, null);
        if (c != null) {
            c.moveToFirst();
        }
        return c;
    }

    private void openCurrentAndNext() {
        synchronized (this) {
            LogHelper.Log("MediaPlaybackService openCurrentAndNext; mFileToPlay" + mFileToPlay, 1);
            String nextTrack = ArchiveUrl.getNextUrl(mFileToPlay);
            openStream(nextTrack);
            setNextTrack(nextTrack);
        }
    }

    private void setNextTrack(String currentTrack) {
        mNextPlayPos = getNextPosition(false);
        mPlayer.setNextDataSource(ArchiveUrl.getNextUrl(currentTrack));
    }


    /**
     * Opens the specified file and readies it for playback.
     *
     * @param path The full path of the file to be opened.
     */
    public void openStream(String path) {
        LogHelper.Log("MediaPlaybackService run; path: " + path, 1);
        synchronized (this) {
            if (path == null) {
                return;
            }

            mFileToPlay = path;

            Log.i(LOGTAG, "Opening: " + mFileToPlay);

            sendStickyBroadcast(new Intent(START_DIALOG));
            boolean useFFmpegPlayer = mPreferences.getBoolean(PreferenceConstants.USE_FFMPEG_PLAYER, false);

            mPlayer.setDataSource(mFileToPlay, useFFmpegPlayer);

        }
    }

    /**
     * Starts playback of a previously opened file.
     */
    public void play() {
        mAudioManager.requestAudioFocus(mAudioFocusListener, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
        MediaButtonHelper.registerMediaButtonEventReceiverCompat(
                mAudioManager, mMediaButtonReceiverComponent);

        if (mPlayer.isInitialized()) {


            mPlayer.start();
            // make sure we fade in, in case a previous fadein was stopped because
            // of another focus loss
            mMediaplayerHandler.removeMessages(FADEDOWN);
            mMediaplayerHandler.sendEmptyMessage(FADEUP);

            updateNotification(false);
            if (!mIsSupposedToBePlaying) {
                mIsSupposedToBePlaying = true;
                notifyChange(PLAYSTATE_CHANGED);
            }

        } else {
        	openCurrentAndNext();
        }
    }

    private void updateNotification(boolean updateNotification) {
        String streamType = getStreamType();
    	if (streamType == null || streamType.equals(Media.UNKNOWN_STRING)) {
            streamType = getResources().getString(R.string.live_stream);
    	}

        String readableTime = getReadableTime();
    	if (readableTime == null || readableTime.equals(Media.UNKNOWN_STRING)) {
            readableTime = getResources().getString(R.string.no_time);
    	}

    	String showName = getShowName();
        if (showName == null || showName.equals(Media.UNKNOWN_STRING)) {
            showName = getResources().getString(R.string.no_show);
        }

        PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, MediaPlayerActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP), 0);

        NotificationCompat.Builder status = new NotificationCompat.Builder(this)
                                                                  .setContentTitle(showName)
                                                                  .setContentText(streamType + " - " + readableTime)
                                                                  .setContentIntent(contentIntent)
                                                                  .setWhen(0);

        int trackId = getTrackId();

        status.setSmallIcon(R.drawable.notification_icon);


	    Notification notification = status.build();

	    // If the user has a phone running Android 4.0+ show an expanded notification
	    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN){
	    	notification = buildExpandedView(notification, updateNotification);
	    }

		if (!updateNotification) {
    		startForeground(PLAYBACKSERVICE_STATUS, notification);
    	} else {
    		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    		notificationManager.notify(PLAYBACKSERVICE_STATUS, notification);
    	}
    }

    @SuppressLint("NewApi")
	private Notification buildExpandedView(Notification notification, boolean updateNotification) {
    	RemoteViews contentView = new RemoteViews(getPackageName(), R.layout.notification_small);
    	RemoteViews expandedContentView = new RemoteViews(getPackageName(), R.layout.notification_expanded);
    	setupContentView(contentView, updateNotification);
    	setupExpandedContentView(expandedContentView, updateNotification);

        notification.contentView = contentView;
        notification.bigContentView = expandedContentView;
        return notification;
    }

    private void setupContentView(RemoteViews rv, boolean updateNotification) {


        String streamType = getStreamType();
        if (streamType == null || streamType.equals(Media.UNKNOWN_STRING)) {
            streamType = getResources().getString(R.string.live_stream);
        }

        String readableTime = getReadableTime();
        if (readableTime == null || readableTime.equals(Media.UNKNOWN_STRING)) {
            readableTime = getResources().getString(R.string.no_time);
        }

        String showName = getShowName();
        if (showName == null || showName.equals(Media.UNKNOWN_STRING)) {
            showName = getResources().getString(R.string.no_show);
        }

        if (updateNotification) {
        	if (isPlaying()) {
        		rv.setImageViewResource(R.id.play_pause, android.R.drawable.ic_media_pause);
        	} else {
        		rv.setImageViewResource(R.id.play_pause, android.R.drawable.ic_media_play);
        	}
        } else {
        	rv.setImageViewResource(R.id.play_pause, android.R.drawable.ic_media_pause);
        }

        int trackId = getTrackId();

        if(getPath() == Finals.getLiveHiUrl()){
            rv.setViewVisibility(R.id.next, View.GONE);
        }else{
            rv.setViewVisibility(R.id.next, View.VISIBLE);
        }
    	// set the text for the notifications
    	rv.setTextViewText(R.id.title, showName);
    	rv.setTextViewText(R.id.subtitle, streamType + " - " + readableTime);

    	rv.setOnClickPendingIntent(R.id.play_pause, createPendingIntent(2, CMDTOGGLEPAUSE));
    	rv.setOnClickPendingIntent(R.id.next, createPendingIntent(3, CMDNEXT));
    	rv.setOnClickPendingIntent(R.id.close, createPendingIntent(4, CMDSTOP));
    }

    private void setupExpandedContentView(RemoteViews rv, boolean updateNotification) {
        String streamType = getStreamType();
        if (streamType == null || streamType.equals(Media.UNKNOWN_STRING)) {
            streamType = getResources().getString(R.string.live_stream);
        }

        String readableTime = getReadableTime();
        if (readableTime == null || readableTime.equals(Media.UNKNOWN_STRING)) {
            readableTime = getResources().getString(R.string.no_time);
        }

        String showName = getShowName();
        if (showName == null || showName.equals(Media.UNKNOWN_STRING)) {
            showName = getResources().getString(R.string.no_show);
        }

        if (updateNotification) {
        	if (isPlaying()) {
        		rv.setImageViewResource(R.id.play_pause, android.R.drawable.ic_media_pause);
        	} else {
        		rv.setImageViewResource(R.id.play_pause, android.R.drawable.ic_media_play);
        	}
        } else {
        	rv.setImageViewResource(R.id.play_pause, android.R.drawable.ic_media_pause);
        }

        if(getPath() == Finals.getLiveHiUrl()){
            rv.setViewVisibility(R.id.next, View.GONE);
        }else{
            rv.setViewVisibility(R.id.next, View.VISIBLE);
        }

        int trackId = getTrackId();


    	// set the text for the notifications
    	rv.setTextViewText(R.id.firstLine, showName);
    	rv.setTextViewText(R.id.secondLine, readableTime);
    	rv.setTextViewText(R.id.thirdLine, streamType);

    	rv.setOnClickPendingIntent(R.id.prev, createPendingIntent(1, CMDPREVIOUS));
    	rv.setOnClickPendingIntent(R.id.play_pause, createPendingIntent(2, CMDTOGGLEPAUSE));
    	rv.setOnClickPendingIntent(R.id.next, createPendingIntent(3, CMDNEXT));
    	rv.setOnClickPendingIntent(R.id.close, createPendingIntent(4, CMDSTOP));
    }

    private PendingIntent createPendingIntent(int requestCode, String command) {
        Intent intent = new Intent(this, MediaPlaybackService.class);
        intent.setAction(MediaPlaybackService.SERVICECMD);
        intent.putExtra(MediaPlaybackService.CMDNOTIF, requestCode);
        intent.putExtra(MediaPlaybackService.CMDNAME, command);
		return PendingIntent.getService(this, requestCode, intent, 0);
    }

    private void stop(boolean remove_status_icon) {
        if (mPlayer.isInitialized()) {
            mPlayer.stop();
        }

        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }
        if (remove_status_icon) {
            gotoIdleState();
        } else {
            stopForeground(false);
        }
        if (remove_status_icon) {
            mIsSupposedToBePlaying = false;
        }
    }

    /**
     * Stops playback.
     */
    public void stop() {
        stop(true);
    }

    /**
     * Pauses playback (call play() to resume)
     */
    public void pause(boolean remove_status_icon) {
        synchronized(this) {
            mMediaplayerHandler.removeMessages(FADEUP);
            if (isPlaying()) {
                mPlayer.pause();
                if (remove_status_icon) {
                    gotoIdleState();
                } else {
                    stopForeground(false);
                }
                mIsSupposedToBePlaying = false;
                notifyChange(PLAYSTATE_CHANGED);
            }
            mPausedByConnectivityReceiver = false;
        }
    }

    /** Returns whether something is currently playing
     *
     * @return true if something is playing (or will be playing shortly, in case
     * we're currently transitioning between tracks), false if not.
     */
    public boolean isPlaying() {
        return mIsSupposedToBePlaying;
    }

    /*
      Desired behavior for prev/next/shuffle:

      - NEXT will move to the next track in the list when not shuffling, and to
        a track randomly picked from the not-yet-played tracks when shuffling.
        If all tracks have already been played, pick from the full set, but
        avoid picking the previously played track if possible.
      - when shuffling, PREV will go to the previously played track. Hitting PREV
        again will go to the track played before that, etc. When the start of the
        history has been reached, PREV is a no-op.
        When not shuffling, PREV will go to the sequentially previous track (the
        difference with the shuffle-case is mainly that when not shuffling, the
        user can back up to tracks that are not in the history).

        Example:
        When playing an album with 10 tracks from the start, and enabling shuffle
        while playing track 5, the remaining tracks (6-10) will be shuffled, e.g.
        the final play order might be 1-2-3-4-5-8-10-6-9-7.
        When hitting 'prev' 8 times while playing track 7 in this example, the
        user will go to tracks 9-6-10-8-5-4-3-2. If the user then hits 'next',
        a random track will be picked again. If at any time user disables shuffling
        the next/previous track will be picked in sequential order again.
     */

    public void prev() {
        synchronized (this) {
            stop(false);
            //FIXME:
            mFileToPlay = ArchiveUrl.getPrevUrl(ArchiveUrl.getPrevUrl(mFileToPlay));
            openCurrentAndNext();
            notifyChange(META_CHANGED);
        }
    }

    /**
     * Get the next position to play. Note that this may actually modify mPlayPos
     * if playback is in SHUFFLE_AUTO mode and the shuffle list window needed to
     * be adjusted. Either way, the return value is the next value that should be
     * assigned to mPlayPos;
     */
    private int getNextPosition(boolean force) {

        if (mPlayPos >= mPlayListLen - 1) {
            return -1;
        } else {
            return mPlayPos + 1;
        }

    }

    public void gotoNext(boolean force) {
        synchronized (this) {
            stop(false);
            openCurrentAndNext();
            notifyChange(META_CHANGED);
        }
    }

    private void gotoIdleState() {
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
        stopForeground(true);
    }

    // A simple variation of Random that makes sure that the
    // value it returns is not equal to the value it returned
    // previously, unless the interval is 1.
    private static class Shuffler {
        private int mPrevious;
        private Random mRandom = new Random();
        public int nextInt(int interval) {
            int ret;
            do {
                ret = mRandom.nextInt(interval);
            } while (ret == mPrevious && interval > 1);
            mPrevious = ret;
            return ret;
        }
    };

    /**
     * Returns the path of the currently playing file, or null if
     * no file is currently playing.
     */
    public String getPath() {
        return mFileToPlay;
    }

    /**
     * Returns the rowid of the currently playing file, or -1 if
     * no file is currently playing.
     */
    public long getAudioId() {
        synchronized (this) {
            if (mPlayPos >= 0 && mPlayer.isInitialized()) {
                return mPlayList[mPlayPos];
            }
        }
        return -1;
    }

    /**
     * Returns the position in the queue
     * @return the position in the queue
     */
    public int getQueuePosition() {
        synchronized(this) {
            return mPlayPos;
        }
    }

    /**
     * Starts playing the track at the given position in the queue.
     * @param pos The position in the queue of the track that will be played.
     */
    public void setQueuePosition(int pos) {
        synchronized(this) {
            stop(false);
            mPlayPos = pos;
            openCurrentAndNext();
            notifyChange(META_CHANGED);
        }
    }


    public String getShowName() {
        synchronized(this) {
            if (metadata == null) {
                return null;
            }
            return metadata.getShowName();
        }
    }


    public String getReadableTime() {
        synchronized (this) {
            if (metadata == null) {
                return null;
            }
            return metadata.getReadableTime();
        }
    }


    public String getStreamType() {
        synchronized (this) {
            if (metadata == null) {
                return null;
            }

            if(metadata.isLive())
                return getResources().getString(R.string.live_stream);
            else
                return getResources().getString(R.string.archive);

        }
    }


    /**
     * Returns the duration of the file in milliseconds.
     * Currently this method returns -1 for the duration of MIDI files.
     */
    public long duration() {
        if (mPlayer.isInitialized()) {
            return mPlayer.duration();
        }
        return -1;
    }

    /**
     * Returns the current playback position in milliseconds
     */
    public long position() {
        if (mPlayer.isInitialized()) {
            return mPlayer.position();
        }
        return -1;
    }

    /**
     * Seeks to the position specified.
     *
     * @param pos The position to seek to, in milliseconds
     */
    public long seek(long pos) {
        if (mPlayer.isInitialized()) {
            if (pos < 0) pos = 0;
            if (pos > mPlayer.duration()) pos = mPlayer.duration();
            return mPlayer.seek(pos);
        }
        return -1;
    }

    /**
     * Sets the audio session ID.
     *
     * @param sessionId: the audio session ID.
     */
    public void setAudioSessionId(int sessionId) {
        synchronized (this) {
            mPlayer.setAudioSessionId(sessionId);
        }
    }

    /**
     * Returns the audio session ID.
     */
    public int getAudioSessionId() {
        synchronized (this) {
            return mPlayer.getAudioSessionId();
        }
    }

    /*
     * By making this a static class with a WeakReference to the Service, we
     * ensure that the Service can be GCd even when the system process still
     * has a remote reference to the stub.
     */
    static class ServiceStub extends IMediaPlaybackService.Stub {
        WeakReference<MediaPlaybackService> mService;

        ServiceStub(MediaPlaybackService service) {
            mService = new WeakReference<MediaPlaybackService>(service);
        }

        public void openStream(String path)
        {
            mService.get().openStream(path);
        }
        public int getQueuePosition() {
            return mService.get().getQueuePosition();
        }
        public void setQueuePosition(int index) {
            mService.get().setQueuePosition(index);
        }
        public boolean isPlaying() {
            return mService.get().isPlaying();
        }
        public void stop() {
            mService.get().stop();
        }
        public void pause() {
            mService.get().pause(true);
        }
        public void play() {
            mService.get().play();
        }
        public void prev() {
            mService.get().prev();
        }
        public void next() {
            mService.get().gotoNext(true);
        }
        public String getShowName() {
            return mService.get().getShowName();
        }
        public String getReadableTime() {
            return mService.get().getReadableTime();
        }
        public String getStreamType() {
            return mService.get().getStreamType();
        }
        public long [] getQueue() {
            return mService.get().getQueue();
        }
        public String getPath() {
            return mService.get().getPath();
        }
        public long position() {
            return mService.get().position();
        }
        public long duration() {
            return mService.get().duration();
        }
        public long seek(long pos) {
            return mService.get().seek(pos);
        }
        public int getAudioSessionId() {
            return mService.get().getAudioSessionId();
        }
        // new
        public String getTrackNumber() {
        	return mService.get().getTrackNumber();
        }
        public int getTrackId() {
        	return mService.get().getTrackId();
        }
        public String getMediaUri() {
        	return mService.get().getMediaUri();
        }
        public void setSleepTimerMode(int sleepmode) {
        	mService.get().setSleepTimerMode(sleepmode);
        }
        public int getSleepTimerMode() {
        	return mService.get().getSleepTimerMode();
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.println("" + mPlayListLen + " items in queue, currently at index " + mPlayPos);
        writer.println("Currently loaded:");
        writer.println(getStreamType());
        writer.println(getShowName());
        writer.println(getReadableTime());
        writer.println(getPath());
        writer.println("playing: " + mIsSupposedToBePlaying);
        // TODO fix this!
        //writer.println("actual: " + mPlayer.mCurrentMediaPlayer.isPlaying());
    }

    private final IBinder mBinder = new ServiceStub(this);

    public String getTrackNumber() {
    	synchronized (this) {
    		return ((mPlayPos + 1) + " / " + mPlayListLen);
    	}
    }

    public int getTrackId() {
    	synchronized(this) {
            if (mCursor == null) {
                return -1;
            }
            return mCursor.getInt(mCursor.getColumnIndexOrThrow(Media.MediaColumns._ID));
        }
    }

    public String getMediaUri() {
        synchronized(this) {
            if (mCursor == null) {
                return null;
            }
            return mCursor.getString(mCursor.getColumnIndexOrThrow(Media.MediaColumns.URI));
        }
    }

    private int mSleepTimerMode = SLEEP_TIMER_OFF;

    private Handler mSleepTimerHandler = new Handler() {
    	@Override
    	public void handleMessage(Message msg) {
    		Log.v(LOGTAG, "mSleepTimerHandler called");
    		stop();
    	}
    };

    public void setSleepTimerMode(int minutes) {
    	synchronized(this) {
    		mSleepTimerHandler.removeCallbacksAndMessages(null);

    		if (minutes != SLEEP_TIMER_OFF) {
    			Message msg = mSleepTimerHandler.obtainMessage();
    			mSleepTimerHandler.sendMessageDelayed(msg, minutes * 60000);
    		}

    		mSleepTimerMode = minutes;
    	}
    }

    public int getSleepTimerMode() {
    	return mSleepTimerMode;
    }

    @Override
	public synchronized void onMetadataParsed(Metadata metadata) {
        LogHelper.Log("MediaPlayBackService, onMetadataParsed run. getShowName = "+ metadata.getShowName(), 1);
        this.metadata = metadata;
    	notifyChange(META_RETRIEVED);
        notifyChange(META_CHANGED);
        //MusicUtils.clearNotificationArtCache();
        updateNotification(true);

    }

    private Handler mDelayedPlaybackHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            gotoNext(false);
            notifyChange(META_CHANGED);
        }
    };

	private void handleError() {
    	if (!mPlayer.isInitialized()) {
            Intent i = new Intent(STOP_DIALOG);
            sendBroadcast(i);

            stop(true);
            mOpenFailedCounter++;

            if (mPlayListLen > 1) {
            	if (mOpenFailedCounter == mPlayListLen) {
            		mOpenFailedCounter = 0;
            	} else {
            		mDelayedPlaybackHandler.sendEmptyMessageDelayed(0, 2500);
            	}
            }

            if (!mQuietMode) {
            	Toast.makeText(this, R.string.playback_failed, Toast.LENGTH_SHORT).show();
            }

            Log.d(LOGTAG, "Failed to open file for playback");
        } else {
        	mOpenFailedCounter = 0;
        }
    }

	@Override
	public void onMusicRetrieverPrepared(String action, String path) {
		if (action.equals(DetermineActionTask.URL_ACTION_PLAY)) {
			openStream(path);
		}
	}
}
