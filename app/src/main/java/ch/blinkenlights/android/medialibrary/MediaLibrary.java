/*
 * Copyright (C) 2016-2021 Adrian Ulrich <adrian@blinkenlights.ch>
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
import android.provider.MediaStore;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;

import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class MediaLibrary  {

	public static final String TABLE_SONGS                    = "songs";
	public static final String TABLE_ALBUMS                   = "albums";
	public static final String TABLE_CONTRIBUTORS             = "contributors";
	public static final String TABLE_CONTRIBUTORS_SONGS       = "contributors_songs";
	public static final String TABLE_GENRES                   = "genres";
	public static final String TABLE_GENRES_SONGS             = "genres_songs";
	public static final String TABLE_PLAYLISTS                = "playlists";
	public static final String TABLE_PLAYLISTS_SONGS          = "playlists_songs";
	public static final String VIEW_ARTISTS                   = "_artists";
	public static final String VIEW_ALBUMARTISTS              = "_albumartists";
	public static final String VIEW_COMPOSERS                 = "_composers";
	public static final String VIEW_ALBUMS_ARTISTS            = "_albums_artists";
	public static final String VIEW_SONGS_ALBUMS_ARTISTS      = "_songs_albums_artists";
	public static final String VIEW_SONGS_ALBUMS_ARTISTS_HUGE = "_songs_albums_artists_huge";
	public static final String VIEW_PLAYLISTS                 = "_playlists";
	public static final String VIEW_PLAYLISTS_SONGS           = "_playlists_songs";

	public static final int ROLE_ARTIST                   = 0;
	public static final int ROLE_COMPOSER                 = 1;
	public static final int ROLE_ALBUMARTIST              = 2;

	public static final int SONG_FLAG_OUTDATED            = (1 << 0); // entry in library should get rescanned.
	public static final int SONG_FLAG_NO_ALBUM            = (1 << 1); // file had no real album tag.
	public static final int SONG_FLAG_NO_ARTIST           = (1 << 2); // file had no real artist tag.

	public static final String PREFERENCES_FILE = "_prefs-v1.obj";

	/**
	 * Options used by the MediaScanner class
	 */
	public static class Preferences implements Serializable {
		public boolean forceBastp;
		public boolean groupAlbumsByFolder;
		public ArrayList<String> mediaFolders;
		public ArrayList<String> blacklistedFolders;
		int _nativeLibraryCount;
		int _nativeLastMtime;
	}

	/**
	 * The progress of a currently scan, if any
	 * is running
	 */
	public static class ScanProgress {
		public boolean isRunning;
		public String lastFile;
		public int seen;
		public int changed;
		public int total;
	}

	/**
	 * Cached preferences, may be null
	 */
	private static Preferences sPreferences;
	/**
	 * Our static backend instance
	 */
	private volatile static MediaLibraryBackend sBackend;
	/**
	 * An instance to the created scanner thread during our own creation
	 */
	private static MediaScanner sScanner;
	/**
	 * The observer to call-back during database changes
	 */
	private static final ArrayList<LibraryObserver> sLibraryObservers = new ArrayList<LibraryObserver>(2);
	/**
	 * The lock we are using during object creation
	 */
	private static final Object[] sWait = new Object[0];

	private static MediaLibraryBackend getBackend(Context context) {
		if (sBackend == null) {
			// -> unlikely
			synchronized(sWait) {
				if (sBackend == null) {
					sBackend = new MediaLibraryBackend(context.getApplicationContext());
					sScanner = new MediaScanner(context.getApplicationContext(), sBackend);
					sScanner.startQuickScan(50);
				}
			}
		}
		return sBackend;
	}

	/**
	 * Returns the scanner preferences
	 *
	 * @param context the context to use
	 * @return MediaLibrary.Preferences
	 */
	public static MediaLibrary.Preferences getPreferences(Context context) {
		MediaLibrary.Preferences prefs = sPreferences;
		if (prefs == null) {
			try (ObjectInputStream ois = new ObjectInputStream(context.openFileInput(PREFERENCES_FILE))) {
				prefs = (MediaLibrary.Preferences)ois.readObject();
			} catch (Exception e) {
				Log.w("VanillaMusic", "Returning default media-library preferences due to error: "+ e);
			}

			if (prefs == null) {
				prefs = new MediaLibrary.Preferences();
				prefs.forceBastp = true; // Auto enable for new installations
			}

			if (prefs.mediaFolders == null || prefs.mediaFolders.size() == 0)
				prefs.mediaFolders = discoverDefaultMediaPaths(context);

			if (prefs.blacklistedFolders == null) // we allow this to be empty, but it must not be null.
				prefs.blacklistedFolders = discoverDefaultBlacklistedPaths(context);

			sPreferences = prefs; // cached for frequent access
		}
		return prefs;
	}

	/**
	 * Returns the guessed media paths for this device
	 *
	 * @return array with guessed directories
	 */
	private static ArrayList<String> discoverDefaultMediaPaths(Context context) {
		HashSet<String> defaultPaths = new HashSet<>();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
			// Running on a platform which enforces scoped access.
			// Add the external storage dir by default but also try to guess good external paths.
			defaultPaths.add(Environment.getExternalStorageDirectory().getAbsolutePath());

			for (File file : context.getExternalMediaDirs()) {
				// Returns 'null' on some Samsung devices?!.
				if (file == null)
					continue;

				defaultPaths.add(file.getAbsolutePath());
				// Check if we have access to a subdir which contains the 'Music' folder.
				while ( (file = file.getParentFile()) != null) {
					File candidate = new File(file, "Music");
					if (candidate.exists() && !defaultPaths.contains(file.getAbsolutePath())) {
						defaultPaths.add(candidate.getAbsolutePath());
						break;
					}
				}
			}
		} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			// Try to discover media paths using getExternalMediaDirs() on 5.x and newer
			for (File file : context.getExternalMediaDirs()) {
				// Seems to happen on some Samsung 5.x devices. :-(
				if (file == null)
					continue;

				String path = file.getAbsolutePath();
				int match = path.indexOf("/Android/media/"); // From Environment.DIR_ANDROID + Environment.DIR_MEDIA (both hidden)
				if (match >= 0)
					defaultPaths.add(path.substring(0, match));
			}
		}

		// Fall back to old API and some guessing if nothing was found (yet).
		if (defaultPaths.size() == 0) {
			// this should always exist
			defaultPaths.add(Environment.getExternalStorageDirectory().getAbsolutePath());
			// this *may* exist
			File sdCard = new File("/storage/sdcard1");
			if (sdCard.isDirectory())
				defaultPaths.add(sdCard.getAbsolutePath());
		}
		return new ArrayList<String>(defaultPaths);
	}

	/**
	 * Returns default paths which should be blacklisted
	 *
	 * @return array with guessed blacklist
	 */
	private static ArrayList<String> discoverDefaultBlacklistedPaths(Context context) {
		final String[] defaultBlacklistPostfix = { "Android/data", "Android/media", "Alarms", "Notifications", "Ringtones", "media/audio" };
		ArrayList<String> defaultPaths = new ArrayList<>();

		for (String path : discoverDefaultMediaPaths(context)) {
			for (int i = 0; i < defaultBlacklistPostfix.length; i++) {
				File guess = new File(path + "/" + defaultBlacklistPostfix[i]);
				if (guess.isDirectory())
					defaultPaths.add(guess.getAbsolutePath());
			}
		}
		return defaultPaths;
	}

	/**
	 * Updates the scanner preferences
	 *
	 * @param context the context to use
	 * @param prefs the preferences to store - this will update ALL fields, so you are
	 *              supposed to first call getPreferences() to obtain the current values
	 */
	public static void setPreferences(Context context, MediaLibrary.Preferences prefs) {
		MediaLibraryBackend backend = getBackend(context);

		try (ObjectOutputStream oos = new ObjectOutputStream(context.openFileOutput(PREFERENCES_FILE, 0))) {
			oos.writeObject(prefs);
		} catch (Exception e) {
			Log.w("VanillaMusic", "Failed to store media preferences: " + e);
		}

		sPreferences = prefs;
	}

	/**
	 * Triggers a rescan of the library
	 *
	 * @param context the context to use
	 * @param forceFull starts a full / slow scan if true
	 * @param drop drop the existing library if true
	 */
	public static void startLibraryScan(Context context, boolean forceFull, boolean drop) {
		MediaLibraryBackend backend = getBackend(context); // also initialized sScanner
		if (drop) {
			sScanner.flushDatabase();
		}

		if (forceFull) {
			sScanner.startFullScan();
		} else {
			sScanner.startNormalScan();
		}
	}

	/**
	 * Stops any running scan
	 *
	 * @param context the context to use
	 */
	public static void abortLibraryScan(Context context) {
		MediaLibraryBackend backend = getBackend(context); // also initialized sScanner
		sScanner.abortScan();
	}

	/**
	 * Whacky function to get the current scan progress
	 *
	 * @param context the context to use
	 * @return a description of the progress
	 */
	public static MediaLibrary.ScanProgress describeScanProgress(Context context) {
		MediaLibraryBackend backend = getBackend(context); // also initialized sScanner
		return sScanner.describeScanProgress();
	}

	/**
	 * Dumps a copy of the media database to a specified path.
	 *
	 * @param context the context to use.
	 * @param dst the destination path of the db dump.
	 */
	public static void createDebugDump(Context context, String dst) {
		final String src = context.getDatabasePath(MediaLibraryBackend.DATABASE_NAME).getPath();
		try {
			try (InputStream in = new FileInputStream(src)) {
				try (OutputStream out = new FileOutputStream(dst)) {
					byte[] buffer = new byte[4096];
					int len = 0;
					while ((len = in.read(buffer)) > 0) {
						out.write(buffer, 0, len);
					}
				}
			}
		} catch (Exception e) {
			Log.v("VanillaMusic", "Debug dump failed: "+e);
		}
	}

	/**
	 * Registers a new library observer for the media library
	 *
	 * The MediaLibrary will call `onLibraryChanged()` if
	 * the media library changed.
	 *
	 * `ongoing` will be set to `true` if you are expected to receive
	 * more updates soon. A value of `false` indicates that no
	 * scan is going on.
	 *
	 * @param observer the content observer we are going to call on changes
	 */
	public static void registerLibraryObserver(LibraryObserver observer) {
		if (sLibraryObservers.contains(observer))
			throw new IllegalStateException("LibraryObserver was already registered");

		sLibraryObservers.add(observer);
	}

	/**
	 * Unregisters a library observer which was previously registered
	 * by calling registerLibraryObserver().
	 *
	 * @param observer the content observer to unregister.
	 */
	public static void unregisterLibraryObserver(LibraryObserver observer) {
		boolean removed = sLibraryObservers.remove(observer);

		if (!removed)
			throw new IllegalArgumentException("This library observer was never registered!");
	}

	/**
	 * Broadcasts a change to all registered observers
	 *
	 * @param type the type of this event.
	 * @param id the id of type which changed, -1 if unknown
	 * @param ongoing whether or not to expect more of these updates soon
	 */
	public static void notifyObserver(LibraryObserver.Type type, long id, boolean ongoing) {
		ArrayList<LibraryObserver> list = sLibraryObservers;
		for (int i = list.size(); --i != -1; )
			list.get(i).onChange(type, id, ongoing);
	}

	/**
	 * Perform a media query on the database, returns a cursor
	 *
	 * @param context the context to use
	 * @param table the table to query, one of MediaLibrary.TABLE_*
	 * @param columns the columns to returns in this query
	 * @param criteria the criteria used as part of the WHERE clause
	 * @param criteriaArgs arguments for the criteria. You may include question marks (?) in the criteria which will be replaced by the values from the args in the order they appear.
	 * @param orderBy how the result should be sorted
	 */
	public static Cursor queryLibrary(Context context, String table, String[] columns, String criteria, String[] criteriaArgs, String orderBy) {
		return getBackend(context).query(false, table, columns, criteria, criteriaArgs, null, null, orderBy, null);
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
			getBackend(context).cleanOrphanedEntries(true);
			notifyObserver(LibraryObserver.Type.SONG, id, false);
			notifyObserver(LibraryObserver.Type.PLAYLIST, LibraryObserver.Value.UNKNOWN, false);
		}
		return rows;
	}

	/**
	 * Updates the play or skipcount of a song
	 *
	 * @param context the context to use
	 * @param id the song id to update
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
		v.put(MediaLibrary.PlaylistColumns.NAME_SORT, keyFor(name));
		long id = getBackend(context).insert(MediaLibrary.TABLE_PLAYLISTS, null, v);

		if (id != -1)
			notifyObserver(LibraryObserver.Type.PLAYLIST, id, false);
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
			notifyObserver(LibraryObserver.Type.PLAYLIST, id, false);
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
		String[] columns = { MediaLibrary.PlaylistSongColumns.POSITION };
		String criteria = MediaLibrary.PlaylistSongColumns.PLAYLIST_ID+"="+playlistId;
		String order = MediaLibrary.PlaylistSongColumns.POSITION+" DESC";
		Cursor cursor = queryLibrary(context, MediaLibrary.TABLE_PLAYLISTS_SONGS, columns, criteria, null, order);
		if (cursor.moveToFirst())
			pos = cursor.getLong(0) + 1;
		cursor.close();

		ArrayList<ContentValues> bulk = new ArrayList<>();
		for (Long id : ids) {
			if (getBackend(context).getColumnFromSongId(MediaLibrary.SongColumns.MTIME, id) == 0) // no mtime? song does not exist.
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
			notifyObserver(LibraryObserver.Type.PLAYLIST, playlistId, false);
		return rows;
	}

	/**
	 * Removes a set of items from a playlist
	 *
	 * @param context the context to use
	 * @param criteria the selection for the items to drop
	 * @param criteriaArgs arguments for `selection'
	 * @return the number of deleted rows, -1 on error
	 */
	public static int removeFromPlaylist(Context context, String criteria, String[] criteriaArgs) {
		// Grab the list of affected playlist id's before performing a delete.
		// These are needed for the observer notification.
		ArrayList<Long> playlists = new ArrayList<>();
		String[] columns = { "DISTINCT("+MediaLibrary.PlaylistSongColumns.PLAYLIST_ID+")" };
		Cursor cursor = queryLibrary(context, MediaLibrary.TABLE_PLAYLISTS_SONGS, columns, criteria, criteriaArgs, null);
		while(cursor.moveToNext()) {
			playlists.add(cursor.getLong(0));
		}
		cursor.close();

		int affected = 0;
		if (playlists.size() > 0) {
			affected = getBackend(context).delete(MediaLibrary.TABLE_PLAYLISTS_SONGS, criteria, criteriaArgs);
			for (long id : playlists) {
				notifyObserver(LibraryObserver.Type.PLAYLIST, id, false);
			}
		}

		return affected;
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

		if (newId != -1) {
			notifyObserver(LibraryObserver.Type.PLAYLIST, playlistId, false);
			notifyObserver(LibraryObserver.Type.PLAYLIST, newId, false);
		}
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

		String[] columns = { MediaLibrary.PlaylistSongColumns.POSITION, MediaLibrary.PlaylistSongColumns.PLAYLIST_ID };
		String criteria = MediaLibrary.PlaylistSongColumns._ID+"=";

		// Get playlist id and position of the 'from' item
		Cursor cursor = queryLibrary(context, MediaLibrary.TABLE_PLAYLISTS_SONGS, columns, criteria+Long.toString(from), null, null);
		cursor.moveToFirst();
		fromPos = cursor.getLong(0);
		playlistId = cursor.getLong(1);
		cursor.close();

		// Get position of the target item
		cursor = queryLibrary(context, MediaLibrary.TABLE_PLAYLISTS_SONGS, columns, criteria+Long.toString(to), null, null);
		cursor.moveToFirst();
		toPos = cursor.getLong(0);
		cursor.close();

		// Moving down -> We actually want to be below the target
		if (toPos > fromPos)
			toPos++;

		// shift all rows +1
		String setArg = MediaLibrary.PlaylistSongColumns.POSITION+"="+MediaLibrary.PlaylistSongColumns.POSITION+"+1";
		criteria = MediaLibrary.PlaylistSongColumns.PLAYLIST_ID+"="+playlistId+" AND "+MediaLibrary.PlaylistSongColumns.POSITION+" >= "+toPos;
		getBackend(context).execSQL("UPDATE "+MediaLibrary.TABLE_PLAYLISTS_SONGS+" SET "+setArg+" WHERE "+criteria);

		ContentValues v = new ContentValues();
		v.put(MediaLibrary.PlaylistSongColumns.POSITION, toPos);
		criteria = MediaLibrary.PlaylistSongColumns._ID+"="+from;
		getBackend(context).update(MediaLibrary.TABLE_PLAYLISTS_SONGS, v, criteria, null);

		notifyObserver(LibraryObserver.Type.PLAYLIST, playlistId, false);
	}

	/**
	 * Returns the number of songs in the music library
	 *
	 * @param context the context to use
	 * @return the number of songs
	 */
	public static int getLibrarySize(Context context) {
		int count = 0;
		Cursor cursor = queryLibrary(context, TABLE_SONGS, new String[]{"count(*)"}, null, null, null);
		if (cursor.moveToFirst())
			count = cursor.getInt(0);
		cursor.close();
		return count;
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

		// Remove invisible BOM
		if (len > 0 && str.charAt(0) == 65279) {
			str = str.substring(1);
			len--;
		}

		for (int i = 0; i < len ; i++) {
			hash = 31*hash + str.charAt(i);
		}
		return (hash < 0 ? hash*-1 : hash);
	}

	// Columns of Song entries
	public interface SongColumns {
		/**
		 * The id of this song in the database
		 */
		String _ID = "_id";
		/**
		 * The title of this song
		 */
		String TITLE = "title";
		/**
		 * The sortable title of this song
		 */
		String TITLE_SORT = "title_sort";
		/**
		 * The position in the album of this song
		 */
		String SONG_NUMBER = "song_num";
		/**
		 * The disc in a multi-disc album containing this song
		 */
		String DISC_NUMBER = "disc_num";
		/**
		 * The album where this song belongs to
		 */
		String ALBUM_ID = "album_id";
		/**
		 * The year of this song
		 */
		String YEAR = "year";
		/**
		 * How often the song was played
		 */
		String PLAYCOUNT = "playcount";
		/**
		 * How often the song was skipped
		 */
		String SKIPCOUNT = "skipcount";
		/**
		 * The duration of this song
		 */
		String DURATION = "duration";
		/**
		 * The path to the music file
		 */
		String PATH = "path";
		/**
		 * The mtime of this item
		 */
		String MTIME = "mtime";
		/**
		 * Various flags of this entry, see SONG_FLAG...
		 */
		String FLAGS = "_flags";
	}

	// Columns of Album entries
	public interface AlbumColumns {
		/**
		 * The id of this album in the database
		 */
		String _ID = SongColumns._ID;
		/**
		 * The title of this album
		 */
		String ALBUM = "album";
		/**
		 * The sortable title of this album
		 */
		String ALBUM_SORT = "album_sort";
		/**
		 * The primary contributor / artist reference for this album
		 */
		String PRIMARY_ARTIST_ID = "primary_artist_id";
		/**
		 * The year of this album
		 */
		String PRIMARY_ALBUM_YEAR = "primary_album_year";
		/**
		 * The mtime of this item
		 */
		String MTIME = "mtime";
	}

	// Columns of Contributors entries
	public interface ContributorColumns {
		/**
		 * The id of this contributor
		 */
		String _ID = SongColumns._ID;
		/**
		 * The name of this contributor
		 */
		String _CONTRIBUTOR = "_contributor";
		/**
		 * The sortable title of this contributor
		 */
		String _CONTRIBUTOR_SORT = "_contributor_sort";
		/**
		 * The mtime of this item
		 */
		String MTIME = "mtime";

		/**
		 * ONLY IN VIEWS - the artist
		 */
		String ARTIST = "artist";
		/**
		 * ONLY IN VIEWS - the artist_sort key
		 */
		String ARTIST_SORT = "artist_sort";
		/**
		 * ONLY IN VIEWS - the artist id
		 */
		String ARTIST_ID = "artist_id";

		/**
		 * ONLY IN VIEWS - the albumartist
		 */
		String ALBUMARTIST = "albumartist";
		/**
		 * ONLY IN VIEWS - the albumartist_sort key
		 */
		String ALBUMARTIST_SORT = "albumartist_sort";
		/**
		 * ONLY IN VIEWS - the albumartist id
		 */
		String ALBUMARTIST_ID = "albumartist_id";

		/**
		 * ONLY IN VIEWS - the composer
		 */
		String COMPOSER = "composer";
		/**
		 * ONLY IN VIEWS - the composer_sort key
		 */
		String COMPOSER_SORT = "composer_sort";
		/**
		 * ONLY IN VIEWS - the composer id
		 */
		String COMPOSER_ID = "composer_id";

	}

	// Songs <-> Contributor mapping
	public interface ContributorSongColumns {
		/**
		 * The role of this entry
		 */
		String ROLE = "role";
		/**
		 * the contirbutor id this maps to
		 */
		String _CONTRIBUTOR_ID = "_contributor_id";
		/**
		 * the song this maps to
		 */
		String SONG_ID = "song_id";
	}

	// Columns of Genres entries
	public interface GenreColumns {
		/**
		 * The id of this genre
		 */
		String _ID = SongColumns._ID;
		/**
		 * The name of this genre
		 */
		String _GENRE = "_genre";
		/**
		 * The sortable title of this genre
		 */
		String _GENRE_SORT = "_genre_sort";
	}

	// Songs <-> Contributor mapping
	public interface GenreSongColumns {
		/**
		 * the genre id this maps to
		 */
		String _GENRE_ID = "_genre_id";
		/**
		 * the song this maps to
		 */
		String SONG_ID = "song_id";
	}

	// Playlists
	public interface PlaylistColumns {
		/**
		 * The id of this playlist
		 */
		String _ID = SongColumns._ID;
		/**
		 * The name of this playlist
		 */
		 String NAME = "name";
		/**
		 * Sortable column for name
		 */
		String NAME_SORT = "name_sort";
	}

	// Song <-> Playlist mapping
	public interface PlaylistSongColumns {
		/**
		 * The ID of this entry
		 */
		String _ID = SongColumns._ID;
		/**
		 * The playlist this entry belongs to
		 */
		String PLAYLIST_ID = "playlist_id";
		/**
		 * The song this entry references to
		 */
		String SONG_ID = "song_id";
		/**
		 * The order attribute
		 */
		String POSITION = "position";
	}

	// Preference keys
	public interface PreferenceColumns {
		/**
		 * The key of this preference
		 */
		String KEY = "key";
		/**
		 * The value of this preference
		 */
		String VALUE = "value";
	}
}
