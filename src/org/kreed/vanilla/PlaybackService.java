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
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

public class PlaybackService extends Service implements Runnable, MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, SharedPreferences.OnSharedPreferenceChangeListener {	
	private static final int NOTIFICATION_ID = 2;

	public static final String TOGGLE_PLAYBACK = "org.kreed.vanilla.action.TOGGLE_PLAYBACK";
	public static final String NEXT_SONG = "org.kreed.vanilla.action.NEXT_SONG";

	public static final String EVENT_REPLACE_SONG = "org.kreed.vanilla.event.REPLACE_SONG";
	public static final String EVENT_CHANGED = "org.kreed.vanilla.event.CHANGED";

	public static final int ACTION_PLAY = 0;
	public static final int ACTION_ENQUEUE = 1;

	public static final int FLAG_NO_MEDIA = 0x2;
	public static final int FLAG_PLAYING = 0x1;
	public static final int ALL_FLAGS = FLAG_NO_MEDIA + FLAG_PLAYING;

	public static final int NEVER = 0;
	public static final int WHEN_PLAYING = 1;
	public static final int ALWAYS = 2;

	private boolean mHeadsetPause;
	private boolean mHeadsetOnly;
	private boolean mScrobble;
	private int mNotificationMode;

	private Handler mHandler;
	private MediaPlayer mMediaPlayer;
	private boolean mMediaPlayerInitialized;
	private PowerManager.WakeLock mWakeLock;
	private Notification mNotification;
	private SharedPreferences mSettings;
	private AudioManager mAudioManager;
	private NotificationManager mNotificationManager;

	private ArrayList<Song> mSongTimeline;
	private int mCurrentSong;
	private int mQueuePos;
	private int mState = 0x80;
	private Object mStateLock = new Object();
	private boolean mPlayingBeforeCall;
	private int mPendingSeek;
	private int mPendingGo = -1;
	private Song mLastSongBroadcast;
	private boolean mPlugged;
	private ContentObserver mMediaObserver;
	public Receiver mReceiver;
	public InCallListener mCallListener;

	private Method mIsWiredHeadsetOn;
	private Method mStartForeground;
	private Method mStopForeground;

	@Override
	public void onCreate()
	{
		new Thread(this).start();
	}

	private void go(int delta)
	{
		if (mHandler == null) {
			Toast.makeText(this, R.string.starting, Toast.LENGTH_SHORT).show();
			mPendingGo = delta;
			return;
		}

		if (mHandler.hasMessages(GO)) {
			mHandler.removeMessages(GO);
			Intent launcher = new Intent(this, FullPlaybackActivity.class);
			launcher.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(launcher);
		} else {
			mHandler.sendMessageDelayed(mHandler.obtainMessage(GO, delta, 0), 400);
		}
	}

	@Override
	public void onStart(Intent intent, int flags)
	{
		if (intent != null) {
			String action = intent.getAction();

			if (TOGGLE_PLAYBACK.equals(action))
				go(0);
			else if (NEXT_SONG.equals(action))
				go(1);
		}

		if (mHandler != null)
			mHandler.sendMessage(mHandler.obtainMessage(DO_ITEM, intent));
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();

		if (mSongTimeline != null)
			saveState(true);

		if (mMediaPlayer != null) {
			mSongTimeline = null;
			unsetFlag(FLAG_PLAYING);
			mMediaPlayer.release();
			mMediaPlayer = null;
		}

		try {
			unregisterReceiver(mReceiver);
		} catch (IllegalArgumentException e) {
			// we haven't registered the receiver yet
		}

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

		if (cancelNotification && mNotificationManager != null)
			mNotificationManager.cancel(NOTIFICATION_ID);
	}

