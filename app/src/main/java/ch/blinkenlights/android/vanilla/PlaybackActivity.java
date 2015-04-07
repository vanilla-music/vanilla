/*
 * Copyright (C) 2010, 2011 Christopher Eby <kreed@kreed.org>
 * Copyright (C) 2014 Adrian Ulrich <adrian@blinkenlights.ch>
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

package ch.blinkenlights.android.vanilla;

import java.io.File;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;
import android.os.Environment;
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
public abstract class PlaybackActivity extends Activity
	implements Handler.Callback,
	           View.OnClickListener,
	           CoverView.Callback
{
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

		HandlerThread thread = new HandlerThread(getClass().getName(), Process.THREAD_PRIORITY_LOWEST);
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

	@Override
	public void onStart()
	{
		super.onStart();

		if (PlaybackService.hasInstance())
			onServiceReady();
		else
			startService(new Intent(this, PlaybackService.class));

		SharedPreferences prefs = PlaybackService.getSettings(this);
		mUpAction = Action.getAction(prefs, PrefKeys.SWIPE_UP_ACTION, Action.Nothing);
		mDownAction = Action.getAction(prefs, PrefKeys.SWIPE_DOWN_ACTION, Action.Nothing);

		Window window = getWindow();
		if (prefs.getBoolean(PrefKeys.DISABLE_LOCKSCREEN, false))
			window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
					| WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		else
			window.clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
					| WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
	}

	@Override
	public void onResume()
	{
		super.onResume();
		MediaButtonReceiver.registerMediaButton(this);
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
	public void shiftCurrentSong(int delta)
	{
		setSong(PlaybackService.get(this).shiftCurrentSong(delta));
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
			shiftCurrentSong(SongTimeline.SHIFT_NEXT_SONG);
			break;
		case R.id.play_pause:
			playPause();
			break;
		case R.id.previous:
			shiftCurrentSong(SongTimeline.SHIFT_PREVIOUS_SONG);
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
		if ((toggled & PlaybackService.FLAG_PLAYING) != 0 && mPlayPauseButton != null) {
			mPlayPauseButton.setImageResource((state & PlaybackService.FLAG_PLAYING) == 0 ? R.drawable.play : R.drawable.pause);
		}
		if ((toggled & PlaybackService.MASK_FINISH) != 0 && mEndButton != null) {
			mEndButton.setImageResource(SongTimeline.FINISH_ICONS[PlaybackService.finishAction(state)]);
		}
		if ((toggled & PlaybackService.MASK_SHUFFLE) != 0 && mShuffleButton != null) {
			mShuffleButton.setImageResource(SongTimeline.SHUFFLE_ICONS[PlaybackService.shuffleMode(state)]);
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
	 * Called by FileSystem adapter to get the start folder
	 * for browsing directories
	 */
	protected File getFilesystemBrowseStart() {
		SharedPreferences prefs = PlaybackService.getSettings(this);
		String folder = prefs.getString("filesystem_browse_start", "");
		File fs_start = new File( folder.equals("") ? Environment.getExternalStorageDirectory().getAbsolutePath() : folder );
		return fs_start;
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
	 * Called when the song timeline position/size has changed.
	 */
	public void onPositionInfoChanged()
	{
	}

	/**
	 * Called when the content of the media store has changed.
	 */
	public void onMediaChange()
	{
	}

	/**
	 * Called when the timeline change has changed.
	 */
	public void onTimelineChanged()
	{
	}

	static final int MENU_SORT = 1;
	static final int MENU_PREFS = 2;
	static final int MENU_LIBRARY = 3;
	static final int MENU_PLAYBACK = 5;
	static final int MENU_SEARCH = 7;
	static final int MENU_ENQUEUE_ALBUM = 8;
	static final int MENU_ENQUEUE_ARTIST = 9;
	static final int MENU_ENQUEUE_GENRE = 10;
	static final int MENU_CLEAR_QUEUE = 11;
	static final int MENU_SONG_FAVORITE = 12;
	static final int MENU_SHOW_QUEUE = 13;
	static final int MENU_SAVE_AS_PLAYLIST = 14;
	static final int MENU_DELETE = 15;

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
			break;
		case MENU_CLEAR_QUEUE:
			PlaybackService.get(this).clearQueue();
			break;
		default:
			return false;
		}
		return true;
	}

	/**
	 * Call addToPlaylist with the results from a NewPlaylistDialog stored in
	 * obj.
	 */
	protected static final int MSG_NEW_PLAYLIST = 0;
	/**
	 * Call renamePlaylist with the results from a NewPlaylistDialog stored in
	 * obj.
	 */
	protected static final int MSG_RENAME_PLAYLIST = 1;
	/**
	 * Call addToPlaylist with data from the playlisttask object.
	 */
	protected static final int MSG_ADD_TO_PLAYLIST = 2;

	protected static final int MSG_DELETE = 3;

	@Override
	public boolean handleMessage(Message message)
	{
		switch (message.what) {
		case MSG_NEW_PLAYLIST: {
			NewPlaylistDialog dialog = (NewPlaylistDialog)message.obj;
			if (dialog.isAccepted()) {
				String name = dialog.getText();
				long playlistId = Playlist.createPlaylist(getContentResolver(), name);
				PlaylistTask playlistTask = dialog.getPlaylistTask();
				playlistTask.name = name;
				playlistTask.playlistId = playlistId;
				mHandler.sendMessage(mHandler.obtainMessage(MSG_ADD_TO_PLAYLIST, playlistTask));
			}
			break;
		}
		case MSG_ADD_TO_PLAYLIST: {
			PlaylistTask playlistTask = (PlaylistTask)message.obj;
			addToPlaylist(playlistTask);
			break;
		}
		case MSG_RENAME_PLAYLIST: {
			NewPlaylistDialog dialog = (NewPlaylistDialog)message.obj;
			if (dialog.isAccepted()) {
				long playlistId = dialog.getPlaylistTask().playlistId;
				Playlist.renamePlaylist(getContentResolver(), playlistId, dialog.getText());
			}
			break;
		}
		case MSG_DELETE: {
			delete((Intent)message.obj);
			break;
		}
		default:
			return false;
		}
		return true;
	}

	/**
	 * Add a set of songs represented by the playlistTask to a playlist. Displays a
	 * Toast notifying of success.
	 *
	 * @param playlistTask The pending PlaylistTask to execute
	 */
	protected void addToPlaylist(PlaylistTask playlistTask) {
		int count = 0;

		if (playlistTask.query != null) {
			count += Playlist.addToPlaylist(getContentResolver(), playlistTask.playlistId, playlistTask.query);
		}

		if (playlistTask.audioIds != null) {
			count += Playlist.addToPlaylist(getContentResolver(), playlistTask.playlistId, playlistTask.audioIds);
		}

		String message = getResources().getQuantityString(R.plurals.added_to_playlist, count, count, playlistTask.name);
		Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
	}

	/**
	 * Delete the media represented by the given intent and show a Toast
	 * informing the user of this.
	 *
	 * @param intent An intent created with
	 * {@link LibraryAdapter#createData(View)}.
	 */
	private void delete(Intent intent)
	{
		int type = intent.getIntExtra("type", MediaUtils.TYPE_INVALID);
		long id = intent.getLongExtra("id", LibraryAdapter.INVALID_ID);
		String message = null;
		Resources res = getResources();

		if (type == MediaUtils.TYPE_FILE) {
			String file = intent.getStringExtra("file");
			boolean success = MediaUtils.deleteFile(new File(file));
			if (!success) {
				message = res.getString(R.string.delete_file_failed, file);
			}
		} else if (type == MediaUtils.TYPE_PLAYLIST) {
			Playlist.deletePlaylist(getContentResolver(), id);
		} else {
			int count = PlaybackService.get(this).deleteMedia(type, id);
			message = res.getQuantityString(R.plurals.deleted, count, count);
		}

		if (message == null) {
			message = res.getString(R.string.deleted_item, intent.getStringExtra("title"));
		}

		Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
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
	 *
	 * @param song If non-null, will open the library focused on this song.
	 */
	public void openLibrary(Song song)
	{
		Intent intent = new Intent(this, LibraryActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		if (song != null) {
			intent.putExtra("albumId", song.albumId);
			intent.putExtra("album", song.album);
			intent.putExtra("artist", song.artist);
		}
		startActivity(intent);
	}

	@Override
	public void upSwipe()
	{
		PlaybackService.get(this).performAction(mUpAction, this);
	}

	@Override
	public void downSwipe()
	{
		PlaybackService.get(this).performAction(mDownAction, this);
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
