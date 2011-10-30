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

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.PaintDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;
import junit.framework.Assert;

/**
 * The library activity where songs to play can be selected from the library.
 */
public class LibraryActivity
	extends PlaybackActivity
	implements AdapterView.OnItemClickListener
	         , TextWatcher
	         , TabHost.OnTabChangeListener
	         , DialogInterface.OnClickListener
	         , DialogInterface.OnDismissListener
{
	private static final int ACTION_PLAY = 0;
	private static final int ACTION_ENQUEUE = 1;
	private static final int ACTION_LAST_USED = 2;
	private static final int ACTION_PLAY_ALL = 3;
	private static final int ACTION_ENQUEUE_ALL = 4;
	private static final int[] modeForAction =
		{ SongTimeline.MODE_PLAY, SongTimeline.MODE_ENQUEUE, -1,
		  SongTimeline.MODE_PLAY_ID_FIRST, SongTimeline.MODE_ENQUEUE_ID_FIRST };

	private TabHost mTabHost;

	private View mSearchBox;
	private boolean mSearchBoxVisible;
	private TextView mTextFilter;
	private View mClearButton;

	private View mControls;
	private TextView mTitle;
	private TextView mArtist;
	private ImageView mCover;
	private View mEmptyQueue;

	private ViewGroup mLimiterViews;

	private int mDefaultAction;

	private int mLastAction = ACTION_PLAY;
	private long mLastActedId;

	private MediaAdapter[] mAdapters;
	private MediaAdapter mArtistAdapter;
	private MediaAdapter mAlbumAdapter;
	private MediaAdapter mSongAdapter;
	private MediaAdapter mPlaylistAdapter;
	private MediaAdapter mGenreAdapter;
	private MediaAdapter mCurrentAdapter;

	private final ContentObserver mPlaylistObserver = new ContentObserver(null) {
		@Override
		public void onChange(boolean selfChange)
		{
			mUiHandler.sendMessage(mUiHandler.obtainMessage(MSG_REQUEST_REQUERY, mPlaylistAdapter));
		}
	};

	@Override
	public void onCreate(Bundle state)
	{
		super.onCreate(state);

		MediaView.init(this);
		setContentView(R.layout.library_content);

		SharedPreferences settings = PlaybackService.getSettings(this);
		if (settings.getBoolean("controls_in_selector", false)) {
			ViewGroup content = (ViewGroup)findViewById(R.id.content);
			LayoutInflater.from(this).inflate(R.layout.library_controls, content, true);

			mControls = findViewById(R.id.controls);

			mTitle = (TextView)mControls.findViewById(R.id.title);
			mArtist = (TextView)mControls.findViewById(R.id.artist);
			mCover = (ImageView)mControls.findViewById(R.id.cover);
			View previous = mControls.findViewById(R.id.previous);
			mPlayPauseButton = (ImageButton)mControls.findViewById(R.id.play_pause);
			View next = mControls.findViewById(R.id.next);

			mCover.setOnClickListener(this);
			previous.setOnClickListener(this);
			mPlayPauseButton.setOnClickListener(this);
			next.setOnClickListener(this);

			mShuffleButton = (ImageButton)findViewById(R.id.shuffle);
			mShuffleButton.setOnClickListener(this);
			registerForContextMenu(mShuffleButton);
			mEndButton = (ImageButton)findViewById(R.id.end_action);
			mEndButton.setOnClickListener(this);
			registerForContextMenu(mEndButton);

			mEmptyQueue = findViewById(R.id.empty_queue);
			mEmptyQueue.setOnClickListener(this);
		}

		mSearchBox = findViewById(R.id.search_box);

		mTextFilter = (TextView)findViewById(R.id.filter_text);
		mTextFilter.addTextChangedListener(this);

		mClearButton = findViewById(R.id.clear_button);
		mClearButton.setOnClickListener(this);

		mLimiterViews = (ViewGroup)findViewById(R.id.limiter_layout);

		mTabHost = (TabHost)findViewById(R.id.tab_host);
		mTabHost.setup();

		mArtistAdapter = setupView(R.id.artist_list, MediaUtils.TYPE_ARTIST, R.string.artists, R.drawable.ic_tab_artists, true, true, null);
		mAlbumAdapter = setupView(R.id.album_list, MediaUtils.TYPE_ALBUM, R.string.albums, R.drawable.ic_tab_albums, true, true, state == null ? null : (MediaAdapter.Limiter)state.getSerializable("limiter_albums"));
		mSongAdapter = setupView(R.id.song_list, MediaUtils.TYPE_SONG, R.string.songs, R.drawable.ic_tab_songs, false, true, state == null ? null : (MediaAdapter.Limiter)state.getSerializable("limiter_songs"));
		mPlaylistAdapter = setupView(R.id.playlist_list, MediaUtils.TYPE_PLAYLIST, R.string.playlists, R.drawable.ic_tab_playlists, true, false, null);
		mGenreAdapter = setupView(R.id.genre_list, MediaUtils.TYPE_GENRE, R.string.genres, R.drawable.ic_tab_genres, true, false, state == null ? null : (MediaAdapter.Limiter)state.getSerializable("limiter_genres"));
		// These should be in the same order as MediaUtils.TYPE_*
		mAdapters = new MediaAdapter[] { mArtistAdapter, mAlbumAdapter, mSongAdapter, mPlaylistAdapter, mGenreAdapter };

		getContentResolver().registerContentObserver(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, true, mPlaylistObserver);

		int currentTab = 0;
		String filter = null;

		if (state != null) {
			if (state.getBoolean("search_box_visible"))
				setSearchBoxVisible(true);
			currentTab = state.getInt("current_tab", 0);
			filter = state.getString("filter");
		}

		mTabHost.setCurrentTab(currentTab);
		mTabHost.setOnTabChangedListener(this);
		onTabChanged(null);

		if (filter != null)
			mTextFilter.setText(filter);
	}

	@Override
	public void onStart()
	{
		super.onStart();

		SharedPreferences settings = PlaybackService.getSettings(this);
		if (settings.getBoolean("controls_in_selector", false) != (mControls != null)) {
			finish();
			startActivity(new Intent(this, LibraryActivity.class));
		}
		mDefaultAction = Integer.parseInt(settings.getString("default_action_int", "0"));
		mLastActedId = -2;
		updateHeaders();
	}

	@Override
	protected void onSaveInstanceState(Bundle out)
	{
		out.putBoolean("search_box_visible", mSearchBoxVisible);
		out.putInt("current_tab", mTabHost.getCurrentTab());
		out.putString("filter", mTextFilter.getText().toString());
		out.putSerializable("limiter_albums", mAlbumAdapter.getLimiter());
		out.putSerializable("limiter_songs", mSongAdapter.getLimiter());
		out.putSerializable("limiter_genres", mGenreAdapter.getLimiter());
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event)
	{
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			if (mSearchBoxVisible) {
				mTextFilter.setText("");
				setSearchBoxVisible(false);
			} else {
				finish();
			}
			break;
		case KeyEvent.KEYCODE_SEARCH:
			setSearchBoxVisible(!mSearchBoxVisible);
			break;
		default:
			return false;
		}

		return true;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		if (super.onKeyDown(keyCode, event))
			return true;

		if (mTextFilter.onKeyDown(keyCode, event)) {
			if (!mSearchBoxVisible)
				setSearchBoxVisible(true);
			else
				mTextFilter.requestFocus();
			return true;
		}

		return false;
	}

	/**
	 * Update the first row of the lists with the appropriate action (play all
	 * or enqueue all).
	 */
	private void updateHeaders()
	{
		int action = mDefaultAction;
		if (action == ACTION_LAST_USED)
			action = mLastAction;

		int res = action == ACTION_ENQUEUE || action == ACTION_ENQUEUE_ALL ? R.string.enqueue_all : R.string.play_all;
		String text = getString(res);
		mArtistAdapter.setHeaderText(text);
		mAlbumAdapter.setHeaderText(text);
		mSongAdapter.setHeaderText(text);
	}

	/**
	 * Adds songs matching the data from the given intent to the song timelime.
	 *
	 * @param intent An intent created with
	 * {@link LibraryActivity#createClickIntent(MediaAdapter,MediaView)}.
	 * @param action One of LibraryActivity.ACTION_*
	 */
	private void pickSongs(Intent intent, int action)
	{
		if (action == ACTION_LAST_USED)
			action = mLastAction;

		long id = intent.getLongExtra("id", -1);

		boolean all = false;
		int mode = action;
		if (action == ACTION_PLAY_ALL || action == ACTION_ENQUEUE_ALL) {
			MediaAdapter adapter = mCurrentAdapter;
			boolean notPlayAllAdapter = (adapter != mSongAdapter && adapter != mAlbumAdapter
					&& adapter != mArtistAdapter) || id == MediaView.HEADER_ID;
			if (mode == ACTION_ENQUEUE_ALL && notPlayAllAdapter) {
				mode = ACTION_ENQUEUE;
			} else if (mode == ACTION_PLAY_ALL && notPlayAllAdapter) {
				mode = ACTION_PLAY;
			} else {
				all = true;
			}
		}
		mode = modeForAction[mode];

		QueryTask query = buildQueryFromIntent(intent, false, all);
		PlaybackService.get(this).addSongs(mode, query, intent.getIntExtra("type", -1));

		mLastActedId = id;

		if (mDefaultAction == ACTION_LAST_USED && mLastAction != action) {
			mLastAction = action;
			updateHeaders();
		}
	}

	/**
	 * "Expand" the view represented by the given intent by setting the limiter
	 * from the view and switching to the appropriate tab.
	 *
	 * @param intent An intent created with
	 * {@link LibraryActivity#createClickIntent(MediaAdapter,MediaView)}.
	 */
	private void expand(Intent intent)
	{
		int type = intent.getIntExtra("type", 1);
		long id = intent.getLongExtra("id", -1);
		mTabHost.setCurrentTab(setLimiter(mAdapters[type - 1].getLimiter(id)));
	}

	/**
	 * Update the adapters with the given limiter.
	 *
	 * @return The tab to "expand" to
	 */
	private int setLimiter(MediaAdapter.Limiter limiter)
	{
		int tab;

		if (limiter == null) {
			mAlbumAdapter.setLimiter(null);
			mSongAdapter.setLimiter(null);
			tab = -1;
		} else {
			switch (limiter.type) {
			case MediaUtils.TYPE_ALBUM:
				mSongAdapter.setLimiter(limiter);
				loadSortOrder(mSongAdapter);
				requestRequery(mSongAdapter);
				return 2;
			case MediaUtils.TYPE_ARTIST:
				mAlbumAdapter.setLimiter(limiter);
				mSongAdapter.setLimiter(limiter);
				tab = 1;
				break;
			case MediaUtils.TYPE_GENRE:
				mSongAdapter.setLimiter(limiter);
				mAlbumAdapter.setLimiter(null);
				tab = 2;
				break;
			default:
				throw new IllegalArgumentException("Unsupported limiter type: " + limiter.type);
			}
		}

		loadSortOrder(mSongAdapter);
		loadSortOrder(mAlbumAdapter);

		requestRequery(mSongAdapter);
		requestRequery(mAlbumAdapter);

		return tab;
	}

	/**
	 * Open the playback activity and close any activities above it in the
	 * stack.
	 */
	public void openPlaybackActivity()
	{
		Intent intent = new Intent(this, FullPlaybackActivity.class);
		intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);
	}

	@Override
	public void onItemClick(AdapterView<?> list, View view, int pos, long id)
	{
		MediaView mediaView = (MediaView)view;
		MediaAdapter adapter = (MediaAdapter)list.getAdapter();
		if (mediaView.isRightBitmapPressed()) {
			if (adapter == mPlaylistAdapter)
				editPlaylist(mediaView.getMediaId(), mediaView.getTitle());
			else
				expand(createClickIntent(adapter, mediaView));
		} else if (id == mLastActedId) {
			openPlaybackActivity();
		} else {
			pickSongs(createClickIntent(adapter, mediaView), mDefaultAction);
		}
	}

	@Override
	public void afterTextChanged(Editable editable)
	{
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after)
	{
	}

	@Override
	public void onTextChanged(CharSequence text, int start, int before, int count)
	{
		String filter = text.toString();
		for (MediaAdapter adapter : mAdapters) {
			adapter.setFilter(filter);
			requestRequery(adapter);
		}
	}

	private void updateLimiterViews()
	{
		if (mLimiterViews == null)
			return;

		mLimiterViews.removeAllViews();

		MediaAdapter adapter = mCurrentAdapter;
		if (adapter == null)
			return;

		MediaAdapter.Limiter limiterData = adapter.getLimiter();
		if (limiterData == null)
			return;
		String[] limiter = limiterData.names;

		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		params.leftMargin = 5;
		for (int i = 0; i != limiter.length; ++i) {
			PaintDrawable background = new PaintDrawable(Color.GRAY);
			background.setCornerRadius(5);

			TextView view = new TextView(this);
			view.setSingleLine();
			view.setEllipsize(TextUtils.TruncateAt.MARQUEE);
			view.setText(limiter[i] + " | X");
			view.setTextColor(Color.WHITE);
			view.setBackgroundDrawable(background);
			view.setLayoutParams(params);
			view.setPadding(5, 2, 5, 2);
			view.setTag(i);
			view.setOnClickListener(this);
			mLimiterViews.addView(view);
		}
	}

	@Override
	public void onClick(View view)
	{
		if (view == mClearButton) {
			if (mTextFilter.getText().length() == 0)
				setSearchBoxVisible(false);
			else
				mTextFilter.setText("");
		} else if (view == mCover) {
			openPlaybackActivity();
		} else if (view == mEmptyQueue) {
			setState(PlaybackService.get(this).setFinishAction(SongTimeline.FINISH_RANDOM));
		} else if (view.getTag() != null) {
			// a limiter view was clicked
			int i = (Integer)view.getTag();

			if (i == 1) {
				// generate the artist limiter (we need to query the artist id)
				MediaAdapter.Limiter limiter = mSongAdapter.getLimiter();
				Assert.assertEquals(MediaUtils.TYPE_ALBUM, limiter.type);

				ContentResolver resolver = getContentResolver();
				Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
				String[] projection = new String[] { MediaStore.Audio.Media.ARTIST_ID };
				Cursor cursor = resolver.query(uri, projection, limiter.selection, null, null);
				if (cursor != null) {
					if (cursor.moveToNext()) {
						setLimiter(mArtistAdapter.getLimiter(cursor.getLong(0)));
						updateLimiterViews();
						cursor.close();
						return;
					}
					cursor.close();
				}
			}

			setLimiter(null);
			updateLimiterViews();
		} else {
			super.onClick(view);
		}
	}

	/**
	 * Creates an intent based off of the media represented by the given view.
	 *
	 * @param adapter The adapter that owns the view.
	 * @param view The MediaView to build from.
	 */
	private static Intent createClickIntent(MediaAdapter adapter, MediaView view)
	{
		Intent intent = new Intent();
		intent.putExtra("type", adapter.getMediaType());
		intent.putExtra("id", view.getMediaId());
		intent.putExtra("title", view.getTitle());
		return intent;
	}

	/**
	 * Builds a media query based off the data stored in the given intent.
	 *
	 * @param intent An intent created with
	 * {@link LibraryActivity#createClickIntent(MediaAdapter,MediaView)}.
	 * @param empty If true, use the empty projection (only query id).
	 * @param all If true query all songs in the adapter; otherwise query based
	 * on the row selected.
	 */
	private QueryTask buildQueryFromIntent(Intent intent, boolean empty, boolean all)
	{
		int type = intent.getIntExtra("type", 1);

		String[] projection;
		if (type == MediaUtils.TYPE_PLAYLIST)
			projection = empty ? Song.EMPTY_PLAYLIST_PROJECTION : Song.FILLED_PLAYLIST_PROJECTION;
		else
			projection = empty ? Song.EMPTY_PROJECTION : Song.FILLED_PROJECTION;

		long id = intent.getLongExtra("id", -1);
		QueryTask query;
		if (all || id == MediaView.HEADER_ID) {
			query = mAdapters[type - 1].buildSongQuery(projection);
			query.setExtra(id);
		} else {
			query = MediaUtils.buildQuery(type, id, projection, null);
		}

		return query;
	}

	private static final int MENU_PLAY = 0;
	private static final int MENU_ENQUEUE = 1;
	private static final int MENU_EXPAND = 2;
	private static final int MENU_ADD_TO_PLAYLIST = 3;
	private static final int MENU_NEW_PLAYLIST = 4;
	private static final int MENU_DELETE = 5;
	private static final int MENU_EDIT = 6;
	private static final int MENU_RENAME_PLAYLIST = 7;
	private static final int MENU_SELECT_PLAYLIST = 8;
	private static final int MENU_PLAY_ALL = 9;
	private static final int MENU_ENQUEUE_ALL = 10;

	@Override
	public void onCreateContextMenu(ContextMenu menu, View listView, ContextMenu.ContextMenuInfo absInfo)
	{
		if (!(listView instanceof ListView)) {
			super.onCreateContextMenu(menu, listView, absInfo);
			return;
		}

		MediaAdapter adapter = (MediaAdapter)((ListView)listView).getAdapter();
		MediaView view = (MediaView)((AdapterView.AdapterContextMenuInfo)absInfo).targetView;

		// Store view data in intent to avoid problems when the view data changes
		// as worked is performed in the background.
		Intent intent = createClickIntent(adapter, view);

		boolean isHeader = view.getMediaId() == MediaView.HEADER_ID;
		boolean isAllAdapter = adapter == mArtistAdapter || adapter == mAlbumAdapter || adapter == mSongAdapter;

		if (isHeader)
			menu.setHeaderTitle(getString(R.string.all_songs));
		else
			menu.setHeaderTitle(view.getTitle());

		menu.add(0, MENU_PLAY, 0, R.string.play).setIntent(intent);
		if (isAllAdapter)
			menu.add(0, MENU_PLAY_ALL, 0, R.string.play_all).setIntent(intent);
		menu.add(0, MENU_ENQUEUE, 0, R.string.enqueue).setIntent(intent);
		if (isAllAdapter)
			menu.add(0, MENU_ENQUEUE_ALL, 0, R.string.enqueue_all).setIntent(intent);
		if (adapter == mPlaylistAdapter) {
			menu.add(0, MENU_RENAME_PLAYLIST, 0, R.string.rename).setIntent(intent);
			menu.add(0, MENU_EDIT, 0, R.string.edit).setIntent(intent);
		}
		menu.addSubMenu(0, MENU_ADD_TO_PLAYLIST, 0, R.string.add_to_playlist).getItem().setIntent(intent);
		if (adapter != mPlaylistAdapter && adapter != mSongAdapter)
			menu.add(0, MENU_EXPAND, 0, R.string.expand).setIntent(intent);
		if (!isHeader)
			menu.add(0, MENU_DELETE, 0, R.string.delete).setIntent(intent);
	}

	/**
	 * Add a set of songs represented by the intent to a playlist. Displays a
	 * Toast notifying of success.
	 *
	 * @param playlistId The id of the playlist to add to.
	 * @param intent An intent created with
	 * {@link LibraryActivity#createClickIntent(MediaAdapter,MediaView)}.
	 */
	private void addToPlaylist(long playlistId, Intent intent)
	{
		QueryTask query = buildQueryFromIntent(intent, true, false);
		int count = Playlist.addToPlaylist(getContentResolver(), playlistId, query);

		String message = getResources().getQuantityString(R.plurals.added_to_playlist, count, count, intent.getStringExtra("playlistName"));
		Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
	}

	/**
	 * Open the playlist editor for the playlist with the given id.
	 */
	private void editPlaylist(long playlistId, String title)
	{
		Intent launch = new Intent(this, PlaylistActivity.class);
		launch.putExtra("playlist", playlistId);
		launch.putExtra("title", title);
		startActivity(launch);
	}

	/**
	 * Delete the media represented by the given intent and show a Toast
	 * informing the user of this.
	 *
	 * @param intent An intent created with
	 * {@link LibraryActivity#createClickIntent(MediaAdapter,MediaView)}.
	 */
	private void delete(Intent intent)
	{
		int type = intent.getIntExtra("type", 1);
		long id = intent.getLongExtra("id", -1);

		if (type == MediaUtils.TYPE_PLAYLIST) {
			Playlist.deletePlaylist(getContentResolver(), id);
			String message = getResources().getString(R.string.playlist_deleted, intent.getStringExtra("title"));
			Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
		} else {
			int count = PlaybackService.get(this).deleteMedia(type, id);
			String message = getResources().getQuantityString(R.plurals.deleted, count, count);
			Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		if (item.getGroupId() != 0)
			return super.onContextItemSelected(item);

		Intent intent = item.getIntent();

		switch (item.getItemId()) {
		case MENU_EXPAND:
			expand(intent);
			break;
		case MENU_ENQUEUE:
			pickSongs(intent, ACTION_ENQUEUE);
			break;
		case MENU_PLAY:
			pickSongs(intent, ACTION_PLAY);
			break;
		case MENU_PLAY_ALL:
			pickSongs(intent, ACTION_PLAY_ALL);
			break;
		case MENU_ENQUEUE_ALL:
			pickSongs(intent, ACTION_ENQUEUE_ALL);
			break;
		case MENU_NEW_PLAYLIST: {
			NewPlaylistDialog dialog = new NewPlaylistDialog(this, null, R.string.create, intent);
			dialog.setDismissMessage(mHandler.obtainMessage(MSG_NEW_PLAYLIST, dialog));
			dialog.show();
			break;
		}
		case MENU_RENAME_PLAYLIST: {
			NewPlaylistDialog dialog = new NewPlaylistDialog(this, intent.getStringExtra("title"), R.string.rename, intent);
			dialog.setDismissMessage(mHandler.obtainMessage(MSG_RENAME_PLAYLIST, dialog));
			dialog.show();
			break;
		}
		case MENU_DELETE:
			mHandler.sendMessage(mHandler.obtainMessage(MSG_DELETE, intent));
			break;
		case MENU_ADD_TO_PLAYLIST: {
			SubMenu playlistMenu = item.getSubMenu();
			playlistMenu.add(0, MENU_NEW_PLAYLIST, 0, R.string.new_playlist).setIntent(intent);
			Cursor cursor = Playlist.queryPlaylists(getContentResolver());
			if (cursor != null) {
				for (int i = 0, count = cursor.getCount(); i != count; ++i) {
					cursor.moveToPosition(i);
					long id = cursor.getLong(0);
					String name = cursor.getString(1);
					Intent copy = new Intent(intent);
					copy.putExtra("playlist", id);
					copy.putExtra("playlistName", name);
					playlistMenu.add(0, MENU_SELECT_PLAYLIST, 0, name).setIntent(copy);
				}
				cursor.close();
			}
			break;
		}
		case MENU_EDIT:
			editPlaylist(intent.getLongExtra("id", 0), intent.getStringExtra("title"));
			break;
		case MENU_SELECT_PLAYLIST:
			mHandler.sendMessage(mHandler.obtainMessage(MSG_ADD_TO_PLAYLIST, intent));
			break;
		}

		return true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		menu.addSubMenu(0, MENU_SORT, 0, R.string.sort_by).setIcon(R.drawable.ic_menu_sort_alphabetically);
		menu.add(0, MENU_SEARCH, 0, R.string.search).setIcon(R.drawable.ic_menu_search);
		menu.add(0, MENU_PLAYBACK, 0, R.string.playback_view).setIcon(R.drawable.ic_menu_gallery);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId()) {
		case MENU_SEARCH:
			setSearchBoxVisible(!mSearchBoxVisible);
			return true;
		case MENU_PLAYBACK:
			openPlaybackActivity();
			return true;
		case MENU_SORT: {
			MediaAdapter adapter = mCurrentAdapter;
			int mode = adapter.getSortMode();
			int check;
			if (mode < 0) {
				check = R.id.descending;
				mode = ~mode;
			} else {
				check = R.id.ascending;
			}

			int[] itemIds = adapter.getSortEntries();
			String[] items = new String[itemIds.length];
			Resources res = getResources();
			for (int i = itemIds.length; --i != -1; ) {
				items[i] = res.getString(itemIds[i]);
			}

			RadioGroup header = (RadioGroup)getLayoutInflater().inflate(R.layout.sort_dialog, null);
			header.check(check);

			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.sort_by);
			builder.setSingleChoiceItems(items, mode + 1, this); // add 1 for header
			builder.setNeutralButton(R.string.done, null);

			AlertDialog dialog = builder.create();
			dialog.getListView().addHeaderView(header);
			dialog.setOnDismissListener(this);
			dialog.show();
			return true;
		}
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * Hook up a ListView to this Activity and the supplied adapter
	 *
	 * @param id The id of the ListView
	 * @param type The media type for the adapter.
	 * @param label The text to show on the tab.
	 * @param icon The icon to show on the tab.
	 * @param expandable True if the rows are expandable.
	 * @param hasHeader True if the view should have a header row.
	 * @param limiter The initial limiter to set on the adapter.
	 */
	private MediaAdapter setupView(int id, int type, int label, int icon, boolean expandable, boolean hasHeader, MediaAdapter.Limiter limiter)
	{
		ListView view = (ListView)findViewById(id);
		view.setOnItemClickListener(this);
		view.setOnCreateContextMenuListener(this);
		view.setCacheColorHint(Color.BLACK);
		view.setDivider(null);
		view.setFastScrollEnabled(true);

		MediaAdapter adapter = new MediaAdapter(this, type, expandable, hasHeader, limiter);
		view.setAdapter(adapter);
		loadSortOrder(adapter);

		Resources res = getResources();
		String labelRes = res.getString(label);
		Drawable iconRes = res.getDrawable(icon);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB)
			mTabHost.addTab(mTabHost.newTabSpec(labelRes).setIndicator(null, iconRes).setContent(id));
		else
			mTabHost.addTab(mTabHost.newTabSpec(labelRes).setIndicator(labelRes, iconRes).setContent(id));

		return adapter;
	}

	/**
	 * Call addToPlaylist with the results from a NewPlaylistDialog stored in
	 * obj.
	 */
	private static final int MSG_NEW_PLAYLIST = 11;
	/**
	 * Delete the songs represented by the intent stored in obj.
	 */
	private static final int MSG_DELETE = 12;
	/**
	 * Call renamePlaylist with the results from a NewPlaylistDialog stored in
	 * obj.
	 */
	private static final int MSG_RENAME_PLAYLIST = 13;
	/**
	 * Called by MediaAdapters to requery their data on the worker thread.
	 * obj will contain the MediaAdapter.
	 */
	private static final int MSG_RUN_QUERY = 14;
	/**
	 * Call addToPlaylist with data from the intent in obj.
	 */
	private static final int MSG_ADD_TO_PLAYLIST = 15;
	/**
	 * Save the sort mode for the adapter passed in obj.
	 */
	private static final int MSG_SAVE_SORT = 16;
	/**
	 * Call {@link LibraryActivity#requestRequery(MediaAdapter)} on the adapter
	 * passed in obj.
	 */
	private static final int MSG_REQUEST_REQUERY = 17;

	@Override
	public boolean handleMessage(Message message)
	{
		switch (message.what) {
		case MSG_ADD_TO_PLAYLIST: {
			Intent intent = (Intent)message.obj;
			addToPlaylist(intent.getLongExtra("playlist", -1), intent);
			break;
		}
		case MSG_NEW_PLAYLIST: {
			NewPlaylistDialog dialog = (NewPlaylistDialog)message.obj;
			if (dialog.isAccepted()) {
				String name = dialog.getText();
				long playlistId = Playlist.createPlaylist(getContentResolver(), name);
				Intent intent = dialog.getIntent();
				intent.putExtra("playlistName", name);
				addToPlaylist(playlistId, intent);
			}
			break;
		}
		case MSG_DELETE:
			delete((Intent)message.obj);
			break;
		case MSG_RENAME_PLAYLIST: {
			NewPlaylistDialog dialog = (NewPlaylistDialog)message.obj;
			if (dialog.isAccepted()) {
				long playlistId = dialog.getIntent().getLongExtra("id", -1);
				Playlist.renamePlaylist(getContentResolver(), playlistId, dialog.getText());
			}
			break;
		}
		case MSG_RUN_QUERY: {
			final MediaAdapter adapter = (MediaAdapter)message.obj;
			QueryTask query = adapter.buildQuery();
			final Cursor cursor = query.runQuery(getContentResolver());
			runOnUiThread(new Runnable() {
				@Override
				public void run()
				{
					adapter.changeCursor(cursor);
				}
			});
			break;
		}
		case MSG_SAVE_SORT: {
			MediaAdapter adapter = (MediaAdapter)message.obj;
			SharedPreferences.Editor editor = PlaybackService.getSettings(this).edit();
			editor.putInt(String.format("sort_%d_%d", adapter.getMediaType(), adapter.getLimiterType()), adapter.getSortMode());
			editor.commit();
			break;
		}
		case MSG_REQUEST_REQUERY:
			requestRequery((MediaAdapter)message.obj);
			break;
		default:
			return super.handleMessage(message);
		}

		return true;
	}

	/**
	 * Requery the given adapter. If it is the current adapter, requery
	 * immediately. Otherwise, mark the adapter as needing a requery and requery
	 * when its tab is selected.
	 *
	 * Must be called on the UI thread.
	 */
	public void requestRequery(MediaAdapter adapter)
	{
		if (adapter == mCurrentAdapter) {
			runQuery(adapter);
		} else {
			adapter.requestRequery();
			// Clear the data for non-visible adapters (so we don't show the old
			// data briefly when we later switch to that adapter)
			adapter.changeCursor(null);
		}
	}

	/**
	 * Schedule a query to be run for the given adapter on the worker thread.
	 *
	 * @param adapter The adapter to run the query for.
	 */
	private void runQuery(MediaAdapter adapter)
	{
		mHandler.removeMessages(MSG_RUN_QUERY, adapter);
		mHandler.sendMessage(mHandler.obtainMessage(MSG_RUN_QUERY, adapter));
	}

	@Override
	public void onMediaChange()
	{
		Handler handler = mUiHandler;
		for (MediaAdapter adapter : mAdapters) {
			handler.sendMessage(handler.obtainMessage(MSG_REQUEST_REQUERY, adapter));
		}
	}

	private void setSearchBoxVisible(boolean visible)
	{
		mSearchBoxVisible = visible;
		mSearchBox.setVisibility(visible ? View.VISIBLE : View.GONE);
		if (mControls != null)
			mControls.setVisibility(visible || (mState & PlaybackService.FLAG_NO_MEDIA) != 0 ? View.GONE : View.VISIBLE);
		if (visible)
			mSearchBox.requestFocus();
	}

	@Override
	protected void onStateChange(int state, int toggled)
	{
		super.onStateChange(state, toggled);

		if ((toggled & PlaybackService.FLAG_NO_MEDIA) != 0) {
			// update visibility of controls
			setSearchBoxVisible(mSearchBoxVisible);
		}
		if ((toggled & PlaybackService.FLAG_EMPTY_QUEUE) != 0 && mEmptyQueue != null) {
			mEmptyQueue.setVisibility((state & PlaybackService.FLAG_EMPTY_QUEUE) == 0 ? View.GONE : View.VISIBLE);
		}
	}

	@Override
	protected void onSongChange(final Song song)
	{
		super.onSongChange(song);

		if (mTitle != null) {
			Uri cover = null;

			if (song == null) {
				mTitle.setText(R.string.none);
				mArtist.setText(null);
			} else {
				Resources res = getResources();
				String title = song.title == null ? res.getString(R.string.unknown) : song.title;
				String artist = song.artist == null ? res.getString(R.string.unknown) : song.artist;
				mTitle.setText(title);
				mArtist.setText(artist);
				cover = song.hasCover(this) ? song.getCoverUri() : null;
			}

			if (Song.mDisableCoverArt)
				mCover.setVisibility(View.GONE);
			else if (cover == null)
				mCover.setImageResource(R.drawable.albumart_mp_unknown_list);
			else
				mCover.setImageURI(cover);
		}
	}

	@Override
	public void onTabChanged(String tag)
	{
		MediaAdapter adapter = mAdapters[mTabHost.getCurrentTab()];
		mCurrentAdapter = adapter;
		if (adapter.isRequeryNeeded())
			runQuery(adapter);
		updateLimiterViews();
	}

	/**
	 * Set the saved sort mode for the given adapter. The adapter should
	 * be re-queried after calling this.
	 *
	 * @param adapter The adapter to load for.
	 */
	private void loadSortOrder(MediaAdapter adapter)
	{
		String key = String.format("sort_%d_%d", adapter.getMediaType(), adapter.getLimiterType());
		int def = adapter.getDefaultSortMode();
		int sort = PlaybackService.getSettings(this).getInt(key, def);
		adapter.setSortMode(sort);
	}

	@Override
	public void onClick(DialogInterface dialog, int which)
	{
		dialog.dismiss();
	}

	@Override
	public void onDismiss(DialogInterface dialog)
	{
		ListView list = ((AlertDialog)dialog).getListView();
		// subtract 1 for header
		int which = list.getCheckedItemPosition() - 1;

		RadioGroup group = (RadioGroup)list.findViewById(R.id.sort_direction);
		if (group.getCheckedRadioButtonId() == R.id.descending)
			which = ~which;

		MediaAdapter adapter = mCurrentAdapter;
		adapter.setSortMode(which);
		requestRequery(adapter);

		// Force a new FastScroller to be created so the scroll sections
		// are updated.
		ListView view = (ListView)mTabHost.getCurrentView();
		view.setFastScrollEnabled(false);
		view.setFastScrollEnabled(true);

		mHandler.sendMessage(mHandler.obtainMessage(MSG_SAVE_SORT, adapter));
	}
}
