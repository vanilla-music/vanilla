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

package ch.blinkenlights.android.medialibrary;

import ch.blinkenlights.android.vanilla.R;

import android.app.Notification;
import android.app.NotificationManager;

import android.content.Context;
import android.content.ContentValues;
import android.content.SharedPreferences;
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
	 * Timestamp in half-seconds since last notification
	 */
	private int mLastNotification;
	/**
	 * The id we are using for the scan notification
	 */
	private static final int NOTIFICATION_ID = 56162;

	MediaScanner(Context context, MediaLibraryBackend backend) {
		mContext = context;
		mBackend = backend;
		mScanPlan = new MediaScanPlan();
		HandlerThread handlerThread = new HandlerThread("MediaScannerThread", Process.THREAD_PRIORITY_LOWEST);
		handlerThread.start();
		mHandler = new Handler(handlerThread.getLooper(), this);
		mWakeLock = ((PowerManager)context.getSystemService(Context.POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VanillaMusicIndexerLock");

		// the content observer to use
		ContentObserver mObserver = new ContentObserver(null) {
			@Override
			public void onChange(boolean self) {
				startQuickScan(1500);
			}
		};
		context.getContentResolver().registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, false, mObserver);
	}

	/**
	 * Performs a 'fast' scan by checking the native and our own
	 * library for new and changed files
	 */
	public void startNormalScan() {
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
	 * Drops the media library
	 */
	public void flushDatabase() {
		mBackend.delete(MediaLibrary.TABLE_SONGS, null, null);
		mBackend.cleanOrphanedEntries(false); // -> keep playlists

		MediaLibrary.Preferences prefs = MediaLibrary.getPreferences(mContext);
		prefs._nativeLastMtime = 0;
		MediaLibrary.setPreferences(mContext, prefs);
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

		progress.isRunning = (stats.lastFile != null);
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
				MediaLibrary.notifyObserver();
				break;
			}
			case MSG_SCAN_FINISHED: {
				if (mIsInitialScan) {
					mIsInitialScan = false;
					PlaylistBridge.importAndroidPlaylists(mContext);
				}
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
				updateNotification(true);
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
			MediaScanPlan.Step step = mScanPlan.getNextStep();
			if (step == null) {
				mHandler.sendEmptyMessage(MSG_SCAN_FINISHED);
			} else {
				Log.v("VanillaMusic", "--- starting scan of type "+step.msg);
				mHandler.sendMessage(mHandler.obtainMessage(MSG_SCAN_RPC, step.msg, 0, step.arg));
			}
		}

		return true;
	}

	/**
	 * Triggers an update to the scan progress notification
	 *
	 * @param visible if true, the notification is visible (and will get updated)
	 */
	private void updateNotification(boolean visible) {
		MediaLibrary.ScanProgress progress = describeScanProgress();
		NotificationManager manager = (NotificationManager) mContext.getSystemService(mContext.NOTIFICATION_SERVICE);

		if (visible) {
			int nowTime = (int)(SystemClock.uptimeMillis() / 500);
			if (nowTime != mLastNotification) {
				mLastNotification = nowTime;
				int icon = R.drawable.status_scan_0 + (mLastNotification % 5);
				String title = mContext.getResources().getString(R.string.media_library_scan_running);
				String content = progress.lastFile;

				Notification notification = new Notification.Builder(mContext)
					.setProgress(progress.total, progress.seen, false)
					.setContentTitle(title)
					.setContentText(content)
					.setSmallIcon(icon)
					.setOngoing(true)
					.getNotification(); // build() is API 16 :-/
				manager.notify(NOTIFICATION_ID, notification);
			}

			if (!mWakeLock.isHeld())
				mWakeLock.acquire();
		} else {
			manager.cancel(NOTIFICATION_ID);

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
		String[] projection = { "COUNT(*)" };
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
			String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0 AND "+ MediaStore.MediaColumns.DATE_MODIFIED +" > " + mtime;
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
			MediaLibrary.Preferences prefs = MediaLibrary.getPreferences(mContext);
			prefs._nativeLastMtime = mtime;
			MediaLibrary.setPreferences(mContext, prefs);
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

		File[] dirents = dir.listFiles();
		if (dirents == null)
			return;

		for (File file : dirents) {
			int rpc = (file.isFile() ? RPC_INSPECT_FILE : RPC_READ_DIR);
			mHandler.sendMessage(mHandler.obtainMessage(MSG_SCAN_RPC, rpc, 0, file));
		}
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

		long dbEntryMtime = mBackend.getSongMtime(songId) * 1000; // this is in unixtime -> convert to 'ms'
		long fileMtime = file.lastModified();
		boolean hasChanged = false;
		boolean mustInsert = true;

		if (fileMtime > 0 && dbEntryMtime >= fileMtime) {
			return false; // on-disk mtime is older than db mtime and it still exists -> nothing to do
		}

		if (dbEntryMtime != 0) {
			// DB entry exists but is outdated - drop current entry and maybe re-insert it
			// fixme: drops play counts :-(
			mBackend.delete(MediaLibrary.TABLE_SONGS, MediaLibrary.SongColumns._ID+"="+songId, null);
			hasChanged = true;
		}

		MediaMetadataExtractor tags = new MediaMetadataExtractor(path, prefs.forceBastp);
		if (!tags.isMediaFile()) {
			mustInsert = false; // does not have any useable metadata: won't insert even if it is a playable file
		}

		if (hasChanged) {
			boolean purgeUserData = (mustInsert ? false : true);
			mBackend.cleanOrphanedEntries(purgeUserData);
		}

		if (mustInsert) {
			hasChanged = true;

			// Get tags which always must be set
			String title = tags.getFirst(MediaMetadataExtractor.TITLE);
			if (title == null)
				title = file.getName();

			String album = tags.getFirst(MediaMetadataExtractor.ALBUM);
			if (album == null)
				album = "<No Album>";

			String artist = tags.getFirst(MediaMetadataExtractor.ARTIST);
			if (artist == null)
				artist = "<No Artist>";

			String discNumber = tags.getFirst(MediaMetadataExtractor.DISC_NUMBER);
			if (discNumber == null)
				discNumber = "1"; // untagged, but most likely '1' - this prevents annoying sorting issues with partially tagged files

			long artistId = MediaLibrary.hash63(artist);
			long albumId = MediaLibrary.hash63(album);

			// Overwrite albumId with a hash that included the parent dir if set in preferences
			if (prefs.groupAlbumsByFolder) {
				albumId = MediaLibrary.hash63(album + "\n" + file.getParent());
			}

			ContentValues v = new ContentValues();
			v.put(MediaLibrary.SongColumns._ID,         songId);
			v.put(MediaLibrary.SongColumns.TITLE,       title);
			v.put(MediaLibrary.SongColumns.TITLE_SORT,  MediaLibrary.keyFor(title));
			v.put(MediaLibrary.SongColumns.ALBUM_ID,    albumId);
			v.put(MediaLibrary.SongColumns.DURATION,    tags.getFirst(MediaMetadataExtractor.DURATION));
			v.put(MediaLibrary.SongColumns.SONG_NUMBER, tags.getFirst(MediaMetadataExtractor.TRACK_NUMBER));
			v.put(MediaLibrary.SongColumns.DISC_NUMBER, discNumber);
			v.put(MediaLibrary.SongColumns.YEAR,        tags.getFirst(MediaMetadataExtractor.YEAR));
			v.put(MediaLibrary.SongColumns.PATH,        path);
			mBackend.insert(MediaLibrary.TABLE_SONGS, null, v);

			v.clear();
			v.put(MediaLibrary.AlbumColumns._ID,               albumId);
			v.put(MediaLibrary.AlbumColumns.ALBUM,             album);
			v.put(MediaLibrary.AlbumColumns.ALBUM_SORT,        MediaLibrary.keyFor(album));
			v.put(MediaLibrary.AlbumColumns.PRIMARY_ARTIST_ID, artistId);
			v.put(MediaLibrary.AlbumColumns.PRIMARY_ALBUM_YEAR,tags.getFirst(MediaMetadataExtractor.YEAR));
			mBackend.insert(MediaLibrary.TABLE_ALBUMS, null, v);

			v.clear();
			v.put(MediaLibrary.ContributorColumns._ID,               artistId);
			v.put(MediaLibrary.ContributorColumns._CONTRIBUTOR,      artist);
			v.put(MediaLibrary.ContributorColumns._CONTRIBUTOR_SORT, MediaLibrary.keyFor(artist));
			mBackend.insert(MediaLibrary.TABLE_CONTRIBUTORS, null, v);

			v.clear();
			v.put(MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID, artistId);
			v.put(MediaLibrary.ContributorSongColumns.SONG_ID,         songId);
			v.put(MediaLibrary.ContributorSongColumns.ROLE,            MediaLibrary.ROLE_ARTIST);
			mBackend.insert(MediaLibrary.TABLE_CONTRIBUTORS_SONGS, null, v);

			// Composers are optional: only add if we found it
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

			// Same as with composer: albumartist is an optional tag
			String albumartist = tags.getFirst(MediaMetadataExtractor.ALBUMARTIST);
			if (albumartist != null) {
				long albumartistId = MediaLibrary.hash63(albumartist);
				v.clear();
				v.put(MediaLibrary.ContributorColumns._ID,               albumartistId);
				v.put(MediaLibrary.ContributorColumns._CONTRIBUTOR,      albumartist);
				v.put(MediaLibrary.ContributorColumns._CONTRIBUTOR_SORT, MediaLibrary.keyFor(albumartist));
				mBackend.insert(MediaLibrary.TABLE_CONTRIBUTORS, null, v);

				v.clear();
				v.put(MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID, albumartistId);
				v.put(MediaLibrary.ContributorSongColumns.SONG_ID,         songId);
				v.put(MediaLibrary.ContributorSongColumns.ROLE,            MediaLibrary.ROLE_ALBUMARTIST);
				mBackend.insert(MediaLibrary.TABLE_CONTRIBUTORS_SONGS, null, v);
			}

			// A song might be in multiple genres
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

	private static final Pattern sIgnoredFilenames = Pattern.compile("^([^\\.]+|.+\\.(jpe?g|gif|png|bmp|webm|txt|pdf|avi|mp4|mkv|zip|tgz|xml))$", Pattern.CASE_INSENSITIVE);
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
			    file.getPath().startsWith(path)) {
				wlPoints = path.length();
			}
		}

		for (String path : MediaLibrary.getPreferences(mContext).blacklistedFolders) {
			if (path.length() > blPoints &&
			    file.getPath().startsWith(path)) {
				blPoints = path.length();
			}
		}

		// Consider a file to be blacklisted if it is not
		// present in any whitelisted dir OR if we found
		// a blacklist entry with a longer prefix.
		return (wlPoints < 0 || blPoints > wlPoints);
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

