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
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.TypedValue;
import android.widget.RemoteViews;

/**
 * Provider for the smallish one cell widget. Handles updating for current
 * PlaybackService state.
 */
public class OneCellWidget extends AppWidgetProvider {
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
			int[] ids = manager.getAppWidgetIds(new ComponentName(context, OneCellWidget.class));
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
		for (int i = 0; i != ids.length; ++i) {
			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
			boolean doubleTap = settings.getBoolean("double_tap_" + ids[i], false);

			RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.one_cell_widget);

			if (state != -1) {
				boolean playing = (state & PlaybackService.FLAG_PLAYING) != 0;
				views.setImageViewResource(R.id.play_pause, playing ? R.drawable.hidden_pause : R.drawable.hidden_play);
			}

			Intent playPause = new Intent(context, PlaybackService.class);
			playPause.setAction(doubleTap ? PlaybackService.ACTION_TOGGLE_PLAYBACK_DELAYED : PlaybackService.ACTION_TOGGLE_PLAYBACK);
			views.setOnClickPendingIntent(R.id.play_pause, PendingIntent.getService(context, 0, playPause, 0));
	
			Intent next = new Intent(context, PlaybackService.class);
			next.setAction(doubleTap ? PlaybackService.ACTION_NEXT_SONG_DELAYED : PlaybackService.ACTION_NEXT_SONG);
			views.setOnClickPendingIntent(R.id.next, PendingIntent.getService(context, 0, next, 0));

			if (song == null) {
				views.setImageViewResource(R.id.cover_view, R.drawable.icon);
			} else {
				int size = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 72, context.getResources().getDisplayMetrics());
				views.setImageViewBitmap(R.id.cover_view, CoverBitmap.createCompactBitmap(song, size, size));
			}

			manager.updateAppWidget(ids[i], views);
		}
	}
}
