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

import android.database.sqlite.SQLiteDatabase;

public class MediaSchema {
	/**
	 * SQL Schema of `songs' table
	 */
	private static final String DATABASE_CREATE_SONGS = "CREATE TABLE "+ MediaLibrary.TABLE_SONGS + " ("
	  + MediaLibrary.SongColumns._ID          +" INTEGER PRIMARY KEY, "
	  + MediaLibrary.SongColumns.TITLE        +" TEXT NOT NULL, "
	  + MediaLibrary.SongColumns.TITLE_SORT   +" VARCHAR(64) NOT NULL, "
	  + MediaLibrary.SongColumns.SONG_NUMBER  +" INTEGER, "
	  + MediaLibrary.SongColumns.DISC_NUMBER  +" INTEGER, "
	  + MediaLibrary.SongColumns.YEAR         +" INTEGER, "
	  + MediaLibrary.SongColumns.ALBUM_ID     +" INTEGER NOT NULL, "
	  + MediaLibrary.SongColumns.PLAYCOUNT    +" INTEGER NOT NULL DEFAULT 0, "
	  + MediaLibrary.SongColumns.SKIPCOUNT    +" INTEGER NOT NULL DEFAULT 0, "
	  + MediaLibrary.SongColumns.MTIME        +" TIMESTAMP DEFAULT (strftime('%s', CURRENT_TIMESTAMP)), "
	  + MediaLibrary.SongColumns.DURATION     +" INTEGER NOT NULL, "
	  + MediaLibrary.SongColumns.PATH         +" VARCHAR(4096) NOT NULL "
	  + ");";

	/**
	 * SQL Schema of `albums' table
	 */
	private static final String DATABASE_CREATE_ALBUMS = "CREATE TABLE "+ MediaLibrary.TABLE_ALBUMS + " ("
	  + MediaLibrary.AlbumColumns._ID               +" INTEGER PRIMARY KEY, "
	  + MediaLibrary.AlbumColumns.ALBUM             +" TEXT NOT NULL, "
	  + MediaLibrary.AlbumColumns.ALBUM_SORT        +" VARCHAR(64) NOT NULL, "
	  + MediaLibrary.AlbumColumns.PRIMARY_ALBUM_YEAR+" INTEGER, "
	  + MediaLibrary.AlbumColumns.PRIMARY_ARTIST_ID +" INTEGER NOT NULL DEFAULT 0, "
	  + MediaLibrary.AlbumColumns.MTIME             +" TIMESTAMP DEFAULT CURRENT_TIMESTAMP "
	  + ");";

	/**
	 * SQL Schema of `contributors' table
	 */
	private static final String DATABASE_CREATE_CONTRIBUTORS = "CREATE TABLE "+ MediaLibrary.TABLE_CONTRIBUTORS + " ("
	  + MediaLibrary.ContributorColumns._ID               +" INTEGER PRIMARY KEY, "
	  + MediaLibrary.ContributorColumns._CONTRIBUTOR      +" TEXT NOT NULL, "
	  + MediaLibrary.ContributorColumns._CONTRIBUTOR_SORT +" TEXT NOT NULL, "
	  + MediaLibrary.ContributorColumns.MTIME             +" TIMESTAMP DEFAULT CURRENT_TIMESTAMP "
	  + ");";

	/**
	 * SQL Schema of 'contributors<->songs' table
	 */
	private static final String DATABASE_CREATE_CONTRIBUTORS_SONGS = "CREATE TABLE "+ MediaLibrary.TABLE_CONTRIBUTORS_SONGS+ " ("
	  + MediaLibrary.ContributorSongColumns.ROLE             +" INTEGER, "
	  + MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID  +" INTEGER, "
	  + MediaLibrary.ContributorSongColumns.SONG_ID          +" INTEGER, "
	  + "PRIMARY KEY("+MediaLibrary.ContributorSongColumns.ROLE+","
	                  +MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID+","
	                  +MediaLibrary.ContributorSongColumns.SONG_ID+") "
	  + ");";

