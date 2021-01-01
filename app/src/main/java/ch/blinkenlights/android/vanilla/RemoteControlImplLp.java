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

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.view.KeyEvent;

@TargetApi(21)
public class RemoteControlImplLp implements RemoteControl.Client {
	/**
	 * Context of this instance
	 */
	private final Context mContext;
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
	 */
	public void initializeRemote() {
		// make sure there is only one registered remote
		unregisterRemote();
		if (MediaButtonReceiver.useHeadsetControls(mContext) == false)
			return;

		mMediaSession = new MediaSession(mContext, "Vanilla Music");

		mMediaSession.setCallback(new MediaSession.Callback() {
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
		});

		Intent intent = new Intent();
		intent.setComponent(new ComponentName(mContext.getPackageName(), MediaButtonReceiver.class.getName()));
		PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0);
		// This Seems to overwrite our MEDIA_BUTTON intent filter and there seems to be no way to unregister it
		// Well: We intent to keep this around as long as possible anyway. But WHY ANDROID?!
		mMediaSession.setMediaButtonReceiver(pendingIntent);
		mMediaSession.setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS | MediaSession.FLAG_HANDLES_MEDIA_BUTTONS);
	}

	/**
	 * Unregisters a registered media session
	 */
	public void unregisterRemote() {
		if (mMediaSession != null) {
			mMediaSession.setActive(false);
			mMediaSession.release();
			mMediaSession = null;
		}
	}

	/**
	 * Uninitializes our cached preferences, forcing a reload
	 */
	public void reloadPreference() {
		mShowCover = -1;
	}

	/**
	 * Update the remote with new metadata.
	 * {@link #initializeRemote()} must have been called
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
			SharedPreferences settings = SharedPrefHelper.getSettings(mContext);
			mShowCover = settings.getBoolean(PrefKeys.COVER_ON_LOCKSCREEN, PrefDefaults.COVER_ON_LOCKSCREEN) ? 1 : 0;
		}

		PlaybackService service = PlaybackService.get(mContext);

		if (song != null) {
			Bitmap bitmap = null;
			if (mShowCover == 1 && (isPlaying || keepPaused)) {
				bitmap = song.getCover(mContext);
			}

			MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder()
				.putString(MediaMetadata.METADATA_KEY_ARTIST, song.artist)
				.putString(MediaMetadata.METADATA_KEY_ALBUM, song.album)
				.putString(MediaMetadata.METADATA_KEY_TITLE, song.title)
				.putLong(MediaMetadata.METADATA_KEY_DURATION, song.duration)
				.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap);

			// logic copied from FullPlaybackActivity.updateQueuePosition()
			if (PlaybackService.finishAction(service.getState()) != SongTimeline.FINISH_RANDOM) {
				metadataBuilder.putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, service.getTimelinePosition() + 1);
				metadataBuilder.putLong(MediaMetadata.METADATA_KEY_NUM_TRACKS, service.getTimelineLength());
			}

			session.setMetadata(metadataBuilder.build());
		}

		int playbackState = (isPlaying ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED);

		session.setPlaybackState(new PlaybackState.Builder()
			.setState(playbackState, service.getPosition(), 1.0f)
			.setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_STOP | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE |
				PlaybackState.ACTION_SKIP_TO_NEXT | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
			.build());
		mMediaSession.setActive(true);
	}
}
