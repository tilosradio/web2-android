package pontezit.android.tilos.com.activity;

import android.app.FragmentManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.Toast;

import pontezit.android.tilos.com.R;
import pontezit.android.tilos.com.fragment.MediaPlayerFragment;
import pontezit.android.tilos.com.fragment.ShowListFragment;
import pontezit.android.tilos.com.service.IMediaPlaybackService;
import pontezit.android.tilos.com.service.MediaPlaybackService;
import pontezit.android.tilos.com.utils.DetermineActionTask;
import pontezit.android.tilos.com.utils.Finals;
import pontezit.android.tilos.com.utils.LogHelper;
import pontezit.android.tilos.com.utils.MusicUtils;
import pontezit.android.tilos.com.utils.PreferenceConstants;
//import pontezit.android.tilos.com.utils.SleepTimerDialog.SleepTimerDialogListener;

public class MediaPlayerActivity extends ActionBarActivity implements MusicUtils.Defs,
                                                                      OnSharedPreferenceChangeListener,
                                                                      //SleepTimerDialogListener,
                                                                      DetermineActionTask.MusicRetrieverPreparedListener{


    private SharedPreferences mPreferences;
    private String URL;
    private boolean isTabletView;
    private MusicUtils.ServiceToken mToken;
    public IMediaPlaybackService mService = null;
    private DetermineActionTask mDetermineActionTask;
    private MediaPlayerFragment mediaPlayerFragment;
    private boolean isConnecting = false;
    private RelativeLayout detailsFragment;
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        LogHelper.Log("MediaPlayerActivity; onCreate running;", 1);
        setContentView(R.layout.activity_media_player);

        detailsFragment = (RelativeLayout) findViewById(R.id.detailsContainer);
        isTabletView = (detailsFragment != null);
        if(isTabletView)
            this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        /*if (!io.vov.vitamio.LibsChecker.checkVitamioLibs(this))
            return;*/


        /*
        Intent intent = getIntent();
        if(icicle != null){
            URL = intent.getStringExtra("URL");
            //LogHelper.Log("ShowDetailsFragment; onActivityCreated; showId = " + showId, 1);
        }else{
        */
        URL = Finals.getLiveHiUrl();
        //}



        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayShowTitleEnabled(false);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mPreferences.registerOnSharedPreferenceChangeListener(this);


        mediaPlayerFragment = (MediaPlayerFragment) getSupportFragmentManager().findFragmentByTag("player");

        if(mediaPlayerFragment == null){
            mediaPlayerFragment = new MediaPlayerFragment();
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

            // Replace whatever is in the fragment_container view with this fragment,
            // and add the transaction to the back stack so the user can navigate back
            transaction.replace(R.id.mediaPlayerContainer, mediaPlayerFragment, "player");

            //transaction.addToBackStack(null);

            transaction.commit();

            processUri("http://stream.tilos.hu:80/tilos");

            if(isTabletView){
                FragmentTransaction showsTransaction = getSupportFragmentManager().beginTransaction();
                Fragment showsFragment = new ShowListFragment();
                showsTransaction.replace(R.id.detailsContainer, showsFragment, "showList");
                showsTransaction.disallowAddToBackStack();
                showsTransaction.commit();
            }
        }

    }


    @Override
    public void onResume() {
        super.onResume();
        LogHelper.Log("MediaPlayerActivity; onResume running;", 1);
        IntentFilter f = new IntentFilter();
        f.addAction(MediaPlaybackService.PLAYSTATE_CHANGED);
        f.addAction(MediaPlaybackService.META_CHANGED);
        f.addAction(MediaPlaybackService.START_DIALOG);
        f.addAction(MediaPlaybackService.STOP_DIALOG);
        this.registerReceiver(mStatusListener, new IntentFilter(f));
        if(mToken == null){
            LogHelper.Log("MediaPlayerActivity, processUri; mService is null", 1);
            mToken = MusicUtils.bindToService(this, osc);
        }

    }

    @Override
    public void onStop() {
        LogHelper.Log("MediaPlayerActivity; onStop running", 1);
        this.unregisterReceiver(mStatusListener);
        MusicUtils.unbindFromService(mToken);
        mToken = null;
        mService = null;
        /*
        getActivity().unregisterReceiver(mStatusListener);


        paused = true;
        mHandler.removeMessages(REFRESH);
        */
        super.onStop();

    }

    public void killActivity(){
        try{
            mService.stop();
            mService = null;
            this.unregisterReceiver(mStatusListener);
            MusicUtils.unbindFromService(mToken);
            mToken = null;
        }catch(RemoteException e){
            e.printStackTrace();
        }
        int pid = android.os.Process.myPid();
        android.os.Process.killProcess(pid);
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(PreferenceConstants.WAKELOCK)) {
            if (sharedPreferences.getBoolean(PreferenceConstants.WAKELOCK, true)) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
        }
    }


    private void setSleepTimer(int pos) {
        if (mService == null) {
            return;
        }
        try {
            MusicUtils.sService.setSleepTimerMode(pos);
            if (pos == MediaPlaybackService.SLEEP_TIMER_OFF) {
                //showToast(R.string.sleep_timer_off_notif);
            } else {
               // showToast(getString(R.string.sleep_timer_on_notif) + " " + makeTimeString(pos));
            }
        } catch (RemoteException e) {
        }
    }

    /*@Override
    public void onSleepTimerSet(DialogFragment dialog, int pos) {
        setSleepTimer(pos);
    }
    */
    public boolean processUri(String input) {
        LogHelper.Log("MediaPlayerActivity; processUri run", 1);

        if(mToken == null){
            LogHelper.Log("MediaPlayerActivity, processUri; mService is null", 1);
            mToken = MusicUtils.bindToService(this, osc);
        }else{
            LogHelper.Log("MediaPlayerActivity, processUri; mService is NOT null", 1);
            try{
                if(mService != null && mService.getPath() != input){
                    LogHelper.Log("MediaPlayerActivity, processUri; new URL!", 1);
                    URL = input;
                    startPlayback();
                }
            }catch(RemoteException e){
                e.printStackTrace();
            }
        }


        //updateTrackInfo();

        //showDialog(LOADING_DIALOG);

        return true;
    }

    private void startPlayback(){
        LogHelper.Log("MediaPlayerActivity; startPlayback run", 1);
        try{
            if(!mService.isPlaying() || mService.getPath() != URL){
                mDetermineActionTask = new DetermineActionTask(this, URL, this);
                mDetermineActionTask.execute();
                if(mService == null)
                    return;


            }
        }catch(RemoteException e){
            e.printStackTrace();
        }
        mediaPlayerFragment.setViews();
        //updateTrackInfo();
        //long next = refreshNow();
        //queueNextRefresh(next);
    }

    private ServiceConnection osc = new ServiceConnection() {
        public void onServiceConnected(ComponentName classname, IBinder obj) {
            LogHelper.Log("MediaPlayerActivity; onServiceConnected running", 1);
            mService = IMediaPlaybackService.Stub.asInterface(obj);
            mediaPlayerFragment.mListeningText.setText(getInfo());
            mediaPlayerFragment.setPauseButtonImage();
            try{
                if(!mService.isPlaying())
                    startPlayback();
            }catch(RemoteException e){
                startPlayback();
            }
            try {
                // Assume something is playing when the service says it is,
                // but also if the audio ID is valid but the service is paused.
                if (mService.getPath() != null || mService.isPlaying() ||
                        mService.toString() != null) {
                    // something is playing now, we're done
                    //setPauseButtonImage();
                    return;
                }
            } catch (RemoteException ex) {
            }
            // Service is dead or not playing anything. Return to the previous
            // activity.
        }
        public void onServiceDisconnected(ComponentName classname) {
            LogHelper.Log("MediaPlayerActivity; onServiceDisconnected running", 1);
            mService = null;
        }
    };

    private BroadcastReceiver mStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            try{
            if (action.equals(MediaPlaybackService.META_CHANGED)){
                LogHelper.Log("MediaPlayerActivity; mStatusListener; MediaPlaybackService.META_CHANGED", 1);

                mediaPlayerFragment.mListeningText.setText(getInfo());
                mediaPlayerFragment.setSeekControls();
                mediaPlayerFragment.setPauseButtonImage();
            } else if (action.equals(MediaPlaybackService.PLAYSTATE_CHANGED)) {
                LogHelper.Log("MediaPlayerActivity; mStatusListener; MediaPlaybackService.PLAYSTATE_CHANGED", 1);
                mediaPlayerFragment.setPauseButtonImage();
            } else if (action.equals(MediaPlaybackService.START_DIALOG)) {
                LogHelper.Log("MediaPlayerActivity; mStatusListener; MediaPlaybackService.START_DIALOG", 1);
                mediaPlayerFragment.mListeningText.setText("Kapcsolódás...");
                mediaPlayerFragment.mPauseButton.setImageResource(R.anim.connecting);
                mediaPlayerFragment.frameAnimation = (AnimationDrawable) mediaPlayerFragment.mPauseButton.getDrawable();
                isConnecting = true;
                mediaPlayerFragment.frameAnimation.start();
            } else if (action.equals(MediaPlaybackService.STOP_DIALOG)) {
                LogHelper.Log("MediaPlayerActivity; mStatusListener; MediaPlaybackService.STOP_DIALOG", 1);
                mediaPlayerFragment.frameAnimation.stop();
                isConnecting = false;
                mediaPlayerFragment.setPauseButtonImage();
                mediaPlayerFragment.updateTrackInfo();
                mediaPlayerFragment.setViews();
            }
            }catch(NullPointerException e){

            }
        }
    };

    @Override
    public void onMusicRetrieverPrepared(String action, String path) {
        LogHelper.Log("MediaPlayerActivity; onMusicRetrieverPrepared run; path: " + path, 1);

        if (action.equals(DetermineActionTask.URL_ACTION_UNDETERMINED)) {
            Toast.makeText(this, R.string.playback_failed, Toast.LENGTH_SHORT).show();
        } else if (action.equals(DetermineActionTask.URL_ACTION_PLAY)) {
            MusicUtils.play(this, path, false);
        }
    }

    public void doPauseResume() {
        try {
            if(mService != null) {
                if (mService.isPlaying()) {
                    mService.pause();
                }else{
                    mService.play();
                }

            }
        } catch (RemoteException ex) {
            LogHelper.Log("MediaPlayerActivity, doPauseResume; RemoteException");
        }
    }

    public boolean hasService(){
        if (mService == null)
            return false;
        else
            return true;
    }

    public boolean isPlaying(){
        try{
            if(mService == null || !mService.isPlaying())
                return false;
            else
                return true;
        }catch(RemoteException e){
            LogHelper.Log("MediaPlayerActivity, isPlaying; RemoteException");
            return false;
        }catch(NullPointerException e){
            LogHelper.Log("MediaPlayerActivity, isPlaying; NullPointerException");
            return false;
        }
    }

    public String getPath(){
        try{
            if(mService.getPath() == null)
                return Finals.getLiveHiUrl();

            return mService.getPath();
        }catch(RemoteException e){
            LogHelper.Log("MediaPlayerActivity, getPath; RemoteException");
            return Finals.getLiveHiUrl();
        }catch(NullPointerException e){
            LogHelper.Log("MediaPlayerActivity, getPath; NullPointerException");
            return Finals.getLiveHiUrl();
        }
    }

    public long getDuration(){
        try{
            return mService.duration();
        }catch(RemoteException e){
            LogHelper.Log("MediaPlayerActivity, getDuration; RemoteException", 1);
            return 0;
        }catch(NullPointerException e){
            LogHelper.Log("MediaPlayerActivity, getDuration; NullPointerException", 1);
            return 0;
        }
    }

    public long getPosition(){
        try{
            return mService.position();
        }catch(RemoteException e){
            LogHelper.Log("MediaPlayerActivity, getPosition; RemoteException", 1);
            return 0;
        }catch(NullPointerException e){
            LogHelper.Log("MediaPlayerActivity, getPosition; NullPointerException", 1);
            return 0;
        }
    }

    public String getInfo(){
        try{
            return mService.getReadableTime() + " - " + mService.getShowName();
        }catch(RemoteException e){
            LogHelper.Log("MediaPlayerActivity, getInfo; RemoteException", 1);
            return "";
        }catch(NullPointerException e){
            LogHelper.Log("MediaPlayerActivity, getInfo; NullPointerException", 1);
            return "";
        }
    }

    public void setPrev(){
        if (mService == null) return;
        try {
            mService.prev();
        } catch (RemoteException ex) {
            LogHelper.Log("MediaPlayerActivity, setPrev; RemoteException", 1);
        }
    }

    public void setNext(){
        if (mService == null)
            return;

        try {
            mService.next();
        } catch (RemoteException ex) {
            LogHelper.Log("MediaPlayerActivity, setNext; RemoteException", 1);
        }
    }

    public boolean isConnecting(){
        return isConnecting;
    }

    public void seek(long seekTo){
        try{
            mService.seek(seekTo);
        }catch(RemoteException e){
            LogHelper.Log("MediaPlayerActivity, seek; RemoteException", 1);
        }
    }

    public void stop(){
        try{
            mService.stop();
        }catch(RemoteException e){
            LogHelper.Log("MediaPlayerActivity, stop; RemoteException", 1);
        }catch(NullPointerException e){
            LogHelper.Log("MediaPlayerActivity, stop; NullPointerException", 1);
        }
    }

    public boolean isTabletView(){
        return isTabletView;
    }



}
