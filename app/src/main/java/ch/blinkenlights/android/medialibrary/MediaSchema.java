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
	  + MediaLibrary.SongColumns.PATH         +" VARCHAR(4096) NOT NULL, "
	  + MediaLibrary.SongColumns.FLAGS        +" INTEGER NOT NULL DEFAULT 0 "
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
	private static final String NAME_IDX_CONTRIBUTORS_SONGS = "idx_contributors_songs";
	private static final String INDEX_IDX_CONTRIBUTORS_SONGS = "CREATE INDEX "+NAME_IDX_CONTRIBUTORS_SONGS+" ON "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS
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
	  + MediaLibrary.PlaylistColumns._ID       +" INTEGER PRIMARY KEY, "
	  + MediaLibrary.PlaylistColumns.NAME      +" TEXT NOT NULL, "
	  + MediaLibrary.PlaylistColumns.NAME_SORT +" TEXT NOT NULL "
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
	 * View which includes song, album and artist information, enough for a filled song projection;
	 * the information is retrieve from the tables: songs, albums, contributors where role = AlbumArtist.
	 * The artist information is retrieved from the role AlbumArtist since we support multiple Artists but just one Album Artist.
	 * SELECT *, _albumartist._contributor AS albumartist,_albumartist._contributor_sort AS albumartist_sort,_albumartist._id AS albumartist_id
	 * FROM songs
	 * LEFT JOIN albums ON songs.album_id = albums._id
	 * LEFT JOIN contributors_songs INDEXED BY idx_contributors_songs ON contributors_songs.role=2 AND contributors_songs.song_id = songs._id
	 * LEFT JOIN contributors AS _albumartist ON _albumartist._id = contributors_songs._contributor_id
	 */
	private static final String VIEW_CREATE_SONGS_ALBUMS_ARTISTS = "CREATE VIEW "+ MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS+ " AS "
	  + "SELECT *, " + VIEW_ALBUMARTIST_SELECT + " FROM " + MediaLibrary.TABLE_SONGS
	  +" LEFT JOIN "+MediaLibrary.TABLE_ALBUMS+" ON "+MediaLibrary.TABLE_SONGS+"."+MediaLibrary.SongColumns.ALBUM_ID+" = "+MediaLibrary.TABLE_ALBUMS+"."+MediaLibrary.AlbumColumns._ID
	  +" LEFT JOIN "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+" INDEXED BY "+NAME_IDX_CONTRIBUTORS_SONGS
	  +" ON "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+"."+MediaLibrary.ContributorSongColumns.ROLE+"="+MediaLibrary.ROLE_ALBUMARTIST
	  +" AND "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+"."+MediaLibrary.ContributorSongColumns.SONG_ID+" = "+MediaLibrary.TABLE_SONGS+"."+MediaLibrary.SongColumns._ID
	  +" LEFT JOIN "+MediaLibrary.TABLE_CONTRIBUTORS+" AS _albumartist ON _albumartist."+MediaLibrary.ContributorColumns._ID+" = "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+"."+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID
	  +" ;";

	/**
	 * View which includes SONGS_ALBUMS_ARTISTS and any other contributors
	 * This view should only be used if needed as the SQL query is pretty expensive.
	 * SELECT *, _artist._contributor AS artist,_artist._contributor_sort AS artist_sort,_artist._id AS artist_id, _composer._contributor AS composer,_composer._contributor_sort AS composer_sort,_composer._id AS composer_id
	 * FROM _songs_albums_artists
	 * LEFT JOIN contributors_songs as __artists INDEXED BY idx_contributors_songs ON  __artists.role=0 AND __artists.song_id = _songs_albums_artists._id
	 * LEFT JOIN contributors AS _artist ON  _artist._id = __artists._contributor_id
	 * LEFT JOIN contributors_songs as __composers INDEXED BY idx_contributors_songs ON  __composers.role=1 AND __composers.song_id = _songs_albums_artists._id
	 * LEFT JOIN contributors AS _composer ON  _composer._id = __composers._contributor_id
	 */
	private static final String VIEW_CREATE_SONGS_ALBUMS_ARTISTS_HUGE = "CREATE VIEW "+ MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS_HUGE+" AS "
	  + "SELECT *, "+ VIEW_ARTIST_SELECT +", "+ VIEW_COMPOSER_SELECT +" FROM "+MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS
	  // artists
	  +" LEFT JOIN "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+" as __artists INDEXED BY "+NAME_IDX_CONTRIBUTORS_SONGS
	  +" ON  __artists."+MediaLibrary.ContributorSongColumns.ROLE+"="+MediaLibrary.ROLE_ARTIST
	  +" AND __artists."+MediaLibrary.ContributorSongColumns.SONG_ID+" = "+MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS+"."+MediaLibrary.SongColumns._ID
	  +" LEFT JOIN "+MediaLibrary.TABLE_CONTRIBUTORS+" AS _artist ON"
	  +"  _artist."+MediaLibrary.ContributorColumns._ID+" = __artists."+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID
	  // composers
	  +" LEFT JOIN "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+" as __composers INDEXED BY "+NAME_IDX_CONTRIBUTORS_SONGS
	  +" ON  __composers."+MediaLibrary.ContributorSongColumns.ROLE+"="+MediaLibrary.ROLE_COMPOSER
	  +" AND __composers."+MediaLibrary.ContributorSongColumns.SONG_ID+" = "+MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS+"."+MediaLibrary.SongColumns._ID
	  +" LEFT JOIN "+MediaLibrary.TABLE_CONTRIBUTORS+" AS _composer ON"
	  +"  _composer."+MediaLibrary.ContributorColumns._ID+" = __composers."+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID
	  +" ;";

	/**
	 * View which includes album and artist information.
	 * SELECT albums.*, _artist._contributor AS artist,_artist._contributor_sort AS artist_sort,_artist._id AS artist_id, SUM(duration) AS duration
	 * FROM albums
	 * LEFT JOIN contributors AS _artist ON _artist._id = albums.primary_artist_id
	 * LEFT JOIN songs ON songs.album_id = albums._id
	 * GROUP BY albums._id
	 */
	private static final String VIEW_CREATE_ALBUMS_ARTISTS = "CREATE VIEW "+ MediaLibrary.VIEW_ALBUMS_ARTISTS+ " AS "
	  + "SELECT " + MediaLibrary.TABLE_ALBUMS + ".*, " + VIEW_ARTIST_SELECT + ", SUM(" + MediaLibrary.SongColumns.DURATION + ")" + " AS " + MediaLibrary.SongColumns.DURATION
	  +" FROM " + MediaLibrary.TABLE_ALBUMS
	  +" LEFT JOIN "+MediaLibrary.TABLE_CONTRIBUTORS+" AS _artist"
	  +" ON _artist."+MediaLibrary.ContributorColumns._ID+" = "+MediaLibrary.TABLE_ALBUMS+"."+MediaLibrary.AlbumColumns.PRIMARY_ARTIST_ID
	  +" LEFT JOIN " + MediaLibrary.TABLE_SONGS
	  +" ON " + MediaLibrary.TABLE_SONGS + "." + MediaLibrary.SongColumns.ALBUM_ID + " = " + MediaLibrary.TABLE_ALBUMS + "." + MediaLibrary.AlbumColumns._ID
	  +" GROUP BY " + MediaLibrary.TABLE_ALBUMS + "." + MediaLibrary.AlbumColumns._ID
	  +" ;";

	/**
	 * View that retrieves artist information from the contributors table and where the role = Artist;
	 * the role is retrieved from the contributors_songs table.
	 * SELECT *, _artist._contributor AS artist,_artist._contributor_sort AS artist_sort,_artist._id AS artist_id
	 * FROM contributors AS _artist
	 * WHERE _id IN
	 * (SELECT _contributor_id FROM contributors_songs WHERE role=0 GROUP BY _contributor_id)
	 */
	private static final String VIEW_CREATE_ARTISTS = "CREATE VIEW "+ MediaLibrary.VIEW_ARTISTS+ " AS "
	  + "SELECT *, " + VIEW_ARTIST_SELECT + " FROM "+MediaLibrary.TABLE_CONTRIBUTORS+" AS _artist WHERE "+MediaLibrary.ContributorColumns._ID+" IN "
	  +" (SELECT "+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID+" FROM "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS
	  +" WHERE "+MediaLibrary.ContributorSongColumns.ROLE+"="+MediaLibrary.ROLE_ARTIST+" GROUP BY "+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID+")"
	  +" ;";

	/**
	 * View that retrieves album artist information from the contributors table where the role = AlbumArtist;
	 * the role is retrieved from the contributors_songs table.
	 * SELECT *, _albumartist._contributor AS albumartist,_albumartist._contributor_sort AS albumartist_sort,_albumartist._id AS albumartist_id
	 * FROM contributors AS _albumartist
	 * WHERE _id IN
	 * (SELECT _contributor_id FROM contributors_songs WHERE role=2 GROUP BY _contributor_id)
	 */
	private static final String VIEW_CREATE_ALBUMARTISTS = "CREATE VIEW "+ MediaLibrary.VIEW_ALBUMARTISTS+ " AS "
	  + "SELECT *, " + VIEW_ALBUMARTIST_SELECT + " FROM "+MediaLibrary.TABLE_CONTRIBUTORS+" AS _albumartist WHERE "+MediaLibrary.ContributorColumns._ID+" IN "
	  +" (SELECT "+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID+" FROM "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS
	  +" WHERE "+MediaLibrary.ContributorSongColumns.ROLE+"="+MediaLibrary.ROLE_ALBUMARTIST+" GROUP BY "+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID+")"
	  +" ;";

	/**
	 * View that retrieves composer information from the contributors table and where the role = Composer;
	 * the role is retrieved from the contributors_songs table.
	 * SELECT *, _composer._contributor AS composer,_composer._contributor_sort AS composer_sort,_composer._id AS composer_id
	 * FROM contributors AS _composer
	 * WHERE _id IN
	 * (SELECT _contributor_id FROM contributors_songs WHERE role=1 GROUP BY _contributor_id)
	 */
	private static final String VIEW_CREATE_COMPOSERS = "CREATE VIEW "+ MediaLibrary.VIEW_COMPOSERS+ " AS "
	  + "SELECT *, " + VIEW_COMPOSER_SELECT + " FROM "+MediaLibrary.TABLE_CONTRIBUTORS+" AS _composer WHERE "+MediaLibrary.ContributorColumns._ID+" IN "
	  +" (SELECT "+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID+" FROM "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS
	  +" WHERE "+MediaLibrary.ContributorSongColumns.ROLE+"="+MediaLibrary.ROLE_COMPOSER+" GROUP BY "+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID+")"
	  +" ;";

	/**
	 * View that retrieves playlist songs information fom the playlists_songs table;
	 * the song information is retrieved from the tables: songs, albums, contributors (artists) where role = AlbumArtist.
	 * The artist information is retrieved from the role AlbumArtist since we support multiple Artists but just one Album Artist.
	 * SELECT *, _albumartist._contributor AS albumartist,_albumartist._contributor_sort AS albumartist_sort,_albumartist._id AS albumartist_id
	 * FROM playlists_songs
	 * LEFT JOIN songs ON playlists_songs.song_id=songs._id
	 * LEFT JOIN albums ON songs.album_id = albums._id
	 * LEFT JOIN contributors_songs INDEXED BY idx_contributors_songs ON contributors_songs.role=2 AND contributors_songs.song_id = songs._id
	 * LEFT JOIN contributors AS _albumartist ON _albumartist._id = contributors_songs._contributor_id
	 */
	private static final String VIEW_CREATE_PLAYLISTS_SONGS = "CREATE VIEW "+ MediaLibrary.VIEW_PLAYLISTS_SONGS+" AS "
	  + "SELECT *, " + VIEW_ALBUMARTIST_SELECT + " FROM " + MediaLibrary.TABLE_PLAYLISTS_SONGS
	  +" LEFT JOIN "+MediaLibrary.TABLE_SONGS+" ON "+MediaLibrary.TABLE_PLAYLISTS_SONGS+"."+MediaLibrary.PlaylistSongColumns.SONG_ID+"="+MediaLibrary.TABLE_SONGS+"."+MediaLibrary.SongColumns._ID
	  // -> same sql as VIEW_CREATE_SONGS_ALBUMS_ARTISTS follows:
	  +" LEFT JOIN "+MediaLibrary.TABLE_ALBUMS+" ON "+MediaLibrary.TABLE_SONGS+"."+MediaLibrary.SongColumns.ALBUM_ID+" = "+MediaLibrary.TABLE_ALBUMS+"."+MediaLibrary.AlbumColumns._ID
	  +" LEFT JOIN "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+" INDEXED BY "+NAME_IDX_CONTRIBUTORS_SONGS
	  +" ON "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+"."+MediaLibrary.ContributorSongColumns.ROLE+"="+MediaLibrary.ROLE_ALBUMARTIST
	  +" AND "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+"."+MediaLibrary.ContributorSongColumns.SONG_ID+" = "+MediaLibrary.TABLE_SONGS+"."+MediaLibrary.SongColumns._ID
	  +" LEFT JOIN "+MediaLibrary.TABLE_CONTRIBUTORS+" AS _albumartist ON _albumartist."+MediaLibrary.ContributorColumns._ID+" = "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+"."+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID
	  +" ;";

	/**
	 * View of all playlists, including additional information such as the duration.
	 * SELECT playlists.*, SUM(_s.duration) AS duration
	 * FROM playlists
	 * LEFT JOIN playlists_songs AS _ps ON playlists._id = _ps.playlist_id
	 * LEFT JOIN songs AS _s ON _s._id = _ps.song_id
	 * GROUP BY playlists._id
	 */
	private static final String VIEW_CREATE_PLAYLISTS = "CREATE VIEW "+ MediaLibrary.VIEW_PLAYLISTS+ " AS "
		+ "SELECT " + MediaLibrary.TABLE_PLAYLISTS + ".*, SUM(_s." + MediaLibrary.SongColumns.DURATION + ") AS " + MediaLibrary.SongColumns.DURATION + " FROM " + MediaLibrary.TABLE_PLAYLISTS
		+" LEFT JOIN " + MediaLibrary.TABLE_PLAYLISTS_SONGS + " AS _ps ON " + MediaLibrary.TABLE_PLAYLISTS + "." + MediaLibrary.PlaylistColumns._ID +" = _ps." + MediaLibrary.PlaylistSongColumns.PLAYLIST_ID
		+" LEFT JOIN " + MediaLibrary.TABLE_SONGS + " AS _s ON _s." + MediaLibrary.SongColumns._ID + " = _ps." + MediaLibrary.PlaylistSongColumns.SONG_ID
		+" GROUP BY " + MediaLibrary.TABLE_PLAYLISTS + "." + MediaLibrary.PlaylistColumns._ID
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
		dbh.execSQL(INDEX_IDX_PLAYLIST_ID_SONG);
		dbh.execSQL(VIEW_CREATE_SONGS_ALBUMS_ARTISTS);
		dbh.execSQL(VIEW_CREATE_SONGS_ALBUMS_ARTISTS_HUGE);
		dbh.execSQL(VIEW_CREATE_ALBUMS_ARTISTS);
		dbh.execSQL(VIEW_CREATE_ARTISTS);
		dbh.execSQL(VIEW_CREATE_ALBUMARTISTS);
		dbh.execSQL(VIEW_CREATE_COMPOSERS);
		dbh.execSQL(VIEW_CREATE_PLAYLISTS);
		dbh.execSQL(VIEW_CREATE_PLAYLISTS_SONGS);
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

		if (oldVersion < 20170211) {
			// older versions of triggerFullMediaScan did this by mistake
			dbh.execSQL("UPDATE songs SET mtime=1 WHERE mtime=0");
		}

		if (oldVersion < 20170217) {
			dbh.execSQL(VIEW_CREATE_ALBUMARTISTS);
			dbh.execSQL(VIEW_CREATE_COMPOSERS);
			dbh.execSQL(VIEW_CREATE_SONGS_ALBUMS_ARTISTS_HUGE);
		}

		if (oldVersion >= 20170120 && oldVersion < 20170407) {
			dbh.execSQL("DROP TABLE preferences");
		}

		if (oldVersion >= 20170407 && oldVersion < 20170608) {
			// renames were buggy for some time -> get rid of duplicates
			dbh.execSQL("DELETE FROM "+MediaLibrary.TABLE_SONGS+" WHERE "+MediaLibrary.SongColumns._ID+" IN ("+
				"SELECT "+MediaLibrary.SongColumns._ID+" FROM "+MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS+" GROUP BY "+
				MediaLibrary.SongColumns._ID+" HAVING count("+MediaLibrary.SongColumns._ID+") > 1)");
			dbh.execSQL("DELETE FROM "+MediaLibrary.TABLE_ALBUMS+" WHERE "+MediaLibrary.AlbumColumns._ID+" NOT IN (SELECT "+MediaLibrary.SongColumns.ALBUM_ID+" FROM "+MediaLibrary.TABLE_SONGS+");");
			dbh.execSQL("DELETE FROM "+MediaLibrary.TABLE_GENRES_SONGS+" WHERE "+MediaLibrary.GenreSongColumns.SONG_ID+" NOT IN (SELECT "+MediaLibrary.SongColumns._ID+" FROM "+MediaLibrary.TABLE_SONGS+");");
			dbh.execSQL("DELETE FROM "+MediaLibrary.TABLE_GENRES+" WHERE "+MediaLibrary.GenreColumns._ID+" NOT IN (SELECT "+MediaLibrary.GenreSongColumns._GENRE_ID+" FROM "+MediaLibrary.TABLE_GENRES_SONGS+");");
			dbh.execSQL("DELETE FROM "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+" WHERE "+MediaLibrary.ContributorSongColumns.SONG_ID+" NOT IN (SELECT "+MediaLibrary.SongColumns._ID+" FROM "+MediaLibrary.TABLE_SONGS+");");
			dbh.execSQL("DELETE FROM "+MediaLibrary.TABLE_CONTRIBUTORS+" WHERE "+MediaLibrary.ContributorColumns._ID+" NOT IN (SELECT "+MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID+" FROM "+MediaLibrary.TABLE_CONTRIBUTORS_SONGS+");");
		}

		if (oldVersion < 20170619) {
			// Android 4.x tends to not use idx_contributors_songs, resulting in full table scans.
			// We will force the use of this index on views doing a LEFT JOIN as it doesn't cause
			// any harm to newer sqlite versions. (We know that this is the best index to use).
			dbh.execSQL("DROP VIEW "+MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS);
			dbh.execSQL("DROP VIEW "+MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS_HUGE);
			dbh.execSQL("DROP VIEW "+MediaLibrary.VIEW_PLAYLISTS_SONGS);
			dbh.execSQL(VIEW_CREATE_SONGS_ALBUMS_ARTISTS);
			dbh.execSQL(VIEW_CREATE_SONGS_ALBUMS_ARTISTS_HUGE);
			dbh.execSQL(VIEW_CREATE_PLAYLISTS_SONGS);
		}

		if (oldVersion >= 20170211 && oldVersion < 20180129) {
			// Minor indexer changes - invalidate (but do not drop) all
			// existing entries.
			dbh.execSQL("UPDATE songs SET mtime=1");
		}

		if (oldVersion < 20180305) {
			dbh.execSQL("ALTER TABLE "+MediaLibrary.TABLE_SONGS+" ADD COLUMN "+MediaLibrary.SongColumns.FLAGS+" INTEGER NOT NULL DEFAULT 0 ");
		}

		if (oldVersion < 20180416) {
			// This adds NAME_SORT, so we need to pre-populate all keys.
			dbh.execSQL("ALTER TABLE "+MediaLibrary.TABLE_PLAYLISTS+" RENAME TO _migrate");
			dbh.execSQL(MediaSchema.DATABASE_CREATE_PLAYLISTS);
			MediaMigrations.migrate_to_20180416(dbh, "_migrate", MediaLibrary.TABLE_PLAYLISTS);
			dbh.execSQL("DROP TABLE _migrate");
		}

		if (oldVersion < 20181021) {
			// Recreate view to include album duration
			dbh.execSQL("DROP VIEW " + MediaLibrary.VIEW_ALBUMS_ARTISTS);
			dbh.execSQL(VIEW_CREATE_ALBUMS_ARTISTS);
		}

		if (oldVersion < 20190210) {
			dbh.execSQL(VIEW_CREATE_PLAYLISTS);
		}
	}

}
