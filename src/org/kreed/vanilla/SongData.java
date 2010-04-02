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

import java.util.Arrays;
import java.util.Comparator;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.SparseArray;

public class SongData {
	public static final int FIELD_ARTIST = 1;
	public static final int FIELD_ALBUM = 2;
	public static final int FIELD_TITLE = 3;

	public long id;
	public long albumId;
	public long artistId;

	public String title;
	public String album;
	public String artist;

	private SongData(Cursor cursor)
	{
		id = cursor.getLong(0);
		title = cursor.getString(1);
		albumId = cursor.getLong(2);
		album = cursor.getString(3);
		artistId = cursor.getLong(4);
		artist = cursor.getString(5);
	}

	public String getField(int field)
	{
		switch (field) {
		case FIELD_TITLE:
			return title;
		case FIELD_ARTIST:
			return artist;
		case FIELD_ALBUM:
			return album;
		}
		return null;
	}

	public long getFieldId(int field)
	{
		switch (field) {
		case FIELD_TITLE:
			return id;
		case FIELD_ARTIST:
			return artistId;
		case FIELD_ALBUM:
			return albumId;
		}
		return 0;
	}

	public static SongData[] getAllSongData()
	{
		Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		String[] projection = { MediaStore.Audio.Media._ID,
				MediaStore.Audio.Media.TITLE,
				MediaStore.Audio.Media.ALBUM_ID,
				MediaStore.Audio.Media.ALBUM,
				MediaStore.Audio.Media.ARTIST_ID,
				MediaStore.Audio.Media.ARTIST };
		String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";

		ContentResolver resolver = ContextApplication.getContext().getContentResolver();
		Cursor cursor = resolver.query(media, projection, selection, null, null);

		if (cursor == null)
			return null;

		int count = cursor.getCount();
		if (count == 0)
			return null;

		SongData[] songs = new SongData[count];
		while (--count != -1) {
			if (!cursor.moveToNext())
				return null;
			songs[count] = new SongData(cursor);
		}

		cursor.close();
		return songs;
	}

	public static class TitleComparator implements Comparator<SongData> {
		public int compare(SongData a, SongData b)
		{
			return a.title.compareToIgnoreCase(b.title);
		}
	}

	public static class AlbumComparator implements IdComparator {
		public int compare(SongData a, SongData b)
		{
			return a.album.compareToIgnoreCase(b.album);
		}

		public long getId(SongData song)
		{
			return song.albumId;
		}
	}

	public static class ArtistComparator implements IdComparator {
		public int compare(SongData a, SongData b)
		{
			return a.artist.compareToIgnoreCase(b.artist);
		}

		public long getId(SongData song)
		{
			return song.artistId;
		}
	}

	public static interface IdComparator extends Comparator<SongData> {
		public long getId(SongData song);
	}

	public static SongData[] filter(SongData[] songs, IdComparator comparator)
	{
		SparseArray<SongData> albums = new SparseArray<SongData>(songs.length);
		for (int i = songs.length; --i != -1; ) {
			SongData song = songs[i];
			int id = (int)comparator.getId(song);
			if (albums.get(id) == null)
				albums.put(id, song);
		}

		SongData[] result = new SongData[albums.size()];
		for (int i = result.length; --i != -1; )
			result[i] = albums.valueAt(i);

		Arrays.sort(result, comparator);
		return result;
	}
}