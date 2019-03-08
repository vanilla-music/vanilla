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
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;

import ch.blinkenlights.android.vanilla.theming.ColorPickerDialog;


public class PreferencesTheme extends PreferenceFragment
 implements Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener {
	private Context mContext;
	public Preference colorPref;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mContext = getActivity();

		// Themes are 'pre-compiled' in themes-list: get all values
		// and append them to our newly created PreferenceScreen
		PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(mContext);
		final String[] entries = getResources().getStringArray(R.array.theme_entries);
		final String[] values = getResources().getStringArray(R.array.theme_values);
		final String[] ids = getResources().getStringArray(R.array.theme_ids);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {

			screen.addPreference(getDynamicThemingPreference());
			colorPref = new Preference(mContext);
			colorPref.setPersistent(false);
			colorPref.setOnPreferenceClickListener(this);
			colorPref.setTitle(getString(R.string.color_picker_preference_title));
			colorPref.setKey("ColorPicker"); // preference value of this theme
			colorPref.setIcon(generateCustomColorPreview());
			screen.addPreference(colorPref);
		}

		for (int i = 0; i < entries.length; i++) {

			//Only take the background color, because that is the main theme color
			int[] attrs = decodeValue(values[i]);
			int[] finalColorValue={attrs[1]};

			final Preference pref = new Preference(mContext);
			pref.setPersistent(false);
			pref.setOnPreferenceClickListener(this);
			pref.setTitle(entries[i]);
			pref.setKey(ids[i]); // preference value of this theme
			pref.setIcon(generateThemePreview(finalColorValue));
			screen.addPreference(pref);
		}
		setPreferenceScreen(screen);
	}

	@Override
	public boolean onPreferenceClick(Preference pref) {

		if(pref.getKey().equals("ColorPicker")){

			int[] colors = getResources().getIntArray(R.array.colorpicker_defaults);
			ColorPickerDialog cpd = new ColorPickerDialog(this.getActivity(), colors);
			cpd.setOnDismissListener(new DialogInterface.OnDismissListener() {
				@Override
				public void onDismiss(DialogInterface dialog) {
					ThemeHelper.setAccentColor(getActivity());
					colorPref.setIcon(generateCustomColorPreview());
				}
			});
			cpd.show();
			cpd.setColorChangedCallback(this);
			return true;
		}
		SharedPreferences.Editor editor = SharedPrefHelper.getSettings(mContext).edit();
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
		final int step = size / colors.length;
		final int border = 2;

		Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
		Canvas canvas = new Canvas(bitmap);
		Paint paint = new Paint();

		paint.setStyle(Paint.Style.FILL);
		for (int i=0; i < colors.length; i++) {
			paint.setColor(colors[i]);
			canvas.drawRect(border, (step*i) + border, size - border, size - border, paint);
		}

		Drawable d = new BitmapDrawable(mContext.getResources(), bitmap);
		return d;
	}

	public Drawable generateCustomColorPreview(){
		SharedPreferences settings = SharedPrefHelper.getSettings(mContext);
		int color= Color.parseColor(settings.getString(PrefKeys.COLOR_APP_ACCENT, PrefDefaults.COLOR_APP_ACCENT));

		final int size = (int) getResources().getDimension(R.dimen.cover_size);
		final int half = size / 2;
		final int radius = (int) ((size / 2)*0.9);



		Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_4444);
		bitmap.setHasAlpha(true);

		Canvas canvas = new Canvas(bitmap);

		Paint paint = new Paint();

		paint.setStyle(Paint.Style.FILL);
		paint.setColor(color);
		canvas.drawCircle(half, half, radius, paint);

		return new BitmapDrawable(mContext.getResources(), bitmap);
	}

	private CheckBoxPreference getDynamicThemingPreference(){

		SharedPreferences settings = SharedPrefHelper.getSettings(mContext);
		boolean useDynamicTheming = settings.getBoolean(PrefKeys.USE_DYNAMIC_THEME_COLOR,PrefDefaults.USE_DYNAMIC_THEME_COLOR);


		CheckBoxPreference dynamicThemePref = new CheckBoxPreference (mContext);
		dynamicThemePref.setChecked(useDynamicTheming);
		dynamicThemePref.setPersistent(false);
		dynamicThemePref.setOnPreferenceChangeListener(this);
		dynamicThemePref.setTitle(R.string.dynamic_theming_pref);
		dynamicThemePref.setKey("dynamicTheme"); // preference value of this theme
		return dynamicThemePref;
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {

		if(preference.getKey().equals("dynamicTheme")){
			SharedPreferences.Editor editor = SharedPrefHelper.getSettings(mContext).edit();
			editor.putBoolean(PrefKeys.USE_DYNAMIC_THEME_COLOR, (boolean)newValue);
			editor.apply();
			ThemeHelper.setAccentColor(this.getActivity());
		}
		return true;
	}


}
