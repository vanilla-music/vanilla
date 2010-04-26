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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;

/**
 * Very simple activity that simply launches the appropriate Activity based on
 * user preferences.
 */
public class LaunchActivity extends Activity {
	/**
	 * Launch either the PlaybackActivity or SongSelector, depending on user
	 * settings.
	 */
	@Override
	public void onCreate(Bundle state)
	{
		super.onCreate(state);

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		boolean selector = settings.getBoolean("selector_on_startup", false);
		startActivity(new Intent(this, selector ? SongSelector.class : FullPlaybackActivity.class));
		finish();
	}
}
