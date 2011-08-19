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
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.widget.ImageView;

/**
 * ControlButton is a simple extension of ImageView to make it clickable and
 * focusable and provide a visual indication of when these states occur by
 * tinting the image green.
 */
public class ControlButton extends ImageView {
	private static final int ACTIVE_TINT = Color.argb(100, 0, 255, 0);
	private static final int INACTIVE_TINT = Color.TRANSPARENT;
	private int mTint = Color.TRANSPARENT;

	/**
	 * Constructor intended to be called by inflating from XML.
	 */
	public ControlButton(Context context, AttributeSet attrs)
	{
		super(context, attrs);

		setFocusable(true);
		setClickable(true);
	}

	/**
	 * Change the tint of the view when the state changes to pressed or
	 * focused.
	 */
	@Override
	protected void drawableStateChanged()
	{
		super.drawableStateChanged();

		int tint = isPressed() || isFocused() ? ACTIVE_TINT : INACTIVE_TINT;  
		if (tint != mTint) {
			setColorFilter(tint, PorterDuff.Mode.SRC_ATOP);
			mTint = tint;
		}
	}
}