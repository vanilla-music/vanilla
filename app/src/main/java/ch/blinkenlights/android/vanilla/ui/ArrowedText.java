/*
 * Copyright (C) 2020 Adrian Ulrich <adrian@blinkenlights.ch>
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

package ch.blinkenlights.android.vanilla.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.PaintDrawable;
import android.graphics.Path;
import android.widget.TextView;

public class ArrowedText extends TextView {
	/**
	 * Context this view uses.
	 */
	Context mContext;
	/**
	 * Color of the arrow on the left side.
	 */
	int mArrowColor;
	/**
	 * Controls the width of the drawn arrow.
	 */
	float mArrowWidth;
	/**
	 * 'padding' space (used left side) for the arrow to consume.
	 */
	int mArrowPadding;

	public ArrowedText(Context context) {
		super(context);
		mContext = context;
	}

	/**
	 * Configures the width of the arrow.
	 */
	public void setArrowWidth(int w) {
		mArrowWidth = w;
	}

	/**
	 * Configures how much space the arrow uses on the left side.
	 */
	public void setArrowPadding(int p) {
		mArrowPadding = p;
	}

	/**
	 * Set arrow and background color of this view.
	 */
	public void setColors(int colA, int colB) {
		PaintDrawable bg = new PaintDrawable(colB);
		setBackgroundDrawable(bg);
		mArrowColor = colA;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		Paint paint = new Paint();
		paint.setColor(mArrowColor);
		paint.setAntiAlias(true);
		paint.setStyle(Paint.Style.FILL_AND_STROKE);

		int h = canvas.getHeight();
	    float m = h/2.0f;
		Path path = new Path();
		path.moveTo(0, 0);
		path.lineTo(mArrowPadding, 0);
		path.lineTo(mArrowPadding + mArrowWidth, m);
		path.lineTo(mArrowPadding, h);
		path.lineTo(0, h);
		path.lineTo(0,0);
		path.close();
		canvas.drawPath(path, paint);
		super.onDraw(canvas);
	}
}
