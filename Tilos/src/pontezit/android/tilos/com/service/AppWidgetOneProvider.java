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

package pontezit.android.tilos.com.service;

import pontezit.android.tilos.com.R;
import pontezit.android.tilos.com.activity.MediaPlayerActivity;
import pontezit.android.tilos.com.provider.Media;
import pontezit.android.tilos.com.utils.Finals;
import pontezit.android.tilos.com.utils.MusicUtils;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.widget.RemoteViews;

/**
 * Simple widget to show currently playing album art along
 * with play/pause and next track buttons.  
 */
public class AppWidgetOneProvider extends AppWidgetProvider {
    static final String TAG = AppWidgetOneProvider.class.getName();
    
    public static final String CMDAPPWIDGETUPDATE = "appwidgetupdate";

    private static AppWidgetOneProvider sInstance;
    
    public static synchronized AppWidgetOneProvider getInstance() {
        if (sInstance == null) {
            sInstance = new AppWidgetOneProvider();
        }
        return sInstance;
    }

    private long mCoverArtId = -1;
    
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        defaultAppWidget(context, appWidgetIds);
        
        // Send broadcast intent to any running MediaPlaybackService so it can
        // wrap around with an immediate update.
        Intent updateIntent = new Intent(MediaPlaybackService.SERVICECMD);
        updateIntent.putExtra(MediaPlaybackService.CMDNAME,
                AppWidgetOneProvider.CMDAPPWIDGETUPDATE);
        updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        updateIntent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        context.sendBroadcast(updateIntent);
    }
    
    /**
     * Initialize given widgets to default state, where we launch Tilos on default click
     * and hide actions if service not running.
     */
    private void defaultAppWidget(Context context, int[] appWidgetIds) {
        final RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.appwidget_one);

        views.setViewVisibility(R.id.title, View.INVISIBLE);
        views.setViewVisibility(R.id.artist, View.INVISIBLE);

        linkButtons(context, views, false /* not playing */);
        pushUpdate(context, appWidgetIds, views);
    }
    
    private void pushUpdate(Context context, int[] appWidgetIds, RemoteViews views) {
        // Update specific list of appWidgetIds if given, otherwise default to all
        final AppWidgetManager gm = AppWidgetManager.getInstance(context);
        if (appWidgetIds != null) {
            gm.updateAppWidget(appWidgetIds, views);
        } else {
            gm.updateAppWidget(new ComponentName(context, this.getClass()), views);
        }
    }
    
    /**
     * Check against {@link AppWidgetManager} if there are any instances of this widget.
     */
    private boolean hasInstances(Context context) {
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                new ComponentName(context, this.getClass()));
        return (appWidgetIds.length > 0);
    }

    /**
     * Handle a change notification coming over from {@link MediaPlaybackService}
     */
    public void notifyChange(MediaPlaybackService service, String what) {
        if (hasInstances(service)) {
            if (MediaPlaybackService.META_CHANGED.equals(what) ||
            			MediaPlaybackService.PLAYSTATE_CHANGED.equals(what) ||
            				MediaPlaybackService.PLAYER_CLOSED.equals(what)) {
                performUpdate(service, null, what);
            }
        }
    }
    
    /**
     * Update all active widget instances by pushing changes 
     */
    public void performUpdate(MediaPlaybackService service, int[] appWidgetIds, String what) {
    	final Resources res = service.getResources();
        final RemoteViews views = new RemoteViews(service.getPackageName(), R.layout.appwidget_one);
        
        if (what.equals(MediaPlaybackService.PLAYER_CLOSED)) {
        	defaultAppWidget(service, appWidgetIds);
        } else {
        	CharSequence trackName = service.getReadableTime();
        	CharSequence artistName = service.getShowName();
        	//CharSequence errorState = null;

        	if (trackName == null || trackName.equals(Media.UNKNOWN_STRING)) {
                if(service.getPath() == Finals.getLiveHiUrl()){
        		    trackName = res.getText(R.string.live_stream);
                    //views.setViewVisibility(R.id.control_previous, View.INVISIBLE);
                    views.setViewVisibility(R.id.control_next, View.INVISIBLE);
                }else{
                    trackName = res.getText(R.string.archive);
                    views.setViewVisibility(R.id.control_next, View.VISIBLE);
                }
        	}
        		
        	if (artistName == null || artistName.equals(Media.UNKNOWN_STRING)) {
        		artistName = service.getMediaUri();
        	}
            if(service.getPath() == Finals.getLiveHiUrl()){
                views.setViewVisibility(R.id.control_next, View.GONE);
            }else{
                views.setViewVisibility(R.id.control_next, View.VISIBLE);
            }
        	// Show media info
        	views.setViewVisibility(R.id.title, View.VISIBLE);
        	views.setTextViewText(R.id.title, trackName);
        	views.setViewVisibility(R.id.artist, View.VISIBLE);
        	views.setTextViewText(R.id.artist, artistName);
        	
            // Set correct drawable for pause state
            final boolean playing = service.isPlaying();
            if (playing) {
                views.setImageViewResource(R.id.control_play, android.R.drawable.ic_media_pause);
            } else {
                views.setImageViewResource(R.id.control_play, android.R.drawable.ic_media_play);
            }
            
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap b = BitmapFactory.decodeStream(
                    service.getResources().openRawResource(R.drawable.albumart_mp_unknown_widget), null, opts);
            
            views.setImageViewBitmap(R.id.coverart, b);
            
        	long id = service.getAudioId();
        	
        	if (id != mCoverArtId) {
        		//MusicUtils.clearWidgetArtCache();
        	}

            // Link actions buttons to intents
            linkButtons(service, views, true);
            pushUpdate(service, appWidgetIds, views);
        }
    }
    
    /**
     * Link up various button actions using  PendingIntents.
     * 
     * @param playerActive True if player is active in background, which means
     *            widget click will launch link MediaPlayerActivity,
     *            otherwise we launch UriListActivity.
     */
    private void linkButtons(Context context, RemoteViews views, boolean playerActive) {
        // Connect up various buttons and touch events
        Intent intent;
        PendingIntent pendingIntent;
        
        final ComponentName serviceName = new ComponentName(context, MediaPlaybackService.class);

        intent = new Intent(context, MediaPlayerActivity.class);
        pendingIntent = PendingIntent.getActivity(context,
                0 /* no requestCode */, intent, 0 /* no flags */);
        views.setOnClickPendingIntent(R.id.appwidget_two, pendingIntent);

        
        intent = new Intent(MediaPlaybackService.PREVIOUS_ACTION);
        intent.setComponent(serviceName);
        pendingIntent = PendingIntent.getService(context,
                0 /* no requestCode */, intent, 0 /* no flags */);
        views.setOnClickPendingIntent(R.id.control_previous, pendingIntent);
        
        intent = new Intent(MediaPlaybackService.TOGGLEPAUSE_ACTION);
        intent.setComponent(serviceName);
        pendingIntent = PendingIntent.getService(context,
                0 /* no requestCode */, intent, 0 /* no flags */);
        views.setOnClickPendingIntent(R.id.control_play, pendingIntent);
        
        intent = new Intent(MediaPlaybackService.NEXT_ACTION);
        intent.setComponent(serviceName);
        pendingIntent = PendingIntent.getService(context,
                0 /* no requestCode */, intent, 0 /* no flags */);
        views.setOnClickPendingIntent(R.id.control_next, pendingIntent);
    }
}
