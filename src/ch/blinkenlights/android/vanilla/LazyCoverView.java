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
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.TransitionDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.util.LruCache;
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
	 * Our private LRU cache
	 */
	private static BitmapLruCache sBitmapLruCache;
	/**
	 * The cover key we are expected to draw
	 */
	private CoverCache.CoverKey mExpectedKey;

	/**
	 * Cover message we are passing around using mHandler
	 */
	private static class CoverMsg {
		public CoverCache.CoverKey key; // A cache key identifying this RPC
		public LazyCoverView view;      // The view we are updating
		public String title;            // The title of this view, used for Initial-Covers
		CoverMsg(CoverCache.CoverKey key, LazyCoverView view, String title) {
			this.key = key;
			this.view = view;
			this.title = title;
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
		if (sBitmapLruCache == null) {
			sBitmapLruCache = new BitmapLruCache(6*1024*1024);
		}
		if (sFallbackBitmap == null) {
			sFallbackBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.fallback_cover);
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
	private static final int MSG_CREATE_COVER = 61;
	private static final int MSG_DRAW_COVER   = 62;

	@Override
	public boolean handleMessage(Message message) {
		CoverMsg payload = (CoverMsg)message.obj;

		if (payload.isRecent() == false) {
			return false; // this rpc is obsolete
		}

		switch (message.what) {
			case MSG_CREATE_COVER: {
				// This message was sent due to a cache miss, but the cover might got cached in the meantime
				Bitmap bitmap = sBitmapLruCache.get(payload.key);
				if (bitmap == null) {
					if (payload.key.mediaType == MediaUtils.TYPE_ALBUM) {
						// We only display real covers for queries using the album id as key
						Song song = MediaUtils.getSongByTypeId(mContext.getContentResolver(), payload.key.mediaType, payload.key.mediaId);
						if (song != null) {
							bitmap = song.getSmallCover(mContext);
						}
					} else {
						bitmap = drawCoverFromString(payload.title);
					}
					if (bitmap == null) {
						// item has no cover: return a failback
						bitmap = sFallbackBitmap;
					}
				}
				// bitmap is non null: store in LRU cache and draw it
				sBitmapLruCache.put(payload.key, bitmap);
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
	public void setCover(int type, long id, String title) {
		mExpectedKey = new CoverCache.CoverKey(type, id, CoverCache.SIZE_SMALL);
		if (drawFromCache(mExpectedKey, false) == false) {
			CoverMsg payload = new CoverMsg(mExpectedKey, this, title);
			sHandler.sendMessage(sHandler.obtainMessage(MSG_CREATE_COVER, payload));
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
		Bitmap bitmap = sBitmapLruCache.get(key);
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

	/**
	 * Draws a placeholder cover from given title string
	 *
	 * @param title A text string to use in the cover
	 * @return bitmap The drawn bitmap
	 */
	private Bitmap drawCoverFromString(String title) {
		if (title == null)
			return null;

		final int canvasSize = CoverCache.SIZE_SMALL;
		final float textSize = canvasSize * 0.45f;

		title = title.replaceFirst("(?i)^The ", ""); // 'The\s' shall not be a part of the string we are drawing.
		String subText = (title+"  ").substring(0,2);

		Bitmap bitmap = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.RGB_565);
		Canvas canvas = new Canvas(bitmap);
		Paint paint = new Paint();

		// Picks a semi-random color from tiles_colors.xml
		TypedArray colors = getResources().obtainTypedArray(R.array.letter_tile_colors);
		int color = colors.getColor(Math.abs(title.hashCode()) % colors.length(), 0);
		colors.recycle();
		paint.setColor(color);

		paint.setStyle(Paint.Style.FILL);
		canvas.drawPaint(paint);

		paint.setARGB(255, 255, 255, 255);
		paint.setAntiAlias(true);
		paint.setTextSize(textSize);

		Rect bounds = new Rect();
		paint.getTextBounds(subText, 0, subText.length(), bounds);

		canvas.drawText(subText, (canvasSize/2f)-bounds.exactCenterX(), (canvasSize/2f)-bounds.exactCenterY(), paint);
		return bitmap;

	}


	/**
	* A LRU cache implementation, using the CoverKey as key to store Bitmap objects
	*
	* Note that the implementation does not override create() in order to enable
	* the use of fetch-if-cached functions: createBitmap() is therefore called
	* by CoverCache itself.
	*/
	private static class BitmapLruCache extends LruCache<CoverCache.CoverKey, Bitmap> {
		/**
		 * Creates a new in-memory LRU cache
		 *
		 * @param size the lru cache size in bytes
		 */
		public BitmapLruCache(int size) {
			super(size);
		}

		/**
		 * Returns the cache size in bytes, not objects
		 */
		@Override
		protected int sizeOf(CoverCache.CoverKey key, Bitmap value) {
			return value.getByteCount();
		}
	}

}
