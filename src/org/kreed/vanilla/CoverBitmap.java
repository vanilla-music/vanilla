/*
 * Copyright (C) 2010 Christopher Eby <kreed@kreed.org>
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

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.DisplayMetrics;
import android.util.TypedValue;

/**
 * Class containing utility functions to create Bitmaps display song info and
 * album art.
 */
public final class CoverBitmap {
	private static int TEXT_SIZE = -1;
	private static int TEXT_SIZE_BIG;
	private static int PADDING;
	private static int TEXT_SIZE_COMPACT = -1;
	private static int PADDING_COMPACT;
	private static Bitmap SONG_ICON;
	private static Bitmap ALBUM_ICON;
	private static Bitmap ARTIST_ICON;

	/**
	 * Initialize the regular text size members.
	 */
	private static void loadTextSizes()
	{
		DisplayMetrics metrics = ContextApplication.getContext().getResources().getDisplayMetrics();
		TEXT_SIZE = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, metrics);
		TEXT_SIZE_BIG = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, metrics);
		PADDING = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, metrics);
	}

	/**
	 * Initialize the compact text size members.
	 */
	private static void loadMiniTextSizes()
	{
		DisplayMetrics metrics = ContextApplication.getContext().getResources().getDisplayMetrics();
		TEXT_SIZE_COMPACT = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, metrics);
		PADDING_COMPACT = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, metrics);
	}

	/**
	 * Initialize the icon bitmaps.
	 */
	private static void loadIcons()
	{
		Resources res = ContextApplication.getContext().getResources();
		Bitmap bitmap = BitmapFactory.decodeResource(res, R.drawable.ic_tab_songs_selected);
		SONG_ICON = Bitmap.createScaledBitmap(bitmap, TEXT_SIZE, TEXT_SIZE, false);
		bitmap.recycle();
		bitmap = BitmapFactory.decodeResource(res, R.drawable.ic_tab_albums_selected);
		ALBUM_ICON = Bitmap.createScaledBitmap(bitmap, TEXT_SIZE, TEXT_SIZE, false);
		bitmap.recycle();
		bitmap = BitmapFactory.decodeResource(res, R.drawable.ic_tab_artists_selected);
		ARTIST_ICON = Bitmap.createScaledBitmap(bitmap, TEXT_SIZE, TEXT_SIZE, false);
		bitmap.recycle();
	}

	/**
	 * Helper function to draw text within a set width
	 *
	 * @param canvas The canvas to draw to
	 * @param text The text to draw
	 * @param left The x coordinate of the left edge of the text
	 * @param top The y coordinate of the top edge of the text
	 * @param width The measured width of the text
	 * @param maxWidth The maximum width of the text. Text outside of this width
	 * will be truncated.
	 * @param paint The paint style to use
	 */
	private static void drawText(Canvas canvas, String text, int left, int top, int width, int maxWidth, Paint paint)
	{
		canvas.save();
		int offset = Math.max(0, maxWidth - width) / 2;
		canvas.clipRect(left, top, left + maxWidth, top + paint.getTextSize() * 2);
		canvas.drawText(text, left + offset, top - paint.ascent(), paint);
		canvas.restore();
	}

	/**
	 * Scales a cover down to fit in a square of the specified size.
	 *
	 * @param song The Song to retrieve the cover from.
	 * @param size The width/height of the square. Must be greater than zero.
	 * @return The scaled Bitmap, or null if a cover could not be found.
	 */
	public static Bitmap createScaledBitmap(Song song, int size)
	{
		if (song == null || size < 1)
			return null;

		Bitmap cover = song.getCover();
		if (cover == null)
			return null;

		int coverWidth = cover.getWidth();
		int coverHeight = cover.getHeight();
		float scale = coverWidth > coverHeight ? (float)size / coverWidth : (float)size / coverHeight;
		coverWidth *= scale;
		coverHeight *= scale;
		return Bitmap.createScaledBitmap(cover, coverWidth, coverHeight, false);
	}

	/**
	 * Create a compact image, displaying cover art with the song title
	 * overlaid at the bottom edge.
	 *
	 * @param song The song to display information for
	 * @param width Desired width of image
	 * @param height Desired height of image
	 * @return The image, or null if the song was null, or width or height
	 * were less than 1
	 */
	public static Bitmap createCompactBitmap(Song song, int width, int height)
	{
		if (song == null || width < 1 || height < 1)
			return null;

		if (TEXT_SIZE_COMPACT == -1)
			loadMiniTextSizes();

		int textSize = TEXT_SIZE_COMPACT;
		int padding = PADDING_COMPACT;

		Paint paint = new Paint();
		paint.setAntiAlias(true);
		paint.setTextSize(textSize);

		String title = song.title == null ? "" : song.title;
		Bitmap cover = song.getCover();

		int titleWidth = (int)paint.measureText(title);

		int boxWidth = width;
		int boxHeight = Math.min(height, textSize + padding * 2);

		Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
		Canvas canvas = new Canvas(bitmap);

		if (cover != null)
			canvas.drawBitmap(cover, null, new Rect(0, 0, width, height), paint);

		int left = 0;
		int top = height - boxHeight;
		int right = width;
		int bottom = height;

		paint.setARGB(150, 0, 0, 0);
		canvas.drawRect(left, top, right, bottom, paint);

		int maxWidth = boxWidth - padding * 2;
		paint.setARGB(255, 255, 255, 255);
		top += padding;
		left += padding;

		drawText(canvas, title, left, top, titleWidth, maxWidth, paint);

		return bitmap;
	}

	/**
	 * Create a normal image, displaying cover art with the song title, album
	 * and artist overlaid in a box in the center.
	 *
	 * @param song The song to display information for
	 * @param width Maximum width of image
	 * @param height Maximum height of image
	 * @param bitmap A Bitmap to be drawn into. If null, a new Bitmap will be
	 * created. If too small, will be recycled and a new Bitmap will be
	 * created.
	 * @return The image, or null if the song was null, or width or height
	 * were less than 1
	 */
	public static Bitmap createOverlappingBitmap(Song song, int width, int height, Bitmap bitmap)
	{
		if (song == null || width < 1 || height < 1)
			return null;

		if (TEXT_SIZE == -1)
			loadTextSizes();

		Paint paint = new Paint();
		paint.setAntiAlias(true);

		String title = song.title == null ? "" : song.title;
		String album = song.album == null ? "" : song.album;
		String artist = song.artist == null ? "" : song.artist;
		Bitmap cover = song.getCover();

		int titleSize = TEXT_SIZE_BIG;
		int subSize = TEXT_SIZE;
		int padding = PADDING;

		paint.setTextSize(titleSize);
		int titleWidth = (int)paint.measureText(title);
		paint.setTextSize(subSize);
		int albumWidth = (int)paint.measureText(album);
		int artistWidth = (int)paint.measureText(artist);

		int boxWidth = Math.min(width, Math.max(titleWidth, Math.max(artistWidth, albumWidth)) + padding * 2);
		int boxHeight = Math.min(height, titleSize + subSize * 2 + padding * 4);

		int coverWidth;
		int coverHeight;

		if (cover == null) {
			coverWidth = 0;
			coverHeight = 0;
		} else {
			coverWidth = cover.getWidth();
			coverHeight = cover.getHeight();

			float scale;
			if ((float)coverWidth / coverHeight > (float)width / height)
				scale = (float)width / coverWidth;
			else
				scale = (float)height / coverHeight;

			coverWidth *= scale;
			coverHeight *= scale;
		}

		int bitmapWidth = Math.max(coverWidth, boxWidth);
		int bitmapHeight = Math.max(coverHeight, boxHeight);

		if (bitmap != null) {
			if (bitmap.getHeight() < bitmapHeight || bitmap.getWidth() < bitmapWidth) {
				bitmap.recycle();
				bitmap = null;
			} else {
				bitmap.eraseColor(Color.BLACK);
				bitmapHeight = bitmap.getHeight();
				bitmapWidth = bitmap.getWidth();
			}
		}

		if (bitmap == null)
			bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.RGB_565);
		Canvas canvas = new Canvas(bitmap);

		if (cover != null) {
			Rect rect = new Rect(0, 0, bitmapWidth, bitmapHeight);
			canvas.drawBitmap(cover, null, rect, paint);
		}

		int left = (bitmapWidth - boxWidth) / 2;
		int top = (bitmapHeight - boxHeight) / 2;
		int right = (bitmapWidth + boxWidth) / 2;
		int bottom = (bitmapHeight + boxHeight) / 2;

		paint.setARGB(150, 0, 0, 0);
		canvas.drawRect(left, top, right, bottom, paint);

		int maxWidth = boxWidth - padding * 2;
		paint.setARGB(255, 255, 255, 255);
		top += padding;
		left += padding;

		paint.setTextSize(titleSize);
		drawText(canvas, title, left, top, titleWidth, maxWidth, paint);
		top += titleSize + padding;

		paint.setTextSize(subSize);
		drawText(canvas, album, left, top, albumWidth, maxWidth, paint);
		top += subSize + padding;

		drawText(canvas, artist, left, top, artistWidth, maxWidth, paint);

		return bitmap;
	}

	/**
	 * Create a separated image, displaying cover art with the song title, album
	 * and artist below or to the right of the cover art.
	 *
	 * @param song The song to display information for
	 * @param width Maximum width of image
	 * @param height Maximum height of image
	 * @param bitmap A Bitmap to be drawn into. If null, a new Bitmap will be
	 * created. If too small, will be recycled and a new Bitmap will be
	 * created.
	 * @return The image, or null if the song was null, or width or height
	 * were less than 1
	 */
	public static Bitmap createSeparatedBitmap(Song song, int width, int height, Bitmap bitmap)
	{
		if (song == null || width < 1 || height < 1)
			return null;

		if (TEXT_SIZE == -1)
			loadTextSizes();
		if (SONG_ICON == null)
			loadIcons();

		boolean horizontal = width > height;

		Paint paint = new Paint();
		paint.setAntiAlias(true);

		String title = song.title == null ? "" : song.title;
		String album = song.album == null ? "" : song.album;
		String artist = song.artist == null ? "" : song.artist;
		Bitmap cover = song.getCover();

		int textSize = TEXT_SIZE;
		int padding = PADDING;

		int coverWidth;
		int coverHeight;

		if (cover == null) {
			coverWidth = 0;
			coverHeight = 0;
		} else {
			coverWidth = cover.getWidth();
			coverHeight = cover.getHeight();

			float scale;
			if ((float)coverWidth / coverHeight > (float)width / height)
				scale = (float)width / coverWidth;
			else
				scale = (float)height / coverHeight;

			coverWidth *= scale;
			coverHeight *= scale;
		}

		paint.setTextSize(textSize);
		int titleWidth = (int)paint.measureText(title);
		int albumWidth = (int)paint.measureText(album);
		int artistWidth = (int)paint.measureText(artist);

		int maxBoxWidth = horizontal ? width - coverWidth : width;
		int maxBoxHeight = horizontal ? height : height - coverHeight;
		int boxWidth = Math.min(maxBoxWidth, textSize + Math.max(titleWidth, Math.max(artistWidth, albumWidth)) + padding * 3);
		int boxHeight = Math.min(maxBoxHeight, textSize * 3 + padding * 4);

		int bitmapWidth = (int)(horizontal ? coverWidth + boxWidth : Math.max(coverWidth, boxWidth));
		int bitmapHeight = (int)(horizontal ? Math.max(coverHeight, boxHeight) : coverHeight + boxHeight);

		if (bitmap != null) {
			if (bitmap.getHeight() < bitmapHeight || bitmap.getWidth() < bitmapWidth) {
				bitmap.recycle();
				bitmap = null;
			} else {
				bitmap.eraseColor(Color.BLACK);
				bitmapHeight = bitmap.getHeight();
				bitmapWidth = bitmap.getWidth();
			}
		}

		if (bitmap == null)
			bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.RGB_565);
		Canvas canvas = new Canvas(bitmap);

		if (cover != null) {
			RectF rect = new RectF(0, 0, coverWidth, coverHeight);
			canvas.drawBitmap(cover, null, rect, paint);
		}

		int top;
		int left;

		if (horizontal) {
			top = (bitmapHeight - boxHeight) / 2;
			left = padding + coverWidth;
		} else {
			top = padding + coverHeight;
			left = padding;
		}

		int maxWidth = boxWidth - padding * 3 - textSize;
		paint.setARGB(255, 255, 255, 255);

		canvas.drawBitmap(SONG_ICON, left, top, paint);
		drawText(canvas, title, left + padding + textSize, top, maxWidth, maxWidth, paint);
		top += textSize + padding;

		canvas.drawBitmap(ALBUM_ICON, left, top, paint);
		drawText(canvas, album, left + padding + textSize, top, maxWidth, maxWidth, paint);
		top += textSize + padding;

		canvas.drawBitmap(ARTIST_ICON, left, top, paint);
		drawText(canvas, artist, left + padding + textSize, top, maxWidth, maxWidth, paint);

		return bitmap;
	}
}