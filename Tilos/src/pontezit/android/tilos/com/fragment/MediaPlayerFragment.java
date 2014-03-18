package pontezit.android.tilos.com.fragment;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.squareup.picasso.Picasso;

import java.util.ArrayList;

import pontezit.android.tilos.com.R;
import pontezit.android.tilos.com.activity.MediaPlayerActivity;
import pontezit.android.tilos.com.button.RepeatingImageButton;
import pontezit.android.tilos.com.modell.Episode;
import pontezit.android.tilos.com.utils.Finals;
import pontezit.android.tilos.com.utils.LogHelper;
import pontezit.android.tilos.com.utils.MusicUtils;

public class MediaPlayerFragment extends Fragment implements MusicUtils.Defs{


    private RepeatingImageButton mPrevButton;
    public ImageView mPauseButton;
    private RepeatingImageButton mNextButton;
    private TextView mTotalTime;
    private long mDuration;
    private long mPosOverride = -1;
    private ProgressBar mProgress;
    private LinearLayout mSeekBarContainer;
    private long mLastSeekEventTime;
    private boolean mFromTouch = false;
    private TextView mCurrentTime;
    public TextView mListeningText;
    private ArrayList<Episode> episodeList = null;

    private static final int REFRESH = 1;
    private static final int QUIT = 2;
    private ImageView bg;
    public AnimationDrawable frameAnimation;
    private MediaPlayerActivity activity;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        LogHelper.Log("MediaPlayerFragment; onCreate run", 1);
        activity = (MediaPlayerActivity) getActivity();
        activity.supportInvalidateOptionsMenu();
    }

    @Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        LogHelper.Log("MediaPlayerFragment; onCreateView running", 1);

        activity.getSupportActionBar().setDisplayHomeAsUpEnabled(false);

		View view = inflater.inflate(R.layout.fragment_media_player, container, false);
        mSeekBarContainer = (LinearLayout) view.findViewById(R.id.seekBarContainer);
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

        bg = (ImageView) view.findViewById(R.id.bg);


        if(getActivity().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE && !activity.isTabletView())
            Picasso.with(getActivity()).load("file:///android_asset/bg.jpg").fit().rotate(90).into(bg);
        else
            Picasso.with(getActivity()).load("file:///android_asset/bg.jpg").fit().into(bg);

        //Picasso.with(getActivity()).load(R.drawable.bg).fit().into(bg);
        if (mProgress instanceof SeekBar) {
            SeekBar seeker = (SeekBar) mProgress;
            seeker.setOnSeekBarChangeListener(mSeekListener);
        }
        mProgress.setMax(1000);
		return view;
	}

    @Override
    public void onActivityCreated(Bundle savedInstanceState){
        super.onActivityCreated(savedInstanceState);

        setViews();
        LogHelper.Log("MediaPlayerFragment; onActivityCreated run", 1);
        if(!activity.isConnecting())
            updateTrackInfo();
        //getShowDetails();
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.navigation, menu);

        if(activity.isTabletView())
            menu.findItem(R.id.menu_item_shows).setVisible(false);

        return;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
        transaction.setCustomAnimations(R.anim.fragment_in, R.anim.fragment_out);

        switch(item.getItemId()) {
            case R.id.menu_item_alarm:
                LogHelper.Log("menuItem, alarm selected", 1);
                showAlarmDialog();
                return true;
            case (R.id.menu_item_live):
                activity.stop();
                mSeekBarContainer.setVisibility(View.GONE);
                activity.processUri(Finals.getLiveHiUrl());
                return true;
            case (R.id.menu_item_shows):
                LogHelper.Log("menuItem, showList selected", 1);
                Fragment showsFragment = new ShowListFragment();

                transaction.replace(R.id.mediaPlayerContainer, showsFragment, "showList");

                transaction.addToBackStack(null);
                transaction.commit();
                return true;
            case (R.id.menu_item_call):
                showCallDialog();
                return true;
            case (R.id.menu_item_equalizer):
                LogHelper.Log("menuItem equalizer selected", 1);
                try {
                    if (activity.mService != null) {
                        Intent i = new Intent("android.media.action.DISPLAY_AUDIO_EFFECT_CONTROL_PANEL");
                        i.putExtra("android.media.extra.AUDIO_SESSION", activity.mService.getAudioSessionId());
                        startActivityForResult(i, EFFECTS_PANEL);
                    }else{
                        Toast.makeText(getActivity(), getResources().getString(R.string.notSupported), Toast.LENGTH_LONG).show();
                    }
                    return true;
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            return true;
            case (R.id.menu_item_exit):
                activity.killActivity();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }



    @Override
    public void onStart() {
        super.onStart();
        LogHelper.Log("MediaPlayerFragment; onStart running", 1);
    }
    
    @Override
    public void onResume() {
        super.onResume();
        LogHelper.Log("MediaPlayerFragment; onResume running;", 1);
        long next = refreshNow();
        queueNextRefresh(next);
        if(!activity.isConnecting()){
            updateTrackInfo();
            mListeningText.setText(activity.getInfo());
        }
        setPauseButtonImage();

    }

    @Override
    public void onStop() {
        super.onStop();

        LogHelper.Log("MediaPlayerFragment; onStop running", 1);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        activity = null;
        frameAnimation = null;
        LogHelper.Log("MediaPlayerFragment; onDestroy running", 1);
    }



    public void updateTrackInfo(){
        LogHelper.Log("MediaPlayerFragment; updateTrackInfo running", 1);

        //Régi:
        if (activity == null || activity.mService == null)
            return;

        mDuration = activity.getDuration();
        mTotalTime.setText(MusicUtils.makeTimeString(getActivity(), mDuration / 1000));


    }

    private void queueNextRefresh(long delay) {
        Message msg = mHandler.obtainMessage(REFRESH);
        mHandler.removeMessages(REFRESH);
        mHandler.sendMessageDelayed(msg, delay);
    }

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REFRESH:
                    LogHelper.Log("MediaPlayerFragment; mHandler running; CASE: REFRESH", 3);
                    long next = refreshNow();
                    queueNextRefresh(next);
                    break;

                case QUIT:
                    LogHelper.Log("MediaPlayerFragment; mHandler running; CASE: QUIT", 1);
                    // This can be moved back to onCreate once the bug that prevents
                    // Dialogs from being started from onCreate/onResume is fixed.
                    /*new AlertDialog.Builder(getActivity())
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
                            */
                    break;

                default:
                    LogHelper.Log("MediaPlayerFragment; mHandler running; CASE: default; msg:" + msg, 1);
                    break;
            }
        }
    };



    public void setViews(){
        LogHelper.Log("setViews run;", 1);
        try{
            if(activity.getPath() != Finals.getLiveHiUrl()){
                mSeekBarContainer.setVisibility(View.VISIBLE);
                LogHelper.Log("setViews run; mService.getPath() = "+ activity.getPath(), 1);
            }else{
                LogHelper.Log("setViews; else tree", 1);
                mSeekBarContainer.setVisibility(View.GONE);
            }
        }catch(NullPointerException e){

        }


    }

    private long refreshNow() {
        if(activity == null || !activity.isPlaying())
            return 500;

        long pos = mPosOverride < 0 ? activity.getPosition() : mPosOverride;
        if ((pos >= 0)) {
            mCurrentTime.setText(MusicUtils.makeTimeString(getActivity(), pos / 1000));
            if (mDuration > 0) {
                mProgress.setProgress((int) (1000 * pos / mDuration));
            } else {
                mProgress.setProgress(1000);
            }

            if (activity.isPlaying()) {
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

    }





    private View.OnClickListener mPauseListener = new View.OnClickListener() {
        public void onClick(View v) {
            activity.doPauseResume();
        }
    };

    private View.OnClickListener mPrevListener = new View.OnClickListener() {
        public void onClick(View v) {
            activity.setPrev();
        }
    };

    private View.OnClickListener mNextListener = new View.OnClickListener() {
        public void onClick(View v) {
           activity.setNext();
        }
    };

    private SeekBar.OnSeekBarChangeListener mSeekListener = new SeekBar.OnSeekBarChangeListener() {

        public void onStartTrackingTouch(SeekBar bar) {
            LogHelper.Log("MediaPlayerFragment; mSeekListener run; onStartTrackingTouch", 1);
            mLastSeekEventTime = 0;
            mFromTouch = true;
        }
        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
            LogHelper.Log("MediaPlayerFragment; mSeekListener run; onProgressChanged", 3);
            if (!fromuser || !activity.hasService()) return;
            long now = SystemClock.elapsedRealtime();
            if ((now - mLastSeekEventTime) > 250) {
                mLastSeekEventTime = now;
                mPosOverride = mDuration * progress / 1000;

                activity.seek(mPosOverride);


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



    public void setPauseButtonImage() {
        LogHelper.Log("setPauseButtonImage run", 1);
       try{
            if(!activity.isConnecting()){
                if (activity.isPlaying()) {
                    mPauseButton.setImageResource(R.drawable.on);
                } else {
                    mPauseButton.setImageResource(R.drawable.off);
                }
            }
       }catch(NullPointerException e){

       }

    }

    public void setSeekControls() {
        if (activity.hasService()) {
            return;
        }

        if (activity.getDuration() > 0) {
            mProgress.setEnabled(true);
            //mPrevButton.setRepeatListener(mRewListener, 260);
            //mNextButton.setRepeatListener(mFfwdListener, 260);
        } else {
            mProgress.setEnabled(false);
            mPrevButton.setRepeatListener(null, -1);
            mNextButton.setRepeatListener(null, -1);
        }

    }



    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    public void showCallDialog(){
        SharedPreferences sharedPreferences = getActivity().getSharedPreferences(Finals.PREFS_SHARED, 0);
        boolean showCallDialog = sharedPreferences.getBoolean("showCallDialog", true);
        if(showCallDialog){
            final LayoutInflater factory = LayoutInflater.from(getActivity());
            final View textEntryView = factory.inflate(R.layout.dialog_remember, null);
            LinearLayout View = (LinearLayout) textEntryView.findViewById(R.id.LL);
            CheckBox checkBoxCall =( CheckBox ) textEntryView.findViewById( R.id.checkBoxRemember );
            checkBoxCall.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked){
                    if(isChecked){
                        SharedPreferences settings = getActivity().getSharedPreferences(Finals.PREFS_SHARED, 0);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putBoolean("showCallDialog", false);
                        editor.commit();
                    }

                    if(!isChecked){
                        SharedPreferences settings = getActivity().getSharedPreferences(Finals.PREFS_SHARED, 0);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putBoolean("showCallDialog", true);
                        editor.commit();
                    }
                }
            });

            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.dialogCallTitle)
                    .setView(View)
                    .setPositiveButton(R.string.call, new DialogInterface.OnClickListener(){
                        public void onClick(DialogInterface dialog, int which){
                            Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse(Finals.CALL_NO));
                            getActivity().startActivity(intent);
                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener(){
                        public void onClick(DialogInterface dialog, int which){

                        }
                    }).create().show();

        }else{

            Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse(Finals.CALL_NO));
            getActivity().startActivity(intent);

        }
    }

    public void showAlarmDialog(){

        SharedPreferences sharedPreferences = getActivity().getSharedPreferences(Finals.PREFS_SHARED, 0);
        boolean showCallDialog = sharedPreferences.getBoolean("showAlarmDialog", true);
        if(showCallDialog){
            final LayoutInflater factory = LayoutInflater.from(getActivity());
            final View textEntryView = factory.inflate(R.layout.dialog_remember, null);
            LinearLayout View = (LinearLayout) textEntryView.findViewById(R.id.LL);
            CheckBox checkBoxCall = (CheckBox) textEntryView.findViewById( R.id.checkBoxRemember );
            checkBoxCall.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener(){
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked){
                    if(isChecked){
                        SharedPreferences settings = getActivity().getSharedPreferences(Finals.PREFS_SHARED, 0);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putBoolean("showAlarmDialog", false);
                        editor.commit();
                    }

                    if(!isChecked){
                        SharedPreferences settings = getActivity().getSharedPreferences(Finals.PREFS_SHARED, 0);
                        SharedPreferences.Editor editor = settings.edit();
                        editor.putBoolean("showAlarmDialog", true);
                        editor.commit();
                    }
                }
            });
            TextView longText = (TextView) textEntryView.findViewById(R.id.message);
            longText.setText(getResources().getString(R.string.alarm_alarm));

            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.alarm_alar_title)
                    .setView(View)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener(){
                        public void onClick(DialogInterface dialog, int which){
                            FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
                            transaction.setCustomAnimations(R.anim.fragment_in, R.anim.fragment_out);
                            Fragment alarmFragment = new AlarmClockFragment();
                            if(activity.isTabletView())
                                transaction.replace(R.id.detailsContainer, alarmFragment);
                            else
                                transaction.replace(R.id.mediaPlayerContainer, alarmFragment);
                            transaction.addToBackStack(null);
                            transaction.commit();
                        }
                    })
                    .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener(){
                        public void onClick(DialogInterface dialog, int which){

                        }
                    }).create().show();

        }else{
            FragmentTransaction transaction = getActivity().getSupportFragmentManager().beginTransaction();
            transaction.setCustomAnimations(R.anim.fragment_in, R.anim.fragment_out);
            Fragment alarmFragment = new AlarmClockFragment();
            if(activity.isTabletView())
                transaction.replace(R.id.detailsContainer, alarmFragment);
            else
                transaction.replace(R.id.mediaPlayerContainer, alarmFragment);
            transaction.addToBackStack(null);
            transaction.commit();

        }
    }
}