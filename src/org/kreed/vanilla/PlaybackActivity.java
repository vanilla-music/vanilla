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
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

public class PlaybackActivity extends Activity implements ServiceConnection, Handler.Callback {
	Handler mHandler = new Handler(this);

	CoverView mCoverView;
	IPlaybackService mService;
	int mState;

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
		filter.addAction(PlaybackService.EVENT_REPLACE_SONG);
		registerReceiver(mReceiver, filter);
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
			ContextApplication.quit(context);
			return true;
		}

		return false;
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event)
	{
		return handleKeyLongPress(this, keyCode);
	}

	public void onClick(View view)
	{
		try {
			switch (view.getId()) {
			case R.id.next:
				if (mCoverView != null)
					mCoverView.go(1);
				mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_SONG, 1, 0));
				break;
			case R.id.play_pause:
				mHandler.sendMessage(mHandler.obtainMessage(MSG_TOGGLE_FLAG, PlaybackService.FLAG_PLAYING, 0));
				break;
			case R.id.previous:
				if (mCoverView != null)
					mCoverView.go(-1);
				mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_SONG, -1, 0));
				break;
			}
		} catch (RemoteException e) {
			Log.e("VanillaMusic", "service dead", e);
			setService(null);
		}
	}

	/**
	 * Sets <code>mState</code> to <code>state</code>. Override to implement
	 * further behavior in subclasses.
	 *
	 * @param state PlaybackService state
	 */
	protected void setState(int state)
	{
		mState = state;
	}

	/**
	 * Sets up components when the PlaybackService is bound to. Override to
	 * implement further post-connection behavior.
	 *
	 * @param service PlaybackService interface
	 */
	protected void setService(IPlaybackService service)
	{
		mService = service;

		if (mCoverView != null)
			mCoverView.setPlaybackService(service);

		if (service != null) {
			try {
				setState(service.getState());
			} catch (RemoteException e) {
			}
		}
	}

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
		setState(intent.getIntExtra("state", 0));
	}

	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent)
		{
			if (mCoverView != null)
				mCoverView.onReceive(intent);
			if (PlaybackService.EVENT_CHANGED.equals(intent.getAction()))
				onServiceChange(intent);
		}
	};

	static final int MENU_QUIT = 0;
	static final int MENU_DISPLAY = 1;
	static final int MENU_PREFS = 2;
	static final int MENU_LIBRARY = 3;
	static final int MENU_SHUFFLE = 4;
	static final int MENU_PLAYBACK = 5;
	static final int MENU_REPEAT = 6;
	static final int MENU_SEARCH = 7;

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		menu.add(0, MENU_PREFS, 0, R.string.settings).setIcon(android.R.drawable.ic_menu_preferences);
		menu.add(0, MENU_SHUFFLE, 0, R.string.shuffle_enable).setIcon(R.drawable.ic_menu_shuffle);
		menu.add(0, MENU_REPEAT, 0, R.string.repeat_enable).setIcon(R.drawable.ic_menu_refresh);
		menu.add(0, MENU_QUIT, 0, R.string.quit).setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		boolean isShuffling = (mState & PlaybackService.FLAG_SHUFFLE) != 0;
		menu.findItem(MENU_SHUFFLE).setTitle(isShuffling ? R.string.shuffle_disable : R.string.shuffle_enable);
		boolean isRepeating = (mState & PlaybackService.FLAG_REPEAT) != 0;
		menu.findItem(MENU_REPEAT).setTitle(isRepeating ? R.string.repeat_disable : R.string.repeat_enable);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId()) {
		case MENU_QUIT:
			ContextApplication.quit(this);
			return true;
		case MENU_SHUFFLE:
			mHandler.sendMessage(mHandler.obtainMessage(MSG_TOGGLE_FLAG, PlaybackService.FLAG_SHUFFLE, 0));
			return true;
		case MENU_REPEAT:
			mHandler.sendMessage(mHandler.obtainMessage(MSG_TOGGLE_FLAG, PlaybackService.FLAG_REPEAT, 0));
			return true;
		case MENU_PREFS:
			startActivity(new Intent(this, PreferencesActivity.class));
			return true;
		default:
			return false;
		}
	}

	/**
	 * Tell PlaybackService to toggle a state flag (passed as arg1) and display
	 * a message notifying that the flag was toggled.
	 */
	static final int MSG_TOGGLE_FLAG = 0;
	/**
	 * Tell PlaybackService to move to a song a position delta (passed as arg1).
	 */
	static final int MSG_SET_SONG = 1;

	public boolean handleMessage(Message message)
	{
		switch (message.what) {
		case MSG_TOGGLE_FLAG:
			int flag = message.arg1;
			boolean enabling = (mState & flag) == 0;
			int text = -1;
			if (flag == PlaybackService.FLAG_SHUFFLE)
				text = enabling ? R.string.shuffle_enabling : R.string.shuffle_disabling;
			else if (flag == PlaybackService.FLAG_REPEAT)
				text = enabling ? R.string.repeat_enabling : R.string.repeat_disabling;

			if (text != -1)
				Toast.makeText(this, text, Toast.LENGTH_SHORT).show();

			try {
				mService.toggleFlag(flag);
			} catch (RemoteException e) {
				setService(null);
			}
			break;
		case MSG_SET_SONG:
			try {
				mService.setCurrentSong(message.arg1);
			} catch (RemoteException e) {
				setService(null);
			}
			break;
		default:
			return false;
		}

		return true;
	}
}