/*
 * Copyright (C) 2010, 2011 Christopher Eby <kreed@kreed.org>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package ch.blinkenlights.android.vanilla;

import ch.blinkenlights.android.medialibrary.MediaLibrary;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Vector;
import java.util.zip.CRC32;

import android.util.Log;

import junit.framework.Assert;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.database.MatrixCursor;
import android.media.MediaMetadataRetriever;
import android.util.Log;
import android.widget.Toast;


/**
 * Provides some static Song/MediaStore-related utility functions.
 */
public class MediaUtils {
	/**
	 * A special invalid media type.
	 */
	public static final int TYPE_INVALID = -1;
	/**
	 * Type indicating an id represents an artist.
	 */
	public static final int TYPE_ARTIST = 0;
	/**
	 * Type indicating an id represents an album.
	 */
	public static final int TYPE_ALBUM = 1;
	/**
	 * Type indicating an id represents a song.
	 */
	public static final int TYPE_SONG = 2;
	/**
	 * Type indicating an id represents a playlist.
	 */
	public static final int TYPE_PLAYLIST = 3;
	/**
	 * Type indicating ids represent genres.
	 */
	public static final int TYPE_GENRE = 4;
	/**
	 * Special type for files and folders. Most methods do not accept this type
	 * since files have no MediaStore id and require special handling.
	 */
	public static final int TYPE_FILE = 5;
	/**
	 * The number of different valid media types.
	 */
	public static final int TYPE_COUNT = 6;

	/**
	 * The default sort order for media queries. First artist, then album, then
	 * song number.
	 */
	private static final String DEFAULT_SORT = "artist_sort,album_sort,disc_num,song_num";

	/**
	 * The default sort order for albums. First the album, then songnumber
	 */
	private static final String ALBUM_SORT = "artist_sort,disc_num,song_num";

	/**
	 * The default sort order for files. Simply use the path
	 */
	private static final String FILE_SORT = "path";

	/**
	 * Cached random instance.
	 */
	private static Random sRandom;

	/**
	 * Shuffled list of all ids in the library.
	 */
	private static long[] sAllSongs;
	private static int sAllSongsIdx;

	/**
	 * Total number of songs in the music library, or -1 for uninitialized.
	 */
	private static int sSongCount = -1;

	/**
	 * Returns a cached random instanced, creating it if necessary.
	 */
	public static Random getRandom()
	{
		if (sRandom == null)
			sRandom = new Random();
		return sRandom;
	}

	/**
	 * Builds a query that will return all the songs represented by the given
	 * parameters.
	 *
	 * @param type MediaUtils.TYPE_ARTIST, TYPE_ALBUM, or TYPE_SONG.
	 * @param id The MediaStore id of the song, artist, or album.
	 * @param projection The columns to query.
	 * @param select An extra selection to pass to the query, or null.
	 * @return The initialized query.
	 */
	private static QueryTask buildMediaQuery(int type, long id, String[] projection, String select)
	{
		StringBuilder selection = new StringBuilder();
		String sort = DEFAULT_SORT;

		switch (type) {
		case TYPE_SONG:
			selection.append(MediaLibrary.SongColumns._ID);
			break;
		case TYPE_ARTIST:
			selection.append(MediaLibrary.ContributorColumns.ARTIST_ID);
			break;
		case TYPE_ALBUM:
			selection.append(MediaLibrary.SongColumns.ALBUM_ID);
			sort = ALBUM_SORT;
			break;
		case TYPE_GENRE:
			selection.append(MediaLibrary.GenreSongColumns._GENRE_ID);
			break;
		default:
			throw new IllegalArgumentException("Invalid type specified: " + type);
		}

		selection.append('=');
		selection.append(id);

		if (select != null) {
			selection.append(" AND ");
			selection.append(select);
		}

		QueryTask result = new QueryTask(MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS, projection, selection.toString(), null, sort);
		result.type = type;
		return result;
	}

	/**
	 * Builds a query that will return all the songs in the playlist with the
	 * given id.
	 *
	 * @param id The id of the playlist in MediaStore.Audio.Playlists.
	 * @param projection The columns to query.
	 * @return The initialized query.
	 */
	public static QueryTask buildPlaylistQuery(long id, String[] projection) {
		String sort = MediaLibrary.PlaylistSongColumns.POSITION;
		String selection = MediaLibrary.PlaylistSongColumns.PLAYLIST_ID+"="+id;
		QueryTask result = new QueryTask(MediaLibrary.VIEW_PLAYLIST_SONGS, projection, selection, null, sort);
		result.type = TYPE_PLAYLIST;
		return result;
	}

