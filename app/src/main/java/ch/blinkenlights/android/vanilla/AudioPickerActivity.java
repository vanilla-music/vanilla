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
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ProgressBar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;

public class AudioPickerActivity extends PlaybackActivity {
	/**
	 * The cancel button
	 */
	private Button mCancelButton;
	/**
	 * The enqueue button
	 */
	private Button mEnqueueButton;
	/**
	 * The play button
	 */
	private Button mPlayButton;
	/**
	 * The general purpose text view
	 */
	private TextView mTextView;
	/**
	 * Our endless progress bar
	 */
	private ProgressBar mProgressBar;
	/**
	 * Song we found, or failed to find
	 */
	private Song mSong;
	/**
	 * Our async worker task to search for mSong
	 */
	private AudioPickerWorker mWorker;


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

		// Basic sanity test done: Create worker
		// and setup window.
		mWorker = new AudioPickerWorker();

		// Basic sanity test done: Setup window
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.audiopicker);

		// ...and resolve + bind all elements
		mCancelButton = (Button)findViewById(R.id.cancel);
		mCancelButton.setEnabled(true);
		mCancelButton.setOnClickListener(this);

		mEnqueueButton = (Button)findViewById(R.id.enqueue);
		mEnqueueButton.setOnClickListener(this);

		mPlayButton = (Button)findViewById(R.id.play);
		mPlayButton.setOnClickListener(this);

		mTextView = (TextView)findViewById(R.id.filepath);
		mProgressBar = (ProgressBar)findViewById(R.id.progress);

		// UI is ready, we can now execute the actual task.
		mWorker.execute(uri);
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
				mWorker.cancel(false);
				finish();
				return;
		}

		// This code is not reached unless mSong is filled and non-empty
		if (mSong.id < 0) {
			query = MediaUtils.buildFileQuery(mSong.path, Song.FILLED_PROJECTION, false /* recursive */);
		} else {
			query = MediaUtils.buildQuery(MediaUtils.TYPE_SONG, mSong.id, Song.FILLED_PROJECTION, null);
		}

		query.mode = mode;

		PlaybackService service = PlaybackService.get(this);
		service.addSongs(query);
		finish();
	}

	/**
	 * Called after AudioPickerWorker finished.
	 * This inspects the result and sets up the view
	 * if we got a song, cancels the activity otherwise.
	 *
	 * @param song the song we found, may be null
	 */
	private void onSongResolved(Song song) {
		mSong = song;

		if (song == null) {
			finish();
			return;
		}

		// Enable enqueue button if playback service is already
		// active (= we are most likely playing a song)
		if (PlaybackService.hasInstance())
			mEnqueueButton.setEnabled(true);

		mPlayButton.setEnabled(true);

		// Set the title to display, we use the filename as a fallback
		// if the title is empty for whatever reason.
		String displayName = song.title;
		if ("".equals(song.title))
			displayName = new File(song.path).getName();

		mTextView.setText(song.title);
		mTextView.setVisibility(View.VISIBLE);
		mProgressBar.setVisibility(View.GONE);
	}

	/**
	 * Background worker to resolve a song from an Uri.
	 * Will call onSongResolved(Song) on completion.
	 */
	private class AudioPickerWorker extends AsyncTask<Uri, Void, Song> {
		@Override
		protected Song doInBackground(Uri... uri) {
			return getSongForUri(uri[0]);
		}
		@Override
		protected void onPostExecute(Song song) {
			onSongResolved(song);
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
						QueryTask query = MediaUtils.buildFileQuery(mediaPath, Song.FILLED_PROJECTION, false /* recursive */);
						cursor = query.runQuery(getApplicationContext());
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
					if (isCancelled()) {
						throw new IOException("Canceled");
					}
				}
				outFile.deleteOnExit();
			} catch (IOException e) {
				if (outFile != null) {
					outFile.delete();
				}
				outFile = null; // signals failure.
			} finally {
				try { if (ins != null) ins.close(); } catch(IOException e) {}
				try { if (ous != null) ous.close(); } catch(IOException e) {}
			}

			if (outFile != null) {
				cursor = MediaUtils.getCursorForFileQuery(outFile.getPath());
			}
			return cursor;
		}
	}


}
