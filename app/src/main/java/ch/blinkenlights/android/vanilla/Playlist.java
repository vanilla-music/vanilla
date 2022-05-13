/*
 * Copyright (C) 2010 Christopher Eby <kreed@kreed.org>
 * Copyright (C) 2014-2016 Adrian Ulrich <adrian@blinkenlights.ch>
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

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.text.TextUtils;
import java.util.ArrayList;

import androidx.annotation.Nullable;

/**
 * Provides various playlist-related utility functions.
 */
public class Playlist {
	/**
	 * Queries all the playlists known to the MediaLibrary.
	 *
	 * @param context the context to use
	 * @return The queried cursor.
	 */
	public static Cursor queryPlaylists(Context context) {
		final String[] columns = { MediaLibrary.PlaylistColumns._ID, MediaLibrary.PlaylistColumns.NAME };
		final String sort = MediaLibrary.PlaylistColumns.NAME;
		return MediaLibrary.queryLibrary(context, MediaLibrary.TABLE_PLAYLISTS, columns, null, null, sort);
	}

	/**
	 * Retrieves the id for a playlist with the given name.
	 *
	 * @param context the context to use
	 * @param name The name of the playlist.
	 * @return The id of the playlist, or -1 if there is no playlist with the
	 * given name.
	 */
	public static long getPlaylist(Context context, String name)
	{
		long id = -1;
		final String[] columns = { MediaLibrary.PlaylistColumns._ID };
		final String selection = MediaLibrary.PlaylistColumns.NAME+"=?";
		final String[] selectionArgs = { name };
		Cursor cursor = MediaLibrary.queryLibrary(context, MediaLibrary.TABLE_PLAYLISTS, columns, selection, selectionArgs, null);

		if (cursor != null) {
			if (cursor.moveToNext())
				id = cursor.getLong(0);
			cursor.close();
		}

		return id;
	}

	/**
	 * Returns the name of given playlist id.
	 *
	 * @param context the context to use
	 * @param id the playlist id to look up
	 * @return name of the playlist, may be null
	 */
	public static @Nullable String getPlaylist(Context context, long id)
	{
		String name = null;
		final String[] columns = { MediaLibrary.PlaylistColumns.NAME };
		final String selection = MediaLibrary.PlaylistColumns._ID+"=?";
		final String[] selectionArgs = { Long.valueOf(id).toString() };
		Cursor cursor = MediaLibrary.queryLibrary(context, MediaLibrary.TABLE_PLAYLISTS, columns, selection, selectionArgs, null);

		if (cursor != null) {
			if (cursor.moveToNext())
				name = cursor.getString(0);
			cursor.close();
		}

		return name;
	}

	/**
	 * Create a new playlist with the given name. If a playlist with the given
	 * name already exists, it will be overwritten.
	 *
	 * @param context the context to use
	 * @param name The name of the playlist.
	 * @return The id of the new playlist.
	 */
	public static long createPlaylist(Context context, String name)
	{
		long id = getPlaylist(context, name);
		if (id != -1)
			deletePlaylist(context, id);

		id = MediaLibrary.createPlaylist(context, name);
		return id;
	}

	/**
	 * Run the given query and add the results to the given playlist. Should be
	 * run on a background thread.
	 *
	 * @param context the context to use
	 * @param playlistId The playlist id of the playlist to
	 * modify.
	 * @param query The query to run. The audio id should be the first column.
	 * @return The number of songs that were added to the playlist.
	 */
	public static int addToPlaylist(Context context, long playlistId, QueryTask query) {
		ArrayList<Long> result = new ArrayList<Long>();
		Cursor cursor = query.runQuery(context);
		if (cursor != null) {
			while (cursor.moveToNext()) {
				result.add(cursor.getLong(0));
			}
		}
		return addToPlaylist(context, playlistId, result);
	}

	/**
	 * Adds a set of audioIds to the given playlist. Should be
	 * run on a background thread.
	 *
	 * @param context the context to use
	 * @param playlistId The playlist id of the playlist to
	 * modify.
	 * @param audioIds An ArrayList with all IDs to add
	 * @return The number of songs that were added to the playlist.
	 */
	public static int addToPlaylist(Context context, long playlistId, ArrayList<Long> audioIds) {
		if (playlistId == -1)
			return 0;
		return MediaLibrary.addToPlaylist(context, playlistId, audioIds);
	}

	/**
	 * Removes a set of audioIds from the given playlist. Should be
	 * run on a background thread.
	 *
	 * @param context the context to use
	 * @param playlistId id of the playlist to
	 * modify.
	 * @param audioIds An ArrayList with all IDs to drop
	 * @return The number of songs that were removed from the playlist
	 */
	public static int removeFromPlaylist(Context context, long playlistId, ArrayList<Long> audioIds) {
		if (playlistId == -1)
			return 0;

		String idList = TextUtils.join(", ", audioIds);
		String selection = MediaLibrary.PlaylistSongColumns.SONG_ID+" IN ("+idList+") AND "+MediaLibrary.PlaylistSongColumns.PLAYLIST_ID+"="+playlistId;
		return MediaLibrary.removeFromPlaylist(context, selection, null);
	}

	/**
	 * Delete the playlist with the given id.
	 *
	 * @param context the context to use
	 * @param id the id of the playlist.
	 */
	public static void deletePlaylist(Context context, long id) {
		MediaLibrary.removePlaylist(context, id);
	}


	/**
	 * Rename the playlist with the given id.
	 *
	 * @param context the context to use
	 * @param id The Media.Audio.Playlists id of the playlist.
	 * @param newName The new name for the playlist.
	 */
	public static void renamePlaylist(Context context, long id, String newName) {
		MediaLibrary.renamePlaylist(context, id, newName);
	}

	/**
	 * Returns the ID of the 'favorites' playlist.
	 *
	 * @param context The Context to use
	 * @param create Create the playlist if it does not exist
	 * @return the id of the playlist, -1 on error
	 */
	public static long getFavoritesId(Context context, boolean create) {
		String playlistName = context.getString(R.string.playlist_favorites);
		long playlistId = getPlaylist(context, playlistName);

		if (playlistId == -1 && create == true)
			playlistId = createPlaylist(context, playlistName);

		return playlistId;
	}

	/**
	 * Searches for given song in given playlist
	 *
	 * @param context the context to use
	 * @param playlistId The ID of the Playlist to query
	 * @param song The Song to search in given playlistId
	 * @return true if `song' was found in `playlistId'
	 */
	public static boolean isInPlaylist(Context context, long playlistId, Song song) {
		if (playlistId == -1 || song == null)
			return false;

		boolean found = false;
		String selection = MediaLibrary.PlaylistSongColumns.PLAYLIST_ID+"=? AND "+MediaLibrary.PlaylistSongColumns.SONG_ID+"=?";
		String[] selectionArgs = { Long.toString(playlistId), Long.toString(song.id) };

		Cursor cursor = MediaLibrary.queryLibrary(context, MediaLibrary.TABLE_PLAYLISTS_SONGS, Song.EMPTY_PLAYLIST_PROJECTION, selection, selectionArgs, null);
		if (cursor != null) {
			found = cursor.getCount() != 0;
			cursor.close();
		}
		return found;
	}
}