	public void run()
	{
		Looper.prepare();

		PlaybackServiceState state = new PlaybackServiceState();
		if (state.load(this)) {
			Song song = new Song(state.savedIds[state.savedIndex]);
			if (song.populate(false)) {
				broadcastChange(mState, mState, song);

				mSongTimeline = new ArrayList<Song>(state.savedIds.length);
				mCurrentSong = state.savedIndex;
				mPendingSeek = state.savedSeek;

				for (int i = 0; i != state.savedIds.length; ++i)
					mSongTimeline.add(i == mCurrentSong ? song : new Song(state.savedIds[i]));
			}
		}

		if (mSongTimeline == null) {
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
		mNotificationMode = Integer.parseInt(mSettings.getString("notification_mode", "1"));
		mScrobble = mSettings.getBoolean("scrobble", false);
		float volume = mSettings.getFloat("volume", 1.0f);
		if (volume != 1.0f)
			mMediaPlayer.setVolume(volume, volume);

		PowerManager powerManager = (PowerManager)getSystemService(POWER_SERVICE);
		mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VanillaMusicSongChangeLock");

		mHandler = new MusicHandler();

		int go = 0;
		if (mPendingGo == 0)
			mState |= FLAG_PLAYING;
		else if (mPendingGo == 1)
			go = 1;
		setCurrentSong(go);

		if (mPendingSeek != 0)
			mMediaPlayer.seekTo(mPendingSeek);

		sendBroadcast(new Intent(EVENT_REPLACE_SONG).putExtra("all", true));

		mHandler.sendEmptyMessage(POST_CREATE);

		Looper.loop();
	}

	private void loadPreference(String key)
	{
		if ("headset_pause".equals(key)) {
			mHeadsetPause = mSettings.getBoolean("headset_pause", true);
		} else if ("headset_only".equals(key)) {
			mHeadsetOnly = mSettings.getBoolean(key, false);
			if (mHeadsetOnly && isSpeakerOn())
				unsetFlag(FLAG_PLAYING);
		} else if ("remote_player".equals(key)) {
			// the preference is loaded in SongNotification class
			updateNotification(getSong(0));
		} else if ("notification_mode".equals(key)){
			mNotificationMode = Integer.parseInt(mSettings.getString("notification_mode", "1"));
			updateNotification(getSong(0));
		} else if ("scrobble".equals(key)) {
			mScrobble = mSettings.getBoolean("scrobble", false);
		} else if ("volume".equals(key)) {
			float volume = mSettings.getFloat("volume", 1.0f);
			synchronized (mMediaPlayer) {
				mMediaPlayer.setVolume(volume, volume);
			}
		}
	}

	public void broadcastChange(int oldState, int newState, Song song)
	{
		if (newState != oldState || song != mLastSongBroadcast) {
			Intent intent = new Intent(EVENT_CHANGED);
			intent.putExtra("state", newState);
			intent.putExtra("song", song);
			sendBroadcast(intent);

			if (mScrobble) {
				intent = new Intent("net.jjc1138.android.scrobbler.action.MUSIC_STATUS");
				intent.putExtra("playing", (newState & FLAG_PLAYING) != 0);
				if (song != null)
					intent.putExtra("id", song.id);
				sendBroadcast(intent);
			}

			mLastSongBroadcast = song;
		}
	}

	private boolean setFlag(int flag)
	{
		synchronized (mStateLock) {
			return updateState(mState | flag);
		}
	}

	private boolean unsetFlag(int flag)
	{
		synchronized (mStateLock) {
			return updateState(mState & ~flag);
		}
	}

	private boolean updateState(int state)
	{
		state &= ALL_FLAGS;

		if ((state & FLAG_NO_MEDIA) != 0)
			state &= ~FLAG_PLAYING;

		Song song = getSong(0);
		if (song == null && (state & FLAG_PLAYING) != 0)
			return false;

		int oldState = mState;
		mState = state;

		Song lastBroadcast = mLastSongBroadcast;
		broadcastChange(oldState, state, song);

		if (state != oldState || song != lastBroadcast)
			updateNotification(song);

		if ((state & FLAG_NO_MEDIA) != 0 && (oldState & FLAG_NO_MEDIA) == 0) {
			ContentResolver resolver = ContextApplication.getContext().getContentResolver();
			mMediaObserver = new MediaContentObserver(mHandler);
			resolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mMediaObserver);
		} else if ((state & FLAG_NO_MEDIA) == 0 && (oldState & FLAG_NO_MEDIA) != 0) {
			ContentResolver resolver = ContextApplication.getContext().getContentResolver();
			resolver.unregisterContentObserver(mMediaObserver);
			mMediaObserver = null;
		}

		if ((state & FLAG_PLAYING) != 0 && (oldState & FLAG_PLAYING) == 0) {
			if (mNotificationMode != NEVER)
				startForegroundCompat(NOTIFICATION_ID, mNotification);
			if (mMediaPlayerInitialized) {
				synchronized (mMediaPlayer) {
					mMediaPlayer.start();
				}
			}
		} else if ((state & FLAG_PLAYING) == 0 && (oldState & FLAG_PLAYING) != 0) {
			stopForegroundCompat(false);
			if (mMediaPlayerInitialized) {
				synchronized (mMediaPlayer) {
					mMediaPlayer.pause();
				}
			}
		} else {
			return false;
		}

		return true;
	}

	private void updateNotification(Song song)
	{
		boolean shouldNotify = mNotificationMode == ALWAYS || mNotificationMode == WHEN_PLAYING && (mState & FLAG_PLAYING) != 0;
		if (song != null && shouldNotify) {
			mNotification = new SongNotification(song, (mState & FLAG_PLAYING) != 0);
			mNotificationManager.notify(NOTIFICATION_ID, mNotification);
		} else {
			stopForegroundCompat(true);
		}
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

	private void toggleFlag(int flag)
	{
		synchronized (mStateLock) {
			if ((mState & flag) == 0)
				setFlag(flag);
			else
				unsetFlag(flag);
		}
	}

	private void setCurrentSong(int delta)
	{
		if (mMediaPlayer == null)
			return;

		Song song = getSong(delta);
		if (song == null) {
			setFlag(FLAG_NO_MEDIA);
			return;
		} else {
			unsetFlag(FLAG_NO_MEDIA);
		}

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
			if ((mState & FLAG_PLAYING) != 0)
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
		Song song = getSong(+1);
		if (song != null && !song.populate(true))
			setFlag(FLAG_NO_MEDIA);
		else
			mHandler.sendEmptyMessage(TRACK_CHANGED);
		return true;
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
				song = new Song();
				mSongTimeline.add(song);
			} else {
				song = mSongTimeline.get(pos);
			}
		}

