/*
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
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AsyncPlayer;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.view.KeyEvent;

/**
 * Receives media button events and calls to PlaybackService to respond
 * appropriately.
 */
public class MediaButtonReceiver extends BroadcastReceiver {
		/**
	 * If another button event is received before this time in milliseconds
	 * expires, the event with be considered a double click.
	 */
	private static final int DOUBLE_CLICK_DELAY = 400;

	/**
	 * Whether the headset controls should be used. 1 for yes, 0 for no, -1 for
	 * uninitialized.
	 */
	private static int sUseControls = -1;
	/**
	 * Time of the last play/pause click. Used to detect double-clicks.
	 */
	private static long sLastClickTime = 0;
	/**
	 * Whether a beep should be played in response to double clicks be used.
	 * 1 for yes, 0 for no, -1 for uninitialized.
	 */
	private static int sBeep = -1;
	/**
	 * Lazy-loaded AsyncPlayer for beep sounds.
	 */
	private static AsyncPlayer sBeepPlayer;
	/**
	 * Lazy-loaded URI of the beep resource.
	 */
	private static Uri sBeepSound;

	/**
	 * Play a beep sound.
	 */
	private static void beep(Context context)
	{
		if (sBeep == -1) {
			SharedPreferences settings = PlaybackService.getSettings(context);
			sBeep = settings.getBoolean(PrefKeys.MEDIA_BUTTON_BEEP, true) ? 1 : 0;
		}

		if (sBeep == 1) {
			if (sBeepPlayer == null) {
				sBeepPlayer = new AsyncPlayer("BeepPlayer");
				sBeepSound = Uri.parse("android.resource://ch.blinkenlights.android.vanilla/raw/beep");
			}
			sBeepPlayer.play(context, sBeepSound, false, AudioManager.STREAM_MUSIC);
		}
	}

	/**
	 * Reload the preferences and enable/disable buttons as appropriate.
	 *
	 * @param context A context to use.
	 */
	public static void reloadPreference(Context context)
	{
		sUseControls = -1;
		sBeep = -1;
		if (useHeadsetControls(context)) {
			registerMediaButton(context);
		} else {
			unregisterMediaButton(context);
		}
	}

	/**
	 * Return whether headset controls should be used, loading the preference
	 * if necessary.
	 *
	 * @param context A context to use.
	 */
	public static boolean useHeadsetControls(Context context)
	{
		if (sUseControls == -1) {
			SharedPreferences settings = PlaybackService.getSettings(context);
			sUseControls = settings.getBoolean(PrefKeys.MEDIA_BUTTON, true) ? 1 : 0;
		}

		return sUseControls == 1;
	}

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
		if (event == null || !useHeadsetControls(context))
			return false;

		int action = event.getAction();
		String act = null;

		switch (event.getKeyCode()) {
		case KeyEvent.KEYCODE_HEADSETHOOK:
		case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
			// single click: pause/resume.
			// double click: next track

			if (action == KeyEvent.ACTION_DOWN) {
				long time = SystemClock.uptimeMillis();
				if (time - sLastClickTime < DOUBLE_CLICK_DELAY) {
					beep(context);
					act = PlaybackService.ACTION_NEXT_SONG_AUTOPLAY;
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
				act = PlaybackService.ACTION_REWIND_SONG;
			break;
		case KeyEvent.KEYCODE_MEDIA_PLAY:
			if (action == KeyEvent.ACTION_DOWN)
				act = PlaybackService.ACTION_PLAY;
			break;
		case KeyEvent.KEYCODE_MEDIA_PAUSE:
			if (action == KeyEvent.ACTION_DOWN)
				act = PlaybackService.ACTION_PAUSE;
			break;
		default:
			return false;
		}

		if (act != null) {
			Intent intent = new Intent(context, PlaybackService.class);
			intent.setAction(act);
			context.startService(intent);
		}

		return true;
	}

	/**
	 * Request focus on the media buttons from AudioManager if media buttons
	 * are enabled.
	 *
	 * @param context A context to use.
	 */
	public static void registerMediaButton(Context context)
	{
		if (!useHeadsetControls(context))
			return;

		AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
		ComponentName receiver = new ComponentName(context.getPackageName(), MediaButtonReceiver.class.getName());
		audioManager.registerMediaButtonEventReceiver(receiver);
	}

	/**
	 * Unregister the media buttons from AudioManager.
	 *
	 * @param context A context to use.
	 */
	public static void unregisterMediaButton(Context context)
	{
		AudioManager audioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
		ComponentName receiver = new ComponentName(context.getPackageName(), MediaButtonReceiver.class.getName());
		audioManager.unregisterMediaButtonEventReceiver(receiver);
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
