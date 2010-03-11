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
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

public class MediaView extends ViewGroup {
	public static final int SECONDARY_LINE = 0x1;
	public static final int EXPANDER = 0x2;

	private TextView mPrimaryLine;
	private TextView mSecondaryLine;
	private ImageView mExpander;

	private int mPadding;

	public MediaView(Context context, int flags)
	{
		super(context);

		DisplayMetrics metrics = context.getResources().getDisplayMetrics();
		float textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, metrics);
		mPadding = (int)textSize / 2;

		mPrimaryLine = new TextView(context);
		mPrimaryLine.setSingleLine();
		mPrimaryLine.setTextColor(Color.WHITE);
		mPrimaryLine.setTextSize(textSize);
		addView(mPrimaryLine);

		if ((flags & SECONDARY_LINE) != 0) {
			mSecondaryLine = new TextView(context);
			mSecondaryLine.setSingleLine();
			mSecondaryLine.setTextSize(textSize);
			addView(mSecondaryLine);
		}

		if ((flags & EXPANDER) != 0) {
			mExpander = new ImageView(context);
			mExpander.setPadding(mPadding * 2, mPadding, mPadding, mPadding);
			mExpander.setImageResource(R.drawable.expander_arrow);
			addView(mExpander);
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		int width = MeasureSpec.getSize(widthMeasureSpec);

		widthMeasureSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST);
		heightMeasureSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

		int expanderHeight = 0;
		int textHeight = 4 * mPadding;

		if (mExpander != null) {
			mExpander.measure(widthMeasureSpec, heightMeasureSpec);
			expanderHeight = mExpander.getMeasuredHeight();
		}

		if (mSecondaryLine != null) {
			mSecondaryLine.measure(widthMeasureSpec, heightMeasureSpec);
			textHeight = mSecondaryLine.getMeasuredHeight() + mPadding;
		}

		mPrimaryLine.measure(widthMeasureSpec, heightMeasureSpec);
		textHeight += mPrimaryLine.getMeasuredHeight();

		setMeasuredDimension(width, Math.max(expanderHeight, textHeight));
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right, int bottom)
	{
		int width = right - left;
		int height = bottom - top;

		int textWidth;
		int textHeight = height - 2 * mPadding;

		int actualHeight;

		if (mExpander != null) {
			textWidth = width - mExpander.getMeasuredWidth();
			mExpander.layout(textWidth, 0, width, height);
		} else {
			textWidth = width;
		}

		textWidth -= 2 * mPadding;

		if (mSecondaryLine != null) {
			textHeight = (textHeight - mPadding) / 2;

			actualHeight = mSecondaryLine.getMeasuredHeight();
			top = mPadding * 3 / 2 + textHeight + (textHeight - actualHeight) / 2;
			mSecondaryLine.layout(mPadding, top, mPadding + textWidth, top + actualHeight);
		}

		actualHeight = mPrimaryLine.getMeasuredHeight();
		top = mPadding + (textHeight - actualHeight) / 2;
		mPrimaryLine.layout(mPadding, top, mPadding + textWidth, top + actualHeight);
	}

	public void setupExpander(int field, View.OnClickListener listener)
	{
		if (mExpander != null) {
			mExpander.setTag(R.id.field, field);
			mExpander.setOnClickListener(listener);
		}
	}

	public void updateMedia(Song song, int primaryField, int secondaryField)
	{
		if (mPrimaryLine != null)
			mPrimaryLine.setText(song.getField(primaryField));
		if (mSecondaryLine != null)
			mSecondaryLine.setText(song.getField(secondaryField));
		if (mExpander != null)
			mExpander.setTag(R.id.id, song.getFieldId(primaryField));
	}
}