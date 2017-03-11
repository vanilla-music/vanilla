/*
 * Copyright (C) 2016-2017 Adrian Ulrich <adrian@blinkenlights.ch>
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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MediaLibraryBackend extends SQLiteOpenHelper {
	/**
	 * Enables or disables debugging
	 */
	private static final boolean DEBUG = false;
	/**
	 * The database version we are using
	 */
	private static final int DATABASE_VERSION = 20170312;
	/**
	 * on-disk file to store the database
	 */
	private static final String DATABASE_NAME = "media-library.db";
	/**
	 * Regexp to detect genre queries which we can optimize
	 */
	private static final Pattern sQueryMatchGenreSearch = Pattern.compile("(^|.+ )"+MediaLibrary.GenreSongColumns._GENRE_ID+"=(\\d+)$");
	/**
	 * Regexp to detect costy artist_id queries which we can optimize
	 */
	private static final Pattern sQueryMatchArtistSearch = Pattern.compile("(^|.+ )"+MediaLibrary.ContributorColumns.ARTIST_ID+"=(\\d+)$");
	/**
	 * Regexp to detect costy albumartist_id queries which we can optimize
	 */
	private static final Pattern sQueryMatchAlbArtistSearch = Pattern.compile("(^|.+ )"+MediaLibrary.ContributorColumns.ALBUMARTIST_ID+"=(\\d+)$");
	/**
	 * Regexp to detect costy composer_id queries which we can optimize
	 */
	private static final Pattern sQueryMatchComposerSearch = Pattern.compile("(^|.+ )"+MediaLibrary.ContributorColumns.COMPOSER_ID+"=(\\d+)$");

	/**
	* Constructor for the MediaLibraryBackend helper
	*
	* @param context the context to use
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
		MediaSchema.upgradeDatabaseSchema(dbh, oldVersion);
	}

	/**
	 * Returns the modification time of a song, 0 if the song does not exist
	 *
	 * @param id the song id to query
	 * @return the modification time of this song
	 */
	long getSongMtime(long id) {
		long mtime = 0;
		Cursor cursor = query(false, MediaLibrary.TABLE_SONGS, new String[]{ MediaLibrary.SongColumns.MTIME }, MediaLibrary.SongColumns._ID+"="+Long.toString(id), null, null, null, null, "1");
		if (cursor.moveToFirst())
			mtime = cursor.getLong(0);
		cursor.close();
		return mtime;
	}

	/**
	 * Simple interface to set and get preference values
	 *
	 * @param stringKey the key to use
	 * @param newVal the value to set
	 *
	 * Note: The new value will only be set if it is >= 0
	 *       Lookup failures will return 0
	 */
	int getSetPreference(String stringKey, int newVal) {
		int oldVal = 0; // this is returned if we found nothing
		int key = Math.abs(stringKey.hashCode());
		SQLiteDatabase dbh = getWritableDatabase();

		Cursor cursor = dbh.query(MediaLibrary.TABLE_PREFERENCES, new String[] { MediaLibrary.PreferenceColumns.VALUE }, MediaLibrary.PreferenceColumns.KEY+"="+key, null, null, null, null, null);
		if (cursor.moveToFirst()) {
			oldVal = cursor.getInt(0);
		}
		cursor.close();

		if (newVal >= 0 && newVal != oldVal) {
			dbh.execSQL("INSERT OR REPLACE INTO "+MediaLibrary.TABLE_PREFERENCES+" ("+MediaLibrary.PreferenceColumns.KEY+", "+MediaLibrary.PreferenceColumns.VALUE+") "
			            +" VALUES("+key+", "+newVal+")");
		}
		return oldVal;
	}

	/**
	 * Wrapper for SQLiteDatabse.delete() function
	 *
	 * @param table the table to delete data from
	 * @param whereClause the selection
	 * @param whereArgs arguments to selection
	 * @return the number of affected rows
	 */
	int delete(String table, String whereClause, String[] whereArgs) {
		SQLiteDatabase dbh = getWritableDatabase();
		return dbh.delete(table, whereClause, whereArgs);
	}

	/**
	 * Wrapper for SQLiteDatabase.update() function
	 *
	 * @param table the table to update
	 * @param values the data to set / modify
	 * @param whereClause the selection
	 * @param whereArgs arguments to selection
	 * @return the number of affected rows
	 */
	int update (String table, ContentValues values, String whereClause, String[] whereArgs) {
		SQLiteDatabase dbh = getWritableDatabase();
		return dbh.update(table, values, whereClause, whereArgs);
	}

	/**
	 * Wrapper for SQLiteDatabase.execSQL() function
	 *
	 * @param sql the raw sql string
	 */
	void execSQL(String sql) {
		getWritableDatabase().execSQL(sql);
	}

	/**
	 * Wrapper for SQLiteDatabase.insert() function
	 *
	 * @param table the table to insert data to
	 * @param nullColumnHack android hackery (see SQLiteDatabase documentation)
	 * @param values the values to insert
	 */
	long insert (String table, String nullColumnHack, ContentValues values) {
		long result = -1;
		try {
			result = getWritableDatabase().insertOrThrow(table, nullColumnHack, values);
		} catch (Exception e) {
			// avoid logspam as done by insert()
		}

		return result;
	}

	/**
	 * Purges orphaned entries from the media library
	 *
	 * @param purgeUserData also delete user data, such as playlists if true
	 */
	void cleanOrphanedEntries(boolean purgeUserData) {
		SQLiteDatabase dbh = getWritableDatabase();
		dbh.execSQL("DELETE FROM "+MediaLibrary.TABLE_ALBUMS+" WHERE "+MediaLibrary.AlbumColumns._ID+" NOT IN (SELECT "+MediaLibrary.SongColumns.ALBUM_ID+" FROM "+MediaLibrary.TABLE_SONGS+");");
		dbh.execSQL("DELETE FROM "+MediaLibrary.TABLE_GENRES_SONGS+" WHERE "+MediaLibrary.GenreSongColumns.SONG_ID+" NOT IN (SELECT "+MediaLibrary.SongColumns._ID+" FROM "+MediaLibrary.TABLE_SONGS+");");
		dbh.execSQL("DELETE FROM "+MediaLibrary.TABLE_GENRES+" WHERE "+MediaLibrary.GenreColumns._ID+" NOT IN (SELECT "+MediaLibrary.GenreSongColumns._GENRE_ID+" FROM "+MediaLibrary.TABLE_GENRES_SONGS+");");
		dbh.execSQL("DELETE FROM "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+" WHERE "+MediaLibrary.ContributorSongColumns.SONG_ID+" NOT IN (SELECT "+MediaLibrary.SongColumns._ID+" FROM "+MediaLibrary.TABLE_SONGS+");");
		dbh.execSQL("DELETE FROM "+MediaLibrary.TABLE_CONTRIBUTORS+" WHERE "+MediaLibrary.ContributorColumns._ID+" NOT IN (SELECT "+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID+" FROM "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+");");

		if (purgeUserData) {
			dbh.execSQL("DELETE FROM "+MediaLibrary.TABLE_PLAYLISTS_SONGS+" WHERE "+MediaLibrary.PlaylistSongColumns.SONG_ID+" NOT IN (SELECT "+MediaLibrary.SongColumns._ID+" FROM "+MediaLibrary.TABLE_SONGS+");");
		}
	}

	/**
	 * Wrapper for SQLiteDatabase.insert() function working in one transaction
	 *
	 * @param table the table to insert data to
	 * @param nullColumnHack android hackery (see SQLiteDatabase documentation)
	 * @param valuesList an array list of ContentValues to insert
	 * @return the number of inserted rows
	 */
	int bulkInsert (String table, String nullColumnHack, ArrayList<ContentValues> valuesList) {
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

		return count;
	}

	/**
	 * Wrappr for SQLiteDatabase.query() function
	 */
	Cursor query (boolean distinct, String table, String[] columns, String selection, String[] selectionArgs, String groupBy, String having, String orderBy, String limit) {

		if (MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS_HUGE.equals(table)) {
			Log.v("VanillaMusic", "+++ warning : using HUGE table in genquery!");
		}

		if (selection != null) {
			if (MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS.equals(table)) {
				// artist matches in the song-view are costy: try to give sqlite a hint
				String[] contributorMatch = extractVirtualColumn(selection);
				if (contributorMatch != null) {
					selection = contributorMatch[0];
					final String contributorId = contributorMatch[1];
					final String contributorRole = contributorMatch[2];

					selection += MediaLibrary.SongColumns._ID+" IN (SELECT "+MediaLibrary.ContributorSongColumns.SONG_ID+" FROM "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+" WHERE "
					          + MediaLibrary.ContributorSongColumns.ROLE+"="+contributorRole+" AND "+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID+"="+contributorId+")";
				}
			}

			if (MediaLibrary.VIEW_ALBUMS_ARTISTS.equals(table)) {
				// looking up artists by albums will magically return every album where this
				// artist has at least one item (while still using the primary_artist_id as the artist key)
				String[] contributorMatch = extractVirtualColumn(selection);
				if (contributorMatch != null) {
					selection = contributorMatch[0];
					final String contributorId = contributorMatch[1];
					final String contributorRole = contributorMatch[2];

					selection += MediaLibrary.SongColumns._ID+" IN (SELECT DISTINCT "+MediaLibrary.SongColumns.ALBUM_ID+" FROM "+MediaLibrary.TABLE_SONGS+" WHERE "
					          + MediaLibrary.SongColumns._ID+" IN (SELECT "+MediaLibrary.ContributorSongColumns.SONG_ID+" FROM "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+" WHERE "
					          + MediaLibrary.ContributorSongColumns.ROLE+"="+contributorRole+" AND "+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID+"="+contributorId+"))";
				}
			}

			// Genre queries are a special beast: 'optimize' all of them
			Matcher genreMatch = sQueryMatchGenreSearch.matcher(selection);
			if (genreMatch.matches()) {
				selection = genreMatch.group(1); // keep the non-genre search part of the query
				final String genreId = genreMatch.group(2); // and extract the searched genre id
				final String songsQuery = buildSongIdFromGenreSelect(genreId);

				if(table.equals(MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS)      ||
				   table.equals(MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS_HUGE) ) {
					selection += MediaLibrary.SongColumns._ID+" IN ("+songsQuery+") ";
				}

				if (table.equals(MediaLibrary.VIEW_ALBUMS_ARTISTS)) {
					selection += MediaLibrary.AlbumColumns._ID+" IN ("+
						buildSongIdFromGenreSelect(MediaLibrary.SongColumns.ALBUM_ID, MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS, songsQuery)+") ";
				}

				if (table.equals(MediaLibrary.VIEW_ARTISTS)) {
					selection += MediaLibrary.ContributorColumns.ARTIST_ID+" IN ("+
						buildSongIdFromGenreSelect(MediaLibrary.ContributorColumns.ARTIST_ID, MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS, songsQuery)+") ";
				}

				if (table.equals(MediaLibrary.VIEW_ALBUMARTISTS)) {
					selection += MediaLibrary.ContributorColumns.ALBUMARTIST_ID+" IN ("+
						buildSongIdFromGenreSelect(MediaLibrary.ContributorColumns.ALBUMARTIST_ID, MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS_HUGE, songsQuery)+") ";
					Log.v("VanillaMusic", "+++ warning: huge genrequery for albumartist!");
				}

				if (table.equals(MediaLibrary.VIEW_COMPOSERS)) {
					selection += MediaLibrary.ContributorColumns.COMPOSER_ID+" IN ("+
						buildSongIdFromGenreSelect(MediaLibrary.ContributorColumns.COMPOSER_ID, MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS_HUGE, songsQuery)+") ";
					Log.v("VanillaMusic", "+++ warning: huge genrequery composer!");
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
	 * Detects queries for artists, composers and albumartists and returns the
	 * role of the contributor.
	 *
	 * @param sql the raw sql query
	 * @return String[]{ sql-part, contributor-id, contributor-role }
	 */
	private String[] extractVirtualColumn(String sql) {
		final Pattern[] pattern = new Pattern[]{ sQueryMatchArtistSearch, sQueryMatchComposerSearch, sQueryMatchAlbArtistSearch };
		final int[] roles = { MediaLibrary.ROLE_ARTIST, MediaLibrary.ROLE_COMPOSER, MediaLibrary.ROLE_ALBUMARTIST };

		for (int i=0; i < roles.length; i++) {
			Matcher matcher = pattern[i].matcher(sql);
			if (matcher.matches()) {
				return new String[]{ matcher.group(1), matcher.group(2), String.format("%d", roles[i]) };
			}
		}
		return null;
	}

	/**
	 * Returns a select query to get all songs from a genre
	 *
	 * @param genreId the id to query as a string
	 * @return an SQL string which should return song id's for the queried genre
	 */
	private String buildSongIdFromGenreSelect(String genreId) {
		return "SELECT "+MediaLibrary.GenreSongColumns.SONG_ID+" FROM "+MediaLibrary.TABLE_GENRES_SONGS+" WHERE "
		                +MediaLibrary.GenreSongColumns._GENRE_ID+"="+genreId+" GROUP BY "+MediaLibrary.GenreSongColumns.SONG_ID;
	}

	/**
	 * Returns a select query to get artists or albums from a genre
	 *
	 * @param target the target to query
	 * @param table the table to query
	 * @param genreSelect the select string generated by buildSongIdFromGenreSelect
	 * @return an SQL string
	 */
	private String buildSongIdFromGenreSelect(String target, String table, String genreSelect) {
		return "SELECT "+target+" FROM "+ table +" WHERE "
		                +MediaLibrary.SongColumns._ID+" IN ("+genreSelect+") GROUP BY "+target;
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
			dryRun.close();
		}
		long tookMs = System.currentTimeMillis() - startAt;
		Log.v(LT, "--- finished in "+tookMs+" ms with count="+results);
	}

}
