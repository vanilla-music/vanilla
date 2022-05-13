/*
 * Copyright (C) 2016-2018 Adrian Ulrich <adrian@blinkenlights.ch>
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

import ch.blinkenlights.android.vanilla.R;
import ch.blinkenlights.android.vanilla.NotificationHelper;

import android.app.Notification;
import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.ContentObserver;
import android.util.Log;
import android.provider.MediaStore;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;

import java.io.File;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class MediaScanner implements Handler.Callback {
	/**
	 * Our scan plan
	 */
	private MediaScanPlan mScanPlan;
	/**
	 * Our message handler
	 */
	private Handler mHandler;
	/**
	 * The context to use for native library queries
	 */
	private Context mContext;
	/**
	 * The wake lock we hold during the scan
	 */
	private PowerManager.WakeLock mWakeLock;
	/**
	 * Instance of a media backend
	 */
	private MediaLibraryBackend mBackend;
	/**
	 * True if this is a from-scratch import
	 * Set by KICKSTART rpc
	 */
	private boolean mIsInitialScan;
	/**
	 * True if we are currently in a scan phase
	 */
	private boolean mScanIsRunning;
	/**
	 * True if we must do a full cleanup of orphaned entries after the scan finished.
	 */
	private boolean mPendingCleanup;
	/**
	 * Our NotificationHelper instance.
	 */
	private NotificationHelper mNotificationHelper;
	/**
	 * uptimeMillis ts at which we will dispatch the next scan update report
	 */
	private long mNextProgressReportAt;
	/**
	 * The id we are using for the scan notification
	 */
	private static final int NOTIFICATION_ID = 56162;
	/**
	 * The notification channel to use
	 */
	private static final String NOTIFICATION_CHANNEL = "Scanner";
	/**
	 * Relax mtime check while searching for native modifications by this many seconds
	 */
	private static final int NATIVE_VRFY_MTIME_SLACK = 100;
	/**
	 * Delay native scans by this many ms to coalesce multiple modifications
	 */
	private static final int NATIVE_VRFY_COALESCE_DELAY = 3500;

	MediaScanner(Context context, MediaLibraryBackend backend) {
		mContext = context;
		mBackend = backend;
		mScanPlan = new MediaScanPlan();
		HandlerThread handlerThread = new HandlerThread("MediaScannerThread", Process.THREAD_PRIORITY_LOWEST);
		handlerThread.start();
		mHandler = new Handler(handlerThread.getLooper(), this);
		mWakeLock = ((PowerManager)context.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VanillaMusicIndexerLock");
		mNotificationHelper = new NotificationHelper(context, NOTIFICATION_CHANNEL, context.getString(R.string.media_stats_progress));

		// the content observer to use
		ContentObserver mObserver = new ContentObserver(null) {
			@Override
			public void onChange(boolean self) {
				startQuickScan(NATIVE_VRFY_COALESCE_DELAY);
			}
		};
		context.getContentResolver().registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mObserver);
	}

	/**
	 * Performs a 'fast' scan by checking the native and our own
	 * library for new and changed files
	 */
	public void startNormalScan() {
		setNativeLastMtime(MTIME_DIRTY);
		mScanPlan.addNextStep(RPC_NATIVE_VRFY, null)
			.addNextStep(RPC_LIBRARY_VRFY, null);
		mHandler.sendMessage(mHandler.obtainMessage(MSG_SCAN_RPC, RPC_KICKSTART, 0));
	}

	/**
	 * Performs a 'slow' scan by inspecting all files on the device
	 */
	public void startFullScan() {
		MediaLibrary.Preferences prefs = MediaLibrary.getPreferences(mContext);
		for (String path : prefs.mediaFolders) {
			mScanPlan.addNextStep(RPC_READ_DIR, new File(path));
		}
		mScanPlan.addNextStep(RPC_LIBRARY_VRFY, null);
		mScanPlan.addNextStep(RPC_NATIVE_VRFY, null);
		mHandler.sendMessage(mHandler.obtainMessage(MSG_SCAN_RPC, RPC_KICKSTART, 0));
	}

	/**
	 * Called by the content observer if a change in the media library
	 * has been detected
	 *
	 * @param delay how many ms we should wait (used to coalesce multiple calls)
	 */
	public void startQuickScan(int delay) {
		if (!mHandler.hasMessages(MSG_GUESS_QUICKSCAN) && !mHandler.hasMessages(MSG_SCAN_RPC)) {
			mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_GUESS_QUICKSCAN, 0, 0), delay);
		}
	}

	/**
	 * Stops a running scan
	 */
	public void abortScan() {
		mHandler.removeMessages(MSG_SCAN_RPC);
		mScanPlan.clear();
		mHandler.sendMessage(mHandler.obtainMessage(MSG_SCAN_RPC, RPC_KICKSTART, 0));
	}

	/**
	 * Prepares a flush of the databse.
	 */
	public void flushDatabase() {
		mBackend.setPendingDeletion();
		mPendingCleanup = true;
		setNativeLastMtime(MTIME_PRISTINE);
	}

	/**
	 * Returns some scan statistics
	 *
	 * @return a MediaLibrary.ScanProgress object
	 */
	public MediaLibrary.ScanProgress describeScanProgress() {
		MediaLibrary.ScanProgress progress = new MediaLibrary.ScanProgress();
		MediaLibrary.Preferences prefs = MediaLibrary.getPreferences(mContext);
		MediaScanPlan.Statistics stats = mScanPlan.getStatistics();

		progress.isRunning = mScanIsRunning;
		progress.lastFile = stats.lastFile;
		progress.seen = stats.seen;
		progress.changed = stats.changed;
		progress.total = prefs._nativeLibraryCount;

		return progress;
	}

	private static final int MSG_SCAN_RPC         = 0;
	private static final int MSG_SCAN_FINISHED    = 1;
	private static final int MSG_NOTIFY_CHANGE    = 2;
	private static final int MSG_GUESS_QUICKSCAN  = 3;
	private static final int RPC_KICKSTART        = 100;
	private static final int RPC_READ_DIR         = 101;
	private static final int RPC_INSPECT_FILE     = 102;
	private static final int RPC_LIBRARY_VRFY     = 103;
	private static final int RPC_NATIVE_VRFY      = 104;

	@Override
	public boolean handleMessage(Message message) {
		int rpc = (message.what == MSG_SCAN_RPC ? message.arg1 : message.what);

		switch (rpc) {
			case MSG_NOTIFY_CHANGE: {
				MediaLibrary.notifyObserver(LibraryObserver.Type.SONG, LibraryObserver.Value.UNKNOWN, true);
				break;
			}
			case MSG_SCAN_FINISHED: {
				mScanIsRunning = false;
				if (mIsInitialScan) {
					mIsInitialScan = false;
					MediaLibrary.notifyObserver(LibraryObserver.Type.PLAYLIST, LibraryObserver.Value.OUTDATED, false);
				}
				if (mPendingCleanup) {
					mPendingCleanup = false;
					mBackend.cleanOrphanedEntries(true);
					// scan run possibly deleted file which may affect playlists:
					MediaLibrary.notifyObserver(LibraryObserver.Type.PLAYLIST, LibraryObserver.Value.UNKNOWN, false);
				}

				// Send a last change notification to all observers.
				// This lets all consumers know about (possible)
				// cleanups of orphaned files. The `false' value
				// also signals that this will be our last update
				// for this scan.
				mHandler.removeMessages(MSG_NOTIFY_CHANGE);
				MediaLibrary.notifyObserver(LibraryObserver.Type.SONG, LibraryObserver.Value.UNKNOWN, false);
				MediaLibrary.notifyObserver(LibraryObserver.Type.SCAN_PROGRESS, LibraryObserver.Value.UNKNOWN, false);

				updateNotification(false);
				break;
			}
			case MSG_GUESS_QUICKSCAN: {
				guessQuickScanPlan();
				break;
			}
			case RPC_KICKSTART: {
				// a new scan was triggered: check if this is a 'initial / from scratch' scan
				if (!mIsInitialScan && MediaLibrary.getPreferences(mContext)._nativeLastMtime == 0) {
					mIsInitialScan = true;
				}
				break;
			}
			case RPC_INSPECT_FILE: {
				final File file = (File)message.obj;
				boolean changed = rpcInspectFile(file);
				mScanPlan.registerProgress(file.toString(), changed);
				if (changed && !mHandler.hasMessages(MSG_NOTIFY_CHANGE)) {
					mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_NOTIFY_CHANGE), 500);
				}

				// Unlike MSG_NOTIFY_CHANGE, we don't want the progress report to lag behind, but we also don't want
				// to call it on EVERY file inspection; so we add our own delay which will not be influenced by
				// the message queue size.
				long now = SystemClock.uptimeMillis();
				if (now >= mNextProgressReportAt) {
					mNextProgressReportAt = now + 80;
					MediaLibrary.notifyObserver(LibraryObserver.Type.SCAN_PROGRESS, LibraryObserver.Value.UNKNOWN, true);
					updateNotification(true);
				}
				break;
			}
			case RPC_READ_DIR: {
				rpcReadDirectory((File)message.obj);
				break;
			}
			case RPC_LIBRARY_VRFY: {
				rpcLibraryVerify((Cursor)message.obj);
				break;
			}
			case RPC_NATIVE_VRFY: {
				rpcNativeVerify((Cursor)message.obj, message.arg2);
				break;
			}
			default: {
				throw new IllegalArgumentException();
			}
		}

		if (message.what == MSG_SCAN_RPC && !mHandler.hasMessages(MSG_SCAN_RPC)) {
			mScanIsRunning = true;
			MediaScanPlan.Step step = mScanPlan.getNextStep();
			if (step == null) {
				mHandler.sendEmptyMessage(MSG_SCAN_FINISHED);
			} else {
				Log.v("VanillaMusic", "xxx --- starting scan of type "+step.msg);
				mHandler.sendMessage(mHandler.obtainMessage(MSG_SCAN_RPC, step.msg, 0, step.arg));
			}
		}

		return true;
	}

	private static final int MTIME_PRISTINE = 0;
	private static final int MTIME_DIRTY = 1;
	/**
	 * Updates the _nativeLastMtime value in the media scanner storage
	 *
	 * @param mtime or one of MTIME_*
	 */
	private void setNativeLastMtime(int mtime) {
		MediaLibrary.Preferences prefs = MediaLibrary.getPreferences(mContext);
		prefs._nativeLastMtime = mtime;
		MediaLibrary.setPreferences(mContext, prefs);
	}

	/**
	 * Triggers an update to the scan progress notification
	 *
	 * @param visible if true, the notification is visible (and will get updated)
	 */
	private void updateNotification(boolean visible) {
		MediaLibrary.ScanProgress progress = describeScanProgress();

		if (visible) {
			// We there are 5 drawables, pick one based on the 'uptime-seconds'.
			int tick = (int)(SystemClock.uptimeMillis() / 1000) % 5;
			int icon = R.drawable.status_scan_0 + tick;
			String title = mContext.getResources().getString(R.string.media_library_scan_running);
			String content = progress.lastFile;
			Notification notification = mNotificationHelper.getNewBuilder(mContext)
				.setProgress(progress.total, progress.seen, false)
				.setContentTitle(title)
				.setContentText(content)
				.setSmallIcon(icon)
				.setOngoing(true)
				.getNotification(); // build() is API 16 :-/
			mNotificationHelper.notify(NOTIFICATION_ID, notification);

			if (!mWakeLock.isHeld())
				mWakeLock.acquire();
		} else {
			mNotificationHelper.cancel(NOTIFICATION_ID);

			if (mWakeLock.isHeld())
				mWakeLock.release();
		}
	}

	/**
	 * Checks the state of the native media db to deceide if we are going to
	 * check for deleted or new/modified items
	 */
	private void guessQuickScanPlan() {
		int lastSeenDbSize = MediaLibrary.getPreferences(mContext)._nativeLibraryCount;
		String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";
		String[] projection = { "COUNT("+MediaStore.Audio.Media.IS_MUSIC+")" };
		Cursor cursor = null;
		try {
			cursor = mContext.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, null);
		} catch (SecurityException e) {
			Log.e("VanillaMusic", "rpcObserveRemoval query failed: "+e);
		}

		if (cursor == null)
			return;

		cursor.moveToFirst();
		int currentDbSize = cursor.getInt(0);
		cursor.close();

		// Store new db size
		MediaLibrary.Preferences prefs = MediaLibrary.getPreferences(mContext);
		prefs._nativeLibraryCount = currentDbSize;
		MediaLibrary.setPreferences(mContext, prefs);

		if (currentDbSize < lastSeenDbSize) {
			// db is smaller! check for deleted files
			mScanPlan.addNextStep(RPC_LIBRARY_VRFY, null);
		} else {
			// same size or more entries -> check only for modifications
			mScanPlan.addNextStep(RPC_NATIVE_VRFY, null);
		}

		mHandler.sendMessage(mHandler.obtainMessage(MSG_SCAN_RPC, RPC_KICKSTART, 0));
	}

	/**
	 * Scans the android library, inspecting every found file
	 *
	 * @param cursor the cursor we are using
	 * @param mtime the mtime to carry over, ignored if cursor is null
	 */
	private void rpcNativeVerify(Cursor cursor, int mtime) {
		if (cursor == null) {
			mtime = MediaLibrary.getPreferences(mContext)._nativeLastMtime; // starting a new scan -> read stored mtime from preferences
			String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0 AND "+ MediaStore.MediaColumns.DATE_MODIFIED +" > " + (mtime - NATIVE_VRFY_MTIME_SLACK);
			String sort = MediaStore.MediaColumns.DATE_MODIFIED;
			String[] projection = { MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DATE_MODIFIED };
			try {
				cursor = mContext.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, sort);
			} catch(SecurityException e) {
				Log.e("VanillaMusic", "rpcNativeVerify failed: "+e);
			}
		}

		if (cursor == null)
			return; // still null.. fixme: handle me better

		if (cursor.moveToNext()) {
			String path = cursor.getString(0);
			mtime = cursor.getInt(1);
			if (path != null) { // this seems to be a thing...
				File entry = new File(path);
				mHandler.sendMessage(mHandler.obtainMessage(MSG_SCAN_RPC, RPC_INSPECT_FILE, 0, entry));
				mHandler.sendMessage(mHandler.obtainMessage(MSG_SCAN_RPC, RPC_NATIVE_VRFY, mtime, cursor));
			}
		} else {
			cursor.close();
			setNativeLastMtime(mtime);
			Log.v("VanillaMusic", "NativeLibraryScanner finished, mtime mark is now at "+mtime);
		}
	}

	/**
	 * Scans every file in our own library and checks for changes
	 *
	 * @param cursor the cursor we are using
	 */
	private void rpcLibraryVerify(Cursor cursor) {
		if (cursor == null)
			cursor = mBackend.query(false, MediaLibrary.TABLE_SONGS, new String[]{MediaLibrary.SongColumns.PATH}, null, null, null, null, null, null);

		if (cursor.moveToNext()) {
			File entry = new File(cursor.getString(0));
			mHandler.sendMessage(mHandler.obtainMessage(MSG_SCAN_RPC, RPC_INSPECT_FILE, 0, entry));
			mHandler.sendMessage(mHandler.obtainMessage(MSG_SCAN_RPC, RPC_LIBRARY_VRFY, 0, cursor));
		} else {
			cursor.close();
		}
	}

	/**
	 * Loops trough given directory and adds all found
	 * files to the scan queue
	 *
	 * @param dir the directory to scan
	 */
	private void rpcReadDirectory(File dir) {
		if (!dir.isDirectory())
			return;

		if (new File(dir, ".nomedia").exists())
			return;

		if (isDotfile(dir))
			return;

		File[] dirents = dir.listFiles();
		if (dirents == null)
			return;

		for (File file : dirents) {
			int rpc = (file.isFile() ? RPC_INSPECT_FILE : RPC_READ_DIR);
			mHandler.sendMessage(mHandler.obtainMessage(MSG_SCAN_RPC, rpc, 0, file));
		}
	}

	/**
	 * Checks if a string is null, empty or whitespace.
	 *
	 * @param s the string to check
	 * @return true if null, empty or whitespace
	 */
	private static boolean isUnset(String s) {
		return s == null || s.trim().isEmpty();
	}


	/**
	 * Inspects a single file and adds it to the database or removes it. maybe.
	 *
	 * @param file the file to add
	 * @return true if we modified the database
	 */
	private boolean rpcInspectFile(File file) {
		MediaLibrary.Preferences prefs = MediaLibrary.getPreferences(mContext);
		String path  = file.getAbsolutePath();
		long songId  = MediaLibrary.hash63(path);

		if (isBlacklisted(file))
			return false;

		if (isDotfile(file))
			return false;

		long dbEntryMtime = mBackend.getColumnFromSongId(MediaLibrary.SongColumns.MTIME, songId) * 1000; // this is in unixtime -> convert to 'ms'
		long songFlags = mBackend.getColumnFromSongId(MediaLibrary.SongColumns.FLAGS, songId);
		long fileMtime = file.lastModified();
		long playCount = 0;
		long skipCount = 0;
		boolean hasChanged = false;
		boolean mustInsert = false;

		if (fileMtime > 0 && dbEntryMtime >= fileMtime && (songFlags & MediaLibrary.SONG_FLAG_OUTDATED) == 0) {
			return false; // on-disk mtime is older than db mtime and it still exists -> nothing to do
		}

		if (dbEntryMtime != 0) {
			// DB entry exists but is outdated - drop current entry and maybe re-insert it
			// this tries to preserve play and skipcounts of the song
			playCount = mBackend.getColumnFromSongId(MediaLibrary.SongColumns.PLAYCOUNT, songId);
			skipCount = mBackend.getColumnFromSongId(MediaLibrary.SongColumns.SKIPCOUNT, songId);
			// Remove the song from the database for now but do not delete any
			// playlist references to it.
			mBackend.delete(MediaLibrary.TABLE_SONGS, MediaLibrary.SongColumns._ID+"="+songId, null);
			mBackend.cleanOrphanedEntries(false);
			mPendingCleanup = true; // Ensure that we run a full cleanup after all scans finished, to get rid of orphaned playlist entries.
			hasChanged = true; // notify caller about change even if we are not going to re-insert this file.
		}

		// Check if we are willing to insert this file
		// This is the case if we consider it to be playable on this device.
		MediaMetadataExtractor tags = new MediaMetadataExtractor(path, prefs.forceBastp);
		mustInsert = tags.isMediaFile();

		if (mustInsert) {
			hasChanged = true;

			// Clear old flags of this song:
			songFlags &= ~MediaLibrary.SONG_FLAG_OUTDATED;   // This file is not outdated anymore
			songFlags &= ~MediaLibrary.SONG_FLAG_NO_ARTIST;  // May find an artist now.
			songFlags &= ~MediaLibrary.SONG_FLAG_NO_ALBUM;   // May find an album now.


			String lang = tags.getFirst(MediaMetadataExtractor.LANGUAGE);
			if (lang != null) {
				Log.w("VanillaMusic", lang);
			}
			String mood = tags.getFirst(MediaMetadataExtractor.MOOD);
			if (mood != null) {
				Log.w("VanillaMusic", mood);
			}

			// TITLE
			String title = tags.getFirst(MediaMetadataExtractor.TITLE);
			if (isUnset(title))
				title = file.getName();

			// ALBUM
			String album = tags.getFirst(MediaMetadataExtractor.ALBUM);
			if (isUnset(album)) {
				album = "<No Album>";
				songFlags |= MediaLibrary.SONG_FLAG_NO_ALBUM;
			}

			long albumId = MediaLibrary.hash63(album);
			// Overwrite albumId with a hash that included the parent dir if set in preferences
			if (prefs.groupAlbumsByFolder) {
				albumId = MediaLibrary.hash63(album + "\n" + file.getParent());
			}

			// ARTISTS AND ALBUM ARTISTS
			ArrayList<String> performers = null;
			if (tags.containsKey(MediaMetadataExtractor.ARTIST)) {
				performers = tags.get(MediaMetadataExtractor.ARTIST);
			}

			ArrayList<String> albumArtists = null;
			if (tags.containsKey(MediaMetadataExtractor.ALBUMARTIST)) {
				albumArtists = tags.get(MediaMetadataExtractor.ALBUMARTIST);
			}

			// Try to use the album artist as the primary artist.
			String primaryArtist = null;
			if (albumArtists != null && albumArtists.size() > 0) {
				primaryArtist = albumArtists.get(0);
			}

			// If primary artist not found yet, try to use the first artist tag
			if (isUnset(primaryArtist)) {
				if (performers != null && performers.size() > 0) {
					primaryArtist = performers.get(0);
				}
			}

			// No artist found :(
			if (isUnset(primaryArtist)) {
				primaryArtist = "<No Artist>";
				songFlags |= MediaLibrary.SONG_FLAG_NO_ARTIST;
				// If we get to this point there are no artists or album artists so add the default
				performers = new ArrayList<>();
				performers.add(primaryArtist);
			}

			long primaryArtistId = MediaLibrary.hash63(primaryArtist);

			// DISC NUMBER
			String discNumber = tags.getFirst(MediaMetadataExtractor.DISC_NUMBER);
			if (isUnset(discNumber))
				discNumber = "1"; // untagged, but most likely '1' - this prevents annoying sorting issues with partially tagged files

			// YEAR
			String year = tags.getFirst(MediaMetadataExtractor.YEAR);

			// SONG TABLE
			ContentValues v = new ContentValues();
			v.put(MediaLibrary.SongColumns._ID,         songId);
			v.put(MediaLibrary.SongColumns.TITLE,       title);
			v.put(MediaLibrary.SongColumns.TITLE_SORT,  MediaLibrary.keyFor(title));
			v.put(MediaLibrary.SongColumns.ALBUM_ID,    albumId);
			v.put(MediaLibrary.SongColumns.DURATION,    tags.getFirst(MediaMetadataExtractor.DURATION));
			v.put(MediaLibrary.SongColumns.SONG_NUMBER, tags.getFirst(MediaMetadataExtractor.TRACK_NUMBER));
			v.put(MediaLibrary.SongColumns.DISC_NUMBER, discNumber);
			v.put(MediaLibrary.SongColumns.YEAR,        year);
			v.put(MediaLibrary.SongColumns.PLAYCOUNT,   playCount);
			v.put(MediaLibrary.SongColumns.SKIPCOUNT,   skipCount);
			v.put(MediaLibrary.SongColumns.PATH,        path);
			v.put(MediaLibrary.SongColumns.FLAGS,       songFlags);
			mBackend.insert(MediaLibrary.TABLE_SONGS, null, v);

			// ALBUMS TABLE
			v.clear();
			v.put(MediaLibrary.AlbumColumns._ID,               albumId);
			v.put(MediaLibrary.AlbumColumns.ALBUM,             album);
			v.put(MediaLibrary.AlbumColumns.ALBUM_SORT,        MediaLibrary.keyFor(album));
			v.put(MediaLibrary.AlbumColumns.PRIMARY_ARTIST_ID, primaryArtistId);
			v.put(MediaLibrary.AlbumColumns.PRIMARY_ALBUM_YEAR, year);
			long albumInsert = mBackend.insert(MediaLibrary.TABLE_ALBUMS, null, v);
			if (albumInsert == -1) {
				// Insert failed, so the column probably already existed.
				// We need to ensure that the album table is up-to-date as it contains
				// some 'cached' (PRIMARY_*) values.
				// Failure to do so would mean that we never update the year or may point to an
				// orphaned artist id.
				v.clear();
				v.put(MediaLibrary.AlbumColumns.PRIMARY_ARTIST_ID, primaryArtistId);
				v.put(MediaLibrary.AlbumColumns.PRIMARY_ALBUM_YEAR, year);
				mBackend.update(MediaLibrary.TABLE_ALBUMS, v, MediaLibrary.AlbumColumns._ID+"=?", new String[]{ Long.toString(albumId) });
			}

			// CONTRIBUTORS: ALBUM ARTIST (we support a single album artist which is the primary artist)
			// In theory primary artist will never be null but just in case
			if (primaryArtist != null) {
				long albumArtistId = MediaLibrary.hash63(primaryArtist);
				v.clear();
				v.put(MediaLibrary.ContributorColumns._ID,               albumArtistId);
				v.put(MediaLibrary.ContributorColumns._CONTRIBUTOR,      primaryArtist);
				v.put(MediaLibrary.ContributorColumns._CONTRIBUTOR_SORT, MediaLibrary.keyFor(primaryArtist));
				mBackend.insert(MediaLibrary.TABLE_CONTRIBUTORS, null, v);

				v.clear();
				v.put(MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID, albumArtistId);
				v.put(MediaLibrary.ContributorSongColumns.SONG_ID,         songId);
				v.put(MediaLibrary.ContributorSongColumns.ROLE,            MediaLibrary.ROLE_ALBUMARTIST);
				mBackend.insert(MediaLibrary.TABLE_CONTRIBUTORS_SONGS, null, v);
			}

			// CONTRIBUTORS: ARTISTS (we support multiple artists)
			if (performers != null) {
				for (String performer: performers) {
					long artistId = MediaLibrary.hash63(performer);
					v.clear();
					v.put(MediaLibrary.ContributorColumns._ID,               artistId);
					v.put(MediaLibrary.ContributorColumns._CONTRIBUTOR,      performer);
					v.put(MediaLibrary.ContributorColumns._CONTRIBUTOR_SORT, MediaLibrary.keyFor(performer));
					mBackend.insert(MediaLibrary.TABLE_CONTRIBUTORS, null, v);

					v.clear();
					v.put(MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID, artistId);
					v.put(MediaLibrary.ContributorSongColumns.SONG_ID,         songId);
					v.put(MediaLibrary.ContributorSongColumns.ROLE,            MediaLibrary.ROLE_ARTIST);
					mBackend.insert(MediaLibrary.TABLE_CONTRIBUTORS_SONGS, null, v);
				}
			}

			// CONTRIBUTORS: COMPOSER
			String composer = tags.getFirst(MediaMetadataExtractor.COMPOSER);
			if (composer != null) {
				long composerId = MediaLibrary.hash63(composer);
				v.clear();
				v.put(MediaLibrary.ContributorColumns._ID,               composerId);
				v.put(MediaLibrary.ContributorColumns._CONTRIBUTOR,      composer);
				v.put(MediaLibrary.ContributorColumns._CONTRIBUTOR_SORT, MediaLibrary.keyFor(composer));
				mBackend.insert(MediaLibrary.TABLE_CONTRIBUTORS, null, v);

				v.clear();
				v.put(MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID, composerId);
				v.put(MediaLibrary.ContributorSongColumns.SONG_ID,         songId);
				v.put(MediaLibrary.ContributorSongColumns.ROLE,            MediaLibrary.ROLE_COMPOSER);
				mBackend.insert(MediaLibrary.TABLE_CONTRIBUTORS_SONGS, null, v);
			}

			// GENRES (multiple genre support)
			if (tags.containsKey(MediaMetadataExtractor.GENRE)) {
				ArrayList<String> genres = tags.get(MediaMetadataExtractor.GENRE);
				for (String genre : genres) {
					long genreId = MediaLibrary.hash63(genre);
					v.clear();
					v.put(MediaLibrary.GenreColumns._ID,         genreId);
					v.put(MediaLibrary.GenreColumns._GENRE,      genre);
					v.put(MediaLibrary.GenreColumns._GENRE_SORT, MediaLibrary.keyFor(genre));
					mBackend.insert(MediaLibrary.TABLE_GENRES, null, v);

					v.clear();
					v.put(MediaLibrary.GenreSongColumns._GENRE_ID, genreId);
					v.put(MediaLibrary.GenreSongColumns.SONG_ID, songId);
					mBackend.insert(MediaLibrary.TABLE_GENRES_SONGS, null, v);
				}
			}
		} // end if (mustInsert)

		Log.v("VanillaMusic", "MediaScanner: inserted "+path);
		return hasChanged;
	}

	private static final Pattern sIgnoredFilenames = Pattern.compile("^([^\\.]+|.+\\.(jpe?g|gif|png|bmp|webm|txt|pdf|avi|mp4|mkv|zip|tgz|xml|tmp|bin))$", Pattern.CASE_INSENSITIVE);
	/**
	 * Returns true if the file should not be scanned
	 *
	 * @param file the file to inspect
	 * @return boolean
	 */
	private boolean isBlacklisted(File file) {
		if (sIgnoredFilenames.matcher(file.getName()).matches())
			return true;

		int wlPoints = -1;
		int blPoints = -1;

		for (String path : MediaLibrary.getPreferences(mContext).mediaFolders) {
			if (path.length() > wlPoints &&
			    file.getPath().toLowerCase().startsWith(path.toLowerCase())) {
				wlPoints = path.length();
			}
		}

		for (String path : MediaLibrary.getPreferences(mContext).blacklistedFolders) {
			if (path.length() > blPoints &&
			    file.getPath().toLowerCase().startsWith(path.toLowerCase())) {
				blPoints = path.length();
			}
		}

		// Consider a file to be blacklisted if it is not
		// present in any whitelisted dir OR if we found
		// a blacklist entry with a longer prefix.
		return (wlPoints < 0 || blPoints > wlPoints);
	}


	private static final Pattern sDotfilePattern = Pattern.compile("^\\..*$", Pattern.CASE_INSENSITIVE);
	/**
	 * Returns true if the file is a hidden dotfile.
	 *
	 * @param file to inspect
	 * @return boolean
	 */
	private boolean isDotfile(File file) {
		return sDotfilePattern.matcher(file.getName()).matches();
	}

	// MediaScanPlan describes how we are going to perform the media scan
	class MediaScanPlan {
		class Step {
			private static final int MODE_NORMAL   = 1; // this step is always run
			private static final int MODE_OPTIONAL = 2; // only run if previous step found NO changes
			private static final int MODE_CHAINED  = 3; // only run if previous step DID find changes
			int msg;
			Object arg;
			int mode;
			Step (int msg, Object arg, int mode) {
				this.msg = msg;
				this.arg = arg;
				this.mode = mode;
			}
		}

		class Statistics {
			String lastFile;
			int seen = 0;
			int changed = 0;
			void reset() {
				this.seen = 0;
				this.changed = 0;
				this.lastFile = null;
			}
		}

		/**
		 * All steps in this plan
		 */
		private ArrayList<Step> mSteps;
		/**
		 * Statistics of the currently running step
		 */
		private Statistics mStats;

		MediaScanPlan() {
			mSteps = new ArrayList<>();
			mStats = new Statistics();
		}

		Statistics getStatistics() {
			return mStats;
		}

		/**
		 * Called by the scanner to signal that a file was handled
		 *
		 * @param path the file we scanned
		 * @param changed true if this triggered a database update
		 */
		void registerProgress(String path, boolean changed) {
			mStats.lastFile = path;
			mStats.seen++;
			if (changed) {
				mStats.changed++;
			}
		}

		/**
		 * Flushes all progress, turning the object into a fresh state
		 */
		void clear() {
			mSteps.clear();
			mStats.reset();
		}

		/**
		 * Adds the next step in our plan
		 *
		 * @param msg the message to add
		 * @param arg the argument to msg
		 */
		MediaScanPlan addNextStep(int msg, Object arg) {
			mSteps.add(new Step(msg, arg, Step.MODE_NORMAL));
			return this;
		}

		/**
		 * Adds an optional step to our plan. This will NOT
		 * run if the previous step caused database changes
		 *
		 * @param msg the message to add
		 * @param arg the argument to msg
		 */
		MediaScanPlan addOptionalStep(int msg, Object arg) {
			mSteps.add(new Step(msg, arg, Step.MODE_OPTIONAL));
			return this;
		}

		/**
		 * Adds a chained step to our plan. This will ONLY
		 * run if the previous step caused database changes
		 *
		 * @param msg the message to add
		 * @param arg the argument to msg
		 */
		MediaScanPlan addChainedStep(int msg, Object arg) {
			mSteps.add(new Step(msg, arg, Step.MODE_CHAINED));
			return this;
		}

		/**
		 * Returns the next step of our scan plan
		 *
		 * @return a new step object, null if we hit the end
		 */
		Step getNextStep() {
			Step next = (mSteps.size() != 0 ? mSteps.remove(0) : null);
			if (next != null) {
				if (next.mode == Step.MODE_OPTIONAL && mStats.changed != 0) {
					next = null;
					mSteps.clear();
				}
				if (next.mode == Step.MODE_CHAINED && mStats.changed == 0) {
					next = null;
					mSteps.clear();
				}
			}
			mStats.reset();
			return next;
		}
	}
}

