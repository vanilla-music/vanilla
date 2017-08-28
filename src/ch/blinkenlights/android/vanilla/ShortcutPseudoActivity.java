/*
 * Copyright (C) 2017 Adrian Ulrich <adrian@blinkenlights.ch>
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


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;


public class ShortcutPseudoActivity extends Activity {

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final String action = getIntent().getAction();
		switch (action) {
			case PlaybackService.ACTION_TOGGLE_PLAYBACK:
			case PlaybackService.ACTION_TOGGLE_PLAYBACK_DELAYED:
			case PlaybackService.ACTION_RANDOM_MIX_AUTOPLAY:
			case PlaybackService.ACTION_NEXT_SONG:
			case PlaybackService.ACTION_NEXT_SONG_DELAYED:
			case PlaybackService.ACTION_PREVIOUS_SONG:
			case PlaybackService.ACTION_CYCLE_SHUFFLE:
			case PlaybackService.ACTION_CYCLE_REPEAT:
				Intent intent = new Intent(this, PlaybackService.class);
				intent.setAction(action);
				startService(intent);
				break;
			default:
				throw new IllegalArgumentException("No such action: " + action);
		}

		finish();
	}
}
