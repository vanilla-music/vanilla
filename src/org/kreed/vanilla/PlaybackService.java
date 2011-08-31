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

package org.kreed.vanilla;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import android.app.Activity;
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
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

public final class PlaybackService extends Service implements Handler.Callback, MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, SharedPreferences.OnSharedPreferenceChangeListener, SongTimeline.Callback {	
	private static final int NOTIFICATION_ID = 2;

	/**
	 * Action for startService: toggle playback on/off.
	 */
	public static final String ACTION_TOGGLE_PLAYBACK = "org.kreed.vanilla.action.TOGGLE_PLAYBACK";
	/**
	 * Action for startService: toggle playback on/off.
	 *
	 * Unlike {@link PlaybackService#ACTION_TOGGLE_PLAYBACK}, the toggle does
	 * not occur immediately. Instead, it is delayed so that if two of these
	 * actions are received within 400 ms, the playback activity is opened
	 * instead.
	 */
	public static final String ACTION_TOGGLE_PLAYBACK_DELAYED = "org.kreed.vanilla.action.TOGGLE_PLAYBACK_DELAYED";
	/**
	 * Action for startService: advance to the next song.
	 */
	public static final String ACTION_NEXT_SONG = "org.kreed.vanilla.action.NEXT_SONG";
	/**
	 * Action for startService: advance to the next song.
	 *
	 * Unlike {@link PlaybackService#ACTION_NEXT_SONG}, the toggle does
	 * not occur immediately. Instead, it is delayed so that if two of these
	 * actions are received within 400 ms, the playback activity is opened
	 * instead.
	 */
	public static final String ACTION_NEXT_SONG_DELAYED = "org.kreed.vanilla.action.NEXT_SONG_DELAYED";
	/**
	 * Action for startService: advance to the next song.
	 *
	 * Like ACTION_NEXT_SONG, but starts playing automatically if paused
	 * when this is called.
	 */
	public static final String ACTION_NEXT_SONG_AUTOPLAY = "org.kreed.vanilla.action.NEXT_SONG_AUTOPLAY";
	/**
	 * Action for startService: go back to the previous song.
	 */
	public static final String ACTION_PREVIOUS_SONG = "org.kreed.vanilla.action.PREVIOUS_SONG";
	/**
	 * Action for startService: go back to the previous song.
	 *
	 * Like ACTION_PREVIOUS_SONG, but starts playing automatically if paused
	 * when this is called.
	 */
	public static final String ACTION_PREVIOUS_SONG_AUTOPLAY = "org.kreed.vanilla.action.PREVIOUS_SONG_AUTOPLAY";
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

	public static final int FLAG_NO_MEDIA = 0x2;
	public static final int FLAG_PLAYING = 0x1;
	public static final int FLAG_SHUFFLE = 0x4;
	public static final int FLAG_REPEAT = 0x8;
	/**
	 * Set when the current song is unplayable.
	 */
	public static final int FLAG_ERROR = 0x10;
	public static final int ALL_FLAGS = FLAG_NO_MEDIA + FLAG_PLAYING + FLAG_SHUFFLE + FLAG_REPEAT + FLAG_ERROR;

	public static final int NEVER = 0;
	public static final int WHEN_PLAYING = 1;
	public static final int ALWAYS = 2;

	boolean mHeadsetPause;
	private boolean mScrobble;
	private int mNotificationMode;
	/**
	 * The time to wait before considering the player idle.
	 */
	private int mIdleTimeout;

	private Looper mLooper;
	private Handler mHandler;
	MediaPlayer mMediaPlayer;
	private boolean mMediaPlayerInitialized;
	private PowerManager.WakeLock mWakeLock;
	private SharedPreferences mSettings;
	private NotificationManager mNotificationManager;

	SongTimeline mTimeline;
	int mState = 0x80;
	Object mStateLock = new Object();
	boolean mPlayingBeforeCall;
	private int mPendingSeek;
	public Receiver mReceiver;
	public InCallListener mCallListener;
	private boolean mLoaded;
	/**
	 * The volume set by the user in the preferences.
	 */
	private float mUserVolume = 1.0f;
	/**
	 * The actual volume of the media player. Will differ from the user volume
	 * when fading the volume.
	 */
	private float mCurrentVolume = 1.0f;

