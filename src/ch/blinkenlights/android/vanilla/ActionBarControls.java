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
import android.widget.LinearLayout;
import android.os.Build;
import android.util.DisplayMetrics;

/**
 * LinearLayout that contains some hacks for sizing inside an ActionBar.
 */
public class ActionBarControls extends LinearLayout {

	private final int dpiElementLp    = 52;  // Size of the ActionBarSearch icon in 5.x (50 + some slack)
	private final int dpiElementHolo  = 64;  // Size of the ActionBarSearch icon in HOLO
	private final int dpiMaxWidth     = 350; // Never use more then 350 DPIs
	private final int visibleElements = 2;   // The ActionBarSearch + Menu icons are visible

	public ActionBarControls(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public void onMeasure(int ws, int hs) {
		super.onMeasure(ws, hs);

		final float density = getResources().getDisplayMetrics().density;
		final int dpiElement = ( android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ? dpiElementLp : dpiElementHolo );
		int widthMode = MeasureSpec.getMode(ws);

		if (widthMode != MeasureSpec.EXACTLY) {
			float dpiAvailable = (getSmallestAxisPx() / density) - (dpiElement * visibleElements);
			if (dpiAvailable > dpiMaxWidth || dpiAvailable < 1) {
				dpiAvailable = dpiMaxWidth;
			}
			setMeasuredDimension((int)(dpiAvailable * density), (int)(dpiElement * density));
		}
	}

	/**
	 * Returns the smaller axis of the display dimensions
	 * @return The dimension of the smaller axis in pixels
	 */
	private final int getSmallestAxisPx() {
		DisplayMetrics metrics = getResources().getDisplayMetrics();
		return (metrics.widthPixels > metrics.heightPixels ? metrics.heightPixels : metrics.widthPixels);
	}

}
