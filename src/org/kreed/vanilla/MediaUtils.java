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

package org.kreed.vanilla;

import java.io.File;
import java.util.Random;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

public class MediaUtils {
	/**
	 * Type indicating an id represents an artist.
	 */
	public static final int TYPE_ARTIST = 1;
	/**
	 * Type indicating an id represents an album.
	 */
	public static final int TYPE_ALBUM = 2;
	/**
	 * Type indicating an id represents a song.
	 */
	public static final int TYPE_SONG = 3;
	/**
	 * Type indicating an id represents a playlist.
	 */
	public static final int TYPE_PLAYLIST = 4;
	/**
	 * Type indicating ids represent genres.
	 */
	public static final int TYPE_GENRE = 5;

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
	private static Song[] sRandomCache = new Song[RANDOM_POPULATE_SIZE];
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
	public static QueryTask buildMediaQuery(int type, long id, String[] projection, String select)
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
		selection.append(" AND is_music!=0");

		if (select != null) {
			selection.append(" AND ");
			selection.append(select);
		}

		String sort = MediaStore.Audio.Media.ARTIST_KEY + ',' + MediaStore.Audio.Media.ALBUM_KEY + ',' + MediaStore.Audio.Media.TRACK;
		return new QueryTask(media, projection, selection.toString(), null, sort);
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
		return new QueryTask(uri, projection, selection, null, sort);
	}

	/**
	 * Builds a query that will return all the songs in the genre with the
	 * given id.
	 *
	 * @param id The id of the genre in MediaStore.Audio.Genres.
	 * @param projection The columns to query.
	 * @param selection The selection to pass to the query, or null.
	 * @param selectionArgs The arguments to substitute into the selection.
	 */
	public static QueryTask buildGenreQuery(long id, String[] projection, String selection, String[] selectionArgs)
	{
		Uri uri = MediaStore.Audio.Genres.Members.getContentUri("external", id);
		String sort = MediaStore.Audio.Genres.Members.TITLE_KEY;
		return new QueryTask(uri, projection, selection, selectionArgs, sort);
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
			return buildGenreQuery(id, projection, selection, null);
		default:
			throw new IllegalArgumentException("Specified type not valid: " + type);
		}
	}

	/**
	 * Return an array containing all the song ids that match the specified parameters. Should be run on a background thread.
	 *
	 * @param context A context to use.
	 * @param type Type the id represents. Must be one of the Song.TYPE_*
	 * constants.
	 * @param id The id of the element in the MediaStore content provider for
	 * the given type.
	 */
	public static long[] getAllSongIdsWith(Context context, int type, long id)
	{
		if (type == TYPE_SONG)
			return new long[] { id };

		String[] projection = type == MediaUtils.TYPE_PLAYLIST ?
			Song.EMPTY_PLAYLIST_PROJECTION : Song.EMPTY_PROJECTION;
		Cursor cursor = buildQuery(type, id, projection, null).runQuery(context.getContentResolver());
		if (cursor == null)
			return null;

		int count = cursor.getCount();
		if (count == 0)
			return null;

		long[] songs = new long[count];
		for (int i = 0; i != count; ++i) {
			if (!cursor.moveToNext())
				return null;
			songs[i] = cursor.getLong(0);
		}

		cursor.close();
		return songs;
	}

	/**
	 * Delete all the songs in the given media set. Should be run on a
	 * background thread.
	 *
	 * @param context A context to use.
	 * @param type One of the TYPE_* constants, excluding playlists.
	 * @param id The MediaStore id of the media to delete.
	 * @return The number of songs deleted.
	 */
	public static int deleteMedia(Context context, int type, long id)
	{
		int count = 0;

		ContentResolver resolver = context.getContentResolver();
		String[] projection = new String [] { MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA };
		Cursor cursor = buildQuery(type, id, projection, null).runQuery(resolver);

		if (cursor != null) {
			PlaybackService service = PlaybackService.hasInstance() ? PlaybackService.get(context) : null;

			while (cursor.moveToNext()) {
				if (new File(cursor.getString(1)).delete()) {
					long songId = cursor.getLong(0);
					String where = MediaStore.Audio.Media._ID + '=' + songId;
					resolver.delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, where, null);
					if (service != null)
						service.removeSong(songId);
					++count;
				}
			}

			cursor.close();
		}

		return count;
	}

	/**
	 * Query the MediaStore to determine the id of the genre the song belongs
	 * to.
	 */
	public static long queryGenreForSong(Context context, long id)
	{
		// This is terribly inefficient, but it seems to be the only way to do
		// this. Honeycomb introduced an API to query the genre of the song.
		// We should look into it when ICS is released.

		ContentResolver resolver = context.getContentResolver();

		// query ids of all the genres
		Uri uri = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI;
		String[] projection = { "_id" };
		Cursor cursor = resolver.query(uri, projection, null, null, null);

		if (cursor != null) {
			String selection = "_id=" + id;
			while (cursor.moveToNext()) {
				// check if the given song belongs to this genre
				long genreId = cursor.getLong(0);
				Uri genreUri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId);
				Cursor c = resolver.query(genreUri, projection, selection, null, null);
				if (c != null) {
					if (c.getCount() == 1)
						return genreId;
					c.close();
				}
			}
			cursor.close();
		}

		return -1;
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
		assert(end <= list.length && end >= 0);
		Random random = getRandom();
		for (int i = end; --i != -1; ) {
			int j = random.nextInt(i + 1);
			Song tmp = list[j];
			list[j] = list[i];
			list[i] = tmp;
		}
	}

	/**
	 * Determine if any songs are available from the library.
	 *
	 * @param context A context to use.
	 * @return True if it's possible to retrieve any songs, false otherwise. For
	 * example, false could be returned if there are no songs in the library.
	 */
	public static boolean isSongAvailable(Context context)
	{
		if (sSongCount == -1) {
			ContentResolver resolver = context.getContentResolver();
			Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
			String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";
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
	 * @param context A context to use.
	 */
	public static long[] loadAllSongs(Context context)
	{
		sAllSongsIdx = 0;
		sRandomCacheEnd = -1;

		ContentResolver resolver = context.getContentResolver();
		Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";
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

		MediaUtils.shuffle(ids);

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
	 * @param context A context to use.
	 */
	public static Song randomSong(Context context)
	{
		long[] songs = sAllSongs;

		if (songs == null) {
			songs = loadAllSongs(context);
			if (songs == null)
				return null;
			sAllSongs = songs;
		} else if (sAllSongsIdx == sAllSongs.length) {
			sAllSongsIdx = 0;
			sRandomCacheEnd = -1;
			shuffle(sAllSongs);
		}

		if (sAllSongsIdx >= sRandomCacheEnd) {
			ContentResolver resolver = context.getContentResolver();
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
				assert(count <= RANDOM_POPULATE_SIZE);

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
}
