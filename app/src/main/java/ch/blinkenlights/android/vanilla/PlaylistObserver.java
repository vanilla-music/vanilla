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

package ch.blinkenlights.android.vanilla;

import ch.blinkenlights.android.medialibrary.MediaLibrary;
import ch.blinkenlights.android.medialibrary.LibraryObserver;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;

import java.io.File;
import java.io.PrintWriter;
import java.io.IOException;

import android.util.Log;


public class PlaylistObserver extends SQLiteOpenHelper implements Handler.Callback {
	/**
	 * Timeout to coalesce duplicate messages.
	 */
	private final static int COALESCE_EVENTS_DELAY_MS = 280;
	/**
	 * Extension to use for M3U files
	 */
	private final static String M3U_EXT = ".m3u";
	/**
	 * Context to use.
	 */
	private Context mContext;
	/**
	 * Thread used for execution.
	 */
	private HandlerThread mHandlerThread;
	/**
	 * Handler thread used to perform playlist sync.
	 */
	private Handler mHandler;
	/**
	 * Directory which holds observed playlists.
	 */
	private File mPlaylists = new File(Environment.getExternalStorageDirectory(), "Playlists");

	static class Database {
		final static String TABLE_NAME = "playlist_metadata";
		final static String _ID = "_id";
		final static String NAME = "name";
		final static String MTIME = "mtime";
		final static String[] FILLED_PROJECTION = {
			_ID,
			NAME,
			MTIME,
		};
	}

	public PlaylistObserver(Context context) {
		super(context, "playlistobserver.db", null, 1 /* version */);

		mContext = context;
		// Launch new thread for background execution
		mHandlerThread= new HandlerThread("PlaylistWriter", Process.THREAD_PRIORITY_LOWEST);
		mHandlerThread.start();
		mHandler = new Handler(mHandlerThread.getLooper(), this);

		// Register to receive media library events.
		MediaLibrary.registerLibraryObserver(mObserver);
	}

	/**
	 * Unregisters this observer, the object must not be used anymore
	 * after this function was called.
	 */
	public void unregister() {
		MediaLibrary.unregisterLibraryObserver(mObserver);

		mHandlerThread.quitSafely();
		mHandlerThread = null;
		mHandler = null;
	}

	private final static int MSG_DUMP_M3U = 1;
	private final static int MSG_DUMP_ALL_M3U = 2;
	@Override
	public boolean handleMessage(Message message) {
		switch (message.what) {
		case MSG_DUMP_M3U:
			Long id = (Long)message.obj;
			if (!dumpM3uPlaylist(id)) {
				// Dump of 'id' failed, so this playlist was likely deleted.
				cleanupOrphanedM3u();
			}
			break;
		case MSG_DUMP_ALL_M3U:
			dumpM3uPlaylists();
			cleanupOrphanedM3u();
			break;
		default:
			throw new IllegalArgumentException("Invalid message type received");
		}
		return true;
	}

	/**
	 * Exports a single playlist ad M3U(8).
	 *
	 * @param id the playlist id to export.
	 * @return true if the playlist was dumped.
	 */
	private boolean dumpM3uPlaylist(long id) {
		final String name = Playlist.getPlaylist(mContext, id);

		if (id < 0)
			throw new IllegalArgumentException("Called with negative id!");

		if (name == null)
			return false;

		if (!mPlaylists.isDirectory())
			mPlaylists.mkdir();

		Log.v("VanillaMusic", "Dumping "+getFileForName(mPlaylists, name));

		PrintWriter pw = null;
		QueryTask query = MediaUtils.buildPlaylistQuery(id, Song.FILLED_PLAYLIST_PROJECTION);
		Cursor cursor = query.runQuery(mContext);
		try {
			if (cursor != null) {
				pw = new PrintWriter(getFileForName(mPlaylists, name + M3U_EXT));
				pw.println("#EXTM3U");
				while (cursor.moveToNext()) {
					final String path = cursor.getString(1);
					final String title = cursor.getString(2);
					final String artist = cursor.getString(3);
					final long duration = cursor.getLong(7);

					pw.printf("#EXTINF:%d,%s - %s%n", (duration/1000), artist, title);
					pw.println(path);
				}
				updatePlaylistMetadata(id, name);
			}
		} catch (IOException e) {
			Log.v("VanillaMusic", "IOException while writing:", e);
		} finally {
			if (cursor != null) cursor.close();
			if (pw != null) pw.close();
		}
		return true;
	}

	/**
	 * Dumps all playlist to stable storage.
	 */
	private void dumpM3uPlaylists() {
		Cursor cursor = Playlist.queryPlaylists(mContext);
		if (cursor != null) {
			while(cursor.moveToNext()) {
				final long id = cursor.getLong(0);
				sendUniqueMessage(MSG_DUMP_M3U, id);
			}
			cursor.close();
		}
	}

