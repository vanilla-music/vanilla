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
import java.util.Collections;
import java.util.Random;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

public final class PlaybackService extends Service implements Handler.Callback, MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, SharedPreferences.OnSharedPreferenceChangeListener {	
	private static final int NOTIFICATION_ID = 2;
	private static final int DOUBLE_CLICK_DELAY = 400;

	public static final String ACTION_TOGGLE_PLAYBACK = "org.kreed.vanilla.action.TOGGLE_PLAYBACK";
	public static final String ACTION_NEXT_SONG = "org.kreed.vanilla.action.NEXT_SONG";
	public static final String ACTION_PREVIOUS_SONG = "org.kreed.vanilla.action.PREVIOUS_SONG";
	/**
	 * Intent action that may be invoked through startService.
	 *
	 * Given a song or group of songs, play the first and enqueues the rest after
	 * it.
	 *
	 * If FLAG_SHUFFLE is enabled, songs will be added to the song timeline in
	 * random order, otherwise, songs will be ordered by album name and then
	 * track number.
	 *
	 * Requires two extras: "type", which can be 1, 2, or 3, indicating artist,
	 * album, or song respectively, and "id", which is the MediaStore id for the
	 * song, album, or artist.
	 */
	public static final String ACTION_PLAY_ITEMS = "org.kreed.vanilla.action.PLAY_ITEMS";
	/**
	 * Intent action that may be invoked through startService.
	 *
	 * Enqueues a song or group of songs.
	 *
	 * The first song from the group will be placed in the timeline either
	 * after the last enqueued song or after the playing song if the queue is
	 * empty. If FLAG_SHUFFLE is enabled, songs will be added to the song
	 * timeline in random order, otherwise, songs will be ordered by album name
	 * and then track number.
	 *
	 * Requires two extras: "type", which can be 1, 2, or 3, indicating artist,
	 * album, or song respectively, and "id", which is the MediaStore id for the
	 * song, album, or artist.
	 */
	public static final String ACTION_ENQUEUE_ITEMS = "org.kreed.vanilla.action.ENQUEUE_ITEMS";
	/**
	 * Reset the position at which songs are enqueued, that is, new songs will
	 * be placed directly after the playing song after this action is invoked.
	 */
	public static final String ACTION_FINISH_ENQUEUEING = "org.kreed.vanilla.action.FINISH_ENQUEUEING";

	public static final String EVENT_REPLACE_SONG = "org.kreed.vanilla.event.REPLACE_SONG";
	public static final String EVENT_CHANGED = "org.kreed.vanilla.event.CHANGED";
	public static final String EVENT_INITIALIZED = "org.kreed.vanilla.event.INITIALIZED";

	public static final int FLAG_NO_MEDIA = 0x2;
	public static final int FLAG_PLAYING = 0x1;
	public static final int FLAG_SHUFFLE = 0x4;
	public static final int FLAG_REPEAT = 0x8;
	public static final int ALL_FLAGS = FLAG_NO_MEDIA + FLAG_PLAYING + FLAG_SHUFFLE + FLAG_REPEAT;

	public static final int NEVER = 0;
	public static final int WHEN_PLAYING = 1;
	public static final int ALWAYS = 2;

	boolean mHeadsetPause;
	boolean mHeadsetOnly;
	private boolean mScrobble;
	private int mNotificationMode;
	private byte mHeadsetControls = -1;

	private Looper mLooper;
	private Handler mHandler;
	MediaPlayer mMediaPlayer;
	private boolean mMediaPlayerInitialized;
	private PowerManager.WakeLock mWakeLock;
	private Notification mNotification;
	private SharedPreferences mSettings;
	private AudioManager mAudioManager;
	private NotificationManager mNotificationManager;

	private ArrayList<Song> mSongTimeline;
	int mTimelinePos;
	private int mQueuePos;
	int mState = 0x80;
	Object mStateLock = new Object();
	boolean mPlayingBeforeCall;
	private int mPendingSeek;
	private Song mLastSongBroadcast;
	boolean mPlugged;
	private ContentObserver mMediaObserver;
	public Receiver mReceiver;
	public InCallListener mCallListener;
	private boolean mIgnoreNextUp;
	private boolean mLoaded;
	boolean mInCall;
	private int mRepeatStart = -1;
	private ArrayList<Song> mRepeatedSongs;

	private Method mIsWiredHeadsetOn;
	private Method mStartForeground;
	private Method mStopForeground;

