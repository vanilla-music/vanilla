/*
 * Copyright (C) 2010 Christopher Eby <kreed@kreed.org>
 *
 * This file is part of Vanilla Music Player.
 *
 * Vanilla Music Player is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Vanilla Music Player is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.kreed.vanilla;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

		RemoteViews views = new RemoteViews(ContextApplication.getContext().getPackageName(), R.layout.notification);
		views.setImageViewResource(R.id.icon, statusIcon);
		views.setTextViewText(R.id.title, song.title);
		views.setTextViewText(R.id.artist, song.artist);

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
