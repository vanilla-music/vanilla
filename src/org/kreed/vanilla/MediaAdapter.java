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

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.SectionIndexer;
import java.io.Serializable;
import java.util.regex.Pattern;

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
public class MediaAdapter extends CursorAdapter implements SectionIndexer {
	private static final Pattern SPACE_SPLIT = Pattern.compile("\\s+");

	/**
	 * A context to use.
	 */
	private final Context mContext;
	/**
	 * The type of media represented by this adapter. Must be one of the
	 * MediaUtils.FIELD_* constants. Determines which content provider to query for
	 * media and what fields to display.
	 */
	private final int mType;
	/**
	 * The URI of the content provider backing this adapter.
	 */
	private Uri mStore;
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
	private String[] mProjection;
	/**
	 * If true, show an expand arrow next the the text in each view.
	 */
	private final boolean mExpandable;
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
	 * The section indexer, for the letter pop-up when scrolling.
	 */
	private final MusicAlphabetIndexer mIndexer;
	/**
	 * True if this adapter should have a special MediaView with custom text in
	 * the first row.
	 */
	private final boolean mHasHeader;
	/**
	 * The text to show in the header.
	 */
	private String mHeaderText;
	/**
	 * The sort order for use with buildSongQuery().
	 */
	private String mSongSort;
	/**
	 * True if the data is stale and the query should be re-run.
	 */
	private boolean mNeedsRequery;

	/**
	 * Construct a MediaAdapter representing the given <code>type</code> of
	 * media.
	 *
	 * @param context A context to use.
	 * @param type The type of media to represent. Must be one of the
	 * Song.TYPE_* constants. This determines which content provider to query
	 * and what fields to display in the views.
	 * @param expandable Whether an expand arrow should be shown to the right
	 * of the views' text
	 * @param hasHeader Wether this view has a header row.
	 * @param limiter An initial limiter to use
	 */
	public MediaAdapter(Context context, int type, boolean expandable, boolean hasHeader, Limiter limiter)
	{
		super(context, null, false);

		mContext = context;
		mType = type;
		mExpandable = expandable;
		mHasHeader = hasHeader;
		mLimiter = limiter;
		mIndexer = new MusicAlphabetIndexer(1);
		mNeedsRequery = true;

		switch (type) {
		case MediaUtils.TYPE_ARTIST:
			mStore = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;
			mFields = new String[] { MediaStore.Audio.Artists.ARTIST };
			mFieldKeys = new String[] { MediaStore.Audio.Artists.ARTIST_KEY };
			mSongSort = MediaUtils.DEFAULT_SORT;
			break;
		case MediaUtils.TYPE_ALBUM:
			mStore = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
			mFields = new String[] { MediaStore.Audio.Albums.ARTIST, MediaStore.Audio.Albums.ALBUM };
			// Why is there no artist_key column constant in the album MediaStore? The column does seem to exist.
			mFieldKeys = new String[] { "artist_key", MediaStore.Audio.Albums.ALBUM_KEY };
			mSongSort = "album_key,track";
			break;
		case MediaUtils.TYPE_SONG:
			mStore = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
			mFields = new String[] { MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.TITLE };
			mFieldKeys = new String[] { MediaStore.Audio.Media.ARTIST_KEY, MediaStore.Audio.Media.ALBUM_KEY, MediaStore.Audio.Media.TITLE_KEY };
			break;
		case MediaUtils.TYPE_PLAYLIST:
			mStore = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI;
			mFields = new String[] { MediaStore.Audio.Playlists.NAME };
			mFieldKeys = null;
			break;
		case MediaUtils.TYPE_GENRE:
			mStore = MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI;
			mFields = new String[] { MediaStore.Audio.Genres.NAME };
			mFieldKeys = null;
			break;
		default:
			throw new IllegalArgumentException("Invalid value for type: " + type);
		}

		if (mFields.length == 1)
			mProjection = new String[] { BaseColumns._ID, mFields[0] };
		else
			mProjection = new String[] { BaseColumns._ID, mFields[mFields.length - 1], mFields[0] };
	}

