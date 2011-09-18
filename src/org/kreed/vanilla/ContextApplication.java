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

import java.util.ArrayList;
import java.util.Random;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.provider.MediaStore;

/**
 * Subclass of Application that provides various static utility functions
 */
public class ContextApplication extends Application {
	private static ContextApplication mInstance;
	public static ArrayList<Activity> mActivities;
	private static PlaybackService mService;
	private static Random mRandom;

	public ContextApplication()
	{
		mInstance = this;
	}

	@Override
	public void onCreate()
	{
		getContentResolver().registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mObserver);
	}

	private ContentObserver mObserver = new ContentObserver(null) {
		@Override
		public void onChange(boolean selfChange)
		{
			Song.onMediaChange();
			if (mService != null)
				mService.onMediaChange();
			ArrayList<Activity> list = mActivities;
			if (list != null) {
				for (int i = list.size(); --i != -1; ) {
					Activity activity = list.get(i);
					if (activity instanceof PlaybackActivity)
						((PlaybackActivity)activity).onMediaChange();
				}
			}
		}
	};

	/**
	 * Returns a shared, application-wide Random instance.
	 */
	public static Random getRandom()
	{
		if (mRandom == null)
			mRandom = new Random();
		return mRandom;
	}

	/**
	 * Provides an easy to access Context instance.
	 */
	public static Context getContext()
	{
		return mInstance;
	}

	/**
	 * Return the PlaybackService instance, creating one if needed.
	 */
	public static PlaybackService getService()
	{
		if (mService == null) {
			mInstance.startService(new Intent(mInstance, PlaybackService.class));
			while (mService == null) {
				try {
					synchronized (mInstance) {
						mInstance.wait();
					}
				} catch (InterruptedException e) {
				}
			}
		}

		return mService;
	}

	/**
	 * Returns whether a PlaybackService instance is active.
	 */
	public static boolean hasService()
	{
		return mService != null;
	}

	/**
	 * Set the PlaybackService instance to <code>service</code> and notify all
	 * clients waiting for an instance.
	 */
	public static void setService(PlaybackService service)
	{
		mService = service;
		synchronized (mInstance) {
			mInstance.notifyAll();
		}
	}

	/**
	 * Add an Activity to the list of Activities.
	 *
	 * @param activity The Activity to be added
	 */
	public static void addActivity(Activity activity)
	{
		if (mActivities == null)
			mActivities = new ArrayList<Activity>();
		mActivities.add(activity);
	}

	/**
	 * Remove an Activity from the list of Activities.
	 *
	 * @param activity The Activity to be removed
	 */
	public static void removeActivity(Activity activity)
	{
		if (mActivities != null)
			mActivities.remove(activity);
	}

	/**
	 * Send a broadcast to all PlaybackActivities that have been added with
	 * addActivity and then with Context.sendBroadcast.
	 *
	 * @param intent The intent to be sent as a broadcast
	 */
	public static void broadcast(Intent intent)
	{
		ArrayList<Activity> list = mActivities;
		if (list != null) {
			for (int i = list.size(); --i != -1; ) {
				Activity activity = list.get(i);
				if (activity instanceof PlaybackActivity)
					((PlaybackActivity)activity).receive(intent);
			}
		}

		if (mInstance != null)
			mInstance.sendBroadcast(intent);
	}
}
