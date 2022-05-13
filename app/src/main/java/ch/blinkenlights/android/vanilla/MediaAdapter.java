/*
 * Copyright (C) 2015-2018 Adrian Ulrich <adrian@blinkenlights.ch>
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

package ch.blinkenlights.android.vanilla;

import ch.blinkenlights.android.medialibrary.MediaLibrary;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SectionIndexer;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.lang.StringBuilder;


/**
 * MediaAdapter provides an adapter backed by a MediaStore content provider.
 * It generates simple one- or two-line text views to display each media
 * element.
 *
 * Filtering is supported, as is a more specific type of filtering referred to
 * as limiting. Limiting is separate from filtering; a new filter will not
 * erase an active filter. Limiting is intended to allow only media belonging
 * to a specific group to be displayed, e.g. only songs from a certain artist.
 * See getLimiter and setLimiter for details.
 */
public class MediaAdapter
		extends SortableAdapter
		implements LibraryAdapter
		, View.OnClickListener
		, SectionIndexer
{
	private static final Pattern SPACE_SPLIT = Pattern.compile("\\s+");
	/**
	 * The string to use for length==0 db fields
	 */
	private static final String DB_NULLSTRING_FALLBACK = "?";
	/**
	 * A context to use.
	 */
	private final Context mContext;
	/**
	 * The library activity to use.
	 */
	private final LibraryActivity mActivity;
	/**
	 * A LayoutInflater to use.
	 */
	private final LayoutInflater mInflater;
	/**
	 * The current data.
	 */
	private Cursor mCursor;
	/**
	 * The type of media represented by this adapter. Must be one of the
	 * MediaUtils.FIELD_* constants. Determines which content provider to query for
	 * media and what fields to display.
	 */
	private final int mType;
	/**
	 * The table / view to use for this query
	 */
	private String mSource;
	/**
	 * The fields to use from the content provider. The last field will be
	 * displayed in the MediaView, as will the first field if there are
	 * multiple fields. Other fields will be used for searching.
	 */
	private String[] mFields;
	/**
	 * The collation keys corresponding to each field. If provided, these are
	 * used to speed up sorting and filtering.
	 */
	private String[] mFieldKeys;
	/**
	 * The columns to query from the content provider.
	 */
	private String[] mColumns;
	/**
	 * A limiter is used for filtering. The intention is to restrict items
	 * displayed in the list to only those of a specific artist or album, as
	 * selected through an expander arrow in a broader MediaAdapter list.
	 */
	private Limiter mLimiter;
	/**
	 * The constraint used for filtering, set by the search box.
	 */
	private String mConstraint;
	/**
	 * An array ORDER BY expressions for each sort mode. %1$s is replaced by
	 * ASC or DESC as appropriate before being passed to the query.
	 */
	private String[] mAdapterSortValues;
	/**
	 * If true, show the expander button on each row.
	 */
	private boolean mExpandable;
	/**
	 * Defines the media type to use for this entry
	 * Setting this to MediaUtils.TYPE_INVALID disables cover artwork
	 */
	private int mCoverCacheType;
	/**
	 * Alphabet to be used for {@link SectionIndexer}. Populated in {@link #buildAlphabet()}.
	 */
	private List<SectionIndex> mAlphabet = new ArrayList<>(512);

	/**
	 * Construct a MediaAdapter representing the given <code>type</code> of
	 * media.
	 *
	 * @param context The Context used to access the content model.
	 * @param type The type of media to represent. Must be one of the
	 * MediaUtils.TYPE_* constants. This determines which content provider to query
	 * and what fields to display in the views.
	 * @param limiter An initial limiter to use
	 * @param activity The LibraryActivity that will contain this adapter - may be null
	 *
	 */
	public MediaAdapter(Context context, int type, Limiter limiter, LibraryActivity activity)
	{
		mContext = context;
		mActivity = activity;
		mType = type;
		mLimiter = limiter;

		if (mActivity != null) {
			mInflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		} else {
			mInflater = null; // not running inside an activity
		}

		// Use media type + base id as cache key combination
		mCoverCacheType = mType;
		String coverCacheKey = BaseColumns._ID;

		switch (type) {
		case MediaUtils.TYPE_ARTIST:
			mSource = MediaLibrary.VIEW_ARTISTS;
			mFields = new String[] { MediaLibrary.ContributorColumns.ARTIST };
			mFieldKeys = new String[] { MediaLibrary.ContributorColumns.ARTIST_SORT };
			mSortEntries = new int[] { R.string.title, R.string.date_added };
			mAdapterSortValues = new String[] { MediaLibrary.ContributorColumns.ARTIST_SORT+" %1$s", MediaLibrary.ContributorColumns.MTIME+" %1$s" };
			break;
		case MediaUtils.TYPE_ALBARTIST:
			mSource = MediaLibrary.VIEW_ALBUMARTISTS;
			mFields = new String[] { MediaLibrary.ContributorColumns.ALBUMARTIST };
			mFieldKeys = new String[] { MediaLibrary.ContributorColumns.ALBUMARTIST_SORT };
			mSortEntries = new int[] { R.string.title, R.string.date_added };
			mAdapterSortValues = new String[] { MediaLibrary.ContributorColumns.ALBUMARTIST_SORT+" %1$s", MediaLibrary.ContributorColumns.MTIME+" %1$s" };
			break;
		case MediaUtils.TYPE_COMPOSER:
			mSource = MediaLibrary.VIEW_COMPOSERS;
			mFields = new String[] { MediaLibrary.ContributorColumns.COMPOSER };
			mFieldKeys = new String[] { MediaLibrary.ContributorColumns.COMPOSER_SORT };
			mSortEntries = new int[] { R.string.title, R.string.date_added };
			mAdapterSortValues = new String[] { MediaLibrary.ContributorColumns.COMPOSER_SORT+" %1$s", MediaLibrary.ContributorColumns.MTIME+" %1$s" };
			break;
		case MediaUtils.TYPE_ALBUM:
			mSource = MediaLibrary.VIEW_ALBUMS_ARTISTS;
			mFields = new String[] { MediaLibrary.AlbumColumns.ALBUM, MediaLibrary.ContributorColumns.ARTIST, MediaLibrary.SongColumns.DURATION };
			mFieldKeys = new String[] { MediaLibrary.AlbumColumns.ALBUM_SORT, MediaLibrary.ContributorColumns.ARTIST_SORT };
			mSortEntries = new int[] { R.string.title, R.string.artist_album, R.string.year, R.string.date_added, R.string.duration };
			mAdapterSortValues = new String[] { MediaLibrary.AlbumColumns.ALBUM_SORT+" %1$s",
												MediaLibrary.ContributorColumns.ARTIST_SORT+" %1$s,"+MediaLibrary.AlbumColumns.ALBUM_SORT+" %1$s",
			                                    MediaLibrary.AlbumColumns.PRIMARY_ALBUM_YEAR+" %1$s", MediaLibrary.AlbumColumns.MTIME+" %1$s",
												MediaLibrary.SongColumns.DURATION+" %1$s" };
			break;
		case MediaUtils.TYPE_SONG:
			mSource = MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS;
			mFields = new String[] { MediaLibrary.SongColumns.TITLE, MediaLibrary.AlbumColumns.ALBUM, MediaLibrary.ContributorColumns.ALBUMARTIST, MediaLibrary.SongColumns.DURATION };
			mFieldKeys = new String[] { MediaLibrary.SongColumns.TITLE_SORT, MediaLibrary.AlbumColumns.ALBUM_SORT, MediaLibrary.ContributorColumns.ALBUMARTIST_SORT, null };
			mSortEntries = new int[] { R.string.title,
					                   R.string.artist_album_track,
					                   R.string.artist_album_title,
					                   R.string.album_track,
					                   R.string.year,
					                   R.string.date_added,
			                       R.string.song_playcount,
					                   R.string.filename,
					                   R.string.duration };
			mAdapterSortValues = new String[] { MediaLibrary.SongColumns.TITLE_SORT+" %1$s",
			                                    MediaLibrary.ContributorColumns.ALBUMARTIST_SORT+" %1$s,"+MediaLibrary.AlbumColumns.ALBUM_SORT+" %1$s,"+MediaLibrary.SongColumns.DISC_NUMBER+","+MediaLibrary.SongColumns.SONG_NUMBER,
			                                    MediaLibrary.ContributorColumns.ALBUMARTIST_SORT+" %1$s,"+MediaLibrary.AlbumColumns.ALBUM_SORT+" %1$s,"+MediaLibrary.SongColumns.TITLE_SORT+" %1$s",
			                                    MediaLibrary.AlbumColumns.ALBUM_SORT+" %1$s,"+MediaLibrary.SongColumns.DISC_NUMBER+","+MediaLibrary.SongColumns.SONG_NUMBER,
			                                    MediaLibrary.SongColumns.YEAR+" %1$s,"+MediaLibrary.AlbumColumns.ALBUM_SORT+" %1$s,"+MediaLibrary.SongColumns.DISC_NUMBER+","+MediaLibrary.SongColumns.SONG_NUMBER,
			                                    MediaLibrary.SongColumns.MTIME+" %1$s,"+MediaLibrary.SongColumns.DISC_NUMBER+","+MediaLibrary.SongColumns.SONG_NUMBER,
			                                    MediaLibrary.SongColumns.PLAYCOUNT+" %1$s,"+MediaLibrary.SongColumns.DISC_NUMBER+","+MediaLibrary.SongColumns.SONG_NUMBER,
			                                    MediaLibrary.SongColumns.PATH+" %1$s",
			                                    MediaLibrary.SongColumns.DURATION+" %1$s",
			                                  };
			// Songs covers are cached per-album
			mCoverCacheType = MediaUtils.TYPE_ALBUM;
			coverCacheKey = MediaStore.Audio.Albums.ALBUM_ID;
			break;
		case MediaUtils.TYPE_PLAYLIST:
			mSource = MediaLibrary.VIEW_PLAYLISTS;
			mFields = new String[] { MediaLibrary.PlaylistColumns.NAME, MediaLibrary.SongColumns.DURATION };
			mFieldKeys = new String[] { MediaLibrary.PlaylistColumns.NAME_SORT };
			mSortEntries = new int[] { R.string.title, R.string.date_added, R.string.duration };
			mAdapterSortValues = new String[] { MediaLibrary.PlaylistColumns.NAME_SORT+" %1$s", MediaLibrary.PlaylistColumns._ID+" %1$s",
			                                    MediaLibrary.SongColumns.DURATION+" %1$s" };
			mExpandable = true;
			break;
		case MediaUtils.TYPE_GENRE:
			mSource = MediaLibrary.TABLE_GENRES;
			mFields = new String[] { MediaLibrary.GenreColumns._GENRE };
			mFieldKeys = new String[] { MediaLibrary.GenreColumns._GENRE_SORT };
			mSortEntries = new int[] { R.string.title };
			mAdapterSortValues = new String[] { MediaLibrary.GenreColumns._GENRE_SORT+" %1$s" };
			break;
		default:
			throw new IllegalArgumentException("Invalid value for type: " + type);
		}


		mColumns = new String[mFields.length + 2];
		mColumns[0] = BaseColumns._ID;
		mColumns[1] = coverCacheKey;
		for (int i = 0; i < mFields.length; i++) {
			mColumns[i + 2] = mFields[i];
		}
	}

	/**
	 * Returns first sort column for this adapter. Ensure {@link #mSortMode} is correctly set
	 * prior to calling this.
	 *
	 * @return string representing sort column to be used in projection.
	 * 		   If the column is binary, returns its human-readable counterpart instead.
	 */
	private String getFirstSortColumn() {
		int mode = mSortMode < 0 ? ~mSortMode : mSortMode; // get current sort mode
		String column = SPACE_SPLIT.split(mAdapterSortValues[mode])[0];
		if(column.endsWith("_sort")) { // we want human-readable string, not machine-composed
			column = column.substring(0, column.length() - 5);
		}

		return column;
	}

	/**
	 * Set whether or not the expander button should be shown in each row.
	 * Defaults to true for playlist adapter and false for all others.
	 *
	 * @param expandable True to show expander, false to hide.
	 */
	public void setExpandable(boolean expandable)
	{
		if (expandable != mExpandable) {
			mExpandable = expandable;
			notifyDataSetChanged();
		}
	}

	@Override
	public void setFilter(String filter)
	{
		mConstraint = filter;
	}

	/**
	 * Build the query to be run with runQuery().
	 *
	 * @param columns The columns to query.
	 * @param returnSongs return songs instead of mType if true.
	 */
	private QueryTask buildQuery(String[] columns, boolean returnSongs) {
		String source = mSource;
		String constraint = mConstraint;
		Limiter limiter = mLimiter;

		StringBuilder selection = new StringBuilder();
		String[] selectionArgs = null;
		String[] enrichedProjection = columns;

		// Assemble the sort string as requested by the user
		int mode = getSortModeIndex();
		String sortDir = isSortDescending() ? "DESC" : "ASC";

		// Fetch current sorting mode and sort by disc+track if we are going to look up the songs table
		String sortRaw = mAdapterSortValues[mode];
		if (returnSongs) {
			// songs returned from the artist tab should also sort by album
			if (mType == MediaUtils.TYPE_ARTIST) // fixme: composer?
				sortRaw += ", "+MediaLibrary.AlbumColumns.ALBUM_SORT+" %1$s";
			// and this is for all types:
			sortRaw += ", "+MediaLibrary.SongColumns.DISC_NUMBER+", "+MediaLibrary.SongColumns.SONG_NUMBER;
		}

		// ...and assemble the SQL string we are really going to use
		String sort = String.format(sortRaw, sortDir);

		// include the constraint (aka: search string) if any
		if (constraint != null && constraint.length() != 0) {
			String colKey = MediaLibrary.keyFor(constraint);
			String spaceColKey = DatabaseUtils.getCollationKey(" ");
			String[] needles = colKey.split(spaceColKey);
			String[] keySource = mFieldKeys;

			int size = needles.length;
			selectionArgs = new String[size];

			StringBuilder keys = new StringBuilder(20);
			keys.append(keySource[0]);
			for (int j = 1; j != keySource.length; ++j) {
				String src = keySource[j];
				if (src != null) {
					keys.append("||");
					keys.append(src);
				}
			}

			for (int j = 0; j != needles.length; ++j) {
				selectionArgs[j] = '%' + needles[j] + '%';

				// If we have something in the selection args (i.e. j > 0), we
				// must have something in the selection, so we can skip the more
				// costly direct check of the selection length.
				if (j != 0 || selection.length() != 0)
					selection.append(" AND ");
				selection.append(keys);
				selection.append(" LIKE ?");
			}
		}

		if (limiter != null) {
			if (selection.length() != 0) {
				selection.append(" AND ");
			}
			selection.append(limiter.data);
		}

		if (returnSongs == true) {
			source = MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS_HUGE;
		} else {
			enrichedProjection = Arrays.copyOf(columns, columns.length + 1);
			enrichedProjection[columns.length] = getFirstSortColumn();
		}

		QueryTask query = new QueryTask(source, enrichedProjection, selection.toString(), selectionArgs, sort);
		return query;
	}

	@Override
	public Cursor query()
	{
		return buildQuery(mColumns, false).runQuery(mContext);
	}

	@Override
	public void commitQuery(Object data)
	{
		changeCursor((Cursor)data);
	}

	/**
	 * Build a query for all the songs represented by this adapter, for adding
	 * to the timeline.
	 *
	 * @param columns The columns to query.
	 */
	@Override
	public QueryTask buildSongQuery(String[] columns)
	{
		QueryTask query = buildQuery(columns, true);
		query.type = mType;
		return query;
	}

	@Override
	public void clear()
	{
		changeCursor(null);
	}

	@Override
	public int getMediaType()
	{
		return mType;
	}

	@Override
	public void setLimiter(Limiter limiter)
	{
		mLimiter = limiter;
	}

	@Override
	public Limiter getLimiter()
	{
		return mLimiter;
	}

	@Override
	public Limiter buildLimiter(long id)
	{
		String[] fields;
		Object data;

		Cursor cursor = mCursor;
		if (cursor == null)
			return null;
		for (int i = 0, count = cursor.getCount(); i != count; ++i) {
			cursor.moveToPosition(i);
			long rowId = cursor.getLong(0);
			if (rowId == id)
				break;
		}

		switch (mType) {
		case MediaUtils.TYPE_ARTIST:
			String artistName = cursor.getString(2);
			fields = new String[] { artistName };
			data = String.format("%s=%d", MediaLibrary.ContributorColumns.ARTIST_ID, id);
			break;
		case MediaUtils.TYPE_ALBARTIST:
			String albumArtistName = cursor.getString(2);
			fields = new String[] { albumArtistName };
			data = String.format("%s=%d", MediaLibrary.ContributorColumns.ALBUMARTIST_ID, id);
			break;
		case MediaUtils.TYPE_COMPOSER:
			String composer = cursor.getString(2);
			fields = new String[] { composer };
			data = String.format("%s=%d", MediaLibrary.ContributorColumns.COMPOSER_ID, id);
			break;
		case MediaUtils.TYPE_ALBUM:
			String primaryArtistName = cursor.getString(3);
			String albumName = cursor.getString(2);
			fields = new String[] { primaryArtistName, albumName };
			data = String.format("%s=%d",  MediaLibrary.SongColumns.ALBUM_ID, id);
			break;
		case MediaUtils.TYPE_GENRE:
			String genreName = cursor.getString(2);
			fields = new String[] { genreName };
			data = String.format("%s=%d", MediaLibrary.GenreSongColumns._GENRE_ID, id);
			break;
		default:
			throw new IllegalStateException("getLimiter() is not supported for media type: " + mType);
		}

		return new Limiter(mType, fields, data);
	}

	/**
	 * Set a new cursor for this adapter. The old cursor will be closed.
	 *
	 * @param cursor The new cursor.
	 */
	public void changeCursor(Cursor cursor)
	{
		Cursor old = mCursor;
		mCursor = cursor;
		buildAlphabet();
		if (cursor == null) {
			notifyDataSetInvalidated();
		} else {
			notifyDataSetChanged();
		}
		if (old != null) {
			old.close();
		}
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		DraggableRow row;
		ViewHolder holder;

		if (convertView == null) {
			// We must create a new view if we're not given a recycle view or
			// if the recycle view has the wrong layout.
			row = (DraggableRow)mInflater.inflate(R.layout.draggable_row, parent, false);
			row.setupLayout(DraggableRow.LAYOUT_LISTVIEW);

			holder = new ViewHolder();
			row.setTag(holder);

			row.setDraggerOnClickListener(this);
			row.showDragger(mExpandable);
		} else {
			row = (DraggableRow)convertView;
			holder = (ViewHolder)row.getTag();
		}

		Cursor cursor = mCursor;
		cursor.moveToPosition(position);

		long id = cursor.getLong(0);
		long cacheId = cursor.getLong(1);
		String title = cursor.getString(2);
		String subtitle = null;
		long duration = -1;

		// Title is never null, subtitle may be depending on the type.
		title = (title == null ? DB_NULLSTRING_FALLBACK : title);

		// Add subtitle if media type has one.
		switch (mType) {
		case MediaUtils.TYPE_ALBUM:
		case MediaUtils.TYPE_SONG:
			subtitle = cursor.getString(3);
			subtitle = (subtitle == null ? DB_NULLSTRING_FALLBACK : subtitle);

			// Add album information for songs.
			String subsub = (mType == MediaUtils.TYPE_SONG ? cursor.getString(4) : null);
			subtitle += (subsub != null ? " · " + subsub : "");
			break;
		}

		// Pick up duration
		switch (mType) {
		case MediaUtils.TYPE_ALBUM:
			duration = cursor.getLong(4);
			break;
		case MediaUtils.TYPE_SONG:
			duration = cursor.getLong(5);
			break;
		case MediaUtils.TYPE_PLAYLIST:
			duration = cursor.getLong(3);
			break;
		}

		holder.id = id;
		holder.title = title;

		if (subtitle == null) {
			row.setText(title);
		} else {
			row.setText(title, subtitle);
		}
		row.showDuration(duration != -1);
		row.setDuration(duration);
		row.getCoverView().setCover(mCoverCacheType, cacheId, holder.title);
		return row;
	}

	/**
	 * Returns the type of the current limiter.
	 *
	 * @return One of MediaUtils.TYPE_, or MediaUtils.TYPE_INVALID if there is
	 * no limiter set.
	 */
	public int getLimiterType()
	{
		Limiter limiter = mLimiter;
		if (limiter != null)
			return limiter.type;
		return MediaUtils.TYPE_INVALID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getDefaultSortMode()
	{
		int type = mType;
		if (type == MediaUtils.TYPE_ALBUM || type == MediaUtils.TYPE_SONG)
			return 1; // aritst,album,track
		return 0;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String getSortSettingsKey() {
		return String.format("sort_%d_%d", getMediaType(), getLimiterType());
	}

	/**
	 * Creates an intent to dispatch
	 */
	@Override
	public Intent createData(View view)
	{
		ViewHolder holder = (ViewHolder)view.getTag();
		Intent intent = new Intent();
		intent.putExtra(LibraryAdapter.DATA_TYPE, mType);
		intent.putExtra(LibraryAdapter.DATA_ID, holder.id);
		intent.putExtra(LibraryAdapter.DATA_TITLE, holder.title);
		intent.putExtra(LibraryAdapter.DATA_EXPANDABLE, mExpandable);
		return intent;
	}

	/**
	 * Callback of array clicks (item clicks are handled in LibraryPagerAdapter)
	 */
	@Override
	public void onClick(View view)
	{
		int id = view.getId();
		view = (View)view.getParent(); // get view of linear layout, not the click consumer
		Intent intent = createData(view);
		mActivity.onItemExpanded(intent);
	}

	@Override
	public int getCount()
	{
		Cursor cursor = mCursor;
		if (cursor == null)
			return 0;
		return cursor.getCount();
	}

	@Override
	public Object getItem(int position)
	{
		return null;
	}

	@Override
	public long getItemId(int position)
	{
		Cursor cursor = mCursor;
		if (cursor == null || cursor.getCount() == 0)
			return 0;
		cursor.moveToPosition(position);
		return cursor.getLong(0);
	}

	@Override
	public boolean hasStableIds()
	{
		return true;
	}

	/**
	 * Helper class for building an alphabet.
	 * Contains a hint that is shown in fast scroll thumb popup and a position where this hint
	 * has appeared first.
	 */
	private class SectionIndex
	{

		public SectionIndex(Object hint, int position) {
			this.hint = hint;
			this.position = position;
		}

		private Object hint;
		private int position;

		@Override
		public String toString() {
			return String.valueOf(hint);
		}
	}

	/**
	 * Build alphabet for fast-scroller. Detects automatically whether we're sorting
	 * on string-type (e.g. title or album) or integer type (e.g. year).
	 *
	 * <p/>Alphabet building is only performed if applicable, i.e. magic playcount sort
	 * or sort by date added will yield no results as the section hints would not be
	 * human-readable.
	 *
	 * <p/>Note: This clears alphabet in case current cursor is invalid.
	 */
	private void buildAlphabet()
	{
		mAlphabet.clear();

		Cursor cursor = mCursor;
		if(cursor == null || cursor.getCount() == 0) {
			return;
		}

		String columnName = getFirstSortColumn();
		int sortColumnIndex = cursor.getColumnIndex(columnName);
		if(sortColumnIndex <= 0) {
			// either projection doesn't contain this column
			// or the column is _id (e.g. sort by date added),
			// no point in building
			return;
		}

		cursor.moveToFirst();

		SimpleDateFormat dfmt = new SimpleDateFormat("yyyy-MM-dd");
		String lastString = null;
		Object lastKnown = null;
		Object next;
		do {
			int type = cursor.getType(sortColumnIndex);
			switch (type) {
				case Cursor.FIELD_TYPE_NULL:
					next = DB_NULLSTRING_FALLBACK;
					break;
				case Cursor.FIELD_TYPE_INTEGER:
					int value = cursor.getInt(sortColumnIndex);
					if (columnName.equals(MediaLibrary.SongColumns.MTIME)) {
						next = dfmt.format(new Date(value * 1000L));
					} else if (columnName.equals(MediaLibrary.SongColumns.DURATION)) {
						next = DateUtils.formatElapsedTime(value / 1000);
					} else {
						next = value;
					}
					break;
				case Cursor.FIELD_TYPE_STRING:
					lastString = cursor.getString(sortColumnIndex);
					lastString = lastString.trim().toUpperCase(); // normalize

					// This is what AOSP's MediaStore.java:1337 does during indexing
					if (lastString.startsWith("THE "))
						lastString = lastString.substring(4);

					if (lastString.startsWith("AN "))
						lastString = lastString.substring(3);

					if (lastString.startsWith("A "))
						lastString = lastString.substring(2);

					// Ensure that we got at least one char
					if (lastString.length() < 1)
						lastString = DB_NULLSTRING_FALLBACK;

					next = lastString.charAt(0);
					break;
				default:
					continue;
			}
			if (!next.equals(lastKnown)) { // new char
				mAlphabet.add(new SectionIndex(next, cursor.getPosition()));
				lastKnown = next;
			}
		} while (cursor.moveToNext());
	}

	@Override
	public Object[] getSections()
	{
		return mAlphabet.toArray(new SectionIndex[mAlphabet.size()]);
	}

	@Override
	public int getPositionForSection(int sectionIndex)
	{
		// clip to start
		if(sectionIndex < 0) {
			return 0;
		}

		// clip to end
		if(sectionIndex >= mAlphabet.size()) {
			return mCursor.getCount() - 1;
		}

		return mAlphabet.get(sectionIndex).position;
	}

	@Override
	public int getSectionForPosition(int position)
	{
		for(int i = 0; i < mAlphabet.size(); ++i) {
			if(mAlphabet.get(i).position > position)
				return i - 1;
		}
		return 0;
	}
}
