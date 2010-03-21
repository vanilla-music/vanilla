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
import android.util.TypedValue;
import android.widget.RemoteViews;

public class OneCellWidget extends AppWidgetProvider {
	@Override
	public void onUpdate(Context context, AppWidgetManager manager, int[] ids)
	{
		PlaybackServiceState state = new PlaybackServiceState();
		Song song = null;
		if (state.load(context)) {
			song = new Song(state.savedIds[state.savedIndex]);
			if (!song.populate())
				song = null;
		}

		RemoteViews views = createViews(context, song, false, true);
		manager.updateAppWidget(ids, views);
	}

	@Override
	public void onReceive(Context context, Intent intent)
	{
		if (PlaybackService.EVENT_CHANGED.equals(intent.getAction())) {
			Song song = intent.getParcelableExtra("song");
			boolean playing = intent.getIntExtra("newState", 0) == PlaybackService.STATE_PLAYING;

			ComponentName widget = new ComponentName(context, OneCellWidget.class);
			RemoteViews views = createViews(context, song, playing, false);

			AppWidgetManager.getInstance(context).updateAppWidget(widget, views);
		} else {
			super.onReceive(context, intent);
		}
	}

	public static RemoteViews createViews(Context context, Song song, boolean playing, boolean fromState)
	{
		RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.one_cell_widget);

		views.setImageViewResource(R.id.play_pause, playing ? R.drawable.hidden_pause : R.drawable.hidden_play);
		Intent playPause = new Intent(context, PlaybackService.class);
		playPause.setAction(PlaybackService.TOGGLE_PLAYBACK);
		views.setOnClickPendingIntent(R.id.play_pause, PendingIntent.getService(context, 0, playPause, 0));
		Intent next = new Intent(context, PlaybackService.class);
		next.setAction(PlaybackService.NEXT_SONG);
		views.setOnClickPendingIntent(R.id.next, PendingIntent.getService(context, 0, next, 0));

		if (song == null) {
			if (fromState)
				views.setImageViewResource(R.id.cover_view, R.drawable.icon);
		} else {
			int size = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 72, context.getResources().getDisplayMetrics());
			views.setImageViewBitmap(R.id.cover_view, CoverView.createMiniBitmap(song, size, size));
		}

		return views;
	}
}