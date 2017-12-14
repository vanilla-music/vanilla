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
import android.app.SearchManager;
import android.os.Bundle;
import android.content.Intent;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;

import android.util.Log;

public class AudioSearchActivity extends PlaybackActivity {

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		Intent intent = getIntent();
		String action = (intent == null ? null : intent.getAction());
		if (action == null || !action.equals(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)) {
			finish();
			return;
		}

		if (PermissionRequestActivity.requestPermissions(this, intent)) {
			finish();
			return;
		}

		// Looks like a search query: grab search string.
		String query = intent.getExtras().getString(SearchManager.QUERY);

		Log.v("VanillaMusic", "QQ: "+query);
		Log.v("VanillaMusic", "YY: "+intent.getExtras().getString(SearchManager.USER_QUERY));
		Log.v("VanillaMusic", "YY: "+intent.getExtras().getString(SearchManager.SUGGEST_COLUMN_AUDIO_CHANNEL_CONFIG));
		Log.v("VanillaMusic", "YY: "+intent.getExtras().getString(SearchManager.APP_DATA));
		Log.v("VanillaMusic", "YY: "+intent.getExtras().getString(SearchManager.ACTION_MSG));
		Log.v("VanillaMusic", "YY: "+intent.getExtras().getString(SearchManager.ACTION_MSG));
		Log.v("VanillaMusic", "YY: "+intent.getExtras().getString(SearchManager.SUGGEST_COLUMN_TEXT_1));
		// Basic sanity test done: Setup window
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.audiopicker);
	}
}
