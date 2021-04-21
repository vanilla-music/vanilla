/*
 * Copyright (C) 2016 Adrian Ulrich <adrian@blinkenlights.ch>
 * Copyright (C) 2012 Christopher Eby <kreed@kreed.org>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package ch.blinkenlights.android.vanilla;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.os.Handler;
import android.view.KeyEvent;

/**
 * Receives media button events and calls to PlaybackService to respond
 * appropriately.
 *
 * Most of this logic is only needed for RemoteControlImplICS (like double-click)
 * as >= LP devices handle this using the MediaSessionCompat.
 */
public class MediaButtonReceiver extends BroadcastReceiver {
	/**
	 * If another button event is received before this time in milliseconds
	 * expires, the event with be considered a double click.
	 */
	private static final int DOUBLE_CLICK_DELAY = 600;

	/**
	 * Time of the last play/pause click. Used to detect double-clicks.
	 */
	private static long sLastClickTime = 0;

	/**
	 * Process a media button key press.
	 *
	 * @param context A context to use.
	 * @param event The key press event.
	 * @return True if the event was handled and the broadcast should be
	 * aborted.
	 */
	public static boolean processKey(Context context, KeyEvent event)
	{
		if (event == null)
			return false;

		int action = event.getAction();
		String act = null;

		switch (event.getKeyCode()) {
		case KeyEvent.KEYCODE_HEADSETHOOK:
		case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
			// single click: pause/resume.
			// double click: next track
			// triple click: previous track
			if (action == KeyEvent.ACTION_DOWN) {
				long time = SystemClock.uptimeMillis();
				if (time - sLastClickTime < DOUBLE_CLICK_DELAY) {
					Handler handler = new Handler();
					DelayedClickCounter dcc = new DelayedClickCounter(context, time);
					handler.postDelayed(dcc, DOUBLE_CLICK_DELAY);
				} else {
					act = PlaybackService.ACTION_TOGGLE_PLAYBACK;
				}
				sLastClickTime = time;
			}
			break;
		case KeyEvent.KEYCODE_MEDIA_NEXT:
			if (action == KeyEvent.ACTION_DOWN)
				act = PlaybackService.ACTION_NEXT_SONG_AUTOPLAY;
			break;
		case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
			if (action == KeyEvent.ACTION_DOWN)
				act = PlaybackService.ACTION_PREVIOUS_SONG_AUTOPLAY;
			break;
		case KeyEvent.KEYCODE_MEDIA_PLAY:
			if (action == KeyEvent.ACTION_DOWN)
				act = PlaybackService.ACTION_PLAY;
			break;
		case KeyEvent.KEYCODE_MEDIA_PAUSE:
		case KeyEvent.KEYCODE_MEDIA_STOP:
			// We will behave the same as Google Play Music: for "Stop" we unconditionally Pause instead
			if (action == KeyEvent.ACTION_DOWN)
				act = PlaybackService.ACTION_PAUSE;
			break;
		default:
			return false;
		}

		runAction(context, act);
		return true;
	}


	/**
	 * Passes an action to PlaybackService
	 *
	 * @param context the context to use
	 * @param act the action to pass on
	 */
	private static void runAction(Context context, String act) {
		if (act == null)
			return;

		Intent intent = new Intent(context, PlaybackService.class)
			.setAction(act)
			.putExtra(PlaybackService.EXTRA_EARLY_NOTIFICATION, true);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			context.startForegroundService(intent);
		} else {
			context.startService(intent);
		}
	}


	/**
	 * Runable to run a delayed action
	 * Inspects sLastClickTime and sDelayedClicks to guess what to do
	 * Note: this is only used on pre-lolipop devices.
	 *
	 * @param context the context to use
	 * @param serial the value of sLastClickTime during creation, used to identify stale events
	 */
	private static class DelayedClickCounter implements Runnable {
		private Context mContext;
		private long mSerial;
		private static int sDelayedClicks;

		public DelayedClickCounter(Context context, long serial) {
			mContext = context;
			mSerial = serial;
		}

		@Override
		public void run() {
			sDelayedClicks++;
			if (mSerial != sLastClickTime)
				return; // just count the click, don't fire.

			String act = null;
			switch (sDelayedClicks) {
				case 1:
					act = PlaybackService.ACTION_NEXT_SONG_AUTOPLAY;
					break;
				default:
					act = PlaybackService.ACTION_PREVIOUS_SONG_AUTOPLAY;
			}
			sDelayedClicks = 0;
			runAction(mContext, act);
		}
	}



	@Override
	public void onReceive(Context context, Intent intent)
	{
		if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())) {
			KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
			boolean handled = processKey(context, event);
			if (handled && isOrderedBroadcast())
				abortBroadcast();
		}
	}
}
