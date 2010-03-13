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

import org.kreed.vanilla.IPlaybackService;
import org.kreed.vanilla.R;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;

public class RemoteActivity extends PlaybackServiceActivity implements View.OnClickListener {
	private CoverView mCoverView;

	private View mOpenButton;
	private View mKillButton;
	private View mPreviousButton;
	private ImageView mPlayPauseButton;
	private View mNextButton;

	@Override
	public void onCreate(Bundle state)
	{
		super.onCreate(state);

		requestWindowFeature(Window.FEATURE_NO_TITLE); 
		setContentView(R.layout.remote_dialog);

		mCoverView = (CoverView)findViewById(R.id.cover_view);

		mOpenButton = findViewById(R.id.open_button);
		mOpenButton.setOnClickListener(this);
		mKillButton = findViewById(R.id.kill_button);
		mKillButton.setOnClickListener(this);
		mPreviousButton = findViewById(R.id.previous);
		mPreviousButton.setOnClickListener(this);
		mPlayPauseButton = (ImageView)findViewById(R.id.play_pause);
		mPlayPauseButton.setOnClickListener(this);
		mNextButton = findViewById(R.id.next);
		mNextButton.setOnClickListener(this);
	}

	@Override
	public void onResume()
	{
		super.onResume();

		bindPlaybackService(true);
		registerReceiver(mReceiver, new IntentFilter(PlaybackService.EVENT_STATE_CHANGED));
	}

	@Override
	public void onPause()
	{
		super.onPause();

		unbindService(this);
		try {
			unregisterReceiver(mReceiver);
		} catch (IllegalArgumentException e) {
			// we haven't registered the receiver yet
		}
	}

	@Override
	protected void setService(IPlaybackService service)
	{
		if (service == null) {
			finish();
		} else {
			mCoverView.setPlaybackService(service);
			try {
				setState(service.getState());
			} catch (RemoteException e) {
			}
		}
	}

	private void setState(int state)
	{
		if (state == PlaybackService.STATE_NO_MEDIA)
			finish();

		mPlayPauseButton.setImageResource(state == PlaybackService.STATE_PLAYING ? R.drawable.pause : R.drawable.play);
	}

	public void onClick(View view)
	{
		if (view == mKillButton) {
			stopPlaybackService(this);
			finish();
		} else if (view == mOpenButton) {
			startActivity(new Intent(this, NowPlayingActivity.class));
			finish();
		} else {
			try {
				if (view == mNextButton)
					mCoverView.go(1);
				else if (view == mPreviousButton)
					mCoverView.go(-1);
				else if (view == mPlayPauseButton)
					mCoverView.go(0);
			} catch (RemoteException e) {
				Log.e("VanillaMusic", "service dead", e);
				finish();
			}
		}
	}

	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();
			if (PlaybackService.EVENT_STATE_CHANGED.equals(action))
				setState(intent.getIntExtra("newState", 0));
		}
	};
}