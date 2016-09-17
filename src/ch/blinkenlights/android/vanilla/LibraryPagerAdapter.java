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

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.provider.MediaStore;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.util.LruCache;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.LinearLayout;
import java.util.Arrays;

/**
 * PagerAdapter that manages the library media ListViews.
 */
public class LibraryPagerAdapter
	extends PagerAdapter
	implements Handler.Callback
	         , ViewPager.OnPageChangeListener
	         , View.OnCreateContextMenuListener
	         , AdapterView.OnItemClickListener
{
	/**
	 * The number of unique list types. The number of visible lists may be
	 * smaller.
	 */
	public static final int MAX_ADAPTER_COUNT = 6;
	/**
	 * The human-readable title for each list. The positions correspond to the
	 * MediaUtils ids, so e.g. TITLES[MediaUtils.TYPE_SONG] = R.string.songs
	 */
	public static final int[] TITLES = { R.string.artists, R.string.albums, R.string.songs,
	                                     R.string.playlists, R.string.genres, R.string.files };
	/**
	 * Default tab order.
	 */
	public static final int[] DEFAULT_ORDER = { MediaUtils.TYPE_ARTIST, MediaUtils.TYPE_ALBUM, MediaUtils.TYPE_SONG,
	                                            MediaUtils.TYPE_PLAYLIST, MediaUtils.TYPE_GENRE, MediaUtils.TYPE_FILE };
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
	private DraggableRow mArtistHeader;
	private DraggableRow mAlbumHeader;
	private DraggableRow mSongHeader;
	/**
	 * The current filter text, or null if none.
	 */
	private String mFilter;
	/**
	 * The position of the songs page, or -1 if it is hidden.
	 */
	public int mSongsPosition = -1;
	/**
	 * The position of the albums page, or -1 if it is hidden.
	 */
	public int mAlbumsPosition = -1;
	/**
	 * The position of the artists page, or -1 if it is hidden.
	 */
	public int mArtistsPosition = -1;
	/**
	 * The position of the genres page, or -1 if it is hidden.
	 */
	public int mGenresPosition = -1;

	private final ContentObserver mPlaylistObserver = new ContentObserver(null) {
		@Override
		public void onChange(boolean selfChange)
		{
			if (mPlaylistAdapter != null) {
				postRequestRequery(mPlaylistAdapter);
			}
		}
	};

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
		activity.getContentResolver().registerContentObserver(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, true, mPlaylistObserver);
	}

	/**
	 * Load the tab order from SharedPreferences.
	 *
	 * @return True if order has changed.
	 */
	public boolean loadTabOrder()
	{
		String in = PlaybackService.getSettings(mActivity).getString(PrefKeys.TAB_ORDER, PrefDefaults.TAB_ORDER);
		int[] order;
		int count;
		if (in == null || in.length() != MAX_ADAPTER_COUNT) {
			order = DEFAULT_ORDER;
			count = MAX_ADAPTER_COUNT;
		} else {
			char[] chars = in.toCharArray();
			order = new int[MAX_ADAPTER_COUNT];
			count = 0;
			for (int i = 0; i != MAX_ADAPTER_COUNT; ++i) {
				char v = chars[i];
				if (v >= 128) {
					v -= 128;
					if (v >= MediaUtils.TYPE_COUNT) {
						// invalid media type; use default order
						order = DEFAULT_ORDER;
						count = MAX_ADAPTER_COUNT;
						break;
					}
					order[count++] = v;
				}
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
		int[] order = mTabOrder;
		int songsPosition = -1;
		int albumsPosition = -1;
		int artistsPosition = -1;
		int genresPosition = -1;
		for (int i = mTabCount; --i != -1; ) {
			switch (order[i]) {
			case MediaUtils.TYPE_ALBUM:
				albumsPosition = i;
				break;
			case MediaUtils.TYPE_SONG:
				songsPosition = i;
				break;
			case MediaUtils.TYPE_ARTIST:
				artistsPosition = i;
				break;
			case MediaUtils.TYPE_GENRE:
				genresPosition = i;
				break;
			}
		}

		if (mArtistAdapter != null)
			mArtistAdapter.setExpandable(songsPosition != -1 || albumsPosition != -1);
		if (mAlbumAdapter != null)
			mAlbumAdapter.setExpandable(songsPosition != -1);
		if (mGenreAdapter != null)
			mGenreAdapter.setExpandable(songsPosition != -1);

		mSongsPosition = songsPosition;
		mAlbumsPosition = albumsPosition;
		mArtistsPosition = artistsPosition;
		mGenresPosition = genresPosition;
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
				mArtistAdapter.setExpandable(mSongsPosition != -1 || mAlbumsPosition != -1);
				mArtistHeader = header = (DraggableRow)inflater.inflate(R.layout.draggable_row, null);
				break;
			case MediaUtils.TYPE_ALBUM:
				adapter = mAlbumAdapter = new MediaAdapter(activity, MediaUtils.TYPE_ALBUM, mPendingAlbumLimiter, activity);
				mAlbumAdapter.setExpandable(mSongsPosition != -1);
				mPendingAlbumLimiter = null;
				mAlbumHeader = header = (DraggableRow)inflater.inflate(R.layout.draggable_row, null);
				break;
			case MediaUtils.TYPE_SONG:
				adapter = mSongAdapter = new MediaAdapter(activity, MediaUtils.TYPE_SONG, mPendingSongLimiter, activity);
				mPendingSongLimiter = null;
				mSongHeader = header = (DraggableRow)inflater.inflate(R.layout.draggable_row, null);
				break;
			case MediaUtils.TYPE_PLAYLIST:
				adapter = mPlaylistAdapter = new MediaAdapter(activity, MediaUtils.TYPE_PLAYLIST, null, activity);
				break;
			case MediaUtils.TYPE_GENRE:
				adapter = mGenreAdapter = new MediaAdapter(activity, MediaUtils.TYPE_GENRE, null, activity);
				mGenreAdapter.setExpandable(mSongsPosition != -1);
				break;
			case MediaUtils.TYPE_FILE:
				adapter = mFilesAdapter = new FileSystemAdapter(activity, mPendingFileLimiter);
				mPendingFileLimiter = null;
				break;
			default:
				throw new IllegalArgumentException("Invalid media type: " + type);
			}

			view = (ListView)inflater.inflate(R.layout.listview, null);
			view.setOnCreateContextMenuListener(this);
			view.setOnItemClickListener(this);

			view.setTag(type);
			if (header != null) {
				header.getTextView().setText(mHeaderText);
				header.setTag(new ViewHolder()); // behave like a normal library row
				view.addHeaderView(header);
			}
			view.setAdapter(adapter);
			if (type != MediaUtils.TYPE_FILE)
				loadSortOrder((MediaAdapter)adapter);

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
		int[] order = mTabOrder;
		for (int i = mTabCount; --i != -1; ) {
			if (order[i] == type)
				return i;
		}
		return POSITION_NONE;
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
		if (mArtistHeader != null)
			mArtistHeader.getTextView().setText(text);
		if (mAlbumHeader != null)
			mAlbumHeader.getTextView().setText(text);
		if (mSongHeader != null)
			mSongHeader.getTextView().setText(text);
		mHeaderText = text;
	}

	/**
	 * Clear a limiter.
	 *
	 * @param type Which type of limiter to clear.
	 */
	public void clearLimiter(int type)
	{
		maintainPosition();

		if (type == MediaUtils.TYPE_FILE) {
			if (mFilesAdapter == null) {
				mPendingFileLimiter = null;
			} else {
				mFilesAdapter.setLimiter(null);
				requestRequery(mFilesAdapter);
			}
		} else {
			if (mArtistAdapter == null) {
				mPendingArtistLimiter = null;
			} else {
				mArtistAdapter.setLimiter(null);
				loadSortOrder(mArtistAdapter);
				requestRequery(mArtistAdapter);
			}
			if (mAlbumAdapter == null) {
				mPendingAlbumLimiter = null;
			} else {
				mAlbumAdapter.setLimiter(null);
				loadSortOrder(mAlbumAdapter);
				requestRequery(mAlbumAdapter);
			}
			if (mSongAdapter == null) {
				mPendingSongLimiter = null;
			} else {
				mSongAdapter.setLimiter(null);
				loadSortOrder(mSongAdapter);
				requestRequery(mSongAdapter);
			}
		}
	}

	/**
	 * Update the adapters with the given limiter.
	 *
	 * @param limiter The limiter to set.
	 * @return The tab type that should be switched to to expand the row.
	 */
	public int setLimiter(Limiter limiter)
	{
		int tab;

		maintainPosition();

		switch (limiter.type) {
		case MediaUtils.TYPE_ALBUM:
			if (mSongAdapter == null) {
				mPendingSongLimiter = limiter;
			} else {
				mSongAdapter.setLimiter(limiter);
				loadSortOrder(mSongAdapter);
				requestRequery(mSongAdapter);
			}
			tab = mSongsPosition;
			break;
		case MediaUtils.TYPE_ARTIST:
			if (mAlbumAdapter == null) {
				mPendingAlbumLimiter = limiter;
			} else {
				mAlbumAdapter.setLimiter(limiter);
				loadSortOrder(mAlbumAdapter);
				requestRequery(mAlbumAdapter);
			}
			if (mSongAdapter == null) {
				mPendingSongLimiter = limiter;
			} else {
				mSongAdapter.setLimiter(limiter);
				loadSortOrder(mSongAdapter);
				requestRequery(mSongAdapter);
			}
			tab = mAlbumsPosition;
			if (tab == -1)
				tab = mSongsPosition;
			break;
		case MediaUtils.TYPE_GENRE:
			if (mArtistAdapter == null) {
				mPendingArtistLimiter = limiter;
			} else {
				mArtistAdapter.setLimiter(limiter);
				loadSortOrder(mArtistAdapter);
				requestRequery(mArtistAdapter);
			}
			if (mAlbumAdapter == null) {
				mPendingAlbumLimiter = limiter;
			} else {
				mAlbumAdapter.setLimiter(limiter);
				loadSortOrder(mAlbumAdapter);
				requestRequery(mAlbumAdapter);
			}
			if (mSongAdapter == null) {
				mPendingSongLimiter = limiter;
			} else {
				mSongAdapter.setLimiter(limiter);
				loadSortOrder(mSongAdapter);
				requestRequery(mSongAdapter);
			}
			tab = mArtistsPosition;
			if (tab == -1)
				tab = mAlbumsPosition;
			if (tab == -1)
				tab = mSongsPosition;
			break;
		case MediaUtils.TYPE_FILE:
			if (mFilesAdapter == null) {
				mPendingFileLimiter = limiter;
			} else {
				mFilesAdapter.setLimiter(limiter);
				requestRequery(mFilesAdapter);
			}
			tab = -1;
			break;
		default:
			throw new IllegalArgumentException("Unsupported limiter type: " + limiter.type);
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

			// Restore scrolling position if present and valid
			Integer curPos = sLruAdapterPos.popPosition(mAdapters[index]);
			if (curPos != null && curPos < mLists[index].getCount())
				mLists[index].setSelection(curPos);

			break;
		}
		case MSG_SAVE_SORT: {
			MediaAdapter adapter = (MediaAdapter)message.obj;
			SharedPreferences.Editor editor = PlaybackService.getSettings(mActivity).edit();
			editor.putInt(String.format("sort_%d_%d", adapter.getMediaType(), adapter.getLimiterType()), adapter.getSortMode());
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
	public void loadSortOrder(MediaAdapter adapter)
	{
		String key = String.format("sort_%d_%d", adapter.getMediaType(), adapter.getLimiterType());
		int def = adapter.getDefaultSortMode();
		int sort = PlaybackService.getSettings(mActivity).getInt(key, def);
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
		MediaAdapter adapter = (MediaAdapter)mCurrentAdapter;
		if (mode == adapter.getSortMode())
			return;

		adapter.setSortMode(mode);
		requestRequery(adapter);

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
		header = (View)header.getParent(); // tag is set on parent view of header
		int type = (Integer)header.getTag();
		Intent intent = new Intent();
		intent.putExtra(LibraryAdapter.DATA_ID, LibraryAdapter.HEADER_ID);
		intent.putExtra(LibraryAdapter.DATA_TYPE, type);
		return intent;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo)
	{
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
		View targetView = info.targetView;
		Intent intent = info.id == -1 ? createHeaderIntent(targetView) : mCurrentAdapter.createData(targetView);
		mActivity.onCreateContextMenu(menu, intent);
	}

	@Override
	public void onItemClick (AdapterView<?> parent, View view, int position, long id) {
		int type = (Integer)parent.getTag();
		if (type == MediaUtils.TYPE_FILE) {
			mFilesAdapter.onViewClicked(view);
		} else {
			Intent intent = id == -1 ? createHeaderIntent(view) : mCurrentAdapter.createData(view);
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
		SharedPreferences sharedPrefs = PlaybackService.getSettings(mActivity);
		boolean shouldScroll = sharedPrefs.getBoolean(PrefKeys.ENABLE_SCROLL_TO_SONG, PrefDefaults.ENABLE_SCROLL_TO_SONG);
		if(shouldScroll) {
			int middlePos = (view.getFirstVisiblePosition() + view.getLastVisiblePosition()) / 2;
			for (int pos = 0; pos < view.getCount(); pos++) {
				if (view.getItemIdAtPosition(pos) == id) {
					if (Math.abs(middlePos - pos) < 30) {
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
