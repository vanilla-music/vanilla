/*
 * Copyright (C) 2014-2016 Adrian Ulrich <adrian@blinkenlights.ch>
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

import ch.blinkenlights.android.medialibrary.MediaLibrary;

import android.content.Context;
import android.database.Cursor;
import java.util.ArrayList;

public class PlayCountsHelper {

	public PlayCountsHelper() {
	}

	/**
	 * Counts this song object as 'played' or 'skipped'
	 */
	public static void countSong(Context context, Song song, boolean played) {
		final long id = Song.getId(song);
		MediaLibrary.updateSongPlayCounts(context, id, played);
	}



	/**
	 * Returns a sorted array list of most often listen song ids
	 */
	public static ArrayList<Long> getTopSongs(Context context, int limit) {
		ArrayList<Long> payload = new ArrayList<Long>();
		Cursor cursor = MediaLibrary.queryLibrary(context, MediaLibrary.TABLE_SONGS, new String[]{ MediaLibrary.SongColumns._ID }, MediaLibrary.SongColumns.PLAYCOUNT+" > 0", null, MediaLibrary.SongColumns.PLAYCOUNT+" DESC");
		while (cursor.moveToNext() && limit > 0) {
			payload.add(cursor.getLong(0));
			limit--;
		}
		cursor.close();
		return payload;
	}

}
