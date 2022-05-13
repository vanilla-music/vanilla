/*
 * Copyright (C) 2012 Christopher Eby <kreed@kreed.org>
 * Copyright (C) 2015-2020 Adrian Ulrich <adrian@blinkenlights.ch>
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

import ch.blinkenlights.android.medialibrary.MediaLibrary;
import ch.blinkenlights.android.vanilla.ui.FancyMenu;
import ch.blinkenlights.android.vanilla.ui.FancyMenuItem;
import ch.blinkenlights.android.vanilla.ui.ArrowedText;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.iosched.tabs.VanillaTabLayout;
import android.text.TextUtils;
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
import android.widget.Toast;

import androidx.viewpager.widget.ViewPager;

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
	                 , FancyMenu.Callback
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
	 * Action for row click: queue selection as next item.
	 */
	public static final int ACTION_ENQUEUE_AS_NEXT = 8;
	/**
	 * Action for row click: expand or play all.
	 */
	public static final int ACTION_EXPAND_OR_PLAY_ALL = 9;
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
	 * Whether or not to jump to songs if the are in the queue
	 */
	private boolean mJumpToEnqueuedOnPlay;
	/**
	 * The last used action from the menu. Used with ACTION_LAST_USED.
	 */
	private int mLastAction = ACTION_PLAY;
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

		SharedPreferences settings = SharedPrefHelper.getSettings(this);

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

		loadLimiterIntent(getIntent());
		bindControlButtons();

		if (state != null && state.getBoolean("launch_search")) {
			mBottomBarControls.showSearch(true);
		}
	}

	@Override
	public void onStart()
	{
		super.onStart();

		loadPreferences();
		loadTabOrder();
		updateHeaders();
	}

	/**
	 * Load settings and cache them.
	 */
	private void loadPreferences() {
		SharedPreferences settings = SharedPrefHelper.getSettings(this);
		mDefaultAction = Integer.parseInt(settings.getString(PrefKeys.DEFAULT_ACTION_INT, PrefDefaults.DEFAULT_ACTION_INT));
		mJumpToEnqueuedOnPlay = settings.getBoolean(PrefKeys.JUMP_TO_ENQUEUED_ON_PLAY, PrefDefaults.JUMP_TO_ENQUEUED_ON_PLAY);
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
		SharedPreferences settings = SharedPrefHelper.getSettings(this);
		if (settings.getBoolean(PrefKeys.PLAYBACK_ON_STARTUP, PrefDefaults.PLAYBACK_ON_STARTUP) && Intent.ACTION_MAIN.equals(intent.getAction())) {
			startActivity(new Intent(this, FullPlaybackActivity.class));
		}
	}

	/**
	 * If the given intent has type data, set a limiter built from that
	 * data.
	 */
	private void loadLimiterIntent(Intent intent)
	{
		Limiter limiter = null;

		int type = intent.getIntExtra("type", -1);
		long id = intent.getLongExtra("id", -1);
		String folder = intent.getStringExtra("folder");

		if (type == MediaUtils.TYPE_FILE && folder != null) {
			limiter = FileSystemAdapter.buildLimiter(new File(folder));
		} else if (type != -1 && id != -1) {
			MediaAdapter adapter = new MediaAdapter(this, type, null, null);
			adapter.commitQuery(adapter.query());
			limiter = adapter.buildLimiter(id);
		}

		if (limiter != null) {
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
		loadLimiterIntent(intent);
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event)
	{
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			Limiter limiter = mPagerAdapter.getCurrentLimiter();

			if (mSlidingView.isShrinkable()) {
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
					pos = mPagerAdapter.getMediaTypePosition(limiter.type);
					break;
				case MediaUtils.TYPE_ARTIST:
				case MediaUtils.TYPE_ALBARTIST:
				case MediaUtils.TYPE_COMPOSER:
				case MediaUtils.TYPE_GENRE:
					mPagerAdapter.clearLimiter(limiter.type);
					pos = mPagerAdapter.getMediaTypePosition(limiter.type);
					break;
				case MediaUtils.TYPE_FILE:
					File parentFile = ((File)limiter.data).getParentFile();
					mPagerAdapter.setLimiter(FileSystemAdapter.buildLimiter(parentFile));
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
		case KeyEvent.KEYCODE_SEARCH:
			mBottomBarControls.showSearch(true);
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
	 * Adds songs matching the data from the given intent to the song timeline.
	 *
	 * @param intent An intent created with
	 * {@link LibraryAdapter#createData(View)}.
	 * @param action One of LibraryActivity.ACTION_*
	 */
	private void pickSongs(Intent intent, final int action)
	{
		int effectiveAction = action; // mutable copy
		long id = intent.getLongExtra("id", LibraryAdapter.INVALID_ID);
		int type = mCurrentAdapter.getMediaType();

		// special handling if we pick one song to be played that is already in queue
		boolean songPicked = (id >= 0 && type == MediaUtils.TYPE_SONG); // not invalid, not play all
		if (songPicked && effectiveAction == ACTION_PLAY && mJumpToEnqueuedOnPlay) {
			int songPosInQueue = PlaybackService.get(this).getQueuePositionForSongId(id);
			if (songPosInQueue > -1) {
				// we picked for play one song that is already present in the queue, just jump to it
				PlaybackService.get(this).jumpToQueuePosition(songPosInQueue);
				Toast.makeText(this, R.string.jumping_to_song, Toast.LENGTH_SHORT).show();
				return;
			}
		}

		boolean all = false;
		if (action == ACTION_PLAY_ALL || action == ACTION_ENQUEUE_ALL) {
			boolean notPlayAllAdapter =
				(id == LibraryAdapter.HEADER_ID)
				|| !(type <= MediaUtils.TYPE_SONG || type == MediaUtils.TYPE_FILE);
			if (effectiveAction == ACTION_ENQUEUE_ALL && notPlayAllAdapter) {
				effectiveAction = ACTION_ENQUEUE;
			} else if (effectiveAction == ACTION_PLAY_ALL && notPlayAllAdapter) {
				effectiveAction = ACTION_PLAY;
			} else {
				all = true;
			}
		}

		if (id == LibraryAdapter.HEADER_ID)
			all = true; // page header was clicked -> force all mode

		QueryTask query = buildQueryFromIntent(intent, false, (all ? mCurrentAdapter : null));
		query.mode = modeForAction[effectiveAction];
		PlaybackService.get(this).addSongs(query);

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
		if (mSlidingView.isShrinkable())
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

		boolean tryExpand = action == ACTION_EXPAND || action == ACTION_EXPAND_OR_PLAY_ALL;
		if (tryExpand && rowData.getBooleanExtra(LibraryAdapter.DATA_EXPANDABLE, false)) {
			onItemExpanded(rowData);
		} else if (action != ACTION_DO_NOTHING) {
			if (action == ACTION_EXPAND) {
				// default to playing when trying to expand something that can't
				// be expanded
				action = ACTION_PLAY;
			} else if (action == ACTION_EXPAND_OR_PLAY_ALL) {
				action = ACTION_PLAY_ALL;
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
			final int arrowWidth = 8;
			String[] limiter = limiterData.names;
			LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			for (int i = 0; i != limiter.length; ++i) {
				final boolean last = i+1 == limiter.length;
				final boolean first = i == 0;

				String txt = limiter[i];
				ArrowedText view = new ArrowedText(this);
				int[] colors = {0xFF606060, 0xFF707070};

				if (i%2 == 0) {
					int tmp = colors[0];
					colors[0] = colors[1];
					colors[1] = tmp;
				}

				if (last) {
					colors[1] = 0xFF404040;
				}

				int leftPadding = 14;
				if (first) {
					leftPadding = 6;
					colors[0] = colors[1];
				}

				view.setSingleLine();
				view.setEllipsize(TextUtils.TruncateAt.MARQUEE);
				view.setText(txt);
				view.setTextColor(Color.WHITE);
				view.setLayoutParams(params);
				view.setPaddingDIP(leftPadding, 2, 6, 2);
				view.setArrowWidthDIP(arrowWidth);
				view.setTag(i);
				view.setColors(colors[0], colors[1]);
				view.setOnClickListener(this);
				mLimiterViews.addView(view);

				if (last) {
					final int ap = 10;
					ArrowedText end = new ArrowedText(this);
					end.setOnClickListener(this);
					end.setText("×");
					end.setTextColor(0xFFB0B0B0);
					end.setLayoutParams(params);
					end.setPaddingDIP(0, 2, 0, 2);
					end.setArrowWidthDIP(arrowWidth);
					end.setArrowPaddingDIP(ap);
					end.setMinWidthDIP(arrowWidth+ap);
					end.setTag(i);
					end.setColors(colors[1], 0);
					mLimiterViews.addView(end);
				}
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
	 * MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS row that matches the selection.
	 *
	 * @param limiterType The type of limiter to create. Must be either
	 * MediaUtils.TYPE_ARTIST or MediaUtils.TYPE_ALBUM.
	 * @param selection Selection to pass to the query.
	 */
	private void setLimiter(int limiterType, String selection)
	{
		String[] columns = new String[] { MediaLibrary.ContributorColumns.ALBUMARTIST_ID, MediaLibrary.SongColumns.ALBUM_ID, MediaLibrary.ContributorColumns.ALBUMARTIST, MediaLibrary.AlbumColumns.ALBUM };
		QueryTask query = new QueryTask(MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS, columns, selection, null, null);
		Cursor cursor = query.runQuery(getApplicationContext());

		if (cursor != null) {
			if (cursor.moveToNext()) {
				String[] fields;
				String data;
				switch (limiterType) {
				case MediaUtils.TYPE_ALBARTIST:
					long albumArtistId = cursor.getLong(0);
					String albumArtist1 = cursor.getString(2);
					fields = new String[] { albumArtist1 };
					data = String.format(MediaLibrary.ContributorColumns.ALBUMARTIST_ID+"=%d", albumArtistId);
					break;
				case MediaUtils.TYPE_ALBUM:
					long albumId = cursor.getLong(1);
					String albumArtist2 = cursor.getString(2);
					String album = cursor.getString(3);
					fields = new String[] { albumArtist2, album };
					data = String.format(MediaLibrary.SongColumns.ALBUM_ID+"=%d", albumId);
					break;
				default:
					throw new IllegalArgumentException("setLimiter() does not support limiter type " + limiterType);
				}
				mPagerAdapter.setLimiter(new Limiter(limiterType, fields, data));
			}
			cursor.close();
		}
	}

	private static final int CTX_MENU_NOOP = -1;
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
	private static final int CTX_MENU_PLUGINS = 11;
	private static final int CTX_MENU_SHOW_DETAILS = 12;
	private static final int CTX_MENU_ADD_TO_HOMESCREEN = 13;
	private static final int CTX_MENU_ADD_TO_PLAYLIST = 14;

	/**
	 * Creates a context menu for an adapter row.
	 *
	 * @param rowData Data for the adapter row.
	 * @param view the view which was clicked.
	 * @param x x-coords of event
	 * @param y y-coords of event
	 */
	public boolean onCreateFancyMenu(Intent rowData, View view, float x, float y) {
		FancyMenu fm = new FancyMenu(this, this);
		// Add to playlist is always available.
		fm.addSpacer(20);
		fm.add(CTX_MENU_ADD_TO_PLAYLIST, 20, R.drawable.menu_add_to_playlist, R.string.add_to_playlist).setIntent(rowData);

		if (rowData.getLongExtra(LibraryAdapter.DATA_ID, LibraryAdapter.INVALID_ID) == LibraryAdapter.HEADER_ID) {
			fm.setHeaderTitle(getString(R.string.all_songs));
			fm.add(CTX_MENU_PLAY_ALL, 10, R.drawable.menu_play_all, R.string.play_all).setIntent(rowData);
			fm.add(CTX_MENU_ENQUEUE_ALL, 10, R.drawable.menu_enqueue, R.string.enqueue_all).setIntent(rowData);
		} else {
			int type = rowData.getIntExtra(LibraryAdapter.DATA_TYPE, MediaUtils.TYPE_INVALID);

			fm.setHeaderTitle(rowData.getStringExtra(LibraryAdapter.DATA_TITLE));

			if (type != MediaUtils.TYPE_FILE)
				fm.add(CTX_MENU_ADD_TO_HOMESCREEN, 20, R.drawable.menu_add_to_homescreen, R.string.add_to_homescreen).setIntent(rowData);

			if (FileUtils.canDispatchIntent(rowData))
				fm.add(CTX_MENU_OPEN_EXTERNAL, 10, R.drawable.menu_launch, R.string.open).setIntent(rowData);

			fm.add(CTX_MENU_PLAY, 0, R.drawable.menu_play, R.string.play).setIntent(rowData);
			if (type <= MediaUtils.TYPE_SONG || type == MediaUtils.TYPE_FILE)
				fm.add(CTX_MENU_PLAY_ALL, 1, R.drawable.menu_play_all, R.string.play_all).setIntent(rowData);

			fm.add(CTX_MENU_ENQUEUE_AS_NEXT, 1, R.drawable.menu_enqueue_as_next, R.string.enqueue_as_next).setIntent(rowData);
			fm.add(CTX_MENU_ENQUEUE, 1, R.drawable.menu_enqueue, R.string.enqueue).setIntent(rowData);

			if (type == MediaUtils.TYPE_PLAYLIST) {
				fm.add(CTX_MENU_RENAME_PLAYLIST, 0, R.drawable.menu_edit, R.string.rename).setIntent(rowData);
			} else if (rowData.getBooleanExtra(LibraryAdapter.DATA_EXPANDABLE, false)) {
				fm.add(CTX_MENU_EXPAND, 2, R.drawable.menu_expand, R.string.expand).setIntent(rowData);
			}

			if (type == MediaUtils.TYPE_SONG || type == MediaUtils.TYPE_ALBUM) {
				fm.addSpacer(30);
				fm.add(CTX_MENU_MORE_FROM_ARTIST, 30, R.drawable.menu_artist, R.string.more_from_artist).setIntent(rowData);

				if (type == MediaUtils.TYPE_SONG) {
					fm.add(CTX_MENU_MORE_FROM_ALBUM, 30, R.drawable.menu_album, R.string.more_from_album).setIntent(rowData);
					fm.add(CTX_MENU_SHOW_DETAILS, 91, R.drawable.menu_details, R.string.details).setIntent(rowData);
					if (PluginUtils.checkPlugins(this)) {
						// not part of submenu: just last item in normal menu.
						fm.add(CTX_MENU_PLUGINS, 99, R.drawable.menu_plugins, R.string.plugins).setIntent(rowData);
					}
				}
			}
			fm.addSpacer(90);
			fm.add(CTX_MENU_DELETE, 91, R.drawable.menu_delete, R.string.delete).setIntent(rowData);
		}
		fm.show(view, x, y);
		return true;
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
	public boolean onFancyItemSelected(FancyMenuItem item) {
		final Intent intent = item.getIntent();
		switch (item.getItemId()) {
		case CTX_MENU_NOOP:
			break;
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
			PlaylistInputDialog dialog = PlaylistInputDialog.newInstance(new PlaylistInputDialog.Callback() {
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
		case CTX_MENU_SHOW_DETAILS:
			long songId = intent.getLongExtra(LibraryAdapter.DATA_ID, -1);
			TrackDetailsDialog.show(getFragmentManager(), songId);
			break;
		case CTX_MENU_PLUGINS: {
			showPluginMenu(intent);
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
		case CTX_MENU_ADD_TO_PLAYLIST: {
			long id = intent.getLongExtra("id", LibraryAdapter.INVALID_ID);
			PlaylistDialog plDialog = PlaylistDialog.newInstance(this, intent, (id == LibraryAdapter.HEADER_ID ? mCurrentAdapter : null));
			plDialog.show(getFragmentManager(), "PlaylistDialog");
			break;
		}
		case CTX_MENU_ADD_TO_HOMESCREEN: {
			int type = intent.getIntExtra(LibraryAdapter.DATA_TYPE, MediaUtils.TYPE_INVALID);
			long id = intent.getLongExtra(LibraryAdapter.DATA_ID, LibraryAdapter.INVALID_ID);
			String label = intent.getStringExtra(LibraryAdapter.DATA_TITLE);
			SystemUtils.installLauncherShortcut(this, label, type, id);
			break;
		}
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
		menu.add(0, MENU_GO_HOME, 30, R.string.go_home);
		menu.add(0, MENU_SORT, 30, R.string.sort_by).setIcon(R.drawable.ic_menu_sort_alphabetically);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		LibraryAdapter adapter = mCurrentAdapter;
		menu.findItem(MENU_GO_HOME).setVisible(
				adapter != null &&
				adapter.getMediaType() == MediaUtils.TYPE_FILE &&
				(!mSlidingView.isShrinkable() || !mSlidingView.isFullyExpanded()));
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId()) {
		case MENU_SEARCH:
			mBottomBarControls.showSearch(true);
			break;
		case MENU_PLAYBACK:
			openPlaybackActivity();
			break;
		case MENU_GO_HOME:
			mPagerAdapter.setLimiter(FileSystemAdapter.buildHomeLimiter(getApplicationContext()));
			updateLimiterViews();
			break;
		case MENU_SORT: {
			SortableAdapter adapter = (SortableAdapter)mCurrentAdapter;
			LinearLayout list = (LinearLayout)getLayoutInflater().inflate(R.layout.sort_dialog, null);
			CheckBox reverseSort = (CheckBox)list.findViewById(R.id.reverse_sort);

			int[] itemIds = adapter.getSortEntries();
			String[] items = new String[itemIds.length];
			Resources res = getResources();
			for (int i = 0; i < itemIds.length; i++) {
				items[i] = res.getString(itemIds[i]);
			}

			int mode = adapter.getSortModeIndex();
			reverseSort.setChecked(adapter.isSortDescending());

			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.sort_by);
			builder.setSingleChoiceItems(items, mode, this);
			builder.setPositiveButton(R.string.done, null);

			AlertDialog dialog = builder.create();
			dialog.getListView().addFooterView(list);
			dialog.setOnDismissListener(this);
			dialog.show();
			break;
		}
		default:
			return super.onOptionsItemSelected(item);
		}
		return true;
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
			SharedPreferences.Editor editor = SharedPrefHelper.getSettings(this).edit();
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
		if (mPagerAdapter != null)
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
		int which = list.getCheckedItemPosition();

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
