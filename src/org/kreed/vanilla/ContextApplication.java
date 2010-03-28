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

public class ContextApplication extends Application {
	private static ContextApplication mInstance;
	private static ArrayList<Activity> mActivities;
	private static Random mRandom;

	public ContextApplication()
	{
		mInstance = this;
	}

	public static Random getRandom()
	{
		if (mRandom == null)
			mRandom = new Random();
		return mRandom;
	}

	public static Context getContext()
	{
		return mInstance;
	}

	public static void addActivity(Activity activity)
	{
		if (mActivities == null)
			mActivities = new ArrayList<Activity>();
		mActivities.add(activity);
	}

	public static void removeActivity(Activity activity)
	{
		if (mActivities != null)
			mActivities.remove(activity);
	}

	public static void quit(Context context)
	{
		context.stopService(new Intent(context, PlaybackService.class));
		if (mActivities != null) {
			for (int i = mActivities.size(); --i != -1; )
				mActivities.remove(i).finish();
		}
	}
}