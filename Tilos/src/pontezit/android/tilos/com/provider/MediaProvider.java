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

package pontezit.android.tilos.com.provider;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.database.sqlite.SQLiteStatement;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.util.HashMap;

/**
 * Provides access to a database of media files. Each media has a title, the note
 * itself, a creation date and a modified data.
 */
public class MediaProvider extends ContentProvider {
	
    private static final String TAG = MediaProvider.class.getName();

    private static final String DATABASE_NAME = "media.db";
    private static final int DATABASE_VERSION = 2;
    private static final String MEDIA_TABLE_NAME = "media_files";

    private static HashMap<String, String> sMediaProjectionMap;

    private static final int MEDIA = 1;
    private static final int MEDIA_ID = 2;

    private static final UriMatcher sUriMatcher;

    /**
     * This class helps open, create, and upgrade the database file.
     */
    private static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE " + MEDIA_TABLE_NAME + " ("
                    + Media.MediaColumns._ID + " INTEGER PRIMARY KEY,"
                    + Media.MediaColumns.URI + " TEXT,"
                    + Media.MediaColumns.TITLE + " TEXT,"
                    + Media.MediaColumns.ALBUM + " TEXT,"
                    + Media.MediaColumns.ARTIST + " TEXT,"
                    + Media.MediaColumns.DURATION + " INTEGER,"
                    + Media.MediaColumns.TRACK + " TEXT,"
                    + Media.MediaColumns.YEAR + " INTEGER,"
                    + Media.MediaColumns.ARTWORK + " BLOB"
                    + ");");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion + " to "
                    + newVersion + ", which will destroy all old data");
            db.execSQL("DROP TABLE IF EXISTS " + MEDIA_TABLE_NAME);
            onCreate(db);
        }
    }

    private DatabaseHelper mOpenHelper;

    @Override
    public boolean onCreate() {
        mOpenHelper = new DatabaseHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables(MEDIA_TABLE_NAME);

        switch (sUriMatcher.match(uri)) {
        case MEDIA:
            qb.setProjectionMap(sMediaProjectionMap);
            break;

        case MEDIA_ID:
            qb.setProjectionMap(sMediaProjectionMap);
            qb.appendWhere(Media.MediaColumns._ID + "=" + uri.getPathSegments().get(1));
            break;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        // If no sort order is specified use the default
        String orderBy;
        if (TextUtils.isEmpty(sortOrder)) {
            orderBy = Media.MediaColumns.DEFAULT_SORT_ORDER;
        } else {
            orderBy = sortOrder;
        }

        // Get the database and run the query
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor c = qb.query(db, projection, selection, selectionArgs, null, null, orderBy);

        // Tell the cursor what uri to watch, so it knows when its source data changes
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
        case MEDIA:
            return Media.MediaColumns.CONTENT_TYPE;

        case MEDIA_ID:
            return Media.MediaColumns.CONTENT_ITEM_TYPE;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {
        // Validate the requested uri
        if (sUriMatcher.match(uri) != MEDIA) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }

        // Make sure that the fields are all set
        if (values.containsKey(Media.MediaColumns.URI) == false) {
            values.put(Media.MediaColumns.URI, Media.UNKNOWN_STRING);
        }
        
        if (values.containsKey(Media.MediaColumns.TITLE) == false) {
            values.put(Media.MediaColumns.TITLE, Media.UNKNOWN_STRING);
        }
        
        if (values.containsKey(Media.MediaColumns.ALBUM) == false) {
            values.put(Media.MediaColumns.ALBUM, Media.UNKNOWN_STRING);
        }
        
        if (values.containsKey(Media.MediaColumns.ARTIST) == false) {
            values.put(Media.MediaColumns.ARTIST, Media.UNKNOWN_STRING);
        }
        
        if (values.containsKey(Media.MediaColumns.DURATION) == false) {
            values.put(Media.MediaColumns.DURATION, Media.UNKNOWN_INTEGER);
        }
        
        if (values.containsKey(Media.MediaColumns.TRACK) == false) {
            values.put(Media.MediaColumns.TRACK, Media.UNKNOWN_STRING);
        }
        
        if (values.containsKey(Media.MediaColumns.YEAR) == false) {
            values.put(Media.MediaColumns.YEAR, Media.UNKNOWN_INTEGER);
        }
        
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        long rowId = db.insert(MEDIA_TABLE_NAME, Media.MediaColumns.URI, values);
        if (rowId > 0) {
            Uri audioUri = ContentUris.withAppendedId(Media.MediaColumns.CONTENT_URI, rowId);
            getContext().getContentResolver().notifyChange(audioUri, null);
            return audioUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
    	// Validate the requested uri
    	if (sUriMatcher.match(uri) != MEDIA) {
    		throw new IllegalArgumentException("Unknown URI " + uri);
    	}
    	
    	int numInserted = 0;
    	
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        db.beginTransaction();
        	        
        try {
        	//standard SQL insert statement, that can be reused
            SQLiteStatement insert = 
            		db.compileStatement("insert into " + MEDIA_TABLE_NAME 
            				+ " (" + Media.MediaColumns.URI + ","
        	                + Media.MediaColumns.TITLE + ","
        	                + Media.MediaColumns.ALBUM + ","
        	                + Media.MediaColumns.ARTIST + ","
        	                + Media.MediaColumns.DURATION + ","
        	                + Media.MediaColumns.TRACK + ","
        	                + Media.MediaColumns.YEAR + ")"
        	                + " values " + "(?,?,?,?,?,?,?)");
        	
        	for (ContentValues value : values) {
        		// Make sure that the fields are all set
                if (value.containsKey(Media.MediaColumns.URI) == false) {
                	value.put(Media.MediaColumns.URI, Media.UNKNOWN_STRING);
                }
                
                if (value.containsKey(Media.MediaColumns.TITLE) == false) {
                	value.put(Media.MediaColumns.TITLE, Media.UNKNOWN_STRING);
                }
                
                if (value.containsKey(Media.MediaColumns.ALBUM) == false) {
                	value.put(Media.MediaColumns.ALBUM, Media.UNKNOWN_STRING);
                }
                
                if (value.containsKey(Media.MediaColumns.ARTIST) == false) {
                	value.put(Media.MediaColumns.ARTIST, Media.UNKNOWN_STRING);
                }
                
                if (value.containsKey(Media.MediaColumns.DURATION) == false) {
                	value.put(Media.MediaColumns.DURATION, Media.UNKNOWN_INTEGER);
                }
                
                if (value.containsKey(Media.MediaColumns.TRACK) == false) {
                	value.put(Media.MediaColumns.TRACK, Media.UNKNOWN_STRING);
                }
                
                if (value.containsKey(Media.MediaColumns.YEAR) == false) {
                	value.put(Media.MediaColumns.YEAR, Media.UNKNOWN_INTEGER);
                }
        		
                insert.bindString(1, value.getAsString(Media.MediaColumns.URI));
        	    insert.bindString(2, value.getAsString(Media.MediaColumns.TITLE));
        	    insert.bindString(3, value.getAsString(Media.MediaColumns.ALBUM));
        	    insert.bindString(4, value.getAsString(Media.MediaColumns.ARTIST));
        	    insert.bindLong(5, value.getAsInteger(Media.MediaColumns.DURATION));
        	    insert.bindString(6, value.getAsString(Media.MediaColumns.TRACK));
        	    insert.bindLong(7, value.getAsInteger(Media.MediaColumns.YEAR));
        	    insert.execute();
        	    numInserted++;
        	}
            
        	db.setTransactionSuccessful();
        } finally {
        	db.endTransaction();
        }
        
        return numInserted;
    }
    
    @Override
    public int delete(Uri uri, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
        case MEDIA:
            count = db.delete(MEDIA_TABLE_NAME, where, whereArgs);
            break;

        case MEDIA_ID:
            String noteId = uri.getPathSegments().get(1);
            count = db.delete(MEDIA_TABLE_NAME, Media.MediaColumns._ID + "=" + noteId
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    @Override
    public int update(Uri uri, ContentValues values, String where, String[] whereArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int count;
        switch (sUriMatcher.match(uri)) {
        case MEDIA:
            count = db.update(MEDIA_TABLE_NAME, values, where, whereArgs);
            break;

        case MEDIA_ID:
            String noteId = uri.getPathSegments().get(1);
            count = db.update(MEDIA_TABLE_NAME, values, Media.MediaColumns._ID + "=" + noteId
                    + (!TextUtils.isEmpty(where) ? " AND (" + where + ')' : ""), whereArgs);
            break;

        default:
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    static {
        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Media.AUTHORITY, "uris", MEDIA);
        sUriMatcher.addURI(Media.AUTHORITY, "uris/#", MEDIA_ID);

        sMediaProjectionMap = new HashMap<String, String>();
        sMediaProjectionMap.put(Media.MediaColumns._ID, Media.MediaColumns._ID);
        sMediaProjectionMap.put(Media.MediaColumns.URI, Media.MediaColumns.URI);
        sMediaProjectionMap.put(Media.MediaColumns.TITLE, Media.MediaColumns.TITLE);
        sMediaProjectionMap.put(Media.MediaColumns.ALBUM, Media.MediaColumns.ALBUM);
        sMediaProjectionMap.put(Media.MediaColumns.ARTIST, Media.MediaColumns.ARTIST);
        sMediaProjectionMap.put(Media.MediaColumns.DURATION, Media.MediaColumns.DURATION);
        sMediaProjectionMap.put(Media.MediaColumns.TRACK, Media.MediaColumns.TRACK);
        sMediaProjectionMap.put(Media.MediaColumns.YEAR, Media.MediaColumns.YEAR);
        sMediaProjectionMap.put(Media.MediaColumns.ARTWORK, Media.MediaColumns.ARTWORK);
    }
}
