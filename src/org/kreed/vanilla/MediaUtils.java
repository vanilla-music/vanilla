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
	 * Return a cursor containing the ids of all the songs with artist or
	 * album of the specified id.
	 *
	 * @param type One of the TYPE_* constants, excluding playlists.
	 * @param id The MediaStore id of the artist or album.
	 * @param projection The columns to query.
	 * @param select An extra selection to pass to the query, or null.
	 */
	private static Cursor getMediaCursor(int type, long id, String[] projection, String select)
	{
		ContentResolver resolver = ContextApplication.getContext().getContentResolver();
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
		return resolver.query(media, projection, selection.toString(), null, sort);
	}

	/**
	 * Return a cursor containing the ids of all the songs in the playlist
	 * with the given id.
	 *
	 * @param id The id of the playlist in MediaStore.Audio.Playlists.
	 * @param projection The columns to query.
	 */
	private static Cursor getPlaylistCursor(long id, String[] projection)
	{
		ContentResolver resolver = ContextApplication.getContext().getContentResolver();
		Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", id);
		String sort = MediaStore.Audio.Playlists.Members.PLAY_ORDER;
		return resolver.query(uri, projection, null, null, sort);
	}

	/**
	 * Return a cursor containing the ids of all the songs in the genre
	 * with the given id.
	 *
	 * @param id The id of the genre in MediaStore.Audio.Genres.
	 * @param projection The columns to query.
	 * @param selection The selection to pass to the query, or null.
	 * @param selectionArgs The arguments to substitute into the selection.
	 */
	public static Cursor queryGenre(long id, String[] projection, String selection, String[] selectionArgs)
	{
		ContentResolver resolver = ContextApplication.getContext().getContentResolver();
		Uri uri = MediaStore.Audio.Genres.Members.getContentUri("external", id);
		String sort = MediaStore.Audio.Genres.Members.TITLE_KEY;
		return resolver.query(uri, projection, selection, selectionArgs, sort);
	}

	/**
	 * Returns a Cursor queried with the given information.
	 *
	 * @param type Type the id represents. Must be one of the Song.TYPE_*
	 * constants.
	 * @param id The id of the element in the MediaStore content provider for
	 * the given type.
	 * @param selection An extra selection to be passed to the query. May be
	 * null. Must not be used with type == TYPE_SONG or type == TYPE_PLAYLIST
	 */
	public static Cursor query(int type, long id, String[] projection, String selection)
	{
		switch (type) {
		case TYPE_ARTIST:
		case TYPE_ALBUM:
		case TYPE_SONG:
			return getMediaCursor(type, id, projection, selection);
		case TYPE_PLAYLIST:
			assert(selection == null);
			return getPlaylistCursor(id, projection);
		case TYPE_GENRE:
			return queryGenre(id, projection, selection, null);
		default:
			throw new IllegalArgumentException("Specified type not valid: " + type);
		}
	}

	/**
	 * Return an array containing all the song ids that match the specified parameters
	 *
	 * @param type Type the id represents. Must be one of the Song.TYPE_*
	 * constants.
	 * @param id The id of the element in the MediaStore content provider for
	 * the given type.
	 */
	public static long[] getAllSongIdsWith(int type, long id)
	{
		if (type == TYPE_SONG)
			return new long[] { id };

		Cursor cursor;
		if (type == MediaUtils.TYPE_PLAYLIST)
			cursor = query(type, id, Song.EMPTY_PLAYLIST_PROJECTION, null);
		else
			cursor = query(type, id, Song.EMPTY_PROJECTION, null);
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
	 * Delete all the songs in the given media set.
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
		Cursor cursor = getMediaCursor(type, id, projection, null);

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
	public static long queryGenreForSong(long id)
	{
		// This is terribly inefficient, but it seems to be the only way to do
		// this. Honeycomb introduced an API to query the genre of the song.
		// We should look into it when ICS is released.

		ContentResolver resolver = ContextApplication.getContext().getContentResolver();

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
		Random random = ContextApplication.getRandom();
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
		Random random = ContextApplication.getRandom();
		for (int i = end; --i != -1; ) {
			int j = random.nextInt(i + 1);
			Song tmp = list[j];
			list[j] = list[i];
			list[i] = tmp;
		}
	}
}
