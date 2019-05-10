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
import ch.blinkenlights.android.vanilla.Song;
import ch.blinkenlights.android.vanilla.VanillaMediaPlayer;

public class PlaybackTimestampHandler {

	private static final int mDelayUpdate = 2000;
	private static String TAG = "PlaybackUpdateHandler";

	private final Handler mHandler = new Handler();
	private Context mContext;
	private Song mSong;

	private boolean mAlreadyUpdating =false;
	private int mTimestamp;


	public PlaybackTimestampHandler(Context c) {
		this.mContext = c;
	}

	public void stopUpdates(){
		Log.d(TAG, "Called on main thread stopUpdates");
		mHandler.removeCallbacksAndMessages(null);
		mAlreadyUpdating =false;
	}

	public void start(VanillaMediaPlayer mediaPlayer) {
		if(mAlreadyUpdating){
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
					//Log.d(TAG, "Timestamp: "+vmp.getCurrentPosition()/1000+" - "+mSong.title+" - "+mSong.id);
					mTimestamp = vmp.getCurrentPosition();

					//This fixes the timestamp beeing set to 0 if the player has not been initialized
					if(vmp.isPlaying ()){
						storeTimestampdataForSong();
					}
				} finally {
					//if mediaplayer is not playing, wait longer.
					if(vmp.isPlaying()){
						mHandler.postDelayed(this, mDelayUpdate);
					}else{
						//Stop Handler if no music is playing.
						//Log.e(TAG, "Mediaplayer not playing, stop updating!");
						mAlreadyUpdating =false;
					}
				}
			}
		};
		// Start the initial runnable task by posting through the handler
		mAlreadyUpdating =true;
		mHandler.post(runnableCode);
		//Log.d(TAG, "Mediaplayer started updating");
	}

	public void setSong(Song song) {
		mSong=song;
	}

	public int getInitialTimestamp() {
		if(mSong==null){
			return 0;
		}
		return MediaLibrary.getSongTimestamp(mContext, mSong.id);
	}


	private void storeTimestampdataForSong() {
		if(mSong == null || mSong.id == 0 || mSong.albumId == 0 || mContext == null){
			return;
		}
		MediaLibrary.updateSongTimestamp(mContext, mSong.id, mTimestamp);
		MediaLibrary.updateAlbumLastSong(mContext, mSong.id, mSong.albumId);
	}

	public void updateToZero() {
		MediaLibrary.updateSongTimestamp(mContext, mSong.id, 0);
		MediaLibrary.updateAlbumLastSong(mContext, mSong.id, mSong.albumId);
	}
}
