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

import android.content.Context;
import android.content.ContentResolver;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.Cursor;
import android.util.Log;
import java.util.ArrayList;

public class PlayCountsHelper extends SQLiteOpenHelper {

	/**
	 * SQL constants and CREATE TABLE statements used by 
	 * this java class
	 */
	private static final int DATABASE_VERSION = 2;
	private static final String DATABASE_NAME = "playcounts.db";
	private static final String TABLE_PLAYCOUNTS = "playcounts";
	private static final String DATABASE_CREATE = "CREATE TABLE "+TABLE_PLAYCOUNTS + " ("
	  + "type      INTEGER, "
	  + "type_id   BIGINT, "
	  + "playcount INTEGER, "
	  + "skipcount INTEGER);";
	private static final String INDEX_UNIQUE_CREATE = "CREATE UNIQUE INDEX idx_uniq ON "+TABLE_PLAYCOUNTS
	  + " (type, type_id);";
	private static final String INDEX_TYPE_CREATE = "CREATE INDEX idx_type ON "+TABLE_PLAYCOUNTS
	  + " (type);";

	private Context ctx;

	public PlayCountsHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		ctx = context;
	}

	@Override
	public void onCreate(SQLiteDatabase dbh) {
		dbh.execSQL(DATABASE_CREATE);
		dbh.execSQL(INDEX_UNIQUE_CREATE);
		dbh.execSQL(INDEX_TYPE_CREATE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase dbh, int oldVersion, int newVersion) {
		if (oldVersion < 2) {
			dbh.execSQL("ALTER TABLE "+TABLE_PLAYCOUNTS+" ADD COLUMN skipcount INTEGER");
			dbh.execSQL("UPDATE "+TABLE_PLAYCOUNTS+" SET skipcount=0");
		}
	}

	/**
	 * Counts this song object as 'played' or 'skipped'
	 */
	public void countSong(Song song, boolean played) {
		long id = Song.getId(song);
		final String column = played ? "playcount" : "skipcount";

		SQLiteDatabase dbh = getWritableDatabase();
		dbh.execSQL("INSERT OR IGNORE INTO "+TABLE_PLAYCOUNTS+" (type, type_id, playcount, skipcount) VALUES ("+MediaUtils.TYPE_SONG+", "+id+", 0, 0);"); // Creates row if not exists
		dbh.execSQL("UPDATE "+TABLE_PLAYCOUNTS+" SET "+column+"="+column+"+1 WHERE type="+MediaUtils.TYPE_SONG+" AND type_id="+id+";");
		dbh.close();

		performGC(MediaUtils.TYPE_SONG);
	}



	/**
	 * Returns a sorted array list of most often listen song ids
	 */
	public ArrayList<Long> getTopSongs(int limit) {
		ArrayList<Long> payload = new ArrayList<Long>();
		SQLiteDatabase dbh = getReadableDatabase();

		Cursor cursor = dbh.rawQuery("SELECT type_id FROM "+TABLE_PLAYCOUNTS+" WHERE type="+MediaUtils.TYPE_SONG+" AND playcount != 0 ORDER BY playcount DESC limit "+limit, null);

		while (cursor.moveToNext()) {
			payload.add(cursor.getLong(0));
		}

		cursor.close();
		dbh.close();
		return payload;
	}

	/**
	 * Picks a random amount of 'type' items from the provided DBH
	 * and checks them against Androids media database.
	 * Items not found in the media library are removed from the DBH's database
	 */
	private int performGC(int type) {
		SQLiteDatabase dbh      = getWritableDatabase();
		ArrayList<Long> toCheck = new ArrayList<Long>(); // List of songs we are going to check
		QueryTask query;                                 // Reused query object
		Cursor cursor;                                   // recycled cursor
		int removed = 0;                                 // Amount of removed items

		// We are just grabbing a bunch of random IDs
		cursor = dbh.rawQuery("SELECT type_id FROM "+TABLE_PLAYCOUNTS+" WHERE type="+type+" ORDER BY RANDOM() LIMIT 10", null);
		while (cursor.moveToNext()) {
			toCheck.add(cursor.getLong(0));
		}
		cursor.close();

		for (Long id : toCheck) {
			query = MediaUtils.buildQuery(type, id, null, null);
			cursor = query.runQuery(ctx.getContentResolver());
			if(cursor.getCount() == 0) {
				dbh.execSQL("DELETE FROM "+TABLE_PLAYCOUNTS+" WHERE type="+type+" AND type_id="+id);
				removed++;
			}
			cursor.close();
		}
		Log.v("VanillaMusic", "performGC: items removed="+removed);
		dbh.close();
		return removed;
	}

}