	/**
	 * Builds a query with the given information.
	 *
	 * @param type Type the id represents. Must be one of the Song.TYPE_*
	 * constants.
	 * @param id The id of the element in the MediaStore content provider for
	 * the given type.
	 * @param selection An extra selection to be passed to the query. May be
	 * null. Must not be used with type == TYPE_SONG or type == TYPE_PLAYLIST
	 */
	public static QueryTask buildQuery(int type, long id, String[] projection, String selection)
	{
		switch (type) {
		case TYPE_ARTIST:
		case TYPE_ALBUM:
		case TYPE_SONG:
		case TYPE_GENRE:
			return buildMediaQuery(type, id, projection, selection);
		case TYPE_PLAYLIST:
			return buildPlaylistQuery(id, projection);
		default:
			throw new IllegalArgumentException("Specified type not valid: " + type);
		}
	}

	/**
	 * Query the MediaStore to determine the id of the genre the song belongs
	 * to.
	 *
	 * @param context The context to use
	 * @param id The id of the song to query the genre for.
	 */
	public static long queryGenreForSong(Context context, long id) {
		String[] projection = { MediaLibrary.GenreSongColumns._GENRE_ID };
		String query = MediaLibrary.GenreSongColumns.SONG_ID+"=?";
		String[] queryArgs = new String[] { id+"" };

		Cursor cursor = MediaLibrary.queryLibrary(context, MediaLibrary.TABLE_GENRES_SONGS, projection, query, queryArgs, null); 
		if (cursor != null) {
			if (cursor.moveToNext())
				return cursor.getLong(0);
			cursor.close();
		}
		return 0;
	}

	/**
	 * Shuffle an array using Fisher-Yates algorithm.
	 *
	 * @param list The array. It will be shuffled in place.
	 */
	public static void shuffle(long[] list)
	{
		Random random = getRandom();
		for (int i = list.length; --i != -1; ) {
			int j = random.nextInt(i + 1);
			long tmp = list[j];
			list[j] = list[i];
			list[i] = tmp;
		}
	}

	/**
	 * Shuffle a Song list using Collections.shuffle().
	 *
	 * @param albumShuffle If true, preserve the order of songs inside albums.
	 */
	public static void shuffle(List<Song> list, boolean albumShuffle)
	{
		int size = list.size();
		if (size < 2)
			return;

		Random random = getRandom();
		if (albumShuffle) {
			List<Song> tempList = new ArrayList<Song>(list);
			Collections.sort(tempList);

			// Build map of albumId to start index in sorted list
			Map<Long, Integer> albumStartIndices = new HashMap<Long, Integer>();
			int index = 0;
			for (Song song : tempList) {
				if (!albumStartIndices.containsKey(song.albumId)) {
					albumStartIndices.put(song.albumId, index);
				}
				index++;
			}

			//Extract album list and shuffle
			List<Long> shuffledAlbums = new ArrayList<Long>(albumStartIndices.keySet());
			Collections.shuffle(shuffledAlbums, random);

			//Build Song list from album list
			list.clear();
			for (Long albumId : shuffledAlbums) {
				int songIndex = albumStartIndices.get(albumId);
				Song song = tempList.get(songIndex);
				do {
					list.add(song);
					songIndex++;
					if (songIndex < size) {
						song = tempList.get(songIndex);
					} else {
						break;
					}
				} while (albumId == song.albumId);
			}
		} else {
			Collections.shuffle(list, random);
		}
	}

	/**
	 * Determine if any songs are available from the library.
	 *
	 * @param context The Context to use
	 * @return True if it's possible to retrieve any songs, false otherwise. For
	 * example, false could be returned if there are no songs in the library.
	 */
	public static boolean isSongAvailable(Context context) {
		if (sSongCount == -1) {
			QueryTask query = new QueryTask(MediaLibrary.TABLE_SONGS, new String[]{"count(*)"}, null, null, null);
			Cursor cursor = query.runQuery(context);
			if (cursor == null) {
				sSongCount = 0;
			} else {
				cursor.moveToFirst();
				sSongCount = cursor.getInt(0);
				cursor.close();
			}
		}

		return sSongCount != 0;
	}

