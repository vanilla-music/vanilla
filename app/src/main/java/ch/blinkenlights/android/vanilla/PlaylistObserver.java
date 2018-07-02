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

import android.database.Cursor;
import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;

import java.io.File;
import java.io.PrintWriter;
import java.io.IOException;

import android.util.Log;


public class PlaylistObserver implements Handler.Callback {
	/**
	 * Timeout to coalesce duplicate messages.
	 */
	private final static int COALESCE_EVENTS_DELAY_MS = 280;
	/**
	 * Context to use.
	 */
	private Context mContext;
	/**
	 * Handler thread used to perform playlist sync.
	 */
	private Handler mHandler;
	/**
	 * Directory which holds observed playlists.
	 */
	private File mPlaylists = new File(Environment.getExternalStorageDirectory(), "Playlists");


	public PlaylistObserver(Context context) {
		mContext = context;
		// Create thread which will be used for background work.
		HandlerThread handlerThread = new HandlerThread("PlaylistWriter", Process.THREAD_PRIORITY_LOWEST);
		handlerThread.start();
		mHandler = new Handler(handlerThread.getLooper(), this);
		// Register to receive media library events.
		MediaLibrary.registerLibraryObserver(mObserver);
	}

	/**
	 * Unregisters this observer, the object must not be used anymore
	 * after this function was called.
	 */
	public void unregister() {
		MediaLibrary.unregisterLibraryObserver(mObserver);
	}

	private final static int MSG_SYNC_M3U = 1;
	@Override
	public boolean handleMessage(Message message) {
		switch (message.what) {
		case MSG_SYNC_M3U:
			Long id = (Long)message.obj;
			if (id == -1) {
				syncAllM3u();
			} else {
				syncM3uPlaylist(id);
			}
			break;
		default:
			throw new IllegalArgumentException("Invalid message type received");
		}
		return true;
	}

	/**
	 * Exports a single playlist ad M3U(8).
	 *
	 * @param id the playlist id to export
	 */
	private void syncM3uPlaylist(long id) {
		String name = Playlist.getPlaylist(mContext, id);

		if (name == null) {
			cleanupOrphanedM3u();
			return;
		}

		if (!mPlaylists.isDirectory())
			mPlaylists.mkdir();
		Log.v("VanillaMusic", "Dumping "+getFileFromName(name));

		PrintWriter pw = null;
		QueryTask query = MediaUtils.buildPlaylistQuery(id, Song.FILLED_PLAYLIST_PROJECTION);
		Cursor cursor = query.runQuery(mContext);
		try {
			if (cursor != null) {
				pw = new PrintWriter(getFileFromName(name));
				pw.println("#EXTM3U");
				while (cursor.moveToNext()) {
					final String path = cursor.getString(1);
					final String title = cursor.getString(2);
					final String artist = cursor.getString(3);
					final long duration = cursor.getLong(7);

					pw.printf("#EXTINF:%d,%s - %s%n", (duration/1000), artist, title);
					pw.println(path);
				}
				cursor.close();
			}
		} catch (IOException e) {
			Log.v("VanillaMusic", "IOException while writing:", e);
		} finally {
			if (pw != null) pw.close();
		}
	}

	/**
	 * Dumps all playlist to stable storage.
	 */
	private void syncAllM3u() {
		Log.v("VanillaMusic", "M3U: Dumping all playlists");
		cleanupOrphanedM3u();

		Cursor cursor = Playlist.queryPlaylists(mContext);
		if (cursor != null) {
			while(cursor.moveToNext()) {
				final long id = cursor.getLong(0);
				syncM3uPlaylist(id);
			}
			cursor.close();
		}
	}

	/**
	 * Checks our playlists directory for files which reference
	 * non-existing playlists and removes them.
	 */
	private void cleanupOrphanedM3u() {
		Log.v("VanillaMusic", "Implement me!");
	}

	/**
	 * Returns a file object for given playlist name
	 *
	 * @param name name of playlist to use.
	 * @return file object for given name
	 */
	private File getFileFromName(String name) {
		//Fixme: check for m3u8 and remove invalid chars.
		File f = new File(mPlaylists, name +".m3u8");
		return f;
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

			mHandler.removeMessages(MSG_SYNC_M3U, id);
			mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_SYNC_M3U, id), COALESCE_EVENTS_DELAY_MS);
		}
	};

	// TODO:
	// Use FileObserver to track playlist changes?
	// how do we check modifications? write a shadow-dir in private app storage with same mtimes?
}
