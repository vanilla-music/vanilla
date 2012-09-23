/*
 * Copyright (C) 2011 Christopher Eby <kreed@kreed.org>
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

import android.annotation.TargetApi;
import android.app.backup.BackupManager;
import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;

/**
 * Framework methods only in Froyo or above go here.
 */
@TargetApi(8)
public class CompatFroyo implements AudioManager.OnAudioFocusChangeListener {
	/**
	 * Instance of the audio focus listener created by {@link #createAudioFocus()}.
	 */
	private static CompatFroyo sAudioFocus;

	/**
	 * Calls {@link AudioManager#registerMediaButtonEventReceiver(ComponentName)}.
	 */
	public static void registerMediaButtonEventReceiver(AudioManager manager, ComponentName receiver)
	{
		manager.registerMediaButtonEventReceiver(receiver);
	}

	/**
	 * Calls {@link AudioManager#unregisterMediaButtonEventReceiver(ComponentName)}.
	 */
	public static void unregisterMediaButtonEventReceiver(AudioManager manager, ComponentName receiver)
	{
		manager.unregisterMediaButtonEventReceiver(receiver);
	}

	/**
	 * Calls {@link BackupManager#dataChanged()}.
	 */
	public static void dataChanged(Context context)
	{
		new BackupManager(context).dataChanged();
	}

	/**
	 * Creates an audio focus listener that calls back to {@link PlaybackService#onAudioFocusChange(int)}.
	 */
	public static void createAudioFocus()
	{
		sAudioFocus = new CompatFroyo();
	}

	/**
	 * Calls {@link AudioManager#requestAudioFocus(AudioManager.OnAudioFocusChangeListener, int, int)}
	 */
	public static void requestAudioFocus(AudioManager manager)
	{
		manager.requestAudioFocus(sAudioFocus, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
	}

	@Override
	public void onAudioFocusChange(int type)
	{
		PlaybackService service = PlaybackService.sInstance;
		if (service != null) {
			service.onAudioFocusChange(type);
		}
	}

	/**
	 * Calls {@link VelocityTracker#getYVelocity(int)}.
	 */
	public static float getYVelocity(VelocityTracker tracker, int id)
	{
		return tracker.getYVelocity(id);
	}

	/**
	 * Calls {@link VelocityTracker#getXVelocity(int)}.
	 */
	public static float getXVelocity(VelocityTracker tracker, int id)
	{
		return tracker.getXVelocity(id);
	}

	/**
	 * Calls {@link ViewConfiguration#getScaledPagingTouchSlop()}.
	 */
	public static int getScaledPagingTouchSlop(ViewConfiguration config)
	{
		return config.getScaledPagingTouchSlop();
	}
}
