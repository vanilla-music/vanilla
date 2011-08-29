/*
 * Copyright (C) 2010, 2011 Christopher Eby <kreed@kreed.org>
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

package org.kreed.vanilla;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;

/**
 * Handle a provided MediaButton event and take the appropriate action in
 * PlaybackService.
 */
public class MediaButtonHandler implements Handler.Callback {
	/**
	 * If another button event is received before this time in milliseconds
	 * expires, the event with be considered a double click.
	 */
	private static final int DOUBLE_CLICK_DELAY = 400;

	/**
	 * The current global instance of this class.
	 */
	private static MediaButtonHandler mInstance;
	/**
	 * The Handler for delayed processing.
	 */
	private Handler mHandler;
	/**
	 * Whether the headset controls should be used. 1 for yes, 0 for no, -1 for
	 * uninitialized.
	 */
	private static int mUseControls = -1;
	/**
	 * Whether the phone is currently in a call. 1 for yes, 0 for no, -1 for
	 * uninitialized.
	 */
	private int mInCall = -1;

	private static AudioManager mAudioManager;
	private static Method mRegisterMediaButtonEventReceiver;
	private static Method mUnregisterMediaButtonEventReceiver;
	public static ComponentName mButtonReceiver;

	/**
	 * Retrieve the MediaButtonHandler singleton, creating it if necessary.
	 * Returns null if headset controls are not enabled.
	 */
	public static MediaButtonHandler getInstance()
	{
		if (useHeadsetControls()) {
			if (mInstance == null)
				mInstance = new MediaButtonHandler();
			return mInstance;
		}
		return null;
	}

	/**
	 * Construct a MediaButtonHandler.
	 */
	private MediaButtonHandler()
	{
		mHandler = new Handler(this);

		Context context = ContextApplication.getContext();

		mAudioManager = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
		mButtonReceiver = new ComponentName(context.getPackageName(), MediaButtonReceiver.class.getName());
		try {
			mRegisterMediaButtonEventReceiver = AudioManager.class.getMethod("registerMediaButtonEventReceiver", ComponentName.class);
			mUnregisterMediaButtonEventReceiver = AudioManager.class.getMethod("unregisterMediaButtonEventReceiver", ComponentName.class);
		} catch (NoSuchMethodException nsme) {
			// Older Android; just use receiver priority
		}
	}

	private static void loadPreference()
	{
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(ContextApplication.getContext());
		mUseControls = settings.getBoolean("media_button", true) ? 1 : 0;
	}

	public static void reloadPreference()
	{
		loadPreference();
		if (useHeadsetControls()) {
			getInstance().registerMediaButton();
		} else {
			unregisterMediaButton();
		}
	}

	/**
	 * Return whether headset controls should be used, loading the preference
	 * if necessary.
	 */
	public static boolean useHeadsetControls()
	{
		if (mUseControls == -1)
			loadPreference();
		return mUseControls == 1;
	}

	/**
	 * Return whether the phone is currently in a call.
	 */
	private boolean isInCall()
	{
		if (mInCall == -1) {
			Context context = ContextApplication.getContext();
			TelephonyManager manager = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);
			mInCall = (byte)(manager.getCallState() == TelephonyManager.CALL_STATE_IDLE ? 0 : 1);
		}
		return mInCall == 1;
	}

	/**
	 * Set the cached value for whether the phone is in a call.
	 *
	 * @param value True if in a call, false otherwise.
	 */
	public void setInCall(boolean value)
	{
		mInCall = value ? 1 : 0;
	}

	/**
	 * Send the given action to the playback service.
	 *
	 * @param action One of the PlaybackService.ACTION_* actions.
	 */
	private static void act(String action)
	{
		Context context = ContextApplication.getContext();
		Intent intent = new Intent(context, PlaybackService.class);
		intent.setAction(action);
		context.startService(intent);
	}

	/**
	 * Process a media button key press.
	 */
	public boolean processKey(KeyEvent event)
	{
		if (event == null || isInCall() || !useHeadsetControls())
			return false;

		int action = event.getAction();

		switch (event.getKeyCode()) {
		case KeyEvent.KEYCODE_HEADSETHOOK:
		case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
			// single click: pause/resume.
			// double click: next track

			if (action == KeyEvent.ACTION_DOWN) {
				if (mHandler.hasMessages(MSG_SINGLE_PRESS_TIMEOUT)) {
					// double click
					mHandler.removeMessages(MSG_SINGLE_PRESS_TIMEOUT);
					act(PlaybackService.ACTION_NEXT_SONG_AUTOPLAY);
				} else {
					mHandler.sendEmptyMessageDelayed(MSG_SINGLE_PRESS_TIMEOUT, DOUBLE_CLICK_DELAY);
				}
			}
			break;
		case KeyEvent.KEYCODE_MEDIA_NEXT:
			if (action == KeyEvent.ACTION_DOWN)
				act(PlaybackService.ACTION_NEXT_SONG_AUTOPLAY);
			break;
		case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
			if (action == KeyEvent.ACTION_DOWN)
				act(PlaybackService.ACTION_PREVIOUS_SONG_AUTOPLAY);
			break;
		default:
			return false;
		}

		return true;
	}

	/**
	 * Process a MediaButton broadcast.
	 *
	 * @param intent The intent that was broadcast
	 * @return True if the intent was handled and the broadcast should be
	 * aborted.
	 */
	public boolean process(Intent intent)
	{
		KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
		return processKey(event);
	}

	/**
	 * A delayed message that performs the single press action after the double
	 * click period has expired.
	 */
	private static final int MSG_SINGLE_PRESS_TIMEOUT = 0;

	public boolean handleMessage(Message message)
	{
		switch (message.what) {
		case MSG_SINGLE_PRESS_TIMEOUT:
			act(PlaybackService.ACTION_TOGGLE_PLAYBACK);
			break;
		default:
			return false;
		}

		return true;
	}

	/**
	 * Request focus on the media buttons from AudioManager.
	 */
	public void registerMediaButton()
	{
		assert(mUseControls == 1);
		if (mRegisterMediaButtonEventReceiver != null) {
			try {
				mRegisterMediaButtonEventReceiver.invoke(mAudioManager, mButtonReceiver);
			} catch (InvocationTargetException e) {
				Log.w("VanillaMusic", e);
			} catch (IllegalAccessException e) {
				Log.w("VanillaMusic", e);
			}
		}
	}

	/**
	 * Unregister the media buttons from AudioManager.
	 */
	public static void unregisterMediaButton()
	{
		if (mUnregisterMediaButtonEventReceiver != null) {
			try {
				mUnregisterMediaButtonEventReceiver.invoke(mAudioManager, mButtonReceiver);
			} catch (InvocationTargetException e) {
				Log.w("VanillaMusic", e);
			} catch (IllegalAccessException e) {
				Log.w("VanillaMusic", e);
			}
		}
	}
}
