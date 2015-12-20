/*
 * Copyright (C) 2015 Adrian Ulrich <adrian@blinkenlights.ch>
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

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;



public class RemoteControlImplLp implements RemoteControl.Client {
	/**
	 * Context of this instance
	 */
	private Context mContext;
	/**
	 * Objects MediaSession handle
	 */
	private MediaSession mMediaSession;
	/**
	 * Whether the cover should be shown. 1 for yes, 0 for no, -1 for
	 * uninitialized.
	 */
	private int mShowCover = -1;

	/**
	 * Creates a new instance
	 *
	 * @param context The context to use
	 */
	public RemoteControlImplLp(Context context) {
		mContext = context;
	}

	/**
	 * Registers a new MediaSession on the device
	 *
	 * @param am The AudioManager service. (unused)
	 */
	public void registerRemote(AudioManager am) {
		mMediaSession = new MediaSession(mContext, "VanillaMusic");
		mMediaSession.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
		mMediaSession.setActive(true);
	}

	/**
	 * Unregisters a registered media session
	 */
	public void unregisterRemote() {
		mMediaSession.setActive(false);
		mMediaSession.release();
	}

	/**
	 * Uninitializes our cached preferences, forcing a reload
	 */
	public void reloadPreference() {
		mShowCover = -1;
	}

	/**
	 * Update the remote with new metadata.
	 * {@link #registerRemote(AudioManager)} must have been called
	 * first.
	 *
	 * @param song The song containing the new metadata.
	 * @param state PlaybackService state, used to determine playback state.
	 * @param keepPaused whether or not to keep the remote updated in paused mode
	 */
	public void updateRemote(Song song, int state, boolean keepPaused) {
		MediaSession session = mMediaSession;
		if (session == null)
			return;

		boolean isPlaying = ((state & PlaybackService.FLAG_PLAYING) != 0);

		if (mShowCover == -1) {
			SharedPreferences settings = PlaybackService.getSettings(mContext);
			mShowCover = settings.getBoolean(PrefKeys.COVER_ON_LOCKSCREEN, PrefDefaults.COVER_ON_LOCKSCREEN) ? 1 : 0;
		}

		if (song != null) {
			Bitmap bitmap = null;
			if (mShowCover == 1 && (isPlaying || keepPaused)) {
				bitmap = song.getCover(mContext);
				if (bitmap != null)
					bitmap.copy(Bitmap.Config.ARGB_8888, false);
			}

			session.setMetadata(new MediaMetadata.Builder()
				.putString(MediaMetadata.METADATA_KEY_ARTIST, song.artist)
				.putString(MediaMetadata.METADATA_KEY_ALBUM, song.album)
				.putString(MediaMetadata.METADATA_KEY_TITLE, song.title)
				.putLong(MediaMetadata.METADATA_KEY_DURATION, song.duration)
				.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
			.build());
		}

		int playbackState = (isPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED);
		session.setPlaybackState(new PlaybackState.Builder()
			.setState(playbackState, PlaybackState.PLAYBACK_POSITION_UNKNOWN , 1.0f).build());
	}
}
