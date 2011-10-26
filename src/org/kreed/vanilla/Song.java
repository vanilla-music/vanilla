/*
 * Copyright (C) 2010, 2011 Christopher Eby <kreed@kreed.org>
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

package org.kreed.vanilla;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;

/**
 * Represents a Song backed by the MediaStore. Includes basic metadata and
 * utilities to retrieve songs from the MediaStore.
 */
public class Song implements Comparable<Song> {
	/**
	 * Indicates that this song was randomly selected from all songs.
	 */
	public static final int FLAG_RANDOM = 0x1;
	/**
	 * The number of flags.
	 */
	public static final int FLAG_COUNT = 1;

	public static final String[] EMPTY_PROJECTION = {
		MediaStore.Audio.Media._ID,
	};

	public static final String[] FILLED_PROJECTION = {
		MediaStore.Audio.Media._ID,
		MediaStore.Audio.Media.DATA,
		MediaStore.Audio.Media.TITLE,
		MediaStore.Audio.Media.ALBUM,
		MediaStore.Audio.Media.ARTIST,
		MediaStore.Audio.Media.ALBUM_ID,
		MediaStore.Audio.Media.ARTIST_ID,
		MediaStore.Audio.Media.DURATION,
		MediaStore.Audio.Media.TRACK,
	};

	public static final String[] EMPTY_PLAYLIST_PROJECTION = {
		MediaStore.Audio.Playlists.Members.AUDIO_ID,
	};

	public static final String[] FILLED_PLAYLIST_PROJECTION = {
		MediaStore.Audio.Playlists.Members.AUDIO_ID,
		MediaStore.Audio.Playlists.Members.DATA,
		MediaStore.Audio.Playlists.Members.TITLE,
		MediaStore.Audio.Playlists.Members.ALBUM,
		MediaStore.Audio.Playlists.Members.ARTIST,
		MediaStore.Audio.Playlists.Members.ALBUM_ID,
		MediaStore.Audio.Playlists.Members.ARTIST_ID,
		MediaStore.Audio.Playlists.Members.DURATION,
		MediaStore.Audio.Playlists.Members.TRACK,
	};

	/**
	 * If true, will not attempt to load any cover art in getCover()
	 */
	public static boolean mDisableCoverArt = false;

	/**
	 * Id of this song in the MediaStore
	 */
	public long id;
	/**
	 * Id of this song's album in the MediaStore
	 */
	public long albumId;
	/**
	 * Id of this song's artist in the MediaStore
	 */
	public long artistId;

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
	 * Length of the song in milliseconds.
	 */
	public long duration;
	/**
	 * The position of the song in its album.
	 */
	public int trackNumber;

	/**
	 * Song flags. Currently FLAG_RANDOM or 0.
	 */
	public int flags;

	/**
	 * 1 if this song has cover art in the MediaStore, 0 if not, -1 if uninitialized.
	 */
	public int hasCover = -1;

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
	 * Return true if this song was retrieved from randomSong().
	 */
	public boolean isRandom()
	{
		return (flags & FLAG_RANDOM) != 0;
	}

	/**
	 * Populate fields with data from the supplied cursor.
	 *
	 * @param cursor Cursor queried with FILLED_PROJECTION projection
	 */
	public void populate(Cursor cursor)
	{
		id = cursor.getLong(0);
		path = cursor.getString(1);
		title = cursor.getString(2);
		album = cursor.getString(3);
		artist = cursor.getString(4);
		albumId = cursor.getLong(5);
		artistId = cursor.getLong(6);
		duration = cursor.getLong(7);
		trackNumber = cursor.getInt(8);
	}

	/**
	 * Get the id of the given song.
	 *
	 * @param song The Song to get the id from.
	 * @return The id, or 0 if the given song is null.
	 */
	public static long getId(Song song)
	{
		if (song == null)
			return 0;
		return song.id;
	}

	/**
	 * Returns true if this song has cover art stored in the MediaStore.
	 *
	 * @param context A context to use to query the MediaStore
	 */
	public boolean hasCover(Context context)
	{
		if (hasCover == -1) {
			try {
				hasCover = context.getContentResolver().openFileDescriptor(getCoverUri(), "r") == null ? 0 : 1;
			} catch (FileNotFoundException e) {
				hasCover = 0;
			}
		}
		return hasCover == 1;
	}

	/**
	 * Return a URI describing where the cover art is stored, or null if this
	 * song has not been populated.
	 */
	public Uri getCoverUri()
	{
		// Use undocumented API to extract the cover from the media file from Eclair
		// See http://android.git.kernel.org/?p=platform/packages/apps/Music.git;a=blob;f=src/com/android/music/MusicUtils.java;h=d1aea0660009940a0160cb981f381e2115768845;hb=0749a3f1c11e052f97a3ba60fd624c9283ee7331#l986

		if (id == -1 || hasCover == 0)
			return null;
		return Uri.parse("content://media/external/audio/media/" + id + "/albumart");
	}

	private static final BitmapFactory.Options BITMAP_OPTIONS = new BitmapFactory.Options();

	static {
		BITMAP_OPTIONS.inPreferredConfig = Bitmap.Config.RGB_565;
		BITMAP_OPTIONS.inDither = false;
	}

	/**
	 * Query the album art for this song.
	 *
	 * @param context A context to use.
	 * @return The album art or null if no album art could be found
	 */
	public Bitmap getCover(Context context)
	{
		Uri uri = getCoverUri();
		if (uri == null)
			return null;

		ContentResolver res = context.getContentResolver();
		hasCover = 0;
		try {
			ParcelFileDescriptor parcelFileDescriptor = res.openFileDescriptor(uri, "r");
			if (parcelFileDescriptor != null) {
				hasCover = 1;
				FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
				return BitmapFactory.decodeFileDescriptor(fileDescriptor, null, BITMAP_OPTIONS);
			}
		} catch (Exception e) {
			Log.d("VanillaMusic", "Failed to load cover art for " + path, e);
		}

		return null;
	}

	@Override
	public String toString()
	{
		return String.format("%d %d %s", id, albumId, path);
	}

	/**
	 * Compares the album ids of the two songs; if equal, compares track order.
	 */
	@Override
	public int compareTo(Song other)
	{
		if (albumId == other.albumId)
			return trackNumber - other.trackNumber;
		if (albumId > other.albumId)
			return 1;
		return -1;
	}
}
