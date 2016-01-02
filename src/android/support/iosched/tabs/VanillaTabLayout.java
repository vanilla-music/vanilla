/*
 * Copyright (C) 2015-2016 Adrian Ulrich <adrian@blinkenlights.ch>
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

package android.support.iosched.tabs;

import android.app.ActionBar;
import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import android.os.Build;

/**
 * Simple wrapper for SlidingTabLayout which takes
 * care of setting sane per-platform defaults
 */
public class VanillaTabLayout extends SlidingTabLayout {

	public VanillaTabLayout(Context context) {
		this(context, null);
	}

	public VanillaTabLayout(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public VanillaTabLayout(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		setCustomTabColorizer(new TabColorizer(context));
		setDistributeEvenly(true);
	}

	/**
	 * Overrides the default text color
	 */
	@Override
	protected TextView createDefaultTabView(Context context) {
		TextView view = super.createDefaultTabView(context);
		int color = getResources().getColor(android.R.color.primary_text_dark);
		view.setTextColor(color);
		view.setTextSize(14);
		return view;
	}

	/**
	 * Borrow elevation of given action bar
	 *
	 * @param ab The active action bar
	 */
	public void inheritElevation(ActionBar ab) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
			return; // noop on earlier releases

		float elevation = ab.getElevation();
		ab.setElevation(0.0f);
		setElevation(elevation);
	}


	private static class TabColorizer implements SlidingTabLayout.TabColorizer {
		private final int mTabIndicatorColor;
		TabColorizer(Context context) {
			mTabIndicatorColor = context.getResources().getColor(ch.blinkenlights.android.vanilla.R.color.tabs_active_indicator);
		}
		@Override
		public final int getIndicatorColor(int position) {
			return mTabIndicatorColor;
		}
	}


}
