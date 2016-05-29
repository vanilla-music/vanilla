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
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.util.Log;


public class PreferencesTheme extends PreferenceFragment
 implements Preference.OnPreferenceClickListener
{
	private Context mContext;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mContext = getActivity();

		// Themes are 'pre-compiled' in themes-list: get all values
		// and append them to our newly created PreferenceScreen
		PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(mContext);
		final String[] entries = getResources().getStringArray(R.array.theme_entries);
		final String[] values = getResources().getStringArray(R.array.theme_values);
		for (int i = 0; i < entries.length; i++) {

			int[] attrs = decodeValue(values[i]);

			final Preference pref = new Preference(mContext);
			pref.setPersistent(false);
			pref.setOnPreferenceClickListener(this);
			pref.setTitle(entries[i]);
			pref.setKey(""+attrs[0]); // that's actually our value
			pref.setIcon(generateThemePreview(attrs));
			screen.addPreference(pref);
		}
		setPreferenceScreen(screen);
	}

	@Override
	public boolean onPreferenceClick(Preference pref) {
		SharedPreferences.Editor editor = PlaybackService.getSettings(mContext).edit();
		editor.putString(PrefKeys.SELECTED_THEME, pref.getKey());
		editor.apply();
		return true;
	}


	private int[] decodeValue(String v) {
		String[] parts = v.split(",");
		int[] values = new int[parts.length];
		for (int i=0; i<parts.length; i++) {
			long parsedLong = (long)Long.decode(parts[i]); // the colors overflow an int, so we first must parse it as Long to make java happy.
			values[i] = (int)parsedLong;
		}
		return values;
	}

	private Drawable generateThemePreview(int[] colors) {
		final int size = (int) getResources().getDimension(R.dimen.cover_size);
		final int step = size / (colors.length - 1);

		Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
		Canvas canvas = new Canvas(bitmap);
		Paint paint = new Paint();

		paint.setStyle(Paint.Style.FILL);
		for (int i=1; i < colors.length; i++) {
			paint.setColor(colors[i]);
			canvas.drawRect(0, step*(i-1), size, size, paint);
		}

		Drawable d = new BitmapDrawable(mContext.getResources(), bitmap);
		return d;
	}





}
