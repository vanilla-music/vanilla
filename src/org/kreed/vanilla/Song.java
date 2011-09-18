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
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.provider.MediaStore;

import java.io.FileDescriptor;
import java.io.FileNotFoundException;

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
	 * A cache of covers that have been loaded with getCover().
	 */
	private static final Cache<Bitmap> mCoverCache = new Cache<Bitmap>(10);

	/**
	 * If true, will not attempt to load any cover art in getCover()
	 */
	public static boolean mDisableCoverArt = false;

	/**
	 * Shuffled list of all ids in the library.
	 */
	private static long[] mAllSongs;
	private static int mAllSongsIdx;

	private static final int RANDOM_POPULATE_SIZE = 20;
	private static Song[] mRandomCache = new Song[RANDOM_POPULATE_SIZE];
	private static int mRandomCacheIdx;
	private static int mRandomCacheEnd;

	/**
	 * Total number of songs in the music library. -1 means uninitialized.
	 */
	private static int mSongCount = -1;

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
		MediaStore.Audio.Media.DURATION
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
		MediaStore.Audio.Playlists.Members.DURATION
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
	 * Song flags. Currently FLAG_RANDOM or 0.
	 */
	public int flags;

	/**
	 * @return true if it's possible to retrieve any songs, otherwise false. For example, false
	 * could be returned if there are no songs in the library.
	 */
	public static boolean isSongAvailable()
	{
		if (mSongCount == -1) {
			ContentResolver resolver = ContextApplication.getContext().getContentResolver();
			Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
			String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";
			Cursor cursor = resolver.query(media, new String[]{"count(_id)"}, selection, null, null);
			if (cursor != null) {
				cursor.moveToFirst();
				mSongCount = cursor.getInt(0);
				cursor.close();
			}
		}

		return mSongCount != 0;
	}

	/**
	 * Returns a shuffled array contaning the ids of all the songs on the
	 * device's library.
	 */
	public static long[] loadAllSongs()
	{
		mAllSongsIdx = 0;
		mRandomCacheEnd = -1;

		ContentResolver resolver = ContextApplication.getContext().getContentResolver();
		Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";
		Cursor cursor = resolver.query(media, EMPTY_PROJECTION, selection, null, null);
		if (cursor == null || cursor.getCount() == 0) {
			mSongCount = 0;
			return null;
		}

		int count = cursor.getCount();
		long[] ids = new long[count];
		for (int i = 0; i != count; ++i) {
			if (!cursor.moveToNext())
				return null;
			ids[i] = cursor.getLong(0);
		}
		mSongCount = count;
		cursor.close();

		MediaUtils.shuffle(ids);

		return ids;
	}

	public static void onMediaChange()
	{
		mSongCount = -1;
		// TODO: make reload optional?
		mAllSongs = loadAllSongs();
	}

	/**
	 * Returns a song randomly selected from all the songs in the Android
	 * MediaStore.
	 */
	public static Song randomSong()
	{
		long[] songs = mAllSongs;

		if (songs == null) {
			songs = loadAllSongs();
			if (songs == null)
				return null;
			mAllSongs = songs;
		} else if (mAllSongsIdx == mAllSongs.length) {
			mAllSongsIdx = 0;
			mRandomCacheEnd = -1;
			MediaUtils.shuffle(mAllSongs);
		}

		if (mAllSongsIdx >= mRandomCacheEnd) {
			ContentResolver resolver = ContextApplication.getContext().getContentResolver();
			Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

			StringBuilder selection = new StringBuilder("_ID IN (");

			boolean first = true;
			int end = Math.min(mAllSongsIdx + RANDOM_POPULATE_SIZE, mAllSongs.length);
			for (int i = mAllSongsIdx; i != end; ++i) {
				if (!first)
					selection.append(',');

				first = false;

				selection.append(mAllSongs[i]);
			}

			selection.append(')');

			Cursor cursor = resolver.query(media, FILLED_PROJECTION, selection.toString(), null, null);

			if (cursor == null) {
				mAllSongs = null;
				return null;
			}

			int count = cursor.getCount();
			if (count > 0) {
				assert(count <= RANDOM_POPULATE_SIZE);

				for (int i = 0; i != count; ++i) {
					cursor.moveToNext();
					Song newSong = new Song(-1);
					newSong.populate(cursor);
					newSong.flags |= FLAG_RANDOM;
					mRandomCache[i] = newSong;
				}
			}

			cursor.close();

			// The query will return sorted results; undo that
			MediaUtils.shuffle(mRandomCache, count);

			mRandomCacheIdx = 0;
			mRandomCacheEnd = mAllSongsIdx + count;
		}

		Song result = mRandomCache[mRandomCacheIdx];
		++mRandomCacheIdx;
		++mAllSongsIdx;

		return result;
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
	}

	/**
	 * Query the MediaStore, if necessary, to fill this Song's fields.
	 *
	 * @param force Query even if fields have already been populated
	 * @return true if fields have been populated, false otherwise
	 */
	public boolean query(boolean force)
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
		if (id == -1 || mDisableCoverArt)
			return null;

		// Query the cache for the cover
		Bitmap cover = mCoverCache.get(id);
		if (cover != null)
			return cover;

		Context context = ContextApplication.getContext();
		ContentResolver res = context.getContentResolver();

		cover = getCoverFromMediaFile(res);

		Bitmap deletedCover = mCoverCache.put(id, cover);
		if (deletedCover != null)
			deletedCover.recycle();

		return cover;
	}

	/**
	 * Return a URI describing where the cover art is stored, or null if this
	 * song has not been populated.
	 */
	public Uri getCoverUri()
	{
		// Use undocumented API to extract the cover from the media file from Eclair
		// See http://android.git.kernel.org/?p=platform/packages/apps/Music.git;a=blob;f=src/com/android/music/MusicUtils.java;h=d1aea0660009940a0160cb981f381e2115768845;hb=0749a3f1c11e052f97a3ba60fd624c9283ee7331#l986

		if (id == -1)
			return null;
		return Uri.parse("content://media/external/audio/media/" + id + "/albumart");
	}

	/**
	 * Attempts to read the album art directly from a media file using the
	 * media ContentProvider.
	 *
	 * @param resolver A ContentResolver to use.
	 * @return The album art or null if no album art could be found.
	 */
	private Bitmap getCoverFromMediaFile(ContentResolver resolver)
	{
		Bitmap cover = null;

		try {
			ParcelFileDescriptor parcelFileDescriptor = resolver.openFileDescriptor(getCoverUri(), "r");
			if (parcelFileDescriptor != null) {
				FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
				cover = BitmapFactory.decodeFileDescriptor(fileDescriptor, null, BITMAP_OPTIONS);
			}
		} catch (IllegalStateException e) {
		} catch (FileNotFoundException e) {
		}

		return cover;
	}
}
