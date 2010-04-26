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