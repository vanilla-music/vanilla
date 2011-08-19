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

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/**
 * Custom layout that acts like a very simple vertical LinearLayout with
 * special case: CoverViews will be made square at all costs.
 */
public class MiniPlaybackActivityLayout extends ViewGroup {
	private int mCoverSize;

	public MiniPlaybackActivityLayout(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		int maxHeight = MeasureSpec.getSize(heightMeasureSpec);

		int measuredHeight = 0;
		int measuredWidth = 0;

		View coverView = null;
		for (int i = getChildCount(); --i != -1; ) {
			View view = getChildAt(i);
			if (view instanceof CoverView) {
				coverView = view;
			} else {
				int spec = MeasureSpec.makeMeasureSpec(maxHeight - measuredHeight, MeasureSpec.AT_MOST);
				view.measure(widthMeasureSpec, spec);
				measuredHeight += view.getMeasuredHeight();
				if (view.getMeasuredWidth() > measuredWidth)
					measuredWidth = view.getMeasuredWidth();
			}
		}

		if (coverView != null) {
			if (measuredHeight + measuredWidth > maxHeight) {
				mCoverSize = maxHeight - measuredHeight;
				measuredHeight = maxHeight;
			} else {
				mCoverSize = measuredWidth;
				measuredHeight += measuredWidth;
			}
		}

		setMeasuredDimension(measuredWidth, measuredHeight);
	}

	@Override
	protected void onLayout(boolean arg0, int arg1, int arg2, int arg3, int arg4)
	{
		int layoutWidth = getMeasuredWidth();
		int top = 0;

		for (int i = 0, end = getChildCount(); i != end; ++i) {
			View view = getChildAt(i);
			if (view instanceof CoverView) {
				view.layout(0, top, layoutWidth, top + mCoverSize);
				top += mCoverSize;
			} else {
				int height = view.getMeasuredHeight();
				int width = view.getMeasuredWidth();
				int left = (layoutWidth - width) / 2;
				view.layout(left, top, layoutWidth - left, top + height);
				top += height;
			}
		}
	}
}