	/**
	 * song, role index on contributors_songs table
	 */
	private static final String INDEX_IDX_CONTRIBUTORS_SONGS = "CREATE INDEX idx_contributors_songs ON "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS
	  +" ("+MediaLibrary.ContributorSongColumns.SONG_ID+", "+MediaLibrary.ContributorSongColumns.ROLE+")"
	  +";";

	/**
	 * SQL Schema of `genres' table
	 */
	private static final String DATABASE_CREATE_GENRES = "CREATE TABLE "+ MediaLibrary.TABLE_GENRES + " ("
	  + MediaLibrary.GenreColumns._ID         +" INTEGER PRIMARY KEY, "
	  + MediaLibrary.GenreColumns._GENRE      +" TEXT NOT NULL, "
	  + MediaLibrary.GenreColumns._GENRE_SORT +" TEXT NOT NULL "
	  + ");";

	/**
	 * SQL Schema of 'genres<->songs' table
	 */
	private static final String DATABASE_CREATE_GENRES_SONGS = "CREATE TABLE "+ MediaLibrary.TABLE_GENRES_SONGS + " ("
	  + MediaLibrary.GenreSongColumns._GENRE_ID  +" INTEGER, "
	  + MediaLibrary.GenreSongColumns.SONG_ID    +" INTEGER, "
	  + "PRIMARY KEY("+MediaLibrary.GenreSongColumns._GENRE_ID+","
	                  +MediaLibrary.GenreSongColumns.SONG_ID+") "
	  + ");";

	/**
	 * SQL Schema for the playlists table
	 */
	private static final String DATABASE_CREATE_PLAYLISTS = "CREATE TABLE "+ MediaLibrary.TABLE_PLAYLISTS +" ("
	  + MediaLibrary.PlaylistColumns._ID   +" INTEGER PRIMARY KEY, "
	  + MediaLibrary.PlaylistColumns.NAME  +" TEXT NOT NULL "
	  + ");";

	/**
	 * SQL Schema of 'songs<->playlists' table
	 */
	private static final String DATABASE_CREATE_PLAYLISTS_SONGS = "CREATE TABLE "+ MediaLibrary.TABLE_PLAYLISTS_SONGS + " ("
	  + MediaLibrary.PlaylistSongColumns._ID          +" INTEGER PRIMARY KEY, "
	  + MediaLibrary.PlaylistSongColumns.PLAYLIST_ID  +" INTEGER NOT NULL, "
	  + MediaLibrary.PlaylistSongColumns.SONG_ID      +" INTEGER NOT NULL, "
	  + MediaLibrary.PlaylistSongColumns.POSITION     +" INTEGER NOT NULL "
	  + ");";

	/**
	 * SQL schema for our preferences
	 */
	private static final String DATABASE_CREATE_PREFERENCES = "CREATE TABLE "+ MediaLibrary.TABLE_PREFERENCES + " ("
	  + MediaLibrary.PreferenceColumns.KEY   +" INTEGER PRIMARY KEY, "
	  + MediaLibrary.PreferenceColumns.VALUE +" INTEGER NOT NULL "
	  + ");";

	/**
	 * SQL schema of audiobooks table
	 */
	private static final String DATABASE_CREATE_AUDIOBOOKS = "CREATE TABLE IF NOT EXISTS " + MediaLibrary.TABLE_AUDIOBOOKS + " ("
			+ MediaLibrary.AudiobookColumns._ID 		   + " INTEGER PRIMARY KEY, "
			+ MediaLibrary.AudiobookColumns.PATH           + " VARCHAR(4096), "
			+ MediaLibrary.AudiobookColumns.SONG_ID        + " INTEGER , "
			+ MediaLibrary.AudiobookColumns.LOCATION       + " INTEGER "
			+ ");";

