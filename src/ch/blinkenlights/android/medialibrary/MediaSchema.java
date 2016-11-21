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
	  + MediaLibrary.SongColumns.ALBUM_ID     +" INTEGER NOT NULL, "
	  + MediaLibrary.SongColumns.PLAYCOUNT    +" INTEGER NOT NULL DEFAULT 0, "
	  + MediaLibrary.SongColumns.SKIPCOUNT    +" INTEGER NOT NULL DEFAULT 0, "
	  + MediaLibrary.SongColumns.MTIME        +" TIMESTAMP DEFAULT CURRENT_TIMESTAMP, "
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
	  + MediaLibrary.AlbumColumns.SONG_COUNT        +" INTEGER, "
	  + MediaLibrary.AlbumColumns.DISC_NUMBER       +" INTEGER, "
	  + MediaLibrary.AlbumColumns.DISC_COUNT        +" INTEGER, "
	  + MediaLibrary.AlbumColumns.YEAR              +" INTEGER, "
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
	 * Index to select a playlist quickly
	 */
	private static final String INDEX_IDX_PLAYLIST_ID = "CREATE INDEX idx_playlist_id ON "+MediaLibrary.TABLE_PLAYLISTS_SONGS
	 +" ("+MediaLibrary.PlaylistSongColumns.PLAYLIST_ID+")"
	 +";";

	/**
	 * Additional columns to select for artist info
	 */
	private static final String VIEW_ARTIST_SELECT = "_artist."+MediaLibrary.ContributorColumns._CONTRIBUTOR+" AS "+MediaLibrary.ContributorColumns.ARTIST
	                                               +",_artist."+MediaLibrary.ContributorColumns._CONTRIBUTOR_SORT+" AS "+MediaLibrary.ContributorColumns.ARTIST_SORT
	                                               +",_artist."+MediaLibrary.ContributorColumns._ID+" AS "+MediaLibrary.ContributorColumns.ARTIST_ID;

	/**
	 * View which includes song, album and artist information
	 */
	private static final String VIEW_CREATE_SONGS_ALBUMS_ARTISTS = "CREATE VIEW "+ MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS+ " AS "
	  + "SELECT *, " + VIEW_ARTIST_SELECT + " FROM " + MediaLibrary.TABLE_SONGS
	  +" LEFT JOIN "+MediaLibrary.TABLE_ALBUMS+" ON "+MediaLibrary.TABLE_SONGS+"."+MediaLibrary.SongColumns.ALBUM_ID+" = "+MediaLibrary.TABLE_ALBUMS+"."+MediaLibrary.AlbumColumns._ID
	  +" LEFT JOIN "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+" ON "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+"."+MediaLibrary.ContributorSongColumns.ROLE+"=0 "
	  +" AND "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+"."+MediaLibrary.ContributorSongColumns.SONG_ID+" = "+MediaLibrary.TABLE_SONGS+"."+MediaLibrary.SongColumns._ID
	  +" LEFT JOIN "+MediaLibrary.TABLE_CONTRIBUTORS+" AS _artist ON _artist."+MediaLibrary.ContributorColumns._ID+" = "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+"."+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID
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
	  +" WHERE "+MediaLibrary.ContributorSongColumns.ROLE+"=0 GROUP BY "+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID+")"
	  +" ;";

	/**
	 * View like VIEW_CREATE_ARTISTS but includes playlist information
	 */
	private static final String VIEW_CREATE_PLAYLIST_SONGS = "CREATE VIEW "+ MediaLibrary.VIEW_PLAYLIST_SONGS+" AS "
	  + "SELECT *, " + VIEW_ARTIST_SELECT + " FROM " + MediaLibrary.TABLE_PLAYLISTS_SONGS
	  +" LEFT JOIN "+MediaLibrary.TABLE_SONGS+" ON "+MediaLibrary.TABLE_PLAYLISTS_SONGS+"."+MediaLibrary.PlaylistSongColumns.SONG_ID+"="+MediaLibrary.TABLE_SONGS+"."+MediaLibrary.SongColumns._ID
	  // -> same sql as VIEW_CREATE_SONGS_ALBUMS_ARTISTS follows:
	  +" LEFT JOIN "+MediaLibrary.TABLE_ALBUMS+" ON "+MediaLibrary.TABLE_SONGS+"."+MediaLibrary.SongColumns.ALBUM_ID+" = "+MediaLibrary.TABLE_ALBUMS+"."+MediaLibrary.AlbumColumns._ID
	  +" LEFT JOIN "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+" ON "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+"."+MediaLibrary.ContributorSongColumns.ROLE+"=0 "
	  +" AND "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+"."+MediaLibrary.ContributorSongColumns.SONG_ID+" = "+MediaLibrary.TABLE_SONGS+"."+MediaLibrary.SongColumns._ID
	  +" LEFT JOIN "+MediaLibrary.TABLE_CONTRIBUTORS+" AS _artist ON _artist."+MediaLibrary.ContributorColumns._ID+" = "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+"."+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID
	  +" ;";

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
		dbh.execSQL(VIEW_CREATE_SONGS_ALBUMS_ARTISTS);
		dbh.execSQL(VIEW_CREATE_ALBUMS_ARTISTS);
		dbh.execSQL(VIEW_CREATE_ARTISTS);
		dbh.execSQL(VIEW_CREATE_PLAYLIST_SONGS);
	}

}
