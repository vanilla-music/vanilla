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

import ch.blinkenlights.bastp.Bastp;
import android.content.ContentValues;
import android.media.MediaMetadataRetriever;
import android.util.Log;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.os.SystemClock;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;

public class MediaScanner implements Handler.Callback {
	/**
	 * How long to wait until we post an update notification
	 */
	private final static int SCAN_NOTIFY_DELAY_MS = 1200;
	/**
	 * At which (up-)time we shall trigger the next notification
	 */
	private long mNextNotification = 0;
	/**
	 * The backend instance we are acting on
	 */
	private MediaLibraryBackend mBackend;
	/**
	 * Our message handler
	 */
	private Handler mHandler;
	/**
	 * Files we are ignoring based on their filename
	 */
	private static final Pattern sIgnoredNames = Pattern.compile("^([^\\.]+|.+\\.(jpe?g|gif|png|bmp|webm|txt|pdf|avi|mp4|mkv|zip|tgz|xml))$", Pattern.CASE_INSENSITIVE);
	/**
	 * Constructs a new MediaScanner instance
	 *
	 * @param backend the backend to use
	 */
	MediaScanner(MediaLibraryBackend backend) {
		mBackend = backend;
		HandlerThread handlerThread = new HandlerThread("MediaScannerThred", Process.THREAD_PRIORITY_LOWEST);
		handlerThread.start();
		mHandler = new Handler(handlerThread.getLooper(), this);
	}

	/**
	 * Initiates a scan at given directory
	 *
	 * @param dir the directory to scan
	 */
	void startScan(File dir) {
		mHandler.sendMessage(mHandler.obtainMessage(MSG_SCAN_DIRECTORY, 0, 0, dir));
	}

	private static final int MSG_SCAN_DIRECTORY = 1;
	private static final int MSG_ADD_FILE       = 2;

	@Override
	public boolean handleMessage(Message message) {
		File file = (File)message.obj;
		switch (message.what) {
			case MSG_SCAN_DIRECTORY: {
				scanDirectory(file);
				break;
			}
			case MSG_ADD_FILE: {
				long now = SystemClock.uptimeMillis();
				boolean changed = addFile(file);

				// Notify the observer if this was the last message OR if the deadline was reached
				if (mHandler.hasMessages(MSG_ADD_FILE) == false || (mNextNotification != 0 && now >= mNextNotification)) {
					MediaLibrary.notifyObserver();
					mNextNotification = 0;
				}

				// Initiate a new notification trigger if the old one fired and we got a change
				if (changed && mNextNotification == 0)
					mNextNotification = now + SCAN_NOTIFY_DELAY_MS;

				break;
			}
			default: {
				throw new IllegalArgumentException();
			}
		}

		return true;
	}

	/**
	 * Scans a directory for indexable files
	 *
	 * @param dir the directory to scan
	 */
	private void scanDirectory(File dir) {
		if (dir.isDirectory() == false)
			return;

		if (new File(dir, ".nomedia").exists())
			return;

		File[] dirents = dir.listFiles();
		if (dirents == null)
			return;

		for (File file : dirents) {
			if (file.isFile()) {
				mHandler.sendMessage(mHandler.obtainMessage(MSG_ADD_FILE, 0, 0, file));
			} else if (file.isDirectory()) {
				mHandler.sendMessage(mHandler.obtainMessage(MSG_SCAN_DIRECTORY, 0, 0, file));
			}
		}
	}

	/**
	 * Returns true if the file should not be scanned
	 *
	 * @param file the file to inspect
	 * @return boolean
	 */
	private boolean isBlacklisted(File file) {
		return sIgnoredNames.matcher(file.getName()).matches();
	}

	/**
	 * Scans a single file and adds it to the database
	 *
	 * @param file the file to add
	 * @return true if we modified the database
	 */
	private boolean addFile(File file) {
		String path  = file.getAbsolutePath();
		long songId  = MediaLibrary.hash63(path);

		if (isBlacklisted(file))
			return false;

		long dbEntryMtime = mBackend.getSongMtime(songId) * 1000; // this is in unixtime -> convert to 'ms'
		long fileMtime = file.lastModified();
		boolean needsInsert = true;
		boolean needsCleanup = false;

		if (dbEntryMtime >= fileMtime) {
			return false; // on-disk mtime is older than db mtime -> nothing to do
		}

		if (dbEntryMtime != 0) {
			// file on disk is newer: delete old entry and re-insert it
			// fixme: drops play counts :-(
			mBackend.delete(MediaLibrary.TABLE_SONGS, MediaLibrary.SongColumns._ID+"="+songId, null);
			needsCleanup = true;
		}

		MediaMetadataExtractor tags = new MediaMetadataExtractor(path);
		if (tags.isTagged() == false) {
			needsInsert = false; // does not have any useable metadata: wont insert even if it is a playable file
		}

		if (needsInsert) {
			// Get tags which always must be set
			String title = tags.getFirst(MediaMetadataExtractor.TITLE);
			if (title == null)
				title = "Untitled";

			String album = tags.getFirst(MediaMetadataExtractor.ALBUM);
			if (album == null)
				album = "No Album";

			String artist = tags.getFirst(MediaMetadataExtractor.ARTIST);
			if (artist == null)
				artist = "No Artist";


			long albumId = MediaLibrary.hash63(album);
			long artistId = MediaLibrary.hash63(artist);

			ContentValues v = new ContentValues();
			v.put(MediaLibrary.SongColumns._ID,         songId);
			v.put(MediaLibrary.SongColumns.TITLE,       title);
			v.put(MediaLibrary.SongColumns.TITLE_SORT,  MediaLibrary.keyFor(title));
			v.put(MediaLibrary.SongColumns.ALBUM_ID,    albumId);
			v.put(MediaLibrary.SongColumns.DURATION,    tags.getFirst(MediaMetadataExtractor.DURATION));
			v.put(MediaLibrary.SongColumns.SONG_NUMBER, tags.getFirst(MediaMetadataExtractor.TRACK_NUMBER));
			v.put(MediaLibrary.SongColumns.YEAR,        tags.getFirst(MediaMetadataExtractor.YEAR));
			v.put(MediaLibrary.SongColumns.PATH,        path);
			mBackend.insert(MediaLibrary.TABLE_SONGS, null, v);

			v.clear();
			v.put(MediaLibrary.AlbumColumns._ID,               albumId);
			v.put(MediaLibrary.AlbumColumns.ALBUM,             album);
			v.put(MediaLibrary.AlbumColumns.ALBUM_SORT,        MediaLibrary.keyFor(album));
			v.put(MediaLibrary.AlbumColumns.PRIMARY_ARTIST_ID, artistId);
			v.put(MediaLibrary.AlbumColumns.PRIMARY_ALBUM_YEAR,tags.getFirst(MediaMetadataExtractor.YEAR));
			v.put(MediaLibrary.AlbumColumns.DISC_NUMBER,       tags.getFirst(MediaMetadataExtractor.DISC_NUMBER));
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
		} // end if (needsInsert)


		if (needsCleanup)
			mBackend.cleanOrphanedEntries();

		Log.v("VanillaMusic", "MediaScanner: inserted "+path);
		return (needsInsert || needsCleanup);
	}

}

