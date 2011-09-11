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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

public class PlaybackActivity extends Activity implements Handler.Callback, View.OnClickListener, CoverView.Callback {
	public static final int ACTION_NOTHING = 0;
	public static final int ACTION_LIBRARY = 1;
	public static final int ACTION_PLAY_PAUSE = 2;
	public static final int ACTION_NEXT_SONG = 3;
	public static final int ACTION_PREVIOUS_SONG = 4;
	public static final int ACTION_REPEAT = 5;
	public static final int ACTION_SHUFFLE = 6;
	public static final int ACTION_ENQUEUE_ALBUM = 7;
	public static final int ACTION_ENQUEUE_ARTIST = 8;
	public static final int ACTION_ENQUEUE_GENRE = 9;
	public static final int ACTION_CLEAR_QUEUE = 10;

	public static int mUpAction;
	public static int mDownAction;

	protected Handler mHandler;
	protected Looper mLooper;

	protected CoverView mCoverView;
	protected ControlButton mPlayPauseButton;

	protected int mState;
	private long mLastStateEvent;
	private long mLastSongEvent;

	@Override
	public void onCreate(Bundle state)
	{
		super.onCreate(state);
		ContextApplication.addActivity(this);

		HandlerThread thread = new HandlerThread(getClass().getName());
		thread.start();

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		mUpAction = Integer.parseInt(prefs.getString("swipe_up_action", "0"));
		mDownAction = Integer.parseInt(prefs.getString("swipe_down_action", "0"));

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

			MediaButtonHandler buttons = MediaButtonHandler.getInstance();
			if (buttons != null)
				buttons.setInCall(false);
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

	public void nextSong()
	{
		setSong(ContextApplication.getService().nextSong());
	}

	public void previousSong()
	{
		setSong(ContextApplication.getService().previousSong());
	}

	public void playPause()
	{
		setState(ContextApplication.getService().toggleFlag(PlaybackService.FLAG_PLAYING));
	}

	public void onClick(View view)
	{
		switch (view.getId()) {
		case R.id.next:
			nextSong();
			break;
		case R.id.play_pause:
			playPause();
			break;
		case R.id.previous:
			previousSong();
			break;
		}
	}

	/**
	 * Called when the PlaybackService state has changed.
	 *
	 * @param state PlaybackService state
	 * @param toggled The flags that have changed from the previous state
	 */
	protected void onStateChange(int state, int toggled)
	{
		if ((toggled & PlaybackService.FLAG_PLAYING) != 0 && mPlayPauseButton != null)
			mPlayPauseButton.setImageResource((state & PlaybackService.FLAG_PLAYING) == 0 ? R.drawable.play : R.drawable.pause);

	}

	protected void setState(final int state)
	{
		mLastStateEvent = SystemClock.uptimeMillis();

		if (mState != state) {
			final int toggled = mState ^ state;
			mState = state;
			runOnUiThread(new Runnable() {
				public void run()
				{
					onStateChange(state, toggled);
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
		PlaybackService service = ContextApplication.getService();
		setSong(service.getSong(0));
		setState(service.getState());
	}

	/**
	 * Called when the current song changes.
	 *
	 * @param song The new song
	 */
	protected void onSongChange(Song song)
	{
		if (mCoverView != null)
			mCoverView.setCurrentSong(song);
	}

	protected void setSong(final Song song)
	{
		mLastSongEvent = SystemClock.uptimeMillis();
		runOnUiThread(new Runnable() {
			public void run()
			{
				onSongChange(song);
			}
		});
	}

	/**
	 * Called by PlaybackService when it broadcasts an intent.
	 *
	 * @param intent The intent that was broadcast.
	 */
	public void receive(Intent intent)
	{
		String action = intent.getAction();

		if (PlaybackService.EVENT_CHANGED.equals(action)) {
			long time = intent.getLongExtra("time", -1);
			assert(time != -1);

			int state = intent.getIntExtra("state", -1);
			if (state != -1 && time > mLastStateEvent)
				setState(state);

			if (intent.hasExtra("song") && time > mLastSongEvent) {
				Song song = intent.getParcelableExtra("song");
				setSong(song);
			}
		}
		if (mCoverView != null)
			mCoverView.receive(intent);
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
		if ((mState & PlaybackService.FLAG_NO_MEDIA) != 0)
			menu.findItem(MENU_REPEAT).setEnabled(false);
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
			toggleShuffle();
			return true;
		case MENU_REPEAT:
			toggleRepeat();
			return true;
		case MENU_PREFS:
			startActivity(new Intent(this, PreferencesActivity.class));
			return true;
		default:
			return false;
		}
	}

	@Override
	public boolean handleMessage(Message msg)
	{
		return false;
	}

	/**
	 * Toggle shuffle mode on/off
	 */
	public void toggleShuffle()
	{
		setState(ContextApplication.getService().toggleFlag(PlaybackService.FLAG_SHUFFLE));
	}

	/**
	 * Toggle repeat mode on/off
	 */
	public void toggleRepeat()
	{
		setState(ContextApplication.getService().toggleFlag(PlaybackService.FLAG_REPEAT));
	}

	/**
	 * Open the library activity.
	 */
	public void openLibrary()
	{
		startActivity(new Intent(this, SongSelector.class));
	}

	public void enqueue(int type)
	{
		int count = ContextApplication.getService().enqueueFromCurrent(type);
		String text = getResources().getQuantityString(R.plurals.enqueued_count, count, count);
		Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
	}

	public void performAction(int action)
	{
		switch (action) {
		case ACTION_NOTHING:
			break;
		case ACTION_LIBRARY:
			openLibrary();
			break;
		case ACTION_PLAY_PAUSE:
			playPause();
			break;
		case ACTION_NEXT_SONG:
			nextSong();
			break;
		case ACTION_PREVIOUS_SONG:
			previousSong();
			break;
		case ACTION_REPEAT:
			toggleRepeat();
			break;
		case ACTION_SHUFFLE:
			toggleShuffle();
			break;
		case ACTION_ENQUEUE_ALBUM:
			enqueue(MediaUtils.TYPE_ALBUM);
			break;
		case ACTION_ENQUEUE_ARTIST:
			enqueue(MediaUtils.TYPE_ARTIST);
			break;
		case ACTION_ENQUEUE_GENRE:
			enqueue(MediaUtils.TYPE_GENRE);
			break;
		case ACTION_CLEAR_QUEUE:
			ContextApplication.getService().clearQueue();
			Toast.makeText(this, R.string.queue_cleared, Toast.LENGTH_SHORT).show();
			break;
		default:
			throw new IllegalArgumentException("Invalid action: " + action);
		}
	}

	@Override
	public void upSwipe()
	{
		performAction(mUpAction);
	}

	@Override
	public void downSwipe()
	{
		performAction(mDownAction);
	}
}
