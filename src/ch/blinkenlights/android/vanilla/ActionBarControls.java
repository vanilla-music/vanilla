/*
 * Copyright (C) 2015 Adrian Ulrich <adrian@blinkenlights.ch>
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
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.view.View;
import android.widget.LinearLayout;

/**
 * LinearLayout that contains some hacks for sizing inside an ActionBar.
 */
public class ActionBarControls extends LinearLayout {

	private final int dpiMaxWidth = 350;
	private final int dpiCompatMax = 200;
	private final int dpiElement = 50;
	private final int slackElements = 2;

	public ActionBarControls(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}

	@Override
	public void onMeasure(int ws, int hs)
	{
		ViewGroup parent = (ViewGroup)this.getParent();
		int pxUsed = 0;  // pixels used by child elements, excluding us
		int pxTotal = 0; // pixels we can actually use

		// Measure how much space we got and how much is already used
		// by other children
		if (parent != null) {
			// We are item 0, so we skip ourselfs
			for (int i=1; i<parent.getChildCount(); i++) {
				pxUsed += parent.getChildAt(i).getWidth();
			}
			View topParent = (View)parent.getParent();
			if (topParent != null) {
				pxTotal = topParent.getWidth();
			}
		}

		final float density = getResources().getDisplayMetrics().density;
		int widthMode = MeasureSpec.getMode(ws);

		super.onMeasure(ws, hs);

		if (widthMode != MeasureSpec.EXACTLY && pxTotal > 0) {
			int pxAvailable = (pxTotal - (int)(density * dpiElement * slackElements));
			int pxMaxWidth = (int)(dpiMaxWidth * density);

			if (pxAvailable <= 0 || pxAvailable > pxMaxWidth) {
				pxAvailable = pxMaxWidth;
			}
			if (android.os.Build.VERSION.SDK_INT < 21) {
				pxAvailable = (int)(dpiCompatMax * density);
			}
			setMeasuredDimension( pxAvailable, (int)(dpiElement * density));
		}

	}
}
