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
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public class Song implements Parcelable {
	public static final int FLAG_RANDOM = 0x1;

	public static final String[] FILLED_PROJECTION = {
		MediaStore.Audio.Media._ID,
		MediaStore.Audio.Media.DATA,
		MediaStore.Audio.Media.TITLE,
		MediaStore.Audio.Media.ALBUM,
		MediaStore.Audio.Media.ARTIST,
		MediaStore.Audio.Media.ALBUM_ID
		};

	public long id;
	public long albumId;

	public String path;

	public String title;
	public String album;
	public String artist;

	public int flags;

	public Song()
	{
		randomize();
	}

	public Song(long id)
	{
		this.id = id;
	}

	public Song(long id, int flags)
	{
		this.id = id;
		this.flags = flags;
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
				populate(cursor);
			cursor.close();
		}

		return id != -1;
	}

	private void populate(Cursor cursor)
	{
		id = cursor.getLong(0);
		path = cursor.getString(1);
		title = cursor.getString(2);
		album = cursor.getString(3);
		artist = cursor.getString(4);
		albumId = cursor.getLong(5);
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
		String sort = MediaStore.Audio.Media.ARTIST_KEY + ',' + MediaStore.Audio.Media.ALBUM_KEY + ',' + MediaStore.Audio.Media.TRACK;
		Cursor cursor = resolver.query(media, projection, selection, null, sort);

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
			if (cursor.moveToPosition(ContextApplication.getRandom().nextInt(cursor.getCount()))) {
				populate(cursor);
				flags |= FLAG_RANDOM;
			}
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
		albumId = in.readLong();
		path = in.readString();
		title = in.readString();
		album = in.readString();
		artist = in.readString();
	}

	public void writeToParcel(Parcel out, int flags)
	{
		out.writeLong(id);
		out.writeLong(albumId);
		out.writeString(path);
		out.writeString(title);
		out.writeString(album);
		out.writeString(artist);
	}

	public int describeContents()
	{
		return 0;
	}

	private static final Uri ARTWORK_URI = Uri.parse("content://media/external/audio/albumart");
	private static final BitmapFactory.Options BITMAP_OPTIONS = new BitmapFactory.Options();

	static {
		BITMAP_OPTIONS.inPreferredConfig = Bitmap.Config.RGB_565;
		BITMAP_OPTIONS.inDither = false;
	}

	public Bitmap getCover()
	{
		if (id == -1)
			return null;

		Context context = ContextApplication.getContext();
		ContentResolver res = context.getContentResolver();

		Bitmap cover;

		// If we have a valid album, try to get the album art from the media store cache
		if (0 <= albumId) {
			cover = getCoverFromMediaStoreCache(res);

			if (cover == null) {
				// Perhaps it hasn't been loaded yet, try to extract it directly from the file
				cover = getCoverFromMediaFile(res);
			}
		} else {
			// The android API doesn't allow files without albums to have their album art cached, so get the cover directly from the media file
			cover = getCoverFromMediaFile(res);
		}

		// Load cover data from an alternate source here!

		return cover;
	}

	/**
	 * Attempts to read the album art directly from a media file.
	 *
	 * @param resolver An instance of the content resolver for this app
	 * @return The album art or null if no album art could be found
	 */
	private Bitmap getCoverFromMediaFile(ContentResolver resolver)
	{
		// Use undocumented API to extract the cover from the media file from Eclair
		// See http://android.git.kernel.org/?p=platform/packages/apps/Music.git;a=blob;f=src/com/android/music/MusicUtils.java;h=d1aea0660009940a0160cb981f381e2115768845;hb=0749a3f1c11e052f97a3ba60fd624c9283ee7331#l986
		Bitmap cover = null;

		try {
			Uri uri;

			if (albumId < 1) {
				uri = Uri.parse("content://media/external/audio/media/" + id + "/albumart");
			} else {
				uri = ContentUris.withAppendedId(ARTWORK_URI, albumId);
			}

			ParcelFileDescriptor parcelFileDescriptor = resolver.openFileDescriptor(uri, "r");
			if (parcelFileDescriptor != null) {
				FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
				cover = BitmapFactory.decodeFileDescriptor(fileDescriptor);
			}

			// If we couldn't get the cover, try loading it directly using the MediaScanner private API
			if (cover == null) {
				cover = getCoverFromMediaUsingMediaScanner(resolver);
			}
		} catch (IllegalStateException e) {
			// Apparently we're on 1.5 and the Eclair way of doing things is unsupported, so use another undocumented API to do it
			cover = getCoverFromMediaUsingMediaScanner(resolver);
		} catch (FileNotFoundException e) {
			// Couldn't fetch the cover, try fetching it using the private MediaScanner API
			cover = getCoverFromMediaUsingMediaScanner(resolver);
		}

		return cover;
	}

	/**
	 * Obtain the cover from a media file using the private MediaScanner API
	 *
	 * @param resolver An instance of the content resolver for this app
	 * @return The cover or null if there is no cover in the file or the API is unavailable
	 */
	private Bitmap getCoverFromMediaUsingMediaScanner(ContentResolver resolver)
	{
		Bitmap cover = null;

		// This is a private API, so do everything using reflection
		// see http://android.git.kernel.org/?p=platform/packages/apps/Music.git;a=blob;f=src/com/android/music/MusicUtils.java;h=ea2079435ca5e2c6834c9f6f02d07fe7621e0fd9;hb=aae2791ffdd8923d99242f2cf453eb66116fd6b6#l1044
		try {
			// Attempt to open the media file in read-only mode
			Uri uri = Uri.fromFile(new File(path));
			FileDescriptor fileDescriptor = resolver.openFileDescriptor(uri, "r").getFileDescriptor();

			if (fileDescriptor != null) {
				// Construct a MediaScanner
				Class mediaScannerClass = Class.forName("android.media.MediaScanner");
				Constructor mediaScannerConstructor = mediaScannerClass.getDeclaredConstructor(Context.class);
				Object mediaScanner = mediaScannerConstructor.newInstance(ContextApplication.getContext());

				// Call extractAlbumArt(fileDescriptor)
				Method method = mediaScannerClass.getDeclaredMethod("extractAlbumArt", FileDescriptor.class);
				byte[] artBinary = (byte[]) method.invoke(mediaScanner, fileDescriptor);

				// Convert the album art to a bitmap
				if (artBinary != null)
					cover = BitmapFactory.decodeByteArray(artBinary, 0, artBinary.length, BITMAP_OPTIONS);
			}
		} catch (Exception e) {
			// Swallow every exception and return an empty cover if we can't do it due to the API not being there anymore
		}

		return cover;
	}

	/**
	 * Get the cover from the media store cache(this is the official way of doing things)
	 *
	 * @param resolver An instance of the content resolver for this app
	 * @return The cover or null if there is no cover or the media scanner hasn't cached the file yet
	 */
	private Bitmap getCoverFromMediaStoreCache(ContentResolver resolver)
	{
		Uri media = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
		String[] albumProjection = {MediaStore.Audio.Albums.ALBUM_ART};
		String albumSelection = MediaStore.Audio.Albums._ID + '=' + albumId;

		Cursor cursor = resolver.query(media, albumProjection, albumSelection, null, null);
		if (cursor != null) {
			if (cursor.moveToNext())
				return BitmapFactory.decodeFile(cursor.getString(0), BITMAP_OPTIONS);
			cursor.close();
		}

		return null;
	}
}