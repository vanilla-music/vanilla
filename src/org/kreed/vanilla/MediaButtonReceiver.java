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

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.view.KeyEvent;
import android.os.Handler;
import android.os.Message;

public class MediaButtonReceiver extends BroadcastReceiver {
	public static final String ENABLE = "org.kreed.vanilla.action.ENABLE_RECEIVER";

	private static final int MSG_MEDIA_CLICK = 2;
	private static final int DOUBLE_CLICK_DELAY = 300;

	private static boolean mEnabled = true;
	private static boolean mIgnoreNextUp;

	private static Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg)
		{
			switch (msg.what) {
			case MSG_MEDIA_CLICK:
				try {
					((PendingIntent)msg.obj).send();
				} catch (PendingIntent.CanceledException e) {
				}
				break;
			}
		}
	};

	private static Intent getCommand(Context context, String action)
	{
		return new Intent(context, PlaybackService.class).setAction(action);
	}

	@Override
	public void onReceive(Context context, Intent intent)
	{
		String intentAction = intent.getAction();

		if (mEnabled && Intent.ACTION_MEDIA_BUTTON.equals(intentAction)) {
			KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
			if (event == null)
				return;

			int action = event.getAction();

			switch (event.getKeyCode()) {
			case KeyEvent.KEYCODE_HEADSETHOOK:
			case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
				// single quick press: pause/resume. 
				// double press: next track
				// long press: unused (could also do next track? open player?)

				if (action == KeyEvent.ACTION_UP && mIgnoreNextUp) {
					mIgnoreNextUp = false;
					return;
				}

				if (mHandler.hasMessages(MSG_MEDIA_CLICK)) {
					// double press
					if (action == KeyEvent.ACTION_DOWN) {
						mHandler.removeMessages(MSG_MEDIA_CLICK);
						context.startService(getCommand(context, PlaybackService.NEXT_SONG));
						mIgnoreNextUp = true;
					}
				} else {
					// single press
					if (action == KeyEvent.ACTION_UP) {
						Intent command = getCommand(context, PlaybackService.TOGGLE_PLAYBACK);
						PendingIntent pendingIntent = PendingIntent.getService(context, 0, command, 0);
						Message message = mHandler.obtainMessage(MSG_MEDIA_CLICK, pendingIntent);
						mHandler.sendMessageDelayed(message, DOUBLE_CLICK_DELAY);
					}
				}
				break;
			case KeyEvent.KEYCODE_MEDIA_NEXT:
				if (action == KeyEvent.ACTION_DOWN)
					context.startService(getCommand(context, PlaybackService.NEXT_SONG));
				break;
			case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
				if (action == KeyEvent.ACTION_DOWN)
					context.startService(getCommand(context, PlaybackService.PREVIOUS_SONG));
				break;
			default:
				return;
			}

			abortBroadcast();
		} else if (ENABLE.equals(intentAction)) {
			// this approach does not seem elegant.
			mEnabled = intent.getBooleanExtra("enabled", true);
		}
	}
}
