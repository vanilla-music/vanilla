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

import android.content.ContentResolver;
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
	/**
	 * The activity that owns this adapter.
	 */
	private SongSelector mActivity;
	/**
	 * The type of media represented by this adapter. Must be one of the
	 * MediaUtils.FIELD_* constants. Determines which content provider to query for
	 * media and what fields to display.
	 */
	private int mType;
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
	 * If true, show an expand arrow next the the text in each view.
	 */
	private boolean mExpandable;
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
	private MusicAlphabetIndexer mIndexer;

	/**
	 * Construct a MediaAdapter representing the given <code>type</code> of
	 * media.
	 *
	 * @param activity The activity that owns this adapter.
	 * @param type The type of media to represent. Must be one of the
	 * Song.TYPE_* constants. This determines which content provider to query
	 * and what fields to display in the views.
	 * @param expandable Whether an expand arrow should be shown to the right
	 * of the views' text
	 * @param limiter An initial limiter to use
	 */
	public MediaAdapter(SongSelector activity, int type, boolean expandable, Limiter limiter)
	{
		super(activity, null, false);

		mActivity = activity;
		mType = type;
		mExpandable = expandable;
		mLimiter = limiter;
		mIndexer = new MusicAlphabetIndexer(1);

		switch (type) {
		case MediaUtils.TYPE_ARTIST:
			mStore = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;
			mFields = new String[] { MediaStore.Audio.Artists.ARTIST };
			mFieldKeys = new String[] { MediaStore.Audio.Artists.ARTIST_KEY };
			break;
		case MediaUtils.TYPE_ALBUM:
			mStore = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
			mFields = new String[] { MediaStore.Audio.Albums.ARTIST, MediaStore.Audio.Albums.ALBUM };
			// Why is there no artist_key column constant in the album MediaStore? The column does seem to exist.
			mFieldKeys = new String[] { "artist_key", MediaStore.Audio.Albums.ALBUM_KEY };
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
	}

	/**
	 * Perform filtering on a background thread.
	 *
	 * @param constraint The terms to filter on, separated by spaces. Only
	 * media that contain all of the terms (in any order) will be displayed
	 * after filtering is complete.
	 */
	public void filter(String constraint)
	{
		mConstraint = constraint;
		mActivity.runQuery(this);
	}

	/**
	 * Query the backing content provider. Should be called on a background
	 * thread.
	 */
	public void runQuery()
	{
		ContentResolver resolver = mActivity.getContentResolver();
		Cursor cursor;

		String constraint = mConstraint;
		Limiter limiter = mLimiter;

		String[] projection;
		if (mFields.length == 1)
			projection = new String[] { BaseColumns._ID, mFields[0] };
		else
			projection = new String[] { BaseColumns._ID, mFields[mFields.length - 1], mFields[0] };

		StringBuilder selection = new StringBuilder();
		String[] selectionArgs = null;

		String sort;
		if (mLimiter != null && mLimiter.type == MediaUtils.TYPE_ALBUM)
			sort = MediaStore.Audio.Media.TRACK;
		else if (mFieldKeys == null)
			sort = mFields[mFields.length - 1];
		else
			sort = mFieldKeys[mFieldKeys.length - 1];

		if (mType == MediaUtils.TYPE_SONG)
			selection.append("is_music!=0");

		if (constraint != null && constraint.length() != 0) {
			String[] needles;

			// If we are using sorting keys, we need to change our constraint
			// into a list of collation keys. Otherwise, just split the
			// constraint with no modification.
			if (mFieldKeys != null) {
				String colKey = MediaStore.Audio.keyFor(constraint);
				String spaceColKey = DatabaseUtils.getCollationKey(" ");
				needles = colKey.split(spaceColKey);
			} else {
				needles = constraint.split("\\s+");
			}

			int size = needles.length;
			selectionArgs = new String[size];

			String[] keySource = mFieldKeys == null ? mFields : mFieldKeys;
			String keys = keySource[0];
			for (int j = 1; j != keySource.length; ++j)
				keys += "||" + keySource[j];

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
			QueryTask query = MediaUtils.buildGenreQuery(mLimiter.id, projection,  selection.toString(), selectionArgs);
			cursor = query.runQuery(resolver);
		} else {
			if (limiter != null) {
				if (selection.length() != 0)
					selection.append(" AND ");
				selection.append(mLimiter.selection);
			}

			cursor = resolver.query(mStore, projection, selection.toString(), selectionArgs, sort);
		}

		mActivity.changeCursor(this, cursor);
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
	 * Set the limiter for the adapter. A limiter is intended to restrict
	 * displayed media to only those that are children of a given parent
	 * media item.
	 *
	 * @param limiter The limiter, created by MediaView.getLimiter()
	 */
	public final void setLimiter(Limiter limiter)
	{
		mLimiter = limiter;
		mActivity.runQuery(this);
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
	 * @param view The row to create the limiter from.
	 * @see MediaAdapter#getLimiter()
	 * @see MediaAdapter#setLimiter(MediaAdapter.Limiter)
	 */
	public Limiter getLimiter(MediaView view)
	{
		long id = view.getMediaId();
		String[] fields;
		String selection = null;

		switch (mType) {
		case MediaUtils.TYPE_ARTIST: {
			fields = new String[] { view.getTitle() };
			String field = MediaStore.Audio.Media.ARTIST_ID;
			selection = String.format("%s=%d", field, id);
			break;
		}
		case MediaUtils.TYPE_ALBUM: {
			fields = new String[] { view.getSubTitle(), view.getTitle() };
			String field = MediaStore.Audio.Media.ALBUM_ID;
			selection = String.format("%s=%d", field, id);
			break;
		}
		case MediaUtils.TYPE_GENRE:
			fields = new String[] { view.getTitle() };
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
		return mIndexer.getPositionForSection(section);
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
		return new MediaView(context, this, mExpandable);
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
