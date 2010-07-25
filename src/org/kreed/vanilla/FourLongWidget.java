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

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.RemoteViews;

/**
 * Simple 1x4 widget to show currently playing song, album art, and play/pause,
 * next, and previous buttons. Uses artwork from the default Android music
 * widget.
 */
public class FourLongWidget extends AppWidgetProvider {
	@Override
	public void onUpdate(Context context, AppWidgetManager manager, int[] ids)
	{
		Song song;
		int state;

		if (ContextApplication.hasService()) {
			PlaybackService service = ContextApplication.getService();
			song = service.getSong(0);
			state = service.getState();
		} else {
			SongTimeline timeline = new SongTimeline();
			timeline.loadState(context);
			song = timeline.getSong(0);
			state = 0;

			// If we generated a new current song (because the PlaybackService has
			// never been started), then we need to save the state.
			timeline.saveState(context, 0);
		}

		updateWidget(context, manager, ids, song, state);
	}

	/**
	 * Receive a broadcast sent by the PlaybackService and update the widget
	 * accordingly.
	 *
	 * @param intent The intent that was broadcast.
	 */
	public static void receive(Intent intent)
	{
		String action = intent.getAction();
		if (PlaybackService.EVENT_CHANGED.equals(action) || PlaybackService.EVENT_REPLACE_SONG.equals(action)) {
			Context context = ContextApplication.getContext();
			Song song = intent.getParcelableExtra("song");
			int state = intent.getIntExtra("state", -1);

			AppWidgetManager manager = AppWidgetManager.getInstance(context);
			int[] ids = manager.getAppWidgetIds(new ComponentName(context, FourLongWidget.class));
			updateWidget(context, manager, ids, song, state);
		}
	}

	/**
	 * Populate the widgets with the given ids with the given info.
	 *
	 * @param context A Context to use.
	 * @param manager The AppWidgetManager that will be used to update the
	 * widget.
	 * @param ids An array containing the ids of all the widgets to update.
	 * @param song The current Song in PlaybackService.
	 * @param state The current PlaybackService state.
	 */
	public static void updateWidget(Context context, AppWidgetManager manager, int[] ids, Song song, int state)
	{
		if (ids == null || ids.length == 0)
			return;

		Resources res = context.getResources();
		RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.four_long_widget);

		Bitmap cover = null;

		if (song == null) {
			views.setViewVisibility(R.id.title, View.GONE);
			views.setViewVisibility(R.id.next, View.GONE);
			views.setViewVisibility(R.id.play_pause, View.GONE);
			views.setTextViewText(R.id.artist, res.getText(R.string.no_songs));
		} else {
			views.setViewVisibility(R.id.title, View.VISIBLE);
			views.setViewVisibility(R.id.next, View.VISIBLE);
			views.setViewVisibility(R.id.play_pause, View.VISIBLE);
			views.setTextViewText(R.id.title, song.title);
			views.setTextViewText(R.id.artist, song.artist);
			cover = song.getCover();
		}

		if (cover == null) {
			views.setViewVisibility(R.id.cover, View.GONE);
		} else {
			views.setViewVisibility(R.id.cover, View.VISIBLE);
			views.setImageViewBitmap(R.id.cover, cover);
		}

		if (state != -1) {
			boolean playing = (state & PlaybackService.FLAG_PLAYING) != 0;
			views.setImageViewResource(R.id.play_pause, playing ? R.drawable.pause_multi : R.drawable.play_multi);
		}

		Intent intent;
		PendingIntent pendingIntent;

		ComponentName service = new ComponentName(context, PlaybackService.class);

		intent = new Intent(context, LaunchActivity.class);
		pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
		views.setOnClickPendingIntent(R.id.cover, pendingIntent);
		views.setOnClickPendingIntent(R.id.text, pendingIntent);

		intent = new Intent(PlaybackService.ACTION_TOGGLE_PLAYBACK).setComponent(service);
		pendingIntent = PendingIntent.getService(context, 0, intent, 0);
		views.setOnClickPendingIntent(R.id.play_pause, pendingIntent);

		intent = new Intent(PlaybackService.ACTION_NEXT_SONG).setComponent(service);
		pendingIntent = PendingIntent.getService(context, 0, intent, 0);
		views.setOnClickPendingIntent(R.id.next, pendingIntent);

		manager.updateAppWidget(ids, views);
	}
}
