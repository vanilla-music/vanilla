/*
 * Copyright (C) 2016 Adrian Ulrich <adrian@blinkenlights.ch>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>. 
 */

package ch.blinkenlights.android.medialibrary;

import android.content.Context;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.ContentObserver;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.Cursor;
import android.util.Log;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MediaLibraryBackend extends SQLiteOpenHelper {
	/**
	 * Enables or disables debugging
	 */
	private static final boolean DEBUG = true;
	/**
	 * The database version we are using
	 */
	private static final int DATABASE_VERSION = 1;
	/**
	 * on-disk file to store the database
	 */
	private static final String DATABASE_NAME = "media-library.db";
	/**
	 * The tag to use for log messages
	 */
	private static final String TAG = "VanillaMediaLibraryBackend";
	/**
	 * Regexp to detect genre queries which we can optimize
	 */
	private static final Pattern sQueryMatchGenreSearch = Pattern.compile("(^|.+ )"+MediaLibrary.GenreSongColumns._GENRE_ID+"=(\\d+)$");
	/**
	 * Regexp to detect costy artist_id queries which we can optimize
	 */
	private static final Pattern sQueryMatchArtistSearch = Pattern.compile("(^|.+ )"+MediaLibrary.ContributorColumns.ARTIST_ID+"=(\\d+)$");
	/**
	 * A list of registered content observers
	 */
	private ContentObserver mContentObserver;

	/**
	* Constructor for the MediaLibraryBackend helper
	*
	* @param Context the context to use
	*/
	MediaLibraryBackend(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}

	/**
	 * Called when database does not exist
	 *
	 * @param dbh the writeable database handle
	 */
	@Override
	public void onCreate(SQLiteDatabase dbh) {
		MediaSchema.createDatabaseSchema(dbh);
	}

	/**
	 * Called when the existing database
	 * schema is outdated
	 *
	 * @param dbh the writeable database handle
	 * @param oldVersion the current version in use
	 * @param newVersion the target version
	 */
	@Override
	public void onUpgrade(SQLiteDatabase dbh, int oldVersion, int newVersion) {
	}

	/**
	 * Returns true if given song id is already present in the library
	 *
	 * @param id the song id to query
	 * @return true if a song with given id exists
	 */
	public boolean isSongExisting(long id) {
		long count = DatabaseUtils.queryNumEntries(getReadableDatabase(), MediaLibrary.TABLE_SONGS, MediaLibrary.SongColumns._ID+"=?", new String[]{""+id});
		return count != 0;
	}

	/**
	 * Registers a new observer which we call on database changes
	 *
	 * @param observer the observer to register
	 */
	public void registerContentObserver(ContentObserver observer) {
		if (mContentObserver == null) {
			mContentObserver = observer;
		} else {
			throw new IllegalStateException("ContentObserver was already registered");
		}
	}

	/**
	 * Sends a callback to the registered observer
	 */
	private void notifyObserver() {
		if (mContentObserver != null)
			mContentObserver.onChange(true);
	}


	/**
	 * Wrapper for SQLiteDatabse.delete() function
	 *
	 * @param table the table to delete data from
	 * @param whereClause the selection
	 * @param whereArgs arguments to selection
	 * @return the number of affected rows
	 */
	public int delete(String table, String whereClause, String[] whereArgs) {
		SQLiteDatabase dbh = getWritableDatabase();
		int res = dbh.delete(table, whereClause, whereArgs);
		if (res > 0) {
			cleanOrphanedEntries();
			notifyObserver();
		}
		return res;
	}

	/**
	 * Wrapper for SQLiteDatabase.update() function
	 *
	 * @param table the table to update
	 * @param values the data to set / modify
	 * @param whereClause the selection
	 * @param whereArgs arguments to selection
	 * @param userVisible controls if we shall call notifyObserver() to refresh the UI
	 * @return the number of affected rows
	 */
	public int update (String table, ContentValues values, String whereClause, String[] whereArgs, boolean userVisible) {
		SQLiteDatabase dbh = getWritableDatabase();
		int res = dbh.update(table, values, whereClause, whereArgs);
		if (res > 0 && userVisible == true) {
			// Note: we are not running notifyObserver for performance reasons here
			// Code which changes relations should just delete + re-insert data
			notifyObserver();
		}
		return res;
	}

	/**
	 * Wrapper for SQLiteDatabase.insert() function
	 *
	 * @param table the table to insert data to
	 * @param nullColumnHack android hackery (see SQLiteDatabase documentation)
	 * @param values the values to insert
	 */
	public long insert (String table, String nullColumnHack, ContentValues values) {
		long result = -1;
		try {
			result = getWritableDatabase().insertOrThrow(table, nullColumnHack, values);
		} catch (Exception e) {
			// avoid logspam as done by insert()
		}

		if (result != -1)
			notifyObserver();
		return result;
	}

	/**
	 * Wrapper for SQLiteDatabase.insert() function working in one transaction
	 *
	 * @param table the table to insert data to
	 * @param nullColumnHack android hackery (see SQLiteDatabase documentation)
	 * @param valuesList an array list of ContentValues to insert
	 * @return the number of inserted rows
	 */
	public int bulkInsert (String table, String nullColumnHack, ArrayList<ContentValues> valuesList) {
		SQLiteDatabase dbh = getWritableDatabase();

		int count = 0;

		dbh.beginTransactionNonExclusive();
		try {
			for(ContentValues values : valuesList) {
				try {
					long result = dbh.insertOrThrow(table, nullColumnHack, values);
					if (result > 0)
						count++;
				} catch (Exception e) {
					// avoid logspam
				}
			}
			dbh.setTransactionSuccessful();
		} finally {
			dbh.endTransaction();
		}

		if (count > 0)
			notifyObserver();

		return count;
	}

	/**
	 * Wrappr for SQLiteDatabase.query() function
	 */
	public Cursor query (boolean distinct, String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {

		if (selection != null) {
			if (MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS.equals(table)) {
				// artist matches in the song-view are costy: try to give sqlite a hint
				Matcher artistMatch = sQueryMatchArtistSearch.matcher(selection);
				if (artistMatch.matches()) {
					selection = artistMatch.group(1);
					final String artistId = artistMatch.group(2);

					selection += MediaLibrary.SongColumns._ID+" IN (SELECT "+MediaLibrary.ContributorSongColumns.SONG_ID+" FROM "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+" WHERE "
					          + MediaLibrary.ContributorSongColumns.ROLE+"=0 AND "+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID+"="+artistId+")";
				}
			}

			// Genre queries are a special beast: 'optimize' all of them
			Matcher genreMatch = sQueryMatchGenreSearch.matcher(selection);
			if (genreMatch.matches()) {
				selection = genreMatch.group(1); // keep the non-genre search part of the query
				final String genreId = genreMatch.group(2); // and extract the searched genre id
				final String songsQuery = buildSongIdFromGenreSelect(genreId);

				if(table.equals(MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS)) {
					selection += MediaLibrary.SongColumns._ID+" IN ("+songsQuery+") ";
				}

				if (table.equals(MediaLibrary.VIEW_ARTISTS)) {
					selection += MediaLibrary.ContributorColumns.ARTIST_ID+" IN ("+ buildSongIdFromGenreSelect(MediaLibrary.ContributorColumns.ARTIST_ID, songsQuery)+") ";
				}

				if (table.equals(MediaLibrary.VIEW_ALBUMS_ARTISTS)) {
					selection += MediaLibrary.AlbumColumns._ID+" IN ("+ buildSongIdFromGenreSelect(MediaLibrary.SongColumns.ALBUM_ID, songsQuery)+") ";
				}
			}
		}

		if (DEBUG)
			debugQuery(distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit);

		Cursor cursor = getReadableDatabase().query(distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit);
		if (cursor != null) {
			// Hold on! This is not some kind of black magic - it makes '''sense''':
			// SQLites count() performance is pretty poor, but most queries will call getCount() during their
			// lifetime anyway - unfortunately this might happen in the main thread, causing some lag.
			// Androids SQLite class caches the result of getCount() calls, so we are going to run it
			// here as we are (hopefully!) in a background thread anyway.
			cursor.getCount();
		}
		return cursor;
	}

	/**
	 * Returns a select query to get all songs from a genre
	 *
	 * @param genreId the id to query as a string
	 * @return an SQL string which should return song id's for the queried genre
	 */
	private String buildSongIdFromGenreSelect(String genreId) {
		final String query = "SELECT "+MediaLibrary.GenreSongColumns.SONG_ID+" FROM "+MediaLibrary.TABLE_GENRES_SONGS+" WHERE "
		                     +MediaLibrary.GenreSongColumns._GENRE_ID+"="+genreId+" GROUP BY "+MediaLibrary.GenreSongColumns.SONG_ID;
		return query;
	}

	/**
	 * Returns a select query to get artists or albums from a genre
	 *
	 * @param target the target to query
	 * @param genreSelect the select string generated by buildSongIdFromGenreSelect
	 * @return an SQL string
	 */
	private String buildSongIdFromGenreSelect(String target, String genreSelect) {
		final String query = "SELECT "+target+" FROM "+MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS+" WHERE "
		                    +MediaLibrary.SongColumns._ID+" IN ("+genreSelect+") GROUP BY "+target;
		return query;
	}

	/**
	 * Purges orphaned entries from the media library
	 */
	private void cleanOrphanedEntries() {
		SQLiteDatabase dbh = getWritableDatabase();
		dbh.execSQL("DELETE FROM "+MediaLibrary.TABLE_ALBUMS+" WHERE "+MediaLibrary.AlbumColumns._ID+" NOT IN (SELECT "+MediaLibrary.SongColumns.ALBUM_ID+" FROM "+MediaLibrary.TABLE_SONGS+");");
		dbh.execSQL("DELETE FROM "+MediaLibrary.TABLE_GENRES_SONGS+" WHERE "+MediaLibrary.GenreSongColumns.SONG_ID+" NOT IN (SELECT "+MediaLibrary.SongColumns._ID+" FROM "+MediaLibrary.TABLE_SONGS+");");
		dbh.execSQL("DELETE FROM "+MediaLibrary.TABLE_GENRES+" WHERE "+MediaLibrary.GenreColumns._ID+" NOT IN (SELECT "+MediaLibrary.GenreSongColumns._GENRE_ID+" FROM "+MediaLibrary.TABLE_GENRES_SONGS+");");
		dbh.execSQL("DELETE FROM "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+" WHERE "+MediaLibrary.ContributorSongColumns.SONG_ID+" NOT IN (SELECT "+MediaLibrary.SongColumns._ID+" FROM "+MediaLibrary.TABLE_SONGS+");");
		dbh.execSQL("DELETE FROM "+MediaLibrary.TABLE_CONTRIBUTORS+" WHERE "+MediaLibrary.ContributorColumns._ID+" NOT IN (SELECT "+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID+" FROM "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+");");
	}

	/**
	 * Debug function to print and benchmark queries
	 */
	private void debugQuery(boolean distinct, String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {
		final String LT = "VanillaMusicSQL";
		Log.v(LT, "---- start query ---");
		Log.v(LT, "SELECT");
		for (String c : columns) {
			Log.v(LT, "   "+c);
		}
		Log.v(LT, "FROM "+table+" WHERE "+selection+" ");
		if (selectionArgs != null) {
			Log.v(LT, " /* with args: ");
			for (String a : selectionArgs) {
				Log.v(LT, a+", ");
			}
			Log.v(LT, " */");
		}
		Log.v(LT, " GROUP BY "+groupBy+" HAVING "+having+" ORDER BY "+orderBy+" LIMIT "+limit);

		Log.v(LT, "DBH = "+getReadableDatabase());

		Cursor dryRun = getReadableDatabase().query(distinct, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit);
		long results = 0;
		long startAt = System.currentTimeMillis();
		if (dryRun != null) {
			while(dryRun.moveToNext()) {
				results++;
			}
		}
		dryRun.close();
		long tookMs = System.currentTimeMillis() - startAt;
		Log.v(LT, "--- finished in "+tookMs+" ms with count="+results);
	}

}
