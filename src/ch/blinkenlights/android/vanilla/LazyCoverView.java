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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import android.util.AttributeSet;
import android.widget.ImageView;
import android.util.LruCache;

/**
 * LazyCoverView implements a 'song-aware' ImageView
 *
 * View updates should be triggered via setCover(type, id) to
 * instruct the view to load the cover from its own LRU cache.
 * 
 * The cover will automatically  be fetched & scaled in a background
 * thread on cache miss
 */
public class LazyCoverView extends ImageView
	implements Handler.Callback 
{
	/**
	 * Context of constructor
	 */
	private Context mContext;
	/**
	 * UI Thread handler
	 */
	private static Handler sUiHandler;
	/**
	 * Worker thread handler
	 */
	private static Handler sHandler;
	/**
	 * The fallback cover image resource encoded as bitmap
	 */
	private static Bitmap sFallbackBitmap;
	/**
	 * Bitmap LRU cache
	 */
	private static BitmapCache sBitmapCache;
	/**
	 * The key we are expected to draw
	 */
	private String mExpectedKey;
	/**
	 * Dimension of cached pictures in pixels
	 */
	private int mImageDimensionPx;
	/**
	 * Bitmap cache LRU cache implementation
	 */
	private class BitmapCache extends LruCache<String, Bitmap> {
		public BitmapCache(int size) {
			super(size);
		}
	}
	/**
	 * Cover message we are passing around using mHandler
	 */
	private static class CoverMsg {
		public int type;           // Media type
		public long id;            // ID of this media type to query
		public LazyCoverView view; // The view we are updating
		CoverMsg(int type, long id, LazyCoverView view) {
			this.type = type;
			this.id = id;
			this.view = view;
		}
		public String getKey() {
			return this.type +"/"+ this.id;
		}
		/**
		 * Returns true if the view still requires updating
		 */
		public boolean isRecent() {
			return this.getKey().equals(this.view.mExpectedKey);
		}
	}

	/**
	 * Constructor of class inflated from XML
	 *
	 * @param context The context of the calling activity
	 * @param attributes attributes passed by the xml inflater
	 */
	public LazyCoverView(Context context, AttributeSet attributes) {
		super(context, attributes);
		mContext = context;
	}

	/**
	 * Setup the handler of this view instance. This function
	 * must be called before calling setCover().
	 * 
	 * @param looper The worker thread to use for image scaling
	 */
	public void setup(Looper looper) {
		if (sBitmapCache == null) {
			sBitmapCache = new BitmapCache(255); // Cache up to 255 items
		}
		if (sFallbackBitmap == null) {
			sFallbackBitmap = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.fallback_cover);
		}
		if (sUiHandler == null) {
			sUiHandler = new Handler(this);
		}
		if (sHandler == null || sHandler.getLooper().equals(looper) == false) {
			sHandler = new Handler(looper, this);
		}
		// image dimension we are going to cache - we should probably calculate this
		mImageDimensionPx = 128;
	}


	/**
	 * mHandler and mUiHandler callbacks
	 */
	private static final int MSG_CACHE_COVER = 60;
	private static final int MSG_DRAW_COVER = 61;

	@Override
	public boolean handleMessage(Message message) {
		switch (message.what) {
			case MSG_CACHE_COVER: {
				CoverMsg payload = (CoverMsg)message.obj;

				if (payload.isRecent() == false) {
					// This RPC is already obsoleted: drop it
					break;
				}

				Bitmap bitmap = sBitmapCache.get(payload.getKey());
				if (bitmap == null) {
					Song song = MediaUtils.getSongByTypeId(mContext.getContentResolver(), payload.type, payload.id);
					if (song != null) {
						bitmap = song.getCover(mContext);
					}
					if (bitmap == null) {
						bitmap = sFallbackBitmap;
					}
					bitmap = Bitmap.createScaledBitmap(bitmap, mImageDimensionPx, mImageDimensionPx, true);
					sBitmapCache.put(payload.getKey(), bitmap);
				}
				sUiHandler.sendMessage(sUiHandler.obtainMessage(MSG_DRAW_COVER, payload));
				break;
			}
			case MSG_DRAW_COVER: {
				CoverMsg payload = (CoverMsg)message.obj;
				synchronized(payload.view) {
					if (payload.isRecent()) {
						drawFromCache(payload);
					}
				}
			}
			default:
				return false;
		}
		return true;
	}

	/**
	 * Attempts to set the image of this cover
	 * 
	 * @param type The Media type
	 * @param id The id of this media type to query
	 */
	public void setCover(int type, long id) {
		CoverMsg payload = new CoverMsg(type, id, this);
		mExpectedKey = payload.getKey();
		if (drawFromCache(payload) == false) {
			int delay = 1;
			if (sHandler.hasMessages(MSG_CACHE_COVER)) {
				// User is probably scrolling fast as there is already a queued resize job
				// wait 200ms as this view will most likely be obsolete soon anyway.
				// This frees us from scaling bitmaps we are never going to show
				delay = 200;
			}
			sHandler.sendMessageDelayed(sHandler.obtainMessage(MSG_CACHE_COVER, payload), delay);
		}
	}

	/**
	 * Updates the view with a cached bitmap
	 * A fallback image will be used on cache miss
	 *
	 * @param payload The cover message containing the cache key and view to use
	 */
	public boolean drawFromCache(CoverMsg payload) {
		boolean cacheHit = true;
		Bitmap bitmap = sBitmapCache.get(payload.getKey());
		if (bitmap == null) {
			cacheHit = false;
			bitmap = sFallbackBitmap;
		}
		payload.view.setImageBitmap(bitmap);
		return cacheHit;
	}

}
