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

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

/**
 * Single view that paints one or two text fields and an optional arrow
 * to the right side.
 */
public final class MediaView extends View {
	/**
	 * The expander arrow bitmap used in all views that have expanders.
	 */
	private static Bitmap sExpander;
	/**
	 * The paint object, cached for reuse.
	 */
	private static Paint sPaint;
	/**
	 * The cached dash effect that separates the expander arrow and the text.
	 */
	private static DashPathEffect sDashEffect;
	/**
	 * The cached divider gradient that separates each view from other views.
	 */
	private static RadialGradient sDividerGradient;
	/**
	 * The text size used for the text in all views.
	 */
	private static int sTextSize;

	public static void init(Context context)
	{
		Resources res = context.getResources();
		sExpander = BitmapFactory.decodeResource(res, R.drawable.expander_arrow);
		sTextSize = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, res.getDisplayMetrics());
		sDashEffect = new DashPathEffect(new float[] { 3, 3 }, 0);
		sDividerGradient = null;

		sPaint = new Paint();
		sPaint.setTextSize(sTextSize);
		sPaint.setAntiAlias(true);
	}

	/**
	 * The MediaStore id of the media represented by this view.
	 */
	private long mId;
	/**
	 * The primary text field in the view, displayed on the upper line.
	 */
	private String mTitle;
	/**
	 * The secondary text field in the view, displayed on the lower line.
	 */
	private String mSubTitle;
	/**
	 * True if the last touch event was over the expander arrow.
	 */
	private boolean mExpanderPressed;
	/**
	 * True if the expander should be shown.
	 */
	private boolean mExpandable;
	/**
	 * The cached measured view height.
	 */
	private int mViewHeight;
	/**
	 * True if this view is a header. Will override expandable setting to false.
	 */
	private boolean mIsHeader;

	/**
	 * Construct a MediaView.
	 *
	 * @param context A Context to use.
	 * @param expandable True if the expander should be shown.
	 */
	public MediaView(Context context, boolean expandable)
	{
		super(context);

		mExpandable = expandable;

		mViewHeight = (int)(7 * sTextSize / 2);
		if (expandable)
			mViewHeight = Math.max(mViewHeight, sExpander.getHeight() + sTextSize);
	}

	/**
	 * Request the cached height and maximum width from the layout.
	 */
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), mViewHeight);
	}

	/**
	 * Draw the view on the given canvas.
	 */
	@Override
	public void onDraw(Canvas canvas)
	{
		if (mTitle == null)
			return;

		int width = getWidth();
		int height = getHeight();
		int padding = sTextSize / 2;

		Paint paint = sPaint;

		if (mExpandable && !mIsHeader) {
			Bitmap expander = sExpander;
			width -= padding * 4 + expander.getWidth();

			paint.setColor(Color.GRAY);
			paint.setPathEffect(sDashEffect);
			canvas.drawLine(width, padding, width, height - padding, paint);
			paint.setPathEffect(null);

			canvas.drawBitmap(expander, width + padding * 2, (height - expander.getHeight()) / 2, paint);
		}

		canvas.save();
		canvas.clipRect(padding, 0, width - padding, height);

		int allocatedHeight;

		if (mSubTitle != null) {
			allocatedHeight = height / 2 - padding * 3 / 2;

			paint.setColor(Color.GRAY);
			canvas.drawText(mSubTitle, padding, height / 2 + padding / 2 + (allocatedHeight - sTextSize) / 2 - paint.ascent(), paint);
		} else {
			allocatedHeight = height - padding * 2;
		}

		paint.setColor(Color.WHITE);
		canvas.drawText(mTitle, padding, padding + (allocatedHeight - sTextSize) / 2 - paint.ascent(), paint);
		canvas.restore();

		width = getWidth();

		if (sDividerGradient == null)
			sDividerGradient = new RadialGradient(width / 2, 1, width / 2, Color.WHITE, Color.BLACK, Shader.TileMode.CLAMP);

		paint.setShader(sDividerGradient);
		canvas.drawLine(0, height - 1, width, height - 1, paint);
		paint.setShader(null);
	}

	/**
	 * Returns the MediaStore id of the media represented by this view.
	 */
	public long getMediaId()
	{
		return mId;
	}

	/**
	 * Returns the title of this view, the primary/upper field.
	 */
	public String getTitle()
	{
		return mTitle;
	}

	/**
	 * Returns true if the expander arrow was pressed in the last touch
	 * event.
	 */
	public boolean isExpanderPressed()
	{
		return mExpanderPressed;
	}

	/**
	 * Returns true if views has expander arrows displayed.
	 */
	public boolean hasExpanders()
	{
		return mExpandable;
	}

	/**
	 * Set this view to be a header (custom text, never expandable).
	 *
	 * @param text The text to show.
	 */
	public void makeHeader(String text)
	{
		mIsHeader = true;
		mTitle = text;
		mSubTitle = null;
	}

	/**
	 * Update the fields in this view with the data from the given Cursor.
	 *
	 * @param cursor A cursor moved to the correct position. The first
	 * column must be the id of the media, the second the primary field.
	 * If this adapter contains more than one field, the third column
	 * must contain the secondary field.
	 * @param useSecondary True if the secondary field should be read.
	 */
	public void updateMedia(Cursor cursor, boolean useSecondary)
	{
		mIsHeader = false;
		mId = cursor.getLong(0);
		mTitle = cursor.getString(1);
		if (useSecondary)
			mSubTitle = cursor.getString(2);
		invalidate();
	}

	/**
	 * Returns true if the view is set to be a "Play/Enqueue All" header.
	 */
	public boolean isHeader()
	{
		return mIsHeader;
	}

	/**
	 * Update mExpanderPressed.
	 */
	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		if (mExpandable && !mIsHeader)
			mExpanderPressed = event.getX() > getWidth() - sExpander.getWidth() - 2 * sTextSize;
		return false;
	}
}
