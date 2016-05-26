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
			final Preference pref = new Preference(mContext);
			pref.setPersistent(false);
			pref.setOnPreferenceClickListener(this);
			pref.setTitle(entries[i]);
			pref.setKey(values[i]); // that's actually our value
			pref.setIcon(R.drawable.icon);
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
}
