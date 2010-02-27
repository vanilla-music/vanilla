package org.kreed.vanilla;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class OneCellWidget extends AppWidgetProvider {
	@Override
	public void onUpdate(Context context, AppWidgetManager manager, int[] ids)
	{
		reset(context);
		context.sendBroadcast(new Intent(PlaybackService.APPWIDGET_SMALL_UPDATE));
	}

	private static void sendUpdate(Context context, RemoteViews views)
	{
		AppWidgetManager manager = AppWidgetManager.getInstance(context);
		ComponentName widget = new ComponentName(context, OneCellWidget.class);
		manager.updateAppWidget(widget, views);
	}

	public static void update(Context context, Song song)
	{
		RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.one_cell_widget);
		views.setOnClickPendingIntent(R.id.play_pause, PendingIntent.getBroadcast(context, 0, new Intent(PlaybackService.TOGGLE_PLAYBACK), 0));
		views.setOnClickPendingIntent(R.id.next, PendingIntent.getBroadcast(context, 0, new Intent(PlaybackService.NEXT_SONG), 0));
		if (song != null)
			views.setImageViewBitmap(R.id.cover_view, CoverView.createMiniBitmap(song, 72, 72));

		sendUpdate(context, views);
	}

	public static void reset(Context context)
	{
		RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.default_widget);
		views.setOnClickPendingIntent(R.id.stopped_text, PendingIntent.getService(context, 0, new Intent(context, PlaybackService.class), 0));
		sendUpdate(context, views);
	}
}