/*
 * Copyright (C) 2010 Christopher Eby <kreed@kreed.org>
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

package org.kreed.vanilla;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.RemoteViews;

/**
 * Notification subclass that provides information about the current song.
 *
 * To the left of the view is the application icon. If playback is paused a
 * the application with a paused icon overlayed is displayed instead.
 *
 * To the right are two lines of text. The upper line is the song title; the
 * lower line is the song artist.
 */
public class SongNotification extends Notification {
	/**
	 * Notification click action: open LaunchActivity.
	 */
	private static final int ACTION_MAIN_ACTIVITY = 0;
	/**
	 * Notification click action: open MiniPlaybackActivity.
	 */
	private static final int ACTION_MINI_ACTIVITY = 1;
	/**
	 * Notification click action: skip to next song.
	 */
	private static final int ACTION_NEXT_SONG = 2;

	/**
	 * Create a SongNotification. Call through the NotificationManager to
	 * display it.
	 *
	 * @param song The Song to display information about.
	 * @param playing True if music is playing.
	 */
	public SongNotification(Song song, boolean playing)
	{
		Context context = ContextApplication.getContext();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
		int action = Integer.parseInt(prefs.getString("notification_action", "0"));
		int statusIcon = playing ? R.drawable.status_icon : R.drawable.status_icon_paused;

		RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.notification);
		views.setImageViewResource(R.id.icon, statusIcon);
		views.setTextViewText(R.id.title, song.title);
		views.setTextViewText(R.id.artist, song.artist);

		if (prefs.getBoolean("notification_inverted_color", false)) {
			TypedArray array = context.getTheme().obtainStyledAttributes(new int[] { android.R.attr.textColorPrimary });
			int color = array.getColor(0, 0xFF00FF);
			array.recycle();
			views.setTextColor(R.id.title, color);
			views.setTextColor(R.id.artist, color);
		}

		contentView = views;
		icon = statusIcon;
		flags |= Notification.FLAG_ONGOING_EVENT;

		Intent intent;
		switch (action) {
		case ACTION_NEXT_SONG:
			intent = new Intent(context, PlaybackService.class);
			intent.setAction(PlaybackService.ACTION_NEXT_SONG_AUTOPLAY);
			contentIntent = PendingIntent.getService(context, 0, intent, 0);
			break;
		case ACTION_MINI_ACTIVITY:
			intent = new Intent(context, MiniPlaybackActivity.class);
			contentIntent = PendingIntent.getActivity(context, 0, intent, 0);
			break;
		default:
			Log.w("VanillaMusic", "Unknown value for notification_action: " + action + ". Resetting to 0.");
			SharedPreferences.Editor editor = prefs.edit();
			editor.putString("notification_action", "0");
			editor.commit();
			// fall through
		case ACTION_MAIN_ACTIVITY:
			intent = new Intent(context, LaunchActivity.class);
			contentIntent = PendingIntent.getActivity(context, 0, intent, 0);
			break;
		}
	}
}