	@Override
	public void onCreate()
	{
		HandlerThread thread = new HandlerThread("PlaybackService");
		thread.start();

		PlaybackServiceState state = new PlaybackServiceState();
		if (state.load(this)) {
			mSongTimeline = new ArrayList<Song>(state.savedIds.length);
			mTimelinePos = state.savedIndex;
			mPendingSeek = state.savedSeek;
			mState |= state.savedState;
			mRepeatStart = state.repeatStart;

			for (int i = 0; i != state.savedIds.length; ++i)
				mSongTimeline.add(new Song(state.savedIds[i], state.savedFlags[i]));
		} else {
			mSongTimeline = new ArrayList<Song>();
		}

		ContextApplication.setService(this);

		mLooper = thread.getLooper();
		mHandler = new Handler(mLooper, this);
		mHandler.sendEmptyMessage(CREATE);
	}

	/**
	 * Show a Toast that notifies the user the Service is starting up. Useful
	 * to provide a quick reponse to play/pause and next events from widgets
	 * when we must initialize the service before acting on the event.
	 */
	private void showStartupToast()
	{
		Toast.makeText(this, R.string.starting, Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onStart(Intent intent, int flags)
	{
		if (intent != null) {
			String action = intent.getAction();
			int delta = -10;

			if (ACTION_TOGGLE_PLAYBACK.equals(action)) {
				delta = 0;
			} else if (ACTION_NEXT_SONG.equals(action)) {
				delta = 1;
				// Preemptively broadcast an update in attempt to hasten UI
				// feedback.
				broadcastReplaceSong(0, getSong(+1));
			} else if (ACTION_PREVIOUS_SONG.equals(action)) {
				delta = -1;
			} else if (Intent.ACTION_MEDIA_BUTTON.equals(action)) {
				KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
				boolean handled = handleMediaKey(event);
				if (handled) {
					if (!mLoaded)
						showStartupToast();
				} else {
					// We aborted this broadcast in MediaButtonReceiver.
					// Since we did not handle it, we should pass it on
					// to others.
					intent.setComponent(null);
					// Make sure we don't try to handle this again.
					intent.putExtra("org.kreed.vanilla.resent", true);
					sendOrderedBroadcast(intent, null);
				}
			} else if (ACTION_PLAY_ITEMS.equals(action)) {
				chooseSongs(false, intent.getIntExtra("type", 3), intent.getLongExtra("id", -1));
			} else if (ACTION_ENQUEUE_ITEMS.equals(action)) {
				chooseSongs(true, intent.getIntExtra("type", 3), intent.getLongExtra("id", -1));
			} else if (ACTION_FINISH_ENQUEUEING.equals(action)) {
				mQueuePos = 0;
			}

			if (delta != -10) {
				if (!mLoaded)
					showStartupToast();

				go(delta, false);
			}
		}
	}

	@Override
	public void onDestroy()
	{
		ContextApplication.setService(null);

		super.onDestroy();

		if (mSongTimeline != null)
			saveState(true);

		if (mMediaPlayer != null) {
			unsetFlag(FLAG_PLAYING);
			mMediaPlayer.release();
			mMediaPlayer = null;
		}

		mLooper.quit();

		try {
			unregisterReceiver(mReceiver);
		} catch (IllegalArgumentException e) {
			// we haven't registered the receiver yet
		}

		// Renable the external receiver
		PackageManager manager = getPackageManager();
		manager.setComponentEnabledSetting(new ComponentName(this, MediaButtonReceiver.class), PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);

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

	private void initialize()
	{
		sendBroadcast(new Intent(EVENT_INITIALIZED));

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

		if (mSettings == null)
			mSettings = PreferenceManager.getDefaultSharedPreferences(this);
		mSettings.registerOnSharedPreferenceChangeListener(this);
		mHeadsetOnly = mSettings.getBoolean("headset_only", false);
		mNotificationMode = Integer.parseInt(mSettings.getString("notification_mode", "1"));
		mScrobble = mSettings.getBoolean("scrobble", false);
		float volume = mSettings.getFloat("volume", 1.0f);
		if (volume != 1.0f)
			mMediaPlayer.setVolume(volume, volume);

		PowerManager powerManager = (PowerManager)getSystemService(POWER_SERVICE);
		mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VanillaMusicSongChangeLock");

		mLoaded = true;

		setCurrentSong(0);

		if (mPendingSeek != 0)
			mMediaPlayer.seekTo(mPendingSeek);

		mHandler.sendEmptyMessage(POST_CREATE);
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
			if (mMediaPlayer != null) {
				synchronized (mMediaPlayer) {
					mMediaPlayer.setVolume(volume, volume);
				}
			}
		} else if ("media_button".equals(key)) {
			mHeadsetControls = (byte)(mSettings.getBoolean("media_button", true) ? 1 : 0);
			setupReceiver();
		}
	}

	@Override
	public void sendBroadcast(Intent intent)
	{
		ContextApplication.broadcast(intent);
		super.sendBroadcast(intent);
	}

	private void broadcastReplaceSong(int delta, Song song)
	{
		Intent intent = new Intent(EVENT_REPLACE_SONG);
		intent.putExtra("pos", delta);
		intent.putExtra("song", song);
		sendBroadcast(intent);
	}

	private void broadcastReplaceSong(int delta)
	{
		broadcastReplaceSong(delta, getSong(delta));
	}

	boolean setFlag(int flag)
	{
		synchronized (mStateLock) {
			return updateState(mState | flag);
		}
	}

	boolean unsetFlag(int flag)
	{
		synchronized (mStateLock) {
			return updateState(mState & ~flag);
		}
	}

	private boolean updateState(int state)
	{
		state &= ALL_FLAGS;

		if ((state & FLAG_NO_MEDIA) != 0 || mHeadsetOnly && isSpeakerOn())
			state &= ~FLAG_PLAYING;

		Song song = getSong(0);
		if (song == null && (state & FLAG_PLAYING) != 0)
			return false;

		int oldState = mState;
		mState = state;

		if (state != oldState || song != mLastSongBroadcast) {
			Intent intent = new Intent(EVENT_CHANGED);
			intent.putExtra("state", state);
			intent.putExtra("song", song);
			intent.putExtra("pos", mTimelinePos);
			sendBroadcast(intent);

			if (mScrobble) {
				intent = new Intent("net.jjc1138.android.scrobbler.action.MUSIC_STATUS");
				intent.putExtra("playing", (state & FLAG_PLAYING) != 0);
				if (song != null)
					intent.putExtra("id", (int)song.id);
				sendBroadcast(intent);
			}

			updateNotification(song);

			mLastSongBroadcast = song;
		}

		// The current song is the starting point for repeated tracks
		if ((state & FLAG_REPEAT) != 0 && (oldState & FLAG_REPEAT) == 0) {
			song.flags &= ~Song.FLAG_RANDOM;
			mRepeatStart = mTimelinePos;
			broadcastReplaceSong(+1);
		} else if ((state & FLAG_REPEAT) == 0 && (oldState & FLAG_REPEAT) != 0) {
			mRepeatStart = -1;
			broadcastReplaceSong(+1);
		}

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

	boolean isSpeakerOn()
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

	/**
	 * Toggle a flag in the state on or off
	 *
	 * @param flag The flag to be toggled (FLAG_PLAYING, FLAG_SHUFFLE, or FLAG_REPEAT)
	 */
	public void toggleFlag(int flag)
	{
		synchronized (mStateLock) {
			if ((mState & flag) == 0)
				setFlag(flag);
			else
				unsetFlag(flag);
		}
	}

	private ArrayList<Song> getShuffledRepeatedSongs(int end)
	{
		ArrayList<Song> songs = mRepeatedSongs;
		if (songs == null) {
			songs = new ArrayList<Song>(mSongTimeline.subList(mRepeatStart, end));
			Collections.shuffle(songs, ContextApplication.getRandom());
			mRepeatedSongs = songs;
		}
		return songs;
	}

	/**
	 * Move <code>delta</code> places away from the current song.
	 */
	public void setCurrentSong(int delta)
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

		ArrayList<Song> timeline = mSongTimeline;
		synchronized (timeline) {
			if (delta == 1 && mRepeatStart >= 0 && (timeline.get(mTimelinePos + 1).flags & Song.FLAG_RANDOM) != 0) {
				if ((mState & FLAG_SHUFFLE) == 0) {
					mTimelinePos = mRepeatStart;
				} else {
					int j = mTimelinePos + delta;
					ArrayList<Song> songs = getShuffledRepeatedSongs(j);
					for (int i = songs.size(); --i != -1 && --j != -1; )
						mSongTimeline.set(j, songs.get(i));
					mRepeatedSongs = null;
					mTimelinePos = j;
				}
				song = getSong(0);
				broadcastReplaceSong(-1);
			} else {
				mTimelinePos += delta;
			}
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

	/**
	 * Returns the song <code>delta</code> places away from the current position.
	 */
	public Song getSong(int delta)
	{
		if (mSongTimeline == null)
			return null;

		ArrayList<Song> timeline = mSongTimeline;
		Song song;

		synchronized (timeline) {
			int pos = mTimelinePos + delta;
			if (pos < 0)
				return null;

			int size = timeline.size();
			if (pos > size)
				return null;

			if (pos == size) {
				song = new Song();
				timeline.add(song);
			} else {
				song = timeline.get(pos);
			}

			if (delta == 1 && mRepeatStart >= 0 && (song.flags & Song.FLAG_RANDOM) != 0) {
				// We have reached a non-user-selected song; this song will
				// repeated in setCurrentSong so take alternative measures
				if ((mState & FLAG_SHUFFLE) == 0)
					song = timeline.get(mRepeatStart);
				else
					song = getShuffledRepeatedSongs(mTimelinePos + delta).get(0);
			}
		}

		if (!song.populate(false)) {
			song.randomize();
			if (!song.populate(false))
				return null;
		}

		return song;
	}

	/**
	 * Add a set of songs to the song timeline. There are two modes: play and
	 * enqueue. Play will play the first song in the set immediately and enqueue
	 * the remaining songs directly after it. Enqueue will place the set after
	 * the last enqueued item or after the currently playing item if the queue
	 * is empty.
	 *
	 * @param enqueue If true, enqueue the set. If false, play the set.
	 * @param type 1, 2, or 3, indicating artist, album, or song, respectively.
	 * @param id The MediaStore id of the artist, album, or song.
	 */
	private void chooseSongs(boolean enqueue, int type, long id)
	{
		long[] songs = Song.getAllSongIdsWith(type, id);
		if (songs == null || songs.length == 0)
			return;

		if ((mState & FLAG_SHUFFLE) != 0) {
			Random random = ContextApplication.getRandom();
			for (int i = songs.length; --i != 0; ) {
				int j = random.nextInt(i + 1);
				long tmp = songs[j];
				songs[j] = songs[i];
				songs[i] = tmp;
			}
		}

		Song oldSong = getSong(+1);

		ArrayList<Song> timeline = mSongTimeline;
		synchronized (timeline) {
			if (enqueue) {
				int i = mTimelinePos + mQueuePos + 1;
				if (i < timeline.size())
					timeline.subList(i, timeline.size()).clear();

				for (int j = 0; j != songs.length; ++j)
					timeline.add(new Song(songs[j]));

				mQueuePos += songs.length;
			} else {
				timeline.subList(mTimelinePos + 1, timeline.size()).clear();

				for (int j = 0; j != songs.length; ++j)
					timeline.add(new Song(songs[j]));

				mQueuePos += songs.length - 1;

				mHandler.sendEmptyMessage(TRACK_CHANGED);
			}
		}

		mRepeatedSongs = null;
		Song newSong = getSong(+1);
		if (newSong != oldSong)
			broadcastReplaceSong(+1, newSong);

		mHandler.removeMessages(SAVE_STATE);
		mHandler.sendEmptyMessageDelayed(SAVE_STATE, 5000);
	}

	private void saveState(boolean savePosition)
	{
		PlaybackServiceState.saveState(this, mSongTimeline, mTimelinePos, savePosition && mMediaPlayer != null ? mMediaPlayer.getCurrentPosition() : 0, mState & (FLAG_REPEAT + FLAG_SHUFFLE), mRepeatStart);
	}

	private void go(int delta, boolean autoPlay)
	{
		if (autoPlay) {
			synchronized (mStateLock) {
				mState |= FLAG_PLAYING;
			}
		}

		if (mLoaded) {
			if (delta == 0)
				toggleFlag(FLAG_PLAYING);
			else
				setCurrentSong(delta);
		} else {
			mHandler.sendMessage(mHandler.obtainMessage(GO, delta, 0));
		}
	}

	/**
	 * Return whether headset controls should be used, loading the preference
	 * if necessary.
	 */
	private boolean useHeadsetControls()
	{
		if (mSettings == null)
			mSettings = PreferenceManager.getDefaultSharedPreferences(this);
		if (mHeadsetControls == -1)
			mHeadsetControls = (byte)(mSettings.getBoolean("media_button", true) ? 1 : 0);
		return mHeadsetControls == 1;
	}

	boolean handleMediaKey(KeyEvent event)
	{
		if (mInCall || !useHeadsetControls())
			return false;

		int action = event.getAction();

		switch (event.getKeyCode()) {
		case KeyEvent.KEYCODE_HEADSETHOOK:
		case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
			// single quick press: pause/resume. 
			// double press: next track
			// long press: unused (could also do next track? open player?)

			if (action == KeyEvent.ACTION_UP && mIgnoreNextUp) {
				mIgnoreNextUp = false;
				break;
			}

			if (mHandler.hasMessages(MEDIA_BUTTON)) {
				// double press
				if (action == KeyEvent.ACTION_DOWN) {
					mHandler.removeMessages(MEDIA_BUTTON);
					mIgnoreNextUp = true;
					go(1, true);
				}
			} else {
				// single press
				if (action == KeyEvent.ACTION_UP)
					mHandler.sendEmptyMessageDelayed(MEDIA_BUTTON, DOUBLE_CLICK_DELAY);
			}
			break;
		case KeyEvent.KEYCODE_MEDIA_NEXT:
			if (action == KeyEvent.ACTION_DOWN)
				go(1, true);
			break;
		case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
			if (action == KeyEvent.ACTION_DOWN)
				go(-1, true);
			break;
		default:
			return false;
		}

		return true;
	}

	private class Receiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context content, Intent intent)
		{
			String action = intent.getAction();

			if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
				boolean oldPlugged = mPlugged;
				mPlugged = intent.getIntExtra("state", 0) != 0;
				if (mPlugged != oldPlugged && mHeadsetPause && !mPlugged || mHeadsetOnly && isSpeakerOn())
					unsetFlag(FLAG_PLAYING);
			} else if (Intent.ACTION_MEDIA_BUTTON.equals(action)) {
				KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
				if (event != null && handleMediaKey(event))
					abortBroadcast();
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
				mInCall = true;
				if (!mPlayingBeforeCall)
					mPlayingBeforeCall = unsetFlag(FLAG_PLAYING);
				break;
			case TelephonyManager.CALL_STATE_IDLE:
				mInCall = false;
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

	private void setupReceiver()
	{
		if (mReceiver == null) {
			mReceiver = new Receiver();
		} else {
			try {
				unregisterReceiver(mReceiver);
			} catch (IllegalArgumentException e) {
			}
		}

		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_HEADSET_PLUG);
		if (useHeadsetControls())
			filter.addAction(Intent.ACTION_MEDIA_BUTTON);
		filter.setPriority(2000);
		registerReceiver(mReceiver, filter);
	}

	private static final int GO = 0;
	private static final int POST_CREATE = 1;
	private static final int MEDIA_BUTTON = 2;
	private static final int CREATE = 3;
	private static final int TRACK_CHANGED = 5;
	private static final int RELEASE_WAKE_LOCK = 6;
	private static final int SAVE_STATE = 12;
	private static final int PROCESS_SONG = 13;

	public boolean handleMessage(Message message)
	{
		switch (message.what) {
		case MEDIA_BUTTON:
			toggleFlag(FLAG_PLAYING);
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
				while (mTimelinePos > 15 && mRepeatStart > 0) {
					mSongTimeline.remove(0);
					--mTimelinePos;
					--mRepeatStart;
				}
			}

			mHandler.removeMessages(SAVE_STATE);
			mHandler.sendEmptyMessageDelayed(SAVE_STATE, 5000);
			break;
		case CREATE:
			initialize();
			break;
		case POST_CREATE:
			mHeadsetPause = mSettings.getBoolean("headset_pause", true);
			setupReceiver();

			// Don't receive broadcasts through the external receiver now that
			// we get them in the Service's receiver
			PackageManager manager = getPackageManager();
			manager.setComponentEnabledSetting(new ComponentName(this, MediaButtonReceiver.class), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

			mCallListener = new InCallListener();
			TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
			telephonyManager.listen(mCallListener, PhoneStateListener.LISTEN_CALL_STATE);
			break;
		default:
			return false;
		}

		return true;
	}

	/**
	 * Returns the current service state. The state comprises several individual
	 * flags.
	 */
	public int getState()
	{
		synchronized (mStateLock) {
			return mState;
		}
	}

	/**
	 * Returns the current position in current song in milliseconds.
	 */
	public int getPosition()
	{
		if (mMediaPlayer == null)
			return 0;
		synchronized (mMediaPlayer) {
			return mMediaPlayer.getCurrentPosition();
		}
	}

	/**
	 * Returns the duration of the current song in milliseconds.
	 */
	public int getDuration()
	{
		if (mMediaPlayer == null)
			return 0;
		synchronized (mMediaPlayer) {
			return mMediaPlayer.getDuration();
		}
	}

	/**
	 * Returns the position of the current song in the song timeline.
	 */
	public int getTimelinePos()
	{
		return mTimelinePos;
	}

	/**
	 * Seek to a position in the current song.
	 *
	 * @param progress Proportion of song completed (where 1000 is the end of the song)
	 */
	public void seekToProgress(int progress)
	{
		if (mMediaPlayer == null)
			return;
		synchronized (mMediaPlayer) {
			long position = (long)mMediaPlayer.getDuration() * progress / 1000;
			mMediaPlayer.seekTo((int)position);
		}
	}

	@Override
	public IBinder onBind(Intent intents)
	{
		return null;
	}
}
