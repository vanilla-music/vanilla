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

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Random;

import org.kreed.vanilla.IMusicPlayerWatcher;
import org.kreed.vanilla.IPlaybackService;
import org.kreed.vanilla.R;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

public class PlaybackService extends Service implements Runnable, MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, SharedPreferences.OnSharedPreferenceChangeListener {	
	private static final int NOTIFICATION_ID = 2;

	public static final String APPWIDGET_SMALL_UPDATE = "org.kreed.vanilla.action.APPWIDGET_SMALL_UPDATE";
	public static final String TOGGLE_PLAYBACK = "org.kreed.vanilla.action.TOGGLE_PLAYBACK";
	public static final String NEXT_SONG = "org.kreed.vanilla.action.NEXT_SONG";

	public static final int STATE_NORMAL = 0;
	public static final int STATE_NO_MEDIA = 1;
	public static final int STATE_PLAYING = 2;

	private RemoteCallbackList<IMusicPlayerWatcher> mWatchers;

	public IPlaybackService.Stub mBinder = new IPlaybackService.Stub() {
		public Song[] getCurrentSongs()
		{
			return new Song[] { getSong(-1), getSong(0), getSong(1) };
		}

		public Song getSong(int delta)
		{
			return PlaybackService.this.getSong(delta);
		}

		public int getState()
		{
			return mState;
		}

		public int getPosition()
		{
			if (mMediaPlayer == null)
				return 0;
			return mMediaPlayer.getCurrentPosition();
		}

		public int getDuration()
		{
			if (mMediaPlayer == null)
				return 0;
			return mMediaPlayer.getDuration();
		}

		public void nextSong()
		{
			if (mHandler == null)
				return;
			mHandler.sendMessage(mHandler.obtainMessage(SET_SONG, 1, 0));
		}

		public void previousSong()
		{
			if (mHandler == null)
				return;
			mHandler.sendMessage(mHandler.obtainMessage(SET_SONG, -1, 0));
		}

		public void togglePlayback()
		{
			if (mHandler == null)
				return;
			mHandler.sendMessage(mHandler.obtainMessage(PLAY_PAUSE, 0, 0));
		}

		public void registerWatcher(IMusicPlayerWatcher watcher)
		{
			if (watcher != null)
				mWatchers.register(watcher);
		}

		public void unregisterWatcher(IMusicPlayerWatcher watcher)
		{
			if (watcher != null)
				mWatchers.unregister(watcher);
		}

		public void seekToProgress(int progress)
		{
			if (mMediaPlayer == null || !mMediaPlayer.isPlaying())
				return;
			long position = (long)mMediaPlayer.getDuration() * progress / 1000;
			mMediaPlayer.seekTo((int)position);
		}
	};

	@Override
	public IBinder onBind(Intent intent)
	{
		return mBinder;
	}

	@Override
	public void onCreate()
	{
		mWatchers = new RemoteCallbackList<IMusicPlayerWatcher>();

		new Thread(this).start();
	}

	@Override
	public void onStart(Intent intent, int flags)
	{
		if (mHandler == null)
			return;

		int id = intent.getIntExtra("songId", -1);
		mHandler.sendMessage(mHandler.obtainMessage(QUEUE_ITEM, id, 0));
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();

		if (mMediaPlayer != null) {
			MediaPlayer player = mMediaPlayer;
			mMediaPlayer = null;

			if (player.isPlaying())
				player.pause();
			player.release();
		}

		unregisterReceiver(mReceiver);
		mNotificationManager.cancel(NOTIFICATION_ID);

		resetWidgets();

		if (mWakeLock != null && mWakeLock.isHeld())
			mWakeLock.release();
	}

	public void startForegroundCompat(int id, Notification notification)
	{
		if (mStartForeground == null) {
			setForeground(true);
			mNotificationManager.notify(id, notification);
		} else {
			try {
				mStartForeground.invoke(this, Integer.valueOf(id), notification);
			} catch (InvocationTargetException e) {
				Log.w("VanillaMusic", e);
			} catch (IllegalAccessException e) {
				Log.w("VanillaMusic", e);
			}
		}
	}

