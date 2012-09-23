/*
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

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;

/**
 * Framework methods only in ICS or above go here.
 */
@TargetApi(14)
public class CompatIcs {
	/**
	 * Used with updateRemote method.
	 */
	private static RemoteControlClient sRemote;

	/**
	 * Perform initialization required for RemoteControlClient.
	 *
	 * @param context A context to use.
	 * @param am The AudioManager service.
	 */
	public static void registerRemote(Context context, AudioManager am)
	{
		if (!MediaButtonReceiver.useHeadsetControls(context)) {
			// RemoteControlClient requires MEDIA_BUTTON intent
			return;
		}

		MediaButtonReceiver.registerMediaButton(context);

		Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
		mediaButtonIntent.setComponent(new ComponentName(context.getPackageName(), MediaButtonReceiver.class.getName()));
		PendingIntent mediaPendingIntent = PendingIntent.getBroadcast(context, 0, mediaButtonIntent, 0);
		RemoteControlClient remote = new RemoteControlClient(mediaPendingIntent);
		int flags = RemoteControlClient.FLAG_KEY_MEDIA_NEXT
			| RemoteControlClient.FLAG_KEY_MEDIA_PREVIOUS
			| RemoteControlClient.FLAG_KEY_MEDIA_PLAY_PAUSE
			| RemoteControlClient.FLAG_KEY_MEDIA_PLAY
			| RemoteControlClient.FLAG_KEY_MEDIA_PAUSE;
		remote.setTransportControlFlags(flags);
		am.registerRemoteControlClient(remote);
		sRemote = remote;
	}

	/**
	 * Update the remote with new metadata.
	 * {@link #registerRemote(Context, AudioManager)} must have been called
	 * first.
	 *
	 * @param context A context to use.
	 * @param song The song containing the new metadata.
	 * @param state PlaybackService state, used to determine playback state.
	 */
	public static void updateRemote(Context context, Song song, int state)
	{
		RemoteControlClient remote = sRemote;
		if (remote == null)
			return;

		remote.setPlaybackState((state & PlaybackService.FLAG_PLAYING) != 0 ? RemoteControlClient.PLAYSTATE_PLAYING : RemoteControlClient.PLAYSTATE_PAUSED);
		RemoteControlClient.MetadataEditor editor = remote.editMetadata(true);
		if (song != null) {
			editor.putString(MediaMetadataRetriever.METADATA_KEY_ARTIST, song.artist);
			editor.putString(MediaMetadataRetriever.METADATA_KEY_ALBUM, song.album);
			editor.putString(MediaMetadataRetriever.METADATA_KEY_TITLE, song.title);
			Bitmap bitmap = song.getCover(context);
			if (bitmap != null) {
				// Create a copy of the cover art, since RemoteControlClient likes
				// to recycle what we give it.
				bitmap = bitmap.copy(Bitmap.Config.RGB_565, false);
			}
			editor.putBitmap(RemoteControlClient.MetadataEditor.BITMAP_KEY_ARTWORK, bitmap);
		}
		editor.apply();
	}
}
