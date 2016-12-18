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
import android.content.ContentValues;
import android.database.Cursor;
import android.database.ContentObserver;
import android.provider.MediaStore;
import android.os.Environment;

import java.util.ArrayList;
import java.io.File;

public class MediaLibrary  {

	public static final String TABLE_SONGS                = "songs";
	public static final String TABLE_ALBUMS               = "albums";
	public static final String TABLE_CONTRIBUTORS         = "contributors";
	public static final String TABLE_CONTRIBUTORS_SONGS   = "contributors_songs";
	public static final String TABLE_GENRES               = "genres";
	public static final String TABLE_GENRES_SONGS         = "genres_songs";
	public static final String TABLE_PLAYLISTS            = "playlists";
	public static final String TABLE_PLAYLISTS_SONGS      = "playlists_songs";
	public static final String VIEW_ARTISTS               = "_artists";
	public static final String VIEW_ALBUMS_ARTISTS        = "_albums_artists";
	public static final String VIEW_SONGS_ALBUMS_ARTISTS  = "_songs_albums_artists";
	public static final String VIEW_PLAYLIST_SONGS        = "_playlists_songs";

	public static final int ROLE_ARTIST                   = 0;
	public static final int ROLE_COMPOSER                 = 1;

	/**
	 * Our static backend instance
	 */
	private static MediaLibraryBackend sBackend;
	/**
	 * An instance to the created scanner thread during our own creation
	 */
	private static MediaScanner sScanner;
	/**
	 * The observer to call-back during database changes
	 */
	private static ContentObserver sContentObserver;

	private static MediaLibraryBackend getBackend(Context context) {
		if (sBackend == null) {
			// -> unlikely
//			synchronized(sLock) {
				if (sBackend == null) {
					sBackend = new MediaLibraryBackend(context);

					sScanner = new MediaScanner(sBackend);
					sScanner.startNativeLibraryScan(context);
					sScanner.startUpdateScan();
					for (File dir : discoverMediaPaths()) {
						sScanner.startFullScan(dir);
					}
				}
//			}
		}
		return sBackend;
	}

	/**
	 * Registers a new content observer for the media library
	 *
	 * @param context the context to use
	 * @param observer the content observer we are going to call on changes
	 */
	public static void registerContentObserver(ContentObserver observer) {
		if (sContentObserver == null) {
			sContentObserver = observer;
		} else {
			throw new IllegalStateException("ContentObserver was already registered");
		}
	}

	/**
	 * Broadcasts a change to the observer, which will queue and dispatch
	 * the event to any registered observer
	 */
	static void notifyObserver() {
		if (sContentObserver != null)
			sContentObserver.onChange(true);
	}

	/**
	 * Perform a media query on the database, returns a cursor
	 *
	 * @param context the context to use
	 * @param table the table to query, one of MediaLibrary.TABLE_*
	 * @param projection the columns to returns in this query
	 * @param selection the selection (WHERE) to use
	 * @param selectionArgs arguments for the selection
	 * @param orderBy how the result should be sorted
	 */
	public static Cursor queryLibrary(Context context, String table, String[] projection, String selection, String[] selectionArgs, String orderBy) {
		return getBackend(context).query(false, table, projection, selection, selectionArgs, null, null, orderBy, null);
	}

	/**
	 * Removes a single song from the database
	 *
	 * @param context the context to use
	 * @param id the song id to delete
	 * @return the number of affected rows
	 */
	public static int removeSong(Context context, long id) {
		int rows = getBackend(context).delete(TABLE_SONGS, SongColumns._ID+"="+id, null);

		if (rows > 0) {
			getBackend(context).cleanOrphanedEntries();
			notifyObserver();
		}
		return rows;
	}

	/**
	 * Updates the play or skipcount of a song
	 *
	 * @param context the context to use
	 * @param id the song id to update
	 * @return boolean true if song was played, false if skipped
	 */
	public static void updateSongPlayCounts(Context context, long id, boolean played) {
		final String column = played ? MediaLibrary.SongColumns.PLAYCOUNT : MediaLibrary.SongColumns.SKIPCOUNT;
		String selection = MediaLibrary.SongColumns._ID+"="+id;
		getBackend(context).execSQL("UPDATE "+MediaLibrary.TABLE_SONGS+" SET "+column+"="+column+"+1 WHERE "+selection);
	}

