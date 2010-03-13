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
import android.view.KeyEvent;

public class PlaybackServiceActivity extends Activity {
	public static boolean handleKeyLongPress(Activity activity, int keyCode)
	{
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			activity.stopService(new Intent(activity, PlaybackService.class));
			activity.finish();
			return true;
		}

		return false;
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event)
	{
		return handleKeyLongPress(this, keyCode);
	}
}