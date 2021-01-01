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
	final static int INTENT_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME;

	/**
	 * Returns an intent pointing to this activity.
	 *
	 * @param context the context to use.
	 * @param action the action to set.
	 * @return an intent for this activity.
	 */
	public static Intent getIntent(Context context, String action) {
		Intent intent = new Intent(context, ShortcutPseudoActivity.class)
			.setFlags(INTENT_FLAGS)
			.setAction(action);
		return intent;
	}

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final String action = getIntent().getAction();
		switch (action) {
			case PlaybackService.ACTION_PLAY:
			case PlaybackService.ACTION_PAUSE:
			case PlaybackService.ACTION_TOGGLE_PLAYBACK:
			case PlaybackService.ACTION_TOGGLE_PLAYBACK_DELAYED:
			case PlaybackService.ACTION_RANDOM_MIX_AUTOPLAY:
			case PlaybackService.ACTION_NEXT_SONG:
			case PlaybackService.ACTION_NEXT_SONG_DELAYED:
			case PlaybackService.ACTION_NEXT_SONG_AUTOPLAY:
			case PlaybackService.ACTION_PREVIOUS_SONG:
			case PlaybackService.ACTION_PREVIOUS_SONG_AUTOPLAY:
			case PlaybackService.ACTION_CYCLE_SHUFFLE:
			case PlaybackService.ACTION_CYCLE_REPEAT: {
				Intent intent = new Intent(this, PlaybackService.class);
				intent.setAction(action);
				startService(intent);
				break;
			}
			case PlaybackService.ACTION_FROM_TYPE_ID_AUTOPLAY: {
				// From pinned shortcuts: Same as other actions, but this
				// includes some extras.
				Intent intent = new Intent(this, PlaybackService.class);
				intent.setAction(action);
				intent.putExtra(LibraryAdapter.DATA_TYPE, getIntent().getIntExtra(LibraryAdapter.DATA_TYPE, MediaUtils.TYPE_INVALID));
				intent.putExtra(LibraryAdapter.DATA_ID, getIntent().getLongExtra(LibraryAdapter.DATA_ID, LibraryAdapter.INVALID_ID));
				startService(intent);
				break;
			}
			default:
				throw new IllegalArgumentException("No such action: " + action);
		}

		finish();
	}
}
