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

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.RemoteViews;

/**
 * Simple 1x4 widget to show currently playing song, album art, and play/pause,
 * next, and previous buttons. Uses artwork from the default Android music
 * widget.
 */
public class FourSquareWidget extends AppWidgetProvider {
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
			Song song;
			if (intent.hasExtra("song"))
				song = intent.getParcelableExtra("song");
			else
				song = ContextApplication.getService().getSong(0);
			int state = intent.getIntExtra("state", -1);

			AppWidgetManager manager = AppWidgetManager.getInstance(context);
			int[] ids = manager.getAppWidgetIds(new ComponentName(context, FourSquareWidget.class));
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

		RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.four_square_widget);

		if (song == null) {
			views.setViewVisibility(R.id.buttons, View.GONE);
			views.setViewVisibility(R.id.title, View.GONE);
			views.setInt(R.id.artist, "setText", R.string.no_songs);
			views.setImageViewResource(R.id.cover, 0);
		} else {
			views.setViewVisibility(R.id.title, View.VISIBLE);
			views.setViewVisibility(R.id.buttons, View.VISIBLE);
			views.setTextViewText(R.id.title, song.title);
			views.setTextViewText(R.id.artist, song.artist);
			views.setImageViewUri(R.id.cover, song.getCoverUri());
		}

		if (state != -1) {
			boolean playing = (state & PlaybackService.FLAG_PLAYING) != 0;
			views.setImageViewResource(R.id.play_pause, playing ? R.drawable.hidden_pause : R.drawable.hidden_play);
		}

		Intent intent;
		PendingIntent pendingIntent;

		ComponentName service = new ComponentName(context, PlaybackService.class);

		intent = new Intent(context, LaunchActivity.class);
		pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
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
