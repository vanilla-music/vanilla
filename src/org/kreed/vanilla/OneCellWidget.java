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
			Song song;
			if (intent.hasExtra("song"))
				song = intent.getParcelableExtra("song");
			else
				song = ContextApplication.getService().getSong(0);
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
		if (ids == null || ids.length == 0)
			return;

		for (int i = ids.length; --i != -1; ) {
			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
			boolean doubleTap = settings.getBoolean("double_tap_" + ids[i], false);

			RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.one_cell_widget);

			if (state != -1) {
				boolean playing = (state & PlaybackService.FLAG_PLAYING) != 0;
				views.setImageViewResource(R.id.play_pause, playing ? R.drawable.hidden_pause : R.drawable.hidden_play);
			}

			ComponentName service = new ComponentName(context, PlaybackService.class);

			Intent playPause = new Intent(doubleTap ? PlaybackService.ACTION_TOGGLE_PLAYBACK_DELAYED : PlaybackService.ACTION_TOGGLE_PLAYBACK);
			playPause.setComponent(service);
			views.setOnClickPendingIntent(R.id.play_pause, PendingIntent.getService(context, 0, playPause, 0));
	
			Intent next = new Intent(doubleTap ? PlaybackService.ACTION_NEXT_SONG_DELAYED : PlaybackService.ACTION_NEXT_SONG);
			next.setComponent(service);
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
