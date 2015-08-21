/*
 * Copyright (C) 2015 Adrian Ulrich <adrian@blinkenlights.ch>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>. 
 */

package ch.blinkenlights.android.vanilla;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.LruCache;
import android.util.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;


public class CoverCache {
	/**
	 * Returned size of small album covers
	 */
	public final static int SIZE_SMALL = 96;
	/**
	 * Returned size of large (cover view) album covers
	 */
	public final static int SIZE_LARGE = 600;
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
	/**
	 * Shared LRU cache class
	 */
	private static BitmapLruCache sBitmapLruCache;
	/**
	 * Bitmask on how we are going to load coverart
	 */
	public static int mCoverLoadMode = 0;

	/**
	 * Constructs a new BitmapCache object
	 * Will initialize the internal LRU cache on first call
	 *
	 * @param context A context to use
	 */
	public CoverCache(Context context) {
		if (sBitmapLruCache == null) {
			sBitmapLruCache = new BitmapLruCache(context, 6*1024*1024);
		}
	}

	/**
	 * Returns a (possibly uncached) cover for the song - may return null if the song has no cover
	 *
	 * @param key The cache key to use for storing a generated cover
	 * @param song The song used to identify the artwork to load 
	 */
	public Bitmap getCoverFromSong(CoverKey key, Song song) {
		Bitmap cover = getCachedCover(key);
		if (cover == null) {
			cover = sBitmapLruCache.createBitmap(song, key.coverSize*key.coverSize);
			if (cover != null) {
				putCover(key, cover);
			}
		}
		return cover;
	}

	/**
	 * Returns a cached version of the cover. Will return null if nothing was cached
	 *
	 * @param key The cache key to use
	 */
	public Bitmap getCachedCover(CoverKey key) {
		return sBitmapLruCache.get(key);
	}

	/**
	 * Stores a new entry in the cache
	 *
	 * @param key The cache key to use
	 * @param cover The bitmap to store
	 */
	public void putCover(CoverKey key, Bitmap cover) {
		sBitmapLruCache.put(key, cover);
	}

	/**
	 * Deletes all items hold in the LRU cache
	 */
	public static void evictAll() {
		if (sBitmapLruCache != null) {
			sBitmapLruCache.evictAll();
		}
	}


	/**
	 * Object used as cache key. Objects with the same
	 * media type, id and size are considered to be equal
	 */
	public static class CoverKey {
		public final int coverSize;
		public final int mediaType;
		public final long mediaId;

		CoverKey(int mediaType, long mediaId, int coverSize) {
			this.mediaType = mediaType;
			this.mediaId = mediaId;
			this.coverSize = coverSize;
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof CoverKey
			    && this.mediaId   == ((CoverKey)obj).mediaId
			    && this.mediaType == ((CoverKey)obj).mediaType
			    && this.coverSize == ((CoverKey)obj).coverSize) {
				return true;
			}
			return false;
		}

		@Override
		public int hashCode() {
			return this.mediaType*10 + (int)this.mediaId + this.coverSize * (int)1e5;
		}

	}

	/**
	 * The LRU cache implementation, using the CoverKey as key to store Bitmap objects
	 *
	 * Note that the implementation does not override create() in order to enable
	 * the use of fetch-if-cached functions: createBitmap() is therefore called
	 * by CoverCache itself.
	 */
	private static class BitmapLruCache extends LruCache<CoverKey, Bitmap> {
		/**
		 * Context required for content resolver
		 */
		private final Context mContext;
		/**
		 * Filenames we consider to be cover art
		 */
		private final static String[] coverNames = { "cover.jpg", "cover.png", "album.jpg", "album.png", "artwork.jpg", "artwork.png", "art.jpg", "art.png" };

		/**
		 * Creates a new in-memory LRU cache
		 *
		 * @param context the application context
		 * @param size the lru cache size in bytes
		 */
		public BitmapLruCache(Context context, int size) {
			super(size);
			mContext = context;
		}

		/**
		 * Returns the cache size in bytes, not objects
		 */
		@Override
		protected int sizeOf(CoverKey key, Bitmap value) {
			return value.getByteCount();
		}

		/**
		 * Attempts to create a new bitmap object for given song.
		 * Returns null if no cover art was found
		 *
		 * @param song the function will search for artwork of this object
		 * @param maxPxCount the maximum amount of pixels to return (30*30 = 900)
		 */
		public Bitmap createBitmap(Song song, long maxPxCount) {

			if (song.id < 0) {
				// Unindexed song: return early
				return null;
			}

			try {
				InputStream inputStream = null;
				InputStream sampleInputStream = null; // same as inputStream but used for getSampleSize

				if ((CoverCache.mCoverLoadMode & CoverCache.COVER_MODE_VANILLA) != 0) {
					String basePath = (new File(song.path)).getParentFile().getAbsolutePath(); // parent dir of the currently playing file
					for (String coverFile: coverNames) {
						File guessedFile = new File( basePath + "/" + coverFile);
						if (guessedFile.exists() && !guessedFile.isDirectory()) {
							inputStream = new FileInputStream(guessedFile);
							sampleInputStream = new FileInputStream(guessedFile);
							break;
						}
					}
				}

				if (inputStream == null && (CoverCache.mCoverLoadMode & CoverCache.COVER_MODE_SHADOW) != 0) {
					String shadowPath = "/sdcard/Music/.vanilla/"+(song.artist.replaceAll("/", "_"))+"/"+(song.album.replaceAll("/", "_"))+".jpg";

					File guessedFile = new File(shadowPath);
					if (guessedFile.exists() && !guessedFile.isDirectory()) {
						inputStream = new FileInputStream(guessedFile);
						sampleInputStream = new FileInputStream(guessedFile);
					}
				}

				if (inputStream == null && (CoverCache.mCoverLoadMode & CoverCache.COVER_MODE_ANDROID) != 0) {
					Uri uri =  Uri.parse("content://media/external/audio/media/" + song.id + "/albumart");
					ContentResolver res = mContext.getContentResolver();
					inputStream = res.openInputStream(uri);
					sampleInputStream = res.openInputStream(uri);
				}

				if (inputStream != null) {
					BitmapFactory.Options bopts = new BitmapFactory.Options();
					bopts.inPreferredConfig  = Bitmap.Config.RGB_565;
					bopts.inJustDecodeBounds = true;

					final int inSampleSize   = getSampleSize(sampleInputStream, bopts, maxPxCount);

					/* reuse bopts: we are now REALLY going to decode the image */
					bopts.inJustDecodeBounds = false;
					bopts.inSampleSize       = inSampleSize;
					return BitmapFactory.decodeStream(inputStream, null, bopts);
				}
			} catch (Exception e) {
				// no cover art found
				Log.v("VanillaMusic", "Loading coverart for "+song+" failed with exception "+e);
			}
			// failed!
			return null;
		}

		/**
		 * Guess a good sampleSize value for given inputStream
		 *
		 * @param inputStream the input stream to read from
		 * @param bopts the bitmap options to use
		 * @param maxPxCount how many pixels we are returning at most
		 */
		private static int getSampleSize(InputStream inputStream, BitmapFactory.Options bopts, long maxPxCount) {
			int sampleSize = 1;     /* default sample size                   */

			BitmapFactory.decodeStream(inputStream, null, bopts);
			long hasPixels = bopts.outHeight * bopts.outWidth;
			if(hasPixels > maxPxCount) {
				sampleSize = Math.round((int)Math.sqrt((float) hasPixels / (float) maxPxCount));
			}
			return sampleSize;
		}

	}
}
