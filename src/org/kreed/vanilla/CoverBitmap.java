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

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.TypedValue;

/**
 * Class containing utility functions to create Bitmaps display song info and
 * album art.
 */
public final class CoverBitmap {
	/**
	 * Draw cover in background and a box with song info on top.
	 */
	public static final int STYLE_OVERLAPPING_BOX = 0;
	/**
	 * Draw cover on top or left with song info on bottom or right (depending
	 * on orientation).
	 */
	public static final int STYLE_INFO_BELOW = 1;
	/**
	 * Draw no song info, only the cover.
	 */
	public static final int STYLE_NO_INFO = 2;
	/**
	 * Draw no song info and zoom the cover so that it fills the entire bitmap
	 * (preserving aspect ratio---some parts of the cover may be cut off).
	 */
	public static final int STYLE_NO_INFO_ZOOMED = 3;

	private static int TEXT_SIZE = -1;
	private static int TEXT_SIZE_BIG;
	private static int PADDING;
	private static int TEXT_SPACE;
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
		TEXT_SPACE = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 150, metrics);
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
	 * Create an image representing the given song. Includes cover art and
	 * possibly song title/artist/ablum, depending on the given style.
	 *
	 * @param style One of CoverBitmap.STYLE_*
	 * @param song The song to display information for
	 * @param width Maximum width of image
	 * @param height Maximum height of image
	 * @param bitmap A Bitmap to be drawn into. If null, a new Bitmap will be
	 * created. If the bitmap cannot be used, it will be recycled and a new
	 * Bitmap created.
	 * @return The image, or null if the song was null, or width or height
	 * were less than 1
	 */
	public static Bitmap createBitmap(int style, Song song, int width, int height, Bitmap bitmap)
	{
		switch (style) {
		case STYLE_OVERLAPPING_BOX:
			return createOverlappingBitmap(song, width, height, bitmap);
		case STYLE_INFO_BELOW:
			return createSeparatedBitmap(song, width, height, bitmap);
		case STYLE_NO_INFO:
			return createScaledBitmap(song, width, height);
		case STYLE_NO_INFO_ZOOMED:
			return createZoomedBitmap(song, width, height, bitmap);
		default:
			throw new IllegalArgumentException("Invalid bitmap type given: " + style);
		}
	}

	private static Bitmap createOverlappingBitmap(Song song, int width, int height, Bitmap bitmap)
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

			float scale = Math.min((float)width / coverWidth, (float)height / coverHeight);

			coverWidth *= scale;
			coverHeight *= scale;
		}

		int bitmapWidth = Math.max(coverWidth, boxWidth);
		int bitmapHeight = Math.max(coverHeight, boxHeight);

		if (bitmap != null) {
			if (bitmap.getHeight() != bitmapHeight || bitmap.getWidth() != bitmapWidth) {
				bitmap.recycle();
				bitmap = null;
			} else {
				bitmap.eraseColor(Color.BLACK);
			}
		}

		if (bitmap == null)
			bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.RGB_565);
		Canvas canvas = new Canvas(bitmap);

		if (cover != null) {
			int x = (bitmapWidth - coverWidth) / 2;
			int y = (bitmapHeight - coverHeight) / 2;
			Rect rect = new Rect(x, y, x + coverWidth, y + coverHeight);
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

	private static Bitmap createSeparatedBitmap(Song song, int width, int height, Bitmap bitmap)
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

			int maxWidth = horizontal ? width - TEXT_SPACE : width;
			int maxHeight = horizontal ? height : height - textSize * 3 - padding * 4;
			float scale = Math.min((float)maxWidth / coverWidth, (float)maxHeight / coverHeight);

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
			if (bitmap.getHeight() != bitmapHeight || bitmap.getWidth() != bitmapWidth) {
				bitmap.recycle();
				bitmap = null;
			} else {
				bitmap.eraseColor(Color.BLACK);
			}
		}

		if (bitmap == null)
			bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.RGB_565);
		Canvas canvas = new Canvas(bitmap);

		if (cover != null) {
			int x = horizontal ? 0 : (bitmapWidth - coverWidth) / 2;
			int y = horizontal ? (bitmapHeight - coverHeight) / 2 : 0;
			Rect rect = new Rect(x, y, x + coverWidth, y + coverHeight);
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

	private static Bitmap createZoomedBitmap(Song song, int width, int height, Bitmap bitmap)
	{
		if (song == null || width < 1 || height < 1)
			return null;

		Bitmap cover = song.getCover();
		if (cover == null)
			return null;

		if (bitmap != null && (bitmap.getHeight() != height || bitmap.getWidth() != width)) {
			bitmap.recycle();
			bitmap = null;
		}

		if (bitmap == null)
			bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
		Canvas canvas = new Canvas(bitmap);

		int coverWidth = cover.getWidth();
		int coverHeight = cover.getHeight();
		int size = Math.max(width, height);
		float scale = coverWidth < coverHeight ? (float)size / coverWidth : (float)size / coverHeight;

		int srcWidth = (int)(Math.min(width, coverWidth * scale) / scale);
		int srcHeight = (int)(Math.min(height, coverHeight * scale) / scale);
		int xOffset = (coverWidth - srcWidth) / 2;
		int yOffset = (coverHeight - srcHeight) / 2;
		Rect src = new Rect(xOffset, yOffset, coverWidth - xOffset, coverHeight - yOffset);
		Rect dest = new Rect(0, 0, width, height);
		canvas.drawBitmap(cover, src, dest, null);

		return bitmap;
	}

	/**
	 * Scales a cover to fit in a rectangle of the given size. Aspect ratio is
	 * preserved. At least one dimension of the result will match the provided
	 * dimension exactly.
	 *
	 * @param song The song to display information for
	 * @param width Maximum width of image
	 * @param height Maximum height of image
	 * @return The scaled Bitmap, or null if a cover could not be found.
	 */
	public static Bitmap createScaledBitmap(Song song, int width, int height)
	{
		if (song == null || width < 1 || height < 1)
			return null;

		Bitmap cover = song.getCover();
		if (cover == null)
			return null;

		int coverWidth = cover.getWidth();
		int coverHeight = cover.getHeight();
		float scale = Math.min((float)width / coverWidth, (float)height / coverHeight);
		coverWidth *= scale;
		coverHeight *= scale;
		return Bitmap.createScaledBitmap(cover, coverWidth, coverHeight, false);
	}
}