		if (!song.populate(false)) {
			song.randomize();
			if (!song.populate(false))
				return null;
		}

		return song;
	}

	private void saveState(boolean savePosition)
	{
		PlaybackServiceState.saveState(this, mSongTimeline, mCurrentSong, savePosition && mMediaPlayer != null ? mMediaPlayer.getCurrentPosition() : 0);
	}

	private class Receiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context content, Intent intent)
		{
			String action = intent.getAction();
			if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
				boolean oldPlugged = mPlugged;
				mPlugged = intent.getIntExtra("state", 0) != 0;
				if (mPlugged != oldPlugged && (mHeadsetPause && !mPlugged || mHeadsetOnly && !isSpeakerOn()))
					unsetFlag(FLAG_PLAYING);
			}
		}
	};

	private class InCallListener extends PhoneStateListener {
		@Override
		public void onCallStateChanged(int state, String incomingNumber)
		{
			switch (state) {
			case TelephonyManager.CALL_STATE_RINGING:
			case TelephonyManager.CALL_STATE_OFFHOOK:
				if (!mPlayingBeforeCall)
					mPlayingBeforeCall = unsetFlag(FLAG_PLAYING);
				break;
			case TelephonyManager.CALL_STATE_IDLE:
				if (mPlayingBeforeCall) {
					setFlag(FLAG_PLAYING);
					mPlayingBeforeCall = false;
				}
				break;
			}
		}
	};

	private class MediaContentObserver extends ContentObserver {
		public MediaContentObserver(Handler handler)
		{
			super(handler);
		}

		@Override
		public void onChange(boolean selfChange)
		{
			setCurrentSong(0);
		}
	}

	public void onSharedPreferenceChanged(SharedPreferences settings, String key)
	{
		loadPreference(key);
	}

	private static final int GO = 0;
	private static final int POST_CREATE = 1;
	private static final int DO_ITEM = 4;
	private static final int TRACK_CHANGED = 5;
	private static final int RELEASE_WAKE_LOCK = 6;
	private static final int SAVE_STATE = 12;
	private static final int PROCESS_SONG = 13;

	private class MusicHandler extends Handler {
		@Override
		public void handleMessage(Message message)
		{
			switch (message.what) {
			case DO_ITEM:
				Intent intent = (Intent)message.obj;
				long id = message.obj == null ? -1 : intent.getLongExtra("id", -1);
				if (id == -1) {
					mQueuePos = 0;
				} else {
					boolean enqueue = intent.getIntExtra("action", ACTION_PLAY) == ACTION_ENQUEUE;

					long[] songs = Song.getAllSongIdsWith(intent.getIntExtra("type", 3), id);
					if (songs == null || songs.length == 0)
						break;

					Random random = ContextApplication.getRandom();
					for (int i = songs.length; --i != 0; ) {
						int j = random.nextInt(i + 1);
						long tmp = songs[j];
						songs[j] = songs[i];
						songs[i] = tmp;
					}

					boolean changed = false;

					synchronized (mSongTimeline) {
						if (enqueue) {
							int i = mCurrentSong + mQueuePos + 1;
							if (mQueuePos == 0)
								changed = true;

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

							if (songs.length > 1)
								changed = true;
						}
					}

					if (!enqueue)
						mHandler.sendEmptyMessage(TRACK_CHANGED);

					if (changed)
						sendBroadcast(new Intent(EVENT_REPLACE_SONG));

					mHandler.removeMessages(SAVE_STATE);
					mHandler.sendEmptyMessageDelayed(SAVE_STATE, 5000);
				}
				break;
			case TRACK_CHANGED:
				setCurrentSong(+1);
				setFlag(FLAG_PLAYING);
				break;
			case RELEASE_WAKE_LOCK:
				if (mWakeLock != null && mWakeLock.isHeld())
					mWakeLock.release();
				break;
			case GO:
				if (message.arg1 == 0)
					toggleFlag(FLAG_PLAYING);
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
			case POST_CREATE:
				mReceiver = new Receiver();
				IntentFilter filter = new IntentFilter();
				filter.addAction(Intent.ACTION_HEADSET_PLUG);
				registerReceiver(mReceiver, filter);

				mCallListener = new InCallListener();
				TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
				telephonyManager.listen(mCallListener, PhoneStateListener.LISTEN_CALL_STATE);
				break;
			}
		}
	}

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

		public void toggleFlag(int flag)
		{
			PlaybackService.this.toggleFlag(flag);
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
}