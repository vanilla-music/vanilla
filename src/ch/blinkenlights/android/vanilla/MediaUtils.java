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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import junit.framework.Assert;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

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
	 * track number.
	 */
	public static final String DEFAULT_SORT = "artist_key,album_key,track";

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
	 * Query this many songs at a time from sAllSongs.
	 */
	private static final int RANDOM_POPULATE_SIZE = 20;
	private static final Song[] sRandomCache = new Song[RANDOM_POPULATE_SIZE];
	private static int sRandomCacheIdx;
	private static int sRandomCacheEnd;

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
		Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		StringBuilder selection = new StringBuilder();

		switch (type) {
		case TYPE_SONG:
			selection.append(MediaStore.Audio.Media._ID);
			break;
		case TYPE_ARTIST:
			selection.append(MediaStore.Audio.Media.ARTIST_ID);
			break;
		case TYPE_ALBUM:
			selection.append(MediaStore.Audio.Media.ALBUM_ID);
			break;
		default:
			throw new IllegalArgumentException("Invalid type specified: " + type);
		}

		selection.append('=');
		selection.append(id);
		selection.append(" AND is_music AND length(_data)");

		if (select != null) {
			selection.append(" AND ");
			selection.append(select);
		}

		QueryTask result = new QueryTask(media, projection, selection.toString(), null, DEFAULT_SORT);
		result.type = type;
		return result;
	}

	/**
	 * Builds a query that will return all the songs in the playlist with the
	 * given id.
	 *
	 * @param id The id of the playlist in MediaStore.Audio.Playlists.
	 * @param projection The columns to query.
	 * @param selection The selection to pass to the query, or null.
	 * @return The initialized query.
	 */
	public static QueryTask buildPlaylistQuery(long id, String[] projection, String selection)
	{
		Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", id);
		String sort = MediaStore.Audio.Playlists.Members.PLAY_ORDER;
		QueryTask result = new QueryTask(uri, projection, selection, null, sort);
		result.type = TYPE_PLAYLIST;
		return result;
	}

	/**
	 * Builds a query that will return all the songs in the genre with the
	 * given id.
	 *
	 * @param id The id of the genre in MediaStore.Audio.Genres.
	 * @param projection The columns to query.
	 * @param selection The selection to pass to the query, or null.
	 * @param selectionArgs The arguments to substitute into the selection.
	 * @param sort The sort order.
	 */
	public static QueryTask buildGenreQuery(long id, String[] projection, String selection, String[] selectionArgs, String sort)
	{
		Uri uri = MediaStore.Audio.Genres.Members.getContentUri("external", id);
		QueryTask result = new QueryTask(uri, projection, selection, selectionArgs, sort);
		result.type = TYPE_GENRE;
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
			return buildMediaQuery(type, id, projection, selection);
		case TYPE_PLAYLIST:
			return buildPlaylistQuery(id, projection, selection);
		case TYPE_GENRE:
			return buildGenreQuery(id, projection, selection, null,  MediaStore.Audio.Genres.Members.TITLE_KEY);
		default:
			throw new IllegalArgumentException("Specified type not valid: " + type);
		}
	}

	/**
	 * Query the MediaStore to determine the id of the genre the song belongs
	 * to.
	 *
	 * @param resolver A ContentResolver to use.
	 * @param id The id of the song to query the genre for.
	 */
	public static long queryGenreForSong(ContentResolver resolver, long id)
	{
		String[] projection = { "_id" };
		Uri uri = CompatHoneycomb.getContentUriForAudioId((int)id);
		Cursor cursor = resolver.query(uri, projection, null, null, null);
		
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
	 * Shuffle an array using Fisher-Yates algorithm.
	 *
	 * @param list The array. It will be shuffled in place.
	 * @param end Only elements before this index will be shuffled.
	 */
	public static void shuffle(Song[] list, int end)
	{
		Assert.assertTrue(end <= list.length && end >= 0);
		Random random = getRandom();
		for (int i = end; --i != -1; ) {
			int j = random.nextInt(i + 1);
			Song tmp = list[j];
			list[j] = list[i];
			list[i] = tmp;
		}
	}

	/**
	 * Shuffle a Song list using Collections.shuffle().
	 *
	 * @param albumShuffle If true, preserve the order of tracks inside albums.
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
	 * @param resolver A ContentResolver to use.
	 * @return True if it's possible to retrieve any songs, false otherwise. For
	 * example, false could be returned if there are no songs in the library.
	 */
	public static boolean isSongAvailable(ContentResolver resolver)
	{
		if (sSongCount == -1) {
			Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
			String selection = MediaStore.Audio.Media.IS_MUSIC;
			selection += " AND length(_data)";
			Cursor cursor = resolver.query(media, new String[]{"count(_id)"}, selection, null, null);
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
	 * @param resolver A ContentResolver to use.
	 */
	public static long[] queryAllSongs(ContentResolver resolver)
	{
		sAllSongsIdx = 0;
		sRandomCacheEnd = -1;

		Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		String selection = MediaStore.Audio.Media.IS_MUSIC;
		selection += " AND length(_data)";
		Cursor cursor = resolver.query(media, Song.EMPTY_PROJECTION, selection, null, null);
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

	public static void onMediaChange()
	{
		sSongCount = -1;
		sAllSongs = null;
	}

	/**
	 * Returns a song randomly selected from all the songs in the Android
	 * MediaStore.
	 *
	 * @param resolver A ContentResolver to use.
	 */
	public static Song randomSong(ContentResolver resolver)
	{
		long[] songs = sAllSongs;

		if (songs == null) {
			songs = queryAllSongs(resolver);
			if (songs == null)
				return null;
			sAllSongs = songs;
		} else if (sAllSongsIdx == sAllSongs.length) {
			sAllSongsIdx = 0;
			sRandomCacheEnd = -1;
			shuffle(sAllSongs);
		}

		if (sAllSongsIdx >= sRandomCacheEnd) {
			Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

			StringBuilder selection = new StringBuilder("_ID IN (");

			boolean first = true;
			int end = Math.min(sAllSongsIdx + RANDOM_POPULATE_SIZE, sAllSongs.length);
			for (int i = sAllSongsIdx; i != end; ++i) {
				if (!first)
					selection.append(',');

				first = false;

				selection.append(sAllSongs[i]);
			}

			selection.append(')');

			Cursor cursor = resolver.query(media, Song.FILLED_PROJECTION, selection.toString(), null, null);

			if (cursor == null) {
				sAllSongs = null;
				return null;
			}

			int count = cursor.getCount();
			if (count > 0) {
				Assert.assertTrue(count <= RANDOM_POPULATE_SIZE);

				for (int i = 0; i != count; ++i) {
					cursor.moveToNext();
					Song newSong = new Song(-1);
					newSong.populate(cursor);
					newSong.flags |= Song.FLAG_RANDOM;
					sRandomCache[i] = newSong;
				}
			}

			cursor.close();

			// The query will return sorted results; undo that
			shuffle(sRandomCache, count);

			sRandomCacheIdx = 0;
			sRandomCacheEnd = sAllSongsIdx + count;
		}

		Song result = sRandomCache[sRandomCacheIdx];
		++sRandomCacheIdx;
		++sAllSongsIdx;

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
		final String query = "_data LIKE ? AND is_music";
		String[] qargs = { path };

		Log.d("VanillaMusic", "Running buildFileQuery for "+query+" with ARG="+qargs[0]);
		Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		QueryTask result = new QueryTask(media, projection, query, qargs, DEFAULT_SORT);
		result.type = TYPE_FILE;
		return result;
	}
}