	/**
	 * SQL Schema of 'audiobook<->songs' table
	 */
	private static final String DATABASE_CREATE_AUDIOBOOKS_SONGS = "CREATE TABLE IF NOT EXISTS "+ MediaLibrary.TABLE_AUDIOBOOKS_SONGS + " ("
			+ MediaLibrary.AudiobookSongColumns.AUDIOBOOK_ID    +" INTEGER, "
			+ MediaLibrary.AudiobookSongColumns.SONG_ID    		+" INTEGER, "
			+ "PRIMARY KEY("+ MediaLibrary.AudiobookSongColumns.AUDIOBOOK_ID +","
			+ 				   MediaLibrary.AudiobookSongColumns.SONG_ID +") "
			+ ");";

	/**
	 * Index to select a song from an audiobook quickly
	 */
	private static final String INDEX_IDX_AUDIOBOOK_ID_SONG = "CREATE INDEX idx_audiobook_id_song ON "+MediaLibrary.TABLE_AUDIOBOOKS_SONGS
			+" ("+MediaLibrary.AudiobookSongColumns.SONG_ID+")"
			+";";

	/**
	 * Index to select a playlist quickly
	 */
	private static final String INDEX_IDX_PLAYLIST_ID = "CREATE INDEX idx_playlist_id ON "+MediaLibrary.TABLE_PLAYLISTS_SONGS
	 +" ("+MediaLibrary.PlaylistSongColumns.PLAYLIST_ID+")"
	 +";";

	/**
	 * Index to select a song from a playlist quickly
	 */
	private static final String INDEX_IDX_PLAYLIST_ID_SONG = "CREATE INDEX idx_playlist_id_song ON "+MediaLibrary.TABLE_PLAYLISTS_SONGS
	 +" ("+MediaLibrary.PlaylistSongColumns.PLAYLIST_ID+", "+MediaLibrary.PlaylistSongColumns.SONG_ID+")"
	 +";";

	/**
	 * Additional columns to select for artist info
	 */
	private static final String VIEW_ARTIST_SELECT = "_artist."+MediaLibrary.ContributorColumns._CONTRIBUTOR+" AS "+MediaLibrary.ContributorColumns.ARTIST
	                                               +",_artist."+MediaLibrary.ContributorColumns._CONTRIBUTOR_SORT+" AS "+MediaLibrary.ContributorColumns.ARTIST_SORT
	                                               +",_artist."+MediaLibrary.ContributorColumns._ID+" AS "+MediaLibrary.ContributorColumns.ARTIST_ID;

	/**
	 * Additional columns to select for albumartist info
	 */
	private static final String VIEW_ALBUMARTIST_SELECT = "_albumartist."+MediaLibrary.ContributorColumns._CONTRIBUTOR+" AS "+MediaLibrary.ContributorColumns.ALBUMARTIST
	                                                    +",_albumartist."+MediaLibrary.ContributorColumns._CONTRIBUTOR_SORT+" AS "+MediaLibrary.ContributorColumns.ALBUMARTIST_SORT
	                                                    +",_albumartist."+MediaLibrary.ContributorColumns._ID+" AS "+MediaLibrary.ContributorColumns.ALBUMARTIST_ID;

	/**
	 * Additional columns to select for composer info
	 */
	private static final String VIEW_COMPOSER_SELECT = "_composer."+MediaLibrary.ContributorColumns._CONTRIBUTOR+" AS "+MediaLibrary.ContributorColumns.COMPOSER
	                                                  +",_composer."+MediaLibrary.ContributorColumns._CONTRIBUTOR_SORT+" AS "+MediaLibrary.ContributorColumns.COMPOSER_SORT
	                                                  +",_composer."+MediaLibrary.ContributorColumns._ID+" AS "+MediaLibrary.ContributorColumns.COMPOSER_ID;


