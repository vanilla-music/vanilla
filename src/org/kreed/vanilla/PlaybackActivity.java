/*
 * Copyright (C) 2010 Christopher Eby <kreed@kreed.org>
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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

public class PlaybackActivity extends Activity implements Handler.Callback, View.OnClickListener {
	Handler mHandler;
	Looper mLooper;

	CoverView mCoverView;
	ControlButton mPlayPauseButton;
	int mState;

	@Override
	public void onCreate(Bundle state)
	{
		super.onCreate(state);
		ContextApplication.addActivity(this);

		HandlerThread thread = new HandlerThread(getClass().getName());
		thread.start();

		mLooper = thread.getLooper();
		mHandler = new Handler(mLooper, this);
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		ContextApplication.removeActivity(this);
		mLooper.quit();
	}

	@Override
	public void onStart()
	{
		super.onStart();

		if (ContextApplication.hasService())
			onServiceReady();
		else
			startService(new Intent(this, PlaybackService.class));
	}

	@Override
	public void onResume()
	{
		super.onResume();
		if (ContextApplication.hasService()) {
			PlaybackService service = ContextApplication.getService();
			service.userActionTriggered();
			service.registerMediaButton();
		}
	}

	public static boolean handleKeyLongPress(int keyCode)
	{
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			ContextApplication.quit();
			return true;
		}

		return false;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		switch (keyCode) {
		case KeyEvent.KEYCODE_HEADSETHOOK:
		case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
		case KeyEvent.KEYCODE_MEDIA_NEXT:
		case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
			return MediaButtonHandler.getInstance().processKey(event);
		}

		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event)
	{
		switch (keyCode) {
		case KeyEvent.KEYCODE_HEADSETHOOK:
		case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
		case KeyEvent.KEYCODE_MEDIA_NEXT:
		case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
			return MediaButtonHandler.getInstance().processKey(event);
		}

		return super.onKeyUp(keyCode, event);
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event)
	{
		return handleKeyLongPress(keyCode);
	}

	public void onClick(View view)
	{
		switch (view.getId()) {
		case R.id.next:
			mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_SONG, 1, 0));
			if (mCoverView != null)
				mCoverView.go(1);
			break;
		case R.id.play_pause:
			mHandler.sendMessage(mHandler.obtainMessage(MSG_TOGGLE_FLAG, PlaybackService.FLAG_PLAYING, 0));
			break;
		case R.id.previous:
			mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_SONG, -1, 0));
			if (mCoverView != null)
				mCoverView.go(-1);
			break;
		}
	}

	/**
	 * Updates <code>mState</code> and the play/pause button. Override to
	 * implement further behavior in subclasses.
	 *
	 * @param state PlaybackService state
	 */
	protected void setState(int state)
	{
		if (mState == state)
			return;

		mState = state;

		if (mPlayPauseButton != null) {
			final int res = (mState & PlaybackService.FLAG_PLAYING) == 0 ? R.drawable.play : R.drawable.pause;
			runOnUiThread(new Runnable() {
				public void run()
				{
					mPlayPauseButton.setImageResource(res);
				}
			});
		}
	}

	/**
	 * Sets up components when the PlaybackService is initialized and available to
	 * interact with. Override to implement further post-initialization behavior.
	 */
	protected void onServiceReady()
	{
		if (mCoverView != null)
			mCoverView.initialize();
		setState(ContextApplication.getService().getState());
	}

	/**
	 * Called by PlaybackService when it broadcasts an intent.
	 *
	 * @param intent The intent that was broadcast.
	 */
	public void receive(Intent intent)
	{
		String action = intent.getAction();

		if (PlaybackService.EVENT_INITIALIZED.equals(action))
			onServiceReady();
		if (mCoverView != null)
			mCoverView.receive(intent);
		if (PlaybackService.EVENT_CHANGED.equals(action))
			setState(intent.getIntExtra("state", 0));
	}

	/**
	 * Called when the content of the media store has changed.
	 */
	public void onMediaChange()
	{
	}

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
		menu.add(0, MENU_PREFS, 0, R.string.settings).setIcon(R.drawable.ic_menu_preferences);
		menu.add(0, MENU_SHUFFLE, 0, R.string.shuffle_enable).setIcon(R.drawable.ic_menu_shuffle);
		menu.add(0, MENU_REPEAT, 0, R.string.repeat_enable).setIcon(R.drawable.ic_menu_refresh);
		menu.add(0, MENU_QUIT, 0, R.string.quit).setIcon(R.drawable.ic_menu_close_clear_cancel);
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
			ContextApplication.quit();
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
	 * Tell PlaybackService to change the current song.
	 * 
	 * arg1 should be the delta, -1 or 1, indicating the previous or next song,
	 * respectively.
	 */
	static final int MSG_SET_SONG = 1;

	public boolean handleMessage(Message message)
	{
		switch (message.what) {
		case MSG_TOGGLE_FLAG:
			ContextApplication.getService().toggleFlag(message.arg1);
			break;
		case MSG_SET_SONG:
			if (message.arg1 == 1)
				ContextApplication.getService().nextSong();
			else
				ContextApplication.getService().previousSong();
			break;
		default:
			return false;
		}

		return true;
	}
}
