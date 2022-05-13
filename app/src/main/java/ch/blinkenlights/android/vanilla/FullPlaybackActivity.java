/*
 * Copyright (C) 2012 Christopher Eby <kreed@kreed.org>
 * Copyright (C) 2016-2019 Adrian Ulrich <adrian@blinkenlights.ch>
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

import ch.blinkenlights.android.medialibrary.MediaMetadataExtractor;

import java.util.ArrayList;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.app.AlertDialog;
import android.content.DialogInterface;

/**
 * The primary playback screen with playback controls and large cover display.
 */
public class FullPlaybackActivity extends SlidingPlaybackActivity
	implements View.OnLongClickListener
{
	public static final int DISPLAY_INFO_OVERLAP = 0;
	public static final int DISPLAY_INFO_BELOW = 1;
	public static final int DISPLAY_INFO_WIDGETS = 2;

	private TextView mOverlayText;

	private TableLayout mInfoTable;
	private TextView mQueuePosView;

	private TextView mTitle;
	private TextView mAlbum;
	private TextView mArtist;

	/**
	 * True if the controls are visible (play, next, seek bar, etc).
	 */
	private boolean mControlsVisible;
	/**
	 * True if the extra info is visible.
	 */
	private boolean mExtraInfoVisible;

	/**
	 * The current display mode, which determines layout and cover render style.
	 */
	private int mDisplayMode;

	private Action mCoverPressAction;
	private Action mCoverLongPressAction;

	/**
	 * The currently playing song.
	 */
	private Song mCurrentSong;

	private String mGenre;
	private TextView mGenreView;
	private String mTrack;
	private TextView mTrackView;
	private String mYear;
	private TextView mYearView;
	private String mComposer;
	private TextView mComposerView;
	private String mPath;
	private TextView mPathView;
	private String mFormat;
	private TextView mFormatView;
	private String mReplayGain;
	private TextView mReplayGainView;
	private MenuItem mFavorites;

	@Override
	public void onCreate(Bundle icicle)
	{
		ThemeHelper.setTheme(this, R.style.Playback);
		super.onCreate(icicle);

		setTitle(R.string.playback_view);

		SharedPreferences settings = SharedPrefHelper.getSettings(this);
		int displayMode = Integer.parseInt(settings.getString(PrefKeys.DISPLAY_MODE, PrefDefaults.DISPLAY_MODE));
		mDisplayMode = displayMode;

		int layout = R.layout.full_playback;
		int coverStyle;

		switch (displayMode) {
		default:
			Log.w("VanillaMusic", "Invalid display mode given. Defaulting to widget mode.");
			// fall through
		case DISPLAY_INFO_WIDGETS:
			coverStyle = CoverBitmap.STYLE_NO_INFO;
			layout = R.layout.full_playback_alt;
			break;
		case DISPLAY_INFO_OVERLAP:
			coverStyle = CoverBitmap.STYLE_OVERLAPPING_BOX;
			break;
		case DISPLAY_INFO_BELOW:
			coverStyle = CoverBitmap.STYLE_INFO_BELOW;
			break;
		}

		setContentView(layout);

		CoverView coverView = (CoverView)findViewById(R.id.cover_view);
		coverView.setup(mLooper, this, coverStyle);
		coverView.setOnClickListener(this);
		coverView.setOnLongClickListener(this);
		mCoverView = coverView;

		TableLayout table = (TableLayout)findViewById(R.id.info_table);
		if (table != null) {
			table.setOnClickListener(this);
			table.setOnLongClickListener(this);
			mInfoTable = table;
		}

		mTitle = (TextView)findViewById(R.id.title);
		mAlbum = (TextView)findViewById(R.id.album);
		mArtist = (TextView)findViewById(R.id.artist);

		mQueuePosView = (TextView)findViewById(R.id.queue_pos);

		mGenreView = (TextView)findViewById(R.id.genre);
		mTrackView = (TextView)findViewById(R.id.track);
		mYearView = (TextView)findViewById(R.id.year);
		mComposerView = (TextView)findViewById(R.id.composer);
		mPathView = (TextView)findViewById(R.id.path);
		mFormatView = (TextView)findViewById(R.id.format);
		mReplayGainView = (TextView)findViewById(R.id.replaygain);

		bindControlButtons();

		setControlsVisible(settings.getBoolean(PrefKeys.VISIBLE_CONTROLS, PrefDefaults.VISIBLE_CONTROLS));
		setExtraInfoVisible(settings.getBoolean(PrefKeys.VISIBLE_EXTRA_INFO, PrefDefaults.VISIBLE_EXTRA_INFO));
	}

	@Override
	public void onStart()
	{
		super.onStart();

		SharedPreferences settings = SharedPrefHelper.getSettings(this);
		if (mDisplayMode != Integer.parseInt(settings.getString(PrefKeys.DISPLAY_MODE, PrefDefaults.DISPLAY_MODE))) {
			finish();
			startActivity(new Intent(this, FullPlaybackActivity.class));
		}

		mCoverPressAction = Action.getAction(settings, PrefKeys.COVER_PRESS_ACTION, PrefDefaults.COVER_PRESS_ACTION);
		mCoverLongPressAction = Action.getAction(settings, PrefKeys.COVER_LONGPRESS_ACTION, PrefDefaults.COVER_LONGPRESS_ACTION);
	}

	/**
	 * Hide the message overlay, if it exists.
	 */
	private void hideMessageOverlay()
	{
		if (mOverlayText != null)
			mOverlayText.setVisibility(View.GONE);
	}

	/**
	 * Show some text in a message overlay.
	 *
	 * @param text Resource id of the text to show.
	 */
	private void showOverlayMessage(int text)
	{
		if (mOverlayText == null) {
			TextView view = new TextView(this);
			// This will be drawn on top of all other controls, so we flood this view
			// with a non-alpha color
			view.setBackgroundColor(ThemeHelper.fetchThemeColor(this, android.R.attr.colorBackground));
			view.setGravity(Gravity.CENTER);
			view.setPadding(25, 25, 25, 25);
			// Make the view clickable so it eats touch events
			view.setClickable(true);
			view.setOnClickListener(this);
			addContentView(view,
					new ViewGroup.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
							LinearLayout.LayoutParams.MATCH_PARENT));
			mOverlayText = view;
		} else {
			mOverlayText.setVisibility(View.VISIBLE);
		}

		mOverlayText.setText(text);
	}

	@Override
	protected void onStateChange(int state, int toggled)
	{
		super.onStateChange(state, toggled);

		if ((toggled & (PlaybackService.FLAG_NO_MEDIA|PlaybackService.FLAG_EMPTY_QUEUE)) != 0) {
			if ((state & PlaybackService.FLAG_NO_MEDIA) != 0) {
				showOverlayMessage(R.string.no_songs);
			} else if ((state & PlaybackService.FLAG_EMPTY_QUEUE) != 0) {
				showOverlayMessage(R.string.empty_queue);
			} else {
				hideMessageOverlay();
			}
		}

		if (mQueuePosView != null)
			updateQueuePosition();
	}

	@Override
	protected void onSongChange(Song song) {
		if (mTitle != null) {
			if (song == null) {
				mTitle.setText(null);
				mAlbum.setText(null);
				mArtist.setText(null);
			} else {
				mTitle.setText(song.title);
				mAlbum.setText(song.album);
				mArtist.setText(song.albumArtist);
			}
			updateQueuePosition();
		}

		mCurrentSong = song;

		mHandler.sendEmptyMessage(MSG_LOAD_FAVOURITE_INFO);

		// All quick UI updates are done: Time to update the cover
		// and parse additional info
		if (mExtraInfoVisible) {
			mHandler.sendEmptyMessage(MSG_LOAD_EXTRA_INFO);
		}
		super.onSongChange(song);
	}

	/**
	 * Update the queue position display. mQueuePos must not be null.
	 */
	private void updateQueuePosition()
	{
		if (PlaybackService.finishAction(mState) == SongTimeline.FINISH_RANDOM) {
			// Not very useful in random mode; it will always show something
			// like 11/13 since the timeline is trimmed to 10 previous songs.
			// So just hide it.
			mQueuePosView.setText(null);
		} else {
			PlaybackService service = PlaybackService.get(this);
			mQueuePosView.setText((service.getTimelinePosition() + 1) + "/" + service.getTimelineLength());
		}
		mQueuePosView.requestLayout(); // ensure queue pos column has enough room
	}

	@Override
	public void onPositionInfoChanged()
	{
		if (mQueuePosView != null)
			mUiHandler.sendEmptyMessage(MSG_UPDATE_POSITION);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		menu.add(0, MENU_DELETE, 30, R.string.delete);
		SubMenu enqueueMenu = menu.addSubMenu(0, MENU_ENQUEUE, 30, R.string.enqueue_current);
		SubMenu moreMenu = menu.addSubMenu(0, MENU_MORE, 30, R.string.more_from_current);
		menu.addSubMenu(0, MENU_ADD_TO_PLAYLIST, 30, R.string.add_to_playlist);
		menu.add(0, MENU_SHARE, 30, R.string.share);

		if (PluginUtils.checkPlugins(this)) {
			menu.add(0, MENU_PLUGINS, 30, R.string.plugins)
				.setIcon(R.drawable.plugin)
				.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM);
		}

		mFavorites = menu.add(0, MENU_SONG_FAVORITE, 0, R.string.add_to_favorites)
			.setIcon(R.drawable.btn_rating_star_off_mtrl_alpha)
			.setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM);

		// Subitems of 'enqueue...'
		enqueueMenu.add(0, MENU_ENQUEUE_ALBUM, 30, R.string.album);
		enqueueMenu.add(0, MENU_ENQUEUE_ARTIST, 30, R.string.artist);
		enqueueMenu.add(0, MENU_ENQUEUE_GENRE, 30, R.string.genre);

		// Subitems of 'more from...'
		moreMenu.add(0, MENU_MORE_ALBUM, 30, R.string.album);
		moreMenu.add(0, MENU_MORE_ARTIST, 30, R.string.artist);
		moreMenu.add(0, MENU_MORE_GENRE, 30, R.string.genre);
		moreMenu.add(0, MENU_MORE_FOLDER, 30, R.string.folder);

		// ensure that mFavorites is updated
		mHandler.sendEmptyMessage(MSG_LOAD_FAVOURITE_INFO);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		final Song song = mCurrentSong;

		switch (item.getItemId()) {
		case android.R.id.home:
			openLibrary(null, -1);
			break;
		case MENU_MORE_ALBUM:
			openLibrary(song, MediaUtils.TYPE_ALBUM);
			break;
		case MENU_MORE_ARTIST:
			openLibrary(song, MediaUtils.TYPE_ARTIST);
			break;
		case MENU_MORE_GENRE:
			openLibrary(song, MediaUtils.TYPE_GENRE);
			break;
		case MENU_MORE_FOLDER:
			openLibrary(song, MediaUtils.TYPE_FILE);
			break;
		case MENU_ENQUEUE_ALBUM:
			PlaybackService.get(this).enqueueFromSong(song, MediaUtils.TYPE_ALBUM);
			break;
		case MENU_ENQUEUE_ARTIST:
			PlaybackService.get(this).enqueueFromSong(song, MediaUtils.TYPE_ARTIST);
			break;
		case MENU_ENQUEUE_GENRE:
			PlaybackService.get(this).enqueueFromSong(song, MediaUtils.TYPE_GENRE);
			break;
		case MENU_SONG_FAVORITE:
			long playlistId = Playlist.getFavoritesId(this, true);
			if (song != null) {
				PlaylistTask playlistTask = new PlaylistTask(playlistId, getString(R.string.playlist_favorites));
				playlistTask.audioIds = new ArrayList<Long>();
				playlistTask.audioIds.add(song.id);
				int action = Playlist.isInPlaylist(this, playlistId, song) ? MSG_REMOVE_FROM_PLAYLIST : MSG_ADD_TO_PLAYLIST;
				mHandler.sendMessage(mHandler.obtainMessage(action, playlistTask));
			}
			break;
		case MENU_ADD_TO_PLAYLIST:
			if (song != null) {
				Intent intent = new Intent();
				intent.putExtra("type", MediaUtils.TYPE_SONG);
				intent.putExtra("id", song.id);
				PlaylistDialog dialog = PlaylistDialog.newInstance(this, intent, null);
				dialog.show(getFragmentManager(), "PlaylistDialog");
			}
			break;
		case MENU_SHARE:
			if (song != null)
				MediaUtils.shareMedia(this, song);
			break;
		case MENU_DELETE:
			final PlaybackService playbackService = PlaybackService.get(this);
			final PlaybackActivity activity = this;

			if (song != null) {
				String delete_message = getString(R.string.delete_file, song.title);
				AlertDialog.Builder dialog = new AlertDialog.Builder(this);
				dialog.setTitle(R.string.delete);
				dialog
					.setMessage(delete_message)
					.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
							// MSG_DELETE expects an intent (usually called from listview)
							Intent intent = new Intent();
							intent.putExtra(LibraryAdapter.DATA_TYPE, MediaUtils.TYPE_SONG);
							intent.putExtra(LibraryAdapter.DATA_ID, song.id);
							mHandler.sendMessage(mHandler.obtainMessage(MSG_DELETE, intent));
						}
					})
					.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int id) {
						}
					});
				dialog.create().show();
			}
			break;
			case MENU_PLUGINS:
				if (song != null) {
					Intent songIntent = new Intent();
					songIntent.putExtra("id", song.id);
					showPluginMenu(songIntent);
				}
				break;
		default:
			return super.onOptionsItemSelected(item);
		}

		return true;
	}

	@Override
	public boolean onSearchRequested()
	{
		openLibrary(null, -1);
		return false;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		switch (keyCode) {
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			shiftCurrentSong(SongTimeline.SHIFT_NEXT_SONG);
			findViewById(R.id.next).requestFocus();
			break;
		case KeyEvent.KEYCODE_DPAD_LEFT:
			shiftCurrentSong(SongTimeline.SHIFT_PREVIOUS_SONG);
			findViewById(R.id.previous).requestFocus();
			break;
		case KeyEvent.KEYCODE_SEARCH:
			Intent librarySearch = new Intent(this, LibraryActivity.class);
			librarySearch.putExtra("launch_search", true);
			startActivity(librarySearch);
			break;
		default:
			return super.onKeyDown(keyCode, event);
		}
		return true;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event)
	{
		switch (keyCode) {
			case KeyEvent.KEYCODE_DPAD_CENTER:
			case KeyEvent.KEYCODE_ENTER:
				setControlsVisible(!mControlsVisible);
				mHandler.sendEmptyMessage(MSG_SAVE_CONTROLS);
				return true;
			case KeyEvent.KEYCODE_BACK:
				if (mSlidingView.isShrinkable()) {
					mSlidingView.hideSlide();
					return true;
				}
		}

		return super.onKeyUp(keyCode, event);
	}

	/**
	 * Set the visibility of the controls views.
	 *
	 * @param visible True to show, false to hide
	 */
	private void setControlsVisible(boolean visible)
	{
		int mode = visible ? View.VISIBLE : View.GONE;
		mSlidingView.setVisibility(mode);
		mControlsVisible = visible;

		if (visible) {
			mPlayPauseButton.requestFocus();
		}
	}

	/**
	 * Set the visibility of the extra metadata view.
	 *
	 * @param visible True to show, false to hide
	 */
	private void setExtraInfoVisible(boolean visible)
	{
		TableLayout table = mInfoTable;
		if (table == null)
			return;

		table.setColumnCollapsed(0, !visible);
		// Make title, album, and artist multi-line when extra info is visible
		boolean singleLine = !visible;
		for (int i = 0; i != 3; ++i) {
			TableRow row = (TableRow)table.getChildAt(i);
			((TextView)row.getChildAt(1)).setSingleLine(singleLine);
		}
		// toggle visibility of all but the first three rows (the title/artist/
		// album rows)
		int visibility = visible ? View.VISIBLE : View.GONE;
		for (int i = table.getChildCount() - 1; i > 2 ; i--) {
			table.getChildAt(i).setVisibility(visibility);
		}
		mExtraInfoVisible = visible;
		if (visible && !mHandler.hasMessages(MSG_LOAD_EXTRA_INFO)) {
			mHandler.sendEmptyMessage(MSG_LOAD_EXTRA_INFO);
		}
	}

	/**
	 * Retrieve the extra metadata for the current song.
	 */
	private void loadExtraInfo()
	{
		Song song = mCurrentSong;

		mGenre = null;
		mTrack = null;
		mYear = null;
		mComposer = null;
		mPath = null;
		mFormat = null;
		mReplayGain = null;

		if(song != null) {
			MediaMetadataExtractor data = new MediaMetadataExtractor(song.path);

			mGenre = data.getFirst(MediaMetadataExtractor.GENRE);
			mTrack = song.getTrackAndDiscNumber();
			mComposer = data.getFirst(MediaMetadataExtractor.COMPOSER);
			mYear = data.getFirst(MediaMetadataExtractor.YEAR);
			mPath = song.path;

			mFormat = data.getFormat();

			BastpUtil.GainValues rg = PlaybackService.get(this).getReplayGainValues(song.path);
			mReplayGain = String.format("found=%s, track=%.2f, album=%.2f", rg.found, rg.track, rg.album);
		}

		mUiHandler.sendEmptyMessage(MSG_COMMIT_INFO);
	}

	/**
	 * Save the hidden_controls preference to storage.
	 */
	private static final int MSG_SAVE_CONTROLS = 10;
	/**
	 * Call {@link #loadExtraInfo()}.
	 */
	private static final int MSG_LOAD_EXTRA_INFO = 11;
	/**
	 * Pass obj to mExtraInfo.setText()
	 */
	private static final int MSG_COMMIT_INFO = 12;
	/**
	 * Calls {@link #updateQueuePosition()}.
	 */
	private static final int MSG_UPDATE_POSITION = 13;
	/**
	 * Check if passed song is a favorite
	 */
	private static final int MSG_LOAD_FAVOURITE_INFO = 14;
	/**
	 * Updates the favorites state
	 */
	private static final int MSG_COMMIT_FAVOURITE_INFO = 15;

	@Override
	public boolean handleMessage(Message message)
	{
		switch (message.what) {
		case MSG_SAVE_CONTROLS: {
			SharedPreferences.Editor editor = SharedPrefHelper.getSettings(this).edit();
			editor.putBoolean(PrefKeys.VISIBLE_CONTROLS, mControlsVisible);
			editor.putBoolean(PrefKeys.VISIBLE_EXTRA_INFO, mExtraInfoVisible);
			editor.apply();
			break;
		}
		case MSG_LOAD_EXTRA_INFO:
			loadExtraInfo();
			break;
		case MSG_COMMIT_INFO: {
			mGenreView.setText(mGenre);
			mTrackView.setText(mTrack);
			mYearView.setText(mYear);
			mComposerView.setText(mComposer);
			mPathView.setText(mPath);
			mFormatView.setText(mFormat);
			mReplayGainView.setText(mReplayGain);
			break;
		}
		case MSG_UPDATE_POSITION:
			updateQueuePosition();
			break;
		case MSG_NOTIFY_PLAYLIST_CHANGED: // triggers a fav-refresh
		case MSG_LOAD_FAVOURITE_INFO:
			if (mCurrentSong != null) {
				boolean found = Playlist.isInPlaylist(this, Playlist.getFavoritesId(this, false), mCurrentSong);
				mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_COMMIT_FAVOURITE_INFO, found));
			}
			break;
		case MSG_COMMIT_FAVOURITE_INFO:
			if (mFavorites != null) {
				boolean found = (boolean)message.obj;
				mFavorites.setIcon(found ? R.drawable.btn_rating_star_on_mtrl_alpha: R.drawable.btn_rating_star_off_mtrl_alpha);
				mFavorites.setTitle(found ? R.string.remove_from_favorites : R.string.add_to_favorites);
			}
			break;
		default:
			return super.handleMessage(message);
		}

		return true;
	}

	@Override
	protected void performAction(Action action) {
		switch (action) {
			case ToggleControls:
				setControlsVisible(!mControlsVisible);
				mHandler.sendEmptyMessage(MSG_SAVE_CONTROLS);
				break;
			case ShowQueue:
				mSlidingView.expandSlide();
				break;
			default:
				super.performAction(action);
		}
	}

	@Override
	public void onClick(View view)
	{
		if (view == mOverlayText && (mState & PlaybackService.FLAG_EMPTY_QUEUE) != 0) {
			setState(PlaybackService.get(this).setFinishAction(SongTimeline.FINISH_RANDOM));
		} else if (view == mCoverView) {
			performAction(mCoverPressAction);
		} else if (view.getId() == R.id.info_table) {
			openLibrary(mCurrentSong, MediaUtils.TYPE_ALBUM);
		} else {
			super.onClick(view);
		}
	}

	@Override
	public boolean onLongClick(View view)
	{
		switch (view.getId()) {
		case R.id.cover_view:
			performAction(mCoverLongPressAction);
			break;
		case R.id.info_table:
			setExtraInfoVisible(!mExtraInfoVisible);
			mHandler.sendEmptyMessage(MSG_SAVE_CONTROLS);
			break;
		default:
			return false;
		}

		return true;
	}

	@Override
	public void onSlideExpansionChanged(int expansion) {
		super.onSlideExpansionChanged(expansion);

		setControlsVisible(true);
		if (expansion != SlidingView.EXPANSION_PARTIAL) {
			setExtraInfoVisible(false);
		} else {
			SharedPreferences settings = SharedPrefHelper.getSettings(this);
			setExtraInfoVisible(settings.getBoolean(PrefKeys.VISIBLE_EXTRA_INFO, PrefDefaults.VISIBLE_EXTRA_INFO));
		}
	}

}
