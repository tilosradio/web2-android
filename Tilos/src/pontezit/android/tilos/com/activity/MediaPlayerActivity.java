package pontezit.android.tilos.com.activity;

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
import android.database.ContentObserver;
import android.media.AudioManager;
import android.net.Uri;
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
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.WindowManager;
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
import pontezit.android.tilos.com.dbutils.TilosDatabase;
import pontezit.android.tilos.com.fragment.MediaPlayerFragment;
import pontezit.android.tilos.com.modell.Episode;
import pontezit.android.tilos.com.modell.Show;
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

public class MediaPlayerActivity extends ActionBarActivity implements MusicUtils.Defs,
        OnSharedPreferenceChangeListener,
        LoadingDialogListener,
        SleepTimerDialogListener {


    private static final String LOADING_DIALOG = "loading_dialog";
    private static final String SLEEP_TIMER_DIALOG = "sleep_timer_dialog";

    private DialogFragment mLoadingDialog;

    private int mParentActivityState = VISIBLE;
    private static int VISIBLE = 1;

    private SharedPreferences mPreferences;


    private IMediaPlaybackService mService = null;
    private Toast mToast;
    private MusicUtils.ServiceToken mToken;
    public MediaPlayerActivity()
    {
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.activity_media_player);


        ActionBar actionBar = getSupportActionBar();
        actionBar.setDisplayHomeAsUpEnabled(false);
        actionBar.setDisplayShowCustomEnabled(true);
        actionBar.setDisplayShowTitleEnabled(false);
        /*
        LayoutInflater inflater = getLayoutInflater();
        View v = inflater.inflate(R.layout.action_bar_media_player, null);
        actionBar.setCustomView(v);
        */

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mPreferences.registerOnSharedPreferenceChangeListener(this);









    }



    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        SubMenu sub = menu.addSubMenu("More");
        sub.getItem().setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        sub.getItem().setIcon(R.drawable.ic_menu_moreoverflow_normal_holo_dark);

        getMenuInflater().inflate(R.menu.navigation, sub);

        return true;
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
            case (R.id.menu_item_equalizer): {

                try {
                    if (MusicUtils.getCurrentAudioId() >= 0) {
                        Intent i = new Intent("android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL");
                        i.putExtra("android.media.extra.AUDIO_SESSION", mService.getAudioSessionId());
                        startActivityForResult(i, EFFECTS_PANEL);
                    }else{
                        Toast.makeText(this, getResources().getString(R.string.notSupported), Toast.LENGTH_LONG).show();
                    }
                    return true;
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        }

        return super.onOptionsItemSelected(item);
    }


    private void doStop() {
        try {
            if(mService != null) {
                mService.stop();
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















    private BroadcastReceiver mStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            LogHelper.Log("MediaPlayerActivity; mStatusListener (BroadcastReceiver) run", 1);
            String action = intent.getAction();
            if (action.equals(MediaPlaybackService.START_DIALOG)) {
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
