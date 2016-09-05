/*
 * Copyright (C) 2012 Christopher Eby <kreed@kreed.org>
 * Copyright (C) 2015 Adrian Ulrich <adrian@blinkenlights.ch>
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

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.PaintDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.iosched.tabs.VanillaTabLayout;
import android.support.v4.view.ViewPager;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.SearchView;

import java.io.File;
import junit.framework.Assert;

/**
 * The library activity where songs to play can be selected from the library.
 */
public class LibraryActivity
	extends SlidingPlaybackActivity
	implements DialogInterface.OnClickListener
	         , DialogInterface.OnDismissListener
	         , SearchView.OnQueryTextListener
{


	/**
	 * NOTE: ACTION_* must be in sync with default_action_entries
	 */

	/**
	 * Action for row click: play the row.
	 */
	public static final int ACTION_PLAY = 0;
	/**
	 * Action for row click: enqueue the row.
	 */
	public static final int ACTION_ENQUEUE = 1;
	/**
	 * Action for row click: perform the last used action.
	 */
	public static final int ACTION_LAST_USED = 2;
	/**
	 * Action for row click: play all the songs in the adapter, starting with
	 * the current row.
	 */
	public static final int ACTION_PLAY_ALL = 3;
	/**
	 * Action for row click: enqueue all the songs in the adapter, starting with
	 * the current row.
	 */
	public static final int ACTION_ENQUEUE_ALL = 4;
	/**
	 * Action for row click: do nothing.
	 */
	public static final int ACTION_DO_NOTHING = 5;
	/**
	 * Action for row click: expand the row.
	 */
	public static final int ACTION_EXPAND = 6;
	/**
	 * Action for row click: play if paused or enqueue if playing.
	 */
	public static final int ACTION_PLAY_OR_ENQUEUE = 7;
	/**
	 * Action for row click: queue selection as next item
	 */
	public static final int ACTION_ENQUEUE_AS_NEXT = 8;
	/**
	 * The SongTimeline add song modes corresponding to each relevant action.
	 */
	private static final int[] modeForAction =
		{ SongTimeline.MODE_PLAY, SongTimeline.MODE_ENQUEUE, -1,
		  SongTimeline.MODE_PLAY_ID_FIRST, SongTimeline.MODE_ENQUEUE_ID_FIRST,
		  -1, -1, -1, SongTimeline.MODE_ENQUEUE_AS_NEXT };

	public ViewPager mViewPager;

	private BottomBarControls mBottomBarControls;

	private HorizontalScrollView mLimiterScroller;
	private ViewGroup mLimiterViews;
	private VanillaTabLayout mVanillaTabLayout;
	/**
	 * The action to execute when a row is tapped.
	 */
	private int mDefaultAction;
	/**
	 * The last used action from the menu. Used with ACTION_LAST_USED.
	 */
	private int mLastAction = ACTION_PLAY;
	/**
	 * The id of the media that was last pressed in the current adapter. Used to
	 * open the playback activity when an item is pressed twice.
	 */
	private long mLastActedId;
	/**
	 * The pager adapter that manages each media ListView.
	 */
	public LibraryPagerAdapter mPagerAdapter;
	/**
	 * The adapter for the currently visible list.
	 */
	private LibraryAdapter mCurrentAdapter;


	@Override
	public void onCreate(Bundle state)
	{
		ThemeHelper.setTheme(this, R.style.Library);
		super.onCreate(state);

		if (state == null) {
			checkForLaunch(getIntent());
		}

		setContentView(R.layout.library_content);

		mLimiterScroller = (HorizontalScrollView)findViewById(R.id.limiter_scroller);
		mLimiterViews = (ViewGroup)findViewById(R.id.limiter_layout);

		LibraryPagerAdapter pagerAdapter = new LibraryPagerAdapter(this, mLooper);
		mPagerAdapter = pagerAdapter;

		ViewPager pager = (ViewPager)findViewById(R.id.pager);
		pager.setAdapter(pagerAdapter);
		mViewPager = pager;

		SharedPreferences settings = PlaybackService.getSettings(this);

		mBottomBarControls = (BottomBarControls)findViewById(R.id.bottombar_controls);
		mBottomBarControls.setOnClickListener(this);
		mBottomBarControls.setOnQueryTextListener(this);
		mBottomBarControls.enableOptionsMenu(this);

		if(PermissionRequestActivity.havePermissions(this) == false) {
			PermissionRequestActivity.showWarning(this, getIntent());
		}

		mVanillaTabLayout = (VanillaTabLayout)findViewById(R.id.sliding_tabs);
		mVanillaTabLayout.setOnPageChangeListener(pagerAdapter);

		loadTabOrder();
		int page = settings.getInt(PrefKeys.LIBRARY_PAGE, PrefDefaults.LIBRARY_PAGE);
		if (page != 0) {
			pager.setCurrentItem(page);
		}

		loadAlbumIntent(getIntent());
		bindControlButtons();
	}

	@Override
	public void onRestart()
	{
		super.onRestart();
		loadTabOrder();
	}

	@Override
	public void onStart()
	{
		super.onStart();

		SharedPreferences settings = PlaybackService.getSettings(this);
		mDefaultAction = Integer.parseInt(settings.getString(PrefKeys.DEFAULT_ACTION_INT, PrefDefaults.DEFAULT_ACTION_INT));
		mLastActedId = LibraryAdapter.INVALID_ID;
		updateHeaders();
	}

	/**
	 * Load the tab order and update the tab bars if needed.
	 */
	private void loadTabOrder()
	{
		if (mPagerAdapter.loadTabOrder()) {
			// Reinitializes all tabs
			mVanillaTabLayout.setViewPager(mViewPager);
		}
	}

	/**
	 * If this intent looks like a launch from icon/widget/etc, perform
	 * launch actions.
	 */
	private void checkForLaunch(Intent intent)
	{
		SharedPreferences settings = PlaybackService.getSettings(this);
		if (settings.getBoolean(PrefKeys.PLAYBACK_ON_STARTUP, PrefDefaults.PLAYBACK_ON_STARTUP) && Intent.ACTION_MAIN.equals(intent.getAction())) {
			startActivity(new Intent(this, FullPlaybackActivity.class));
		}
	}

	/**
	 * If the given intent has album data, set a limiter built from that
	 * data.
	 */
	private void loadAlbumIntent(Intent intent)
	{
		long albumId = intent.getLongExtra("albumId", -1);
		if (albumId != -1) {
			String[] fields = { intent.getStringExtra("artist"), intent.getStringExtra("album") };
			String data = String.format("album_id=%d", albumId);
			Limiter limiter = new Limiter(MediaUtils.TYPE_ALBUM, fields, data);
			int tab = mPagerAdapter.setLimiter(limiter);
			if (tab == -1 || tab == mViewPager.getCurrentItem())
				updateLimiterViews();
			else
				mViewPager.setCurrentItem(tab);

		}
	}

	@Override
	public void onNewIntent(Intent intent)
	{
		if (intent == null)
			return;

		checkForLaunch(intent);
		loadAlbumIntent(intent);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event)
	{
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			Limiter limiter = mPagerAdapter.getCurrentLimiter();

			if (mSlidingView.isHidden() == false) {
				mSlidingView.hideSlide();
				break;
			}

			if (mBottomBarControls.showSearch(false)) {
				break;
			}

			if (limiter != null) {
				int pos = -1;
				switch (limiter.type) {
				case MediaUtils.TYPE_ALBUM:
					setLimiter(MediaUtils.TYPE_ARTIST, limiter.data.toString());
					pos = mPagerAdapter.mAlbumsPosition;
					break;
				case MediaUtils.TYPE_ARTIST:
					mPagerAdapter.clearLimiter(MediaUtils.TYPE_ARTIST);
					pos = mPagerAdapter.mArtistsPosition;
					break;
				case MediaUtils.TYPE_GENRE:
					mPagerAdapter.clearLimiter(MediaUtils.TYPE_GENRE);
					pos = mPagerAdapter.mGenresPosition;
					break;
				case MediaUtils.TYPE_FILE:
					if(limiter.names.length > 1) {
						File parentFile = ((File)limiter.data).getParentFile();
						mPagerAdapter.setLimiter(FileSystemAdapter.buildLimiter(parentFile));
					} else {
						mPagerAdapter.clearLimiter(MediaUtils.TYPE_FILE);
					}
					break;
				}
				if (pos == -1) {
					updateLimiterViews();
				} else {
					mViewPager.setCurrentItem(pos);
				}
			} else {
				finish();
			}
			break;
		case KeyEvent.KEYCODE_MENU:
			// We intercept these to avoid showing the activity-default menu
			mBottomBarControls.openMenu();
			break;
		default:
			return false;
		}

		return true;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		if (keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL)
			// On ICS, EditText reports backspace events as unhandled despite
			// actually handling them. To workaround, just assume the event was
			// handled if we get here.
			return true;

		if (super.onKeyDown(keyCode, event))
			return true;

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
		boolean isEnqueue = action == ACTION_ENQUEUE || action == ACTION_ENQUEUE_ALL;
		String text = getString(isEnqueue ? R.string.enqueue_all : R.string.play_all);
		mPagerAdapter.setHeaderText(text);
	}

	/**
	 * Adds songs matching the data from the given intent to the song timelime.
	 *
	 * @param intent An intent created with
	 * {@link LibraryAdapter#createData(View)}.
	 * @param action One of LibraryActivity.ACTION_*
	 */
	private void pickSongs(Intent intent, int action)
	{
		long id = intent.getLongExtra("id", LibraryAdapter.INVALID_ID);

		boolean all = false;
		int mode = action;
		if (action == ACTION_PLAY_ALL || action == ACTION_ENQUEUE_ALL) {
			int type = mCurrentAdapter.getMediaType();
			boolean notPlayAllAdapter = type > MediaUtils.TYPE_SONG || id == LibraryAdapter.HEADER_ID;
			if (mode == ACTION_ENQUEUE_ALL && notPlayAllAdapter) {
				mode = ACTION_ENQUEUE;
			} else if (mode == ACTION_PLAY_ALL && notPlayAllAdapter) {
				mode = ACTION_PLAY;
			} else {
				all = true;
			}
		}

		if (id == LibraryAdapter.HEADER_ID)
			all = true; // page header was clicked -> force all mode

		QueryTask query = buildQueryFromIntent(intent, false, (all ? (MediaAdapter)mCurrentAdapter : null));
		query.mode = modeForAction[mode];
		PlaybackService.get(this).addSongs(query);

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
	 * {@link LibraryAdapter#createData(View)}.
	 */
	private void expand(Intent intent)
	{
		mBottomBarControls.showSearch(false);
		int type = intent.getIntExtra("type", MediaUtils.TYPE_INVALID);
		long id = intent.getLongExtra("id", LibraryAdapter.INVALID_ID);
		int tab = mPagerAdapter.setLimiter(mPagerAdapter.mAdapters[type].buildLimiter(id));
		if (tab == -1 || tab == mViewPager.getCurrentItem())
			updateLimiterViews();
		else
			mViewPager.setCurrentItem(tab);
	}

	/**
	 * Open the playback activity and close any activities above it in the
	 * stack.
	 */
	public void openPlaybackActivity()
	{
		if (mSlidingView.isExpanded())
			mSlidingView.hideSlideDelayed();
		startActivity(new Intent(this, FullPlaybackActivity.class));
	}

	/**
	 * Called by LibraryAdapters when a row has been clicked.
	 *
	 * @param rowData The data for the row that was clicked.
	 */
	public void onItemClicked(Intent rowData)
	{
		int action = mDefaultAction;
		if (action == ACTION_LAST_USED)
			action = mLastAction;

		if (action == ACTION_EXPAND && rowData.getBooleanExtra(LibraryAdapter.DATA_EXPANDABLE, false)) {
			onItemExpanded(rowData);
		} else if (rowData.getLongExtra(LibraryAdapter.DATA_ID, LibraryAdapter.INVALID_ID) == mLastActedId) {
			openPlaybackActivity();
		} else if (action != ACTION_DO_NOTHING) {
			if (action == ACTION_EXPAND) {
				// default to playing when trying to expand something that can't
				// be expanded
				action = ACTION_PLAY;
			} else if (action == ACTION_PLAY_OR_ENQUEUE) {
				action = (mState & PlaybackService.FLAG_PLAYING) == 0 ? ACTION_PLAY : ACTION_ENQUEUE;
			}
			pickSongs(rowData, action);
		}
	}

	/**
	 * Called by LibraryAdapters when a row's expand arrow has been clicked.
	 *
	 * @param rowData The data for the row that was clicked.
	 */
	public void onItemExpanded(Intent rowData)
	{
		int type = rowData.getIntExtra(LibraryAdapter.DATA_TYPE, MediaUtils.TYPE_INVALID);
		if (type == MediaUtils.TYPE_PLAYLIST)
			editPlaylist(rowData);
		else
			expand(rowData);
	}

	/**
	 * Create or recreate the limiter breadcrumbs.
	 */
	public void updateLimiterViews()
	{
		mLimiterViews.removeAllViews();

		Limiter limiterData = mPagerAdapter.getCurrentLimiter();
		if (limiterData != null) {
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

			mLimiterScroller.setVisibility(View.VISIBLE);
		} else {
			mLimiterScroller.setVisibility(View.GONE);
		}
	}

	/**
	 * Updates mCover with the new bitmap, running in the UI thread
	 *
	 * @param cover the cover to set, will use a fallback drawable if null
	 */
	private void updateCover(final Bitmap cover) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mBottomBarControls.setCover(cover);
			}
		});
	}

	@Override
	public void onClick(View view)
	{
		if (view == mBottomBarControls) {
			openPlaybackActivity();
		} else if (view.getTag() != null) {
			// a limiter view was clicked
			int i = (Integer)view.getTag();

			Limiter limiter = mPagerAdapter.getCurrentLimiter();
			int type = limiter.type;
			if (i == 1 && type == MediaUtils.TYPE_ALBUM) {
				setLimiter(MediaUtils.TYPE_ARTIST, limiter.data.toString());
			} else if (i > 0) {
				Assert.assertEquals(MediaUtils.TYPE_FILE, limiter.type);
				File file = (File)limiter.data;
				int diff = limiter.names.length - i;
				while (--diff != -1) {
					file = file.getParentFile();
				}
				mPagerAdapter.setLimiter(FileSystemAdapter.buildLimiter(file));
			} else {
				mPagerAdapter.clearLimiter(type);
			}
			updateLimiterViews();
		} else {
			super.onClick(view);
		}
	}

	/**
	 * Set a new limiter of the given type built from the first
	 * MediaStore.Audio.Media row that matches the selection.
	 *
	 * @param limiterType The type of limiter to create. Must be either
	 * MediaUtils.TYPE_ARTIST or MediaUtils.TYPE_ALBUM.
	 * @param selection Selection to pass to the query.
	 */
	private void setLimiter(int limiterType, String selection)
	{
		ContentResolver resolver = getContentResolver();
		Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		String[] projection = new String[] { MediaStore.Audio.Media.ARTIST_ID, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM };
		Cursor cursor = MediaUtils.queryResolver(resolver, uri, projection, selection, null, null);
		if (cursor != null) {
			if (cursor.moveToNext()) {
				String[] fields;
				String data;
				switch (limiterType) {
				case MediaUtils.TYPE_ARTIST:
					fields = new String[] { cursor.getString(2) };
					data = String.format("artist_id=%d", cursor.getLong(0));
					break;
				case MediaUtils.TYPE_ALBUM:
					fields = new String[] { cursor.getString(2), cursor.getString(3) };
					data = String.format("album_id=%d", cursor.getLong(1));
					break;
				default:
					throw new IllegalArgumentException("setLimiter() does not support limiter type " + limiterType);
				}
				mPagerAdapter.setLimiter(new Limiter(limiterType, fields, data));
			}
			cursor.close();
		}
	}

	private static final int CTX_MENU_PLAY = 0;
	private static final int CTX_MENU_ENQUEUE = 1;
	private static final int CTX_MENU_EXPAND = 2;
	private static final int CTX_MENU_ENQUEUE_AS_NEXT = 3;
	private static final int CTX_MENU_DELETE = 4;
	private static final int CTX_MENU_RENAME_PLAYLIST = 5;
	private static final int CTX_MENU_PLAY_ALL = 6;
	private static final int CTX_MENU_ENQUEUE_ALL = 7;
	private static final int CTX_MENU_MORE_FROM_ALBUM = 8;
	private static final int CTX_MENU_MORE_FROM_ARTIST = 9;
	private static final int CTX_MENU_OPEN_EXTERNAL = 10;

	/**
	 * Creates a context menu for an adapter row.
	 *
	 * @param menu The menu to create.
	 * @param rowData Data for the adapter row.
	 */
	public void onCreateContextMenu(ContextMenu menu, Intent rowData)
	{
		if (rowData.getLongExtra(LibraryAdapter.DATA_ID, LibraryAdapter.INVALID_ID) == LibraryAdapter.HEADER_ID) {
			menu.setHeaderTitle(getString(R.string.all_songs));
			menu.add(0, CTX_MENU_PLAY_ALL, 0, R.string.play_all).setIntent(rowData);
			menu.add(0, CTX_MENU_ENQUEUE_ALL, 0, R.string.enqueue_all).setIntent(rowData);
			menu.addSubMenu(0, CTX_MENU_ADD_TO_PLAYLIST, 0, R.string.add_to_playlist).getItem().setIntent(rowData);
		} else {
			int type = rowData.getIntExtra(LibraryAdapter.DATA_TYPE, MediaUtils.TYPE_INVALID);

			menu.setHeaderTitle(rowData.getStringExtra(LibraryAdapter.DATA_TITLE));

			if (FileUtils.canDispatchIntent(rowData))
				menu.add(0, CTX_MENU_OPEN_EXTERNAL, 0, R.string.open).setIntent(rowData);
			menu.add(0, CTX_MENU_PLAY, 0, R.string.play).setIntent(rowData);
			if (type <= MediaUtils.TYPE_SONG) {
				menu.add(0, CTX_MENU_PLAY_ALL, 0, R.string.play_all).setIntent(rowData);
			}
			menu.add(0, CTX_MENU_ENQUEUE_AS_NEXT, 0, R.string.enqueue_as_next).setIntent(rowData);
			menu.add(0, CTX_MENU_ENQUEUE, 0, R.string.enqueue).setIntent(rowData);
			if (type == MediaUtils.TYPE_PLAYLIST) {
				menu.add(0, CTX_MENU_RENAME_PLAYLIST, 0, R.string.rename).setIntent(rowData);
			} else if (rowData.getBooleanExtra(LibraryAdapter.DATA_EXPANDABLE, false)) {
				menu.add(0, CTX_MENU_EXPAND, 0, R.string.expand).setIntent(rowData);
			}
			if (type == MediaUtils.TYPE_ALBUM || type == MediaUtils.TYPE_SONG)
				menu.add(0, CTX_MENU_MORE_FROM_ARTIST, 0, R.string.more_from_artist).setIntent(rowData);
			if (type == MediaUtils.TYPE_SONG)
				menu.add(0, CTX_MENU_MORE_FROM_ALBUM, 0, R.string.more_from_album).setIntent(rowData);
			menu.addSubMenu(0, CTX_MENU_ADD_TO_PLAYLIST, 0, R.string.add_to_playlist).getItem().setIntent(rowData);
			menu.add(0, CTX_MENU_DELETE, 0, R.string.delete).setIntent(rowData);
		}
	}

	/**
	 * Open the playlist editor for the playlist with the given id.
	 */
	private void editPlaylist(Intent rowData)
	{
		Intent launch = new Intent(this, PlaylistActivity.class);
		launch.putExtra("playlist", rowData.getLongExtra(LibraryAdapter.DATA_ID, LibraryAdapter.INVALID_ID));
		launch.putExtra("title", rowData.getStringExtra(LibraryAdapter.DATA_TITLE));
		startActivity(launch);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		if (item.getGroupId() != 0)
			return super.onContextItemSelected(item);

		final Intent intent = item.getIntent();
		switch (item.getItemId()) {
		case CTX_MENU_EXPAND:
			expand(intent);
			if (mDefaultAction == ACTION_LAST_USED && mLastAction != ACTION_EXPAND) {
				mLastAction = ACTION_EXPAND;
				updateHeaders();
			}
			break;
		case CTX_MENU_ENQUEUE:
			pickSongs(intent, ACTION_ENQUEUE);
			break;
		case CTX_MENU_PLAY:
			pickSongs(intent, ACTION_PLAY);
			break;
		case CTX_MENU_PLAY_ALL:
			pickSongs(intent, ACTION_PLAY_ALL);
			break;
		case CTX_MENU_ENQUEUE_ALL:
			pickSongs(intent, ACTION_ENQUEUE_ALL);
			break;
		case CTX_MENU_ENQUEUE_AS_NEXT:
			pickSongs(intent, ACTION_ENQUEUE_AS_NEXT);
			break;
		case CTX_MENU_RENAME_PLAYLIST: {
			final String playlistName = intent.getStringExtra("title");
			final long playlistId = intent.getLongExtra("id", -1);
			PlaylistInputDialog dialog = new PlaylistInputDialog(new PlaylistInputDialog.Callback() {
				@Override
				public void onSuccess(String input) {
					PlaylistTask playlistTask = new PlaylistTask(playlistId, input);
					mHandler.sendMessage(mHandler.obtainMessage(MSG_RENAME_PLAYLIST, playlistTask));
				}
			}, playlistName, R.string.rename);
			dialog.show(getFragmentManager(), "RenamePlaylistInputDialog");
			break;
		}
		case CTX_MENU_DELETE:
			String delete_message = getString(R.string.delete_item, intent.getStringExtra("title"));
			AlertDialog.Builder dialog = new AlertDialog.Builder(this);
			dialog.setTitle(R.string.delete);
			dialog
				.setMessage(delete_message)
				.setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						mHandler.sendMessage(mHandler.obtainMessage(MSG_DELETE, intent));
					}
				})
				.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
					}
				});
				dialog.create().show();
			break;
		case CTX_MENU_OPEN_EXTERNAL: {
			FileUtils.dispatchIntent(this, intent);
			break;
		}
		case CTX_MENU_MORE_FROM_ARTIST: {
			String selection;
			if (intent.getIntExtra(LibraryAdapter.DATA_TYPE, -1) == MediaUtils.TYPE_ALBUM) {
				selection = "album_id=";
			} else {
				selection = "_id=";
			}
			selection += intent.getLongExtra(LibraryAdapter.DATA_ID, LibraryAdapter.INVALID_ID);
			setLimiter(MediaUtils.TYPE_ARTIST, selection);
			updateLimiterViews();
			break;
		}
		case CTX_MENU_MORE_FROM_ALBUM:
			setLimiter(MediaUtils.TYPE_ALBUM, "_id=" + intent.getLongExtra(LibraryAdapter.DATA_ID, LibraryAdapter.INVALID_ID));
			updateLimiterViews();
			break;
		case CTX_MENU_ADD_TO_PLAYLIST:
			long id = intent.getLongExtra("id", LibraryAdapter.INVALID_ID);
			PlaylistDialog plDialog = new PlaylistDialog(this, intent, (id == LibraryAdapter.HEADER_ID ? (MediaAdapter)mCurrentAdapter : null));
			plDialog.show(getFragmentManager(), "PlaylistDialog");
			break;
		default:
			return super.onContextItemSelected(item);
		}

		return true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		menu.add(0, MENU_PLAYBACK, 0, R.string.playback_view);
		menu.add(0, MENU_SEARCH, 0, R.string.search).setIcon(R.drawable.ic_menu_search).setVisible(false);
		menu.add(0, MENU_SORT, 30, R.string.sort_by).setIcon(R.drawable.ic_menu_sort_alphabetically);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		LibraryAdapter adapter = mCurrentAdapter;
		menu.findItem(MENU_SORT).setEnabled(adapter != null && adapter.getMediaType() != MediaUtils.TYPE_FILE);
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId()) {
		case MENU_SEARCH:
			mBottomBarControls.showSearch(true);
			return true;
		case MENU_PLAYBACK:
			openPlaybackActivity();
			return true;
		case MENU_SORT: {
			MediaAdapter adapter = (MediaAdapter)mCurrentAdapter;
			LinearLayout header = (LinearLayout)getLayoutInflater().inflate(R.layout.sort_dialog, null);
			CheckBox reverseSort = (CheckBox)header.findViewById(R.id.reverse_sort);

			int[] itemIds = adapter.getSortEntries();
			String[] items = new String[itemIds.length];
			Resources res = getResources();
			for (int i = itemIds.length; --i != -1; ) {
				items[i] = res.getString(itemIds[i]);
			}

			int mode = adapter.getSortMode();
			if (mode < 0) {
				mode =~ mode;
				reverseSort.setChecked(true);
			}

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
	 * Callback of mSearchView while user types in text
	 */
	@Override
	public boolean onQueryTextChange (String newText) {
		mPagerAdapter.setFilter(newText);
		return true;
	}

	/**
	 * Callback of mSearchViews submit action
	 */
	@Override
	public boolean onQueryTextSubmit (String query) {
		mPagerAdapter.setFilter(query);
		return true;
	}

	/**
	 * Save the current page, passed in arg1, to SharedPreferences.
	 */
	private static final int MSG_SAVE_PAGE = 40;
	/**
	 * Updates mCover using a background thread
	 */
	private static final int MSG_UPDATE_COVER = 41;

	@Override
	public boolean handleMessage(Message message)
	{
		switch (message.what) {
		case MSG_SAVE_PAGE: {
			SharedPreferences.Editor editor = PlaybackService.getSettings(this).edit();
			editor.putInt("library_page", message.arg1);
			editor.apply();
			super.adjustSpines();
			break;
		}
		case MSG_UPDATE_COVER: {
			Bitmap cover = null;
			Song song = (Song)message.obj;
			if (song != null) {
				cover = song.getSmallCover(this);
			}
			// Dispatch view update to UI thread
			updateCover(cover);
			break;
		}
		default:
			return super.handleMessage(message);
		}

		return true;
	}

	@Override
	public void onMediaChange()
	{
		mPagerAdapter.invalidateData();
	}

	@Override
	protected void onStateChange(int state, int toggled)
	{
		super.onStateChange(state, toggled);

		if ((state & PlaybackService.FLAG_EMPTY_QUEUE) != 0)
			mBottomBarControls.setSong(null);
	}

	@Override
	protected void onSongChange(Song song)
	{
		super.onSongChange(song);

		mBottomBarControls.setSong(song);
		if (song != null) {
			mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_COVER, song));
			mPagerAdapter.onSongChange(song);
		}
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

		CheckBox reverseSort = (CheckBox)list.findViewById(R.id.reverse_sort);
		if (reverseSort.isChecked()) {
			which = ~which;
		}

		mPagerAdapter.setSortMode(which);
	}

	/**
	 * Called when a new page becomes visible.
	 *
	 * @param position The position of the new page.
	 * @param adapter The new visible adapter.
	 */
	public void onPageChanged(int position, LibraryAdapter adapter)
	{
		mCurrentAdapter = adapter;
		mLastActedId = LibraryAdapter.INVALID_ID;
		updateLimiterViews();
		if (adapter != null && (adapter.getLimiter() == null || adapter.getMediaType() == MediaUtils.TYPE_FILE)) {
			// Save current page so it is opened on next startup. Don't save if
			// the page was expanded to, as the expanded page isn't the starting
			// point. This limitation does not affect the files tab as the limiter
			// (the files almost always have a limiter)
			Handler handler = mHandler;
			handler.sendMessage(mHandler.obtainMessage(MSG_SAVE_PAGE, position, 0));
		}
	}

}
