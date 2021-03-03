/*
 * Copyright (C) 2017-2021 Adrian Ulrich <adrian@blinkenlights.ch>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package ch.blinkenlights.android.vanilla;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import androidx.core.app.NotificationCompat;


public class NotificationHelper {
	/**
	 * The notification manager instance we are using.
	 */
	private NotificationManager mNotificationManager;
	/**
	 * The name of the channel to use for these notifications.
	 */
	private String mChannelId;

	/**
	 * Creates and returns a new NotificationHelper.
	 *
	 * @param context the context to use.
	 * @param channelId the ID of the notification channel to create.
	 * @param name the user visible name of the channel.
	 */
	public NotificationHelper(Context context, String channelId, String name) {
		mNotificationManager = (NotificationManager)context.getSystemService(context.NOTIFICATION_SERVICE);
		mChannelId = channelId;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			NotificationChannel channel = new NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_LOW);
			mNotificationManager.createNotificationChannel(channel);
		}
	}

	/**
	 * Returns a new NotificationCompat.Builder.
	 *
	 * @param context the context to use
	 */
	public NotificationCompat.Builder getNewBuilder(Context context) {
		return new NotificationCompat.Builder(context, mChannelId);
	}

	/**
	 * Post a notification to be shown in the status bar.
	 *
	 * @param id the id of this notification.
	 * @param notification the notification to display.
	 */
	public void notify(int id, Notification notification) {
		mNotificationManager.notify(id, notification);
	}

	/**
	 * Cancels a notification.
	 *
	 * @param id the id to cancel
	 */
	public void cancel(int id) {
		mNotificationManager.cancel(id);
	}
}