	/**
	 * Creates a new empty playlist
	 *
	 * @param context the context to use
	 * @param name the name of the new playlist
	 * @return long the id of the created playlist, -1 on error
	 */
	public static long createPlaylist(Context context, String name) {
		ContentValues v = new ContentValues();
		v.put(MediaLibrary.PlaylistColumns._ID, hash63(name));
		v.put(MediaLibrary.PlaylistColumns.NAME, name);
		long id = getBackend(context).insert(MediaLibrary.TABLE_PLAYLISTS, null, v);

		if (id != -1)
			notifyObserver();
		return id;
	}

	/**
	 * Deletes a playlist and all of its child elements
	 *
	 * @param context the context to use
	 * @param id the playlist id to delete
	 * @return boolean true if the playlist was deleted
	 */
	public static boolean removePlaylist(Context context, long id) {
		// first, wipe all songs
		removeFromPlaylist(context, MediaLibrary.PlaylistSongColumns.PLAYLIST_ID+"="+id, null);
		int rows = getBackend(context).delete(MediaLibrary.TABLE_PLAYLISTS, MediaLibrary.PlaylistColumns._ID+"="+id, null);
		boolean removed = (rows > 0);

		if (removed)
			notifyObserver();
		return removed;
	}

	/**
	 * Adds a batch of songs to a playlist
	 *
	 * @param context the context to use
	 * @param playlistId the id of the playlist parent
	 * @param ids an array list with the song ids to insert
	 * @return the number of added items
	 */
	public static int addToPlaylist(Context context, long playlistId, ArrayList<Long> ids) {
		long pos = 0;
		// First we need to get the position of the last item
		String[] projection = { MediaLibrary.PlaylistSongColumns.POSITION };
		String selection = MediaLibrary.PlaylistSongColumns.PLAYLIST_ID+"="+playlistId;
		String order = MediaLibrary.PlaylistSongColumns.POSITION+" DESC";
		Cursor cursor = queryLibrary(context, MediaLibrary.TABLE_PLAYLISTS_SONGS, projection, selection, null, order);
		if (cursor.moveToFirst())
			pos = cursor.getLong(0) + 1;
		cursor.close();

		ArrayList<ContentValues> bulk = new ArrayList<ContentValues>();
		for (Long id : ids) {
			if (getBackend(context).getSongMtime(id) == 0)
				continue;

			ContentValues v = new ContentValues();
			v.put(MediaLibrary.PlaylistSongColumns.PLAYLIST_ID, playlistId);
			v.put(MediaLibrary.PlaylistSongColumns.SONG_ID, id);
			v.put(MediaLibrary.PlaylistSongColumns.POSITION, pos);
			bulk.add(v);
			pos++;
		}
		int rows = getBackend(context).bulkInsert(MediaLibrary.TABLE_PLAYLISTS_SONGS, null, bulk);

		if (rows > 0)
			notifyObserver();
		return rows;
	}

	/**
	 * Removes a set of items from a playlist
	 *
	 * @param context the context to use
	 * @param selection the selection for the items to drop
	 * @param selectionArgs arguments for `selection'
	 * @return the number of deleted rows, -1 on error
	 */
	public static int removeFromPlaylist(Context context, String selection, String[] selectionArgs) {
		int rows = getBackend(context).delete(MediaLibrary.TABLE_PLAYLISTS_SONGS, selection, selectionArgs);

		if (rows > 0)
			notifyObserver();
		return rows;
	}

	/**
	 * Renames an existing playlist
	 *
	 * @param context the context to use
	 * @param playlistId the id of the playlist to rename
	 * @param newName the new name of the playlist
	 * @return the id of the new playlist, -1 on error
	 */
	public static long renamePlaylist(Context context, long playlistId, String newName) {
		long newId = createPlaylist(context, newName);
		if (newId >= 0) {
			String selection = MediaLibrary.PlaylistSongColumns.PLAYLIST_ID+"="+playlistId;
			ContentValues v = new ContentValues();
			v.put(MediaLibrary.PlaylistSongColumns.PLAYLIST_ID, newId);
			getBackend(context).update(MediaLibrary.TABLE_PLAYLISTS_SONGS, v, selection, null);
			removePlaylist(context, playlistId);
		}

		if (newId != -1)
			notifyObserver();
		return newId;
	}

