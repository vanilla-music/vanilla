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
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore.Audio;
import android.util.Log;

import java.util.ArrayList;

class PlaylistBridge {

	/**
	 * Queries all native playlists and imports them
	 *
	 * @param context the context to use
	 */
	static void importAndroidPlaylists(Context context) {
		ContentResolver resolver = context.getContentResolver();
		Cursor cursor = null;

		try {
			cursor = resolver.query(Audio.Playlists.EXTERNAL_CONTENT_URI, new String[]{Audio.Playlists._ID, Audio.Playlists.NAME}, null, null, null);
		} catch (SecurityException e) {
			Log.v("VanillaMusic", "Unable to query existing playlists, exception: "+e);
		}

		if (cursor != null) {
			while (cursor.moveToNext()) {
				long playlistId = cursor.getLong(0);
				String playlistName = cursor.getString(1);
				importAndroidPlaylist(context, playlistName, playlistId);
			}
			cursor.close();
		}
	}

	/**
	 * Imports a single native playlist into our own media library
	 *
	 * @param context the context to use
	 * @param targetName the name of the playlist in our media store
	 * @param playlistId the native playlist id to import
	 */
	static void importAndroidPlaylist(Context context, String targetName, long playlistId) {
		ArrayList<Long> bulkIds = new ArrayList<>();
		ContentResolver resolver = context.getContentResolver();
		Uri uri = Audio.Playlists.Members.getContentUri("external", playlistId);
		Cursor cursor = null;

		try {
			cursor = resolver.query(uri, new String[]{Audio.Media.DATA}, null, null, Audio.Playlists.Members.DEFAULT_SORT_ORDER);
		} catch (SecurityException e) {
			Log.v("VanillaMusic", "Failed to query playlist: "+e);
		}

		if (cursor != null) {
			while (cursor.moveToNext()) {
				String path = cursor.getString(0);
				// We do not need to do a lookup by path as we can calculate the id used
				// by the mediastore using the path
				bulkIds.add(MediaLibrary.hash63(path));
			}
			cursor.close();
		}

		if (bulkIds.size() == 0)
			return; // do not import empty playlists

		long targetPlaylistId = MediaLibrary.createPlaylist(context, targetName);
		if (targetPlaylistId == -1)
			return; // already exists, won't touch

		MediaLibrary.addToPlaylist(context, targetPlaylistId, bulkIds);
	}

}
