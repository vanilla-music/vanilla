/*
 * Copyright (C) 2010, 2011 Christopher Eby <kreed@kreed.org>
 * Copyright 2017-2020 Adrian Ulrich <adrian@blinkenlights.ch>
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

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
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
	 * Whether or not to debug painting operations.
	 */
	private static final boolean DEBUG_PAINT = false;
	/**
	 * Cover slack ratio
	 */
	private static final float SLACK_RATIO = 0.95f;

	private static int TEXT_SIZE = -1;
	private static int TEXT_SIZE_BIG;
	private static int PADDING;
	private static int TOP_PADDING;
	private static int BOTTOM_PADDING;

	/**
	 * Initialize the regular text size members.
	 *
	 * @param context A context to use.
	 */
	private static void loadTextSizes(Context context)
	{
		DisplayMetrics metrics = context.getResources().getDisplayMetrics();
		TEXT_SIZE = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, metrics);
		TEXT_SIZE_BIG = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, metrics);
		PADDING = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, metrics);
		// padding to take actionbar into account.
		TOP_PADDING = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, metrics);
		// space we keep from the view bottom.
		BOTTOM_PADDING = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 32, metrics);
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
	 * @param context A context to use.
	 * @param style One of CoverBitmap.STYLE_*
	 * @param coverArt The cover art for the song.
	 * @param song Title and other data are taken from here for info modes.
	 * @param width Maximum width of image
	 * @param height Maximum height of image
	 * @return The image, or null if the song was null, or width or height
	 * were less than 1
	 */
	public static Bitmap createBitmap(Context context, int style, Bitmap coverArt, Song song, int width, int height)
	{
		switch (style) {
		case STYLE_OVERLAPPING_BOX:
			return createOverlappingBitmap(context, coverArt, song, width, height);
		case STYLE_INFO_BELOW:
			return createSeparatedBitmap(context, coverArt, song, width, height);
		case STYLE_NO_INFO:
			return createScaledBitmap(coverArt, width, height);
		default:
			throw new IllegalArgumentException("Invalid bitmap type given: " + style);
		}
	}

	private static Bitmap createOverlappingBitmap(Context context, Bitmap cover, Song song, int width, int height)
	{
		if (TEXT_SIZE == -1)
			loadTextSizes(context);

		Paint paint = new Paint();
		paint.setAntiAlias(true);

		String title = song.title == null ? "" : song.title;
		String album = song.album == null ? "" : song.album;
		String artist = song.albumArtist == null ? "" : song.albumArtist;

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

		int coverWidth = 0;
		int coverHeight = 0;
		if (cover != null) {
			cover = createScaledBitmap(cover, width, height);
			coverWidth = cover.getWidth();
			coverHeight = cover.getHeight();
		}

		Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);

		if (DEBUG_PAINT) {
			Paint dpaint = new Paint();
			dpaint.setColor(0xffff0000);
			dpaint.setStyle(Paint.Style.FILL);
			canvas.drawPaint(dpaint);
		}

		if (coverWidth != 0) {
			canvas.drawBitmap(cover, (width-coverWidth)/2, (height-coverHeight)/2, new Paint());
		}

		int left = (width - boxWidth) / 2;
		int top = (height - boxHeight) / 2;
		int right = (width + boxWidth) / 2;
		int bottom = (height + boxHeight) / 2;

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

	private static Bitmap createSeparatedBitmap(Context context, Bitmap cover, Song song, int width, int height)
	{
		if (TEXT_SIZE == -1)
			loadTextSizes(context);

		int textSize = TEXT_SIZE;
		int textSizeBig = TEXT_SIZE_BIG;
		int padding = PADDING;
		int topPadding = TOP_PADDING;
		int bottomPadding = BOTTOM_PADDING;

		// Get desired text color from theme and draw textual information
		int colors[] = ThemeHelper.getDefaultCoverColors(context);
		// inverted cover background color.
		int textColor = 0xFF000000 + (0xFFFFFF - (colors[0] & 0xFFFFFF));
		// Whether or not the image is rendered in vertical mode.
		boolean verticalMode = width > height;

		// Prepare text and calculate minimum height, so we can ensure
		// that the cover doesn't 'eat' into it.
		String title = song.title == null ? "" : song.title;
		String album = song.album == null ? "" : song.album;
		String artist = song.albumArtist == null ? "" : song.albumArtist;
		// Space required to draw the bottom text.
		int textTotalHeight = padding + textSizeBig + (padding+textSize) * 2 + padding;
		// Y coord where text shall be placed.
		int textStart = (verticalMode ? (height-textTotalHeight)/2 : height - bottomPadding - textTotalHeight);

		Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(bitmap);

		if (DEBUG_PAINT) {
			Paint dpaint = new Paint();
			dpaint.setColor(0xffff0033);
			dpaint.setStyle(Paint.Style.FILL);
			canvas.drawPaint(dpaint);
		}

		if (cover != null) {
			Bitmap scaled;
			if (verticalMode) {
				int hh = height/2;
				int hw = width/2;
				scaled = createScaledBitmap(cover, Math.min(hw, height), Math.min(hw, height));
				canvas.drawBitmap(scaled, (hw-scaled.getWidth())/2, (height-scaled.getHeight())/2, null);
			} else {
				scaled = createScaledBitmap(cover, width, textStart);
				canvas.drawBitmap(scaled, (width-scaled.getWidth())/2, 0, null);
			}
		}

		// Where to start drawing the text.
		int top = textStart;

		// Now draw the text:
		PorterDuffColorFilter filter = new PorterDuffColorFilter(textColor, PorterDuff.Mode.SRC_ATOP);
		Paint paint = new Paint();
		paint.setAntiAlias(true);

		// how much to shift text to the right forcefully.
		int tshift = (verticalMode ? width/2 : 0);
		// Title
		paint.setColorFilter(filter);
		paint.setTextSize(textSizeBig);
		int twidth = (int)paint.measureText(title);
		int tstart = tshift+(width - tshift - twidth)/2;
		drawText(canvas, title, tstart, top, width, twidth, paint);

		// Bottom text
		paint.setAlpha(0xAA);
		paint.setTextSize(textSize);

		// Album
		top += textSizeBig + padding;
		twidth = (int)paint.measureText(album);
		tstart = tshift+(width - tshift - twidth)/2;
		drawText(canvas, album, tstart, top, width, twidth, paint);

		// Artist
		top += textSize + padding;
		twidth = (int)paint.measureText(artist);
		tstart = tshift+(width - tshift - twidth)/2;
		drawText(canvas, artist, tstart, top, width, twidth, paint);

		return bitmap;
	}

	/**
	 * Scales a bitmap to fit in a rectangle of the given size. Aspect ratio is
	 * preserved. At least one dimension of the result will match the provided
	 * dimension exactly.
	 *
	 * @param source The bitmap to be scaled
	 * @param width Maximum width of image
	 * @param height Maximum height of image
	 * @return The scaled bitmap.
	 */
	private static Bitmap createScaledBitmap(Bitmap source, int width, int height)
	{
		int sourceWidth = source.getWidth();
		int sourceHeight = source.getHeight();
		float scale = Math.min((float)width / sourceWidth, (float)height / sourceHeight);
		sourceWidth *= scale;
		sourceHeight *= scale;
		Bitmap scaled = Bitmap.createScaledBitmap(source, (int)(SLACK_RATIO*sourceWidth), (int)(SLACK_RATIO*sourceHeight), true);
		return createBorderedBitmap(scaled, sourceWidth, sourceHeight);
	}

	/**
	 * Creates a bitmap with a round border. The returned bitmap will fit exactly
	 * into the passed in dimensions and the source image is not scaled.
	 *
	 * @param source the source bitmap to use
	 * @param width the with of the returned bitmap
	 * @param height the height of the returned bitmap
	 * @return the painted bitmap
	 */
	private static Bitmap createBorderedBitmap(Bitmap source, int width, int height) {
		Bitmap dst = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		Canvas canvas = new Canvas(dst);

		if (DEBUG_PAINT) {
			Paint dpaint = new Paint();
			dpaint.setColor(0xff0000ff);
			dpaint.setStyle(Paint.Style.FILL);
			canvas.drawPaint(dpaint);
		}

		BitmapShader shader = new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
		Paint paint = new Paint();
		paint.setAntiAlias(true);
		paint.setShader(shader);

		RectF rect = new RectF(0.0f, 0.0f, source.getWidth(), source.getHeight());
		float radius = 12;
		canvas.translate((height-source.getHeight())/2, (width-source.getWidth())/2);
		canvas.drawRoundRect(rect, radius, radius, paint);
		return dst;
	}

	/**
	 * Generate the default cover (a rendition of a music note). Returns a square iamge.
	 * Both dimensions are the lesser of width and height.
	 *
	 * @param width The max width
	 * @param height The max height
	 * @return The default cover.
	 */
	public static Bitmap generateDefaultCover(Context context, int width, int height)
	{
		int size = Math.min(width, height);

		int[] colors = ThemeHelper.getDefaultCoverColors(context);
		int rgb_background = colors[0];
		int rgb_note_inner = colors[1];

		final int line_thickness = size / 10;
		final int line_vertical = line_thickness*5;
		final int line_horizontal = line_thickness*3;
		final int circle_radius = line_thickness*2;

		final int total_len_x = circle_radius*2 + line_horizontal - line_thickness; // total length of x axis
		final int total_len_y = circle_radius + line_vertical + (line_thickness/2); // total length of y axis
		final int xoff = circle_radius + (size - total_len_x)/2;                    // 'center offset' of x
		final int yoff = size - circle_radius - (size - total_len_y)/2;             // 'center offset' of y

		Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
		bitmap.eraseColor(rgb_background);

		Paint paint = new Paint();
		paint.setAntiAlias(true);
		paint.setColor(rgb_note_inner);

		Canvas canvas = new Canvas(bitmap);
		// main circle
		canvas.drawCircle(xoff, yoff, circle_radius, paint);
		// vertical line
		int lpos = xoff + circle_radius - line_thickness; // lpos + thickness will touch the outer right edge of the circle
		int tpos = yoff - line_vertical;                  // tpos + vertical will be the center of the circle
		canvas.drawRoundRect(new RectF(lpos, tpos, lpos+line_thickness, yoff), 0, 0, paint);
		// horizontal line
		int hdiff = tpos - (line_thickness/2); // shift this up by half of the thickness to have the circle radius touch the top of the vertical line
		canvas.drawRoundRect(new RectF(lpos, hdiff, lpos+line_horizontal, hdiff+line_thickness), line_thickness, line_thickness, paint);

		return bitmap;
	}

	/**
	 * Draws a placeholder cover from given title string
	 *
	 * @param title A text string to use in the cover
	 * @return bitmap The drawn bitmap
	 */
	public static Bitmap generatePlaceholderCover(Context context, int width, int height, String title)
	{
		if (title == null || width < 1 || height < 1)
			return null;

		final float textSize = width * 0.4f;

		title = title.replaceFirst("(?i)^The ", ""); // 'The\s' shall not be a part of the string we are drawing.
		title = title.replaceAll("[ <>_-]", ""); // Remove clutter, so eg. J-Rock becomes JR
		String subText = (title+"  ").substring(0,2);

		// Use only the first char if it is 'wide'
		if(Character.UnicodeBlock.of(subText.charAt(0)) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
			subText = subText.substring(0,1);
		}

		Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
		Canvas canvas = new Canvas(bitmap);
		Paint paint = new Paint();

		// Picks a semi-random color from tiles_colors.xml
		TypedValue tv = new TypedValue();
		context.getTheme().resolveAttribute(R.attr.themed_letter_tile_colors, tv, false);
		TypedArray colors = context.getResources().obtainTypedArray(tv.data);
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

		canvas.drawText(subText, (width/2f)-bounds.exactCenterX(), (height/2f)-bounds.exactCenterY(), paint);
		return bitmap;
	}
}
