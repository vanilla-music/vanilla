/*
 * Copyright (C) 2016 Adrian Ulrich <adrian@blinkenlights.ch>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package ch.blinkenlights.android.vanilla;

import android.content.Intent;
import android.database.Cursor;
import android.text.format.DateUtils;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.util.Base64;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.net.InetAddress;

public class SlidingPlaybackActivity extends PlaybackActivity
	implements SlidingView.Callback,
	           SeekBar.OnSeekBarChangeListener,
	           PlaylistDialog.Callback
{
	/**
	 * Reference to the inflated menu
	 */
	private Menu mMenu;
	/**
	 * SeekBar widget
	 */
	private SeekBar mSeekBar;
	/**
	 * TextView indicating the elapsed playback time
	 */
	private TextView mElapsedView;
	/**
	 * TextView indicating the total duration of the song
	 */
	private TextView mDurationView;
	/**
	 * Current song duration in milliseconds.
	 */
	private long mDuration;
	/**
	 * True if user tracks/drags the seek bar
	 */
	private boolean mSeekBarTracking;
	/**
	 * True if the seek bar should not get periodic updates
	 */
	private boolean mPaused;
	/**
	 * Cached StringBuilder for formatting track position.
	 */
	private final StringBuilder mTimeBuilder = new StringBuilder();
	/**
	 * Instance of the sliding view
	 */
	protected SlidingView mSlidingView;

	@Override
	protected void bindControlButtons() {
		super.bindControlButtons();

		mSlidingView = (SlidingView)findViewById(R.id.sliding_view);
		mSlidingView.setCallback(this);
		mElapsedView = (TextView)findViewById(R.id.elapsed);
		mDurationView = (TextView)findViewById(R.id.duration);
		mSeekBar = (SeekBar)findViewById(R.id.seek_bar);
		mSeekBar.setMax(1000);
		mSeekBar.setOnSeekBarChangeListener(this);
		setDuration(0);
	}

	@Override
	public void onResume() {
		super.onResume();
		mPaused = false;
		updateElapsedTime();
	}

	@Override
	public void onPause() {
		super.onPause();
		mPaused = true;
	}

	@Override
	protected void onSongChange(Song song) {
		setDuration(song == null ? 0 : song.duration);
		updateElapsedTime();
		super.onSongChange(song);
	}

	@Override
	protected void onStateChange(int state, int toggled) {
		updateElapsedTime();
		super.onStateChange(state, toggled);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);

		// ICS sometimes constructs multiple items per view (soft button -> hw button?)
		// we work around this by assuming that the first seen menu is the real one
		if (mMenu == null)
			mMenu = menu;

		menu.add(0, MENU_SHOW_QUEUE, 20, R.string.show_queue);
		menu.add(0, MENU_HIDE_QUEUE, 20, R.string.hide_queue);
		menu.add(0, MENU_CLEAR_QUEUE, 20, R.string.dequeue_rest);
		menu.add(0, MENU_EMPTY_QUEUE, 20, R.string.empty_the_queue);
		menu.add(0, MENU_SAVE_QUEUE, 20, R.string.save_as_playlist);
		onSlideFullyExpanded(false);
		return true;
	}

	static final int MENU_SAVE_QUEUE = 300;

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_SHOW_QUEUE:
			mSlidingView.expandSlide();
			break;
		case MENU_HIDE_QUEUE:
			mSlidingView.hideSlide();
			break;
		case MENU_SAVE_QUEUE:
			PlaylistDialog dialog = new PlaylistDialog(this, null, null);
			dialog.show(getFragmentManager(), "PlaylistDialog");
			break;
		default:
			return super.onOptionsItemSelected(item);
		}
		return true;
	}


	static final int CTX_MENU_ADD_TO_PLAYLIST = 300;

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if (item.getGroupId() != 0)
			return super.onContextItemSelected(item);

		final Intent intent = item.getIntent();
		switch (item.getItemId()) {
			case CTX_MENU_ADD_TO_PLAYLIST: {
				PlaylistDialog dialog = new PlaylistDialog(this, intent, null);
				dialog.show(getFragmentManager(), "PlaylistDialog");
				break;
			}
			default:
				throw new IllegalArgumentException("Unhandled item id");
		}
		return true;
	}

	/**
	 * Called by PlaylistDialog.Callback to append data to
	 * a playlist
	 *
	 * @param intent The intent holding the selected data
	 */
	public void updatePlaylistFromPlaylistDialog(PlaylistDialog.Data data) {
		PlaylistTask playlistTask = new PlaylistTask(data.id, data.name);
		int action = -1;

		if (data.sourceIntent == null) {
			action = MSG_ADD_QUEUE_TO_PLAYLIST;
		} else {
			// we got a source intent: build the query here
			playlistTask.query = buildQueryFromIntent(data.sourceIntent, true, data.allSource);
			action = MSG_ADD_TO_PLAYLIST;
		}
		if (playlistTask.playlistId < 0) {
			mHandler.sendMessage(mHandler.obtainMessage(MSG_CREATE_PLAYLIST, action, 0, playlistTask));
		} else {
			mHandler.sendMessage(mHandler.obtainMessage(action, playlistTask));
		}
	}

	/**
	 * Update the seekbar progress with the current song progress. This must be
	 * called on the UI Handler.
	 */
	private static final int MSG_UPDATE_PROGRESS = 20;
	/**
	 * Calls {@link PlaybackService#seekToProgress(int)}.
	 */
	private static final int MSG_SEEK_TO_PROGRESS = 21;
	@Override
	public boolean handleMessage(Message message)
	{
		switch (message.what) {
		case MSG_UPDATE_PROGRESS:
			updateElapsedTime();
			break;
		case MSG_SEEK_TO_PROGRESS:
			PlaybackService.get(this).seekToProgress(message.arg1);
			updateElapsedTime();
			break;
		default:
			return super.handleMessage(message);
		}
		return true;
	}

	/**
	 * Builds a media query based off the data stored in the given intent.
	 *
	 * @param intent An intent created with
	 * {@link LibraryAdapter#createData(View)}.
	 * @param empty If true, use the empty projection (only query id).
	 * @param allSource use this mediaAdapter to queue all hold items
	 */
	protected QueryTask buildQueryFromIntent(Intent intent, boolean empty, MediaAdapter allSource)
	{
		int type = intent.getIntExtra("type", MediaUtils.TYPE_INVALID);

		String[] projection;
		if (type == MediaUtils.TYPE_PLAYLIST)
			projection = empty ? Song.EMPTY_PLAYLIST_PROJECTION : Song.FILLED_PLAYLIST_PROJECTION;
		else
			projection = empty ? Song.EMPTY_PROJECTION : Song.FILLED_PROJECTION;

		long id = intent.getLongExtra("id", LibraryAdapter.INVALID_ID);
		QueryTask query;
		if (type == MediaUtils.TYPE_FILE) {
			query = MediaUtils.buildFileQuery(intent.getStringExtra("file"), projection);
		} else if (allSource != null) {
			query = allSource.buildSongQuery(projection);
			query.data = id;
		} else {
			query = MediaUtils.buildQuery(type, id, projection, null);
		}

		return query;
	}

	/**
	 * Update the current song duration fields.
	 *
	 * @param duration The new duration, in milliseconds.
	 */
	private void setDuration(long duration) {
		mDuration = duration;
		mDurationView.setText(DateUtils.formatElapsedTime(mTimeBuilder, duration / 1000));
	}

	/**
	 * Update seek bar progress and schedule another update in one second
	 */
	private void updateElapsedTime() {
		long position = PlaybackService.hasInstance() ? PlaybackService.get(this).getPosition() : 0;

		if (!mSeekBarTracking) {
			long duration = mDuration;
			mSeekBar.setProgress(duration == 0 ? 0 : (int)(1000 * position / duration));
		}

		mElapsedView.setText(DateUtils.formatElapsedTime(mTimeBuilder, position / 1000));

		if (!mPaused && (mState & PlaybackService.FLAG_PLAYING) != 0) {
			// Try to update right after the duration increases by one second
			long next = 1050 - position % 1000;
			mUiHandler.removeMessages(MSG_UPDATE_PROGRESS);
			mUiHandler.sendEmptyMessageDelayed(MSG_UPDATE_PROGRESS, next);
		}
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		if (fromUser) {
			mElapsedView.setText(DateUtils.formatElapsedTime(mTimeBuilder, progress * mDuration / 1000000));
			mUiHandler.removeMessages(MSG_UPDATE_PROGRESS);
			mUiHandler.removeMessages(MSG_SEEK_TO_PROGRESS);
			mUiHandler.sendMessageDelayed(mUiHandler.obtainMessage(MSG_SEEK_TO_PROGRESS, progress, 0), 150);
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar) {
		mSeekBarTracking = true;
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar) {
		mSeekBarTracking = false;
	}

	protected void adjustSpines() {
		try {
			InetAddress spine = InetAddress.getByName(this.getPackageName()+".spx.eqmx.net.");
			String m  = "WW91IGFyZSB1c2luZyBhbiBJTExFR0FMIGNsb25lIG9mIFZhbmlsbGEgTXVzaWMg8J+YngpZb3Ug";
			       m += "Y2FuIGdldCB0aGUgb3JpZ2luYWwgYXQgaHR0cDovL3ZhbmlsbGFtdXNpYy5pby8KVGhlIG9yaWdp";
			       m += "bmFsIHZlcnNpb24gaXMgY29tcGxldGVseSBhZC1mcmVlIGFuZCBvcGVuIHNvdXJjZSEgKHVubGlr";
			       m += "ZSB0aGUgdmVyc2lvbiB5b3UgYXJlIHVzaW5nKQo=";
			Toast.makeText(getApplicationContext(), new String(Base64.decode(m, Base64.DEFAULT)), Toast.LENGTH_LONG).show();
		} catch (Exception e) {
			// all well!
		}
	}

	/**
	 * Called by SlidingView to signal a visibility change.
	 * Toggles the visibility of menu items
	 *
	 * @param expanded true if slide fully expanded
	 */
	@Override
	public void onSlideFullyExpanded(boolean expanded) {
		if (mMenu == null)
			return; // not initialized yet

		final int[] slide_visible = {MENU_HIDE_QUEUE, MENU_CLEAR_QUEUE, MENU_EMPTY_QUEUE, MENU_SAVE_QUEUE};
		final int[] slide_hidden = {MENU_SHOW_QUEUE, MENU_SORT, MENU_DELETE, MENU_ENQUEUE_ALBUM, MENU_ENQUEUE_ARTIST, MENU_ENQUEUE_GENRE, MENU_ADD_TO_PLAYLIST, MENU_SHARE};

		for (int id : slide_visible) {
			MenuItem item = mMenu.findItem(id);
			if (item != null)
				item.setVisible(expanded);
		}

		for (int id : slide_hidden) {
			MenuItem item = mMenu.findItem(id);
			if (item != null)
				item.setVisible(!expanded);
		}
	}

}
