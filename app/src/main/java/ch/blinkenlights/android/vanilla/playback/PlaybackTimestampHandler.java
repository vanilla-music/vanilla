/*
 * Copyright (C) 2019 Felix Nüsse <felix.nuesse@t-online.de>
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

package ch.blinkenlights.android.vanilla.playback;

import android.content.Context;
import android.os.Handler;
import android.util.Log;

import ch.blinkenlights.android.medialibrary.MediaLibrary;
import ch.blinkenlights.android.medialibrary.MediaLibraryBackend;
import ch.blinkenlights.android.vanilla.Song;
import ch.blinkenlights.android.vanilla.VanillaMediaPlayer;

public class PlaybackTimestampHandler {

	final Handler mHandler = new Handler();

	private MediaLibraryBackend mlb;
	private Context c;

	private boolean alreadyUpdating=false;
	private Song mSong;
	private int mTimestamp;

	private static String TAG = "PlaybackUpdateHandler";

	public PlaybackTimestampHandler(Context c) {
		this.c = c;
	}

	public void stopUpdates(){
		Log.d(TAG, "Called on main thread stopUpdates");
		mHandler.removeCallbacksAndMessages(null);
		alreadyUpdating=false;
	}

	public void start(VanillaMediaPlayer mediaPlayer) {

		if(!mediaPlayer.isPlaying()){
			//Log.d(TAG, "Mediaplayer not playing!");
			//return;
		}

		if(alreadyUpdating){
			//Log.d(TAG, "Mediaplayer already updating!");
			return;
		}

		final VanillaMediaPlayer vmp = mediaPlayer;
		// Create the Handler object (on the main thread by default)

		// Define the code block to be executed
		Runnable runnableCode = new Runnable() {
			@Override
			public void run() {
				try {
					//Log.d(TAG, "Timestamp: "+vmp.getCurrentPosition()+" - "+mSong.title+" - "+mSong.id);
					mTimestamp = vmp.getCurrentPosition();
					storeTimestampdataForSong();
				} finally {
					//if mediaplayer is not playing, wait longer.
					int delay=500;
					if(!vmp.isPlaying()){
						delay=5000;
						//Log.d(TAG, "Long Delay: "+delay);
					}
					mHandler.postDelayed(this, delay);
				}
			}
		};
		// Start the initial runnable task by posting through the handler
		alreadyUpdating=true;
		mHandler.post(runnableCode);
		//Log.d(TAG, "Mediaplayer started updating");
	}

	public void setSong(Song song) {
		mSong=song;
	}

	public int getInitialTimestamp() {
		return MediaLibrary.getSongTimestamp(c, mSong.id);
	}


	private void storeTimestampdataForSong() {
		MediaLibrary.updateSongTimestamp(c, mSong.id, mTimestamp);
		MediaLibrary.updateAlbumLastSong(c, mSong.id, mSong.albumId);
	}


}