	@Override
	public int getCount()
	{
		int count = super.getCount();
		if (count == 0)
			return 0;
		else if (mHasHeader)
			return count + 1;
		else
			return count;
	}

	@Override
	public Object getItem(int pos)
	{
		if (mHasHeader) {
			if (pos == 0)
				return null;
			else
				pos -= 1;
		}

		return super.getItem(pos);
	}

	@Override
	public long getItemId(int pos)
	{
		if (mHasHeader) {
			if (pos == 0)
				return -1;
			else
				pos -= 1;
		}

		return super.getItemId(pos);
	}

	@Override
	public View getView(int pos, View convertView, ViewGroup parent)
	{
		if (mHasHeader) {
			if (pos == 0) {
				MediaView view;
				if (convertView == null)
					view = new MediaView(mContext, mExpandable);
				else
					view = (MediaView)convertView;
				view.makeHeader(mHeaderText);
				return view;
			} else {
				pos -= 1;
			}
		}

		return super.getView(pos, convertView, parent);
	}

	/**
	 * Modify the header text to be shown in the first row.
	 *
	 * @param text The new text.
	 */
	public void setHeaderText(String text)
	{
		mHeaderText = text;
		notifyDataSetChanged();
	}

	/**
	 * Returns true if the data is stale and should be requeried.
	 */
	public boolean isRequeryNeeded()
	{
		return mNeedsRequery;
	}

	/**
	 * Mark the current data as requiring a requery.
	 */
	public void requestRequery()
	{
		mNeedsRequery = true;
	}

	/**
	 * Set a new filter.
	 *
	 * The data should be requeried after calling this.
	 *
	 * @param filter The terms to filter on, separated by spaces. Only
	 * media that contain all of the terms (in any order) will be displayed
	 * after filtering is complete.
	 */
	public void setFilter(String filter)
	{
		mConstraint = filter;
	}

