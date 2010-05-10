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

import java.util.ArrayList;
import java.util.Random;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;

/**
 * Subclass of Application that provides various static utility functions
 */
public class ContextApplication extends Application {
	private static ContextApplication mInstance;
	private static ArrayList<Activity> mActivities;
	private static PlaybackService mService;
	private static Random mRandom;

	public ContextApplication()
	{
		mInstance = this;
	}

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
					mInstance.wait();
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
		if (list == null)
			return;

		for (int i = list.size(); --i != -1; ) {
			Activity activity = list.get(i);
			if (activity instanceof PlaybackActivity)
				((PlaybackActivity)activity).receive(intent);
		}

		if (mInstance != null)
			mInstance.sendBroadcast(intent);
	}

	/**
	 * Stop the PlaybackService, if running, and close all Activities that
	 * have been added with <code>addActivity</code>.
	 */
	public static void quit()
	{
		if (mActivities != null) {
			for (int i = mActivities.size(); --i != -1; )
				mActivities.remove(i).finish();
		}
		mInstance.stopService(new Intent(mInstance, PlaybackService.class));
	}
}
