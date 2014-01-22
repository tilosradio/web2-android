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

package pontezit.android.tilos.com.activity;

import pontezit.android.tilos.com.R;
import pontezit.android.tilos.com.bean.UriBean;
import pontezit.android.tilos.com.button.RepeatingImageButton;
import pontezit.android.tilos.com.dbutils.TilosDatabase;
import pontezit.android.tilos.com.fragment.MediaPlayerFragment;
import pontezit.android.tilos.com.service.IMediaPlaybackService;
import pontezit.android.tilos.com.service.MediaPlaybackService;
import pontezit.android.tilos.com.transport.AbsTransport;
import pontezit.android.tilos.com.transport.TransportFactory;
import pontezit.android.tilos.com.utils.DetermineActionTask;
import pontezit.android.tilos.com.utils.LoadingDialog;
import pontezit.android.tilos.com.utils.LoadingDialog.LoadingDialogListener;
import pontezit.android.tilos.com.utils.LogHelper;
import pontezit.android.tilos.com.utils.MusicUtils;
import pontezit.android.tilos.com.utils.PreferenceConstants;
import pontezit.android.tilos.com.utils.SleepTimerDialog;
import pontezit.android.tilos.com.utils.SleepTimerDialog.SleepTimerDialogListener;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Parcelable;
import android.os.RemoteException;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class MediaPlayerActivity extends ActionBarActivity implements MusicUtils.Defs,
    		OnSharedPreferenceChangeListener,
    		LoadingDialogListener,
    		SleepTimerDialogListener,
            DetermineActionTask.MusicRetrieverPreparedListener {
	
    private static final String TAG = MediaPlayerActivity.class.getName();

    private TilosDatabase mStreamdb = null;
    private DetermineActionTask mDetermineActionTask;

	private static final String LOADING_DIALOG = "loading_dialog";
	private static final String SLEEP_TIMER_DIALOG = "sleep_timer_dialog";
    
	private DialogFragment mLoadingDialog;
	
    private int mParentActivityState = VISIBLE;
    private static int VISIBLE = 1;
    private static int GONE = 2;
    
    private SharedPreferences mPreferences;
    
    private boolean mSeeking = false;
    private boolean mDeviceHasDpad;
    private long mStartSeekPos = 0;
    private long mLastSeekEventTime;
    private IMediaPlaybackService mService = null;
    private RepeatingImageButton mPrevButton;
    private ImageButton mPauseButton;
    private RepeatingImageButton mNextButton;
    private Toast mToast;
    private MusicUtils.ServiceToken mToken;

    private VolumeObserver mVolumeObserver;

	private GridPagerAdapter mAdapter;
    
    public MediaPlayerActivity()
    {
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_media_player);
        mStreamdb = new TilosDatabase(this);

		ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayShowTitleEnabled(false);
        
        LayoutInflater inflater = getLayoutInflater();
        View v = inflater.inflate(R.layout.action_bar_media_player, null);
        mVolume = (SeekBar) v.findViewById(R.id.volume_bar);
        
        actionBar.setCustomView(v);
		
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mPreferences.registerOnSharedPreferenceChangeListener(this);
        
        mCurrentTime = (TextView) findViewById(R.id.position_text);
        mTotalTime = (TextView) findViewById(R.id.duration_text);
        mProgress = (ProgressBar) findViewById(R.id.seek_bar);

        mPrevButton = (RepeatingImageButton) findViewById(R.id.previous_button);
        mPrevButton.setOnClickListener(mPrevListener);
        mPrevButton.setRepeatListener(mRewListener, 260);
        mPauseButton = (ImageButton) findViewById(R.id.play_pause_button);
        mPauseButton.setOnClickListener(mPauseListener);
        mNextButton = (RepeatingImageButton) findViewById(R.id.next_button);
        mNextButton.setOnClickListener(mNextListener);
        mNextButton.setRepeatListener(mFfwdListener, 260);
        seekmethod = 1;

        mDeviceHasDpad = (getResources().getConfiguration().navigation ==
            Configuration.NAVIGATION_DPAD);

        if (mProgress instanceof SeekBar) {
            SeekBar seeker = (SeekBar) mProgress;
            seeker.setOnSeekBarChangeListener(mSeekListener);
        }
        mProgress.setMax(1000);
        
        mVolume.setOnSeekBarChangeListener(mVolumeListener);

    }

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
        mDetermineActionTask = new DetermineActionTask(this, uriBean, this);
        mDetermineActionTask.execute();

        return true;
    }

    @Override
    public void onMusicRetrieverPrepared(String action, UriBean uri, long[] list) {
        LogHelper.Log("MediaPlayerActivity; onMusicRetrieverPrepared run", 1);

        if (action.equals(DetermineActionTask.URL_ACTION_UNDETERMINED)) {
            showUrlNotOpenedToast();
        } else if (action.equals(DetermineActionTask.URL_ACTION_PLAY)) {
            mStreamdb.touchUri(uri);
            MusicUtils.playAll(this, list, 0);
        }
    }

    private void showUrlNotOpenedToast() {
        Toast.makeText(this, R.string.url_not_opened_message, Toast.LENGTH_SHORT).show();
    }
    
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
  	    if (key.equals(PreferenceConstants.WAKELOCK)) {
			if (sharedPreferences.getBoolean(PreferenceConstants.WAKELOCK, true)) {
		    	getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  	        } else {
  	      		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  	        }
  	    }
  	}
    
    private OnSeekBarChangeListener mSeekListener = new OnSeekBarChangeListener() {
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

    private OnSeekBarChangeListener mVolumeListener = new OnSeekBarChangeListener() {

		@Override
		public void onProgressChanged(SeekBar seekBar, int progress,
				boolean fromUser) {
			AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
	    	audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0);
		}

		@Override
		public void onStartTrackingTouch(SeekBar seekBar) {
			
		}

		@Override
		public void onStopTrackingTouch(SeekBar seekBar) {
			
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
    
    private RepeatingImageButton.RepeatListener mRewListener =
        new RepeatingImageButton.RepeatListener() {
        public void onRepeat(View v, long howlong, int repcnt) {
            scanBackward(repcnt, howlong);
        }
    };
    
    private RepeatingImageButton.RepeatListener mFfwdListener =
        new RepeatingImageButton.RepeatListener() {
        public void onRepeat(View v, long howlong, int repcnt) {
            scanForward(repcnt, howlong);
        }
    };

    @Override
    public void onStart(){
        LogHelper.Log("MediaPlayerActivity; onStart run", 1);
        super.onStart();
        paused = false;
        processUri("http://stream.tilos.hu/tilos.m3u");

        if (mPreferences.getBoolean(PreferenceConstants.WAKELOCK, true)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        mToken = MusicUtils.bindToService(this, osc);
        if (mToken == null) {
            // something went wrong
            LogHelper.Log("MediaPlayerActivity; onStart; mToken is null", 1);
            mHandler.sendEmptyMessage(QUIT);
        }

        mVolumeObserver = new VolumeObserver(new Handler());
        getApplicationContext().getContentResolver().registerContentObserver(
        		android.provider.Settings.System.CONTENT_URI,
        		true,
        		mVolumeObserver);

        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
        f.addAction(MediaPlaybackService.META_CHANGED);
        f.addAction(MediaPlaybackService.START_DIALOG);
        f.addAction(MediaPlaybackService.STOP_DIALOG);
        registerReceiver(mStatusListener, new IntentFilter(f));
        updateTrackInfo();
        long next = refreshNow();
        queueNextRefresh(next);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        mParentActivityState = VISIBLE;
        updateTrackInfo();
        setPauseButtonImage();
        updateVolumeBar();
    }

    @Override
    public void onPause() {
    	super.onPause();
    	mParentActivityState = GONE;
    	dismissLoadingDialog();
    }

    @Override
    public void onStop() {
        paused = true;
        mHandler.removeMessages(REFRESH);
        getApplicationContext().getContentResolver().unregisterContentObserver(mVolumeObserver);
        unregisterReceiver(mStatusListener);
        MusicUtils.unbindFromService(mToken);
        mService = null;
        super.onStop();
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch(item.getItemId()) {
    		case android.R.id.home:
    			Intent intent = new Intent(this, MainActivity.class);
    			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    			startActivity(intent);
    			finish();
    			return true;
    		case (R.id.menu_play_queue):
    			startActivity(new Intent(MediaPlayerActivity.this, NowPlayingActivity.class));
    			return true;
    		case (R.id.menu_item_stop):
    			doStop();
    			return true;
        	case (R.id.menu_item_sleep_timer):
        		showDialog(SLEEP_TIMER_DIALOG);
        		return true;
        	case (R.id.menu_item_settings):
        		startActivity(new Intent(MediaPlayerActivity.this, SettingsActivity.class));
        		return true;
        	case EFFECTS_PANEL: {
                try {
                	Intent i = new Intent("android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL");
					i.putExtra("android.media.extra.AUDIO_SESSION", mService.getAudioSessionId());
	                startActivityForResult(i, EFFECTS_PANEL);
	                return true;
				} catch (RemoteException e) {
					e.printStackTrace();
				}
             }
        }
         	
        return super.onOptionsItemSelected(item);
    }
    
    @Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		super.onPrepareOptionsMenu(menu);

		if (Build.VERSION.SDK_INT >= 9) {
			// Don't offer the audio effects display when running on an OS
			// before API level 9 because it relies on the getAudioSessionId method,
			// which isn't available until after API 8
			if (menu.findItem(EFFECTS_PANEL) == null) {
				Intent i = new Intent("android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL");
				if (getPackageManager().resolveActivity(i, 0) != null) {
					menu.add(0, EFFECTS_PANEL, 0, R.string.list_menu_effects).setIcon(R.drawable.ic_menu_eq);
				}
			} else {
				MenuItem item = menu.findItem(EFFECTS_PANEL);
				
				if (item != null) {
					if (MusicUtils.getCurrentAudioId() >= 0) {
						item.setVisible(true);
					} else {
						item.setVisible(false);
					}
				}
			}
		}
		
		return true;
	}
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.media_player, menu);
        return true;
    }


    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        try {
            switch(keyCode)
            {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (!useDpadMusicControl()) {
                        break;
                    }
                    if (mService != null) {
                        if (!mSeeking && mStartSeekPos >= 0) {
                            mPauseButton.requestFocus();
                            if (mStartSeekPos < 1000) {
                                mService.prev();
                            } else {
                                mService.seek(0);
                            }
                        } else {
                            scanBackward(-1, event.getEventTime() - event.getDownTime());
                            mPauseButton.requestFocus();
                            mStartSeekPos = -1;
                        }
                    }
                    mSeeking = false;
                    mPosOverride = -1;
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (!useDpadMusicControl()) {
                        break;
                    }
                    if (mService != null) {
                        if (!mSeeking && mStartSeekPos >= 0) {
                            mPauseButton.requestFocus();
                            mService.next();
                        } else {
                            scanForward(-1, event.getEventTime() - event.getDownTime());
                            mPauseButton.requestFocus();
                            mStartSeekPos = -1;
                        }
                    }
                    mSeeking = false;
                    mPosOverride = -1;
                    return true;
            }
        } catch (RemoteException ex) {
        }
        return super.onKeyUp(keyCode, event);
    }

    private boolean useDpadMusicControl() {
        if (mDeviceHasDpad && (mPrevButton.isFocused() ||
                mNextButton.isFocused() ||
                mPauseButton.isFocused())) {
            return true;
        }
        return false;
    }

    private void scanBackward(int repcnt, long delta) {
        if(mService == null) return;
        try {
            if(repcnt == 0) {
                mStartSeekPos = mService.position();
                mLastSeekEventTime = 0;
                mSeeking = false;
            } else {
                mSeeking = true;
                if (delta < 5000) {
                    // seek at 10x speed for the first 5 seconds
                    delta = delta * 10; 
                } else {
                    // seek at 40x after that
                    delta = 50000 + (delta - 5000) * 40;
                }
                long newpos = mStartSeekPos - delta;
                if (newpos < 0) {
                    // move to previous track
                    mService.prev();
                    long duration = mService.duration();
                    mStartSeekPos += duration;
                    newpos += duration;
                }
                if (((delta - mLastSeekEventTime) > 250) || repcnt < 0){
                    mService.seek(newpos);
                    mLastSeekEventTime = delta;
                }
                if (repcnt >= 0) {
                    mPosOverride = newpos;
                } else {
                    mPosOverride = -1;
                }
                refreshNow();
            }
        } catch (RemoteException ex) {
        }
    }

    private void scanForward(int repcnt, long delta) {
        if(mService == null) return;
        try {
            if(repcnt == 0) {
                mStartSeekPos = mService.position();
                mLastSeekEventTime = 0;
                mSeeking = false;
            } else {
                mSeeking = true;
                if (delta < 5000) {
                    // seek at 10x speed for the first 5 seconds
                    delta = delta * 10; 
                } else {
                    // seek at 40x after that
                    delta = 50000 + (delta - 5000) * 40;
                }
                long newpos = mStartSeekPos + delta;
                long duration = mService.duration();
                if (newpos >= duration) {
                    // move to next track
                    mService.next();
                    mStartSeekPos -= duration; // is OK to go negative
                    newpos -= duration;
                }
                if (((delta - mLastSeekEventTime) > 250) || repcnt < 0){
                    mService.seek(newpos);
                    mLastSeekEventTime = delta;
                }
                if (repcnt >= 0) {
                    mPosOverride = newpos;
                } else {
                    mPosOverride = -1;
                }
                refreshNow();
            }
        } catch (RemoteException ex) {
        }
    }
    
    private void doPauseResume() {
        try {
            if(mService != null) {
                if (mService.isPlaying()) {
                    mService.pause();
                } else {
                    mService.play();
                }
                refreshNow();
                setPauseButtonImage();
            }
        } catch (RemoteException ex) {
        }
    }
    
    private void doStop() {
    	try {
    		if(mService != null) {
    			mService.stop();
    			refreshNow();
    			setPauseButtonImage();
    		}
    	} catch (RemoteException ex) {
    	}
    }

    private void setSleepTimer(int pos) {
        if (mService == null) {
            return;
        }    	
    	try {
			MusicUtils.sService.setSleepTimerMode(pos);
			if (pos == MediaPlaybackService.SLEEP_TIMER_OFF) {
				showToast(R.string.sleep_timer_off_notif);
			} else {
			    showToast(getString(R.string.sleep_timer_on_notif) + " " + makeTimeString(pos));
			}
		} catch (RemoteException e) {
		}
    }
    
    private String makeTimeString(int pos) {
    	String minuteText;
    	
    	if (pos == MediaPlaybackService.SLEEP_TIMER_OFF) {
	    	minuteText = getResources().getString(R.string.disable_sleeptimer_label);
	    } else if (pos == 1) {
	    	minuteText = getResources().getString(R.string.minute);
	    } else if (pos % 60 == 0 && pos > 60) {
	    	minuteText = getResources().getString(R.string.hours, String.valueOf(pos / 60));
	    } else if (pos % 60 == 0) {
	    	minuteText = getResources().getString(R.string.hour);
	    } else {
	    	minuteText = getResources().getString(R.string.minutes, String.valueOf(pos));
	    }
    	
    	return minuteText;
    }
    
    private void showToast(int resid) {
        if (mToast == null) {
            mToast = Toast.makeText(this, null, Toast.LENGTH_SHORT);
        }
        mToast.setText(resid);
        mToast.show();
    }
    
    private void showToast(String message) {
        if (mToast == null) {
            mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        }
        mToast.setText(message);
        mToast.show();
    }
    
    private void startPlayback() {
        LogHelper.Log("MediaPlayerActivity; startPlayback run", 1);
        if(mService == null)
            return;

        updateTrackInfo();
        long next = refreshNow();
        queueNextRefresh(next);
    }

    private ServiceConnection osc = new ServiceConnection() {
            public void onServiceConnected(ComponentName classname, IBinder obj) {
                LogHelper.Log("MediaPlayerActivity; ServiceConncetion; onServiceConnected run", 1);
                mService = IMediaPlaybackService.Stub.asInterface(obj);
                startPlayback();
                try {
                    // Assume something is playing when the service says it is,
                    // but also if the audio ID is valid but the service is paused.
                    if (mService.getAudioId() >= 0 || mService.isPlaying() ||
                            mService.getPath() != null) {
                        setPauseButtonImage();

                        return;
                    }
                } catch (RemoteException ex) {
                }
                // Service is dead or not playing anything.
                /* TODO: DO SOMETHING */

            }
            public void onServiceDisconnected(ComponentName classname) {
                LogHelper.Log("MediaPlayerActivity; ServiceConncetion; onServiceDisconnected run", 1);
                mService = null;
            }
    };

    
    private void setPauseButtonImage() {
        try {
            if (mService != null && mService.isPlaying()) {
                mPauseButton.setImageResource(R.drawable.btn_player_pause);
            } else {
                mPauseButton.setImageResource(R.drawable.btn_player_play);
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
            	mPrevButton.setRepeatListener(mRewListener, 260);
            	mNextButton.setRepeatListener(mFfwdListener, 260);
			} else {
				mProgress.setEnabled(false);
    			mPrevButton.setRepeatListener(null, -1);
    			mNextButton.setRepeatListener(null, -1);
			}
		} catch (RemoteException e) {
		}	
    }

    private TextView mCurrentTime;
    private TextView mTotalTime;
    private ProgressBar mProgress;
    private SeekBar mVolume;
    private long mPosOverride = -1;
    private boolean mFromTouch = false;
    private long mDuration;
    private int seekmethod;
    private boolean paused;

    private static final int REFRESH = 1;
    private static final int QUIT = 2;

    private void queueNextRefresh(long delay) {
        if (!paused) {
            Message msg = mHandler.obtainMessage(REFRESH);
            mHandler.removeMessages(REFRESH);
            mHandler.sendMessageDelayed(msg, delay);
        }
    }

    private long refreshNow() {
        if(mService == null)
            return 500;
        try {
            long pos = mPosOverride < 0 ? mService.position() : mPosOverride;
            if ((pos >= 0)) {
                mCurrentTime.setText(MusicUtils.makeTimeString(this, pos / 1000));
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
                    new AlertDialog.Builder(MediaPlayerActivity.this)
                            .setTitle(R.string.service_start_error_title)
                            .setMessage(R.string.service_start_error_msg)
                            .setPositiveButton(android.R.string.ok,
                                    new DialogInterface.OnClickListener() {
                                        public void onClick(DialogInterface dialog, int whichButton) {
                                            finish();
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

    private BroadcastReceiver mStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            LogHelper.Log("MediaPlayerActivity; mStatusListener (BroadcastReceiver) run", 1);
            String action = intent.getAction();
            if (action.equals(MediaPlaybackService.META_CHANGED)) {
                // redraw the artist/title info and
                // set new max for progress bar
                updateTrackInfo();
                setSeekControls();
                setPauseButtonImage();
                queueNextRefresh(1);
            } else if (action.equals(MediaPlaybackService.PLAYSTATE_CHANGED)) {
                setPauseButtonImage();
            } else if (action.equals(MediaPlaybackService.START_DIALOG)) {
	        	if (mParentActivityState == VISIBLE) {
	        		showLoadingDialog();
	        	}
            } else if (action.equals(MediaPlaybackService.STOP_DIALOG)) {
            	if (mParentActivityState == VISIBLE) {
            		dismissLoadingDialog();
            	}
            }
        }
    };
    
    private void updateTrackInfo() {
        if (mService == null) {
            return;
        }
        try {
        	mDuration = mService.duration();
        	mTotalTime.setText(MusicUtils.makeTimeString(this, mDuration / 1000));
        } catch (RemoteException ex) {
        	finish();
        }
    }
    
	public synchronized void showLoadingDialog() {
		mLoadingDialog = LoadingDialog.newInstance(this, getString(R.string.opening_url_message));
		mLoadingDialog.show(getSupportFragmentManager(), LOADING_DIALOG);
	}
	
	public synchronized void dismissLoadingDialog() {
		if (mLoadingDialog != null) {
			mLoadingDialog.dismiss();
			mLoadingDialog = null;
		}
	}
	
	private synchronized void updateVolumeBar() {
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int curVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        mVolume.setProgress(curVolume);
        mVolume.setMax(maxVolume);
	}
	
	public void showDialog(String tag) {
		// DialogFragment.show() will take care of adding the fragment
		// in a transaction.  We also want to remove any currently showing
		// dialog, so make our own transaction and take care of that here.
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		Fragment prev = getSupportFragmentManager().findFragmentByTag(tag);
		if (prev != null) {
			ft.remove(prev);
		}

		DialogFragment newFragment = null;

		// Create and show the dialog.
		if (tag.equals(SLEEP_TIMER_DIALOG)) {
			if (mService == null) {
				return;
			}
			try {
				newFragment = SleepTimerDialog.newInstance(this, mService.getSleepTimerMode());
			} catch (RemoteException e) {
			}
		}

		ft.add(0, newFragment, tag);
		ft.commit();
	}

	public void dismissDialog(String tag) {
		FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
		DialogFragment prev = (DialogFragment) getSupportFragmentManager().findFragmentByTag(tag);
		if (prev != null) {
			prev.dismiss();
			ft.remove(prev);
		}
		ft.commit();
	}

	@Override
	public void onLoadingDialogCancelled(DialogFragment dialog) {
		
	}
	
	@Override
	public void onSleepTimerSet(DialogFragment dialog, int pos) {
		setSleepTimer(pos);
	}
	
	private class VolumeObserver extends ContentObserver {

		public VolumeObserver(Handler handler) {
			super(handler);
		}

		@Override
		public void onChange(boolean selfChange) {
			onChange(selfChange, null);
		}
			
		@Override
		public void onChange(boolean selfChange, Uri uri) {
			updateVolumeBar();
		}
	}
	
	private class GridPagerAdapter extends FragmentStatePagerAdapter {
    	private int mCount;
		
		public GridPagerAdapter(FragmentManager fm, int count) {
    		super(fm);
    		mCount = count;
        }
    	
        @Override
        public Fragment getItem(int position) {
            return new MediaPlayerFragment();
        }

        @Override
        public int getCount() {
        	return mCount;
        }
        
        @Override
        public Parcelable saveState() {
        	return null;
        }
    }
}
