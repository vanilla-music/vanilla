/*
 * Copyright (C) 2015 Adrian Ulrich <adrian@blinkenlights.ch>
 * Copyright (C) 2012 Christopher Eby <kreed@kreed.org>
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

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;

public class RemoteControlImplICS implements RemoteControl.Client {
	/**
	 * Context of this instance
	 */
	private final Context mContext;
	/**
	 * Used with updateRemote method.
	 */
	private RemoteControlClient mRemote;
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
	public RemoteControlImplICS(Context context) {
		mContext = context;
	}

	/**
	 * Perform initialization required for RemoteControlClient.
	 */
	public void initializeRemote() {
		// make sure there is only one registered remote
		unregisterRemote();
		if (MediaButtonReceiver.useHeadsetControls(mContext) == false)
			return;

		// Receive 'background' play button events
		AudioManager audioManager = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
		ComponentName receiver = new ComponentName(mContext.getPackageName(), MediaButtonReceiver.class.getName());
		audioManager.registerMediaButtonEventReceiver(receiver);

		Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
		mediaButtonIntent.setComponent(new ComponentName(mContext.getPackageName(), MediaButtonReceiver.class.getName()));
		PendingIntent mediaPendingIntent = PendingIntent.getBroadcast(mContext, 0, mediaButtonIntent, 0);
		RemoteControlClient remote = new RemoteControlClient(mediaPendingIntent);

		// Things we can do (eg: buttons to display on lock screen)
		int flags = RemoteControlClient.FLAG_KEY_MEDIA_NEXT
			| RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS
			| RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE
			| RemoteControlClient.FLAG_KEY_MEDIA_PLAY
			| RemoteControlClient.FLAG_KEY_MEDIA_PAUSE;
		remote.setTransportControlFlags(flags);

		audioManager.registerRemoteControlClient(remote);
		mRemote = remote;
	}

	/**
	 * Unregisters a remote control client
	 */
	public void unregisterRemote() {
		if (mRemote != null) {
			AudioManager audioManager = (AudioManager)mContext.getSystemService(Context.AUDIO_SERVICE);
			ComponentName receiver = new ComponentName(mContext.getPackageName(), MediaButtonReceiver.class.getName());
			audioManager.unregisterMediaButtonEventReceiver(receiver);
			audioManager.unregisterRemoteControlClient(mRemote);
			mRemote = null;
		}
	}

	/**
	 * Uninitializes our cached preferences, forcing a reload
	 */
	public void reloadPreference()
	{
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
	public void updateRemote(Song song, int state, boolean keepPaused)
	{
		RemoteControlClient remote = mRemote;
		if (remote == null)
			return;

		boolean isPlaying = ((state & PlaybackService.FLAG_PLAYING) != 0);

		if (mShowCover == -1) {
			SharedPreferences settings = PlaybackService.getSettings(mContext);
			mShowCover = settings.getBoolean(PrefKeys.COVER_ON_LOCKSCREEN, PrefDefaults.COVER_ON_LOCKSCREEN) ? 1 : 0;
		}

		remote.setPlaybackState(isPlaying ? RemoteControlClient.PLAYSTATE_PLAYING : RemoteControlClient.PLAYSTATE_PAUSED);
		RemoteControlClient.MetadataEditor editor = remote.editMetadata(true);
		if (song != null && song.artist != null && song.album != null) {
			String artist_album = song.artist + " - " + song.album;
			artist_album = (song.artist.length() == 0 ? song.album : artist_album); // no artist ? -> only display album
			artist_album = (song.album.length() == 0 ? song.artist : artist_album); // no album ? -> only display artist

			editor.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, artist_album);
			editor.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, song.title);
			Bitmap bitmap = song.getCover(mContext);
			if (bitmap != null  && mShowCover == 1 && (isPlaying || keepPaused)) {
				// Create a copy of the cover art, since RemoteControlClient likes
				// to recycle what we give it.
				bitmap = bitmap.copy(Bitmap.Config.RGB_565, false);
			} else {
				// Some lockscreen implementations fail to clear the cover artwork
				// if we send a null bitmap. We are creating a 16x16 transparent
				// bitmap to work around this limitation.
				bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888);
			}
			editor.putBitmap(RemoteControlClient.MetadataEditor.BITMAP_KEY_ARTWORK, bitmap);
		}
		editor.apply();
	}
}
