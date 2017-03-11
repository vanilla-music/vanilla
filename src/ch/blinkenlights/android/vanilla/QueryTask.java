/*
 * Copyright (C) 2016 Adrian Ulrich <adrian@blinkenlights.ch>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>. 
 */


package ch.blinkenlights.android.vanilla;

import android.content.Context;
import android.database.Cursor;

import ch.blinkenlights.android.medialibrary.MediaLibrary;

/**
 * Represents a pending query.
 */
public class QueryTask {
	public final String table;
	public final String[] projection;
	public final String selection;
	public final String[] selectionArgs;
	public String sortOrder;

	/**
	 * Used for {@link SongTimeline#addSongs(android.content.Context, QueryTask)}.
	 * One of SongTimeline.MODE_*.
	 */
	public int mode;

	/**
	 * Type of the group being query. One of MediaUtils.TYPE_*.
	 */
	public int type;

	/**
	 * Data. Required value depends on value of mode. See individual mode
	 * documentation for details.
	 */
	public long data;

	/**
	 * Additional data applicable to certain mode values. may be null
	 */
	public Object modeData;

	/**
	 * Create the tasks. All arguments are passed directly to
	 * MediaLibrary.runQuery().
	 */
	public QueryTask(String table, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		this.table = table;
		this.projection = projection;
		this.selection = selection;
		this.selectionArgs = selectionArgs;
		this.sortOrder = sortOrder;
	}

	/**
	 * Run the query. Should be called on a background thread.
	 *
	 * @param context The Context to use
	 */
	public Cursor runQuery(Context context) {
		return MediaLibrary.queryLibrary(context, table, projection, selection, selectionArgs, sortOrder);
	}
}
