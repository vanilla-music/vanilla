/*
 * Copyright (C) 2015 Adrian Ulrich <adrian@blinkenlights.ch>
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
import android.content.SharedPreferences;
import android.os.Build;

public class ThemeHelper {

	/**
	 * Calls context.setTheme() with given theme.
	 * Will automatically swap the theme with an alternative
	 * version if the user requested us to use it
	 */
	final public static int setTheme(Context context, int theme)
	{
		if (usesDarkTheme(context)) {
			switch (theme) {
				case R.style.Playback:
					theme = R.style.Dark_Playback;
					break;
				case R.style.Library:
					theme = R.style.Dark_Library;
					break;
				case R.style.BackActionBar:
					theme = R.style.Dark_BackActionBar;
					break;
				default:
					break;
			}
		}
		context.setTheme(theme);
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
	 * Configures (or unconfigures) the use of the black theme
	 */
	final public static void setDarkTheme(boolean enable)
	{
	}

	/**
	 * Returns TRUE if we should use the dark material theme,
	 * Returns FALSE otherwise - always returns FALSE on pre-5.x devices
	 */
	final private static boolean usesDarkTheme(Context context)
	{
		boolean useDark = false;
		if(couldUseDarkTheme()) {
			SharedPreferences settings = PlaybackService.getSettings(context);
			useDark = settings.getBoolean(PrefKeys.USE_DARK_THEME, false);
		}
		return useDark;
	}

	/**
	 * Returns TRUE if this device may use the dark theme
	 * (eg: running api v21 or later)
	 */
	final public static boolean couldUseDarkTheme() {
		return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
	}

	/**
	 * Hacky function to get the colors needed to draw the default cover
	 * These colors should actually be attributes, but getting them programatically
	 * is a big mess
	 */
	final public static int[] getDefaultCoverColors(Context context) {
		int[] colors_holo_yolo      = { 0xff000000, 0xff606060, 0xff404040, 0x88000000 };
		int[] colors_material_light = { 0xffeeeeee, 0xffd6d7d7, 0xffd6d7d7, 0x55ffffff };
		int[] colors_material_dark  = { 0xff303030, 0xff606060, 0xff404040, 0x33ffffff };
		if (couldUseDarkTheme() == false)
			return colors_holo_yolo;
		if (usesDarkTheme(context))
			return colors_material_dark;
		// else
		return colors_material_light;
	}

}
