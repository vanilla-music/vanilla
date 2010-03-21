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
import android.widget.Toast;

public class PlaybackService extends Service implements Runnable, MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, SharedPreferences.OnSharedPreferenceChangeListener {	
	private static final int NOTIFICATION_ID = 2;

	public static final String TOGGLE_PLAYBACK = "org.kreed.vanilla.action.TOGGLE_PLAYBACK";
	public static final String NEXT_SONG = "org.kreed.vanilla.action.NEXT_SONG";

	public static final String EVENT_LOADED = "org.kreed.vanilla.event.LOADED";
	public static final String EVENT_CHANGED = "org.kreed.vanilla.event.CHANGED";

	public static final int ACTION_PLAY = 0;
	public static final int ACTION_ENQUEUE = 1;

	public static final int STATE_NORMAL = 0;
	public static final int STATE_NO_MEDIA = 1;
	public static final int STATE_PLAYING = 2;

	private boolean mStarted;
	private int mPendingGo = -1;

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
			synchronized (mStateLock) {
				return mState;
			}
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
			PlaybackService.this.togglePlayback();
		}

		public void seekToProgress(int progress)
		{
			if (mMediaPlayer == null)
				return;
			synchronized (mMediaPlayer) {
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

	private void go(int delta)
	{
		if (!mStarted) {
			Toast.makeText(this, R.string.starting, Toast.LENGTH_SHORT).show();
			mPendingGo = delta;
			return;
		}

		if (mHandler.hasMessages(GO)) {
			mHandler.removeMessages(GO);
			Intent launcher = new Intent(this, NowPlayingActivity.class);
			launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(launcher);
		} else {
			mHandler.sendMessageDelayed(mHandler.obtainMessage(GO, delta, 0), 250);
		}
	}

	@Override
	public void onStart(Intent intent, int flags)
	{
		String action = intent.getAction();

		if (TOGGLE_PLAYBACK.equals(action)) {
			go(0);
		} else if (NEXT_SONG.equals(action)) {
			go(1);
		}

		if (mHandler != null)
			mHandler.sendMessage(mHandler.obtainMessage(DO_ITEM, intent));

		mStarted = true;
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

	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context content, Intent intent)
		{
			String action = intent.getAction();
			if (Intent.ACTION_HEADSET_PLUG.equals(action) && mHandler != null) {
				boolean oldPlugged = mPlugged;
				mPlugged = intent.getIntExtra("state", 0) != 0;
				if (mPlugged != oldPlugged && (mHeadsetPause && !mPlugged || mHeadsetOnly && !isSpeakerOn()))
					updateState(STATE_NORMAL);
			} else if (Intent.ACTION_MEDIA_SCANNER_FINISHED.equals(action)
			        || Intent.ACTION_MEDIA_SCANNER_SCAN_FILE.equals(action)) {
				mHandler.sendEmptyMessage(RETRIEVE_SONGS);
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
	private boolean mMediaPlayerInitialized;
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
	private Object mStateLock = new Object();
	private boolean mPlayingBeforeCall;
	private int mPendingSeek;
	private Song mLastSongBroadcast;
	private boolean mPlugged;

	private Method mIsWiredHeadsetOn;
	private Method mStartForeground;
	private Method mStopForeground;

	private static final int GO = 0;
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

		PlaybackServiceState state = new PlaybackServiceState();
		if (state.load(this)) {
			Song song = new Song(state.savedIds[state.savedIndex]);
			if (song.populate())
				broadcastChange(mState, mState, song);
			else
				stateLoaded = false;

			mSongTimeline = new ArrayList<Song>(state.savedIds.length);
			mCurrentSong = state.savedIndex;
			mPendingSeek = state.savedSeek;

			for (int i = 0; i != state.savedIds.length; ++i)
				mSongTimeline.add(i == mCurrentSong ? song : new Song(state.savedIds[i]));
		}

		if (stateLoaded)
			stateLoaded = mSongTimeline != null;

		retrieveSongs();

		if (!stateLoaded) {
			mSongTimeline = new ArrayList<Song>();
			broadcastChange(mState, mState, getSong(0));
		}

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

		if (mPendingGo != -1)
			mHandler.sendMessage(mHandler.obtainMessage(GO, mPendingGo, 0));

		updateNotification(getSong(0));

		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_HEADSET_PLUG);
		filter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
		filter.addAction(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
		registerReceiver(mReceiver, filter);

		TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
		telephonyManager.listen(mCallListener, PhoneStateListener.LISTEN_CALL_STATE);

		Looper.loop();
	}

	private void loadPreference(String key)
	{
		if ("headset_pause".equals(key)) {
			mHeadsetPause = mSettings.getBoolean("headset_pause", true);
		} else if ("headset_only".equals(key)) {
			mHeadsetOnly = mSettings.getBoolean(key, false);
			Log.d("VanillaMusic", "mp: " + mMediaPlayer);
			if (mHeadsetOnly && isSpeakerOn())
				updateState(STATE_NORMAL);
		} else if ("remote_player".equals(key)) {
			updateNotification(getSong(0));
		} else if ("notify_while_paused".equals(key)){
			mNotifyWhilePaused = mSettings.getBoolean(key, true);
			updateNotification(getSong(0));
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

			if (mScrobble) {
				intent = new Intent("net.jjc1138.android.scrobbler.action.MUSIC_STATUS");
				intent.putExtra("playing", newState == STATE_PLAYING);
				if (song != null)
					intent.putExtra("id", song.id);
				sendBroadcast(intent);
			}

			mLastSongBroadcast = song;
		}
	}

	public boolean updateState(int state)
	{
		synchronized (mStateLock) {
			if (mState == STATE_NO_MEDIA)
				return false;

			Song song = getSong(0);
			int oldState = mState;
			mState = state;

			if (song == null)
				return false;

			broadcastChange(oldState, state, song);

			boolean cancelNotification;
			if (state != oldState || song != mLastSongBroadcast)
				cancelNotification = updateNotification(song);
			else
				cancelNotification = false;

			if (mState != oldState) {
				if (mState == STATE_PLAYING) {
					startForegroundCompat(NOTIFICATION_ID, mNotification);
					if (mMediaPlayerInitialized) {
						synchronized (mMediaPlayer) {
							mMediaPlayer.start();
						}
					}
				} else {
					stopForegroundCompat(cancelNotification);
					if (mMediaPlayerInitialized) {
						synchronized (mMediaPlayer) {
							mMediaPlayer.pause();
						}
					}
				}

				return true;
			} else {
				return false;
			}
		}
	}

	private void retrieveSongs()
	{
		mSongs = Song.getAllSongIds(null);
		if (mSongs == null) {
			updateState(STATE_NO_MEDIA);
		} else if (mState == STATE_NO_MEDIA) {
			synchronized (mStateLock) {
				mState = -1;
				updateState(STATE_NORMAL);
			}
		}
	}

	private boolean updateNotification(Song song)
	{
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

	private void togglePlayback()
	{
		synchronized (mStateLock) {
			if (mState == STATE_PLAYING)
				updateState(STATE_NORMAL);
			else if (mState == STATE_NORMAL)
				updateState(STATE_PLAYING);
		}
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
				if (!mMediaPlayerInitialized)
					mMediaPlayerInitialized = true;
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

	private void saveState(boolean savePosition)
	{
		PlaybackServiceState.saveState(this, mSongTimeline, mCurrentSong, savePosition && mMediaPlayer != null ? mMediaPlayer.getCurrentPosition() : 0);
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
				updateState(STATE_PLAYING);
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
					mPlayingBeforeCall = updateState(STATE_NORMAL);
				} else if (mPlayingBeforeCall) {
					updateState(STATE_PLAYING);
				}
				break;
			case GO:
				if (message.arg1 == 0)
					togglePlayback();
				else
					setCurrentSong(message.arg1);
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