	/**
	 * View which includes song, album and artist information, enough for a filled song projection
	 */
	private static final String VIEW_CREATE_SONGS_ALBUMS_ARTISTS = "CREATE VIEW "+ MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS+ " AS "
	  + "SELECT *, " + VIEW_ARTIST_SELECT + " FROM " + MediaLibrary.TABLE_SONGS
	  +" LEFT JOIN "+MediaLibrary.TABLE_ALBUMS+" ON "+MediaLibrary.TABLE_SONGS+"."+MediaLibrary.SongColumns.ALBUM_ID+" = "+MediaLibrary.TABLE_ALBUMS+"."+MediaLibrary.AlbumColumns._ID
	  +" LEFT JOIN "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+" ON "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+"."+MediaLibrary.ContributorSongColumns.ROLE+"="+MediaLibrary.ROLE_ARTIST
	  +" AND "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+"."+MediaLibrary.ContributorSongColumns.SONG_ID+" = "+MediaLibrary.TABLE_SONGS+"."+MediaLibrary.SongColumns._ID
	  +" LEFT JOIN "+MediaLibrary.TABLE_CONTRIBUTORS+" AS _artist ON _artist."+MediaLibrary.ContributorColumns._ID+" = "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+"."+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID
	  +" ;";

	/**
	 * View wich includes SONGS_ALBUMS_ARTISTS and any other contributors
	 * This view should only be used if needed as the SQL query is pretty expensive
	 */
	private static final String VIEW_CREATE_SONGS_ALBUMS_ARTISTS_HUGE = "CREATE VIEW "+ MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS_HUGE+" AS "
	  + "SELECT *, "+ VIEW_ALBUMARTIST_SELECT +", "+ VIEW_COMPOSER_SELECT +" FROM "+MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS
	  // albumartists
	  +" LEFT JOIN "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+" as __albumartists"
	  +" ON  __albumartists."+MediaLibrary.ContributorSongColumns.ROLE+"="+MediaLibrary.ROLE_ALBUMARTIST
	  +" AND __albumartists."+MediaLibrary.ContributorSongColumns.SONG_ID+" = "+MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS+"."+MediaLibrary.SongColumns._ID
	  +" LEFT JOIN "+MediaLibrary.TABLE_CONTRIBUTORS+" AS _albumartist ON"
	  +"  _albumartist."+MediaLibrary.ContributorColumns._ID+" = __albumartists."+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID
	  // composers
	  +" LEFT JOIN "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+" as __composers"
	  +" ON  __composers."+MediaLibrary.ContributorSongColumns.ROLE+"="+MediaLibrary.ROLE_COMPOSER
	  +" AND __composers."+MediaLibrary.ContributorSongColumns.SONG_ID+" = "+MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS+"."+MediaLibrary.SongColumns._ID
	  +" LEFT JOIN "+MediaLibrary.TABLE_CONTRIBUTORS+" AS _composer ON"
	  +"  _composer."+MediaLibrary.ContributorColumns._ID+" = __composers."+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID
	  +" ;";

	/**
	 * View which includes album and artist information
	 */
	private static final String VIEW_CREATE_ALBUMS_ARTISTS = "CREATE VIEW "+ MediaLibrary.VIEW_ALBUMS_ARTISTS+ " AS "
	  + "SELECT *, " + VIEW_ARTIST_SELECT + " FROM " + MediaLibrary.TABLE_ALBUMS
	  +" LEFT JOIN "+MediaLibrary.TABLE_CONTRIBUTORS+" AS _artist"
	  +" ON _artist."+MediaLibrary.ContributorColumns._ID+" = "+MediaLibrary.TABLE_ALBUMS+"."+MediaLibrary.AlbumColumns.PRIMARY_ARTIST_ID
	  +" ;";

	/**
	 * View which includes artist information
	 */
	private static final String VIEW_CREATE_ARTISTS = "CREATE VIEW "+ MediaLibrary.VIEW_ARTISTS+ " AS "
	  + "SELECT *, " + VIEW_ARTIST_SELECT + " FROM "+MediaLibrary.TABLE_CONTRIBUTORS+" AS _artist WHERE "+MediaLibrary.ContributorColumns._ID+" IN "
	  +" (SELECT "+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID+" FROM "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS
	  +" WHERE "+MediaLibrary.ContributorSongColumns.ROLE+"="+MediaLibrary.ROLE_ARTIST+" GROUP BY "+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID+")"
	  +" ;";

