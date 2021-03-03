/*
 * Copyright (C) 2021 Adrian Ulrich <adrian@blinkenlights.ch>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package ch.blinkenlights.android.vanilla;

import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.media.MediaMetadataCompat;


// Helper class used to show the notification seekbar.
public class MediaSessionTracker {
	/**
	 * Instance of Vanillas PlaybackService
	 */
	private PlaybackService mPlaybackService;
	/**
	 * Our generic MediaSession
	 */
	private MediaSessionCompat mMediaSession;


	MediaSessionTracker(PlaybackService service) {
		mPlaybackService = service;

		mMediaSession = new MediaSessionCompat(service, "Vanilla Music Media Session");
		mMediaSession.setCallback(new MediaSessionCompat.Callback() {
				@Override
				public void onSeekTo(long pos) {
					mPlaybackService.seekToPosition((int)pos);
				}
			});
	}

	/**
	 * Returns the session token of the media session.
	 */
	public MediaSessionCompat.Token getSessionToken() {
		return mMediaSession.getSessionToken();
	}

	/**
	 * Cleans up the underlying media session
	 */
	public void release() {
		mMediaSession.release();
	}

	/**
	 * Populates the active media session with new info.
	 */
	public void updateSession(Song song, int state) {
		boolean playing = (state & PlaybackService.FLAG_PLAYING) != 0;

		PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
			.setActions(PlaybackStateCompat.ACTION_SEEK_TO)
			.setState(playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
					  mPlaybackService.getPosition(), 1.0f)
			.build();
		mMediaSession.setPlaybackState(playbackState);
		if (song != null) {
			mMediaSession.setMetadata(new MediaMetadataCompat.Builder()
									  .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)
									  .build());
		}
	}
}
