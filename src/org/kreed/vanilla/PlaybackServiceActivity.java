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
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.view.KeyEvent;

public abstract class PlaybackServiceActivity extends Activity implements ServiceConnection {
	protected CoverView mCoverView;

	@Override
	public void onCreate(Bundle state)
	{
		super.onCreate(state);
		ContextApplication.addActivity(this);
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		ContextApplication.removeActivity(this);
	}

	@Override
	public void onStart()
	{
		super.onStart();

		Intent intent = new Intent(this, PlaybackService.class);
		startService(intent);
		bindService(intent, this, Context.BIND_AUTO_CREATE);

		IntentFilter filter = new IntentFilter();
		filter.addAction(PlaybackService.EVENT_CHANGED);
		filter.addAction(PlaybackService.EVENT_LOADED);
		registerReceiver(mReceiver, filter);

		setState(PlaybackService.STATE_NORMAL);
	}

	@Override
	public void onStop()
	{
		super.onStop();

		try {
			unbindService(this);
		} catch (IllegalArgumentException e) {
			// we have not registered the service yet
		}
		try {
			unregisterReceiver(mReceiver);
		} catch (IllegalArgumentException e) {
			// we haven't registered the receiver yet
		}
	}

	public static boolean handleKeyLongPress(Context context, int keyCode)
	{
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			quit(context);
			return true;
		}

		return false;
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event)
	{
		return handleKeyLongPress(this, keyCode);
	}

	protected static void quit(Context context)
	{
		context.stopService(new Intent(context, PlaybackService.class));
		ContextApplication.finishAllActivities();
	}

	protected abstract void setState(int state);
	protected abstract void setService(IPlaybackService service);

	public void onServiceConnected(ComponentName name, IBinder service)
	{
		setService(IPlaybackService.Stub.asInterface(service));
	}

	public void onServiceDisconnected(ComponentName name)
	{
		setService(null);
	}

	protected void onServiceChange(Intent intent)
	{
		int newState = intent.getIntExtra("newState", 0);
		if (intent.getIntExtra("oldState", 0) != newState)
			setState(newState);
	}

	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent)
		{
			mCoverView.onReceive(intent);
			if (PlaybackService.EVENT_CHANGED.equals(intent.getAction()))
				onServiceChange(intent);
		}
	};
}