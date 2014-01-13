/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2013 William Seemann
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this.getActivity() file except in compliance with the License.
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

import java.util.ArrayList;
import java.util.List;

import pontezit.android.tilos.com.transport.AbsTransport;
import pontezit.android.tilos.com.transport.TransportFactory;
import pontezit.android.tilos.com.utils.LoadingDialog;
import pontezit.android.tilos.com.utils.LoadingDialog.LoadingDialogListener;
import pontezit.android.tilos.com.utils.MusicUtils;
import pontezit.android.tilos.com.utils.OverflowClickListener;
import pontezit.android.tilos.com.utils.RateDialog;
import pontezit.android.tilos.com.utils.UriBeanLoader;

import pontezit.android.tilos.com.R;
import pontezit.android.tilos.com.activity.AddUrlActivity;
import pontezit.android.tilos.com.activity.BluetoothOptionsActivity;
import pontezit.android.tilos.com.activity.StreamEditorActivity;
import pontezit.android.tilos.com.adapter.UrlListAdapter;
import pontezit.android.tilos.com.alarm.Alarm;
import pontezit.android.tilos.com.bean.UriBean;
import pontezit.android.tilos.com.dbutils.StreamDatabase;
import pontezit.android.tilos.com.utils.PreferenceConstants;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v7.widget.PopupMenu;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

import pontezit.android.tilos.com.utils.DetermineActionTask;

