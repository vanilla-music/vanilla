package org.kreed.vanilla;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.util.TypedValue;
import android.widget.RemoteViews;

public class OneCellWidget extends AppWidgetProvider {
	@Override
	public void onUpdate(Context context, AppWidgetManager manager, int[] ids)
	{
		Log.i("VanillaMusic", "initial update");
	}

	@Override
	public void onReceive(Context context, Intent intent)
	{
		if (PlaybackService.EVENT_CHANGED.equals(intent.getAction())) {
			Song song = intent.getParcelableExtra("song");
			boolean playing = intent.getIntExtra("newState", 0) == PlaybackService.STATE_PLAYING;
			update(context, song, playing);
		} else {
			super.onReceive(context, intent);
		}
	}

	public static void update(Context context, Song song, boolean playing)
	{
		RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.one_cell_widget);
		ComponentName widget = new ComponentName(context, OneCellWidget.class);

		views.setOnClickPendingIntent(R.id.play_pause, PendingIntent.getBroadcast(context, 0, new Intent(PlaybackService.TOGGLE_PLAYBACK), 0));
		views.setOnClickPendingIntent(R.id.next, PendingIntent.getBroadcast(context, 0, new Intent(PlaybackService.NEXT_SONG), 0));

		if (song != null) {
			int size = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 72, context.getResources().getDisplayMetrics());
			views.setImageViewBitmap(R.id.cover_view, CoverView.createMiniBitmap(song, size, size));
		}

		AppWidgetManager.getInstance(context).updateAppWidget(widget, views);
	}
}