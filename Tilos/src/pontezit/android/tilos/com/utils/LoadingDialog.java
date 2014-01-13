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

package pontezit.android.tilos.com.utils;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.Fragment;

public class LoadingDialog extends DialogFragment implements DialogInterface.OnCancelListener {
	
	private String mDialogText = null;
	
    /* The activity that creates an instance of this dialog fragment must
     * implement this interface in order to receive event callbacks.
     * Each method passes the DialogFragment in case the host needs to query it. */
    public interface LoadingDialogListener {
        public void onLoadingDialogCancelled(DialogFragment dialog);
    }
	
    // Use this instance of the interface to deliver action events
    static LoadingDialogListener mListener;
    
    /* Call this to instantiate a new LoadingDialog.
     * @param activity  The activity hosting the dialog, which must implement the
     *                  LoadingDialogListener to receive event callbacks.
     * @returns A new instance of LoadingDialog.
     * @throws  ClassCastException if the host activity does not
     *          implement LoadingDialogListener
     */
    public static LoadingDialog newInstance(Activity activity, String dialogText) {
    	// Verify that the host activity implements the callback interface
        try {
            // Instantiate the LoadingDialogListener so we can send events with it
            mListener = (LoadingDialogListener) activity;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                    + " must implement LoadingDialogListener");
        }
    	LoadingDialog frag = new LoadingDialog();
    	
   	 	// Supply dialog text as an argument.
        Bundle args = new Bundle();
        args.putString("dialog_text", dialogText);
        frag.setArguments(args);
    	
        return frag;
    }
	
    /* Call this to instantiate a new LoadingDialog.
     * @param activity  The activity hosting the dialog, which must implement the
     *                  LoadingDialogListener to receive event callbacks.
     * @returns A new instance of LoadingDialog.
     * @throws  ClassCastException if the host activity does not
     *          implement LoadingDialogListener
     */
    public static LoadingDialog newInstance(Fragment fragment, String dialogText) {
    	// Verify that the host activity implements the callback interface
        try {
            // Instantiate the LoadingDialogListener so we can send events with it
            mListener = (LoadingDialogListener) fragment;
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(fragment.toString()
                    + " must implement LoadingDialogListener");
        }
    	LoadingDialog frag = new LoadingDialog();
    	
   	 	// Supply dialog text as an argument.
        Bundle args = new Bundle();
        args.putString("dialog_text", dialogText);
        frag.setArguments(args);
    	
        return frag;
    }
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mDialogText = getArguments().getString("dialog_text");
    }
    
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use the Builder class for convenient dialog construction
	    ProgressDialog loadingDialog = null;
	    loadingDialog = new ProgressDialog(getActivity());
	    loadingDialog.setMessage(mDialogText);
	    loadingDialog.setCancelable(true);
	    loadingDialog.setOnCancelListener(this);
        return loadingDialog;
    }
    
	@Override
	public void onDestroyView() {
		if (getDialog() != null) {
			getDialog().setDismissMessage(null);
		}
		
	  	super.onDestroyView();
	}
    
    public void onCancel(DialogInterface dialog) {
		mListener.onLoadingDialogCancelled(LoadingDialog.this);
    }
}