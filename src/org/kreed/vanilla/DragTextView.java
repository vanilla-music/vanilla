/*
 * Copyright (C) 2012 Christopher Eby <kreed@kreed.org>
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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.TextView;

/**
 * TextView that draws a divider line at the bottom.
 *
 * We draw the divider here rather than with ListView.setDivider() so we don't
 * have duplicate dividers when hiding a row for a drag.
 */
public class DragTextView extends TextView {
	private final Paint mPaint;

	public DragTextView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		Paint paint = new Paint();
		paint.setColor(0xff444444);
		mPaint = paint;
	}

	@Override
	public void onDraw(Canvas canvas)
	{
		int height = getHeight();
		if (height <= 1)
			return;
		super.onDraw(canvas);
		if (getDrawingCacheBackgroundColor() != DragListView.DRAG_COLOR) {
			// only draw divider when not dragging
			float h = height - 1;
			canvas.drawLine(0, h, getWidth(), h, mPaint);
		}
	}
}
