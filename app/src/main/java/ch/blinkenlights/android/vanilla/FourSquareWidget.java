/*
 * Copyright (C) 2010, 2011 Christopher Eby <kreed@kreed.org>
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
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.RemoteViews;

/**
 * 2x2 widget that shows title, artist, a (hidden) play/pause button, a (hidden)
 * next button, and cover art in the background.
 */
public class FourSquareWidget extends AppWidgetProvider {
	private static boolean sEnabled;

	@Override
	public void onEnabled(Context context)
	{
		sEnabled = true;
	}

	@Override
	public void onDisabled(Context context)
	{
		sEnabled = false;
	}

	@Override
	public void onUpdate(Context context, AppWidgetManager manager, int[] ids)
	{
		Song song = null;
		int state = 0;

		if (PlaybackService.hasInstance()) {
			PlaybackService service = PlaybackService.get(context);
			song = service.getSong(0);
			state = service.getState();
		}

		sEnabled = true;
		updateWidget(context, manager, song, state);
	}

	/**
	 * Check if there are any instances of this widget placed.
	 */
	public static void checkEnabled(Context context, AppWidgetManager manager)
	{
		sEnabled = manager.getAppWidgetIds(new ComponentName(context, FourSquareWidget.class)).length != 0;
	}

	/**
	 * Populate the widgets with the given ids with the given info.
	 *
	 * @param context A Context to use.
	 * @param manager The AppWidgetManager that will be used to update the
	 * widget.
	 * @param song The current Song in PlaybackService.
	 * @param state The current PlaybackService state.
	 */
	public static void updateWidget(Context context, AppWidgetManager manager, Song song, int state)
	{
		if (!sEnabled)
			return;

		RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.four_square_widget);

		boolean playing = (state & PlaybackService.FLAG_PLAYING) != 0;
		int playResource = R.drawable.play;
		int nextResource = R.drawable.next;
		Bitmap cover = null;

		if ((state & PlaybackService.FLAG_NO_MEDIA) != 0) {
			views.setViewVisibility(R.id.buttons, View.INVISIBLE);
			views.setViewVisibility(R.id.title, View.INVISIBLE);
			views.setInt(R.id.artist, "setText", R.string.no_songs);
		} else if (song == null) {
			views.setViewVisibility(R.id.buttons, View.VISIBLE);
			views.setViewVisibility(R.id.title, View.INVISIBLE);
			views.setInt(R.id.artist, "setText", R.string.app_name);
		} else {
			views.setViewVisibility(R.id.title, View.VISIBLE);
			views.setViewVisibility(R.id.buttons, View.VISIBLE);
			views.setTextViewText(R.id.title, song.title);
			views.setTextViewText(R.id.artist, song.artist);
			cover = song.getMediumCover(context);
			playResource = playing ? R.drawable.hidden_pause : R.drawable.hidden_play;
			nextResource = R.drawable.hidden_next;
		}

		views.setImageViewResource(R.id.play_pause, playResource);
		views.setImageViewResource(R.id.next, nextResource);

		if (cover == null) {
			views.setImageViewResource(R.id.cover, R.drawable.fallback_cover_large);
		} else {
			views.setImageViewBitmap(R.id.cover, cover);
		}


		Intent intent;
		PendingIntent pendingIntent;
		int flags = Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_TASK_ON_HOME;

		intent = new Intent(context, LibraryActivity.class).setAction(Intent.ACTION_MAIN);
		pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
		views.setOnClickPendingIntent(R.id.title, pendingIntent);
		views.setOnClickPendingIntent(R.id.artist, pendingIntent);

		intent = ShortcutPseudoActivity.getIntent(context, PlaybackService.ACTION_TOGGLE_PLAYBACK);
		pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
		views.setOnClickPendingIntent(R.id.play_pause, pendingIntent);

		intent = ShortcutPseudoActivity.getIntent(context, PlaybackService.ACTION_NEXT_SONG);
		pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);
		views.setOnClickPendingIntent(R.id.next, pendingIntent);

		manager.updateAppWidget(new ComponentName(context, FourSquareWidget.class), views);
	}
}
