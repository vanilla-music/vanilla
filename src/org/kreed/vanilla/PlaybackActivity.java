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
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;

/**
 * Base activity for activities that contain playback controls. Handles
 * communication with the PlaybackService and response to state and song
 * changes.
 */
public class PlaybackActivity extends Activity
	implements Handler.Callback,
	           View.OnClickListener,
	           CoverView.Callback
{
	enum Action {
		Nothing,
		Library,
		PlayPause,
		NextSong,
		PreviousSong,
		Repeat,
		Shuffle,
		EnqueueAlbum,
		EnqueueArtist,
		EnqueueGenre,
		ClearQueue,
		ToggleControls,
	}

	private Action mUpAction;
	private Action mDownAction;

	/**
	 * A Handler running on the UI thread, in contrast with mHandler which runs
	 * on a worker thread.
	 */
	protected final Handler mUiHandler = new Handler(this);
	/**
	 * A Handler running on a worker thread.
	 */
	protected Handler mHandler;
	/**
	 * The looper for the worker thread.
	 */
	protected Looper mLooper;

	protected CoverView mCoverView;
	protected ImageButton mPlayPauseButton;
	protected ImageButton mShuffleButton;
	protected ImageButton mEndButton;

	protected int mState;
	private long mLastStateEvent;
	private long mLastSongEvent;

	@Override
	public void onCreate(Bundle state)
	{
		super.onCreate(state);

		PlaybackService.addActivity(this);

		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		HandlerThread thread = new HandlerThread(getClass().getName());
		thread.start();

		mLooper = thread.getLooper();
		mHandler = new Handler(mLooper, this);
	}

	@Override
	public void onDestroy()
	{
		PlaybackService.removeActivity(this);
		mLooper.quit();
		super.onDestroy();
	}

	/**
	 * Retrieve an action from the given SharedPreferences.
	 *
	 * @param prefs The SharedPreferences instance to load from.
	 * @param key The preference key.
	 * @param def The value to return if the key is not found or cannot be loaded.
	 * @return An action
	 */
	public static Action getAction(SharedPreferences prefs, String key, Action def)
	{
		try {
			String pref = prefs.getString(key, null);
			if (pref == null)
				return def;
			return Enum.valueOf(Action.class, pref);
		} catch (Exception e) {
			return def;
		}
	}

	@Override
	public void onStart()
	{
		super.onStart();

		if (PlaybackService.hasInstance())
			onServiceReady();
		else
			startService(new Intent(this, PlaybackService.class));

		SharedPreferences prefs = PlaybackService.getSettings(this);
		mUpAction = getAction(prefs, "swipe_up_action", Action.Nothing);
		mDownAction = getAction(prefs, "swipe_down_action", Action.Nothing);

		Window window = getWindow();
		if (prefs.getBoolean("disable_lockscreen", false))
			window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
		else
			window.clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
	}

	@Override
	public void onResume()
	{
		super.onResume();
		MediaButtonReceiver.registerMediaButton(this);
		MediaButtonReceiver.setInCall(false);
		if (PlaybackService.hasInstance()) {
			PlaybackService service = PlaybackService.get(this);
			service.userActionTriggered();
		}
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		switch (keyCode) {
		case KeyEvent.KEYCODE_HEADSETHOOK:
		case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
		case KeyEvent.KEYCODE_MEDIA_NEXT:
		case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
			return MediaButtonReceiver.processKey(this, event);
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
			return MediaButtonReceiver.processKey(this, event);
		}

		return super.onKeyUp(keyCode, event);
	}

	@Override
	public void nextSong()
	{
		setSong(PlaybackService.get(this).nextSong());
	}

	@Override
	public void previousSong()
	{
		setSong(PlaybackService.get(this).previousSong());
	}

	public void playPause()
	{
		PlaybackService service = PlaybackService.get(this);
		int state = service.playPause();
		if ((state & PlaybackService.FLAG_ERROR) != 0)
			Toast.makeText(this, service.getErrorMessage(), Toast.LENGTH_LONG).show();
		setState(state);
	}

	@Override
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
		case R.id.end_action:
			cycleFinishAction();
			break;
		case R.id.shuffle:
			cycleShuffle();
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

		if ((toggled & PlaybackService.MASK_FINISH) != 0 && mShuffleButton != null) {
			switch (PlaybackService.finishAction(state)) {
			case SongTimeline.FINISH_STOP:
				mEndButton.setImageResource(R.drawable.repeat_inactive);
				break;
			case SongTimeline.FINISH_REPEAT:
				mEndButton.setImageResource(R.drawable.repeat_active);
				break;
			case SongTimeline.FINISH_REPEAT_CURRENT:
				mEndButton.setImageResource(R.drawable.repeat_current_active);
				break;
			case SongTimeline.FINISH_STOP_CURRENT:
				mEndButton.setImageResource(R.drawable.stop_current_active);
				break;
			case SongTimeline.FINISH_RANDOM:
				mEndButton.setImageResource(R.drawable.random_active);
				break;
			}
		}

		if ((toggled & PlaybackService.MASK_SHUFFLE) != 0 && mEndButton != null) {
			switch (PlaybackService.shuffleMode(state)) {
			case SongTimeline.SHUFFLE_NONE:
				mShuffleButton.setImageResource(R.drawable.shuffle_inactive);
				break;
			case SongTimeline.SHUFFLE_SONGS:
				mShuffleButton.setImageResource(R.drawable.shuffle_active);
				break;
			case SongTimeline.SHUFFLE_ALBUMS:
				mShuffleButton.setImageResource(R.drawable.shuffle_album_active);
				break;
			}
		}
	}

	protected void setState(final int state)
	{
		mLastStateEvent = SystemClock.uptimeMillis();

		if (mState != state) {
			final int toggled = mState ^ state;
			mState = state;
			runOnUiThread(new Runnable() {
				@Override
				public void run()
				{
					onStateChange(state, toggled);
				}
			});
		}
	}

	/**
	 * Called by PlaybackService to update the state.
	 */
	public void setState(long uptime, int state)
	{
		if (uptime > mLastStateEvent)
			setState(state);
	}

	/**
	 * Sets up components when the PlaybackService is initialized and available to
	 * interact with. Override to implement further post-initialization behavior.
	 */
	protected void onServiceReady()
	{
		PlaybackService service = PlaybackService.get(this);
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
			mCoverView.querySongs(PlaybackService.get(this));
	}

	protected void setSong(final Song song)
	{
		mLastSongEvent = SystemClock.uptimeMillis();
		runOnUiThread(new Runnable() {
			@Override
			public void run()
			{
				onSongChange(song);
			}
		});
	}

	/**
	 * Called by PlaybackService to update the current song.
	 */
	public void setSong(long uptime, Song song)
	{
		if (uptime > mLastSongEvent)
			setSong(song);
	}

	/**
	 * Called by PlaybackService to update an active song (next, previous, or
	 * current).
	 */
	public void replaceSong(int delta, Song song)
	{
		if (mCoverView != null)
			mCoverView.setSong(delta + 1, song);
	}

	/**
	 * Called when the content of the media store has changed.
	 */
	public void onMediaChange()
	{
	}

	static final int MENU_SORT = 1;
	static final int MENU_PREFS = 2;
	static final int MENU_LIBRARY = 3;
	static final int MENU_PLAYBACK = 5;
	static final int MENU_SEARCH = 7;

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		menu.add(0, MENU_PREFS, 0, R.string.settings).setIcon(R.drawable.ic_menu_preferences);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId()) {
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
	 * Cycle shuffle mode.
	 */
	public void cycleShuffle()
	{
		setState(PlaybackService.get(this).cycleShuffle());
	}

	/**
	 * Cycle the finish action.
	 */
	public void cycleFinishAction()
	{
		setState(PlaybackService.get(this).cycleFinishAction());
	}

	/**
	 * Open the library activity.
	 */
	public void openLibrary()
	{
		Intent intent = new Intent(this, LibraryActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);
	}

	public void enqueue(int type)
	{
		PlaybackService.get(this).enqueueFromCurrent(type);
	}

	public void performAction(Action action)
	{
		switch (action) {
		case Nothing:
			break;
		case Library:
			openLibrary();
			break;
		case PlayPause:
			playPause();
			break;
		case NextSong:
			nextSong();
			break;
		case PreviousSong:
			previousSong();
			break;
		case Repeat:
			cycleFinishAction();
			break;
		case Shuffle:
			cycleShuffle();
			break;
		case EnqueueAlbum:
			enqueue(MediaUtils.TYPE_ALBUM);
			break;
		case EnqueueArtist:
			enqueue(MediaUtils.TYPE_ARTIST);
			break;
		case EnqueueGenre:
			enqueue(MediaUtils.TYPE_GENRE);
			break;
		case ClearQueue:
			PlaybackService.get(this).clearQueue();
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

	private static final int GROUP_SHUFFLE = 100;
	private static final int GROUP_FINISH = 101;

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo menuInfo)
	{
		if (view == mShuffleButton) {
			menu.add(GROUP_SHUFFLE, SongTimeline.SHUFFLE_NONE, 0, R.string.no_shuffle);
			menu.add(GROUP_SHUFFLE, SongTimeline.SHUFFLE_SONGS, 0, R.string.shuffle_songs);
			menu.add(GROUP_SHUFFLE, SongTimeline.SHUFFLE_ALBUMS, 0, R.string.shuffle_albums);
		} else if (view == mEndButton) {
		    menu.add(GROUP_FINISH, SongTimeline.FINISH_STOP, 0, R.string.no_repeat);
			menu.add(GROUP_FINISH, SongTimeline.FINISH_REPEAT, 0, R.string.repeat);
			menu.add(GROUP_FINISH, SongTimeline.FINISH_REPEAT_CURRENT, 0, R.string.repeat_current_song);
			menu.add(GROUP_FINISH, SongTimeline.FINISH_STOP_CURRENT, 0, R.string.stop_current_song);
			menu.add(GROUP_FINISH, SongTimeline.FINISH_RANDOM, 0, R.string.random);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		int group = item.getGroupId();
		int id = item.getItemId();
		if (group == GROUP_SHUFFLE)
			setState(PlaybackService.get(this).setShuffleMode(id));
		else if (group == GROUP_FINISH)
			setState(PlaybackService.get(this).setFinishAction(id));
		return true;
	}
}
