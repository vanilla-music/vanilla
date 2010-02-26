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
	int id;

	public String path;
	public String coverPath;

	public String title;
	public String album;
	public String artist;

	public Song(int id)
	{
		this.id = id;

		Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		String[] projection = {
			MediaStore.Audio.Media.DATA,
			MediaStore.Audio.Media.TITLE,
			MediaStore.Audio.Media.ALBUM,
			MediaStore.Audio.Media.ARTIST,
			MediaStore.Audio.Media.ALBUM_ID
			};
		String selection = MediaStore.Audio.Media._ID + "==" + id;;

		ContentResolver resolver = ContextApplication.getContext().getContentResolver();
		Cursor cursor = resolver.query(media, projection, selection, null, null);

		if (cursor != null && cursor.moveToNext()) {
			path = cursor.getString(0);
			title = cursor.getString(1);
			album = cursor.getString(2);
			artist = cursor.getString(3);
			String albumId = cursor.getString(4);
			cursor.close();
	
			media = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
			String[] albumProjection = { MediaStore.Audio.Albums.ALBUM_ART };
			String albumSelection = MediaStore.Audio.Albums._ID + "==" + albumId;
	
			cursor = resolver.query(media, albumProjection, albumSelection, null, null);
			if (cursor != null && cursor.moveToNext()) {
				coverPath = cursor.getString(0);
				cursor.close();
			}
		}
	}

	public static int[] getAllSongs()
	{
		Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		String[] projection = { MediaStore.Audio.Media._ID };
		String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";

		ContentResolver resolver = ContextApplication.getContext().getContentResolver();
		Cursor cursor = resolver.query(media, projection, selection, null, null);

		if (cursor == null)
			return null;

		int count = cursor.getCount();
		if (count == 0)
			return null;

		int[] songs = new int[count];
		while (--count != -1 && cursor.moveToNext())
			songs[count] = cursor.getInt(0);

		cursor.close();
		cursor = null;

		return songs;
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
		id = in.readInt();
		path = in.readString();
		coverPath = in.readString();
		title = in.readString();
		album = in.readString();
		artist = in.readString();
	}

	public void writeToParcel(Parcel out, int flags)
	{
		out.writeInt(id);
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