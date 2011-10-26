/*
 * Copyright (C) 2011 Christopher Eby <kreed@kreed.org>
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
import android.database.Cursor;
import android.net.Uri;

/**
 * Represents a pending query.
 */
public class QueryTask {
	private Uri mUri;
	private final String[] mProjection;
	private final String mSelection;
	private final String[] mSelectionArgs;
	private String mSortOrder;
	private long mExtra;

	/**
	 * Create the tasks. All arguments are passed directly to
	 * ContentResolver.query().
	 */
	public QueryTask(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
	{
		mUri = uri;
		mProjection = projection;
		mSelection = selection;
		mSelectionArgs = selectionArgs;
		mSortOrder = sortOrder;
	}

	/**
	 * Modify the uri of the pending query.
	 *
	 * @param uri The new uri.
	 */
	public void setUri(Uri uri)
	{
		mUri = uri;
	}

	/**
	 * Modify the sort order of the pending query.
	 *
	 * @param sortOrder The new sort order.
	 */
	public void setSortOrder(String sortOrder)
	{
		mSortOrder = sortOrder;
	}

	/**
	 * Store some extra data with this query. This data is not used at all by
	 * when running the query.
	 *
	 * @param extra The extra data
	 */
	public void setExtra(long extra)
	{
		mExtra = extra;
	}

	/**
	 * Retrieve the extra data stored by {@link QueryTask#setExtra(long)}
	 *
	 * @return The extra data
	 */
	public long getExtra()
	{
		return mExtra;
	}

	/**
	 * Run the query. Should be called on a background thread.
	 *
	 * @param resolver The ContentResolver to query with.
	 */
	public Cursor runQuery(ContentResolver resolver)
	{
		return resolver.query(mUri, mProjection, mSelection, mSelectionArgs, mSortOrder);
	}
}