	/**
	 * Checks our playlists directory for files which reference
	 * non-existing playlists and removes them.
	 */
	private void cleanupOrphanedM3u() {
		SQLiteDatabase dbh = getReadableDatabase();
		Cursor cursor = dbh.query(Database.TABLE_NAME, Database.FILLED_PROJECTION, null, null, null, null, null);
		if (cursor != null) {
			while (cursor.moveToNext()) {
				final long id = cursor.getLong(0);
				final String name = cursor.getString(1);
				final File src_m3u = getFileForName(mPlaylists, name + M3U_EXT);

				if (Playlist.getPlaylist(mContext, id) == null) {
					// Native version of this playlist is gone, rename M3U variant:
					File dst_m3u = getFileForName(mPlaylists, name + ".bak");
					src_m3u.renameTo(dst_m3u);
					deletePlaylistMetadata(id);
					Log.v("VanillaMusic", name+": Renamed old m3u");
				} else if (!src_m3u.exists()) {
					Playlist.deletePlaylist(mContext, id); // Fixme: do we really want this?
					deletePlaylistMetadata(id);
					Log.v("VanillaMusic", name+": Killed native playlist");
				}
			}
			cursor.close();
		}
	}

	/**
	 * Returns a file object for given name
	 *
	 * @param name name of playlist to use.
	 * @return file object for given name
	 */
	private File getFileForName(File parent, String name) {
		//Fixme: check for m3u8 and remove invalid chars.
		name = name.replaceAll("/", "_");
		File f = new File(parent, name);
		return f;
	}

	/**
	 * Updates the metadata of given playlist name
	 *
	 * @param id the id to update.
	 * @param name the name to register for this id.
	 */
	private void updatePlaylistMetadata(long id, String name) {
		SQLiteDatabase dbh = getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put(Database._ID, id);
		values.put(Database.NAME, name);
		values.put(Database.MTIME, System.currentTimeMillis());

		deletePlaylistMetadata(id);
		dbh.insert(Database.TABLE_NAME, null, values);
	}

	/**
	 * Removes all known metadata of given id.
	 *
	 * @param id the id to purge.
	 */
	private void deletePlaylistMetadata(long id) {
		SQLiteDatabase dbh = getWritableDatabase();
		dbh.delete(Database.TABLE_NAME, Database._ID+"=?", new String[] { new Long(id).toString() });
	}

	/**
	 * Returns true if the filename looks like an m3u file
	 *
	 * @param name the name to check
	 * @return true if file appears to be an M3U
	 */
	private boolean FIXME_isM3uFilename(String name) {
		if (name.length() < M3U_EXT.length())
			return false;
		final int offset = name.length() - M3U_EXT.length();
		return name.toLowerCase().substring(offset).equals(M3U_EXT.toLowerCase());
	}

	/**
	 * Adds a new message to the queue. Pending duplicate messages
	 * will be pruged.
	 *
	 * @param type the type of the message
	 * @param obj object payload of this message.
	 */
	private void sendUniqueMessage(int type, Long obj) {
		mHandler.removeMessages(type, obj);
		mHandler.sendMessageDelayed(mHandler.obtainMessage(type, obj), COALESCE_EVENTS_DELAY_MS);
	}

	/**
	 * Library observer callback which notifies us about media library
	 * events.
	 */
	private final LibraryObserver mObserver = new LibraryObserver() {
		@Override
		public void onChange(LibraryObserver.Type type, long id, boolean ongoing) {
			if (type != LibraryObserver.Type.PLAYLIST || ongoing)
				return;

			// Dispatch this event but use different type if id was -1 as
			// this indicates that multiple (unknown) playlists may have changed.
			final int msg = (id < 0 ? MSG_DUMP_ALL_M3U : MSG_DUMP_M3U);
			sendUniqueMessage(msg, id);
		}
	};

	@Override
	public void onCreate(SQLiteDatabase dbh) {
		dbh.execSQL("CREATE TABLE "+Database.TABLE_NAME+" ( "
					+ Database._ID   + " INTEGER PRIMARY KEY, "
					+ Database.MTIME + " INTEGER NOT NULL, "
					+ Database.NAME  + " TEXT NOT NULL )"
					);
	}

	@Override
	public void onUpgrade(SQLiteDatabase dbh, int oldVersion, int newVersion) {
		// No updates so far.
	}
	// TODO:
	// Use FileObserver to track playlist changes?
	// how do we check modifications? write a shadow-dir in private app storage with same mtimes?
}
