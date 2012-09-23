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

package ch.blinkenlights.android.vanilla;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/**
 * LinearLayout that contains some hacks for sizing inside an ActionBar.
 */
public class ActionBarControls extends LinearLayout {
	public ActionBarControls(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}

	@Override
	public void onMeasure(int ws, int hs)
	{
		super.onMeasure(ws, hs);

		float density = getResources().getDisplayMetrics().density;

		int width = MeasureSpec.getSize(ws);
		int widthMode = MeasureSpec.getMode(ws);
		if (widthMode != MeasureSpec.EXACTLY)
			width = (int)(200 * density);

		setMeasuredDimension(width, (int)(40 * density));

		ViewGroup.LayoutParams lp = getLayoutParams();
		try {
			lp.getClass().getField("expandable").set(lp, true);
		} catch (Exception e) {
			Log.d("VanillaMusic", "Failed to set controls expandable", e);
		}
	}
}