	/**
	 * Returns a shuffled array contaning the ids of all the songs on the
	 * device's library.
	 *
	 * @param context The Context to use
	 */
	private static long[] queryAllSongs(Context context) {
		QueryTask query = new QueryTask(MediaLibrary.TABLE_SONGS, Song.EMPTY_PROJECTION, null, null, null);
		Cursor cursor = query.runQuery(context);
		if (cursor == null || cursor.getCount() == 0) {
			sSongCount = 0;
			return null;
		}

		int count = cursor.getCount();
		long[] ids = new long[count];
		for (int i = 0; i != count; ++i) {
			if (!cursor.moveToNext())
				return null;
			ids[i] = cursor.getLong(0);
		}
		sSongCount = count;
		cursor.close();

		shuffle(ids);

		return ids;
	}

	/**
	 * Called if we detected a medium change
	 * This flushes some cached data
	 */
	public static void onMediaChange()
	{
		sSongCount = -1;
		sAllSongs = null;
	}

	/**
	 * Creates and sends share intent across the system. Includes all eligible songs found
	 * within this type and id (e.g. all songs in album, all songs for this artist etc.)
	 * @param ctx context to execute resolving on
	 * @param type media type to look for e.g. {@link MediaUtils#TYPE_SONG}
	 * @param id id of item to send
	 */
	public static void shareMedia(Context ctx, int type, long id) {
		if (type == TYPE_INVALID || id <= 0) { // invalid
			return;
		}

		String[] projection = new String [] { MediaLibrary.SongColumns._ID, MediaLibrary.SongColumns.PATH };
		Cursor cursor = buildQuery(type, id, projection, null).runQuery(ctx);
		if(cursor == null) {
			return;
		}

		try {
			while (cursor.moveToNext()) { // for all songs resolved...
				File songFile = new File(cursor.getString(1));
				Intent share = new Intent(Intent.ACTION_SEND);
				share.setType("audio/*");
				share.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(songFile));
				ctx.startActivity(Intent.createChooser(share, ctx.getResources().getString(R.string.sendto)));
			}
		} catch (ActivityNotFoundException ex) {
			Toast.makeText(ctx, R.string.no_receiving_apps, Toast.LENGTH_SHORT).show();
		} finally {
			cursor.close();
		}
	}

	/**
	 * Returns the first matching song (or NULL) of given type + id combination
	 *
	 * @param resolver A ContentResolver to use.
	 * @param type The MediaTye to query
	 * @param id The id of given type to query
	 */
	public static Song getSongByTypeId(Context context, int type, long id) {
		Song song = new Song(-1);
		QueryTask query = buildQuery(type, id, Song.FILLED_PROJECTION, null);
		Cursor cursor = query.runQuery(context);
		if (cursor != null) {
			if (cursor.getCount() > 0) {
				cursor.moveToPosition(0);
				song.populate(cursor);
			}
			cursor.close();
		}
		return song.isFilled() ? song : null;
	}

	/**
	 * Returns a song randomly selected from all the songs in the Android
	 * MediaStore.
	 *
	 * @param context The Context to use
	 */
	public static Song getRandomSong(Context context)
	{
		long[] songs = sAllSongs;

		if (songs == null) {
			songs = queryAllSongs(context);
			if (songs == null)
				return null;
			sAllSongs = songs;
			sAllSongsIdx = 0;
		} else if (sAllSongsIdx == sAllSongs.length) {
			sAllSongsIdx = 0;
			shuffle(sAllSongs);
		}

		Song result = getSongByTypeId(context, MediaUtils.TYPE_SONG, sAllSongs[sAllSongsIdx]);
		result.flags |= Song.FLAG_RANDOM;
		sAllSongsIdx++;
		return result;
	}

	/**
	 * Delete the given file or directory recursively.
	 *
	 * @return True if successful; false otherwise.
	 */
	public static boolean deleteFile(File file)
	{
		File[] children = file.listFiles();
		if (children != null) {
			for (File child : children) {
				deleteFile(child);
			}
		}
		return file.delete();
	}


	/**
	* This is an ugly hack: The tries to 'guess' if given path
	* is also accessible using a fuse mount
	*/
	private static String sanitizeMediaPath(String path) {

		String exPath  = Environment.getExternalStorageDirectory().getAbsolutePath();
		File exStorage = new File(exPath+"/Android");
		long exLastmod = exStorage.lastModified();

		if(exLastmod > 0 && path != null) {
			String pfx = path;
			while(true) {
				if((new File(pfx+"/Android")).lastModified() == exLastmod) {
					String guessPath = exPath + path.substring(pfx.length());
					if( (new File(guessPath)).exists() ) {
						path = guessPath;
						break;
					}
				}

				pfx = (new File(pfx)).getParent();
				if(pfx == null)
					break; /* hit root */
			}
		}

		return path;
	}

	/**
	* Adds a final slash if the path points to an existing directory
	*/
	private static String addDirEndSlash(String path) {
		if(path.length() > 0 && path.charAt(path.length()-1) != '/') {
			if( (new File(path)).isDirectory() ) {
				path += "/";
			}
		}
		return path;
	}

	/**
	 * Build a query that will contain all the media under the given path.
	 *
	 * @param path The path, e.g. /mnt/sdcard/music/
	 * @param projection The columns to query
	 * @return The initialized query.
	 */
	public static QueryTask buildFileQuery(String path, String[] projection)
	{
		/* make sure that the path is:
		   -> fixed-up to point to the real mountpoint if user browsed to the mediadir symlink
		   -> terminated with a / if it is a directory
		   -> ended with a % for the LIKE query
		*/
		path = addDirEndSlash(sanitizeMediaPath(path)) + "%";
		final String query = MediaLibrary.SongColumns.PATH+" LIKE ?";
		String[] qargs = { path };

		QueryTask result = new QueryTask(MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS, projection, query, qargs, FILE_SORT);
		result.type = TYPE_FILE;
		return result;
	}

	/**
	 * Returns a (possibly empty) Cursor for given file path
	 * @param path The path to the file to be queried
	 * @return A new Cursor object
	 * */
	public static Cursor getCursorForFileQuery(String path) {
		MatrixCursor matrixCursor = new MatrixCursor(Song.FILLED_PROJECTION);
		MediaMetadataRetriever data = new MediaMetadataRetriever();

		try {
			data.setDataSource(path);
		} catch (Exception e) {
				Log.w("VanillaMusic", "Failed to extract metadata from " + path);
		}

		String title = data.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
		String album = data.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
		String artist = data.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
		String duration = data.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

		if (duration != null) { // looks like we will be able to play this file
			// Vanilla requires each file to be identified by its unique id in the media database.
			// However: This file is not in the database, so we are going to roll our own
			// using the negative crc32 sum of the path value. While this is not perfect
			// (the same file may be accessed using various paths) it's the fastest method
			// and far good enough.
			CRC32 crc = new CRC32();
			crc.update(path.getBytes());
			Long songId = (Long)(2+crc.getValue())*-1; // must at least be -2 (-1 defines Song-Object to be empty)

			// Build minimal fake-database entry for this file
			Object[] objData = new Object[] { songId, path, "", "", "", 0, 0, 0, 0 };

			if (title != null)
				objData[2] = title;
			if (album != null)
				objData[3] = album;
			if (artist != null)
				objData[4] = artist;
			if (duration != null)
				objData[7] = Long.parseLong(duration, 10);

			matrixCursor.addRow(objData);
		}

		return matrixCursor;
	}

	/**
	 * Retrieve ID of specified media type for requested song. This works only for
	 * media-oriented types: {@link #TYPE_ARTIST}, {@link #TYPE_ALBUM}, {@link #TYPE_SONG}
	 * @param song requested song
	 * @param mType media type e.g. {@link #TYPE_ARTIST}
     * @return ID of media type, {@link #TYPE_INVALID} if unsupported
     */
	public static long getCurrentIdForType(Song song, int mType)
	{
		if(song == null)
			return TYPE_INVALID;

		switch(mType) {
			case TYPE_ARTIST:
				return song.artistId;
			case TYPE_ALBUM:
				return song.albumId;
			case TYPE_SONG:
				return song.id;
			default:
				return TYPE_INVALID;
		}
	}

}