	/**
	 * View which includes albumArtists information
	 */
	private static final String VIEW_CREATE_ALBUMARTISTS = "CREATE VIEW "+ MediaLibrary.VIEW_ALBUMARTISTS+ " AS "
	  + "SELECT *, " + VIEW_ALBUMARTIST_SELECT + " FROM "+MediaLibrary.TABLE_CONTRIBUTORS+" AS _albumartist WHERE "+MediaLibrary.ContributorColumns._ID+" IN "
	  +" (SELECT "+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID+" FROM "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS
	  +" WHERE "+MediaLibrary.ContributorSongColumns.ROLE+"="+MediaLibrary.ROLE_ALBUMARTIST+" GROUP BY "+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID+")"
	  +" ;";

	/**
	 * View which includes composer information
	 */
	private static final String VIEW_CREATE_COMPOSERS = "CREATE VIEW "+ MediaLibrary.VIEW_COMPOSERS+ " AS "
	  + "SELECT *, " + VIEW_COMPOSER_SELECT + " FROM "+MediaLibrary.TABLE_CONTRIBUTORS+" AS _composer WHERE "+MediaLibrary.ContributorColumns._ID+" IN "
	  +" (SELECT "+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID+" FROM "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS
	  +" WHERE "+MediaLibrary.ContributorSongColumns.ROLE+"="+MediaLibrary.ROLE_COMPOSER+" GROUP BY "+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID+")"
	  +" ;";

	/**
	 * View like VIEW_CREATE_ARTISTS but includes playlist information
	 */
	private static final String VIEW_CREATE_PLAYLIST_SONGS = "CREATE VIEW "+ MediaLibrary.VIEW_PLAYLIST_SONGS+" AS "
	  + "SELECT *, " + VIEW_ARTIST_SELECT + " FROM " + MediaLibrary.TABLE_PLAYLISTS_SONGS
	  +" LEFT JOIN "+MediaLibrary.TABLE_SONGS+" ON "+MediaLibrary.TABLE_PLAYLISTS_SONGS+"."+MediaLibrary.PlaylistSongColumns.SONG_ID+"="+MediaLibrary.TABLE_SONGS+"."+MediaLibrary.SongColumns._ID
	  // -> same sql as VIEW_CREATE_SONGS_ALBUMS_ARTISTS follows:
	  +" LEFT JOIN "+MediaLibrary.TABLE_ALBUMS+" ON "+MediaLibrary.TABLE_SONGS+"."+MediaLibrary.SongColumns.ALBUM_ID+" = "+MediaLibrary.TABLE_ALBUMS+"."+MediaLibrary.AlbumColumns._ID
	  +" LEFT JOIN "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+" ON "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+"."+MediaLibrary.ContributorSongColumns.ROLE+"="+MediaLibrary.ROLE_ARTIST
	  +" AND "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+"."+MediaLibrary.ContributorSongColumns.SONG_ID+" = "+MediaLibrary.TABLE_SONGS+"."+MediaLibrary.SongColumns._ID
	  +" LEFT JOIN "+MediaLibrary.TABLE_CONTRIBUTORS+" AS _artist ON _artist."+MediaLibrary.ContributorColumns._ID+" = "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+"."+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID
	  +" ;";

	/**
	 * Add no shuffle column to songs table
	 */
	private static final String DATABASE_SONGS_ADD_NO_SHUFFLE_COLUMN = "ALTER TABLE " + MediaLibrary.TABLE_SONGS + " ADD COLUMN " + MediaLibrary.SongColumns.NO_SHUFFLE + " INTEGER";