public class UrlListFragment extends ListFragment implements
        DetermineActionTask.MusicRetrieverPreparedListener,
				LoadingDialogListener,
				OverflowClickListener,
				LoaderManager.LoaderCallbacks<List<UriBean>> {
	
	public final static String TAG = UrlListFragment.class.getName();	
	
    public static final String UPDATE_LIST = "net.sourceforge.servestream.updatelist";
	
    private final static String LOADING_DIALOG = "loading_dialog";
	private final static String RATE_DIALOG = "rate_dialog";
	
	public static final String ARG_TARGET_URI = "target_uri";
	
	private StreamDatabase mStreamdb = null;
	
	private SharedPreferences mPreferences = null;
    private DetermineActionTask mDetermineActionTask;
    
    private UrlListAdapter mAdapter;
    
    private BrowseIntentListener mListener;
    
    private UriBean mSelectedMenuItem;
    
    private int mId = 0;
    
	@SuppressLint("HandlerLeak")
	private Handler mQueueHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			MusicUtils.addToCurrentPlaylist(UrlListFragment.this.getActivity(), (long[]) msg.obj);
		}
	};
	
    @Override
    public void onAttach(Activity activity) {
    	super.onAttach(activity);
       
        try {
        	mListener = (BrowseIntentListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement BrowseIntentListener");
        }
       
		// connect with streams database and populate list
		mStreamdb = new StreamDatabase(getActivity());
    }
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setRetainInstance(true);
        setHasOptionsMenu(true);
    }
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_uri_list, container, false);
		ListView list = (ListView) view.findViewById(android.R.id.list);
		list.setEmptyView(view.findViewById(android.R.id.empty));
		
		return view;
	}
	
	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		
		mPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
		
		ListView list = getListView();
		list.setOnItemClickListener(new OnItemClickListener() {
			public synchronized void onItemClick(AdapterView<?> parent, View view, int position, long id) {

				UriBean uriBean = (UriBean) parent.getItemAtPosition(position);
				processUri(uriBean.getUri().toString());
			}
		});

		mAdapter = new UrlListAdapter(getActivity(), new ArrayList<UriBean>(), this);
		setListAdapter(mAdapter);
	}
	
	@Override
	public void onActivityCreated (Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		if (getArguments().getString(ARG_TARGET_URI) != null) {
			String targetUri = getArguments().getString(ARG_TARGET_URI);
			processUri(targetUri);
			getArguments().putString("target_uri", null);
		} else {
			// see if the user wants to rate the application after 5 uses
			int rateApplicationFlag = mPreferences.getInt(PreferenceConstants.RATE_APPLICATION_FLAG, 0);
			if (rateApplicationFlag != -1) {
				rateApplicationFlag++;
				Editor ed = mPreferences.edit();
				ed.putInt(PreferenceConstants.RATE_APPLICATION_FLAG, rateApplicationFlag);
				ed.commit();
				if (rateApplicationFlag == 10) {
					showDialog(RATE_DIALOG);
				}
			}
		}
	}
	
	@Override
	public void onStart() {
		super.onStart();
		
		 IntentFilter f = new IntentFilter();
	     f.addAction(UPDATE_LIST);
	     getActivity().registerReceiver(mUpdateListListener, f);
	}
	
	@Override
	public void onResume() {
		super.onResume();
		
		updateList();
	}
	
	@Override
	public void onStop() {
		super.onStop();
		
        getActivity().unregisterReceiver(mUpdateListListener);
	}
	
	@Override
	public void onDetach () {
		super.onDetach();
		
		mStreamdb.close();
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.url_list, menu);
	    super.onCreateOptionsMenu(menu, inflater);
	}
		
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
	    	case (R.id.menu_item_add):
				startActivity(new Intent(getActivity(), AddUrlActivity.class));
				return true;
	        default:
	        	return super.onOptionsItemSelected(item);
	    }
	}
	
	private boolean processUri(String input) {
		Uri uri = TransportFactory.getUri(input);

		if (uri == null) {
			return false;
		}

		UriBean uriBean = TransportFactory.findUri(mStreamdb, uri);
		if (uriBean == null) {
			uriBean = TransportFactory.getTransport(uri.getScheme()).createUri(uri);
			
			AbsTransport transport = TransportFactory.getTransport(uriBean.getProtocol());
			transport.setUri(uriBean);
			if (mPreferences.getBoolean(PreferenceConstants.AUTOSAVE, true) && transport.shouldSave()) {
				mStreamdb.saveUri(uriBean);
				updateList();
			}
		}
		
	    showDialog(LOADING_DIALOG);
	    mDetermineActionTask = new DetermineActionTask(getActivity(), uriBean, this);
	    mDetermineActionTask.execute();
		
		return true;
	}
	
	public void updateList() {
		getLoaderManager().initLoader(mId++, null, this);
	}
	
	private void showUrlNotOpenedToast() {
		Toast.makeText(this.getActivity(), R.string.url_not_opened_message, Toast.LENGTH_SHORT).show();
	}
	
	@Override
	public void onMusicRetrieverPrepared(String action, UriBean uri, long[] list) {
		dismissDialog(LOADING_DIALOG);
		
		if (action.equals(DetermineActionTask.URL_ACTION_UNDETERMINED)) {
			showUrlNotOpenedToast();
		} else if (action.equals(DetermineActionTask.URL_ACTION_BROWSE)) {
			if (mPreferences.getBoolean(PreferenceConstants.AUTOSAVE, true)) {
				mStreamdb.touchUri(uri);
			}
			
			mListener.browseToUri(uri.getScrubbedUri());
		} else if (action.equals(DetermineActionTask.URL_ACTION_PLAY)) {
			if (mPreferences.getBoolean(PreferenceConstants.AUTOSAVE, true)) {
				mStreamdb.touchUri(uri);
			}
			
			MusicUtils.playAll(getActivity(), list, 0);        
		}
	}

	private void showDialog(String tag) {
		// DialogFragment.show() will take care of adding the fragment
		// in a transaction.  We also want to remove any currently showing
		// dialog, so make our own transaction and take care of that here.
		FragmentTransaction ft = getChildFragmentManager().beginTransaction();
		Fragment prev = getChildFragmentManager().findFragmentByTag(tag);
		if (prev != null) {
			ft.remove(prev);
		}

		DialogFragment newFragment = null;

		// Create and show the dialog.
		if (tag.equals(LOADING_DIALOG)) {
			newFragment = LoadingDialog.newInstance(this, getString(R.string.opening_url_message));
		} else if (tag.equals(RATE_DIALOG)) {
			newFragment = RateDialog.newInstance();
		}

		ft.add(0, newFragment, tag);
		ft.commitAllowingStateLoss();
	}

	private void dismissDialog(String tag) {
		FragmentTransaction ft = getChildFragmentManager().beginTransaction();
		DialogFragment prev = (DialogFragment) getChildFragmentManager().findFragmentByTag(tag);
		if (prev != null) {
			prev.dismiss();
			ft.remove(prev);
		}
		ft.commitAllowingStateLoss();
	}
	
	@Override
	public void onLoadingDialogCancelled(DialogFragment dialog) {
		if (mDetermineActionTask != null) {
			mDetermineActionTask.cancel(true);
			mDetermineActionTask = null;
		}
	}
	
	private BroadcastReceiver mUpdateListListener = new BroadcastReceiver() {
		@Override
	    public void onReceive(Context context, Intent intent) {
			updateList();
	    }
	};
	
	// Container Activity must implement this interface
    public interface BrowseIntentListener {
        public void browseToUri(Uri uri);
    }
    
    private void showPopup(View v, UriBean uri) {
    	mSelectedMenuItem = uri;
    	
        PopupMenu popup = new PopupMenu(getActivity(), v);
        Menu menu = popup.getMenu();
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.uri_list_uri_actions, menu);
		BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter == null) {
			menu.removeItem(R.id.menu_autostart_on_bluetooth);
		}
        popup.setOnMenuItemClickListener(mPopupMenuOnMenuItemClickListener);
        popup.show();
    }

    private PopupMenu.OnMenuItemClickListener mPopupMenuOnMenuItemClickListener = new PopupMenu.OnMenuItemClickListener() {

		@Override
		public boolean onMenuItemClick(MenuItem item) {
		    Intent intent;
			
			switch (item.getItemId()) {
				case R.id.menu_item_edit:
					intent = new Intent(getActivity(), StreamEditorActivity.class);
					intent.putExtra(Intent.EXTRA_TITLE, mSelectedMenuItem.getId());
					getActivity().startActivity(intent);
					return true;
				case R.id.menu_item_delete:
					// prompt user to make sure they really want this.getActivity()
					new AlertDialog.Builder(getActivity())
					.setMessage(getString(R.string.url_delete_confirmation_msg, mSelectedMenuItem.getNickname()))
					.setPositiveButton(R.string.confirm_label, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							mStreamdb.deleteUri(mSelectedMenuItem);
							ContentResolver resolver = getActivity().getContentResolver();
							resolver.update(
									Alarm.Columns.CONTENT_URI,
									null, null, new String[] { String.valueOf(mSelectedMenuItem.getId()) });
							updateList();
						}
					})
					.setNegativeButton(R.string.cancel_label, null).create().show();
					return true;
				case R.id.menu_item_add_to_playlist_label:
					MusicUtils.addToCurrentPlaylistFromURL(getActivity(), mSelectedMenuItem, mQueueHandler);
					return true;
				case R.id.menu_item_share:
					String url = mSelectedMenuItem.getUri().toString();
					String appName = getString(R.string.app_name);

					intent = new Intent(Intent.ACTION_SEND);
					intent.setType("text/plain");
					intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.share_signature, url, appName));
					startActivity(Intent.createChooser(intent, getString(R.string.share_label)));
					return true;
				case R.id.menu_autostart_on_bluetooth:
			        SharedPreferences prefs = getActivity().getSharedPreferences(BluetoothOptionsActivity.PREFS_NAME, Context.MODE_PRIVATE);
			        Editor editor = prefs.edit();
			        editor.putString(BluetoothOptionsActivity.PREF_AUTOSTART_STREAM, mSelectedMenuItem.getUri().toString());
			        editor.commit();		
					return true;
				default:
					return false;
		    }
		}
    	
    };
    
	@Override
	public void onClick(View view, UriBean uri) {
		showPopup(view, uri);
	}

	@Override
	public Loader<List<UriBean>> onCreateLoader(int id, Bundle args) {
        // This is called when a new Loader needs to be created.  This
        // sample only has one Loader with no arguments, so it is simple.
        return new UriBeanLoader(getActivity());
	}

	@Override
	public void onLoadFinished(Loader<List<UriBean>> loader, List<UriBean> data) {
		// Set the new data in the adapter.
        mAdapter.clear();
		for (int i = 0; i < data.size(); i++) {
			mAdapter.add(data.get(i));
		}
	}

	@Override
	public void onLoaderReset(Loader<List<UriBean>> loader) {
		// Clear the data in the adapter.
		mAdapter.clear();
	}
}
