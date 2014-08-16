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

package ch.blinkenlights.android.vanilla;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.LruCache;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import android.util.Log;

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
	 * If set, this song has no cover art. If not set, this song may or may not
	 * have cover art.
	 */
	public static final int FLAG_NO_COVER = 0x2;
	/**
	 * The number of flags.
	 */
	public static final int FLAG_COUNT = 2;
	/**
	 * Use all cover providers to load cover art
	 */
	public static final int COVER_MODE_ALL = 0xF;
	/**
	 * Use androids builtin cover mechanism to load covers
	 */
	public static final int COVER_MODE_ANDROID = 0x1;
	/**
	 * Use vanilla musics cover load mechanism
	 */
	public static final int COVER_MODE_VANILLA = 0x2;
	/**
	 * Use vanilla musics SHADOW cover load mechanism
	 */
	public static final int COVER_MODE_SHADOW = 0x4;


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

	private class LruCacheKey {
		long id;
		long artistId;
		long albumId;
		String path;

		public LruCacheKey(long id, long artistId, long albumId, String path) {
			this.id = id;
			this.artistId = artistId;
			this.albumId = albumId;
			this.path = path;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof LruCacheKey && this.albumId == ((LruCacheKey)obj).albumId && this.artistId == ((LruCacheKey)obj).artistId) {
				return true;
			}
			return false;
		}

		@Override
		public int hashCode() {
			return (int)( 0xFFFFFF & (this.artistId + this.albumId) );
		}

		@Override
		public String toString() {
			return "LruCacheKey<"+this.id+"> = "+this.path;
		}

	}

	/**
	 * A cache of 6 MiB of covers.
	 */
	private static class CoverCache extends LruCache<LruCacheKey, Bitmap> {
		private final Context mContext;

		// Possible coverart names if we are going to load the cover on our own
		private static String[] coverNames = { "cover.jpg", "cover.png", "album.jpg", "album.png", "artwork.jpg", "artwork.png", "art.jpg", "art.png" };

		public CoverCache(Context context)
		{
			super(6 * 1024 * 1024);
			mContext = context;
		}

		@Override
		public Bitmap create(LruCacheKey key)
		{
			try {
				InputStream inputStream = null;
				InputStream sampleInputStream = null; // same as inputStream but used for getSampleSize

				if ((mCoverLoadMode & COVER_MODE_VANILLA) != 0) {
					String basePath = (new File(key.path)).getParentFile().getAbsolutePath(); // ../ of the currently playing file
					for (String coverFile: coverNames) {
						File guessedFile = new File( basePath + "/" + coverFile);
						if (guessedFile.exists() && !guessedFile.isDirectory()) {
							inputStream = new FileInputStream(guessedFile);
							sampleInputStream = new FileInputStream(guessedFile);
							break;
						}
					}
				}

				if (inputStream == null && (mCoverLoadMode & COVER_MODE_SHADOW) != 0) {
					String[] projection = new String [] { MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM };
					QueryTask query = MediaUtils.buildQuery(MediaUtils.TYPE_SONG, key.id, projection, null);
					Cursor cursor = query.runQuery(mContext.getContentResolver());
					if (cursor != null) {
						if (cursor.getCount() > 0) {
							cursor.moveToNext();
							String thisArtist = cursor.getString(0);
							String thisAlbum = cursor.getString(1);
							String shadowPath = "/sdcard/Music/.vanilla/"+(thisArtist.replaceAll("/", "_"))+"/"+(thisAlbum.replaceAll("/", "_"))+".jpg";

							File guessedFile = new File(shadowPath);
							if (guessedFile.exists() && !guessedFile.isDirectory()) {
								inputStream = new FileInputStream(guessedFile);
								sampleInputStream = new FileInputStream(guessedFile);
							}
						}
						cursor.close();
					}
				}

				if (inputStream == null && (mCoverLoadMode & COVER_MODE_ANDROID) != 0) {
					Uri uri =  Uri.parse("content://media/external/audio/media/" + key.id + "/albumart");
					ContentResolver res = mContext.getContentResolver();
					inputStream = res.openInputStream(uri);
					sampleInputStream = res.openInputStream(uri);
				}

				if (inputStream != null) {
					BitmapFactory.Options bopts = new BitmapFactory.Options();
					bopts.inPreferredConfig  = Bitmap.Config.RGB_565;
					bopts.inJustDecodeBounds = true;

					final int inSampleSize   = getSampleSize(sampleInputStream, bopts);

					/* reuse bopts: we are now REALLY going to decode the image */
					bopts.inJustDecodeBounds = false;
					bopts.inSampleSize       = inSampleSize;
					return BitmapFactory.decodeStream(inputStream, null, bopts);
				}
			} catch (Exception e) {
				// no cover art found
				Log.v("VanillaMusic", "Loading coverart for "+key+" failed with exception "+e);
			}

			return null;
		}

		/**
		 * Guess a good sampleSize value for given inputStream
		 */
		private static int getSampleSize(InputStream inputStream, BitmapFactory.Options bopts) {
			int sampleSize = 1;     /* default sample size                   */
			long maxVal = 600*600;  /* max number of pixels we are accepting */

			BitmapFactory.decodeStream(inputStream, null, bopts);
			long hasPixels = bopts.outHeight * bopts.outWidth;
			if(hasPixels > maxVal) {
				sampleSize = Math.round((int)Math.sqrt((float) hasPixels / (float) maxVal));
			}
			return sampleSize;
		}

		@Override
		protected int sizeOf(LruCacheKey key, Bitmap value)
		{
			return value.getRowBytes() * value.getHeight();
		}
	}

	/**
	 * The cache instance.
	 */
	private static CoverCache sCoverCache = null;

	/**
	 * Bitmask on how we are going to load coverart
	 */
	public static int mCoverLoadMode = 0;

	/**
	 * We will evict our own cache if set to true
	 */
	public static boolean mFlushCoverCache = false;

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
	 * Song flags. Currently {@link #FLAG_RANDOM} or {@link #FLAG_NO_COVER}.
	 */
	public int flags;

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
	 * Query the album art for this song.
	 *
	 * @param context A context to use.
	 * @return The album art or null if no album art could be found
	 */
	public Bitmap getCover(Context context)
	{
		if (mCoverLoadMode == 0 || id == -1 || (flags & FLAG_NO_COVER) != 0)
			return null;

		if (sCoverCache == null)
			sCoverCache = new CoverCache(context.getApplicationContext());

		if (mFlushCoverCache) {
			mFlushCoverCache = false;
			sCoverCache.evictAll();
		}

		LruCacheKey key = new LruCacheKey(id, artistId, albumId, path);
		Bitmap cover = sCoverCache.get(key);

		if (cover == null)
			flags |= FLAG_NO_COVER;
		return cover;
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
