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

package ch.blinkenlights.android.vanilla;

import android.content.Context;
import android.content.res.TypedArray;
import android.content.SharedPreferences;
import android.os.Build;

public class ThemeHelper {

	/**
	 * Calls context.setTheme() with given theme.
	 * Will automatically swap the theme with an alternative
	 * version if the user requested us to use it
	 */
	final public static void setTheme(Context context, int theme) {
		context.setTheme(getThemeResource(context, theme));
	}

	/**
	 * Returns the theme resource id to use based on the user preferences
	 * and platform API.
	 */
	final public static int getThemeResource(Context context, int theme) {
		if(usesHoloTheme() == false) {
			TypedArray ar = null;

			switch (theme) {
			case R.style.Playback:
				ar = context.getResources().obtainTypedArray(R.array.theme_category_playback);
				break;
			case R.style.Library:
				ar = context.getResources().obtainTypedArray(R.array.theme_category_library);
				break;
			case R.style.BackActionBar:
				ar = context.getResources().obtainTypedArray(R.array.theme_category_backactionbar);
				break;
			case R.style.PopupDialog:
				ar = context.getResources().obtainTypedArray(R.array.theme_category_popupdialog);
				break;
			case R.style.BottomSheetDialog:
				ar = context.getResources().obtainTypedArray(R.array.theme_category_bottomsheetdialog);
				break;
			default:
				throw new IllegalArgumentException("setTheme() called with unknown theme!");
			}
			theme = ar.getResourceId(getSelectedThemeIndex(context), -1);
			ar.recycle();
		}
		return theme;
	}

	/**
	 * Helper function to get the correct play button drawable for our
	 * notification: The notification does not depend on the theme but
	 * depends on the API level
	 */
	final public static int getPlayButtonResource(boolean playing)
	{
		int playButton = 0;
		if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			// Android >= 5.0 uses the dark version of this drawable
			playButton = playing ? R.drawable.widget_pause : R.drawable.widget_play;
		} else {
			playButton = playing ? R.drawable.pause : R.drawable.play;
		}
		return playButton;
	}

	/**
	 * Returns TRUE if we should use the dark material theme,
	 * Returns FALSE otherwise - always returns FALSE on pre-5.x devices
	 */
	final private static boolean usesDarkTheme(Context context)
	{
		boolean useDark = false;
		if(usesHoloTheme() == false) {
			final int idx = getSelectedThemeIndex(context);
			final String[] variants = context.getResources().getStringArray(R.array.theme_variant);
			useDark = variants[idx].equals("dark");
		}
		return useDark;
	}

	/**
	 * Returns the user-selected theme index from the shared peferences provider
	 *
	 * @param context the context to use
	 * @return integer of the selected theme
	 */
	final private static int getSelectedThemeIndex(Context context) {
		SharedPreferences settings = SharedPrefHelper.getSettings(context);
		String prefValue = settings.getString(PrefKeys.SELECTED_THEME, PrefDefaults.SELECTED_THEME);

		final String[] ids = context.getResources().getStringArray(R.array.theme_ids);
		for (int i = 0; i < ids.length; i++) {
			if (ids[i].equals(prefValue))
				return i;
		}
		// no theme found? return default theme.
		return 0;
	}

	/**
	 * Returns TRUE if this device uses the HOLO (android 4) theme
	 */
	final public static boolean usesHoloTheme() {
		return (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP);
	}

	/**
	 * Fetches a color resource from the current theme
	 */
	final public static int fetchThemeColor(Context context, int resId) {
		TypedArray a = context.obtainStyledAttributes(new int[] { resId });
		int color = a.getColor(0, 0);
		a.recycle();
		return color;
	}

	/**
	 * Returns the color to be used to draw the placeholder cover.
	 */
	final public static int[] getDefaultCoverColors(Context context) {
		if (usesHoloTheme()) // pre material device
			return new int[] { 0xff000000, 0xff404040 };

		int bg = fetchThemeColor(context, android.R.attr.colorBackground);
		int diff = 0x00171717 * (bg > 0xFF888888 ? -1 : 1);
		return new int[]{ bg, bg+diff };
	}

}
