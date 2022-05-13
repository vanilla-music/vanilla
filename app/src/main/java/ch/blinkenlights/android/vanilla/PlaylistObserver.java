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
import android.os.Build;
import android.os.FileObserver;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.PrintWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.zip.CRC32;

import android.util.Log;


public class PlaylistObserver extends SQLiteOpenHelper implements Handler.Callback {
	/**
	 * Whether or not to write debug logs.
	 */
	private static final boolean DEBUG = false;
	/**
	 * Bits for mSyncMode
	 */
	public static final int SYNC_MODE_IMPORT = (1 << 0);
	public static final int SYNC_MODE_EXPORT = (1 << 1);
	public static final int SYNC_MODE_PURGE  = (1 << 2);
	/**
	 * Timeout to coalesce duplicate messages, ~2.3 sec because no real reason.
	 */
	private static final int COALESCE_EVENTS_DELAY_MS = 2345;
	/**
	 * Extension to use for M3U files
	 */
	private static final String M3U_EXT = ".m3u";
	/**
	 * Line comment prefix for M3U files
	 */
	private static final String M3U_LINE_COMMENT_PREFIX = "#";
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
	 * Observer which watches the playlist directory.
	 */
	private FileObserver mFileObserver;
	/**
	 * Directory which holds observed playlists.
	 */
	private File mPlaylists;
	/**
	 * What kind of synching to perform, bitmask of PlaylistObserver.SYNC_MODE_*
	 */
	private int mSyncMode;
	/**
	 * Whether to export playlists using relative file paths.
	 */
	private boolean mExportRelativePaths;
	/**
	 * Database fields
	 */
	private static class Database {
		static final String TABLE_NAME = "playlist_metadata";
		static final String _ID = "_id";
		static final String NAME = "name";
		static final String HASH = "hash";
		static final String[] FILLED_PROJECTION = {
			_ID,
			NAME,
			HASH,
		};
	}


	public PlaylistObserver(Context context, String folder, int mode, boolean exportRelativePaths) {
		super(context, "playlist_observer.db", null, 1 /* version */);
		mContext = context;
		mSyncMode = mode;
		mPlaylists = new File(folder);
		mExportRelativePaths = exportRelativePaths;

		// Launch new thread for background execution
		mHandlerThread= new HandlerThread("PlaylisObserverHandler", Process.THREAD_PRIORITY_LOWEST);
		mHandlerThread.start();
		mHandler = new Handler(mHandlerThread.getLooper(), this);

		// Create playlists directory if not existing.
		if ((mSyncMode & SYNC_MODE_EXPORT) != 0) {
			try {
				mPlaylists.mkdir();
			} catch (Exception e) {
				// don't care: code will ignore events
				// if mPlaylists is not a (writable) dir.
			}
		}

		// Register to receive media library events.
		MediaLibrary.registerLibraryObserver(mLibraryObserver);
		// Create and start directory observer.
		mFileObserver = getFileObserver(mPlaylists);
		mFileObserver.startWatching();

		XT("Object created, trigger FULL_SYNC_SCAN");
		sendUniqueMessage(MSG_FULL_SYNC_SCAN, 0);
	}

