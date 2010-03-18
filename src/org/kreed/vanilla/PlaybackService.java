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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.kreed.vanilla.IPlaybackService;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

public class PlaybackService extends Service implements Runnable, MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, SharedPreferences.OnSharedPreferenceChangeListener {	
	private static final int NOTIFICATION_ID = 2;

	public static final String APPWIDGET_SMALL_UPDATE = "org.kreed.vanilla.action.APPWIDGET_SMALL_UPDATE";
	public static final String TOGGLE_PLAYBACK = "org.kreed.vanilla.action.TOGGLE_PLAYBACK";
	public static final String NEXT_SONG = "org.kreed.vanilla.action.NEXT_SONG";

	public static final String EVENT_LOADED = "org.kreed.vanilla.event.LOADED";
	public static final String EVENT_CHANGED = "org.kreed.vanilla.event.CHANGED";

	public static final int ACTION_PLAY = 0;
	public static final int ACTION_ENQUEUE = 1;

	public static final int STATE_NORMAL = 0;
	public static final int STATE_NO_MEDIA = 1;
	public static final int STATE_PLAYING = 2;

	public IPlaybackService.Stub mBinder = new IPlaybackService.Stub() {
		public boolean isLoaded()
		{
			return mHandler != null;
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
			synchronized (mMediaPlayer) {
				return mMediaPlayer.getCurrentPosition();
			}
		}

		public int getDuration()
		{
			if (mMediaPlayer == null)
				return 0;
			synchronized (mMediaPlayer) {
				return mMediaPlayer.getDuration();
			}
		}

		public void setCurrentSong(int delta)
		{
			PlaybackService.this.setCurrentSong(delta);
		}

		public void togglePlayback()
		{
			if (mHandler == null)
				return;
			mHandler.sendMessage(mHandler.obtainMessage(GO, 0, 0));
		}

		public void seekToProgress(int progress)
		{
			if (mMediaPlayer == null)
				return;
			synchronized (mMediaPlayer) {
				if (!mMediaPlayer.isPlaying())
					return;
				long position = (long)mMediaPlayer.getDuration() * progress / 1000;
				mMediaPlayer.seekTo((int)position);
			}
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
		new Thread(this).start();
	}

	@Override
	public void onStart(Intent intent, int flags)
	{
		if (mHandler != null)
			mHandler.sendMessage(mHandler.obtainMessage(DO_ITEM, intent));
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();

		Log.d("VanillaMusic", "destroy");

		if (mSongs != null)
			saveState(true);

		if (mMediaPlayer != null) {
			MediaPlayer player = mMediaPlayer;
			mMediaPlayer = null;

			if (player.isPlaying())
				player.pause();
			player.release();
		}

		try {
			unregisterReceiver(mReceiver);
		} catch (IllegalArgumentException e) {
			// we haven't registered the receiver yet
		}
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

	public void stopForegroundCompat(Boolean cancelNotification)
	{
		if (mStopForeground == null) {
			setForeground(false);
		} else {
			try {
				mStopForeground.invoke(this, cancelNotification);
			} catch (InvocationTargetException e) {
				Log.w("VanillaMusic", e);
			} catch (IllegalAccessException e) {
				Log.w("VanillaMusic", e);
			}
		}
	}

	private void go(int delta)
	{
		if (mHandler.hasMessages(GO)) {
			mHandler.removeMessages(GO);
			Intent launcher = new Intent(PlaybackService.this, NowPlayingActivity.class);
			launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(launcher);
		} else {
			mHandler.sendMessageDelayed(mHandler.obtainMessage(GO, delta, 0), 250);
		}
	}

	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context content, Intent intent)
		{
			String action = intent.getAction();
			if (Intent.ACTION_HEADSET_PLUG.equals(action) && mHandler != null) {
				boolean oldPlugged = mPlugged;
				mPlugged = intent.getIntExtra("state", 0) != 0;
				if (mPlugged != oldPlugged && (mHeadsetPause && !mPlugged || mHeadsetOnly && !isSpeakerOn()) && mMediaPlayer.isPlaying()) {
					mMediaPlayer.pause();
					mHandler.sendMessage(mHandler.obtainMessage(SET_STATE, STATE_NORMAL, 0));
				}
			} else if (Intent.ACTION_MEDIA_SCANNER_FINISHED.equals(action)
			        || Intent.ACTION_MEDIA_SCANNER_SCAN_FILE.equals(action)) {
				mHandler.sendEmptyMessage(RETRIEVE_SONGS);
			} else if (TOGGLE_PLAYBACK.equals(action)) {
				go(0);
			} else if (NEXT_SONG.equals(action)) {
				go(1);
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
	private int mCurrentSong = 0;
	private int mQueuePos = 0;
	private int mState = STATE_NORMAL;
	private boolean mPlayingBeforeCall;
	private int mPendingSeek;
	private Song mLastSongBroadcast;
	private boolean mPlugged;

	private Method mIsWiredHeadsetOn;
	private Method mStartForeground;
	private Method mStopForeground;

	private static final int GO = 0;
	private static final int SET_STATE = 1;
	private static final int PREF_CHANGED = 3;
	private static final int DO_ITEM = 4;
	private static final int TRACK_CHANGED = 5;
	private static final int RELEASE_WAKE_LOCK = 6;
	private static final int RETRIEVE_SONGS = 9;
	private static final int CALL = 10;
	private static final int SAVE_STATE = 12;
	private static final int PROCESS_SONG = 13;

	public void run()
	{
		Looper.prepare();

		mRandom = new Random();

		boolean stateLoaded = true;

		try {
			DataInputStream in = new DataInputStream(openFileInput(STATE_FILE));
			if (in.readLong() == STATE_FILE_MAGIC) {
				mCurrentSong = in.readInt();
				int n = in.readInt();

				if (n > 0) {
					int[] ids = new int[n];
					for (int i = 0; i != n; ++i)
						ids[i] = in.readInt();
					mPendingSeek = in.readInt();
					in.close();

					Song song = new Song(ids[mCurrentSong]);
					song.populate();
					if (song.path == null) {
						stateLoaded = false;
					} else {
						broadcastChange(mState, mState, song);
						updateWidgets(song);
					}

					ArrayList<Song> timeline = new ArrayList<Song>(n);
					for (int i = 0; i != n; ++i)
						timeline.add(i == mCurrentSong ? song : new Song(ids[i]));

					mSongTimeline = timeline;
				}
			}
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
			Log.w("VanillaMusic", e);
		}

		if (stateLoaded)
			stateLoaded = mSongTimeline != null;

		if (!stateLoaded) {
			retrieveSongs();
			mSongTimeline = new ArrayList<Song>();
			Song song = getSong(0);
			broadcastChange(mState, mState, song);
			updateWidgets(song);
		}

		if (stateLoaded)
			retrieveSongs();

		mMediaPlayer = new MediaPlayer();

		mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		mMediaPlayer.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
		mMediaPlayer.setOnCompletionListener(this);
		mMediaPlayer.setOnErrorListener(this);

		mAudioManager = (AudioManager)getSystemService(Context.AUDIO_SERVICE);
		mNotificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

		try {
			mStartForeground = getClass().getMethod("startForeground", int.class, Notification.class);
			mStopForeground = getClass().getMethod("stopForeground", boolean.class);
		} catch (NoSuchMethodException e) {
			Log.d("VanillaMusic", "falling back to pre-2.0 Service APIs");
		}

		if (!"3".equals(Build.VERSION.SDK)) {
			try {
				mIsWiredHeadsetOn = mAudioManager.getClass().getMethod("isWiredHeadsetOn", (Class[])null);
			} catch (NoSuchMethodException e) {
				Log.d("VanillaMusic", "falling back to pre-1.6 AudioManager APIs");
			}
		}

		mSettings = PreferenceManager.getDefaultSharedPreferences(this);
		mSettings.registerOnSharedPreferenceChangeListener(this);
		mHeadsetPause = mSettings.getBoolean("headset_pause", true);
		mHeadsetOnly = mSettings.getBoolean("headset_only", false);
		mNotifyWhilePaused = mSettings.getBoolean("notify_while_paused", true);
		mScrobble = mSettings.getBoolean("scrobble", false);

		PowerManager powerManager = (PowerManager)getSystemService(POWER_SERVICE);
		mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VanillaMusicSongChangeLock");

		mHandler = new MusicHandler();

		setCurrentSong(0);
		if (mPendingSeek != 0)
			mMediaPlayer.seekTo(mPendingSeek);

		sendBroadcast(new Intent(EVENT_LOADED));

		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_HEADSET_PLUG);
		filter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
		filter.addAction(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
		filter.addAction(TOGGLE_PLAYBACK);
		filter.addAction(NEXT_SONG);
		filter.addAction(APPWIDGET_SMALL_UPDATE);
		registerReceiver(mReceiver, filter);

		TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		telephonyManager.listen(mCallListener, PhoneStateListener.LISTEN_CALL_STATE);

		SharedPreferences.Editor editor = mSettings.edit();
		editor.putBoolean("explicit_stop", false);
		editor.commit();

		Looper.loop();
	}

	private void loadPreference(String key)
	{
		if ("headset_pause".equals(key)) {
			mHeadsetPause = mSettings.getBoolean("headset_pause", true);
		} else if ("headset_only".equals(key)) {
			mHeadsetOnly = mSettings.getBoolean(key, false);
			Log.d("VanillaMusic", "mp: " + mMediaPlayer);
			if (mHeadsetOnly && isSpeakerOn() && mMediaPlayer.isPlaying())
				pause();
		} else if ("remote_player".equals(key)) {
			updateNotification();
		} else if ("notify_while_paused".equals(key)){
			mNotifyWhilePaused = mSettings.getBoolean(key, true);
			updateNotification();
			if (!mNotifyWhilePaused && mState != STATE_PLAYING)
				stopForegroundCompat(true);
		} else if ("scrobble".equals(key)) {
			mScrobble = mSettings.getBoolean("scrobble", false);
		}
	}

	public void broadcastChange(int oldState, int newState, Song song)
	{
		if (newState != oldState || song != mLastSongBroadcast) {
			Intent intent = new Intent(EVENT_CHANGED);
			intent.putExtra("oldState", oldState);
			intent.putExtra("newState", newState);
			intent.putExtra("song", song);
			sendBroadcast(intent);

			mLastSongBroadcast = song;
		}
	}

	public void updateState(int state)
	{
		Song song = getSong(0);
		int oldState = mState;
		mState = state;

		if (song == null)
			return;

		broadcastChange(oldState, state, song);

		boolean cancelNotification = updateNotification();
		updateWidgets(song);

		if (mState != oldState) {
			if (mState == STATE_PLAYING)
				startForegroundCompat(NOTIFICATION_ID, mNotification);
			else
				stopForegroundCompat(cancelNotification);
		}

		if (mScrobble) {
			Intent intent = new Intent("net.jjc1138.android.scrobbler.action.MUSIC_STATUS");
			intent.putExtra("playing", mState == STATE_PLAYING);
			intent.putExtra("id", song.id);
			sendBroadcast(intent);
		}
	}

	private void retrieveSongs()
	{
		mSongs = Song.getAllSongIds(null);
		if (mSongs == null)
			updateState(STATE_NO_MEDIA);
		else if (mState == STATE_NO_MEDIA)
			updateState(STATE_NORMAL);
	}

	private boolean updateNotification()
	{
		Song song = getSong(0);

		if (song == null || !mNotifyWhilePaused && mState == STATE_NORMAL) {
			mNotificationManager.cancel(NOTIFICATION_ID);
			return true;
		}

		mNotification = new SongNotification(song, mState == STATE_PLAYING);
		mNotificationManager.notify(NOTIFICATION_ID, mNotification);

		return false;
	}

	private boolean isSpeakerOn()
	{
		if (mAudioManager.isBluetoothA2dpOn() || mAudioManager.isBluetoothScoOn())
			return false;

		if (mIsWiredHeadsetOn != null) {
			try {
				if ((Boolean)mIsWiredHeadsetOn.invoke(mAudioManager, (Object[])null))
					return false;
			} catch (InvocationTargetException e) {
				Log.w("VanillaMusic", e);
			} catch (IllegalAccessException e) {
				Log.w("VanillaMusic", e);
			}
		}

		if (mPlugged)
			return false;

		// Why is there no true equivalent to this in Android 2.0?
		return (mAudioManager.getRouting(mAudioManager.getMode()) & AudioManager.ROUTE_SPEAKER) != 0;
	}

	private void play()
	{
		if (mHeadsetOnly && isSpeakerOn())
			return;

		mMediaPlayer.start();
		updateState(STATE_PLAYING);
	}

	private void pause()
	{
		mMediaPlayer.pause();
		updateState(STATE_NORMAL);
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
		if (mMediaPlayer == null)
			return;

		Song song = getSong(delta);
		if (song == null)
			return;

		synchronized (mSongTimeline) {
			mCurrentSong += delta;
		}

		try {
			synchronized (mMediaPlayer) {
				mMediaPlayer.reset();
				mMediaPlayer.setDataSource(song.path);
				mMediaPlayer.prepare();
			}
			if (mState == STATE_PLAYING)
				mMediaPlayer.start();
			updateState(mState);
		} catch (IOException e) {
			Log.e("VanillaMusic", "IOException", e);
		}

		mHandler.sendEmptyMessage(PROCESS_SONG);
	}

	public void onCompletion(MediaPlayer player)
	{
		if (mWakeLock != null)
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

	private int randomSong()
	{
		return mSongs[mRandom.nextInt(mSongs.length)];
	}

	private Song getSong(int delta)
	{
		if (mSongTimeline == null)
			return null;

		Song song;

		synchronized (mSongTimeline) {
			int pos = mCurrentSong + delta;
			if (pos < 0)
				return null;

			int size = mSongTimeline.size();
			if (pos > size)
				return null;

			if (pos == size) {
				if (mSongs == null)
					return null;
				mSongTimeline.add(new Song(randomSong()));
			}

			song = mSongTimeline.get(pos);
		}

		if (!song.populate()) {
			if (mSongs == null)
				return null;
			song.id = randomSong();
			if (!song.populate())
				return null;
		}

		return song;
	}

	private void updateWidgets(Song song)
	{
		OneCellWidget.update(this, song);
	}

	private void resetWidgets()
	{
		OneCellWidget.reset(this);
	}

	private static final String STATE_FILE = "state";
	private static final long STATE_FILE_MAGIC = 0x8a9d3f9fca31L;

	private void saveState(boolean savePosition)
	{
		try {
			DataOutputStream out = new DataOutputStream(openFileOutput(STATE_FILE, 0));
			out.writeLong(STATE_FILE_MAGIC);
			out.writeInt(mCurrentSong);
			int n = mSongTimeline == null ? 0 : mSongTimeline.size();
			out.writeInt(n);
			for (int i = 0; i != n; ++i)
				out.writeInt(mSongTimeline.get(i).id);
			out.writeInt(savePosition && mMediaPlayer != null ? mMediaPlayer.getCurrentPosition() : 0);
			out.close();
		} catch (IOException e) {
			Log.w("VanillaMusic", e);
		}
	}

	private class MusicHandler extends Handler {
		@Override
		public void handleMessage(Message message)
		{
			switch (message.what) {
			case PREF_CHANGED:
				loadPreference((String)message.obj);
				break;
			case DO_ITEM:
				Intent intent = (Intent)message.obj;
				int id = intent.getIntExtra("id", -1);
				if (id == -1) {
					mQueuePos = 0;
				} else {
					boolean enqueue = intent.getIntExtra("action", ACTION_PLAY) == ACTION_ENQUEUE;

					int[] songs = Song.getAllSongIdsWith(intent.getIntExtra("type", SongData.FIELD_TITLE), id);
					if (songs == null || songs.length == 0)
						break;

					for (int i = songs.length; --i != 0; ) {
						int j = mRandom.nextInt(i + 1);
						int tmp = songs[j];
						songs[j] = songs[i];
						songs[i] = tmp;
					}

					synchronized (mSongTimeline) {
						if (enqueue) {
							int i = mCurrentSong + mQueuePos + 1;

							for (int j = 0; j != songs.length; ++i, ++j) {
								Song song = new Song(songs[j]);
								if (i < mSongTimeline.size())
									mSongTimeline.set(i, song);
								else
									mSongTimeline.add(song);
							}

							mQueuePos += songs.length;
						} else {
							List<Song> view = mSongTimeline.subList(mCurrentSong + 1, mSongTimeline.size());
							List<Song> queue = mQueuePos == 0 ? null : new ArrayList<Song>(view);
							view.clear();

							for (int i = 0; i != songs.length; ++i)
								mSongTimeline.add(new Song(songs[i]));

							if (queue != null)
								mSongTimeline.addAll(queue);

							mQueuePos += songs.length - 1;
						}
					}

					if (!enqueue)
						mHandler.sendEmptyMessage(TRACK_CHANGED);

					mHandler.removeMessages(SAVE_STATE);
					mHandler.sendEmptyMessageDelayed(SAVE_STATE, 5000);
				}
				break;
			case TRACK_CHANGED:
				setCurrentSong(+1);
				if (mState != STATE_PLAYING)
					play();
				break;
			case RELEASE_WAKE_LOCK:
				if (mWakeLock != null && mWakeLock.isHeld())
					mWakeLock.release();
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
			case GO:
				if (message.arg1 == 0)
					setPlaying(!mMediaPlayer.isPlaying());
				else
					setCurrentSong(message.arg1);
				break;
			case SET_STATE:
				updateState(message.arg1);
				break;
			case SAVE_STATE:
				// For unexpected terminations: crashes, task killers, etc.
				// In most cases onDestroy will handle this
				saveState(false);
				break;
			case PROCESS_SONG:
				getSong(+2);

				synchronized (mSongTimeline) {
					while (mCurrentSong > 15) {
						mSongTimeline.remove(0);
						--mCurrentSong;
					}
				}

				mHandler.removeMessages(SAVE_STATE);
				mHandler.sendEmptyMessageDelayed(SAVE_STATE, 5000);
				break;
			}
		}
	}
}