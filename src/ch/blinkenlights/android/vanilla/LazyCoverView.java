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
	}

	/**
	 * Setup the handler of this view instance. This function
	 * must be called before calling setCover().
	 * 
	 * @param looper The worker thread to use for image scaling
	 */
	public void setup(Looper looper) {
		if (sCoverCache == null) {
			sCoverCache = new CoverCache(mContext);
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
	}


	/**
	 * mHandler and mUiHandler callbacks
	 */
	private static final int MSG_FETCH_COVER = 60;
	private static final int MSG_DRAW_COVER = 61;

	@Override
	public boolean handleMessage(Message message) {
		CoverMsg payload = (CoverMsg)message.obj;

		if (payload.isRecent() == false) {
			return false; // this rpc is obsolete
		}

		switch (message.what) {
			case MSG_FETCH_COVER: {
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
				// Request UI-Thread to draw the bitmap
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
			int delay = 1;
			if (sHandler.hasMessages(MSG_FETCH_COVER)) {
				// User is probably scrolling fast as there is already a queued read job
				// wait 100ms as this view will most likely be obsolete soon anyway.
				// This frees us from scaling bitmaps we are never going to show
				delay = 100;
			}
			CoverMsg payload = new CoverMsg(mExpectedKey, this);
			sHandler.sendMessageDelayed(sHandler.obtainMessage(MSG_FETCH_COVER, payload), delay);
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
