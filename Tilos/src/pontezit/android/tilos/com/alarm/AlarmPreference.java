/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2010 William Seemann
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

package pontezit.android.tilos.com.alarm;

import java.util.List;

import pontezit.android.tilos.com.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.net.Uri;
import android.preference.ListPreference;
import android.util.AttributeSet;

/**
 * The RingtonePreference does not have a way to get/set the current ringtone so
 * we override onSaveRingtone and onRestoreRingtone to get the same behavior.
 */
public class AlarmPreference extends ListPreference {

	private int [] mIds;
	
    // Initial value that can be set with the values saved in the database.
    private int mId = 0;
    // New value that will be set if a positive result comes back from the
    // dialog.
    private int mNewId = -1;

	public AlarmPreference(Context context, AttributeSet attrs) {
		super(context, attrs);

		String [] entries = new String[1];
		String [] entryValues = new String[1];

		entries[0] = getContext().getString(R.string.live_stream);
		entryValues[0] = String.valueOf("http://stream.tilos.hu:80/tilos");
        mIds = new int[1];
		mIds[0] = 0;

        setEntries(entries);
        setEntryValues(entryValues);
	}
 
    @Override
    protected void onDialogClosed(boolean positiveResult) {
        CharSequence[] entries = getEntries();
    	
        if (positiveResult) {
            mId = mNewId;
            setSummary(entries[mId]);
            callChangeListener(mId);
        }
    }
	
	@Override
	protected void onPrepareDialogBuilder(AlertDialog.Builder builder) {	
        CharSequence[] entries = getEntries();
        
        builder.setSingleChoiceItems(
        		entries,
        		mId,
                new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						mNewId = which;
					}
				});
	}

	public void setAlertId(int id) {
        CharSequence[] entries = getEntries();
		
		for (int i = 0; i < mIds.length; i++) {
			if (mIds[i] == id) {
				mId = i;
				mNewId = i;
	            setSummary(entries[mId]);
			}
		}
	}
	
    public int getAlertId() {
        return mIds[mId];
    }
}
