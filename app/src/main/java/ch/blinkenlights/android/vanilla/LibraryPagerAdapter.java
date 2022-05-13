/*
 * Copyright (C) 2012 Christopher Eby <kreed@kreed.org>
 * Copyright (C) 2015-2018 Adrian Ulrich <adrian@blinkenlights.ch>
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

import ch.blinkenlights.android.vanilla.ext.CoordClickListener;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.util.LruCache;
import android.widget.AdapterView;
import android.widget.ListView;
import java.util.Arrays;
import java.util.ArrayList;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;


/**
 * PagerAdapter that manages the library media ListViews.
 */
public class LibraryPagerAdapter
	extends PagerAdapter
	implements Handler.Callback
	         , ViewPager.OnPageChangeListener
	         , CoordClickListener.Callback
	         , AdapterView.OnItemClickListener
{
	/**
	 * The number of unique list types. The number of visible lists may be
	 * smaller.
	 */
	public static final int MAX_ADAPTER_COUNT = MediaUtils.TYPE_COUNT;
	/**
	 * The human-readable title for each list. The positions correspond to the
	 * MediaUtils ids, so e.g. TITLES[MediaUtils.TYPE_SONG] = R.string.songs
	 */
	public static final int[] TITLES = { R.string.artists, R.string.albums, R.string.songs, R.string.playlists,
	                                     R.string.genres, R.string.albumartists, R.string.composers, R.string.files };
	/**
	 * Default tab order.
	 */
	public static final int[] DEFAULT_TAB_ORDER = { MediaUtils.TYPE_ARTIST, MediaUtils.TYPE_ALBARTIST, MediaUtils.TYPE_COMPOSER,
	                                                MediaUtils.TYPE_ALBUM, MediaUtils.TYPE_SONG, MediaUtils.TYPE_PLAYLIST,
	                                                MediaUtils.TYPE_GENRE, MediaUtils.TYPE_FILE };
	/**
	 * The default visibility of tabs
	 */
	public static final boolean[] DEFAULT_TAB_VISIBILITY = { true, false, false, true, true, true, true, true };

	/**
	 * The user-chosen tab order.
	 */
	int[] mTabOrder;
	/**
	 * The number of visible tabs.
	 */
	private int mTabCount;
	/**
	 * The ListView for each adapter. Each index corresponds to that list's
	 * MediaUtils id.
	 */
	private final ListView[] mLists = new ListView[MAX_ADAPTER_COUNT];
	/**
	 * The adapters. Each index corresponds to that adapter's MediaUtils id.
	 */
	public LibraryAdapter[] mAdapters = new LibraryAdapter[MAX_ADAPTER_COUNT];
	/**
	 * Whether the adapter corresponding to each index has stale data.
	 */
	private final boolean[] mRequeryNeeded = new boolean[MAX_ADAPTER_COUNT];
	/**
	 * The artist adapter instance, also stored at mAdapters[MediaUtils.TYPE_ARTIST].
	 */
	private MediaAdapter mArtistAdapter;
	/**
	 * The albumartist adapter instance, also stored at mAdapters[MediaUtils.TYPE_ALBART].
	 */
	private MediaAdapter mAlbArtAdapter;
	/**
	 * The composer adapter instance, also stored at mAdapters[MediaUtils.TYPE_COMPOSER].
	 */
	private MediaAdapter mComposerAdapter;
	/**
	 * The album adapter instance, also stored at mAdapters[MediaUtils.TYPE_ALBUM].
	 */
	private MediaAdapter mAlbumAdapter;
	/**
	 * The song adapter instance, also stored at mAdapters[MediaUtils.TYPE_SONG].
	 */
	private MediaAdapter mSongAdapter;
	/**
	 * The playlist adapter instance, also stored at mAdapters[MediaUtils.TYPE_PLAYLIST].
	 */
	MediaAdapter mPlaylistAdapter;
	/**
	 * The genre adapter instance, also stored at mAdapters[MediaUtils.TYPE_GENRE].
	 */
	private MediaAdapter mGenreAdapter;
	/**
	 * The file adapter instance, also stored at mAdapters[MediaUtils.TYPE_FILE].
	 */
	private FileSystemAdapter mFilesAdapter;
	/**
	 * LRU cache holding the last scrolling position of all adapter views
	 */
	private static AdaperPositionLruCache sLruAdapterPos;
	/**
	 * The adapter of the currently visible list.
	 */
	private LibraryAdapter mCurrentAdapter;
	/**
	 * The index of the current page.
	 */
	private int mCurrentPage;
	/**
	 * A limiter that should be set when the album adapter is created.
	 */
	private Limiter mPendingArtistLimiter;
	/**
	 * A limiter that should be set when the albumartist adapter is created.
	 */
	private Limiter mPendingAlbArtLimiter;
	/**
	 * A limiter that should be set when the composer adapter is created.
	 */
	private Limiter mPendingComposerLimiter;
	/**
	 * A limiter that should be set when the album adapter is created.
	 */
	private Limiter mPendingAlbumLimiter;
	/**
	 * A limiter that should be set when the song adapter is created.
	 */
	private Limiter mPendingSongLimiter;
	/**
	 * A limiter that should be set when the files adapter is created.
	 */
	private Limiter mPendingFileLimiter;
	/**
	 * The LibraryActivity that owns this adapter. The adapter will be notified
	 * of changes in the current page.
	 */
	private final LibraryActivity mActivity;
	/**
	 * A Handler running on the UI thread.
	 */
	private final Handler mUiHandler;
	/**
	 * A Handler running on a worker thread.
	 */
	private final Handler mWorkerHandler;
	/**
	 * The text to be displayed in the first row of the artist, album, and
	 * song limiters.
	 */
	private String mHeaderText;
	/**
	 * A list of header rows which require test updates
	 */
	private ArrayList<DraggableRow> mHeaderViews = new ArrayList();
	/**
	 * The current filter text, or null if none.
	 */
	private String mFilter;

	/**
	 * Create the LibraryPager.
	 *
	 * @param activity The LibraryActivity that will own this adapter. The activity
	 * will receive callbacks from the ListViews.
	 * @param workerLooper A Looper running on a worker thread.
	 */
	public LibraryPagerAdapter(LibraryActivity activity, Looper workerLooper)
	{
		if (sLruAdapterPos == null)
			sLruAdapterPos = new AdaperPositionLruCache(32);
		mActivity = activity;
		mUiHandler = new Handler(this);
		mWorkerHandler = new Handler(workerLooper, this);
		mCurrentPage = -1;
	}

	/**
	 * Load the tab order from SharedPreferences.
	 *
	 * @return True if order has changed.
	 */
	public boolean loadTabOrder()
	{
		String in = SharedPrefHelper.getSettings(mActivity).getString(PrefKeys.TAB_ORDER, PrefDefaults.TAB_ORDER);
		int[] order = new int[MAX_ADAPTER_COUNT];
		int count = 0;
		if (in != null && in.length() == MAX_ADAPTER_COUNT) {
			char[] chars = in.toCharArray();
			order = new int[MAX_ADAPTER_COUNT];
			for (int i = 0; i != MAX_ADAPTER_COUNT; ++i) {
				char v = chars[i];
				if (v >= 128) {
					v -= 128;
					if (v >= MediaUtils.TYPE_COUNT) {
						// invalid media type, ignore all data
						count = 0;
						break;
					}
					order[count++] = v;
				}
			}
		}

		// set default tabs if none were loaded
		if (count == 0) {
			for (int i=0; i != MAX_ADAPTER_COUNT; i++) {
				if (DEFAULT_TAB_VISIBILITY[i])
					order[count++] = DEFAULT_TAB_ORDER[i];
			}
		}

		if (count != mTabCount || !Arrays.equals(order, mTabOrder)) {
			mTabOrder = order;
			mTabCount = count;
			notifyDataSetChanged();
			computeExpansions();
			return true;
		}

		return false;
	}

	/**
	 * Determines whether adapters should be expandable from the visibility of
	 * the adapters each expands to. Also updates mSongsPosition/mAlbumsPositions.
	 */
	public void computeExpansions()
	{
		if (mGenreAdapter != null)
			mGenreAdapter.setExpandable(
				getMediaTypePosition(MediaUtils.TYPE_SONG) != -1 ||
				getMediaTypePosition(MediaUtils.TYPE_ALBUM) != -1 ||
				getMediaTypePosition(MediaUtils.TYPE_ARTIST) != -1
			);
		if (mArtistAdapter != null)
			mArtistAdapter.setExpandable(
				getMediaTypePosition(MediaUtils.TYPE_SONG) != -1 ||
				getMediaTypePosition(MediaUtils.TYPE_ALBUM) != -1
			);
		if (mAlbumAdapter != null)
			mAlbumAdapter.setExpandable(
				getMediaTypePosition(MediaUtils.TYPE_SONG) != -1
			);
	}

	@Override
	public Object instantiateItem(ViewGroup container, int position)
	{
		int type = mTabOrder[position];
		ListView view = mLists[type];

		if (view == null) {
			LibraryActivity activity = mActivity;
			LayoutInflater inflater = activity.getLayoutInflater();
			LibraryAdapter adapter;
			DraggableRow header = null;

			switch (type) {
			case MediaUtils.TYPE_ARTIST:
				adapter = mArtistAdapter = new MediaAdapter(activity, MediaUtils.TYPE_ARTIST, mPendingArtistLimiter, activity);
				mArtistAdapter.setExpandable(getMediaTypePosition(MediaUtils.TYPE_SONG) != -1 || getMediaTypePosition(MediaUtils.TYPE_ALBUM) != -1);
				header = (DraggableRow)inflater.inflate(R.layout.draggable_row, null);
				break;
			case MediaUtils.TYPE_ALBARTIST:
				adapter = mAlbArtAdapter = new MediaAdapter(activity, MediaUtils.TYPE_ALBARTIST, mPendingAlbArtLimiter, activity);
				mAlbArtAdapter.setExpandable(getMediaTypePosition(MediaUtils.TYPE_SONG) != -1 || getMediaTypePosition(MediaUtils.TYPE_ALBUM) != -1);
				header = (DraggableRow)inflater.inflate(R.layout.draggable_row, null);
				break;
			case MediaUtils.TYPE_COMPOSER:
				adapter = mComposerAdapter = new MediaAdapter(activity, MediaUtils.TYPE_COMPOSER, mPendingComposerLimiter, activity);
				mComposerAdapter.setExpandable(getMediaTypePosition(MediaUtils.TYPE_SONG) != -1 || getMediaTypePosition(MediaUtils.TYPE_ALBUM) != -1);
				header = (DraggableRow)inflater.inflate(R.layout.draggable_row, null);
				break;
			case MediaUtils.TYPE_ALBUM:
				adapter = mAlbumAdapter = new MediaAdapter(activity, MediaUtils.TYPE_ALBUM, mPendingAlbumLimiter, activity);
				mAlbumAdapter.setExpandable(getMediaTypePosition(MediaUtils.TYPE_SONG) != -1);
				mPendingAlbumLimiter = null;
				header = (DraggableRow)inflater.inflate(R.layout.draggable_row, null);
				break;
			case MediaUtils.TYPE_SONG:
				adapter = mSongAdapter = new MediaAdapter(activity, MediaUtils.TYPE_SONG, mPendingSongLimiter, activity);
				mPendingSongLimiter = null;
				header = (DraggableRow)inflater.inflate(R.layout.draggable_row, null);
				break;
			case MediaUtils.TYPE_PLAYLIST:
				adapter = mPlaylistAdapter = new MediaAdapter(activity, MediaUtils.TYPE_PLAYLIST, null, activity);
				break;
			case MediaUtils.TYPE_GENRE:
				adapter = mGenreAdapter = new MediaAdapter(activity, MediaUtils.TYPE_GENRE, null, activity);
				mGenreAdapter.setExpandable(
					getMediaTypePosition(MediaUtils.TYPE_SONG) != -1 ||
					getMediaTypePosition(MediaUtils.TYPE_ALBUM) != -1 ||
					getMediaTypePosition(MediaUtils.TYPE_ARTIST) != -1
				);
				break;
			case MediaUtils.TYPE_FILE:
				adapter = mFilesAdapter = new FileSystemAdapter(activity, mPendingFileLimiter);
				mPendingFileLimiter = null;
				header = (DraggableRow)inflater.inflate(R.layout.draggable_row, null);
				break;
			default:
				throw new IllegalArgumentException("Invalid media type: " + type);
			}

			CoordClickListener ccl = new CoordClickListener(this);
			view = (ListView)inflater.inflate(R.layout.listview, null);
			ccl.registerForOnItemLongClickListener(view);
			view.setOnItemClickListener(this);
			view.setTag(type);

			if (header != null) {
				header.setText(mHeaderText);
				header.setTag(new ViewHolder()); // behave like a normal library row
				view.addHeaderView(header);
				mHeaderViews.add(header);
			}
			view.setAdapter(adapter);

			loadSortOrder((SortableAdapter)adapter);

			adapter.setFilter(mFilter);

			mAdapters[type] = adapter;
			mLists[type] = view;
			mRequeryNeeded[type] = true;
		}

		requeryIfNeeded(type);
		container.addView(view);
		return view;
	}

	@Override
	public int getItemPosition(Object item)
	{
		int type = (Integer)((ListView)item).getTag();
		int pos = getMediaTypePosition(type);
		return pos == -1 ? POSITION_NONE : pos;
	}

	@Override
	public void destroyItem(ViewGroup container, int position, Object object)
	{
		container.removeView((View)object);
	}

	@Override
	public CharSequence getPageTitle(int position)
	{
		return mActivity.getResources().getText(TITLES[mTabOrder[position]]);
	}

	@Override
	public int getCount()
	{
		return mTabCount;
	}

	@Override
	public boolean isViewFromObject(View view, Object object)
	{
		return view == object;
	}

	@Override
	public void setPrimaryItem(ViewGroup container, int position, Object object)
	{
		int type = mTabOrder[position];
		LibraryAdapter adapter = mAdapters[type];
		if (position != mCurrentPage || adapter != mCurrentAdapter) {
			requeryIfNeeded(type);
			mCurrentAdapter = adapter;
			mCurrentPage = position;
			mActivity.onPageChanged(position, adapter);
		}
	}

	@Override
	public void restoreState(Parcelable state, ClassLoader loader)
	{
		Bundle in = (Bundle)state;
		mPendingArtistLimiter = (Limiter)in.getSerializable("limiter_artists");
		mPendingAlbArtLimiter = (Limiter)in.getSerializable("limiter_albumartists");
		mPendingComposerLimiter = (Limiter)in.getSerializable("limiter_composer");
		mPendingAlbumLimiter = (Limiter)in.getSerializable("limiter_albums");
		mPendingSongLimiter = (Limiter)in.getSerializable("limiter_songs");
		mPendingFileLimiter = (Limiter)in.getSerializable("limiter_files");
	}

	@Override
	public Parcelable saveState()
	{
		Bundle out = new Bundle(10);
		if (mArtistAdapter != null)
			out.putSerializable("limiter_artists", mArtistAdapter.getLimiter());
		if (mAlbArtAdapter != null)
			out.putSerializable("limiter_albumartists", mAlbArtAdapter.getLimiter());
		if (mComposerAdapter != null)
			out.putSerializable("limiter_composer", mComposerAdapter.getLimiter());
		if (mAlbumAdapter != null)
			out.putSerializable("limiter_albums", mAlbumAdapter.getLimiter());
		if (mSongAdapter != null)
			out.putSerializable("limiter_songs", mSongAdapter.getLimiter());
		if (mFilesAdapter != null)
			out.putSerializable("limiter_files", mFilesAdapter.getLimiter());

		maintainPosition();
		return out;
	}

	/**
	 * Sets the text to be displayed in the first row of the artist, album, and
	 * song lists.
	 */
	public void setHeaderText(String text)
	{
		for(DraggableRow row : mHeaderViews) {
			row.setText(text);
		}
		mHeaderText = text;
	}

	/**
	 * Clear a limiter.
	 *
	 * @param type Which type of limiter to clear.
	 */
	public void clearLimiter(int type)
	{
		ArrayList<LibraryAdapter> targets = new ArrayList<LibraryAdapter>();

		maintainPosition();

		if (type == MediaUtils.TYPE_FILE) {
			if (mFilesAdapter == null) {
				mPendingFileLimiter = null;
			} else {
				targets.add(mFilesAdapter);
			}
		} else {
			if (mArtistAdapter == null) {
				mPendingArtistLimiter = null;
			} else {
				targets.add(mArtistAdapter);
			}
			if (mAlbArtAdapter == null) {
				mPendingAlbArtLimiter = null;
			} else {
				targets.add(mAlbArtAdapter);
			}
			if (mComposerAdapter == null) {
				mPendingComposerLimiter = null;
			} else {
				targets.add(mComposerAdapter);
			}
			if (mAlbumAdapter == null) {
				mPendingAlbumLimiter = null;
			} else {
				targets.add(mAlbumAdapter);
			}
			if (mSongAdapter == null) {
				mPendingSongLimiter = null;
			} else {
				targets.add(mSongAdapter);
			}
		}

		for (LibraryAdapter adapter : targets) {
			adapter.setLimiter(null);
			loadSortOrder((SortableAdapter)adapter);
			requestRequery(adapter);
		}
	}

	/**
	 * Update the adapters with the given limiter.
	 * Remarks: this is the place that determines the limiters for each tab and
	 * the tab that will load when and item is expanded.
	 * @param limiter The limiter to set.
	 * @return The tab type that should be switched to to expand the row.
	 */
	public int setLimiter(Limiter limiter)
	{
		int tab;
		ArrayList<LibraryAdapter> targets = new ArrayList<LibraryAdapter>();

		maintainPosition();

		switch (limiter.type) {
		case MediaUtils.TYPE_ALBUM:
			if (mSongAdapter == null) {
				mPendingSongLimiter = limiter;
			} else {
				targets.add(mSongAdapter);
			}
			tab = getMediaTypePosition(MediaUtils.TYPE_SONG);
			break;
		case MediaUtils.TYPE_ARTIST:
		case MediaUtils.TYPE_ALBARTIST:
		case MediaUtils.TYPE_COMPOSER:
			if (mAlbumAdapter == null) {
				mPendingAlbumLimiter = limiter;
			} else {
				targets.add(mAlbumAdapter);
			}
			if (mSongAdapter == null) {
				mPendingSongLimiter = limiter;
			} else {
				targets.add(mSongAdapter);
			}
			tab = getMediaTypePosition(MediaUtils.TYPE_ALBUM);
			if (tab == -1)
				tab = getMediaTypePosition(MediaUtils.TYPE_SONG);
			break;
		case MediaUtils.TYPE_GENRE:
			if (mArtistAdapter == null) {
				mPendingArtistLimiter = limiter;
			} else {
				targets.add(mArtistAdapter);
			}
			if (mAlbArtAdapter == null) {
				mPendingAlbArtLimiter = limiter;
			} else {
				targets.add(mAlbArtAdapter);
			}
			if (mComposerAdapter == null) {
				mPendingComposerLimiter = limiter;
			} else {
				targets.add(mComposerAdapter);
			}
			if (mAlbumAdapter == null) {
				mPendingAlbumLimiter = limiter;
			} else {
				targets.add(mAlbumAdapter);
			}
			if (mSongAdapter == null) {
				mPendingSongLimiter = limiter;
			} else {
				targets.add(mSongAdapter);
			}
			tab = getMediaTypePosition(MediaUtils.TYPE_ARTIST);
			if (tab == -1)
				tab = getMediaTypePosition(MediaUtils.TYPE_ALBUM);
			if (tab == -1)
				tab = getMediaTypePosition(MediaUtils.TYPE_SONG);
			break;
		case MediaUtils.TYPE_FILE:
			if (mFilesAdapter == null) {
				mPendingFileLimiter = limiter;
			} else {
				targets.add(mFilesAdapter);
				// forcefully jump to beginning - commit query might restore a saved position
				// but if it doesn't we would end up at the same scrolling position in a new
				// folder which is uncool.
				mLists[MediaUtils.TYPE_FILE].setSelection(0);
			}
			tab = getMediaTypePosition(MediaUtils.TYPE_FILE);
			break;
		default:
			throw new IllegalArgumentException("Unsupported limiter type: " + limiter.type);
		}

		for (LibraryAdapter adapter : targets) {
			adapter.setLimiter(limiter);
			loadSortOrder((SortableAdapter)adapter);
			requestRequery(adapter);
		}

		return tab;
	}

	/**
	 * Saves the scrolling position of every visible limiter
	 */
	private void maintainPosition() {
		for (int i = MAX_ADAPTER_COUNT; --i != -1; ) {
			if (mAdapters[i] != null) {
				sLruAdapterPos.storePosition(mAdapters[i], mLists[i].getFirstVisiblePosition());
			}
		}
	}

	/**
	 * Restores the saved scrolling position
	 */
	private void restorePosition(int index) {
		// Restore scrolling position if present and valid
		Integer curPos = sLruAdapterPos.popPosition(mAdapters[index]);
		if (curPos != null && curPos < mLists[index].getCount())
			mLists[index].setSelection(curPos);
	}

	/**
	 * Returns the limiter set on the current adapter or null if there is none.
	 */
	public Limiter getCurrentLimiter()
	{
		LibraryAdapter current = mCurrentAdapter;
		if (current == null)
			return null;
		return current.getLimiter();
	}

	/**
	 * Returns the tab position of given media type, -1 if not visible.
	 *
	 * @return int
	 */
	public int getMediaTypePosition(int type) {
		int[] order = mTabOrder;
		for (int i = mTabCount; --i != -1; ) {
			if (order[i] == type)
				return i;
		}
		return -1;
	}

	/**
	 * Run on query on the adapter passed in obj.
	 *
	 * Runs on worker thread.
	 */
	private static final int MSG_RUN_QUERY = 0;
	/**
	 * Save the sort mode for the adapter passed in obj.
	 *
	 * Runs on worker thread.
	 */
	private static final int MSG_SAVE_SORT = 1;
	/**
	 * Call {@link LibraryPagerAdapter#requestRequery(LibraryAdapter)} on the adapter
	 * passed in obj.
	 *
	 * Runs on worker thread.
	 */
	private static final int MSG_REQUEST_REQUERY = 2;
	/**
	 * Commit the cursor passed in obj to the adapter at the index passed in
	 * arg1.
	 *
	 * Runs on UI thread.
	 */
	private static final int MSG_COMMIT_QUERY = 3;

	@Override
	public boolean handleMessage(Message message)
	{
		switch (message.what) {
		case MSG_RUN_QUERY: {
			LibraryAdapter adapter = (LibraryAdapter)message.obj;
			int index = adapter.getMediaType();
			Handler handler = mUiHandler;
			handler.sendMessage(handler.obtainMessage(MSG_COMMIT_QUERY, index, 0, adapter.query()));
			break;
		}
		case MSG_COMMIT_QUERY: {
			int index = message.arg1;
			mAdapters[index].commitQuery(message.obj);
			restorePosition(index);
			break;
		}
		case MSG_SAVE_SORT: {
			SortableAdapter adapter = (SortableAdapter)message.obj;
			SharedPreferences.Editor editor = SharedPrefHelper.getSettings(mActivity).edit();
			editor.putInt(adapter.getSortSettingsKey(), adapter.getSortMode());
			editor.apply();
			break;
		}
		case MSG_REQUEST_REQUERY:
			requestRequery((LibraryAdapter)message.obj);
			break;
		default:
			return false;
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
	public void requestRequery(LibraryAdapter adapter)
	{
		if (adapter == mCurrentAdapter) {
			postRunQuery(adapter);
		} else {
			mRequeryNeeded[adapter.getMediaType()] = true;
			// Clear the data for non-visible adapters (so we don't show the old
			// data briefly when we later switch to that adapter)
			adapter.clear();
		}
	}

	/**
	 * Call {@link LibraryPagerAdapter#requestRequery(LibraryAdapter)} on the UI
	 * thread.
	 *
	 * @param adapter The adapter, passed to requestRequery.
	 */
	public void postRequestRequery(LibraryAdapter adapter)
	{
		Handler handler = mUiHandler;
		handler.sendMessage(handler.obtainMessage(MSG_REQUEST_REQUERY, adapter));
	}

	/**
	 * Schedule a query to be run for the given adapter on the worker thread.
	 *
	 * @param adapter The adapter to run the query for.
	 */
	private void postRunQuery(LibraryAdapter adapter)
	{
		mRequeryNeeded[adapter.getMediaType()] = false;
		Handler handler = mWorkerHandler;
		handler.removeMessages(MSG_RUN_QUERY, adapter);
		handler.sendMessage(handler.obtainMessage(MSG_RUN_QUERY, adapter));
	}

	/**
	 * Requery the adapter of the given type if it exists and needs a requery.
	 *
	 * @param type One of MediaUtils.TYPE_*
	 */
	private void requeryIfNeeded(int type)
	{
		LibraryAdapter adapter = mAdapters[type];
		if (adapter != null && mRequeryNeeded[type]) {
			postRunQuery(adapter);
		}
	}

	/**
	 * Invalidate the data for all adapters.
	 */
	public void invalidateData()
	{
		for (LibraryAdapter adapter : mAdapters) {
			if (adapter != null) {
				postRequestRequery(adapter);
			}
		}
	}

	/**
	 * Set the saved sort mode for the given adapter. The adapter should
	 * be re-queried after calling this.
	 *
	 * @param adapter The adapter to load for.
	 */
	public void loadSortOrder(SortableAdapter adapter)
	{
		String key = adapter.getSortSettingsKey();
		int def = adapter.getDefaultSortMode();
		int sort = SharedPrefHelper.getSettings(mActivity).getInt(key, def);
		adapter.setSortMode(sort);
	}

	/**
	 * Set the sort mode for the current adapter. Current adapter must be a
	 * MediaAdapter. Saves this sort mode to preferences and updates the list
	 * associated with the adapter to display the new sort mode.
	 *
	 * @param mode The sort mode. See {@link MediaAdapter#setSortMode(int)} for
	 * details.
	 */
	public void setSortMode(int mode)
	{
		SortableAdapter adapter = (SortableAdapter)mCurrentAdapter;
		if (mode == adapter.getSortMode())
			return;

		adapter.setSortMode(mode);
		requestRequery(mCurrentAdapter);

		Handler handler = mWorkerHandler;
		handler.sendMessage(handler.obtainMessage(MSG_SAVE_SORT, adapter));
	}

	/**
	 * Set a new filter on all the adapters.
	 */
	public void setFilter(String text)
	{
		if (text.length() == 0)
			text = null;

		mFilter = text;
		for (LibraryAdapter adapter : mAdapters) {
			if (adapter != null) {
				adapter.setFilter(text);
				requestRequery(adapter);
			}
		}
	}

	@Override
	public void onPageScrollStateChanged(int state)
	{
	}

	@Override
	public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels)
	{
	}

	@Override
	public void onPageSelected(int position)
	{
		// onPageSelected and setPrimaryItem are called in similar cases, and it
		// would be nice to use just one of them, but each has caveats:
		// - onPageSelected isn't called when the ViewPager is first
		//   initialized if there is no scrolling to do
		// - setPrimaryItem isn't called until scrolling is complete, which
		//   makes tab bar and limiter updates look bad
		// So we use both.
		setPrimaryItem(null, position, null);
	}

	/**
	 * Creates the row data used by LibraryActivity.
	 */
	private static Intent createHeaderIntent(View header)
	{
		int type = (Integer)((View)header.getParent()).getTag(); // tag is set on parent view of header
		Intent intent = new Intent();
		intent.putExtra(LibraryAdapter.DATA_ID, LibraryAdapter.HEADER_ID);
		intent.putExtra(LibraryAdapter.DATA_TYPE, type);
		return intent;
	}

	@Override
	/**
	 * Dispatch long click event of a row.
	 *
	 * @param parent the parent adapter view
	 * @param view the long clicked view
	 * @param position row position
	 * @param id id of the long clicked row
	 * @param x x-coords of event
	 * @param y y-coords of event
	 *
	 * @return true if the event was consumed
	 */
	public boolean onItemLongClickWithCoords (AdapterView<?> parent, View view, int position, long id, float x, float y) {
		Intent intent = id == LibraryAdapter.HEADER_ID ? createHeaderIntent(view) : mCurrentAdapter.createData(view);
		int type = (Integer)((View)view.getParent()).getTag();

		if (type == MediaUtils.TYPE_FILE) {
			return mFilesAdapter.onCreateFancyMenu(intent, view, x, y);
		}
		return mActivity.onCreateFancyMenu(intent, view, x, y);
	}

	@Override
	public void onItemClick (AdapterView<?> parent, View view, int position, long id) {
		Intent intent = id == LibraryAdapter.HEADER_ID ? createHeaderIntent(view) : mCurrentAdapter.createData(view);
		int type = (Integer)((View)view.getParent()).getTag();

		if (type == MediaUtils.TYPE_FILE) {
			mFilesAdapter.onItemClicked(intent);
		} else {
			mActivity.onItemClicked(intent);
		}
	}

	/**
	 * Perform usability-related actions on pager and contained lists, e.g. highlight current song
	 * or scroll to it if opted-in
	 * @param song song that is currently playing, can be null
     */
	public void onSongChange(Song song) {
		if (mCurrentPage == -1) // no page active, nothing to do
			return;

		int type = mTabOrder[mCurrentPage];
		ListView view = mLists[type];
		if (view == null) // not initialized yet, nothing to do
			return;

		long id = MediaUtils.getCurrentIdForType(song, type);
		if (id == -1) // unknown type
			return;

		// scroll to song on song change if opted-in
		SharedPreferences sharedPrefs = SharedPrefHelper.getSettings(mActivity);
		boolean shouldScroll = sharedPrefs.getBoolean(PrefKeys.ENABLE_SCROLL_TO_SONG, PrefDefaults.ENABLE_SCROLL_TO_SONG);
		if(shouldScroll) {
			for (int pos = 0; pos < view.getCount(); pos++) {
				if (view.getItemIdAtPosition(pos) == id) {
					// for Android < 6.x visible positions are valid only if view is shown
					boolean interactive = view.isShown();
					int middlePos = (view.getFirstVisiblePosition() + view.getLastVisiblePosition()) / 2;
					if (interactive && Math.abs(middlePos - pos) < 30) {
						view.smoothScrollToPosition(pos);
					} else {
						view.setSelection(pos);
					}
					break;
				}
			}
		}
	}

	/**
	 * LRU implementation: saves the adapter position
	 */
	private class AdaperPositionLruCache extends LruCache<String, Integer> {
		public AdaperPositionLruCache(int size) {
			super(size);
		}
		public void storePosition(LibraryAdapter adapter, Integer val) {
			this.put(_k(adapter), val);
		}
		public Integer popPosition(LibraryAdapter adapter) {
			return this.remove(_k(adapter));
		}

		/**
		 * Assemble internal cache key from adapter
		 */
		private String _k(LibraryAdapter adapter) {
			String result = adapter.getMediaType()+"://";
			Limiter limiter = adapter.getLimiter();

			if (limiter != null) {
				for(String entry : limiter.names) {
					result = result + entry + "/";
				}
			}
			return result;
		}

	}

}
