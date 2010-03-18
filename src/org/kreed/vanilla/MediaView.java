/*
 * Copyright (C) 2010 Christopher Eby <kreed@kreed.org>
 *
 * This file is part of Vanilla Music Player.
 *
 * Vanilla Music Player is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Vanilla Music Player is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.kreed.vanilla;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

public class MediaView extends View {
	private static float mTextSize = -1;
	private static Bitmap mExpander = null;

	private SongData mData;
	private int mPrimaryField;
	private int mSecondaryField;
	private boolean mHasExpander;

	private boolean mExpanderPressed;

	public MediaView(Context context, int primaryField, int secondaryField, boolean hasExpander)
	{
		super(context);

		if (mExpander == null)
			mExpander = BitmapFactory.decodeResource(context.getResources(), R.drawable.expander_arrow);
		if (mTextSize == -1) {
			DisplayMetrics metrics = context.getResources().getDisplayMetrics();
			mTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, metrics);
		}

		mPrimaryField = primaryField;
		mSecondaryField = secondaryField;
		mHasExpander = hasExpander;
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		int expanderHeight;
		int textHeight;

		if (mHasExpander)
			expanderHeight = mExpander.getHeight() + (int)mTextSize;
		else
			expanderHeight = 0;

		if (mSecondaryField != -1)
			textHeight = (int)(7 * mTextSize / 2);
		else
			textHeight = (int)(2 * mTextSize);

		setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec),
		                     Math.max(expanderHeight, textHeight));
	}

	@Override
	public void onDraw(Canvas canvas)
	{
		if (mData == null)
			return;

		int width = getWidth();
		int height = getHeight();
		float padding = mTextSize / 2;

		Paint paint = new Paint();
		paint.setTextSize(mTextSize);
		paint.setAntiAlias(true);

		if (mHasExpander) {
			width -= padding * 3 + mExpander.getWidth();
			canvas.drawBitmap(mExpander, width + padding * 2, (height - mExpander.getHeight()) / 2, paint);
		}

		canvas.clipRect(padding, 0, width - padding, height);

		float allocatedHeight;

		if (mSecondaryField != -1) {
			allocatedHeight = height / 2 - padding * 3 / 2;

			paint.setColor(Color.GRAY);
			canvas.drawText(mData.getField(mSecondaryField), padding, height / 2 + padding / 2 + (allocatedHeight - mTextSize) / 2 - paint.ascent(), paint);
		} else {
			allocatedHeight = height - padding * 2;
		}

		paint.setColor(Color.WHITE);
		canvas.drawText(mData.getField(mPrimaryField), padding, padding + (allocatedHeight - mTextSize) / 2 - paint.ascent(), paint);
	}

	public void updateMedia(SongData data)
	{
		mData = data;
		invalidate();
	}

	public SongData getExpanderData()
	{
		return mExpanderPressed ? mData : null;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event)
	{
		mExpanderPressed = mHasExpander && event.getX() > getWidth() - mExpander.getWidth() - 3 * mTextSize / 2;
		return false;
	}
}