	public void stopForegroundCompat(boolean cancelNotification)
	{
		if (mStopForeground == null) {
			setForeground(false);
		} else {
			try {
				mStopForeground.invoke(this, Boolean.FALSE);
			} catch (InvocationTargetException e) {
				Log.w("VanillaMusic", e);
			} catch (IllegalAccessException e) {
				Log.w("VanillaMusic", e);
			}
		}
	}

	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context content, Intent intent)
		{
			String action = intent.getAction();
			if (Intent.ACTION_HEADSET_PLUG.equals(action) && mHandler != null) {
				int plugged = intent.getIntExtra("state", 0) == 130 ? 1 : 0;
				mHandler.sendMessage(mHandler.obtainMessage(HEADSET_PLUGGED, plugged, 0));
			} else if (Intent.ACTION_MEDIA_SCANNER_FINISHED.equals(action)
			        || Intent.ACTION_MEDIA_SCANNER_SCAN_FILE.equals(action)) {
				mHandler.sendEmptyMessage(RETRIEVE_SONGS);
			} else if (TOGGLE_PLAYBACK.equals(action)) {
				if (mHandler.hasMessages(DO)) {
					mHandler.removeMessages(DO);
					Intent launcher = new Intent(PlaybackService.this, NowPlayingActivity.class);
					launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					startActivity(launcher);
				} else {
					mHandler.sendMessageDelayed(mHandler.obtainMessage(DO, PLAY_PAUSE, 0), 250);
				}
			} else if (NEXT_SONG.equals(action)) {
				if (mHandler.hasMessages(DO)) {
					mHandler.removeMessages(DO);
					Intent launcher = new Intent(PlaybackService.this, NowPlayingActivity.class);
					launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					startActivity(launcher);
				} else {
					mHandler.sendMessageDelayed(mHandler.obtainMessage(DO, SET_SONG, 1), 250);
				}
			} else if (APPWIDGET_SMALL_UPDATE.equals(intent.getAction())) {
				OneCellWidget.update(PlaybackService.this, getSong(0));
				return;
			}
		}
	};

	private PhoneStateListener mCallListener = new PhoneStateListener() {
		@Override
		public void onCallStateChanged(int state, String incomingNumber)
		{
			int inCall = -1;

			switch (state) {
			case TelephonyManager.CALL_STATE_RINGING:
			case TelephonyManager.CALL_STATE_OFFHOOK:
				inCall = 1;
				break;
			case TelephonyManager.CALL_STATE_IDLE:
				inCall = 0;
				break;
			}

			if (mHandler != null)
				mHandler.sendMessage(mHandler.obtainMessage(CALL, inCall, 0));
		}
	};

	public void onSharedPreferenceChanged(SharedPreferences settings, String key)
	{
		if (mHandler != null)
			mHandler.sendMessage(mHandler.obtainMessage(PREF_CHANGED, key));
	}

	private boolean mHeadsetPause;
	private boolean mHeadsetOnly;
	private boolean mUseRemotePlayer;
	private boolean mNotifyWhilePaused;
	private boolean mScrobble;

	private Handler mHandler;
	private MediaPlayer mMediaPlayer;
	private Random mRandom;
	private PowerManager.WakeLock mWakeLock;
	private Notification mNotification;
	private SharedPreferences mSettings;
	private AudioManager mAudioManager;
	private NotificationManager mNotificationManager;

	private int[] mSongs;
	private ArrayList<Song> mSongTimeline;
	private int mCurrentSong = -1;
	private int mQueuePos = 0;
	private int mState = STATE_NORMAL;
	private boolean mPlayingBeforeCall;

	private Method mIsWiredHeadsetOn;
	private Method mIsBluetoothScoOn;
	private Method mIsBluetoothA2dpOn;
	private Method mStartForeground;
	private Method mStopForeground;

	private static final int SET_SONG = 0;
	private static final int PLAY_PAUSE = 1;
	private static final int HEADSET_PLUGGED = 2;
	private static final int PREF_CHANGED = 3;
	private static final int QUEUE_ITEM = 4;
	private static final int TRACK_CHANGED = 5;
	private static final int RELEASE_WAKE_LOCK = 6;
	private static final int HANDLE_PLAY = 7;
	private static final int HANDLE_PAUSE = 8;
	private static final int RETRIEVE_SONGS = 9;
	private static final int CALL = 10;
	private static final int DO = 11;

	public void run()
	{
		Looper.prepare();

		mMediaPlayer = new MediaPlayer();
		mSongTimeline = new ArrayList<Song>();
		mRandom = new Random();
		mHandler = new MusicHandler();

		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_HEADSET_PLUG);
		filter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
		filter.addAction(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
		filter.addAction(TOGGLE_PLAYBACK);
		filter.addAction(NEXT_SONG);
		filter.addAction(APPWIDGET_SMALL_UPDATE);
		registerReceiver(mReceiver, filter);

		PowerManager powerManager = (PowerManager)getSystemService(POWER_SERVICE);
		mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VanillaMusicSongChangeLock");

		TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		telephonyManager.listen(mCallListener, PhoneStateListener.LISTEN_CALL_STATE);

		mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		try {
			mIsWiredHeadsetOn = mAudioManager.getClass().getMethod("isWiredHeadsetOn", (Class[]) null);
			mIsBluetoothScoOn = mAudioManager.getClass().getMethod("isBluetoothScoOn", (Class[]) null);
			mIsBluetoothA2dpOn = mAudioManager.getClass().getMethod("isBluetoothA2dpOn", (Class[]) null);
		} catch (NoSuchMethodException e) {
			Log.d("VanillaMusic", "falling back to pre-2.0 AudioManager APIs");
		}
		
		mNotificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		try {
			mStartForeground = getClass().getMethod("startForeground", int.class, Notification.class);
			mStopForeground = getClass().getMethod("stopForeground", boolean.class);
		} catch (NoSuchMethodException e) {
			Log.d("VanillaMusic", "falling back to pre-2.0 Service APIs");
		}

		mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		mMediaPlayer.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
		mMediaPlayer.setOnCompletionListener(this);
		mMediaPlayer.setOnErrorListener(this);
		retrieveSongs();

		mSettings = PreferenceManager.getDefaultSharedPreferences(this);
		mSettings.registerOnSharedPreferenceChangeListener(this);
		mHeadsetPause = mSettings.getBoolean("headset_pause", true);
		mHeadsetOnly = mSettings.getBoolean("headset_only", false);
		mUseRemotePlayer = mSettings.getBoolean("remote_player", true);
		mNotifyWhilePaused = mSettings.getBoolean("notify_while_paused", true);
		mScrobble = mSettings.getBoolean("scrobble", false);

		setCurrentSong(1);

		Looper.loop();
	}

	private void loadPreference(String key)
	{
		if ("headset_pause".equals(key)) {
			mHeadsetPause = mSettings.getBoolean("headset_pause", true);
		} else if ("headset_only".equals(key)) {
			mHeadsetOnly = mSettings.getBoolean(key, false);
			if (mHeadsetOnly && isSpeakerOn() && mMediaPlayer.isPlaying())
				pause();
		} else if ("remote_player".equals(key)) {
			mUseRemotePlayer = mSettings.getBoolean(key, true);
			updateNotification();
		} else if ("notify_while_paused".equals(key)){
			mNotifyWhilePaused = mSettings.getBoolean(key, true);
			updateNotification();
		} else if ("scrobble".equals(key)) {
			mScrobble = mSettings.getBoolean("scrobble", false);
		}
	}

	public void setState(int state)
	{
		if (mState == state)
			return;

		int oldState = mState;
		mState = state;

		updateNotification();

		int i = mWatchers.beginBroadcast();
		while (--i != -1) {
			try {
				mWatchers.getBroadcastItem(i).stateChanged(oldState, mState);
			} catch (RemoteException e) {
				// Null elements will be removed automatically
			}
		}
		mWatchers.finishBroadcast();

		if (mScrobble) {
			Intent intent = new Intent("net.jjc1138.android.scrobbler.action.MUSIC_STATUS");
			intent.putExtra("playing", mState == STATE_PLAYING);
			intent.putExtra("id", getSong(0).id);
			sendBroadcast(intent);
		}
	}

	private void retrieveSongs()
	{
		mSongs = Song.getAllSongs();
		if (mSongs == null) {
			setState(STATE_NO_MEDIA);
		} else {
			if (mState == STATE_NO_MEDIA)
				setState(STATE_NORMAL);
		}
	}

	private void updateNotification()
	{
		Song song = getSong(0);

		if (song == null || !mNotifyWhilePaused && mState == STATE_NORMAL) {
			mNotificationManager.cancel(NOTIFICATION_ID);
			return;
		}

		String title = song.title;
		if (mState != STATE_PLAYING)
			title += ' ' + getResources().getString(R.string.paused);

		RemoteViews views = new RemoteViews(getPackageName(), R.layout.statusbar);
		views.setImageViewResource(R.id.icon, R.drawable.status_icon);
		views.setTextViewText(R.id.title, title);
		views.setTextViewText(R.id.artist, song.artist);

		Notification notification = new Notification();
		notification.contentView = views;
		notification.icon = R.drawable.status_icon;
		notification.flags |= Notification.FLAG_ONGOING_EVENT;
		Intent intent = new Intent(this, mUseRemotePlayer ? RemoteActivity.class : NowPlayingActivity.class);
		notification.contentIntent = PendingIntent.getActivity(ContextApplication.getContext(), 0, intent, 0);

		mNotification = notification;
		mNotificationManager.notify(NOTIFICATION_ID, mNotification);
	}

	private boolean isSpeakerOn()
	{
		try {
			return !(Boolean)mIsWiredHeadsetOn.invoke(mAudioManager, (Object[])null)
				&& !(Boolean)mIsBluetoothScoOn.invoke(mAudioManager, (Object[])null)
				&& !(Boolean)mIsBluetoothA2dpOn.invoke(mAudioManager, (Object[])null);
		} catch (InvocationTargetException e) {
			Log.w("VanillaMusic", e);
		} catch (IllegalAccessException e) {
			Log.w("VanillaMusic", e);
		}

		// Why is there no true equivalent to this in Android 2.0?
		return (mAudioManager.getRouting(mAudioManager.getMode()) & AudioManager.ROUTE_SPEAKER) != 0;
	}

	private void play()
	{
		if (mHeadsetOnly && isSpeakerOn())
			return;

		mMediaPlayer.start();
		mHandler.sendEmptyMessage(HANDLE_PLAY);
	}

	private void pause()
	{
		mMediaPlayer.pause();
		mHandler.sendEmptyMessage(HANDLE_PAUSE);
	}

	private void setPlaying(boolean play)
	{
		if (play)
			play();
		else
			pause();
	}

	private void setCurrentSong(int delta)
	{
		Song song = getSong(delta);
		if (song == null)
			return;

		mCurrentSong += delta;

		updateNotification();

		try {
			mMediaPlayer.reset();
			mMediaPlayer.setDataSource(song.path);
			mMediaPlayer.prepare();
			if (mState == STATE_PLAYING)
				play();
		} catch (IOException e) {
			Log.e("VanillaMusic", "IOException", e);
		}

		int i = mWatchers.beginBroadcast();
		while (--i != -1) {
			try {
				mWatchers.getBroadcastItem(i).songChanged(song);
			} catch (RemoteException e) {
				// Null elements will be removed automatically
			}
		}
		mWatchers.finishBroadcast();

		getSong(+2);

		while (mCurrentSong > 15) {
			mSongTimeline.remove(0);
			--mCurrentSong;
		}

		updateWidgets();
	}

	public void onCompletion(MediaPlayer player)
	{
		mWakeLock.acquire();
		mHandler.sendEmptyMessage(TRACK_CHANGED);
		mHandler.sendEmptyMessage(RELEASE_WAKE_LOCK);
	}

	public boolean onError(MediaPlayer player, int what, int extra)
	{
		Log.e("VanillaMusic", "MediaPlayer error: " + what + " " + extra);
		mMediaPlayer.reset();
		mHandler.sendEmptyMessage(TRACK_CHANGED);
		return true;
	}

	private Song randomSong()
	{
		return new Song(mSongs[mRandom.nextInt(mSongs.length)]);
	}

	private synchronized Song getSong(int delta)
	{
		if (mSongTimeline == null)
			return null;

		int pos = mCurrentSong + delta;

		if (pos < 0)
			return null;

		int size = mSongTimeline.size();
		if (pos > size)
			return null;

		if (pos == size) {
			if (mSongs == null)
				return null;
			mSongTimeline.add(randomSong());
		}

		return mSongTimeline.get(pos);
	}

	private void updateWidgets()
	{
		Song song = getSong(0);
		OneCellWidget.update(this, song);
	}

	private void resetWidgets()
	{
		OneCellWidget.reset(this);
	}

	private class MusicHandler extends Handler {
		@Override
		public void handleMessage(Message message)
		{
			switch (message.what) {
			case SET_SONG:
				setCurrentSong(message.arg1);
				break;
			case PLAY_PAUSE:
				setPlaying(!mMediaPlayer.isPlaying());
				break;
			case HEADSET_PLUGGED:
				if (mHeadsetPause) {
					boolean plugged = message.arg1 == 1;
					if (!plugged && mMediaPlayer.isPlaying())
						pause();
				}
				break;
			case PREF_CHANGED:
				loadPreference((String)message.obj);
				break;
			case QUEUE_ITEM:
				if (message.arg1 == -1) {
					mQueuePos = 0;
				} else {
					Song song = new Song(message.arg1);
					String text = getResources().getString(R.string.enqueued, song.title);
					Toast.makeText(ContextApplication.getContext(), text, Toast.LENGTH_SHORT).show();

					int i = mCurrentSong + 1 + mQueuePos++;
					if (i < mSongTimeline.size())
						mSongTimeline.set(i, song);
					else
						mSongTimeline.add(song);
				}
				break;
			case TRACK_CHANGED:
				setCurrentSong(+1);
				break;
			case RELEASE_WAKE_LOCK:
				if (mWakeLock.isHeld())
					mWakeLock.release();
				break;
			case HANDLE_PLAY:
				setState(STATE_PLAYING);
				startForegroundCompat(NOTIFICATION_ID, mNotification);
				break;
			case HANDLE_PAUSE:
				setState(STATE_NORMAL);
				stopForegroundCompat(false);
				break;
			case RETRIEVE_SONGS:
				retrieveSongs();
				break;
			case CALL:
				boolean inCall = message.arg1 == 1;
				if (inCall) {
					if (mState == STATE_PLAYING) {
						mPlayingBeforeCall = true;
						pause();
					}
				} else {
					if (mPlayingBeforeCall) {
						play();
						mPlayingBeforeCall = false;
					}
				}
				break;
			case DO:
				if (message.arg1 == DO) {
					Log.w("VanillaMusic", "handleMessage.DO should not be called with arg1 = DO");
					return;
				}

				message.what = message.arg1;
				message.arg1 = message.arg2;
				handleMessage(message);
				break;
			}
		}
	}
}