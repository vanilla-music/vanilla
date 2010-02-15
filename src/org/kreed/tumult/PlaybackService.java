package org.kreed.tumult;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

public class PlaybackService extends Service {	
	private MusicPlayer mPlayer;
	private NotificationManager mNotificationManager;
	private Method mStartForeground;
	private Method mStopForeground;

	@Override
	public IBinder onBind(Intent intent)
	{
		if (mPlayer == null)
			return null;
		return mPlayer.mBinder;
	}

	@Override
	public void onCreate()
	{
		mNotificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		try {
			mStartForeground = getClass().getMethod("startForeground", new Class[] { int.class, Notification.class });
			mStopForeground = getClass().getMethod("stopForeground", new Class[] { boolean.class });
		} catch (NoSuchMethodException e) {
			// Running on an older platform.
			mStartForeground = mStopForeground = null;
		}
		
		mPlayer = new MusicPlayer(this);
	}
	
	@Override
	public void onStart(Intent intent, int flags)
	{
		int id;

		if ((id = intent.getIntExtra("songId", -1)) != -1)
			mPlayer.queueSong(id);
		else
			mPlayer.stopQueueing();
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		if (mPlayer != null) {
			mPlayer.release();
			mPlayer = null;
		}
	}

	public void startForegroundCompat(int id, Notification notification)
	{
		if (mStartForeground == null) {
			setForeground(true);
			mNotificationManager.notify(id, notification);
		} else {
			Object[] startForegroundArgs = { Integer.valueOf(id), notification };
			try {
				mStartForeground.invoke(this, startForegroundArgs);
			} catch (InvocationTargetException e) {
				Log.w("Tumult", "Unable to invoke startForeground", e);
			} catch (IllegalAccessException e) {
				Log.w("Tumult", "Unable to invoke startForeground", e);
			}
		}
	}

	public void stopForegroundCompat(int id)
	{
		if (mStopForeground == null) {
			mNotificationManager.cancel(id);
			setForeground(false);
		} else {
			Object[] topForegroundArgs = { Boolean.TRUE };
			try {
				mStopForeground.invoke(this, topForegroundArgs);
			} catch (InvocationTargetException e) {
				Log.w("Tumult", "Unable to invoke stopForeground", e);
			} catch (IllegalAccessException e) {
				Log.w("Tumult", "Unable to invoke stopForeground", e);
			}
		}
	}
}