	private static Method mStartForeground;
	private static Method mStopForeground;

	static {
		try {
			mStartForeground = Service.class.getMethod("startForeground", int.class, Notification.class);
			mStopForeground = Service.class.getMethod("stopForeground", boolean.class);
		} catch (NoSuchMethodException e) {
		}
	}

	@Override
	public void onCreate()
	{
		HandlerThread thread = new HandlerThread("PlaybackService");
		thread.start();

		mTimeline = new SongTimeline();
		mTimeline.setCallback(this);
		mPendingSeek = mTimeline.loadState(this);
		if (mTimeline.isRepeating())
			mState |= FLAG_REPEAT;
		if (mTimeline.isShuffling())
			mState |= FLAG_SHUFFLE;

		ContextApplication.setService(this);

		mLooper = thread.getLooper();
		mHandler = new Handler(mLooper, this);
		mHandler.sendEmptyMessage(CREATE);
	}

	/**
	 * Show a Toast that notifies the user the Service is starting up. Useful
	 * to provide a quick response to play/pause and next events from widgets
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

			if (ACTION_TOGGLE_PLAYBACK.equals(action)) {
				go(0, false);
			} else if (ACTION_TOGGLE_PLAYBACK_DELAYED.equals(action)) {
				if (mHandler.hasMessages(CALL_GO, Integer.valueOf(0))) {
					mHandler.removeMessages(CALL_GO, Integer.valueOf(0));
					startActivity(new Intent(this, LaunchActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
				} else {
					mHandler.sendMessageDelayed(mHandler.obtainMessage(CALL_GO, Integer.valueOf(0)), 400);
				}
			} else if (ACTION_NEXT_SONG.equals(action)) {
				// Preemptively broadcast an update in attempt to hasten UI
				// feedback.
				broadcastReplaceSong(0, getSong(+1));
				go(1, false);
			} else if (ACTION_NEXT_SONG_AUTOPLAY.equals(action)) {
				go(1, true);
			} else if (ACTION_NEXT_SONG_DELAYED.equals(action)) {
				if (mHandler.hasMessages(CALL_GO, Integer.valueOf(1))) {
					mHandler.removeMessages(CALL_GO, Integer.valueOf(1));
					startActivity(new Intent(this, LaunchActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
				} else {
					mHandler.sendMessageDelayed(mHandler.obtainMessage(CALL_GO, Integer.valueOf(1)), 400);
				}
			} else if (ACTION_PREVIOUS_SONG.equals(action)) {
				go(-1, false);
			} else if (ACTION_PREVIOUS_SONG_AUTOPLAY.equals(action)) {
				go(-1, true);
			} else if (ACTION_PLAY_ITEMS.equals(action)) {
				mTimeline.chooseSongs(false, intent.getIntExtra("type", 3), intent.getLongExtra("id", -1));
				mHandler.sendEmptyMessage(TRACK_CHANGED);
			} else if (ACTION_ENQUEUE_ITEMS.equals(action)) {
				mTimeline.chooseSongs(true, intent.getIntExtra("type", 3), intent.getLongExtra("id", -1));
				mHandler.removeMessages(SAVE_STATE);
				mHandler.sendEmptyMessageDelayed(SAVE_STATE, 5000);
			} else if (ACTION_FINISH_ENQUEUEING.equals(action)) {
				mTimeline.finishEnqueueing();
			}

			userActionTriggered();
			MediaButtonHandler buttons = MediaButtonHandler.getInstance();
			if (buttons != null)
				buttons.registerMediaButton();
		}
	}

	@Override
	public void onDestroy()
	{
		ContextApplication.setService(null);

		super.onDestroy();

		if (mMediaPlayer != null) {
			mTimeline.saveState(this, mMediaPlayer.getCurrentPosition());

			unsetFlag(FLAG_PLAYING);
			mMediaPlayer.release();
			mMediaPlayer = null;
		}

		mLooper.quit();

		MediaButtonHandler.unregisterMediaButton();

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

	/**
	 * Return the SharedPreferences instance containing the PlaybackService
	 * settings, creating it if necessary.
	 */
	private SharedPreferences getSettings()
	{
		if (mSettings == null)
			mSettings = PreferenceManager.getDefaultSharedPreferences(this);
		return mSettings;
	}

