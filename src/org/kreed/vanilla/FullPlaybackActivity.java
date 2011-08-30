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

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class FullPlaybackActivity extends PlaybackActivity implements SeekBar.OnSeekBarChangeListener, View.OnLongClickListener {
	public static final int DISPLAY_INFO_OVERLAP = 0;
	public static final int DISPLAY_INFO_BELOW = 1;
	public static final int DISPLAY_INFO_WIDGETS = 2;
	public static final int DISPLAY_INFO_WIDGETS_ZOOMED = 3;

	/**
	 * A Handler running on the UI thread, in contrast with mHandler which runs
	 * on a worker thread.
	 */
	private Handler mUiHandler = new Handler(this);

	private RelativeLayout mMessageOverlay;
	private View mControlsTop;
	private View mControlsBottom;

	private SeekBar mSeekBar;
	private TextView mSeekText;

	private TextView mTitle;
	private TextView mAlbum;
	private TextView mArtist;

	private long mDuration;
	private boolean mSeekBarTracking;
	private boolean mPaused;

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		int displayMode = Integer.parseInt(settings.getString("display_mode", "0"));
		boolean hiddenControls = settings.getBoolean("hidden_controls", false);

		int layout = R.layout.full_playback;
		int coverStyle = -1;

		switch (displayMode) {
		default:
			Log.e("VanillaMusic", "Invalid display mode given. Defaulting to overlap");
			// fall through
		case DISPLAY_INFO_OVERLAP:
			coverStyle = CoverBitmap.STYLE_OVERLAPPING_BOX;
			break;
		case DISPLAY_INFO_BELOW:
			coverStyle = CoverBitmap.STYLE_INFO_BELOW;
			break;
		case DISPLAY_INFO_WIDGETS:
			coverStyle = CoverBitmap.STYLE_NO_INFO;
			layout = R.layout.full_playback_alt;
			break;
		case DISPLAY_INFO_WIDGETS_ZOOMED:
			coverStyle = CoverBitmap.STYLE_NO_INFO_ZOOMED;
			layout = R.layout.full_playback_alt;
			break;
		}

		setContentView(layout);

		CoverView coverView = (CoverView)findViewById(R.id.cover_view);
		coverView.setup(mLooper, this, coverStyle);
		coverView.setOnClickListener(this);
		coverView.setOnLongClickListener(this);
		mCoverView = coverView;

		mControlsTop = findViewById(R.id.controls_top);
		mControlsBottom = findViewById(R.id.controls_bottom);
		if (hiddenControls) {
			mControlsTop.setVisibility(View.GONE);
			mControlsBottom.setVisibility(View.GONE);
		}

		View previousButton = findViewById(R.id.previous);
		previousButton.setOnClickListener(this);
		mPlayPauseButton = (ControlButton)findViewById(R.id.play_pause);
		mPlayPauseButton.setOnClickListener(this);
		View nextButton = findViewById(R.id.next);
		nextButton.setOnClickListener(this);

		mTitle = (TextView)findViewById(R.id.title);
		mAlbum = (TextView)findViewById(R.id.album);
		mArtist = (TextView)findViewById(R.id.artist);

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

	/**
	 * Show the message view overlay, creating it if necessary and clearing
	 * it of all content.
	 */
	private void showMessageOverlay()
	{
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
	}

	/**
	 * Hide the message overlay, if it exists.
	 */
	private void hideMessageOverlay()
	{
		if (mMessageOverlay != null)
			mMessageOverlay.setVisibility(View.GONE);
	}

	/**
	 * Show the no media message in the message overlay. The message overlay
	 * must have been created with showMessageOverlay before this method is
	 * called.
	 */
	private void setNoMediaOverlayMessage()
	{
		RelativeLayout.LayoutParams layoutParams =
			new RelativeLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
			                                LinearLayout.LayoutParams.WRAP_CONTENT);
		layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);

		TextView text = new TextView(this);
		text.setText(R.string.no_songs);
		text.setLayoutParams(layoutParams);
		mMessageOverlay.addView(text);
	}

	@Override
	protected void onStateChange(int state, int toggled)
	{
		super.onStateChange(state, toggled);

		if ((toggled & PlaybackService.FLAG_NO_MEDIA) != 0) {
			if ((state & PlaybackService.FLAG_NO_MEDIA) != 0) {
				showMessageOverlay();
				setNoMediaOverlayMessage();
			} else {
				hideMessageOverlay();
			}
		}
	}

	@Override
	protected void onSongChange(final Song song)
	{
		super.onSongChange(song);

		mDuration = song == null ? 0 : song.duration;

		if (mTitle != null) {
			if (song == null) {
				mTitle.setText(null);
				mAlbum.setText(null);
				mArtist.setText(null);
			} else {
				mTitle.setText(song.title);
				mAlbum.setText(song.album);
				mArtist.setText(song.artist);
			}
		}

		updateProgress();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		menu.add(0, MENU_LIBRARY, 0, R.string.library).setIcon(R.drawable.ic_menu_music_library);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId()) {
		case MENU_LIBRARY:
			startActivity(new Intent(this, SongSelector.class));
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
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		switch (keyCode) {
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			nextSong();
			findViewById(R.id.next).requestFocus();
			return true;
		case KeyEvent.KEYCODE_DPAD_LEFT:
			previousSong();
			findViewById(R.id.previous).requestFocus();
			return true;
		}

		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event)
	{
		switch (keyCode) {
		case KeyEvent.KEYCODE_DPAD_CENTER:
		case KeyEvent.KEYCODE_ENTER:
			toggleControls();
			return true;
		}

		return super.onKeyUp(keyCode, event);
	}

	/**
	 * Converts a duration in milliseconds to [HH:]MM:SS
	 */
	private String stringForTime(long ms)
	{
		int seconds = (int)(ms / 1000);

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
		int position = ContextApplication.hasService() ? ContextApplication.getService().getPosition() : 0;

		if (!mSeekBarTracking)
			mSeekBar.setProgress(mDuration == 0 ? 0 : (int)(1000 * position / mDuration));
		mSeekText.setText(stringForTime((int)position) + " / " + stringForTime(mDuration));

		if (!mPaused && mControlsTop.getVisibility() == View.VISIBLE && (mState & PlaybackService.FLAG_PLAYING) != 0) {
			// Try to update right when the duration increases by one second
			long next = 1000 - position % 1000;
			mUiHandler.removeMessages(MSG_UPDATE_PROGRESS);
			mUiHandler.sendEmptyMessageDelayed(MSG_UPDATE_PROGRESS, next);
		}
	}

	/**
	 * Toggles the visibility of the playback controls.
	 */
	private void toggleControls()
	{
		if (mControlsTop.getVisibility() == View.VISIBLE) {
			mControlsTop.setVisibility(View.GONE);
			mControlsBottom.setVisibility(View.GONE);
		} else {
			mControlsTop.setVisibility(View.VISIBLE);
			mControlsBottom.setVisibility(View.VISIBLE);

			mPlayPauseButton.requestFocus();

			updateProgress();
		}

		int hidden = mControlsTop.getVisibility() == View.VISIBLE ? 0 : 1;
		mHandler.sendMessage(mHandler.obtainMessage(MSG_SAVE_CONTROLS, hidden, 0));
	}

	@Override
	public void onClick(View view)
	{
		switch (view.getId()) {
		case R.id.cover_view:
			toggleControls();
			break;
		default:
			super.onClick(view);
		}
	}

	public boolean onLongClick(View view)
	{
		if (view.getId() == R.id.cover_view) {
			setState(ContextApplication.getService().toggleFlag(PlaybackService.FLAG_PLAYING));
			return true;
		}

		return false;
	}

	/**
	 * Update the seekbar progress with the current song progress. This must be
	 * called on the UI Handler.
	 */
	private static final int MSG_UPDATE_PROGRESS = 10;
	/**
	 * Save the hidden_controls preference to storage.
	 */
	private static final int MSG_SAVE_CONTROLS = 14;

	@Override
	public boolean handleMessage(Message message)
	{
		switch (message.what) {
		case MSG_SAVE_CONTROLS: {
			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
			SharedPreferences.Editor editor = settings.edit();
			editor.putBoolean("hidden_controls", message.arg1 == 1);
			editor.commit();
			break;
		}
		case MSG_UPDATE_PROGRESS:
			updateProgress();
			break;
		default:
			return super.handleMessage(message);
		}

		return true;
	}

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
