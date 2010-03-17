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
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.util.SparseArray;

public class Song implements Parcelable {
	public static final int FIELD_ARTIST = 1;
	public static final int FIELD_ALBUM = 2;
	public static final int FIELD_TITLE = 3;

	int id;
	int albumId;
	int artistId;

	public String path;
	public String coverPath;

	public String title;
	public String album;
	public String artist;

	private Song(Cursor cursor)
	{
		id = cursor.getInt(0);
		title = cursor.getString(1);
		albumId = cursor.getInt(2);
		album = cursor.getString(3);
		artistId = cursor.getInt(4);
		artist = cursor.getString(5);
	}

	public Song(int id)
	{
		this.id = id;
	}

	public boolean populate()
	{
		if (path != null)
			return true;
		if (id == -1)
			return false;

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

		if (cursor == null || !cursor.moveToNext()) {
			id = -1;
			return false;
		}

		path = cursor.getString(0);
		title = cursor.getString(1);
		album = cursor.getString(2);
		artist = cursor.getString(3);
		albumId = cursor.getInt(4);
		cursor.close();

		media = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
		String[] albumProjection = { MediaStore.Audio.Albums.ALBUM_ART };
		String albumSelection = MediaStore.Audio.Albums._ID + "==" + albumId;

		cursor = resolver.query(media, albumProjection, albumSelection, null, null);
		if (cursor != null && cursor.moveToNext()) {
			coverPath = cursor.getString(0);
			cursor.close();
		}

		return true;
	}

	public static int[] getAllSongIds(String selection)
	{
		Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		String[] projection = { MediaStore.Audio.Media._ID };
		String isMusic = MediaStore.Audio.Media.IS_MUSIC + "!=0";
		if (selection == null)
			selection = isMusic;
		else
			selection += " AND " + isMusic;

		ContentResolver resolver = ContextApplication.getContext().getContentResolver();
		Cursor cursor = resolver.query(media, projection, selection, null, null);

		if (cursor == null)
			return null;

		int count = cursor.getCount();
		if (count == 0)
			return null;

		int[] songs = new int[count];
		while (--count != -1) {
			if (!cursor.moveToNext())
				return null;
			songs[count] = cursor.getInt(0);
		}

		cursor.close();
		return songs;
	}
	
	public static int[] getAllSongIdsWith(int type, int id)
	{
		if (type == FIELD_TITLE)
			return new int[] { id };
		else if (type == FIELD_ALBUM)
			return Song.getAllSongIds(MediaStore.Audio.Media.ALBUM_ID + "=" + id);
		else if (type == FIELD_ARTIST)
			return Song.getAllSongIds(MediaStore.Audio.Media.ARTIST_ID + "=" + id);
		return null;
	}

	public static Song[] getAllSongMetadata()
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

		Song[] songs = new Song[count];
		while (--count != -1) {
			if (!cursor.moveToNext())
				return null;
			songs[count] = new Song(cursor);
		}

		cursor.close();
		return songs;
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

	public int getFieldId(int field)
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

	public static class TitleComparator implements Comparator<Song> {
		public int compare(Song a, Song b)
		{
			return a.title.compareTo(b.title);
		}
	}

	public static class AlbumComparator implements IdComparator {
		public int compare(Song a, Song b)
		{
			return a.album.compareTo(b.album);
		}

		public int getId(Song song)
		{
			return song.albumId;
		}
	}

	public static class ArtistComparator implements IdComparator {
		public int compare(Song a, Song b)
		{
			return a.artist.compareTo(b.artist);
		}

		public int getId(Song song)
		{
			return song.artistId;
		}
	}

	public static interface IdComparator extends Comparator<Song> {
		public int getId(Song song);
	}

	public static Song[] filter(Song[] songs, IdComparator comparator)
	{
		SparseArray<Song> albums = new SparseArray<Song>(songs.length);
		for (int i = songs.length; --i != -1; ) {
			Song song = songs[i];
			int id = comparator.getId(song);
			if (albums.get(id) == null)
				albums.put(id, song);
		}

		Song[] result = new Song[albums.size()];
		for (int i = result.length; --i != -1; )
			result[i] = albums.valueAt(i);

		Arrays.sort(result, comparator);
		return result;
	}
}

class SongData {
	public SongData(int field, Song media)
	{
		this.field = field;
		this.media = media;
	}

	public SongData(SongData other)
	{
		this.field = other.field;
		this.media = other.media;
	}

	public SongData()
	{
	}

	@Override
	public int hashCode()
	{
		return (field << 29) + media.getFieldId(field);
	}

	public int field;
	public Song media;
}