	/**
	 * Moves an item in a playlist. Note: both items should be in the
	 * same playlist - 'fun things' will happen otherwise.
	 *
	 * @param context the context to use
	 * @param from the _id of the 'dragged' element
	 * @param to the _id of the 'repressed' element
	 */
	public static void movePlaylistItem(Context context, long from, long to) {
		long fromPos, toPos, playlistId;

		String[] projection = { MediaLibrary.PlaylistSongColumns.POSITION, MediaLibrary.PlaylistSongColumns.PLAYLIST_ID };
		String selection = MediaLibrary.PlaylistSongColumns._ID+"=";

		// Get playlist id and position of the 'from' item
		Cursor cursor = queryLibrary(context, MediaLibrary.TABLE_PLAYLISTS_SONGS, projection, selection+Long.toString(from), null, null);
		cursor.moveToFirst();
		fromPos = cursor.getLong(0);
		playlistId = cursor.getLong(1);
		cursor.close();

		// Get position of the target item
		cursor = queryLibrary(context, MediaLibrary.TABLE_PLAYLISTS_SONGS, projection, selection+Long.toString(to), null, null);
		cursor.moveToFirst();
		toPos = cursor.getLong(0);
		cursor.close();

		// Moving down -> We actually want to be below the target
		if (toPos > fromPos)
			toPos++;

		// shift all rows +1
		String setArg = MediaLibrary.PlaylistSongColumns.POSITION+"="+MediaLibrary.PlaylistSongColumns.POSITION+"+1";
		selection = MediaLibrary.PlaylistSongColumns.PLAYLIST_ID+"="+playlistId+" AND "+MediaLibrary.PlaylistSongColumns.POSITION+" >= "+toPos;
		getBackend(context).execSQL("UPDATE "+MediaLibrary.TABLE_PLAYLISTS_SONGS+" SET "+setArg+" WHERE "+selection);

		ContentValues v = new ContentValues();
		v.put(MediaLibrary.PlaylistSongColumns.POSITION, toPos);
		selection = MediaLibrary.PlaylistSongColumns._ID+"="+from;
		getBackend(context).update(MediaLibrary.TABLE_PLAYLISTS_SONGS, v, selection, null);

		notifyObserver();
	}


	/**
	 * Returns the 'key' of given string used for sorting and searching
	 *
	 * @param name the string to convert
	 * @return the the key of given name
	 */
	public static String keyFor(String name) {
		return MediaStore.Audio.keyFor(name);
	}

	/**
	 * Simple 63 bit hash function for strings
	 *
	 * @param str the string to hash
	 * @return a positive long
	 */
	public static long hash63(String str) {
		if (str == null)
			return 0;

		long hash = 0;
		int len = str.length();
		for (int i = 0; i < len ; i++) {
			hash = 31*hash + str.charAt(i);
		}
		return (hash < 0 ? hash*-1 : hash);
	}

	/**
	 * Returns the guessed media paths for this device
	 *
	 * @return array with guessed directories
	 */
	public static File[] discoverMediaPaths() {
		ArrayList<File> scanTargets = new ArrayList<File>();

		// this should always exist
		scanTargets.add(Environment.getExternalStorageDirectory());

		// this *may* exist
		File sdCard = new File("/storage/sdcard1");
		if (sdCard.isDirectory())
			scanTargets.add(sdCard);

		return scanTargets.toArray(new File[scanTargets.size()]);
	}

