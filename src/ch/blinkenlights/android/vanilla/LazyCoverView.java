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
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.widget.ImageView;

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
	 * Cover LRU cache LRU cache
	 */
	private static CoverCache sCoverCache;
	/**
	 * The cover key we are expected to draw
	 */
	private CoverCache.CoverKey mExpectedKey;

	/**
	 * Cover message we are passing around using mHandler
	 */
	private static class CoverMsg {
		public CoverCache.CoverKey key;
		public LazyCoverView view; // The view we are updating
		CoverMsg(CoverCache.CoverKey key, LazyCoverView view) {
			this.key = key;
			this.view = view;
		}
		/**
		 * Returns true if the view still requires updating
		 */
		public boolean isRecent() {
			return this.key.equals(this.view.mExpectedKey);
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
		if (sCoverCache == null) {
			sCoverCache = new CoverCache(mContext);
		}
		if (sFallbackBitmap == null) {
			sFallbackBitmap = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.fallback_cover);
		}
		if (sUiHandler == null) {
			sUiHandler = new Handler(this);
		}
		if (sHandler == null) {
			HandlerThread thread = new HandlerThread("LazyCoverRpc");
			thread.start();
			sHandler = new Handler(thread.getLooper(), this);
		}

	}

	/**
	 * mHandler and mUiHandler callbacks
	 */
	private static final int MSG_READ_COVER   = 60;
	private static final int MSG_CREATE_COVER = 61;
	private static final int MSG_DRAW_COVER   = 62;

	@Override
	public boolean handleMessage(Message message) {
		CoverMsg payload = (CoverMsg)message.obj;

		if (payload.isRecent() == false) {
			return false; // this rpc is obsolete
		}

		switch (message.what) {
			case MSG_READ_COVER: {
				// Cover was not in in-memory cache: Try to read from disk
				Bitmap bitmap = sCoverCache.getStoredCover(payload.key);
				if (bitmap != null) {
					// Got it: promote to memory cache and let ui thread draw it
					sCoverCache.cacheCover(payload.key, bitmap);
					sUiHandler.sendMessage(sUiHandler.obtainMessage(MSG_DRAW_COVER, payload));
				} else {
					sHandler.sendMessageDelayed(sHandler.obtainMessage(MSG_CREATE_COVER, payload), 80);
				}
				break;
			}
			case MSG_CREATE_COVER: {
				// This message was sent due to a cache miss, but the cover might got cached in the meantime
				Bitmap bitmap = sCoverCache.getCachedCover(payload.key);
				if (bitmap == null) {
					Song song = MediaUtils.getSongByTypeId(mContext.getContentResolver(), payload.key.mediaType, payload.key.mediaId);
					if (song != null) {
						// we got a song, try to fetch a cover
						// will also populate all caches if a cover was found
						bitmap = sCoverCache.getCoverFromSong(payload.key, song);
					}
					if (bitmap == null) {
						// song has no cover: return a failback one and store
						// it (only) in memory
						bitmap = sFallbackBitmap;
						sCoverCache.cacheCover(payload.key, bitmap);
					}
				}
				sUiHandler.sendMessage(sUiHandler.obtainMessage(MSG_DRAW_COVER, payload));
				break;
			}
			case MSG_DRAW_COVER: {
				// draw the cover into view. must be called from ui thread handler
				payload.view.drawFromCache(payload.key, true);
				break;
			}
			default:
				return false;
		}
		return true;
	}

	/**
	 * Attempts to set the image of this cover
	 * Must be called from an UI thread
	 * 
	 * @param type The Media type
	 * @param id The id of this media type to query
	 */
	public void setCover(int type, long id) {
		mExpectedKey = new CoverCache.CoverKey(type, id, CoverCache.SIZE_SMALL);
		if (drawFromCache(mExpectedKey, false) == false) {
			CoverMsg payload = new CoverMsg(mExpectedKey, this);
			// We put the message at the queue start to out-race slow CREATE RPC's
			sHandler.sendMessage(sHandler.obtainMessage(MSG_READ_COVER, payload));
		}
	}

	/**
	 * Updates the view with a cached bitmap
	 * A fallback image will be used on cache miss
	 *
	 * @param key The cover message containing the cache key and view to use
	 */
	public boolean drawFromCache(CoverCache.CoverKey key, boolean fadeIn) {
		boolean cacheHit = true;
		Bitmap bitmap = sCoverCache.getCachedCover(key);
		if (bitmap == null) {
			cacheHit = false;
		}

		if (fadeIn) {
			TransitionDrawable td = new TransitionDrawable(new Drawable[] {
				getDrawable(),
				(new BitmapDrawable(getResources(), bitmap))
			});
			setImageDrawable(td);
			td.startTransition(120);
		} else {
			setImageBitmap(bitmap);
		}

		return cacheHit;
	}

}