	/**
	 * Unregisters this observer, the object must not be used anymore
	 * after this function was called.
	 */
	public void unregister() {
		MediaLibrary.unregisterLibraryObserver(mLibraryObserver);
		mFileObserver.stopWatching();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
			mHandlerThread.quitSafely();
		} else {
			mHandlerThread.quit();
		}
		mHandlerThread = null;
		mHandler = null;
	}

	/**
	 * Whether or not to handle (or drop) any events received.
	 * This has the same effect as unregistering, without actually
	 * unregistering. The function prevents false events from
	 * being handled if our playlists folder becomes 'suspect'.
	 */
	private boolean shouldDispatch() {
		// This is so sad, but java.nio.file is only available since API 26,
		// so we are stuck with java being java.
		boolean deny = false;
		try {
			File f = File.createTempFile("vanilla-write-check", null, mPlaylists);
			f.delete();
		} catch (Exception e) {
			// don't care about the exact error: as long as the write failed,
			// we should not assume that we could write anything else.
			deny = true;
		}
		return !deny;
	}

	/**
	 * SQLiteHelper onCreate
	 */
	@Override
	public void onCreate(SQLiteDatabase dbh) {
		dbh.execSQL("CREATE TABLE "+Database.TABLE_NAME+" ( "
					+ Database._ID  + " INTEGER PRIMARY KEY, "
					+ Database.HASH + " INTEGER NOT NULL, "
					+ Database.NAME + " TEXT NOT NULL )"
					);
	}

	/**
	 * SQLiteHelper onUpgrade
	 */
	@Override
	public void onUpgrade(SQLiteDatabase dbh, int oldVersion, int newVersion) {
		// No updates so far.
	}

	/**
	 * Message handler, used to dedupe messages and perform
	 * background work.
	 */
	private static final int MSG_DUMP_M3U = 1;
	private static final int MSG_DUMP_ALL_M3U = 2;
	private static final int MSG_IMPORT_M3U = 3;
	private static final int MSG_FORCE_M3U_IMPORT = 4;
	private static final int MSG_FULL_SYNC_SCAN = 5;
	ArrayList<Integer> msgDedupe = new ArrayList<>();
	@Override
	public boolean handleMessage(Message message) {
		msgDedupe.remove(0);

		switch (message.what) {
		case MSG_DUMP_M3U:
			Long id = (Long)message.obj;
			if (Playlist.getPlaylist(mContext, id) != null) {
				XT("DUMP_M3U: source of id "+id+" exists, dumping");
				dumpAsM3uPlaylist(id);
			} else {
				XT("DUMP_M3U: source of id "+id+" vanished, scanning all");
				sendUniqueMessage(MSG_FULL_SYNC_SCAN, 0);
			}
			break;
		case MSG_DUMP_ALL_M3U:
			dumpAllAsM3uPlaylist();
			break;
		case MSG_IMPORT_M3U:
			File f = (File)(message.obj);
			importM3uPlaylist(f);
			break;
		case MSG_FORCE_M3U_IMPORT:
			forceM3uImport();
			break;
		case MSG_FULL_SYNC_SCAN:
			fullSyncScan();
			break;
		default:
			throw new IllegalArgumentException("Invalid message type received");
		}
		return true;
	}

	/**
	 * Forcefully re-imports all M3U files, even if we think that
	 * our information is up-to-date.
	 */
	private void forceM3uImport() {
		Cursor cursor = queryDatabase(null);
		if (cursor != null) {
			while (cursor.moveToNext()) {
				deletePlaylistMetadata(cursor.getLong(0));
			}
			cursor.close();
		}
		// run this ASAP to ensure that no other message re-populates
		// metadata.
		XT("forceM3uImport: metadata cleared, calling fullSyncScan");
		fullSyncScan();
	}

	/**
	 * Dumps all playlist to stable storage.
	 */
	private void dumpAllAsM3uPlaylist() {
		XT("dumpAllAsM3uPlaylist: called");
		Cursor cursor = Playlist.queryPlaylists(mContext);
		if (cursor != null) {
			while(cursor.moveToNext()) {
				final long id = cursor.getLong(0);
				XT("dumpAllAsM3uPlaylist: Dumping ID "+id);
				sendUniqueMessage(MSG_DUMP_M3U, id);
			}
			cursor.close();
		}
	}

	/**
	 * Adds a new message to the queue. Ignores call if a duplicate
	 * message is already pending.
	 *
	 * @param type the type of the message
	 * @param obj object payload of this message.
	 */
	private void sendUniqueMessage(int type, Object obj) {
		int fprint = (type << 10) + obj.hashCode();
		if (!msgDedupe.contains(fprint)) {
			msgDedupe.add(fprint);
			mHandler.sendMessageDelayed(mHandler.obtainMessage(type, obj), COALESCE_EVENTS_DELAY_MS);
		}
	}


	/**
	 * Imports an M3U formatted file into our native media library.
	 *
	 * @param m3u the file to import
	 */
	private void importM3uPlaylist(File m3u) {;
		XT("importM3uPlaylist("+m3u+")");

		if ((mSyncMode & SYNC_MODE_IMPORT) == 0)
			return;

		if (!m3u.exists())
			return;

		final long hash = getHash(m3u);
		if (hash == -1)
			return;

		boolean must_import = true;
		String import_as = fromM3u(m3u.getName());
		Cursor cursor = queryDatabase(null);
		if (cursor != null) {
			// Try to find an existing playlist where the constructed path
			// would match given input file.
			while(cursor.moveToNext()) {
				File tmp = getFileForName(mPlaylists, asM3u(cursor.getString(1)));
				if (m3u.equals(tmp)) {
					// Found a matching playlist: this will be our import target
					// if the hash indicates that our version is outdated.
					import_as = cursor.getString(1);
					must_import = (hash != cursor.getLong(2));
					XT("importM3uPlaylist(): hash="+hash+", import="+must_import+", import_as="+import_as);
					break;
				}
			}
			cursor.close();
		}

		if (must_import) {
			MediaLibrary.unregisterLibraryObserver(mLibraryObserver);
			long import_id = Playlist.createPlaylist(mContext, import_as);
			try (BufferedReader br = new BufferedReader(new FileReader(m3u))) {
				String line;
				while ((line = br.readLine()) != null) {
					if (!(line.isEmpty() || line.startsWith(M3U_LINE_COMMENT_PREFIX))) {
						// Handle relative paths and Windows directory separators.
						final String mediaPath = FileUtils.resolve(mPlaylists,
							new File(FileUtils.normalizeDirectorySeparators(line)));
						Playlist.addToPlaylist(mContext,
							import_id,
							MediaUtils.buildFileQuery(mediaPath, Song.FILLED_PROJECTION,  false /* recursive */));
					}
				}
				updatePlaylistMetadata(import_id, import_as, hash);
			} catch(IOException e) {
				Log.e("VanillaMusic", "Error while parsing m3u: "+e);
			}
			MediaLibrary.registerLibraryObserver(mLibraryObserver);
		}
	}

	/**
	 * Exports a single playlist ad M3U(8).
	 *
	 * @param id the playlist id to export.
	 * @return the newly written playlist, null if nothing was done.
	 */
	private File dumpAsM3uPlaylist(long id) {
		XT("dumpM3uPlaylist("+id+")");
		if (id < 0)
			throw new IllegalArgumentException("Called with negative id!");

		if ((mSyncMode & SYNC_MODE_EXPORT) == 0)
			return null;

		final String name = Playlist.getPlaylist(mContext, id);
		if (name == null)
			return null;

		final File m3u = getFileForName(mPlaylists, asM3u(name));

		PrintWriter pw = null;
		QueryTask query = MediaUtils.buildPlaylistQuery(id, Song.FILLED_PLAYLIST_PROJECTION);
		Cursor cursor = query.runQuery(mContext);
		try {
			if (cursor != null) {
				pw = new PrintWriter(m3u);
				while (cursor.moveToNext()) {
					// Write paths relative to the playlist export directory, if enabled.
					String path = cursor.getString(1);
					if (mExportRelativePaths) {
						path = FileUtils.relativize(mPlaylists, new File(path));
					}
					pw.println(path);
				}
				pw.flush();
				long hash_new = getHash(m3u);
				updatePlaylistMetadata(id, name, hash_new);
			}
		} catch (IOException e) {
			Log.v("VanillaMusic", "IOException while writing:", e);
		} finally {
			if (cursor != null) cursor.close();
			if (pw != null) pw.close();
		}
		return m3u;
	}

	/**
	 * Identify (and remove) playlist items which do not exist anymore
	 * and pick up any new M3U files.
	 */
	private void fullSyncScan() {
		XT("fullSyncScan() running...");
		ArrayList<File> knownM3u = new ArrayList<>();

		// First step is to check all known playlist metadata entries
		// and check whether their native or M3U copy was purged.
		final boolean do_purge = (mSyncMode & SYNC_MODE_PURGE) != 0;
		Cursor cursor = queryDatabase(null);
		if (cursor != null) {
			while (cursor.moveToNext()) {
				final long id = cursor.getLong(0);
				final String name = cursor.getString(1);
				// generates possible names of this playlist as M3U.
				final File src_m3u = getFileForName(mPlaylists, asM3u(name));
				final File bak_m3u = getFileForName(mPlaylists, name + ".backup");

				if (Playlist.getPlaylist(mContext, id) == null) {
					// Native version of this playlist is gone, rename M3U variant:
					if (do_purge) {
						src_m3u.renameTo(bak_m3u);
					}
					deletePlaylistMetadata(id);
					XT("fullSyncScan(): renamed old M3U -> "+bak_m3u);
				} else if (do_purge && !src_m3u.exists()) {
					// Source vanished, write one last dump and remove it.
					File dump = dumpAsM3uPlaylist(id);
					if (dump != null) {
						dump.renameTo(bak_m3u);
					}
					Playlist.deletePlaylist(mContext, id);
					deletePlaylistMetadata(id);
					XT("fullSyncScan(): killed native playlist with id "+id);
				}
				// If this M3U exists, record it so that we don't try to re-import.
				if (src_m3u.exists()) {
					knownM3u.add(src_m3u);
				}
			}
			cursor.close();
		}

		// Now list all M3U files in the playlists dir and import newly seen files.
		File[] files = mPlaylists.listFiles();
		if (files != null) {
			for (File f : files) {
				final String fname = f.getName();
				if (isM3uFilename(fname) && !knownM3u.contains(f)) {
					if (Playlist.getPlaylist(mContext, fromM3u(fname)) == -1) {
						XT("fullSyncScan(): new M3U discovered, must import "+f);
						sendUniqueMessage(MSG_IMPORT_M3U, f);
					} else {
						XT("fullSyncScan(): native version for "+f+" exists without metadata. Won't touch.");
					}
				}
			}
		}
	}

	/**
	 * Returns a file object for given name
	 *
	 * @param name name of playlist to use.
	 * @return file object for given name
	 */
	private File getFileForName(File parent, String name) {
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
	private void updatePlaylistMetadata(long id, String name, long hash) {
		if (hash < 0)
			throw new IllegalArgumentException("hash can not be negative");

		XT("updatePlaylistMetadata of "+name+" to hash "+hash);
		SQLiteDatabase dbh = getWritableDatabase();
		ContentValues values = new ContentValues();
		values.put(Database._ID, id);
		values.put(Database.NAME, name);
		values.put(Database.HASH, hash);

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
		dbh.delete(Database.TABLE_NAME, Database._ID+"=?", new String[] { Long.valueOf(id).toString() });
	}

	/**
	 * Returns true if the filename looks like an m3u file
	 *
	 * @param name the name to check
	 * @return true if file appears to be an M3U
	 */
	private boolean isM3uFilename(String name) {
		if (name.length() < M3U_EXT.length())
			return false;
		final int offset = name.length() - M3U_EXT.length();
		return name.toLowerCase().substring(offset).equals(M3U_EXT.toLowerCase());
	}

	/**
	 * Returns the m3u-filename of name
	 *
	 * @param name the name to use
	 * @return the m3u name
	 */
	private String asM3u(String name) {
		return name + M3U_EXT;
	}

	/**
	 * Returns the name of an m3u file
	 *
	 * @param name the m3u filename
	 * @return the non-m3u name
	 */
	private String fromM3u(String name) {
		if (!isM3uFilename(name))
			throw new IllegalArgumentException("Not an M3U filename: "+name);
		return name.substring(0, name.length() - M3U_EXT.length());
	}

	/**
	 * Hashes the contents of given file
	 *
	 * @param f the file to hash
	 * @return the calculated hash, -1 on error.
	 */
	private long getHash(File f) {
		long hash = -1;
		byte[] buff = new byte[4096];
		try(FileInputStream fis = new FileInputStream(f)) {
			CRC32 crc = new CRC32();
			while(fis.read(buff) != -1) {
				crc.update(buff);
			}
			hash = crc.getValue();
			if (hash < 0)
				hash = hash * -1;
		} catch(IOException e) {
			// hash will be -1 which signals failure.
		}
		return hash;
	}

	/**
	 * Obtain a cursor to our metadata database.
	 *
	 * @param selection selection for query
	 * @return cursor with results.
	 */
	private Cursor queryDatabase(String selection) {
		return getReadableDatabase().query(Database.TABLE_NAME, Database.FILLED_PROJECTION, selection, null, null, null, null);
	}

	/**
	 * Library observer callback which notifies us about media library
	 * events.
	 *
	 * @param type the event type
	 * @param id the id of given type which had a change
	 * @param ongoing whether or not to expect more of these events
	 */
	private final LibraryObserver mLibraryObserver = new LibraryObserver() {
		@Override
		public void onChange(LibraryObserver.Type type, long id, boolean ongoing) {
			if (type != LibraryObserver.Type.PLAYLIST || ongoing)
				return;

			if (!shouldDispatch())
				return;

			int msg = MSG_DUMP_M3U; // Default: export this playlist ID.
			if (id == LibraryObserver.Value.UNKNOWN) {
				// An unknown (all?) playlist was modified: dump all to M3U
				msg = MSG_DUMP_ALL_M3U;
			}
			if (id == LibraryObserver.Value.OUTDATED) {
				// Our data is wrong, reimport all M3Us
				msg = MSG_FORCE_M3U_IMPORT;
			}

			XT("LibraryObserver::onChange id="+id+", msg="+msg);
			sendUniqueMessage(msg, id);
		}
	};

	/**
	 * Returns a new observer which monitors the playlists directory.
	 *
	 * @param target The target playlists directory.
	 * @return A new observer for the playlists directory.
	 */
	private FileObserver getFileObserver(File target) {
		final int mask = FileObserver.CLOSE_WRITE | FileObserver.MOVED_FROM | FileObserver.MOVED_TO | FileObserver.DELETE;
		XT("new file observer at "+target+" with mask "+mask);

		return new FileObserver(target.getAbsolutePath(), mask) {
			/**
			 * Observer which monitors the playlists directory.
			 *
			 * @param event the event type
			 * @param dirent the filename which triggered the event.
			 */
			@Override
			public void onEvent(int event, String dirent) {
				// Events such as IN_IGNORED pass dirent = null.
				if (dirent == null)
					return;

				if (!isM3uFilename(dirent))
					return;

				if (!shouldDispatch())
					return;

				if ((event & (FileObserver.MOVED_FROM | FileObserver.DELETE)) != 0) {
					// A M3U vanished, do a full scan.
					XT("FileObserver::onEvent DELETE of "+dirent+" triggers FULL_SYNC_SCAN");
					sendUniqueMessage(MSG_FULL_SYNC_SCAN, 0);
				}
				if ((event & (FileObserver.MOVED_TO | FileObserver.CLOSE_WRITE)) != 0) {
					// Single file was created, import it.
					XT("FileObserver::onEvent WRITE of "+dirent+" triggers IMPORT_M3U");
					sendUniqueMessage(MSG_IMPORT_M3U, new File(mPlaylists, dirent));
				}
			}
		};
	}

	private void XT(String s) {
		if (DEBUG) {
			try(PrintWriter pw = new PrintWriter(new FileOutputStream(new File("/sdcard/playlist-observer.txt"), true))) {
				pw.println(System.currentTimeMillis()/1000+": "+s);
				Log.v("VanillaMusic", "XTRACE: "+s);
			} catch(Exception e) {}
		}
	}
}