	// Columns of Song entries
	public interface SongColumns {
		/**
		 * The id of this song in the database
		 */
		public static final String _ID = "_id";
		/**
		 * The title of this song
		 */
		public static final String TITLE = "title";
		/**
		 * The sortable title of this song
		 */
		public static final String TITLE_SORT = "title_sort";
		/**
		 * The position in the album of this song
		 */
		public static final String SONG_NUMBER = "song_num";
		/**
		 * The album where this song belongs to
		 */
		public static final String ALBUM_ID = "album_id";
		/**
		 * The year of this song
		 */
		public static final String YEAR = "year";
		/**
		 * How often the song was played
		 */
		public static final String PLAYCOUNT = "playcount";
		/**
		 * How often the song was skipped
		 */
		public static final String SKIPCOUNT = "skipcount";
		/**
		 * The duration of this song
		 */
		public static final String DURATION = "duration";
		/**
		 * The path to the music file
		 */
		public static final String PATH = "path";
		/**
		 * The mtime of this item
		 */
		public static final String MTIME = "mtime";
	}

	// Columns of Album entries
	public interface AlbumColumns {
		/**
		 * The id of this album in the database
		 */
		public static final String _ID = SongColumns._ID;
		/**
		 * The title of this album
		 */
		public static final String ALBUM = "album";
		/**
		 * The sortable title of this album
		 */
		public static final String ALBUM_SORT = "album_sort";
		/**
		 * The disc number of this album
		 */
		public static final String DISC_NUMBER = "disc_num";
		/**
		 * The primary contributor / artist reference for this album
		 */
		public static final String PRIMARY_ARTIST_ID = "primary_artist_id";
		/**
		 * The year of this album
		 */
		public static final String PRIMARY_ALBUM_YEAR = "primary_album_year";
		/**
		 * The mtime of this item
		 */
		public static final String MTIME = "mtime";
	}

	// Columns of Contributors entries
	public interface ContributorColumns {
		/**
		 * The id of this contributor
		 */
		public static final String _ID = SongColumns._ID;
		/**
		 * The name of this contributor
		 */
		public static final String _CONTRIBUTOR = "_contributor";
		/**
		 * The sortable title of this contributor
		 */
		public static final String _CONTRIBUTOR_SORT = "_contributor_sort";
		/**
		 * The mtime of this item
		 */
		public static final String MTIME = "mtime";
		/**
		 * ONLY IN VIEWS - the artist
		 */
		public static final String ARTIST = "artist";
		/**
		 * ONLY IN VIEWS - the artist_sort key
		 */
		public static final String ARTIST_SORT = "artist_sort";
		/**
		 * ONLY IN VIEWS - the artist id
		 */
		public static final String ARTIST_ID = "artist_id";
	}

	// Songs <-> Contributor mapping
	public interface ContributorSongColumns {
		/**
		 * The role of this entry
		 */
		public static final String ROLE = "role";
		/**
		 * the contirbutor id this maps to
		 */
		public static final String _CONTRIBUTOR_ID = "_contributor_id";
		/**
		 * the song this maps to
		 */
		public static final String SONG_ID = "song_id";
	}

	// Columns of Genres entries
	public interface GenreColumns {
		/**
		 * The id of this genre
		 */
		public static final String _ID = SongColumns._ID;
		/**
		 * The name of this genre
		 */
		public static final String _GENRE = "_genre";
		/**
		 * The sortable title of this genre
		 */
		public static final String _GENRE_SORT = "_genre_sort";
	}

	// Songs <-> Contributor mapping
	public interface GenreSongColumns {
		/**
		 * the genre id this maps to
		 */
		public static final String _GENRE_ID = "_genre_id";
		/**
		 * the song this maps to
		 */
		public static final String SONG_ID = "song_id";
	}

	// Playlists
	public interface PlaylistColumns {
		/**
		 * The id of this playlist
		 */
		public static final String _ID = SongColumns._ID;
		/**
		 * The name of this playlist
		 */
		 public static final String NAME = "name";
	}

	// Song <-> Playlist mapping
	public interface PlaylistSongColumns {
		/**
		 * The ID of this entry
		 */
		public static final String _ID = SongColumns._ID;
		/**
		 * The playlist this entry belongs to
		 */
		public static final String PLAYLIST_ID = "playlist_id";
		/**
		 * The song this entry references to
		 */
		public static final String SONG_ID = "song_id";
		/**
		 * The order attribute
		 */
		public static final String POSITION = "position";
	}

}
