package pontezit.android.tilos.com.media;

import android.content.Context;
//import io.vov.vitamio.MediaPlayer;
import android.media.MediaPlayer;
import android.util.Log;

import java.io.IOException;

import pontezit.android.tilos.com.utils.HTTPTransport;
import pontezit.android.tilos.com.utils.HTTPRequestTask;
import pontezit.android.tilos.com.utils.LogHelper;
import pontezit.android.tilos.com.utils.URLUtils;
import pontezit.android.tilos.com.service.MediaPlaybackService;

/**
 * Provides a unified interface for dealing with media files.
 */
public final class MultiPlayer implements HTTPRequestTask.HTTPRequestListener {
	private static final String TAG = MultiPlayer.class.getName();
	
	private MultiPlayerListener mListener;
	
	private NativePlayer mNativeMediaPlayer;
	private DownloadPlayer mDownloadMediaPlayer;
	private FFmpegPlayer mFFmpegMediaPlayer;
	private AbstractMediaPlayer mMediaPlayer;
    private Context context;
    private boolean mIsInitialized = false;

    /**
     * Default constructor
     */
    protected MultiPlayer() {
    	
    }

    public MultiPlayer(Context context) {
        this.context = context;

        mNativeMediaPlayer = new NativePlayer(context);
        //mNativeMediaPlayer = new NativePlayer();
        mMediaPlayer = mNativeMediaPlayer;
    	// Verify that the host activity implements the callback interface
        try {
            // Instantiate the MultiPlayerListener so we can send events with it
            mListener = (MultiPlayerListener) context;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(context.toString()
                    + " must implement MultiPlayerListener");
        }
    }
    
    public void setDataSource(Context context, long id) {
    	setDataSource(context, null, id, true, false, null);
    }
    
    public void setDataSource(String path, boolean useFFmpegPlayer) {
    	setDataSource(null, path, -1, false, useFFmpegPlayer, null);
    }
    
    private void setDataSource(Context context, String path, long id, boolean isLocalFile, boolean useFFmpegPlayer, String contentType) {
        try {
            mMediaPlayer.reset();
            
            if (!isLocalFile && contentType == null && path.startsWith(HTTPTransport.getProtocolName())) {
            	new HTTPRequestTask(path, useFFmpegPlayer, this).execute();
            	return;
            }
            
            AbstractMediaPlayer player = null;
            if (isLocalFile) {
            	player = getDownloadPlayer();
            } else {
            	if (useFFmpegPlayer) {
            		player = getFFmpegPlayer();
            	} else {
            		player = getMediaPlayer(path);
            	}
            }
            
        	mMediaPlayer = player;
            mMediaPlayer.reset();           
            mMediaPlayer.setOnPreparedListener(onPreparedListener);
            mMediaPlayer.setOnCompletionListener(onCompletionListener);
            mMediaPlayer.setOnErrorListener(onErrorListener);
            mMediaPlayer.setOnInfoListener(onInfoListener);
            
            if (isLocalFile) {
                mMediaPlayer.setDataSource(context, id);
            	mMediaPlayer.prepareAsync();
            } else {
                String pathLog = URLUtils.encodeURL(path);
                LogHelper.Log("MultiPlayer, setDataSource: " + pathLog, 1);
                mMediaPlayer.setDataSource(pathLog);
            	mMediaPlayer.prepareAsync();
            }
            
            LogHelper.Log("Preparing media player", 1);
        } catch (IOException ex) {
            LogHelper.Log("Error initializing media player", 1);
            mIsInitialized = false;
            if (mListener != null) {
            	mListener.onError(this, 0, 0);
            }
        } catch (IllegalArgumentException ex) {
            LogHelper.Log("Error initializing media player", 1);
            mIsInitialized = false;
            if (mListener != null) {
            	mListener.onError(this, 0, 0);
            }
        }
    }
        
    public boolean isInitialized() {
        return mIsInitialized;
    }

    public void start() {
        mMediaPlayer.start();
    }

    public void stop() {
        mMediaPlayer.reset();
        mIsInitialized = false;
    }

    public void release() {
        stop();
        mMediaPlayer.release();
        
        if (mNativeMediaPlayer != null) {
        	mNativeMediaPlayer.release();
        	mNativeMediaPlayer = null;
        }
        
        if (mDownloadMediaPlayer != null) {
        	mDownloadMediaPlayer.release();
        	mDownloadMediaPlayer = null;
        }
        
        if (mFFmpegMediaPlayer != null) {
        	mFFmpegMediaPlayer.release();
        	mFFmpegMediaPlayer = null;
        }
    }
        
