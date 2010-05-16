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

/**
 * Represents a Song backed by the MediaStore. Includes basic metadata and
 * utilities to retrieve songs from the MediaStore.
 */
public class Song implements Parcelable {
	/**
	 * Indicates that this song was randomly selected from all songs.
	 */
	public static final int FLAG_RANDOM = 0x1;

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

	private static final String[] FILLED_PROJECTION = {
		MediaStore.Audio.Media._ID,
		MediaStore.Audio.Media.DATA,
		MediaStore.Audio.Media.TITLE,
		MediaStore.Audio.Media.ALBUM,
		MediaStore.Audio.Media.ARTIST,
		MediaStore.Audio.Media.ALBUM_ID
		};

	/**
	 * Id of this song in the MediaStore
	 */
	public long id;
	/**
	 * Id of this song's album in the MediaStore
	 */
	public long albumId;

	/**
	 * Path to the data for this song
	 */
	public String path;

	/**
	 * Song title
	 */
	public String title;
	/**
	 * Album name
	 */
	public String album;
	/**
	 * Artist name
	 */
	public String artist;

	/**
	 * Song flags. Currently FLAG_RANDOM or 0.
	 */
	public int flags;

	/**
	 * Initialize the song with a randomly selected id. Call populate to fill
	 * fields in the song.
	 */
	public Song()
	{
		randomize();
	}

	/**
	 * Initialize the song with the specified id. Call populate to fill fields
	 * in the song.
	 */
	public Song(long id)
	{
		this.id = id;
	}

	/**
	 * Initialize the song with the specified id and flags. Call populate to
	 * fill fields in the song.
	 */
	public Song(long id, int flags)
	{
		this.id = id;
		this.flags = flags;
	}

	/**
	 * Populate fields with data from the supplied cursor.
	 *
	 * @param cursor Cursor queried with FILLED_PROJECTION projection
	 */
	private void populate(Cursor cursor)
	{
		id = cursor.getLong(0);
		path = cursor.getString(1);
		title = cursor.getString(2);
		album = cursor.getString(3);
		artist = cursor.getString(4);
		albumId = cursor.getLong(5);
	}

	/**
	 * Query the MediaStore, if necessary, to fill this Song's fields.
	 *
	 * @param force Query even if fields have already been populated
	 * @return true if fields have been populated, false otherwise
	 */
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

	/**
	 * Fill the fields with data from a random Song in the MediaStore
	 */
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

	/**
	 * Return a cursor containing the ids of all the songs with artist or
	 * album of the specified id.
	 *
	 * @param resolver The ContentResolver to run the query with.
	 * @param type TYPE_ARTIST or TYPE_ALBUM, indicating the the id represents
	 * an artist or album
	 * @param id The MediaStore id of the artist or album
	 */
	private static Cursor getMediaCursor(ContentResolver resolver, int type, long id)
	{
		String selection = '=' + id + " AND " + MediaStore.Audio.Media.IS_MUSIC + "!=0";

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


		Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		String[] projection = { MediaStore.Audio.Media._ID };
		String sort = MediaStore.Audio.Media.ARTIST_KEY + ',' + MediaStore.Audio.Media.ALBUM_KEY + ',' + MediaStore.Audio.Media.TRACK;
		return resolver.query(media, projection, selection, null, sort);
	}

	/**
	 * Return a cursor containing the ids of all the songs in the playlist
	 * with the given id.
	 *
	 * @param resolver The ContentResolver to run the query with.
	 * @param id The id of the playlist in MediaStore.Audio.Playlists.
	 */
	private static Cursor getPlaylistCursor(ContentResolver resolver, long id)
	{
		Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", id);
		String[] projection = new String[] { MediaStore.Audio.Playlists.Members.AUDIO_ID };
		return resolver.query(uri, projection, null, null,
		                      MediaStore.Audio.Playlists.Members.DEFAULT_SORT_ORDER);
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
		ContentResolver resolver = ContextApplication.getContext().getContentResolver();
		Cursor cursor;

		switch (type) {
		case TYPE_SONG:
			return new long[] { id };
		case TYPE_ARTIST:
		case TYPE_ALBUM:
			cursor = getMediaCursor(resolver, type, id);
			break;
		case TYPE_PLAYLIST:
			cursor = getPlaylistCursor(resolver, id);
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

	private static final BitmapFactory.Options BITMAP_OPTIONS = new BitmapFactory.Options();

	static {
		BITMAP_OPTIONS.inPreferredConfig = Bitmap.Config.RGB_565;
		BITMAP_OPTIONS.inDither = false;
	}

	/**
	 * Query the album art for this song.
	 *
	 * @return The album art or null if no album art could be found
	 */
	public Bitmap getCover()
	{
		if (id == -1)
			return null;

		Context context = ContextApplication.getContext();
		ContentResolver res = context.getContentResolver();

		Bitmap cover;

		// Query the MediaStore content provider first
		cover = getCoverFromMediaFile(res);

		// If that fails, try using MediaScanner directly
		if (cover == null)
			cover = getCoverFromMediaUsingMediaScanner(res);

		// Load cover data from an alternate source here!

		return cover;
	}

	/**
	 * Attempts to read the album art directly from a media file using the
	 * media ContentProvider.
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
			Uri uri = Uri.parse("content://media/external/audio/media/" + id + "/albumart");
			ParcelFileDescriptor parcelFileDescriptor = resolver.openFileDescriptor(uri, "r");
			if (parcelFileDescriptor != null) {
				FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
				cover = BitmapFactory.decodeFileDescriptor(fileDescriptor);
			}
		} catch (IllegalStateException e) {
		} catch (FileNotFoundException e) {
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
				Class<?> mediaScannerClass = Class.forName("android.media.MediaScanner");
				Constructor<?> mediaScannerConstructor = mediaScannerClass.getDeclaredConstructor(Context.class);
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
}