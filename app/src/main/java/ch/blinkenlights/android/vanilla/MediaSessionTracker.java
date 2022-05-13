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

import android.content.Context;
import android.graphics.Bitmap;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.view.KeyEvent;

/**
 * Manages our active media session which is responsible for keeping
 * the notification seek bar up to date and handling key events.
 */
public class MediaSessionTracker {
	/**
	 * Context we are working with.
	 */
	private Context mContext;
	/**
	 * Our generic MediaSession
	 */
	private MediaSessionCompat mMediaSession;


	MediaSessionTracker(Context context) {
		mContext = context;
		mMediaSession = new MediaSessionCompat(mContext, "Vanilla Music Media Session");
		mMediaSession.setCallback(new MediaSessionCompat.Callback() {
			@Override
			public void onPause() {
				MediaButtonReceiver.processKey(mContext, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK));
			}
			@Override
			public void onPlay() {
				MediaButtonReceiver.processKey(mContext, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK));
			}
			@Override
			public void onSkipToNext() {
				MediaButtonReceiver.processKey(mContext, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT));
			}
			@Override
			public void onSkipToPrevious() {
				MediaButtonReceiver.processKey(mContext, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS));
			}
			@Override
			public void onStop() {
				// We will behave the same as Google Play Music: for "Stop" we unconditionally Pause instead
				MediaButtonReceiver.processKey(mContext, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE));
			}
			@Override
			public void onSeekTo(long pos) {
				PlaybackService.get(mContext).seekToPosition((int)pos);
			}});
		mMediaSession.setActive(true);
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
		mMediaSession.setActive(false);
		mMediaSession.release();
	}

	/**
	 * Populates the active media session with new info.
	 *
	 * @param song The song containing the new metadata.
	 * @param state PlaybackService state, used to determine playback state.
	 */
	public void updateSession(Song song, int state) {
		final boolean playing = (state & PlaybackService.FLAG_PLAYING) != 0;
		final PlaybackService service = PlaybackService.get(mContext);

		PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder()
			.setState(playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
					  service.getPosition(), 1.0f)
			.setActions(PlaybackStateCompat.ACTION_PLAY |
						PlaybackStateCompat.ACTION_STOP |
						PlaybackStateCompat.ACTION_PAUSE |
						PlaybackStateCompat.ACTION_PLAY_PAUSE |
						PlaybackStateCompat.ACTION_SEEK_TO |
						PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
						PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
			.build();

		if (song != null) {
			final Bitmap cover = song.getMediumCover(mContext);
			MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder()
				.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, song.albumArtist)
				.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
				.putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
				.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration);

			boolean showCover = SharedPrefHelper.getSettings(mContext).getBoolean(PrefKeys.COVER_ON_LOCKSCREEN, PrefDefaults.COVER_ON_LOCKSCREEN);
			if (showCover) {
				metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, cover);
			}

			// logic copied from FullPlaybackActivity.updateQueuePosition()
			if (PlaybackService.finishAction(service.getState()) != SongTimeline.FINISH_RANDOM) {
				metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, service.getTimelinePosition() + 1);
				metadataBuilder.putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, service.getTimelineLength());
			}
			mMediaSession.setMetadata(metadataBuilder.build());
		}

		mMediaSession.setPlaybackState(playbackState);
		mMediaSession.setActive(true);
	}
}
