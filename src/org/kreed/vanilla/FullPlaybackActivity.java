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

import android.app.Dialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class FullPlaybackActivity extends PlaybackActivity implements View.OnClickListener, SeekBar.OnSeekBarChangeListener, View.OnFocusChangeListener, Handler.Callback {
	private IPlaybackService mService;
	private Handler mHandler = new Handler(this);

	private RelativeLayout mMessageOverlay;
	private View mControlsTop;
	private View mControlsBottom;

	private ImageView mPlayPauseButton;
	private SeekBar mSeekBar;
	private TextView mSeekText;

	private int mState;
	private int mDuration;
	private boolean mSeekBarTracking;

	private static final int SONG_SELECTOR = 8;

	private static final int MENU_QUIT = 0;
	private static final int MENU_DISPLAY = 1;
	private static final int MENU_PREFS = 2;
	private static final int MENU_LIBRARY = 3;
	private static final int MENU_SHUFFLE = 4;
	private static final int MENU_PLAYBACK = 5;
	private static final int MENU_REPEAT = 6;

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		if (icicle == null && settings.getBoolean("selector_on_startup", false))
			showDialog(SONG_SELECTOR);

		setContentView(R.layout.full_playback);

		mCoverView = (CoverView)findViewById(R.id.cover_view);
		mCoverView.setOnClickListener(this);
		mCoverView.setSeparateInfo(settings.getBoolean("separate_info", false));

		mControlsTop = findViewById(R.id.controls_top);
		mControlsBottom = findViewById(R.id.controls_bottom);

		View previousButton = findViewById(R.id.previous);
		previousButton.setOnClickListener(this);
		previousButton.setOnFocusChangeListener(this);
		mPlayPauseButton = (ImageView)findViewById(R.id.play_pause);
		mPlayPauseButton.setOnClickListener(this);
		mPlayPauseButton.setOnFocusChangeListener(this);
		View nextButton = findViewById(R.id.next);
		nextButton.setOnClickListener(this);
		nextButton.setOnFocusChangeListener(this);

		mSeekText = (TextView)findViewById(R.id.seek_text);
		mSeekBar = (SeekBar)findViewById(R.id.seek_bar);
		mSeekBar.setMax(1000);
		mSeekBar.setOnSeekBarChangeListener(this);
		mSeekBar.setOnFocusChangeListener(this);
	}

	@Override
	protected void setState(int state)
	{
		if (state == mState)
			return;

		mState = state;

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

		if ((mState & PlaybackService.FLAG_PLAYING) != 0) {
			if (!mHandler.hasMessages(HIDE))
				mControlsBottom.setVisibility(View.GONE);
			mPlayPauseButton.setImageResource(R.drawable.pause);
		} else {
			mControlsBottom.setVisibility(View.VISIBLE);
			mPlayPauseButton.setImageResource(R.drawable.play);
		}
	}

	@Override
	protected void setService(IPlaybackService service)
	{
		super.setService(service);

		mService = service;

		if (service != null) {
			try {
				mDuration = service.getDuration();
			} catch (RemoteException e) {
			}
		}
	}

	@Override
	protected void onServiceChange(Intent intent)
	{
		super.onServiceChange(intent);

		if (mService != null) {
			try {
				mDuration = mService.getDuration();
				mHandler.sendEmptyMessage(UPDATE_PROGRESS);
			} catch (RemoteException e) {
				setService(null);
			}
		}
	}

	public void fillMenu(Menu menu, boolean fromDialog)
	{
		if (fromDialog) {
			menu.add(0, MENU_PLAYBACK, 0, R.string.playback_view).setIcon(android.R.drawable.ic_menu_gallery);
		} else {
			menu.add(0, MENU_DISPLAY, 0, R.string.display_mode).setIcon(android.R.drawable.ic_menu_gallery);
			menu.add(0, MENU_LIBRARY, 0, R.string.library).setIcon(android.R.drawable.ic_menu_add);
		}
		menu.add(0, MENU_SHUFFLE, 0, R.string.shuffle_enable).setIcon(R.drawable.ic_menu_shuffle);
		menu.add(0, MENU_REPEAT, 0, R.string.repeat_enable).setIcon(R.drawable.ic_menu_refresh);
		menu.add(0, MENU_PREFS, 0, R.string.settings).setIcon(android.R.drawable.ic_menu_preferences);
		menu.add(0, MENU_QUIT, 0, R.string.quit).setIcon(android.R.drawable.ic_menu_close_clear_cancel);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		fillMenu(menu, false);
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
			break;
		case MENU_SHUFFLE:
			mHandler.sendMessage(mHandler.obtainMessage(TOGGLE_FLAG, PlaybackService.FLAG_SHUFFLE, 0));
			break;
		case MENU_REPEAT:
			mHandler.sendMessage(mHandler.obtainMessage(TOGGLE_FLAG, PlaybackService.FLAG_REPEAT, 0));
			break;
		case MENU_PREFS:
			startActivity(new Intent(this, PreferencesActivity.class));
			break;
		case MENU_PLAYBACK:
			dismissDialog(SONG_SELECTOR);
			break;
		case MENU_LIBRARY:
			showDialog(SONG_SELECTOR);
			break;
		case MENU_DISPLAY:
			mCoverView.toggleDisplayMode();
			mHandler.sendEmptyMessage(SAVE_DISPLAY_MODE);
			break;
		}

		return true;
	}

	@Override
	public boolean onSearchRequested()
	{
		showDialog(SONG_SELECTOR);
		return false;
	}

	@Override
	public Dialog onCreateDialog(int id)
	{
		if (id == SONG_SELECTOR)
			return new SongSelector(this);
		return null;
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

	private void updateProgress()
	{
		if (mControlsTop.getVisibility() != View.VISIBLE)
			return;

		int position = 0;
		if (mService != null) {
			try {
				position = mService.getPosition();
			} catch (RemoteException e) {
			}
		}

		if (!mSeekBarTracking)
			mSeekBar.setProgress(mDuration == 0 ? 0 : (int)(1000 * position / mDuration));
		mSeekText.setText(stringForTime((int)position) + " / " + stringForTime(mDuration));

		long next = 1000 - position % 1000;
		mHandler.removeMessages(UPDATE_PROGRESS);
		mHandler.sendEmptyMessageDelayed(UPDATE_PROGRESS, next);
	}
	
	private void sendHideMessage()
	{
		mHandler.removeMessages(HIDE);
		mHandler.sendEmptyMessageDelayed(HIDE, 3000);
	}

	public void onClick(View view)
	{
		sendHideMessage();

		if (view == mCoverView) {
			if (mControlsTop.getVisibility() == View.VISIBLE) {
				mControlsTop.setVisibility(View.GONE);
				if ((mState & PlaybackService.FLAG_PLAYING) != 0)
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

	private static final int HIDE = 0;
	private static final int UPDATE_PROGRESS = 1;
	private static final int SAVE_DISPLAY_MODE = 2;
	private static final int TOGGLE_FLAG = 3;

	public boolean handleMessage(Message message)
	{
		switch (message.what) {
		case HIDE:
			mControlsTop.setVisibility(View.GONE);
			if ((mState & PlaybackService.FLAG_PLAYING) != 0)
				mControlsBottom.setVisibility(View.GONE);
			break;
		case UPDATE_PROGRESS:
			updateProgress();
			break;
		case SAVE_DISPLAY_MODE:
			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(FullPlaybackActivity.this);
			SharedPreferences.Editor editor = settings.edit();
			editor.putBoolean("separate_info", mCoverView.hasSeparateInfo());
			editor.commit();
			break;
		case TOGGLE_FLAG:
			int flag = message.arg1;
			boolean enabling = (mState & flag) == 0;
			int text = -1;
			if (flag == PlaybackService.FLAG_SHUFFLE)
				text = enabling ? R.string.shuffle_enabling : R.string.shuffle_disabling;
			else if (flag == PlaybackService.FLAG_REPEAT)
				text = enabling ? R.string.repeat_enabling : R.string.repeat_disabling;

			if (text != -1)
				Toast.makeText(FullPlaybackActivity.this, text, Toast.LENGTH_SHORT).show();

			try {
				mService.toggleFlag(flag);
			} catch (RemoteException e) {
				setService(null);
			}
			break;
		default:
			return false;
		}

		return true;
	}

	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
	{
		if (!fromUser || mService == null)
			return;

		try {
			mService.seekToProgress(progress);
		} catch (RemoteException e) {
		}
	}

	public void onStartTrackingTouch(SeekBar seekBar)
	{
		mHandler.removeMessages(HIDE);
		mSeekBarTracking = true;
	}

	public void onStopTrackingTouch(SeekBar seekBar)
	{
		sendHideMessage();
		mSeekBarTracking = false;
	}

	public void onFocusChange(View v, boolean hasFocus)
	{
		if (hasFocus)
			sendHideMessage();
	}
}
