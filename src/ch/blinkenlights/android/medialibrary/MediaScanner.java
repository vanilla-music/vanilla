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

import java.io.File;
import java.util.HashMap;
import java.util.Vector;

public class MediaScanner implements Handler.Callback {
	/**
	 * The backend instance we are acting on
	 */
	private MediaLibraryBackend mBackend;
	/**
	 * Our message handler
	 */
	private Handler mHandler;

	/**
	 * Constructs a new MediaScanner instance
	 *
	 * @param backend the backend to use
	 */
	public MediaScanner(MediaLibraryBackend backend) {
		mBackend = backend;
		HandlerThread handlerThread = new HandlerThread("MediaScannerThred", Process.THREAD_PRIORITY_LOWEST);
		handlerThread.start();
		mHandler = new Handler(handlerThread.getLooper(), this);
	}

	public void startScan(File dir) {
		mHandler.sendMessage(mHandler.obtainMessage(MSG_SCAN_DIRECTORY, 1, 0, dir));
	}

	private static final int MSG_SCAN_DIRECTORY = 1;
	private static final int MSG_SCAN_FILE      = 2;
	@Override
	public boolean handleMessage(Message message) {
		File file = (File)message.obj;
		switch (message.what) {
			case MSG_SCAN_DIRECTORY: {
				boolean recursive = (message.arg1 == 0 ? false : true);
				scanDirectory(file, recursive);
				break;
			}
			case MSG_SCAN_FILE: {
				scanFile(file);
				break;
			}
			default: {
				throw new IllegalArgumentException();
			}
		}
		return true;
	}


	private void scanDirectory(File dir, boolean recursive) {
		if (dir.isDirectory() == false)
			return;

		File[] dirents = dir.listFiles();
		if (dirents == null)
			return;

		for (File file : dirents) {
			if (file.isFile()) {
				Log.v("VanillaMusic", "MediaScanner: inspecting file "+file);
				//scanFile(file);
				mHandler.sendMessage(mHandler.obtainMessage(MSG_SCAN_FILE, 0, 0, file));
			}
			else if (file.isDirectory() && recursive) {
				Log.v("VanillaMusic", "MediaScanner: scanning subdir "+file);
				//scanDirectory(file, recursive);
				mHandler.sendMessage(mHandler.obtainMessage(MSG_SCAN_DIRECTORY, 1, 0, file));
			}
		}
	}

	private void scanFile(File file) {
		String path  = file.getAbsolutePath();
		long songId  = hash63(path);

		HashMap tags = (new Bastp()).getTags(path);
		if (tags.containsKey("type") == false)
			return; // no tags found

Log.v("VanillaMusic", "> Found mime "+((String)tags.get("type")));

		if (mBackend.isSongExisting(songId)) {
			Log.v("VanillaMusic", "Skipping already known song with id "+songId);
			return;
		}

		MediaMetadataRetriever data = new MediaMetadataRetriever();
		try {
			data.setDataSource(path);
		} catch (Exception e) {
				Log.w("VanillaMusic", "Failed to extract metadata from " + path);
		}

		String duration = data.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
		if (duration == null)
			return; // not a supported media file!

		if (data.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == null)
			return; // no audio -> do not index

		if (data.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != null)
			return; // has a video stream -> do not index

		String title = (tags.containsKey("TITLE") ? (String)((Vector)tags.get("TITLE")).get(0) : "Untitled");
		String album = (tags.containsKey("ALBUM") ? (String)((Vector)tags.get("ALBUM")).get(0) : "No Album");
		String artist = (tags.containsKey("ARTIST") ? (String)((Vector)tags.get("ARTIST")).get(0) : "Unknown Artist");

		String songnum = data.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);
		String composer = data.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER);

		long albumId = hash63(album);
		long artistId = hash63(artist);
		long composerId = hash63(composer);

		ContentValues v = new ContentValues();
		v.put(MediaLibrary.SongColumns._ID,        songId);
		v.put(MediaLibrary.SongColumns.TITLE,      title);
		v.put(MediaLibrary.SongColumns.TITLE_SORT, MediaLibrary.keyFor(title));
		v.put(MediaLibrary.SongColumns.ALBUM_ID,   albumId);
		v.put(MediaLibrary.SongColumns.DURATION,   duration);
		v.put(MediaLibrary.SongColumns.SONG_NUMBER,songnum);
		v.put(MediaLibrary.SongColumns.PATH,       path);
		mBackend.insert(MediaLibrary.TABLE_SONGS, null, v);

		v.clear();
		v.put(MediaLibrary.AlbumColumns._ID,            albumId);
		v.put(MediaLibrary.AlbumColumns.ALBUM,          album);
		v.put(MediaLibrary.AlbumColumns.ALBUM_SORT,     MediaLibrary.keyFor(album));
		v.put(MediaLibrary.AlbumColumns.PRIMARY_ARTIST_ID, artistId);
		mBackend.insert(MediaLibrary.TABLE_ALBUMS, null, v);

		v.clear();
		v.put(MediaLibrary.ContributorColumns._ID,              artistId);
		v.put(MediaLibrary.ContributorColumns._CONTRIBUTOR,      artist);
		v.put(MediaLibrary.ContributorColumns._CONTRIBUTOR_SORT, MediaLibrary.keyFor(artist));
		mBackend.insert(MediaLibrary.TABLE_CONTRIBUTORS, null, v);

		v.clear();
		v.put(MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID, artistId);
		v.put(MediaLibrary.ContributorSongColumns.SONG_ID,       songId);
		v.put(MediaLibrary.ContributorSongColumns.ROLE,           0);
		mBackend.insert(MediaLibrary.TABLE_CONTRIBUTORS_SONGS, null, v);

		if (composer != null) {
			v.clear();
			v.put(MediaLibrary.ContributorColumns._ID,              composerId);
			v.put(MediaLibrary.ContributorColumns._CONTRIBUTOR,      composer);
			v.put(MediaLibrary.ContributorColumns._CONTRIBUTOR_SORT, MediaLibrary.keyFor(composer));
			mBackend.insert(MediaLibrary.TABLE_CONTRIBUTORS, null, v);

			v.clear();
			v.put(MediaLibrary.ContributorSongColumns._CONTRIBUTOR_ID, composerId);
			v.put(MediaLibrary.ContributorSongColumns.SONG_ID,       songId);
			v.put(MediaLibrary.ContributorSongColumns.ROLE,           1);
			mBackend.insert(MediaLibrary.TABLE_CONTRIBUTORS_SONGS, null, v);
		}

		if (tags.containsKey("GENRE")) {
			Vector<String> genres = (Vector)tags.get("GENRE");
			for (String genre : genres) {
				long genreId = hash63(genre);
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

		Log.v("VanillaMusic", "MediaScanner: inserted "+path);
	}


	/**
	 * Simple 63 bit hash function for strings
	 */
	private long hash63(String str) {
		if (str == null)
			return 0;

		long hash = 0;
		int len = str.length();
		for (int i = 0; i < len ; i++) {
			hash = 31*hash + str.charAt(i);
		}
		return (hash < 0 ? hash*-1 : hash);
	}


}

