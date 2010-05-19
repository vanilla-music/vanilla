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
	 * @param type TYPE_ARTIST or TYPE_ALBUM, indicating the the id represents
	 * an artist or album
	 * @param id The MediaStore id of the artist or album
	 */
	private static Cursor getMediaCursor(int type, long id)
	{
		String selection = "=" + id + " AND " + MediaStore.Audio.Media.IS_MUSIC + "!=0";

		switch (type) {
		case TYPE_ARTIST:
			selection = MediaStore.Audio.Media.ARTIST_ID + selection;
			break;
		case TYPE_ALBUM:
			selection = MediaStore.Audio.Media.ALBUM_ID + selection;
			break;
		default:
			throw new IllegalArgumentException("Invalid type specified: " + type);
		}

		ContentResolver resolver = ContextApplication.getContext().getContentResolver();
		Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		String[] projection = { MediaStore.Audio.Media._ID };
		String sort = MediaStore.Audio.Media.ARTIST_KEY + ',' + MediaStore.Audio.Media.ALBUM_KEY + ',' + MediaStore.Audio.Media.TRACK;
		return resolver.query(media, projection, selection, null, sort);
	}

	/**
	 * Return a cursor containing the ids of all the songs in the playlist
	 * with the given id.
	 *
	 * @param id The id of the playlist in MediaStore.Audio.Playlists.
	 */
	private static Cursor getPlaylistCursor(long id)
	{
		ContentResolver resolver = ContextApplication.getContext().getContentResolver();
		Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", id);
		String[] projection = new String[] { MediaStore.Audio.Playlists.Members.AUDIO_ID };
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
		Cursor cursor;

		switch (type) {
		case TYPE_SONG:
			return new long[] { id };
		case TYPE_ARTIST:
		case TYPE_ALBUM:
			cursor = getMediaCursor(type, id);
			break;
		case TYPE_PLAYLIST:
			cursor = getPlaylistCursor(id);
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
}
