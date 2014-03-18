package pontezit.android.tilos.com.utils;

import java.util.ArrayList;
import java.util.Map;

import android.content.Context;
import android.content.SharedPreferences;

public class PreferencesHelper{
	
	public SharedPreferences sp;
	private LogHelper logHelper;
	
	public PreferencesHelper(String prefsGroup, Context context){
		logHelper = new LogHelper();
		sp = context.getSharedPreferences(prefsGroup, 0);
		logHelper.Log("SharedPreferencesHelper Created", 3);
		return;
	}
	
	public void putString(String key, String value){
		SharedPreferences.Editor editor = sp.edit();
		editor.putString(key, value);
		editor.commit();
		logHelper.Log("String added to SharedPreferences", 3);
		return;
	}
	
	public void putInt(String key, int value){
		SharedPreferences.Editor editor = sp.edit();
		editor.putInt(key, value);
		editor.commit();
		logHelper.Log("Integer added to SharedPreferences", 3);
		return;
	}
	
	public void putLong(String key, long value){
		SharedPreferences.Editor editor = sp.edit();
		editor.putLong(key, value);
		editor.commit();
		logHelper.Log("Integer added to SharedPreferences", 3);
		return;
	}
	
	public void putFloat(String key, float value){
		SharedPreferences.Editor editor = sp.edit();
		editor.putFloat(key, value);
		editor.commit();
		logHelper.Log("Integer added to SharedPreferences", 3);
		return;
	}
	
	public void putBoolean(String key, boolean value){
		SharedPreferences.Editor editor = sp.edit();
		editor.putBoolean(key, value);
		editor.commit();
		logHelper.Log("Boolean added to SharedPreferences", 3);
		return;
	}
	
	public int getPreferencesLength(){
		Map<String,?> preferences = sp.getAll();
		return preferences.size();
	}
	
	public ArrayList<Integer> getAllPreferences(){
		ArrayList<Integer> favIds = new ArrayList<Integer>();
		Map<String, ?> preferences = sp.getAll();
		Object[] keySet = preferences.keySet().toArray();
		
		for(int i=0; i<keySet.length; i++){
			favIds.add(Integer.parseInt(keySet[i].toString()));
			logHelper.Log("Ez a kedvenc id-ja:"+keySet[i].toString(), 3);
		}
		
		return favIds;
	}
	
	public void remove(String key){
		SharedPreferences.Editor editor = sp.edit();
		editor.remove(key);
		editor.commit();
		
		return;
	}
	
	public void removeAll(){
		SharedPreferences.Editor editor = sp.edit();
		editor.clear();
		editor.commit();
		
		return;
	}
	
}