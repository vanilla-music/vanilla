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
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.LruCache;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Random;


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
	 * Shared on-disk cache class
	 */
	private static BitmapDiskCache sBitmapDiskCache;
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
		if (sBitmapDiskCache == null) {
			sBitmapDiskCache = new BitmapDiskCache(context, 25*1024*1024);
		}
	}

	/**
	 * Returns a (possibly uncached) cover for the song - will return null if the song has no cover
	 *
	 * @param key The cache key to use for storing a generated cover
	 * @param song The song used to identify the artwork to load
	 * @return a bitmap or null if no artwork was found
	 */
	public Bitmap getCoverFromSong(CoverKey key, Song song) {
		Bitmap cover = getCachedCover(key);

		if (cover == null) {
			// memory miss: check disk
			cover = getStoredCover(key);
			if (cover == null) {
				// disk miss: create
				cover = sBitmapLruCache.createBitmap(song, key.coverSize*key.coverSize);
				if (cover != null) {
					storeCover(key, cover);
				}
			}
			// store in memory if cover was re-created
			if (cover != null) {
				cacheCover(key, cover);
			}
		}
		return cover;
	}

	/**
	 * Returns a cached version of the cover.
	 *
	 * @param key The cache key to use
	 * @param bitmap or null on cache miss
	 */
	public Bitmap getCachedCover(CoverKey key) {
		return sBitmapLruCache.get(key);
	}

	/**
	 * Stores a new entry in the in-memory cache
	 * Use getCachedCover to read the cached contents back
	 *
	 * @param key The cache key to use
	 * @param cover The bitmap to store
	 */
	public void cacheCover(CoverKey key, Bitmap cover) {
		sBitmapLruCache.put(key, cover);
	}

	/**
	 * Returns the on-disk cached version of the cover.
	 * Should only be used on a background thread
	 *
	 * @param key The cache key to use
	 * @return bitmap or null on cache miss
	 */
	public Bitmap getStoredCover(CoverKey key) {
		return sBitmapDiskCache.get(key);
	}

	/**
	 * Stores a new entry in the on-disk cache
	 * Use getStoredCover to read the cached contents back
	 *
	 * @param key The cache key to use
	 * @param cover The bitmap to store
	 */
	private void storeCover(CoverKey key, Bitmap cover) {
		sBitmapDiskCache.put(key, cover);
	}

	/**
	 * Deletes all items hold in the cover caches
	 */
	public static void evictAll() {
		if (sBitmapLruCache != null) {
			sBitmapLruCache.evictAll();
		}
		if (sBitmapDiskCache != null) {
			sBitmapDiskCache.evictAll();
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

		@Override
		public String toString() {
			return "CoverKey_i"+this.mediaId+"_t"+this.mediaType+"_s"+this.coverSize;
		}

	}


	private static class BitmapDiskCache extends SQLiteOpenHelper {
		/**
		 * Maximal cache size to use in bytes
		 */
		private final long mCacheSize;
		/**
		 * SQLite table to use
		 */
		private final static String TABLE_NAME = "covercache";
		/**
		 * Projection of all columns in the database
		 */
		private final static String[] FULL_PROJECTION = {"id", "size", "expires", "blob"};
		/**
		 * Projection of metadata-only columns
		 */
		private final static String[] META_PROJECTION = {"id", "size", "expires"};
		/**
		 * Restrict lifetime of cached objects to, at most, OBJECT_TTL
		 */
		private final static int OBJECT_TTL = 86400*4;

		/**
		 * Creates a new BitmapDiskCache instance
		 *
		 * @param context The context to use
		 * @param cacheSize The maximal amount of disk space to use in bytes
		 */
		public BitmapDiskCache(Context context, long cacheSize) {
			super(context, "covercache.db", null, 1 /* version */);
			mCacheSize = cacheSize;
		}

		/**
		 * Called by SQLiteOpenHelper to create the database schema
		 */
		@Override
		public void onCreate(SQLiteDatabase dbh) {
			dbh.execSQL("CREATE TABLE "+TABLE_NAME+" (id INTEGER, expires INTEGER, size INTEGER, blob BLOB);");
			dbh.execSQL("CREATE UNIQUE INDEX idx ON "+TABLE_NAME+" (id);");
		}

		/**
		 * Called by SqLiteOpenHelper if the database needs an upgrade
		 */
		@Override
		public void onUpgrade(SQLiteDatabase dbh, int oldVersion, int newVersion) {
			// first db -> nothing to upgrade
		}

		/**
		 * Trims the on disk cache to given size
		 *
		 * @param maxCacheSize Trim cache to this many bytes
		 */
		private void trim(long maxCacheSize) {
			SQLiteDatabase dbh = getWritableDatabase();
			long availableSpace = maxCacheSize - getUsedSpace();

			if (maxCacheSize == 0) {
				// Just drop the whole database (probably a call from evictAll)
				dbh.delete(TABLE_NAME, "1", null);
			} else if (availableSpace < 0) {
				// Try to evict all expired entries first
				int affected = dbh.delete(TABLE_NAME, "expires < ?", new String[] {""+getUnixTime()});
				if (affected > 0)
					availableSpace = maxCacheSize - getUsedSpace();

				if (availableSpace < 0) {
					// still not enough space: purge by expire date (this kills random rows as expire times are random)
					Cursor cursor = dbh.query(TABLE_NAME, META_PROJECTION, null, null, null, null, "expires ASC");
					if (cursor != null) {
						while (cursor.moveToNext() && availableSpace < 0) {
							int id = cursor.getInt(0);
							int size = cursor.getInt(1);
							dbh.delete(TABLE_NAME, "id=?", new String[] {""+id});
							availableSpace += size;
						}
						cursor.close();
					}
				}
			}
		}

		/**
		 * Deletes all cached elements from the on-disk cache
		 */
		public void evictAll() {
			// purge all cached entries
			trim(0);
			// and release the dbh
			getWritableDatabase().close();
		}

		/**
		 * Checks if given stamp is considered to be expired
		 *
		 * @param stamp The timestamp to check
		 * @return boolean true if stamp is expired
		 */
		private boolean isExpired(long stamp) {
			return (getUnixTime() > stamp);
		}

		/**
		 * Returns the current unix timestamp
		 *
		 * @return long unix seconds since epoc
		 */
		private long getUnixTime() {
			return System.currentTimeMillis() / 1000L;
		}

		/**
		 * Calculates the space used by the sqlite database
		 *
		 * @return long the space used in bytes
		 */
		private long getUsedSpace() {
			long usedSpace = -1;
			SQLiteDatabase dbh = getWritableDatabase();
			Cursor cursor = dbh.query(TABLE_NAME, new String[]{"SUM(size)"}, null, null, null, null, null);
			if (cursor != null) {
				if (cursor.moveToNext())
					usedSpace = cursor.getLong(0);
				cursor.close();
			}
			return usedSpace;
		}

		/**
		 * Stores a bitmap in the disk cache, does not update existing objects
		 *
		 * @param key The cover key to use
		 * @param Bitmap The bitmap to store
		 */
		public void put(CoverKey key, Bitmap cover) {
			SQLiteDatabase dbh = getWritableDatabase();

			// Ensure that there is some space left
			trim(mCacheSize);

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			cover.compress(Bitmap.CompressFormat.PNG, 100, out);

			Random rnd = new Random();
			long ttl = getUnixTime() + rnd.nextInt(OBJECT_TTL);

			ContentValues values = new ContentValues();
			values.put("id"     , key.hashCode());
			values.put("expires", ttl);
			values.put("size"   , out.size());
			values.put("blob"   , out.toByteArray());

			dbh.insert(TABLE_NAME, null, values);
		}

		/**
		 * Returns a cached bitmap
		 *
		 * @param key The key to lookup
		 * @return a cached bitmap, null on cache miss
		 */
		public Bitmap get(CoverKey key) {
			Bitmap cover = null;

			SQLiteDatabase dbh = getWritableDatabase(); // may also delete
			String selection = "id=?";
			String[] selectionArgs = { ""+key.hashCode() };
			Cursor cursor = dbh.query(TABLE_NAME, FULL_PROJECTION, selection, selectionArgs, null, null, null);
			if (cursor != null) {
				if (cursor.moveToFirst()) {
					long expires = cursor.getLong(2);
					byte[] blob = cursor.getBlob(3);

					if (isExpired(expires)) {
						dbh.delete(TABLE_NAME, selection, selectionArgs);
					} else {
						ByteArrayInputStream stream = new ByteArrayInputStream(blob);
						cover = BitmapFactory.decodeStream(stream);
					}
				}
				cursor.close();
			}

			return cover;
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
