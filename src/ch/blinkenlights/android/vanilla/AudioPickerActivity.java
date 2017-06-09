/*
 * Copyright (C) 2014-2017 Adrian Ulrich <adrian@blinkenlights.ch>
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

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Button;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

public class AudioPickerActivity extends PlaybackActivity {

	private Song mSong;

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
        
		Intent intent = getIntent();
		if (intent == null) {
			finish();
			return;
		}

		if (PermissionRequestActivity.requestPermissions(this, intent)) {
			finish();
			return;
		}

		Uri uri = intent.getData();
		if (uri == null) {
			finish();
			return;
		}

		mSong = getSongForUri(uri);
		if (mSong == null) {
			// unsupported intent or song not found
			finish();
			return;
		}


		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.audiopicker);

		String displayName = new File(mSong.path).getName();
		TextView filePath = (TextView)findViewById(R.id.filepath);
		filePath.setText(displayName);

		// Bind all 3 clickbuttons
		Button cancelButton = (Button)findViewById(R.id.cancel);
		cancelButton.setOnClickListener(this);
		Button enqueueButton = (Button)findViewById(R.id.enqueue);
		enqueueButton.setOnClickListener(this);
		enqueueButton.setEnabled( PlaybackService.hasInstance() ); // only active if vanilla is still running
		Button playButton = (Button)findViewById(R.id.play);
		playButton.setOnClickListener(this);

	}

	@Override
	public void onClick(View view)
	{
		int mode;
		QueryTask query;

		switch(view.getId()) {
			case R.id.play:
				mode = SongTimeline.MODE_PLAY;
				break;
			case R.id.enqueue:
				mode = SongTimeline.MODE_ENQUEUE;
				break;
			default:
				finish();
				return;
		}

		// This code is not reached unless mSong is filled and non-empty
		if (mSong.id < 0) {
			query = MediaUtils.buildFileQuery(mSong.path, Song.FILLED_PROJECTION);
		} else {
			query = MediaUtils.buildQuery(MediaUtils.TYPE_SONG, mSong.id, Song.FILLED_PROJECTION, null);
		}

		query.mode = mode;

		PlaybackService service = PlaybackService.get(this);
		service.addSongs(query);
		finish();
	}


	/**
	 * Attempts to resolve given uri to a song object
	 *
	 * @param uri The uri to resolve
	 * @return A song object, null on failure
	 */
	private Song getSongForUri(Uri uri) {
		Song song = new Song(-1);
		Cursor cursor = null;

		if (uri.getScheme().equals("content")) {
			if (uri.getHost().equals("media")) {
				cursor = getCursorForMediaContent(uri);
			} else {
				cursor = getCursorForAnyContent(uri);
			}
		}

		if (uri.getScheme().equals("file")) {
			cursor = MediaUtils.getCursorForFileQuery(uri.getPath());
		}

		if (cursor != null) {
			if (cursor.moveToNext()) {
				song.populate(cursor);
			}
			cursor.close();
		}
		return song.isFilled() ? song : null;
	}

	/**
	 * Returns the cursor for a file stored in androids media library.
	 *
	 * @param uri the uri to query - expected to be content://media/...
	 * @return cursor the cursor, may be null.
	 */
	private Cursor getCursorForMediaContent(Uri uri) {
		Cursor cursor = null;
		Cursor pathCursor = getContentResolver().query(uri, new String[]{ MediaStore.Audio.Media.DATA }, null, null, null);
		if (pathCursor != null) {
			if (pathCursor.moveToNext()) {
				String mediaPath = pathCursor.getString(0);
				if (mediaPath != null) { // this happens on android 4.x sometimes?!
					QueryTask query = MediaUtils.buildFileQuery(mediaPath, Song.FILLED_PROJECTION);
					cursor = query.runQuery(this);
				}
			}
			pathCursor.close();
		}
		return cursor;
	}

	/**
	 * Returns the cursor for any content:// uri. The contents will be stored
	 * in our application cache.
	 *
	 * @param uri the uri to query
	 * @return cursor the cursor, may be null.
	 */
	private Cursor getCursorForAnyContent(Uri uri) {
		Cursor cursor = null;
		File outFile = null;
		InputStream ins = null;
		OutputStream ous = null;

		// Cache a local copy, this should really run in a background thread, but we
		// are usually reading local files, which is fast enough.
		try {
			byte[] buffer = new byte[8192];
			ins = getContentResolver().openInputStream(uri);
			outFile = File.createTempFile("cached-download-", ".bin", getCacheDir());
			ous = new FileOutputStream(outFile);

			int len = 0;
			while ((len = ins.read(buffer)) != -1) {
				ous.write(buffer, 0, len);
			}
			outFile.deleteOnExit();
		} catch (IOException e) {
			if (outFile != null)
				outFile.delete();
			outFile = null; // signals failure.
		} finally {
			try { if (ins != null) ins.close(); } catch(IOException e) {}
			try { if (ous != null) ous.close(); } catch(IOException e) {}
		}

		if (outFile != null)
			cursor = MediaUtils.getCursorForFileQuery(outFile.getPath());
		return cursor;
	}
}