    public void pause() {
        mMediaPlayer.pause();
    }
        
    private AbstractMediaPlayer.OnPreparedListener onPreparedListener = new AbstractMediaPlayer.OnPreparedListener() {
		public void onPrepared(AbstractMediaPlayer mp) {
			LogHelper.Log("MultiPlayer; onPreparedListener called", 1);
			
	        mIsInitialized = true;
            if (mListener != null) {
            	mListener.onPrepared(MultiPlayer.this);
            }
		}
    };
    
    private AbstractMediaPlayer.OnCompletionListener onCompletionListener = new AbstractMediaPlayer.OnCompletionListener() {
        public void onCompletion(AbstractMediaPlayer mp) {
        	Log.i(TAG, "onCompletionListener called");
        	
            if (mIsInitialized) {
                if (mListener != null) {
                	mListener.onCompletion(MultiPlayer.this);
                }
            }
        }
    };

    private AbstractMediaPlayer.OnErrorListener onErrorListener = new AbstractMediaPlayer.OnErrorListener() {
        public boolean onError(AbstractMediaPlayer mp, int what, int extra) {
        	LogHelper.Log("MultiPlayer, onErrorListener called", 1);
        	LogHelper.Log("MultiPlayer, onErrorListener; Error: " + what + "," + extra, 1);
        	
            switch (what) {
            	case MediaPlayer.MEDIA_ERROR_IO:
            		release();
            		mNativeMediaPlayer = new NativePlayer(context);
            		mMediaPlayer = mNativeMediaPlayer;
            		
                    if (mListener != null) {
                    	mListener.onError(MultiPlayer.this, MediaPlaybackService.SERVER_DIED, 0);
                    }
            		return true;
            	default:
            		mIsInitialized = false;
                    if (mListener != null) {
                    	mListener.onError(MultiPlayer.this, 0, 0);
                    }
            		break;
            }
            return false;
        }
    };

    private AbstractMediaPlayer.OnInfoListener onInfoListener = new AbstractMediaPlayer.OnInfoListener() {
		@Override
		public boolean onInfo(AbstractMediaPlayer mp, int what, int extra) {
			switch (what) {
				case AbstractMediaPlayer.MEDIA_INFO_METADATA_UPDATE:
                    if (mListener != null) {
                    	mListener.onInfo(MultiPlayer.this, 0, 0);
                    }
					return true;
    			default:
    				break;    	
			}
    		return false;
		}
    };
    	
    public long duration() {
        return mMediaPlayer.getDuration();
    }

    public long position() {
        return mMediaPlayer.getCurrentPosition();
    }

    public long seek(long msec) {
        mMediaPlayer.seekTo((int) msec);
        return msec;
    }

    public void setVolume(float vol) {
        mMediaPlayer.setVolume(vol, vol);
    }
    
    public void setAudioSessionId(int sessionId) {
    	mMediaPlayer.setAudioSessionId(sessionId);
    }
    
    public int getAudioSessionId() {
        return mMediaPlayer.getAudioSessionId();
    }

    public void setNextDataSource(String path) {
    	
    }
    
    /**
     * Detects the appropriate media player depending on the URI of 
     * a file.
     * @param uri path to a file.
     * @return a media player.
     */
	private AbstractMediaPlayer getMediaPlayer(String uri) {
		return mNativeMediaPlayer;
	}
    
	private DownloadPlayer getDownloadPlayer() {
		// allow for lazy initialization of Download player
		// in case it is never used		
		if (mDownloadMediaPlayer == null) {
			mDownloadMediaPlayer = new DownloadPlayer();
		}
		
		return mDownloadMediaPlayer;
	}
	
	private FFmpegPlayer getFFmpegPlayer() {
		// allow for lazy initialization of FFmpeg player
		// in case it is never used		
		if (mFFmpegMediaPlayer == null) {
			mFFmpegMediaPlayer = new FFmpegPlayer();
		}
		
		return mFFmpegMediaPlayer;
	}
	
	@Override
	public void onContentTypeObtained(String path, boolean useFFmpegPlayer, 
			String contentType) {

		setDataSource(null, path, -1, false, useFFmpegPlayer, contentType);
	}

	@Override
	public void onHTTPRequestError(String path, boolean useFFmpegPlayer) {
		setDataSource(null, path, -1, false, useFFmpegPlayer, "");
	}
	
	public interface MultiPlayerListener {
        void onPrepared(MultiPlayer mp);
        void onCompletion(MultiPlayer mp);
        void onError(MultiPlayer mp, int what, int extra);
        void onInfo(MultiPlayer mp, int what, int extra);
	}
}