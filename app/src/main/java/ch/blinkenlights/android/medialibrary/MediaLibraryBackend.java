/*
 * Copyright (C) 2016-2018 Adrian Ulrich <adrian@blinkenlights.ch>
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
import android.content.ContentValues;
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
	private static final boolean DEBUG = false;
	/**
	 * The database version we are using
	 */
	private static final int DATABASE_VERSION = 20190210;
	/**
	 * on-disk file to store the database
	 */
	static final String DATABASE_NAME = "media-library.db";
	/**
	 * The magic mtime to use for songs which are in PENDING_DELETION state.
	 * This is NOT 0 as the mtime is always expected to be > 0 for existing rows
	 */
	private static final int PENDING_DELETION_MTIME = 1;
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
	 * Returns the `long' value stored in the column of the given id.
	 *
	 * @param column the column to return of `id'
	 * @param id the song id to query
	 * @return the value of `column'
	 */
	long getColumnFromSongId(String column, long id) {
		long mtime = 0;
		Cursor cursor = query(false, MediaLibrary.TABLE_SONGS, new String[]{ column }, MediaLibrary.SongColumns._ID+"="+Long.toString(id), null, null, null, null, "1");
		if (cursor.moveToFirst())
			mtime = cursor.getLong(0);
		cursor.close();
		return mtime;
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
	 * Marks all songs as 'deleteable' - but doesn't delete them yet.
	 * Calling cleanOrphanedEntries() would take care of the actual deletion.
	 * This is a hacky way to get rid of all songs that a scan didn't touch.
	 */
	void setPendingDeletion() {
		SQLiteDatabase dbh = getWritableDatabase();
		dbh.execSQL("UPDATE "+MediaLibrary.TABLE_SONGS+" SET "+MediaLibrary.SongColumns.MTIME+"="+PENDING_DELETION_MTIME);
	}

	/**
	 * Purges orphaned entries from the media library
	 *
	 * @param fullCleanup also remove orphaned playlist entries and songs marked for deletion.
	 */
	void cleanOrphanedEntries(boolean fullCleanup) {
		SQLiteDatabase dbh = getWritableDatabase();

		// Remove all songs which are marked for deletion and playlist orphaned playlist entries.
		if (fullCleanup) {
			dbh.execSQL("DELETE FROM "+MediaLibrary.TABLE_SONGS+" WHERE "+MediaLibrary.SongColumns.MTIME+"="+PENDING_DELETION_MTIME);
			dbh.execSQL("DELETE FROM "+MediaLibrary.TABLE_PLAYLISTS_SONGS+" WHERE "+MediaLibrary.PlaylistSongColumns.SONG_ID+" NOT IN (SELECT "+MediaLibrary.SongColumns._ID+" FROM "+MediaLibrary.TABLE_SONGS+");");
		}

		// And remove any orphaned references.
		dbh.execSQL("DELETE FROM "+MediaLibrary.TABLE_ALBUMS+" WHERE "+MediaLibrary.AlbumColumns._ID+" NOT IN (SELECT "+MediaLibrary.SongColumns.ALBUM_ID+" FROM "+MediaLibrary.TABLE_SONGS+");");
		dbh.execSQL("DELETE FROM "+MediaLibrary.TABLE_GENRES_SONGS+" WHERE "+MediaLibrary.GenreSongColumns.SONG_ID+" NOT IN (SELECT "+MediaLibrary.SongColumns._ID+" FROM "+MediaLibrary.TABLE_SONGS+");");
		dbh.execSQL("DELETE FROM "+MediaLibrary.TABLE_GENRES+" WHERE "+MediaLibrary.GenreColumns._ID+" NOT IN (SELECT "+MediaLibrary.GenreSongColumns._GENRE_ID+" FROM "+MediaLibrary.TABLE_GENRES_SONGS+");");
		dbh.execSQL("DELETE FROM "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+" WHERE "+MediaLibrary.ContributorSongColumns.SONG_ID+" NOT IN (SELECT "+MediaLibrary.SongColumns._ID+" FROM "+MediaLibrary.TABLE_SONGS+");");
		dbh.execSQL("DELETE FROM "+MediaLibrary.TABLE_CONTRIBUTORS+" WHERE "+MediaLibrary.ContributorColumns._ID+" NOT IN (SELECT "+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID+" FROM "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+");");
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
	 * Wrapper for SQLiteDatabase.query() function
	 */
	Cursor query (boolean distinct, String table, String[] columns, String criteria, String[] criteriaArgs, String groupBy, String having, String orderBy, String limit) {

		if (MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS_HUGE.equals(table)) {
			Log.v("VanillaMusic", "+++ warning : using HUGE table in genquery!");
		}

		if (criteria != null) {
			if (MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS.equals(table)) {
				// artist matches in the song-view are costly: try to give sqlite a hint
				String[] contributorMatch = extractVirtualColumn(criteria);
				if (contributorMatch != null) {
					criteria = contributorMatch[0];
					final String contributorId = contributorMatch[1];
					final String contributorRole = contributorMatch[2];

					criteria += MediaLibrary.SongColumns._ID+" IN (SELECT "+MediaLibrary.ContributorSongColumns.SONG_ID+" FROM "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+" WHERE "
					          + MediaLibrary.ContributorSongColumns.ROLE+"="+contributorRole+" AND "+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID+"="+contributorId+")";
				}
			}

			if (MediaLibrary.VIEW_ALBUMS_ARTISTS.equals(table)) {
				// looking up artists by albums will magically return every album where this
				// artist has at least one item (while still using the primary_artist_id as the artist key)
				String[] contributorMatch = extractVirtualColumn(criteria);
				if (contributorMatch != null) {
					criteria = contributorMatch[0];
					final String contributorId = contributorMatch[1];
					final String contributorRole = contributorMatch[2];

					criteria += MediaLibrary.SongColumns._ID+" IN (SELECT DISTINCT "+MediaLibrary.SongColumns.ALBUM_ID+" FROM "+MediaLibrary.TABLE_SONGS+" WHERE "
					          + MediaLibrary.SongColumns._ID+" IN (SELECT "+MediaLibrary.ContributorSongColumns.SONG_ID+" FROM "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+" WHERE "
					          + MediaLibrary.ContributorSongColumns.ROLE+"="+contributorRole+" AND "+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID+"="+contributorId+"))";
				}
			}

			// Genre queries are a special beast: 'optimize' all of them
			Matcher genreMatch = sQueryMatchGenreSearch.matcher(criteria);
			if (genreMatch.matches()) {
				criteria = genreMatch.group(1); // keep the non-genre search part of the query
				final String genreId = genreMatch.group(2); // and extract the searched genre id
				final String songsFromGenreQuery = buildSongIdFromGenre(genreId);

				if(table.equals(MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS)      ||
				   table.equals(MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS_HUGE) ) {
					criteria += MediaLibrary.SongColumns._ID+" IN ("+songsFromGenreQuery+") ";
				}

				if (table.equals(MediaLibrary.VIEW_ALBUMS_ARTISTS)) {
					criteria += MediaLibrary.AlbumColumns._ID+" IN ("+
						buildSongIdWithInSelect(MediaLibrary.SongColumns.ALBUM_ID, MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS, songsFromGenreQuery)+") ";
				}

				if (table.equals(MediaLibrary.VIEW_ARTISTS)) {
					criteria += MediaLibrary.ContributorColumns.ARTIST_ID+" IN ("+
						buildSongIdWithInSelect(MediaLibrary.ContributorColumns.ARTIST_ID, MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS_HUGE, songsFromGenreQuery)+") ";
					Log.v("VanillaMusic", "+++ warning: huge genrequery for artist!");
				}

				if (table.equals(MediaLibrary.VIEW_ALBUMARTISTS)) {
					criteria += MediaLibrary.ContributorColumns.ALBUMARTIST_ID+" IN ("+
						buildSongIdWithInSelect(MediaLibrary.ContributorColumns.ALBUMARTIST_ID, MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS, songsFromGenreQuery)+") ";
				}

				if (table.equals(MediaLibrary.VIEW_COMPOSERS)) {
					criteria += MediaLibrary.ContributorColumns.COMPOSER_ID+" IN ("+
						buildSongIdWithInSelect(MediaLibrary.ContributorColumns.COMPOSER_ID, MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS_HUGE, songsFromGenreQuery)+") ";
					Log.v("VanillaMusic", "+++ warning: huge genrequery composer!");
				}

			}
		}

		if (DEBUG)
			debugQuery(distinct, table, columns, criteria, criteriaArgs, groupBy, having, orderBy, limit);

		Cursor cursor = getReadableDatabase().query(distinct, table, columns, criteria, criteriaArgs, groupBy, having, orderBy, limit);
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
	 * Returns a query to get all songs from a genre
	 *
	 * @param genreId the id to query as a string
	 * @return an SQL string which should return song id's for the queried genre
	 */
	private String buildSongIdFromGenre(String genreId) {
		return "SELECT "+MediaLibrary.GenreSongColumns.SONG_ID+" FROM "+MediaLibrary.TABLE_GENRES_SONGS+" WHERE "
		                +MediaLibrary.GenreSongColumns._GENRE_ID+"="+genreId+" GROUP BY "+MediaLibrary.GenreSongColumns.SONG_ID;
	}

	/**
	 * Returns a select query to get entities by matching the SongColumns._ID field.
	 *
	 * @param target the target to query
	 * @param table the table to query
	 * @param select the select string
	 * @return an SQL string
	 */
	private String buildSongIdWithInSelect(String target, String table, String select) {
		return "SELECT "+target+" FROM "+ table +" WHERE "
		                +MediaLibrary.SongColumns._ID+" IN ("+select+") GROUP BY "+target;
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
