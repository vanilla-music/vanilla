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

import org.kreed.vanilla.R;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class FullPlaybackActivity extends PlaybackActivity implements SeekBar.OnSeekBarChangeListener {
	private RelativeLayout mMessageOverlay;
	private View mControlsTop;
	private View mControlsBottom;

	private SeekBar mSeekBar;
	private TextView mSeekText;

	private int mDuration;
	private boolean mSeekBarTracking;
	private boolean mPaused;

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);

		setContentView(R.layout.full_playback);

		mCoverView = (CoverView)findViewById(R.id.cover_view);
		mCoverView.mHandler = mHandler;
		mCoverView.setOnClickListener(this);

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		mCoverView.mSeparateInfo = settings.getBoolean("separate_info", false);

		mControlsTop = findViewById(R.id.controls_top);
		mControlsBottom = findViewById(R.id.controls_bottom);

		View previousButton = findViewById(R.id.previous);
		previousButton.setOnClickListener(this);
		mPlayPauseButton = (ControlButton)findViewById(R.id.play_pause);
		mPlayPauseButton.setOnClickListener(this);
		View nextButton = findViewById(R.id.next);
		nextButton.setOnClickListener(this);

		mSeekText = (TextView)findViewById(R.id.seek_text);
		mSeekBar = (SeekBar)findViewById(R.id.seek_bar);
		mSeekBar.setMax(1000);
		mSeekBar.setOnSeekBarChangeListener(this);
	}

	@Override
	public void onResume()
	{
		super.onResume();
		mPaused = false;
		updateProgress();
	}

	@Override
	public void onPause()
	{
		super.onPause();
		mPaused = true;
	}

	@Override
	protected void setState(int state)
	{
		if (state == mState)
			return;

		super.setState(state);

		if (mMessageOverlay != null)
			mMessageOverlay.setVisibility(View.GONE);

		if ((mState & PlaybackService.FLAG_NO_MEDIA) != 0) {
			if (mMessageOverlay == null) {
				mMessageOverlay = new RelativeLayout(this);
				mMessageOverlay.setBackgroundColor(Color.BLACK);
				addContentView(mMessageOverlay,
					new ViewGroup.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT,
					                           LinearLayout.LayoutParams.FILL_PARENT));
			} else {
				mMessageOverlay.setVisibility(View.VISIBLE);
				mMessageOverlay.removeAllViews();
			}

			RelativeLayout.LayoutParams layoutParams =
				new RelativeLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
				                                LinearLayout.LayoutParams.WRAP_CONTENT);
			layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);

			TextView text = new TextView(this);
			text.setText(R.string.no_songs);
			text.setLayoutParams(layoutParams);
			mMessageOverlay.addView(text);
		}
	}

	@Override
	protected void onServiceReady()
	{
		super.onServiceReady();

		mDuration = ContextApplication.getService().getDuration();
	}

	@Override
	public void receive(Intent intent)
	{
		super.receive(intent);

		if (PlaybackService.EVENT_CHANGED.equals(intent.getAction())) {
			mDuration = ContextApplication.getService().getDuration();
			mUiHandler.sendEmptyMessage(MSG_UPDATE_PROGRESS);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		menu.add(0, MENU_LIBRARY, 0, R.string.library).setIcon(android.R.drawable.ic_menu_add);
		menu.add(0, MENU_DISPLAY, 0, R.string.display_mode).setIcon(android.R.drawable.ic_menu_gallery);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId()) {
		case MENU_LIBRARY:
			startActivity(new Intent(this, SongSelector.class));
			return true;
		case MENU_DISPLAY:
			mCoverView.toggleDisplayMode();
			mHandler.sendEmptyMessage(MSG_SAVE_DISPLAY_MODE);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public boolean onSearchRequested()
	{
		startActivity(new Intent(this, SongSelector.class));
		return false;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event)
	{
		switch (keyCode) {
		case KeyEvent.KEYCODE_DPAD_CENTER:
		case KeyEvent.KEYCODE_ENTER:
			onClick(mCoverView);
			return true;
		}

		return super.onKeyUp(keyCode, event);
	}

	/**
	 * Converts a duration in milliseconds to [HH:]MM:SS
	 */
	private String stringForTime(int ms)
	{
		int seconds = ms / 1000;

		int hours = seconds / 3600;
		seconds -= hours * 3600;
		int minutes = seconds / 60;
		seconds -= minutes * 60;

		if (hours > 0)
			return String.format("%d:%02d:%02d", hours, minutes, seconds);
		else
			return String.format("%02d:%02d", minutes, seconds);
	}

	/**
	 * Update seek bar progress and schedule another update in one second
	 */
	private void updateProgress()
	{
		if (mPaused || mControlsTop.getVisibility() != View.VISIBLE || (mState & PlaybackService.FLAG_PLAYING) == 0)
			return;

		int position = ContextApplication.getService().getPosition();

		if (!mSeekBarTracking)
			mSeekBar.setProgress(mDuration == 0 ? 0 : (int)(1000 * position / mDuration));
		mSeekText.setText(stringForTime((int)position) + " / " + stringForTime(mDuration));

		// Try to update right when the duration increases by one second
		long next = 1000 - position % 1000;
		mUiHandler.removeMessages(MSG_UPDATE_PROGRESS);
		mUiHandler.sendEmptyMessageDelayed(MSG_UPDATE_PROGRESS, next);
	}

	@Override
	public void onClick(View view)
	{
		if (view == mCoverView) {
			if (mControlsTop.getVisibility() == View.VISIBLE) {
				mControlsTop.setVisibility(View.GONE);
				mControlsBottom.setVisibility(View.GONE);
			} else {
				mControlsTop.setVisibility(View.VISIBLE);
				mControlsBottom.setVisibility(View.VISIBLE);

				mPlayPauseButton.requestFocus();

				updateProgress();
			}
		} else {
			super.onClick(view);
		}
	}

	private static final int MSG_UPDATE_PROGRESS = 10;
	private static final int MSG_SAVE_DISPLAY_MODE = 11;

	@Override
	public boolean handleMessage(Message message)
	{
		switch (message.what) {
		case MSG_SAVE_DISPLAY_MODE:
			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
			SharedPreferences.Editor editor = settings.edit();
			editor.putBoolean("separate_info", mCoverView.mSeparateInfo);
			editor.commit();
			return true;
		default:
			return super.handleMessage(message);
		}
	}

	private Handler mUiHandler = new Handler() {
		@Override
		public void handleMessage(Message message)
		{
			switch (message.what) {
			case MSG_UPDATE_PROGRESS:
				updateProgress();
				break;
			}
		}
	};

	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
	{
		if (fromUser)
			ContextApplication.getService().seekToProgress(progress);
	}

	public void onStartTrackingTouch(SeekBar seekBar)
	{
		mSeekBarTracking = true;
	}

	public void onStopTrackingTouch(SeekBar seekBar)
	{
		mSeekBarTracking = false;
	}
}
