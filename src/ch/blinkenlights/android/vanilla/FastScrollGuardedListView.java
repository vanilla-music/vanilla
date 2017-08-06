/*
 * Copyright (C) 2016 Adrian Ulrich <adrian@blinkenlights.ch>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package ch.blinkenlights.android.vanilla;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ListView;


public class FastScrollGuardedListView extends ListView {

	/**
	 * Start edgeProtection at width-start
	 */
	private static final int PROTECT_START_DP = 50; // AOSP has this set to 48dip in 5.x
	/**
	 * End protection at width-end
	 */
	private static final int PROTECT_END_DP = 12;
	/**
	 * The calculated start position in pixel
	 */
	private float mEdgeProtectStartPx = 0;
	/**
	 * The calculated end position in pixel
	 */
	private float mEdgeProtectEndPx = 0;


	public FastScrollGuardedListView(Context context) {
		super(context);
	}
	public FastScrollGuardedListView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}
	public FastScrollGuardedListView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}
	public FastScrollGuardedListView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}

	/**
	 * Intercepted touch event from ListView
	 * We will use this callback to send fake X-coord events if
	 * the actual event happened in the protected area (eg: the hardcoded fastscroll area)
	 */
	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev) {
		if (mEdgeProtectStartPx == 0)
			mEdgeProtectStartPx = getWidth() - PROTECT_START_DP * Resources.getSystem().getDisplayMetrics().density;
		if (mEdgeProtectEndPx == 0)
			mEdgeProtectEndPx = getWidth() - PROTECT_END_DP * Resources.getSystem().getDisplayMetrics().density;

		if (ev.getX() > mEdgeProtectStartPx && ev.getX() < mEdgeProtectEndPx) {
			// Cursor is in protected area: simulate an event with a faked x coordinate
			ev = MotionEvent.obtain(ev.getDownTime(), ev.getEventTime(), ev.getAction(), mEdgeProtectStartPx, ev.getY(), ev.getMetaState());
		}
		return super.onInterceptTouchEvent(ev);
	}

}
