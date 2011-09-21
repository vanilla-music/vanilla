/*
 * Copyright (C) 2010 Christopher Eby <kreed@kreed.org>
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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

/**
 * Instances of this class simply provide a basic representation of a playlist
 * (currently only the id and name). The class also provides various playlist-
 * related static utility functions.
 */
public class Playlist {
	/**
	 * Create a Playlist with the given id and name.
	 */
	public Playlist(long id, String name)
	{
		this.id = id;
		this.name = name;
	}

	/**
	 * The MediaStore.Audio.Playlists id of the playlist.
	 */
	public long id;
	/**
	 * The name of the playlist.
	 */
	public String name;

	/**
	 * Queries all the playlists known to the MediaStore.
	 *
	 * @param context A context to use.
	 * @return An array of Playlists
	 */
	public static Playlist[] getPlaylists(Context context)
	{
		ContentResolver resolver = context.getContentResolver();
		Uri media = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI;
		String[] projection = { MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME };
		String sort = MediaStore.Audio.Playlists.NAME;
		Cursor cursor = resolver.query(media, projection, null, null, sort);

		if (cursor == null)
			return null;

		int count = cursor.getCount();
		if (count == 0)
			return null;

		Playlist[] playlists = new Playlist[count];
		for (int i = 0; i != count; ++i) {
			if (!cursor.moveToNext())
				return null;
			playlists[i] = new Playlist(cursor.getLong(0), cursor.getString(1));
		}

		cursor.close();
		return playlists;
	}

	/**
	 * Retrieves the id for a playlist with the given name.
	 *
	 * @param context A context to use.
	 * @param name The name of the playlist.
	 * @return The id of the playlist, or -1 if there is no playlist with the
	 * given name.
	 */
	public static long getPlaylist(Context context, String name)
	{
		long id = -1;

		ContentResolver resolver = context.getContentResolver();
		Cursor cursor = resolver.query(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
			new String[] { MediaStore.Audio.Playlists._ID },
			MediaStore.Audio.Playlists.NAME + "=?",
			new String[] { name }, null);

		if (cursor != null) {
			if (cursor.moveToNext())
				id = cursor.getLong(0);
			cursor.close();
		}

		return id;
	}

	/**
	 * Create a new playlist with the given name. If a playlist with the given
	 * name already exists, it will be overwritten.
	 *
	 * @param context A context to use.
	 * @param name The name of the playlist.
	 * @return The id of the new playlist.
	 */
	public static long createPlaylist(Context context, String name)
	{
		ContentResolver resolver = context.getContentResolver();
		long id = getPlaylist(context, name);

		if (id == -1) {
			// We need to create a new playlist.
			ContentValues values = new ContentValues(1);
			values.put(MediaStore.Audio.Playlists.NAME, name);
			Uri uri = resolver.insert(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, values);
			id = Long.parseLong(uri.getLastPathSegment());
		} else {
			// We are overwriting an existing playlist. Clear existing songs.
			Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", id);
			resolver.delete(uri, null, null);
		}

		return id;
	}

	/**
	 * Run the given query and add the results to the given playlist. Should be
	 * run on a background thread.
	 *
	 * @param context A context to use.
	 * @param playlistId The MediaStore.Audio.Playlist id of the playlist to
	 * modify.
	 * @param query The query to run. The audio id should be the first column.
	 * @return The number of songs that were added to the playlist.
	 */
	public static int addToPlaylist(Context context, long playlistId, QueryTask query)
	{
		if (playlistId == -1)
			return 0;

		ContentResolver resolver = context.getContentResolver();

		// Find the greatest PLAY_ORDER in the playlist
		Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", playlistId);
		String[] projection = new String[] { MediaStore.Audio.Playlists.Members.PLAY_ORDER };
		Cursor cursor = resolver.query(uri, projection, null, null, null);
		int base = 0;
		if (cursor.moveToLast())
			base = cursor.getInt(0) + 1;
		cursor.close();

		Cursor from = query.runQuery(resolver);
		if (from == null)
			return 0;

		int count = from.getCount();
		if (count > 0) {
			ContentValues[] values = new ContentValues[count];
			for (int i = 0; i != count; ++i) {
				from.moveToPosition(i);
				values[i] = new ContentValues(1);
				values[i].put(MediaStore.Audio.Playlists.Members.PLAY_ORDER, Integer.valueOf(base + i));
				values[i].put(MediaStore.Audio.Playlists.Members.AUDIO_ID, from.getLong(0));
			}
			resolver.bulkInsert(uri, values);
		}

		from.close();

		return count;
	}

	/**
	 * Delete the playlist with the given id.
	 *
	 * @param context A context to use.
	 * @param id The Media.Audio.Playlists id of the playlist.
	 */
	public static void deletePlaylist(Context context, long id)
	{
		Uri uri = ContentUris.withAppendedId(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, id);
		context.getContentResolver().delete(uri, null, null);
	}

	/**
	 * Rename the playlist with the given id.
	 *
	 * @param context A context to use.
	 * @param id The Media.Audio.Playlists id of the playlist.
	 * @param newName The new name for the playlist.
	 */
	public static void renamePlaylist(Context context, long id, String newName)
	{
		long existingId = getPlaylist(context, newName);
		// We are already called the requested name; nothing to do.
		if (existingId == id)
			return;
		// There is already a playlist with this name. Kill it.
		if (existingId != -1)
			deletePlaylist(context, existingId);

		ContentResolver resolver = context.getContentResolver();
		ContentValues values = new ContentValues(1);
		values.put(MediaStore.Audio.Playlists.NAME, newName);
		resolver.update(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, values, MediaStore.Audio.Playlists._ID + "=" + id, null);
	}
}
