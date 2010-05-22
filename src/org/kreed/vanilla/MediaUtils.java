/*
 * Copyright (C) 2010 Christopher Eby <kreed@kreed.org>
 *
 * This file is part of Vanilla Music Player.
 *
 * Vanilla Music Player is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Vanilla Music Player is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.kreed.vanilla;

import java.io.File;

import android.content.ContentResolver;
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
	 * Return a cursor containing the ids of all the songs with artist or
	 * album of the specified id.
	 *
	 * @param type One of the TYPE_* constants, excluding playlists.
	 * @param id The MediaStore id of the artist or album.
	 * @param projection The columns to query.
	 */
	private static Cursor getMediaCursor(int type, long id, String[] projection)
	{
		ContentResolver resolver = ContextApplication.getContext().getContentResolver();
		Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		String selection;

		switch (type) {
		case TYPE_SONG:
			selection = MediaStore.Audio.Media._ID;
			break;
		case TYPE_ARTIST:
			selection = MediaStore.Audio.Media.ARTIST_ID;
			break;
		case TYPE_ALBUM:
			selection = MediaStore.Audio.Media.ALBUM_ID;
			break;
		default:
			throw new IllegalArgumentException("Invalid type specified: " + type);
		}

		selection += "=" + id + " AND " + MediaStore.Audio.Media.IS_MUSIC + "!=0";
		String sort = MediaStore.Audio.Media.ARTIST_KEY + ',' + MediaStore.Audio.Media.ALBUM_KEY + ',' + MediaStore.Audio.Media.TRACK;
		return resolver.query(media, projection, selection, null, sort);
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
	 * Return an array containing all the song ids that match the specified parameters
	 *
	 * @param type Type the id represents. Must be one of the Song.TYPE_*
	 * constants.
	 * @param id The id of the element in the MediaStore content provider for
	 * the given type.
	 */
	public static long[] getAllSongIdsWith(int type, long id)
	{
		String[] projection = { MediaStore.Audio.Media._ID };
		Cursor cursor;

		switch (type) {
		case TYPE_SONG:
			return new long[] { id };
		case TYPE_ARTIST:
		case TYPE_ALBUM:
			cursor = getMediaCursor(type, id, projection);
			break;
		case TYPE_PLAYLIST:
			cursor = getPlaylistCursor(id, projection);
			break;
		default:
			throw new IllegalArgumentException("Specified type not valid: " + type);
		}

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
	 * @param type One of the TYPE_* constants, excluding playlists.
	 * @param id The MediaStore id of the media to delete.
	 * @return The number of songs deleted.
	 */
	public static int deleteMedia(int type, long id)
	{
		int count = 0;

		ContentResolver resolver = ContextApplication.getContext().getContentResolver();
		String[] projection = new String [] { MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA };
		Cursor cursor = getMediaCursor(type, id, projection);

		if (cursor != null) {
			PlaybackService service = ContextApplication.hasService() ? ContextApplication.getService() : null;

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
}