	/**
	 * Creates a new database schema on dbh
	 *
	 * @param dbh the writeable dbh to act on
	 */
	public static void createDatabaseSchema(SQLiteDatabase dbh) {
		dbh.execSQL(DATABASE_CREATE_SONGS);
		dbh.execSQL(DATABASE_CREATE_ALBUMS);
		dbh.execSQL(DATABASE_CREATE_CONTRIBUTORS);
		dbh.execSQL(DATABASE_CREATE_CONTRIBUTORS_SONGS);
		dbh.execSQL(INDEX_IDX_CONTRIBUTORS_SONGS);
		dbh.execSQL(DATABASE_CREATE_GENRES);
		dbh.execSQL(DATABASE_CREATE_GENRES_SONGS);
		dbh.execSQL(DATABASE_CREATE_PLAYLISTS);
		dbh.execSQL(DATABASE_CREATE_PLAYLISTS_SONGS);
		dbh.execSQL(INDEX_IDX_PLAYLIST_ID);
		dbh.execSQL(INDEX_IDX_PLAYLIST_ID_SONG);
		dbh.execSQL(VIEW_CREATE_SONGS_ALBUMS_ARTISTS);
		dbh.execSQL(VIEW_CREATE_SONGS_ALBUMS_ARTISTS_HUGE);
		dbh.execSQL(VIEW_CREATE_ALBUMS_ARTISTS);
		dbh.execSQL(VIEW_CREATE_ARTISTS);
		dbh.execSQL(VIEW_CREATE_ALBUMARTISTS);
		dbh.execSQL(VIEW_CREATE_COMPOSERS);
		dbh.execSQL(VIEW_CREATE_PLAYLIST_SONGS);
		dbh.execSQL(DATABASE_CREATE_PREFERENCES);
		dbh.execSQL(DATABASE_SONGS_ADD_NO_SHUFFLE_COLUMN);
		dbh.execSQL(DATABASE_CREATE_AUDIOBOOKS);
		dbh.execSQL(DATABASE_CREATE_AUDIOBOOKS_SONGS);
		dbh.execSQL(INDEX_IDX_AUDIOBOOK_ID_SONG);
	}

	/**
	 * Upgrades an existing database
	 *
	 * @param dbh the writeable dbh to use
	 * @param oldVersion the version of the old (aka: existing) database
	 */
	public static void upgradeDatabaseSchema(SQLiteDatabase dbh, int oldVersion) {
		if (oldVersion < 20170101) {
			// this is ugly but was never released as a stable version, so
			// it's good enough to keep playlists for testers
			dbh.execSQL("DROP TABLE songs");
			dbh.execSQL("DROP TABLE albums");
			dbh.execSQL(DATABASE_CREATE_SONGS);
			dbh.execSQL(DATABASE_CREATE_ALBUMS);
		}

		if (oldVersion < 20170102) {
			dbh.execSQL("UPDATE songs SET disc_num=1 WHERE disc_num IS null");
		}

		if (oldVersion < 20170120) {
			dbh.execSQL(DATABASE_CREATE_PREFERENCES);
			triggerFullMediaScan(dbh);
		}

		if (oldVersion < 20170211) {
			// older versions of triggerFullMediaScan did this by mistake
			dbh.execSQL("UPDATE songs SET mtime=1 WHERE mtime=0");
		}

		if (oldVersion < 20170217) {
			dbh.execSQL(VIEW_CREATE_ALBUMARTISTS);
			dbh.execSQL(VIEW_CREATE_COMPOSERS);
			dbh.execSQL(VIEW_CREATE_SONGS_ALBUMS_ARTISTS_HUGE);
		}

		if(oldVersion < 20170311) {
			dbh.execSQL(DATABASE_SONGS_ADD_NO_SHUFFLE_COLUMN);
		}

		if(oldVersion < 20170312) {
			dbh.execSQL(DATABASE_CREATE_AUDIOBOOKS);
			dbh.execSQL(DATABASE_CREATE_AUDIOBOOKS_SONGS);
			dbh.execSQL(INDEX_IDX_AUDIOBOOK_ID_SONG);
		}
	}

	/**
	 * Changes the mtime of all songs and flushes the scanner progress / preferences
	 * This triggers a full rebuild of the library on startup
	 *
	 * @param dbh the writeable dbh to use
	 */
	private static void triggerFullMediaScan(SQLiteDatabase dbh) {
		dbh.execSQL("UPDATE "+MediaLibrary.TABLE_SONGS+" SET "+MediaLibrary.SongColumns.MTIME+"=1");
		// wipes non-bools only - not nice but good enough for now
		dbh.execSQL("DELETE FROM "+MediaLibrary.TABLE_PREFERENCES+" WHERE "+MediaLibrary.PreferenceColumns.VALUE+" > 1");
	}

}
