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
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;

public class Song implements Parcelable {
	public static final String[] FILLED_PROJECTION = {
		MediaStore.Audio.Media._ID,
		MediaStore.Audio.Media.DATA,
		MediaStore.Audio.Media.TITLE,
		MediaStore.Audio.Media.ALBUM,
		MediaStore.Audio.Media.ARTIST,
		MediaStore.Audio.Media.ALBUM_ID
		};

	public long id;

	public String path;
	public String coverPath;

	public String title;
	public String album;
	public String artist;

	public Song()
	{
		randomize();
	}

	public Song(long id)
	{
		this.id = id;
	}

	public boolean populate(boolean force)
	{
		if (path != null && !force)
			return true;
		if (id == -1)
			return false;

		ContentResolver resolver = ContextApplication.getContext().getContentResolver();
		Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		String selection = MediaStore.Audio.Media._ID + '=' + id;
		Cursor cursor = resolver.query(media, FILLED_PROJECTION, selection, null, null);

		id = -1;

		if (cursor != null) {
			if (cursor.moveToNext())
				populate(resolver, cursor);
			cursor.close();
		}

		return id != -1;
	}

	private void populate(ContentResolver resolver, Cursor cursor)
	{
		id = cursor.getLong(0);
		path = cursor.getString(1);
		title = cursor.getString(2);
		album = cursor.getString(3);
		artist = cursor.getString(4);
		long albumId = cursor.getLong(5);

		Uri media = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
		String[] albumProjection = { MediaStore.Audio.Albums.ALBUM_ART };
		String albumSelection = MediaStore.Audio.Albums._ID + '=' + albumId;

		cursor = resolver.query(media, albumProjection, albumSelection, null, null);
		if (cursor != null) {
			if (cursor.moveToNext())
				coverPath = cursor.getString(0);
			cursor.close();
		}
	}

	public static long[] getAllSongIdsWith(int type, long id)
	{
		if (type == 3)
			return new long[] { id };

		String selection = "=" + id + " AND " + MediaStore.Audio.Media.IS_MUSIC + "!=0";

		switch (type) {
		case 2:
			selection = MediaStore.Audio.Media.ALBUM_ID + selection;
			break;
		case 1:
			selection = MediaStore.Audio.Media.ARTIST_ID + selection;
			break;
		default:
			return null;
		}

		Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		String[] projection = { MediaStore.Audio.Media._ID };

		ContentResolver resolver = ContextApplication.getContext().getContentResolver();
		Cursor cursor = resolver.query(media, projection, selection, null, null);

		if (cursor == null)
			return null;

		int count = cursor.getCount();
		if (count == 0)
			return null;

		long[] songs = new long[count];
		for (int i = 0; i != count; ++i) {
			if (!cursor.moveToNext())
				return null;
			songs[i] = cursor.getInt(0);
		}

		cursor.close();
		return songs;
	}

	public void randomize()
	{
		id = -1;

		ContentResolver resolver = ContextApplication.getContext().getContentResolver();
		Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";
		Cursor cursor = resolver.query(media, FILLED_PROJECTION, selection, null, null);

		if (cursor != null) {
			if (cursor.moveToPosition(ContextApplication.getRandom().nextInt(cursor.getCount())))
				populate(resolver, cursor);
			cursor.close();
		}
	}

	public boolean equals(Song other)
	{
		if (other == null)
			return false;
		return id == other.id;
	}

	public static Parcelable.Creator<Song> CREATOR = new Parcelable.Creator<Song>() {
		public Song createFromParcel(Parcel in)
		{
			return new Song(in);
		}

		public Song[] newArray(int size)
		{
			return new Song[size];
		}
	};

	public Song(Parcel in)
	{
		id = in.readLong();
		path = in.readString();
		coverPath = in.readString();
		title = in.readString();
		album = in.readString();
		artist = in.readString();
	}

	public void writeToParcel(Parcel out, int flags)
	{
		out.writeLong(id);
		out.writeString(path);
		out.writeString(coverPath);
		out.writeString(title);
		out.writeString(album);
		out.writeString(artist);
	}

	public int describeContents()
	{
		return 0;
	}
}