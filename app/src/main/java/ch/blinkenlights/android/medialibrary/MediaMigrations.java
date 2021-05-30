/*
 * Copyright (C) 2018 Adrian Ulrich <adrian@blinkenlights.ch>
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

package ch.blinkenlights.android.medialibrary;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import android.database.Cursor;
import android.util.Log;


public class MediaMigrations {
	/**
	 * Migrate to 20180416
	 * That is: populate NAME_SORT in the new source database
	 *
	 * @param dbh the database to work on
	 * @param fromDb the name of the source database
	 * @param toDb the name of the target database
	 **/
	static void migrate_to_20180416(SQLiteDatabase dbh, String fromDb, String toDb) {
		Cursor cursor = dbh.query(fromDb, new String[]{MediaLibrary.PlaylistColumns._ID, MediaLibrary.PlaylistColumns.NAME}, null, null, null, null, null);
		while (cursor.moveToNext()) {
			long id = cursor.getLong(0);
			String name = cursor.getString(1);
			String key = MediaLibrary.keyFor(name);

			Log.v("VanillaMusic", "migrate_to_20180416 -> id="+id+", name="+name+" -> key = "+key);
			ContentValues v = new ContentValues();
			v.put(MediaLibrary.PlaylistColumns._ID, id);
			v.put(MediaLibrary.PlaylistColumns.NAME, name);
			v.put(MediaLibrary.PlaylistColumns.NAME_SORT, key);
			dbh.insert(toDb, null, v);
		}
		cursor.close();
	}

	/**
	 * Migrate to 20190228
	 * Add fields for track-timestamp and albumposition
	 *
	 * @param dbh the database to work on
	 **/
	static void migrate_to_20190228(SQLiteDatabase dbh) {
		Log.v("VanillaMusic", "migrate_to_20190228");
		dbh.execSQL("ALTER TABLE "+MediaLibrary.TABLE_ALBUMS+" ADD COLUMN "+MediaLibrary.AlbumColumns.LAST_TRACK_PLAYED+" INTEGER;");
		dbh.execSQL("ALTER TABLE "+MediaLibrary.TABLE_ALBUMS+" ADD COLUMN "+MediaLibrary.AlbumColumns.START_AT_TRACKTIMESTAMP+" INTEGER DEFAULT -1;");
		dbh.execSQL("ALTER TABLE "+MediaLibrary.TABLE_SONGS+" ADD COLUMN "+MediaLibrary.SongColumns.TIMESTAMP_LAST_PLAY+" INTEGER;");


	}

}
