/*
 * Copyright (C) 2019 Felix Nüsse <felix.nuesse@t-online.de>
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

package ch.blinkenlights.android.vanilla.theming;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;

import androidx.core.content.res.ResourcesCompat;
import ch.blinkenlights.android.vanilla.PrefDefaults;
import ch.blinkenlights.android.vanilla.PrefKeys;
import ch.blinkenlights.android.vanilla.R;
import ch.blinkenlights.android.vanilla.SharedPrefHelper;

public class FontTheming {

	public static void setFont(Activity activity) {
		SharedPreferences settings = SharedPrefHelper.getSettings(activity);
		boolean useDyslexia = settings.getBoolean(PrefKeys.DYSLEXIA_FONT, PrefDefaults.DYSLEXIA_FONT);
		if(useDyslexia){
			activity.setTheme(R.style.VanillaBase_FontDyslexia);
		}else{
			activity.setTheme(R.style.VanillaBase_FontDefault);
		}
	}

	public static Typeface getFontPath(Context c){
		SharedPreferences settings = SharedPrefHelper.getSettings(c);
		boolean useDyslexia = settings.getBoolean(PrefKeys.DYSLEXIA_FONT, PrefDefaults.DYSLEXIA_FONT);
		if(useDyslexia){
			return ResourcesCompat.getFont(c, R.font.opendyslexia);
		}else{
			return ResourcesCompat.getFont(c, R.font.google_sans);
		}

	}

}