	private void initialize()
	{
		mMediaPlayer = new MediaPlayer();
		mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		mMediaPlayer.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
		mMediaPlayer.setOnCompletionListener(this);
		mMediaPlayer.setOnErrorListener(this);

		mNotificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);

		SharedPreferences settings = getSettings();
		settings.registerOnSharedPreferenceChangeListener(this);
		mNotificationMode = Integer.parseInt(settings.getString("notification_mode", "1"));
		mScrobble = settings.getBoolean("scrobble", false);
		float volume = settings.getFloat("volume", 1.0f);
		if (volume != 1.0f) {
			mCurrentVolume = mUserVolume = volume;
			mMediaPlayer.setVolume(volume, volume);
		}
		mIdleTimeout = settings.getBoolean("use_idle_timeout", false) ? settings.getInt("idle_timeout", 3600) : 0;
		Song.mDisableCoverArt = settings.getBoolean("disable_cover_art", false);

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
		SharedPreferences settings = getSettings();
		if ("headset_pause".equals(key)) {
			mHeadsetPause = settings.getBoolean("headset_pause", true);
		} else if ("remote_player".equals(key)) {
			// the preference is loaded in SongNotification class
			updateNotification();
		} else if ("notification_mode".equals(key)){
			mNotificationMode = Integer.parseInt(settings.getString("notification_mode", "1"));
			// This is the only way to remove a notification created by
			// startForeground(), even if we are not currently in foreground
			// mode.
			stopForegroundCompat(true);
			updateNotification();
		} else if ("scrobble".equals(key)) {
			mScrobble = settings.getBoolean("scrobble", false);
		} else if ("volume".equals(key)) {
			float volume = settings.getFloat("volume", 1.0f);
			mCurrentVolume = mUserVolume = volume;
			if (mMediaPlayer != null) {
				synchronized (mMediaPlayer) {
					mMediaPlayer.setVolume(volume, volume);
				}
			}
		} else if ("media_button".equals(key)) {
			MediaButtonHandler.reloadPreference();
		} else if ("use_idle_timeout".equals(key) || "idle_timeout".equals(key)) {
			mIdleTimeout = settings.getBoolean("use_idle_timeout", false) ? settings.getInt("idle_timeout", 3600) : 0;
			userActionTriggered();
		} else if ("disable_cover_art".equals(key)) {
			Song.mDisableCoverArt = settings.getBoolean("disable_cover_art", false);
		} else if ("display_mode".equals(key)) {
			ArrayList<Activity> activities = ContextApplication.mActivities;
			for (Activity activity : activities) {
				if (activity instanceof FullPlaybackActivity)
					activity.finish();
			}
		} else if ("notification_inverted_colour".equals(key)) {
			updateNotification();
		}
	}

	private void broadcastReplaceSong(int delta, Song song)
	{
		Intent intent = new Intent(EVENT_REPLACE_SONG);
		intent.putExtra("pos", delta);
		intent.putExtra("song", song);
		mHandler.sendMessage(mHandler.obtainMessage(BROADCAST, intent));
	}

	private void setFlag(int flag)
	{
		synchronized (mStateLock) {
			updateState(mState | flag);
		}
	}

	private void unsetFlag(int flag)
	{
		synchronized (mStateLock) {
			updateState(mState & ~flag);
		}
	}

	/**
	 * Modify the service state.
	 *
	 * @param state Union of PlaybackService.STATE_* flags
	 * @return The new state
	 */
	private int updateState(int state)
	{
		state &= ALL_FLAGS;

		if ((state & FLAG_NO_MEDIA) != 0)
			state &= ~FLAG_PLAYING;

		Song song = getSong(0);

		if ((state & FLAG_ERROR) != 0 && (state & FLAG_PLAYING) != 0) {
			state &= ~FLAG_PLAYING;
			String text = getResources().getString(R.string.song_load_failed, song == null ? null : song.path);
			Toast.makeText(this, text, Toast.LENGTH_LONG).show();
		}

		if (song == null && (state & FLAG_PLAYING) != 0)
			return state;
		if (song == null)
			state &= ~FLAG_REPEAT;

		int oldState = mState;
		mState = state;

		if (state != oldState) {
			mHandler.sendMessage(mHandler.obtainMessage(PROCESS_STATE, oldState, state));
			mHandler.sendMessage(mHandler.obtainMessage(BROADCAST_CHANGE, state, 0));
		}

		return state;
	}

	private void processNewState(int oldState, int state)
	{
		int toggled = oldState ^ state;

		if ((toggled & FLAG_PLAYING) != 0) {
			if ((state & FLAG_PLAYING) != 0) {
				if (mMediaPlayerInitialized) {
					synchronized (mMediaPlayer) {
						mMediaPlayer.start();
					}
				}
				if (mNotificationMode != NEVER)
					startForegroundCompat(NOTIFICATION_ID, new SongNotification(getSong(0), true));
			} else {
				if (mMediaPlayerInitialized) {
					synchronized (mMediaPlayer) {
						mMediaPlayer.pause();
					}
				}
				if (mNotificationMode == ALWAYS) {
					stopForegroundCompat(false);
					mNotificationManager.notify(NOTIFICATION_ID, new SongNotification(getSong(0), false));
				} else {
					stopForegroundCompat(true);
				}
			}
		}

		if ((toggled & FLAG_SHUFFLE) != 0) {
			if ((state & FLAG_SHUFFLE) != 0) {
				mTimeline.setShuffle(true);
				Toast.makeText(this, R.string.shuffle_enabling, Toast.LENGTH_LONG).show();
			} else {
				mTimeline.setShuffle(false);
				Toast.makeText(this, R.string.shuffle_disabling, Toast.LENGTH_SHORT).show();
			}
		}

		if ((toggled & FLAG_REPEAT) != 0) {
			if ((state & FLAG_REPEAT) != 0) {
				mTimeline.setRepeat(true);
				Toast.makeText(this, R.string.repeat_enabling, Toast.LENGTH_LONG).show();
			} else {
				mTimeline.setRepeat(false);
				Toast.makeText(this, R.string.repeat_disabling, Toast.LENGTH_SHORT).show();
			}
		}
	}

	private void broadcastChange(int state, Song song, long uptime)
	{
		Intent intent = new Intent(EVENT_CHANGED);
		if (state != -1)
			intent.putExtra("state", state);
		if (song != null)
			intent.putExtra("song", song);
		intent.putExtra("time", uptime);
		ContextApplication.broadcast(intent);

		if (mScrobble)
			scrobble();
	}

	private void scrobble()
	{
		assert(mScrobble == true);

		Song song = getSong(0);
		int state = mState;

		Intent intent = new Intent("net.jjc1138.android.scrobbler.action.MUSIC_STATUS");
		intent.putExtra("playing", (state & FLAG_PLAYING) != 0);
		if (song != null)
			intent.putExtra("id", (int)song.id);
		sendBroadcast(intent);
	}

	private void updateNotification()
	{
		if (mNotificationMode == ALWAYS || mNotificationMode == WHEN_PLAYING && (mState & FLAG_PLAYING) != 0)
			mNotificationManager.notify(NOTIFICATION_ID, new SongNotification(getSong(0), (mState & FLAG_PLAYING) != 0));
		else
			mNotificationManager.cancel(NOTIFICATION_ID);
	}

	/**
	 * Toggle a flag in the state on or off
	 *
	 * @param flag The flag to be toggled
	 * @return The new state
	 */
	public int toggleFlag(int flag)
	{
		int state;
		synchronized (mStateLock) {
			state = updateState(mState ^ flag);
		}
		userActionTriggered();
		return state;
	}

	/**
	 * Move <code>delta</code> places away from the current song.
	 *
	 * @return The new current song
	 */
	private Song setCurrentSong(int delta)
	{
		if (mMediaPlayer == null)
			return null;

		synchronized (mMediaPlayer) {
			if (mMediaPlayer.isPlaying())
				mMediaPlayer.stop();
		}

		Song song = mTimeline.shiftCurrentSong(delta);
		if (song == null) {
			if (Song.isSongAvailable()) {
				return setCurrentSong(+1); // we only encountered a bad song; skip it
			} else {
				setFlag(FLAG_NO_MEDIA); // we don't have any songs : /
				return null;
			}
		} else if ((mState & FLAG_NO_MEDIA) != 0) {
			unsetFlag(FLAG_NO_MEDIA);
		}

		mHandler.removeMessages(PROCESS_SONG);

		mHandler.sendMessage(mHandler.obtainMessage(PROCESS_SONG, song));
		Message msg = mHandler.obtainMessage(BROADCAST_CHANGE, -1, 0);
		msg.obj = song;
		mHandler.sendMessage(msg);
		return song;
	}

	private void processSong(Song song)
	{
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
			updateState(mState & ~FLAG_ERROR);
		} catch (IOException e) {
			// FLAG_PLAYING is set to force showing the Toast. updateState()
			// will unset it.
			updateState(mState | FLAG_PLAYING | FLAG_ERROR);
			Log.e("VanillaMusic", "IOException", e);
		}

		updateNotification();

		getSong(+2);
		mTimeline.purge();
		mHandler.removeMessages(SAVE_STATE);
		mHandler.sendEmptyMessageDelayed(SAVE_STATE, 5000);
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
		return true;
	}

	/**
	 * Returns the song <code>delta</code> places away from the current
	 * position.
	 *
	 * @see SongTimeline#getSong(int)
	 */
	public Song getSong(int delta)
	{
		if (mTimeline == null)
			return null;

		return mTimeline.getSong(delta);
	}

	private void go(int delta, boolean autoPlay)
	{
		if (!mLoaded)
			showStartupToast();

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

	private class Receiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context content, Intent intent)
		{
			String action = intent.getAction();

			if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action)) {
				if (mHeadsetPause)
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
			case TelephonyManager.CALL_STATE_OFFHOOK: {
				MediaButtonHandler buttons = MediaButtonHandler.getInstance();
				if (buttons != null)
					buttons.setInCall(true);

				if (!mPlayingBeforeCall) {
					synchronized (mStateLock) {
						if (mPlayingBeforeCall = (mState & FLAG_PLAYING) != 0)
							unsetFlag(FLAG_PLAYING);
					}
				}
				break;
			}
			case TelephonyManager.CALL_STATE_IDLE: {
				MediaButtonHandler buttons = MediaButtonHandler.getInstance();
				if (buttons != null)
					buttons.setInCall(false);

				if (mPlayingBeforeCall) {
					setFlag(FLAG_PLAYING);
					mPlayingBeforeCall = false;
				}
				break;
			}
		}
		}
	};

	public void onMediaChange()
	{
		if (Song.isSongAvailable()) {
			if ((mState & FLAG_NO_MEDIA) != 0)
				setCurrentSong(0);
		} else {
			setFlag(FLAG_NO_MEDIA);
		}
	}

	public void onSharedPreferenceChanged(SharedPreferences settings, String key)
	{
		loadPreference(key);
	}

	private void setupReceiver()
	{
		if (mReceiver == null)
			mReceiver = new Receiver();
		IntentFilter filter = new IntentFilter();
		filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
		registerReceiver(mReceiver, filter);
	}

	private static final int GO = 0;
	private static final int POST_CREATE = 1;
	private static final int CREATE = 3;
	/**
	 * This message is sent with a delay specified by a user preference. After
	 * this delay, assuming no new IDLE_TIMEOUT messages cancel it, playback
	 * will be stopped.
	 */
	private static final int IDLE_TIMEOUT = 4;
	private static final int TRACK_CHANGED = 5;
	private static final int RELEASE_WAKE_LOCK = 6;
	/**
	 * Decrease the volume gradually over five seconds, pausing when 0 is
	 * reached.
	 *
	 * arg1 should be the progress in the fade as a percentage, 1-100.
	 */
	private static final int FADE_OUT = 7;
	/**
	 * Calls {@link PlaybackService#go(int, boolean)} with the given delta.
	 *
	 * obj should an Integer representing the delta to pass to go.
	 */
	private static final int CALL_GO = 8;
	/**
	 * Broadcast the given intent with ContextApplication.
	 *
	 * obj should contain the intent to broadcast.
	 *
	 * @see ContextApplication#broadcast(Intent)
	 */
	private static final int BROADCAST = 9;
	private static final int BROADCAST_CHANGE = 10;
	private static final int SAVE_STATE = 12;
	private static final int PROCESS_SONG = 13;
	private static final int PROCESS_STATE = 14;

	public boolean handleMessage(Message message)
	{
		switch (message.what) {
		case TRACK_CHANGED:
			setCurrentSong(+1);
			setFlag(FLAG_PLAYING);
			break;
		case RELEASE_WAKE_LOCK:
			if (mWakeLock != null && mWakeLock.isHeld())
				mWakeLock.release();
			break;
		case CALL_GO:
			int delta = (Integer)message.obj;
			go(delta, false);
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
			mTimeline.saveState(this, 0);
			break;
		case PROCESS_SONG:
			processSong((Song)message.obj);
			break;
		case CREATE:
			initialize();
			break;
		case POST_CREATE:
			mHeadsetPause = mSettings.getBoolean("headset_pause", true);
			setupReceiver();

			mCallListener = new InCallListener();
			TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
			telephonyManager.listen(mCallListener, PhoneStateListener.LISTEN_CALL_STATE);
			break;
		case IDLE_TIMEOUT:
			if ((mState & FLAG_PLAYING) != 0)
				mHandler.sendMessage(mHandler.obtainMessage(FADE_OUT, 100, 0));
			break;
		case FADE_OUT:
			int progress = message.arg1 - 1;
			if (progress == 0) {
				unsetFlag(FLAG_PLAYING);
				mCurrentVolume = mUserVolume;
			} else {
				// Approximate an exponential curve with x^4
				// http://www.dr-lex.be/info-stuff/volumecontrols.html
				mCurrentVolume = Math.max((float)(Math.pow(progress / 100f, 4) * mUserVolume), .01f);
				mHandler.sendMessageDelayed(mHandler.obtainMessage(FADE_OUT, progress, 0), 50);
			}
			if (mMediaPlayer != null) {
				synchronized (mMediaPlayer) {
					mMediaPlayer.setVolume(mCurrentVolume, mCurrentVolume);
				}
			}
			break;
		case PROCESS_STATE:
			processNewState(message.arg1, message.arg2);
			break;
		case BROADCAST:
			ContextApplication.broadcast((Intent)message.obj);
			break;
		case BROADCAST_CHANGE:
			broadcastChange(message.arg1, (Song)message.obj, message.getWhen());
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
	 * Returns the position of the current song in the song timeline.
	 *
	 * @see SongTimeline#getCurrentPosition()
	 */
	public int getTimelinePos()
	{
		return mTimeline.getCurrentPosition();
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

	/**
	 * Notify clients that a song in the timeline has been replaced.
	 */
	public void songReplaced(int delta, Song song)
	{
		broadcastReplaceSong(delta, song);
	}

	/**
	 * Remove the song with the given id from the timeline and advance to the
	 * next song if the given song is currently playing.
	 *
	 * @param id The MediaStore id of the song to remove.
	 * @see SongTimeline#removeSong(long)
	 */
	public void removeSong(long id)
	{
		boolean shouldAdvance = mTimeline.removeSong(id);
		if (shouldAdvance)
			setCurrentSong(0);
	}

	/**
	 * Move to the next song in the queue.
	 */
	public Song nextSong()
	{
		Song song = setCurrentSong(+1);
		userActionTriggered();
		return song;
	}

	/**
	 * Move to the previous song in the queue.
	 */
	public Song previousSong()
	{
		Song song = setCurrentSong(-1);
		userActionTriggered();
		return song;
	}

	/**
	 * Resets the idle timeout countdown. Should be called by a user action
	 * has been trigger (new song chosen or playback toggled).
	 *
	 * If an idle fade out is actually in progress, aborts it and resets the
	 * volume.
	 */
	public void userActionTriggered()
	{
		mHandler.removeMessages(FADE_OUT);
		mHandler.removeMessages(IDLE_TIMEOUT);
		if (mIdleTimeout != 0)
			mHandler.sendEmptyMessageDelayed(IDLE_TIMEOUT, mIdleTimeout * 1000);

		if (mCurrentVolume != mUserVolume) {
			mCurrentVolume = mUserVolume;
			mMediaPlayer.setVolume(mCurrentVolume, mCurrentVolume);
		}
	}
}