	/**
	 * Build the query to be run with runQuery().
	 *
	 * @param projection The columns to query.
	 * @param forceMusicCheck Force the is_music check to be added to the
	 * selection.
	 */
	public QueryTask buildQuery(String[] projection, boolean forceMusicCheck)
	{
		String constraint = mConstraint;
		Limiter limiter = mLimiter;

		StringBuilder selection = new StringBuilder();
		String[] selectionArgs = null;

		String sort;
		if (limiter != null && limiter.type == MediaUtils.TYPE_ALBUM)
			sort = MediaStore.Audio.Media.TRACK;
		else if (mFieldKeys == null)
			sort = mFields[mFields.length - 1];
		else
			sort = mFieldKeys[mFieldKeys.length - 1];

		if (mType == MediaUtils.TYPE_SONG || forceMusicCheck)
			selection.append("is_music!=0");

		if (constraint != null && constraint.length() != 0) {
			String[] needles;
			String[] keySource;

			// If we are using sorting keys, we need to change our constraint
			// into a list of collation keys. Otherwise, just split the
			// constraint with no modification.
			if (mFieldKeys != null) {
				String colKey = MediaStore.Audio.keyFor(constraint);
				String spaceColKey = DatabaseUtils.getCollationKey(" ");
				needles = colKey.split(spaceColKey);
				keySource = mFieldKeys;
			} else {
				needles = SPACE_SPLIT.split(constraint);
				keySource = mFields;
			}

			int size = needles.length;
			selectionArgs = new String[size];

			StringBuilder keys = new StringBuilder(20);
			keys.append(keySource[0]);
			for (int j = 1; j != keySource.length; ++j) {
				keys.append("||");
				keys.append(keySource[j]);
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

		if (limiter != null && limiter.type == MediaUtils.TYPE_GENRE) {
			// Genre is not standard metadata for MediaStore.Audio.Media.
			// We have to query it through a separate provider. : /
			return MediaUtils.buildGenreQuery(limiter.id, projection,  selection.toString(), selectionArgs);
		} else {
			if (limiter != null) {
				if (selection.length() != 0)
					selection.append(" AND ");
				selection.append(limiter.selection);
			}

			return new QueryTask(mStore, projection, selection.toString(), selectionArgs, sort);
		}
	}

	/**
	 * Build a query to populate the adapter with. The result should be set with
	 * changeCursor().
	 */
	public QueryTask buildQuery()
	{
		mNeedsRequery = false;
		return buildQuery(mProjection, false);
	}

	/**
	 * Build a query for all the songs represented by this adapter, for adding
	 * to the timeline.
	 *
	 * @param projection The columns to query.
	 */
	public QueryTask buildSongQuery(String[] projection)
	{
		QueryTask query = buildQuery(projection, true);
		if (mType != MediaUtils.TYPE_SONG) {
			query.setUri(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
			query.setSortOrder(mSongSort);
		}
		return query;
	}

	/**
	 * Return the type of media represented by this adapter. One of
	 * MediaUtils.TYPE_*.
	 */
	public int getMediaType()
	{
		return mType;
	}

	/**
	 * Set the limiter for the adapter.
	 *
	 * A limiter is intended to restrict displayed media to only those that are
	 * children of a given parent media item.
	 *
	 * The data should be requeried after calling this.
	 *
	 * @param limiter The limiter, created by {@link MediaAdapter#getLimiter(long)}.
	 */
	public final void setLimiter(Limiter limiter)
	{
		mLimiter = limiter;
	}

	/**
	 * Returns the limiter currently active on this adapter or null if none are
	 * active.
	 */
	public final Limiter getLimiter()
	{
		return mLimiter;
	}

	/**
	 * Builds a limiter based off of the media represented by the given row.
	 *
	 * @param id The id of the row.
	 * @see MediaAdapter#getLimiter()
	 * @see MediaAdapter#setLimiter(MediaAdapter.Limiter)
	 */
	public Limiter getLimiter(long id)
	{
		String[] fields;
		String selection = null;

		Cursor cursor = getCursor();
		if (cursor == null)
			return null;
		for (int i = 0, count = cursor.getCount(); i != count; ++i) {
			cursor.moveToPosition(i);
			if (cursor.getLong(0) == id)
				break;
		}

		switch (mType) {
		case MediaUtils.TYPE_ARTIST: {
			fields = new String[] { cursor.getString(1) };
			String field = MediaStore.Audio.Media.ARTIST_ID;
			selection = String.format("%s=%d", field, id);
			break;
		}
		case MediaUtils.TYPE_ALBUM: {
			fields = new String[] { cursor.getString(2), cursor.getString(1) };
			String field = MediaStore.Audio.Media.ALBUM_ID;
			selection = String.format("%s=%d", field, id);
			break;
		}
		case MediaUtils.TYPE_GENRE:
			fields = new String[] { cursor.getString(1) };
			break;
		default:
			throw new IllegalStateException("getLimiter() is not supported for media type: " + mType);
		}

		return new MediaAdapter.Limiter(id, mType, selection, fields);
	}

	@Override
	public void changeCursor(Cursor cursor)
	{
		super.changeCursor(cursor);
		mIndexer.setCursor(cursor);
	}

	@Override
	public Object[] getSections()
	{
		return mIndexer.getSections();
	}

	@Override
	public int getPositionForSection(int section)
	{
		int offset = 0;
		if (mHasHeader) {
			if (section == 0)
				return 0;
			offset = 1;
		}
		return offset + mIndexer.getPositionForSection(section);
	}

	@Override
	public int getSectionForPosition(int position)
	{
		// never called by FastScroller
		return 0;
	}

	/**
	 * Update the values in the given view.
	 */
	@Override
	public void bindView(View view, Context context, Cursor cursor)
	{
		((MediaView)view).updateMedia(cursor, mFields.length > 1);
	}

	/**
	 * Generate a new view.
	 */
	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent)
	{
		return new MediaView(context, mExpandable);
	}

	/**
	 * Limiter is a constraint for MediaAdapters used when a row is "expanded".
	 */
	public static class Limiter implements Serializable {
		private static final long serialVersionUID = -4729694243900202614L;

		public final String[] names;
		public final long id;
		public final int type;
		public final String selection;

		public Limiter(long id, int type, String selection, String[] names)
		{
			this.type = type;
			this.names = names;
			this.id = id;
			this.selection = selection;
		}
	}
}
