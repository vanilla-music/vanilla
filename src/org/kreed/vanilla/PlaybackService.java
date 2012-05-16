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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Handles music playback and pretty much all the other work.
 */
public final class PlaybackService extends Service
	implements Handler.Callback
	         , MediaPlayer.OnCompletionListener
	         , MediaPlayer.OnErrorListener
	         , SharedPreferences.OnSharedPreferenceChangeListener
	         , SongTimeline.Callback
	         , SensorEventListener
{
	/**
	 * Name of the state file.
	 */
	private static final String STATE_FILE = "state";
	/**
	 * Header for state file to help indicate if the file is in the right
	 * format.
	 */
	private static final long STATE_FILE_MAGIC = 0x1533574DC74B6ECL;
	/**
	 * State file version that indicates data order.
	 */
	private static final int STATE_VERSION = 6;

	private static final int NOTIFICATION_ID = 2;

	/**
	 * Action for startService: toggle playback on/off.
	 */
	public static final String ACTION_TOGGLE_PLAYBACK = "org.kreed.vanilla.action.TOGGLE_PLAYBACK";
	/**
	 * Action for startService: start playback if paused.
	 */
	public static final String ACTION_PLAY = "org.kreed.vanilla.action.PLAY";
	/**
	 * Action for startService: pause playback if playing.
	 */
	public static final String ACTION_PAUSE = "org.kreed.vanilla.action.PAUSE";
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
	 * Action for startService: toggle playback on/off.
	 *
	 * This works the same way as ACTION_PLAY_PAUSE but prevents the notification
	 * from being hidden regardless of notification visibility settings.
	 */
	public static final String ACTION_TOGGLE_PLAYBACK_NOTIFICATION = "org.kreed.vanilla.action.TOGGLE_PLAYBACK_NOTIFICATION";
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
	 * Change the shuffle mode.
	 */
	public static final String ACTION_CYCLE_SHUFFLE = "org.kreed.vanilla.CYCLE_SHUFFLE";
	/**
	 * Change the repeat mode.
	 */
	public static final String ACTION_CYCLE_REPEAT = "org.kreed.vanilla.CYCLE_REPEAT";
	/**
	 * Pause music and hide the notifcation.
	 */
	public static final String ACTION_CLOSE_NOTIFICATION = "org.kreed.vanilla.CLOSE_NOTIFICATION";

	public static final int NEVER = 0;
	public static final int WHEN_PLAYING = 1;
	public static final int ALWAYS = 2;

	/**
	 * Notification click action: open LaunchActivity.
	 */
	private static final int NOT_ACTION_MAIN_ACTIVITY = 0;
	/**
	 * Notification click action: open MiniPlaybackActivity.
	 */
	private static final int NOT_ACTION_MINI_ACTIVITY = 1;
	/**
	 * Notification click action: skip to next song.
	 */
	private static final int NOT_ACTION_NEXT_SONG = 2;

	/**
	 * If a user action is triggered within this time (in ms) after the
	 * idle time fade-out occurs, playback will be resumed.
	 */
	private static final long IDLE_GRACE_PERIOD = 60000;
	/**
	 * Minimum time in milliseconds between shake actions.
	 */
	private static final int MIN_SHAKE_PERIOD = 500;
	/**
	 * Defer release of mWakeLock for this time (in ms).
	 */
	private static final int WAKE_LOCK_DELAY = 60000;

	/**
	 * If set, music will play.
	 */
	public static final int FLAG_PLAYING = 0x1;
	/**
	 * Set when there is no media available on the device.
	 */
	public static final int FLAG_NO_MEDIA = 0x2;
	/**
	 * Set when the current song is unplayable.
	 */
	public static final int FLAG_ERROR = 0x4;
	/**
	 * Set when the user needs to select songs to play.
	 */
	public static final int FLAG_EMPTY_QUEUE = 0x8;
	public static final int SHIFT_FINISH = 4;
	/**
	 * These two bits will be one of SongTimeline.FINISH_*.
	 */
	public static final int MASK_FINISH = 0x7 << SHIFT_FINISH;
	public static final int SHIFT_SHUFFLE = 7;
	/**
	 * These two bits will be one of SongTimeline.SHUFFLE_*.
	 */
	public static final int MASK_SHUFFLE = 0x3 << SHIFT_SHUFFLE;

	/**
	 * The PlaybackService state, indicating if the service is playing,
	 * repeating, etc.
	 *
	 * The format of this is 0b00000000_00000000_00000000f_feeedcba,
	 * where each bit is:
	 *     a:   {@link PlaybackService#FLAG_PLAYING}
	 *     b:   {@link PlaybackService#FLAG_NO_MEDIA}
	 *     c:   {@link PlaybackService#FLAG_ERROR}
	 *     d:   {@link PlaybackService#FLAG_EMPTY_QUEUE}
	 *     eee: {@link PlaybackService#MASK_FINISH}
	 *     ff:  {@link PlaybackService#MASK_SHUFFLE}
	 */
	int mState;
	/**
	 * Object used for state-related locking.
	 */
	final Object[] mStateLock = new Object[0];
	/**
	 * Object used for PlaybackService startup waiting.
	 */
	private static final Object[] sWait = new Object[0];
	/**
	 * The appplication-wide instance of the PlaybackService.
	 */
	public static PlaybackService sInstance;
	private static final ArrayList<PlaybackActivity> sActivities = new ArrayList<PlaybackActivity>(5);
	/**
	 * Cached app-wide SharedPreferences instance.
	 */
	private static SharedPreferences sSettings;

	boolean mHeadsetPause;
	private boolean mScrobble;
	/**
	 * If true, emulate the music status broadcasts sent by the stock android
	 * music player.
	 */
	private boolean mStockBroadcast;
	private int mNotificationMode;
	/**
	 * If true, audio will not be played through the speaker.
	 */
	private boolean mHeadsetOnly;
	/**
	 * If true, start playing when the headset is plugged in.
	 */
	boolean mHeadsetPlay;
	/**
	 * True if the initial broadcast sent when registering HEADSET_PLUG has
	 * been receieved.
	 */
	boolean mPlugInitialized;
	/**
	 * The time to wait before considering the player idle.
	 */
	private int mIdleTimeout;
	/**
	 * The intent for the notification to execute, created by
	 * {@link PlaybackService#createNotificationAction(SharedPreferences)}.
	 */
	private PendingIntent mNotificationAction;
	/**
	 * Use white text instead of black default text in notification.
	 */
	private boolean mInvertNotification;

	private Looper mLooper;
	private Handler mHandler;
	MediaPlayer mMediaPlayer;
	private boolean mMediaPlayerInitialized;
	private PowerManager.WakeLock mWakeLock;
	private NotificationManager mNotificationManager;
	private AudioManager mAudioManager;
	/**
	 * The SensorManager service.
	 */
	private SensorManager mSensorManager;
	/**
	 * The equalizer wrapper.
	 */
	private CompatEq mEqualizer;

	SongTimeline mTimeline;
	private Song mCurrentSong;

	boolean mPlayingBeforeCall;
	/**
	 * Stores the saved position in the current song from saved state. Should
	 * be seeked to when the song is loaded into MediaPlayer. Used only during
	 * initialization. The song that the saved position is for is stored in
	 * {@link #mPendingSeekSong}.
	 */
	private int mPendingSeek;
	/**
	 * The id of the song that the mPendingSeek position is for. -1 indicates
	 * an invalid song. Value is undefined when mPendingSeek is 0.
	 */
	private long mPendingSeekSong;
	public Receiver mReceiver;
	public InCallListener mCallListener;
	private String mErrorMessage;
	/**
	 * The volume adjustment set in the volume preference.
	 */
	private float mUserVolume;
	/**
	 * The linear scale volume set on the MediaPlayer.
	 */
	private float mBaseVolume;
	/**
	 * If true, the volume is being reduced for the idle fade-out.
	 */
	private boolean mFadeInProgress;
	/**
	 * Elapsed realtime at which playback was paused by idle timeout. -1
	 * indicates that no timeout has occurred.
	 */
	private long mIdleStart = -1;
	/**
	 * True if the last audio focus loss can be ducked.
	 */
	private boolean mDuckedLoss;
	/**
	 * Magnitude of last sensed acceleration.
	 */
	private double mAccelLast;
	/**
	 * Filtered acceleration used for shake detection.
	 */
	private double mAccelFiltered;
	/**
	 * Elapsed realtime of last shake action.
	 */
	private long mLastShakeTime;
	/**
	 * Minimum jerk required for shake.
	 */
	private double mShakeThreshold;
	/**
	 * What to do when an accelerometer shake is detected.
	 */
	private Action mShakeAction;
	/**
	 * If true, the notification should not be hidden when pausing regardless
	 * of user settings.
	 */
	private boolean mForceNotificationVisible;

	@Override
	public void onCreate()
	{
		HandlerThread thread = new HandlerThread("PlaybackService", Process.THREAD_PRIORITY_BACKGROUND);
		thread.start();

		mTimeline = new SongTimeline(this);
		mTimeline.setCallback(this);
		int state = loadState();

		mMediaPlayer = new MediaPlayer();
		mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		mMediaPlayer.setOnCompletionListener(this);
		mMediaPlayer.setOnErrorListener(this);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			try {
				mEqualizer = new CompatEq(mMediaPlayer);
			} catch (IllegalArgumentException e) {
				// equalizer not supported
			}
		}

		mNotificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		mAudioManager = (AudioManager)getSystemService(AUDIO_SERVICE);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
			CompatFroyo.createAudioFocus();
		}

		SharedPreferences settings = getSettings(this);
		settings.registerOnSharedPreferenceChangeListener(this);
		mNotificationMode = Integer.parseInt(settings.getString(PrefKeys.NOTIFICATION_MODE, "1"));
		mScrobble = settings.getBoolean(PrefKeys.SCROBBLE, false);
		mUserVolume = (float)Math.pow(settings.getInt(PrefKeys.VOLUME, 100) / 100.0, 3);
		mIdleTimeout = settings.getBoolean(PrefKeys.USE_IDLE_TIMEOUT, false) ? settings.getInt(PrefKeys.IDLE_TIMEOUT, 3600) : 0;
		Song.mDisableCoverArt = settings.getBoolean(PrefKeys.DISABLE_COVER_ART, false);
		mHeadsetOnly = settings.getBoolean(PrefKeys.HEADSET_ONLY, false);
		mStockBroadcast = settings.getBoolean(PrefKeys.STOCK_BROADCAST, false);
		mHeadsetPlay = settings.getBoolean(PrefKeys.HEADSET_PLAY, false);
		mInvertNotification = settings.getBoolean(PrefKeys.NOTIFICATION_INVERTED_COLOR, false);
		mNotificationAction = createNotificationAction(settings);
		mHeadsetPause = getSettings(this).getBoolean(PrefKeys.HEADSET_PAUSE, true);
		mShakeAction = settings.getBoolean(PrefKeys.ENABLE_SHAKE, false) ? Action.getAction(settings, PrefKeys.SHAKE_ACTION, Action.NextSong) : Action.Nothing;
		mShakeThreshold = settings.getInt(PrefKeys.SHAKE_THRESHOLD, 80) / 10.0f;

		updateVolume();

		PowerManager powerManager = (PowerManager)getSystemService(POWER_SERVICE);
		mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VanillaMusicLock");

		try {
			mCallListener = new InCallListener();
			TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
			telephonyManager.listen(mCallListener, PhoneStateListener.LISTEN_CALL_STATE);
		} catch (SecurityException e) {
			// don't have READ_PHONE_STATE
		}

		mReceiver = new Receiver();
		IntentFilter filter = new IntentFilter();
		filter.addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
		filter.addAction(Intent.ACTION_HEADSET_PLUG);
		filter.addAction(Intent.ACTION_SCREEN_ON);
		registerReceiver(mReceiver, filter);

		getContentResolver().registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mObserver);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			CompatIcs.registerRemote(this, mAudioManager);
		}

		mLooper = thread.getLooper();
		mHandler = new Handler(mLooper, this);

		initWidgets();

		updateState(state);
		setCurrentSong(0);

		sInstance = this;
		synchronized (sWait) {
			sWait.notifyAll();
		}

		mAccelFiltered = 0.0f;
		mAccelLast = SensorManager.GRAVITY_EARTH;
		setupSensor();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId)
	{
		if (intent != null) {
			String action = intent.getAction();

			if (ACTION_TOGGLE_PLAYBACK.equals(action)) {
				playPause();
			} else if (ACTION_TOGGLE_PLAYBACK_NOTIFICATION.equals(action)) {
				mForceNotificationVisible = true;
				synchronized (mStateLock) {
					if ((mState & FLAG_PLAYING) != 0)
						pause();
					else
						play();
				}
			} else if (ACTION_TOGGLE_PLAYBACK_DELAYED.equals(action)) {
				if (mHandler.hasMessages(CALL_GO, Integer.valueOf(0))) {
					mHandler.removeMessages(CALL_GO, Integer.valueOf(0));
					Intent launch = new Intent(this, LibraryActivity.class);
					launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					launch.setAction(Intent.ACTION_MAIN);
					startActivity(launch);
				} else {
					mHandler.sendMessageDelayed(mHandler.obtainMessage(CALL_GO, 0, 0, Integer.valueOf(0)), 400);
				}
			} else if (ACTION_NEXT_SONG.equals(action)) {
				setCurrentSong(1);
				userActionTriggered();
			} else if (ACTION_NEXT_SONG_AUTOPLAY.equals(action)) {
				setCurrentSong(1);
				play();
			} else if (ACTION_NEXT_SONG_DELAYED.equals(action)) {
				if (mHandler.hasMessages(CALL_GO, Integer.valueOf(1))) {
					mHandler.removeMessages(CALL_GO, Integer.valueOf(1));
					Intent launch = new Intent(this, LibraryActivity.class);
					launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					launch.setAction(Intent.ACTION_MAIN);
					startActivity(launch);
				} else {
					mHandler.sendMessageDelayed(mHandler.obtainMessage(CALL_GO, 1, 0, Integer.valueOf(1)), 400);
				}
			} else if (ACTION_PREVIOUS_SONG.equals(action)) {
				setCurrentSong(-1);
				userActionTriggered();
			} else if (ACTION_PREVIOUS_SONG_AUTOPLAY.equals(action)) {
				setCurrentSong(-1);
				play();
			} else if (ACTION_PLAY.equals(action)) {
				play();
			} else if (ACTION_PAUSE.equals(action)) {
				pause();
			} else if (ACTION_CYCLE_REPEAT.equals(action)) {
				cycleFinishAction();
			} else if (ACTION_CYCLE_SHUFFLE.equals(action)) {
				cycleShuffle();
			} else if (ACTION_CLOSE_NOTIFICATION.equals(action)) {
				mForceNotificationVisible = false;
				pause();
				stopForeground(true); // sometimes required to clear notification
				mNotificationManager.cancel(NOTIFICATION_ID);
			}

			MediaButtonReceiver.registerMediaButton(this);
		}

		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy()
	{
		sInstance = null;

		mLooper.quit();

		// clear the notification
		stopForeground(true);

		if (mMediaPlayer != null) {
			saveState(mMediaPlayer.getCurrentPosition());
			mMediaPlayer.release();
			mMediaPlayer = null;
		}

		MediaButtonReceiver.unregisterMediaButton(this);

		try {
			unregisterReceiver(mReceiver);
		} catch (IllegalArgumentException e) {
			// we haven't registered the receiver yet
		}

		if (mSensorManager != null && mShakeAction != Action.Nothing)
			mSensorManager.unregisterListener(this);

		if (mWakeLock != null && mWakeLock.isHeld())
			mWakeLock.release();

		super.onDestroy();
	}

	/**
	 * Return the SharedPreferences instance containing the PlaybackService
	 * settings, creating it if necessary.
	 */
	public static SharedPreferences getSettings(Context context)
	{
		if (sSettings == null)
			sSettings = PreferenceManager.getDefaultSharedPreferences(context);
		return sSettings;
	}

	/**
	 * Setup the accelerometer.
	 */
	private void setupSensor()
	{
		if (mShakeAction == Action.Nothing || (mState & FLAG_PLAYING) == 0) {
			if (mSensorManager != null)
				mSensorManager.unregisterListener(this);
		} else {
			if (mSensorManager == null)
				mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
			mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI);
		}
	}

	/**
	 * Set the volume gain on the MediaPlayer/Equalizer
	 */
	private void updateVolume()
	{
		float base = mUserVolume;

		if (base > 1.0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
			// In Gingerbread and above, MediaPlayer no longer accepts volumes
			// > 1.0. So we use an equalizer instead.
			CompatEq eq = mEqualizer;
			if (eq != null) {
				short gain = (short)(2000 * Math.log10(base));
				for (short i = eq.getNumberOfBands(); --i != -1; ) {
					eq.setBandLevel(i, gain);
				}
			}
			base = 1.0f;
		}

		mBaseVolume = base;
		if (mMediaPlayer != null)
			mMediaPlayer.setVolume(base, base);
	}

	private void loadPreference(String key)
	{
		SharedPreferences settings = getSettings(this);
		if (PrefKeys.HEADSET_PAUSE.equals(key)) {
			mHeadsetPause = settings.getBoolean(PrefKeys.HEADSET_PAUSE, true);
		} else if (PrefKeys.NOTIFICATION_ACTION.equals(key)) {
			mNotificationAction = createNotificationAction(settings);
			updateNotification();
		} else if (PrefKeys.NOTIFICATION_INVERTED_COLOR.equals(key)) {
			mInvertNotification = settings.getBoolean(PrefKeys.NOTIFICATION_INVERTED_COLOR, false);
			updateNotification();
		} else if (PrefKeys.NOTIFICATION_MODE.equals(key)){
			mNotificationMode = Integer.parseInt(settings.getString(PrefKeys.NOTIFICATION_MODE, "1"));
			// This is the only way to remove a notification created by
			// startForeground(), even if we are not currently in foreground
			// mode.
			stopForeground(true);
			updateNotification();
		} else if (PrefKeys.SCROBBLE.equals(key)) {
			mScrobble = settings.getBoolean(PrefKeys.SCROBBLE, false);
		} else if (PrefKeys.VOLUME.equals(key)) {
			mUserVolume = (float)Math.pow(settings.getInt(key, 100) / 100.0, 3);
			updateVolume();
		} else if (PrefKeys.MEDIA_BUTTON.equals(key) || PrefKeys.MEDIA_BUTTON_BEEP.equals(key)) {
			MediaButtonReceiver.reloadPreference(this);
		} else if (PrefKeys.USE_IDLE_TIMEOUT.equals(key) || PrefKeys.IDLE_TIMEOUT.equals(key)) {
			mIdleTimeout = settings.getBoolean(PrefKeys.USE_IDLE_TIMEOUT, false) ? settings.getInt(PrefKeys.IDLE_TIMEOUT, 3600) : 0;
			userActionTriggered();
		} else if (PrefKeys.DISABLE_COVER_ART.equals(key)) {
			Song.mDisableCoverArt = settings.getBoolean(PrefKeys.DISABLE_COVER_ART, false);
		} else if (PrefKeys.NOTIFICATION_INVERTED_COLOR.equals(key)) {
			updateNotification();
		} else if (PrefKeys.HEADSET_ONLY.equals(key)) {
			mHeadsetOnly = settings.getBoolean(key, false);
			if (mHeadsetOnly && isSpeakerOn())
				unsetFlag(FLAG_PLAYING);
		} else if (PrefKeys.STOCK_BROADCAST.equals(key)) {
			mStockBroadcast = settings.getBoolean(key, false);
		} else if (PrefKeys.HEADSET_PLAY.equals(key)) {
			mHeadsetPlay = settings.getBoolean(key, false);
		} else if (PrefKeys.ENABLE_SHAKE.equals(key) || PrefKeys.SHAKE_ACTION.equals(key)) {
			mShakeAction = settings.getBoolean(PrefKeys.ENABLE_SHAKE, false) ? Action.getAction(settings, PrefKeys.SHAKE_ACTION, Action.NextSong) : Action.Nothing;
			setupSensor();
		} else if (PrefKeys.SHAKE_THRESHOLD.equals(key)) {
			mShakeThreshold = settings.getInt(PrefKeys.SHAKE_THRESHOLD, 80) / 10.0f;
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
			CompatFroyo.dataChanged(this);
		}
	}

	/**
	 * Set a state flag.
	 */
	public void setFlag(int flag)
	{
		synchronized (mStateLock) {
			updateState(mState | flag);
		}
	}

	/**
	 * Unset a state flag.
	 */
	public void unsetFlag(int flag)
	{
		synchronized (mStateLock) {
			updateState(mState & ~flag);
		}
	}

	/**
	 * Return true if audio would play through the speaker.
	 */
	@SuppressWarnings("deprecation")
	private boolean isSpeakerOn()
	{
		// Android seems very intent on making this difficult to detect. In
		// Android 1.5, this worked great with AudioManager.getRouting(),
		// which definitively answered if audio would play through the speakers.
		// Android 2.0 deprecated this method and made it no longer function.
		// So this hacky alternative was created. But with Android 4.0,
		// isWiredHeadsetOn() was deprecated, though it still works. But for
		// how much longer?
		//
		// I'd like to remove this feature so I can avoid fighting Android to
		// keep it working, but some users seem to really like it. I think the
		// best solution to this problem is for Android to have separate media
		// volumes for speaker, headphones, etc. That way the speakers can be
		// muted system-wide. There is not much I can do about that here,
		// though.
		return !mAudioManager.isWiredHeadsetOn() && !mAudioManager.isBluetoothA2dpOn() && !mAudioManager.isBluetoothScoOn();
	}

	/**
	 * Modify the service state.
	 *
	 * @param state Union of PlaybackService.STATE_* flags
	 * @return The new state
	 */
	private int updateState(int state)
	{
		if ((state & (FLAG_NO_MEDIA|FLAG_ERROR|FLAG_EMPTY_QUEUE)) != 0 || mHeadsetOnly && isSpeakerOn())
			state &= ~FLAG_PLAYING;

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
				if (mMediaPlayerInitialized)
					mMediaPlayer.start();

				if (mNotificationMode != NEVER)
					startForeground(NOTIFICATION_ID, createNotification(mCurrentSong, mState));

				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO) {
					CompatFroyo.requestAudioFocus(mAudioManager);
				}

				mHandler.removeMessages(RELEASE_WAKE_LOCK);
				try {
					if (mWakeLock != null)
						mWakeLock.acquire();
				} catch (SecurityException e) {
					// Don't have WAKE_LOCK permission
				}
			} else {
				if (mMediaPlayerInitialized)
					mMediaPlayer.pause();

				if (mNotificationMode == ALWAYS || mForceNotificationVisible) {
					stopForeground(false);
					mNotificationManager.notify(NOTIFICATION_ID, createNotification(mCurrentSong, mState));
				} else {
					stopForeground(true);
				}

				// Delay release of the wake lock. This allows the headset
				// button to continue to function for a short period after
				// pausing.
				mHandler.sendEmptyMessageDelayed(RELEASE_WAKE_LOCK, WAKE_LOCK_DELAY);
			}

			setupSensor();
		}

		if ((toggled & FLAG_NO_MEDIA) != 0 && (state & FLAG_NO_MEDIA) != 0) {
			Song song = mCurrentSong;
			if (song != null && mMediaPlayerInitialized) {
				mPendingSeek = mMediaPlayer.getCurrentPosition();
				mPendingSeekSong = song.id;
			}
		}

		if ((toggled & MASK_SHUFFLE) != 0)
			mTimeline.setShuffleMode(shuffleMode(state));
		if ((toggled & MASK_FINISH) != 0)
			mTimeline.setFinishAction(finishAction(state));
	}

	private void broadcastChange(int state, Song song, long uptime)
	{
		if (state != -1) {
			ArrayList<PlaybackActivity> list = sActivities;
			for (int i = list.size(); --i != -1; )
				list.get(i).setState(uptime, state);
		}

		if (song != null) {
			ArrayList<PlaybackActivity> list = sActivities;
			for (int i = list.size(); --i != -1; )
				list.get(i).setSong(uptime, song);
		}

		updateWidgets();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			CompatIcs.updateRemote(this, mCurrentSong, mState);
		}

		if (mStockBroadcast)
			stockMusicBroadcast();
		if (mScrobble)
			scrobble();
	}

	/**
	 * Check if there are any instances of each widget.
	 */
	private void initWidgets()
	{
		AppWidgetManager manager = AppWidgetManager.getInstance(this);
		OneCellWidget.checkEnabled(this, manager);
		FourSquareWidget.checkEnabled(this, manager);
		FourLongWidget.checkEnabled(this, manager);
		FourWhiteWidget.checkEnabled(this, manager);
		WidgetD.checkEnabled(this, manager);
		WidgetE.checkEnabled(this, manager);
	}

	/**
	 * Update the widgets with the current song and state.
	 */
	private void updateWidgets()
	{
		AppWidgetManager manager = AppWidgetManager.getInstance(this);
		Song song = mCurrentSong;
		int state = mState;
		OneCellWidget.updateWidget(this, manager, song, state);
		FourLongWidget.updateWidget(this, manager, song, state);
		FourSquareWidget.updateWidget(this, manager, song, state);
		FourWhiteWidget.updateWidget(this, manager, song, state);
		WidgetD.updateWidget(this, manager, song, state);
		WidgetE.updateWidget(this, manager, song, state);
	}

	/**
	 * Send a broadcast emulating that of the stock music player.
	 */
	private void stockMusicBroadcast()
	{
		Song song = mCurrentSong;
		Intent intent = new Intent("com.android.music.playstatechanged");
		intent.putExtra("playing", (mState & FLAG_PLAYING) != 0);
		if (song != null) {
			intent.putExtra("track", song.title);
			intent.putExtra("album", song.album);
			intent.putExtra("artist", song.artist);
			intent.putExtra("songid", song.id);
			intent.putExtra("albumid", song.albumId);
		}
		sendBroadcast(intent);
	}

	private void scrobble()
	{
		Song song = mCurrentSong;
		Intent intent = new Intent("net.jjc1138.android.scrobbler.action.MUSIC_STATUS");
		intent.putExtra("playing", (mState & FLAG_PLAYING) != 0);
		if (song != null)
			intent.putExtra("id", (int)song.id);
		sendBroadcast(intent);
	}

	private void updateNotification()
	{
		if ((mForceNotificationVisible || mNotificationMode == ALWAYS || mNotificationMode == WHEN_PLAYING && (mState & FLAG_PLAYING) != 0) && mCurrentSong != null)
			mNotificationManager.notify(NOTIFICATION_ID, createNotification(mCurrentSong, mState));
		else
			mNotificationManager.cancel(NOTIFICATION_ID);
	}

	/**
	 * Start playing if currently paused.
	 *
	 * @return The new state after this is called.
	 */
	public int play()
	{
		synchronized (mStateLock) {
			if ((mState & FLAG_EMPTY_QUEUE) != 0) {
				setFinishAction(SongTimeline.FINISH_RANDOM);
				setCurrentSong(0);
				Toast.makeText(this, R.string.random_enabling, Toast.LENGTH_SHORT).show();
			}

			int state = updateState(mState | FLAG_PLAYING);
			userActionTriggered();
			return state;
		}
	}

	/**
	 * Pause if currently playing.
	 *
	 * @return The new state after this is called.
	 */
	public int pause()
	{
		synchronized (mStateLock) {
			int state = updateState(mState & ~FLAG_PLAYING);
			userActionTriggered();
			return state;
		}
	}

	/**
	 * If playing, pause. If paused, play.
	 *
	 * @return The new state after this is called.
	 */
	public int playPause()
	{
		mForceNotificationVisible = false;
		synchronized (mStateLock) {
			if ((mState & FLAG_PLAYING) != 0)
				return pause();
			else
				return play();
		}
	}


	/**
	 * Change the end action (e.g. repeat, random).
	 *
	 * @param action The new action. One of SongTimeline.FINISH_*.
	 * @return The new state after this is called.
	 */
	public int setFinishAction(int action)
	{
		synchronized (mStateLock) {
			return updateState(mState & ~MASK_FINISH | action << SHIFT_FINISH);
		}
	}

	/**
	 * Cycle repeat mode. Disables random mode.
	 *
	 * @return The new state after this is called.
	 */
	public int cycleFinishAction()
	{
		synchronized (mStateLock) {
			int mode = finishAction(mState) + 1;
			if (mode > SongTimeline.FINISH_RANDOM)
				mode = SongTimeline.FINISH_STOP;
			return setFinishAction(mode);
		}
	}

	/**
	 * Change the shuffle mode.
	 *
	 * @param mode The new mode. One of SongTimeline.SHUFFLE_*.
	 * @return The new state after this is called.
	 */
	public int setShuffleMode(int mode)
	{
		synchronized (mStateLock) {
			return updateState(mState & ~MASK_SHUFFLE | mode << SHIFT_SHUFFLE);
		}
	}

	/**
	 * Cycle shuffle mode.
	 *
	 * @return The new state after this is called.
	 */
	public int cycleShuffle()
	{
		synchronized (mStateLock) {
			int mode = shuffleMode(mState) + 1;
			if (mode > SongTimeline.SHUFFLE_ALBUMS)
				mode = SongTimeline.SHUFFLE_NONE;
			return setShuffleMode(mode);
		}
	}

	/**
	 * Move to the next or previous song or album in the timeline.
	 *
	 * @param delta One of SongTimeline.SHIFT_*. 0 can also be passed to
	 * initialize the current song with media player, notification,
	 * broadcasts, etc.
	 * @return The new current song
	 */
	private Song setCurrentSong(int delta)
	{
		if (mMediaPlayer == null)
			return null;

		if (mMediaPlayer.isPlaying())
			mMediaPlayer.stop();

		Song song;
		if (delta == 0)
			song = mTimeline.getSong(0);
		else
			song = mTimeline.shiftCurrentSong(delta);
		mCurrentSong = song;
		if (song == null || song.id == -1 || song.path == null) {
			if (MediaUtils.isSongAvailable(getContentResolver())) {
				int flag = finishAction(mState) == SongTimeline.FINISH_RANDOM ? FLAG_ERROR : FLAG_EMPTY_QUEUE;
				synchronized (mStateLock) {
					updateState((mState | flag) & ~FLAG_NO_MEDIA);
				}
				return null;
			} else {
				// we don't have any songs : /
				synchronized (mStateLock) {
					updateState((mState | FLAG_NO_MEDIA) & ~FLAG_EMPTY_QUEUE);
				}
				return null;
			}
		} else if ((mState & (FLAG_NO_MEDIA|FLAG_EMPTY_QUEUE)) != 0) {
			synchronized (mStateLock) {
				updateState(mState & ~(FLAG_EMPTY_QUEUE|FLAG_NO_MEDIA));
			}
		}

		mHandler.removeMessages(PROCESS_SONG);

		mMediaPlayerInitialized = false;
		mHandler.sendMessage(mHandler.obtainMessage(PROCESS_SONG, song));
		mHandler.sendMessage(mHandler.obtainMessage(BROADCAST_CHANGE, -1, 0, song));
		return song;
	}

	private void processSong(Song song)
	{
		try {
			mMediaPlayerInitialized = false;
			mMediaPlayer.reset();
			mMediaPlayer.setDataSource(song.path);
			mMediaPlayer.prepare();
			mMediaPlayerInitialized = true;

			if (mPendingSeek != 0 && mPendingSeekSong == song.id) {
				mMediaPlayer.seekTo(mPendingSeek);
				mPendingSeek = 0;
			}

			if ((mState & FLAG_PLAYING) != 0)
				mMediaPlayer.start();

			if ((mState & FLAG_ERROR) != 0) {
				mErrorMessage = null;
				updateState(mState & ~FLAG_ERROR);
			}
		} catch (IOException e) {
			mErrorMessage = getResources().getString(R.string.song_load_failed, song.path);
			updateState(mState | FLAG_ERROR);
			Toast.makeText(this, mErrorMessage, Toast.LENGTH_LONG).show();
			Log.e("VanillaMusic", "IOException", e);
		}

		updateNotification();

		mTimeline.purge();
	}

	@Override
	public void onCompletion(MediaPlayer player)
	{
		if (finishAction(mState) == SongTimeline.FINISH_REPEAT_CURRENT) {
			setCurrentSong(0);
		} else if (finishAction(mState) == SongTimeline.FINISH_STOP_CURRENT) {
			unsetFlag(FLAG_PLAYING);
			setCurrentSong(+1);
		} else if (mTimeline.isEndOfQueue()) {
			unsetFlag(FLAG_PLAYING);
		} else {
			setCurrentSong(+1);
		}
	}

	@Override
	public boolean onError(MediaPlayer player, int what, int extra)
	{
		Log.e("VanillaMusic", "MediaPlayer error: " + what + ' ' + extra);
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
		if (delta == 0)
			return mCurrentSong;
		return mTimeline.getSong(delta);
	}

	private class Receiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context content, Intent intent)
		{
			String action = intent.getAction();

			if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(action)) {
				if (mHeadsetPause)
					unsetFlag(FLAG_PLAYING);
			} else if (Intent.ACTION_HEADSET_PLUG.equals(action)) {
				if (mHeadsetPlay && mPlugInitialized && intent.getIntExtra("state", 0) == 1)
					setFlag(FLAG_PLAYING);
				else if (!mPlugInitialized)
					mPlugInitialized = true;
			} else if (Intent.ACTION_SCREEN_ON.equals(action)) {
				userActionTriggered();
			}
		}
	}

	private class InCallListener extends PhoneStateListener {
		@Override
		public void onCallStateChanged(int state, String incomingNumber)
		{
			switch (state) {
			case TelephonyManager.CALL_STATE_RINGING:
			case TelephonyManager.CALL_STATE_OFFHOOK: {
				MediaButtonReceiver.setInCall(true);

				if (!mPlayingBeforeCall) {
					synchronized (mStateLock) {
						if (mPlayingBeforeCall = (mState & FLAG_PLAYING) != 0)
							unsetFlag(FLAG_PLAYING);
					}
				}
				break;
			}
			case TelephonyManager.CALL_STATE_IDLE: {
				MediaButtonReceiver.setInCall(false);

				if (mPlayingBeforeCall) {
					setFlag(FLAG_PLAYING);
					mPlayingBeforeCall = false;
				}
				break;
			}
		}
		}
	}

	public void onMediaChange()
	{
		if (MediaUtils.isSongAvailable(getContentResolver())) {
			if ((mState & FLAG_NO_MEDIA) != 0)
				setCurrentSong(0);
		} else {
			setFlag(FLAG_NO_MEDIA);
		}

		ArrayList<PlaybackActivity> list = sActivities;
		for (int i = list.size(); --i != -1; )
			list.get(i).onMediaChange();

	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences settings, String key)
	{
		loadPreference(key);
	}

	/**
	 * Calls {@link PowerManager.WakeLock#release()} on mWakeLock.
	 */
	private static final int RELEASE_WAKE_LOCK = 1;
	/**
	 * Run the given query and add the results to the timeline.
	 *
	 * obj is the QueryTask. arg1 is the add mode (one of SongTimeline.MODE_*)
	 */
	private static final int QUERY = 2;
	/**
	 * This message is sent with a delay specified by a user preference. After
	 * this delay, assuming no new IDLE_TIMEOUT messages cancel it, playback
	 * will be stopped.
	 */
	private static final int IDLE_TIMEOUT = 4;
	/**
	 * Decrease the volume gradually over five seconds, pausing when 0 is
	 * reached.
	 *
	 * arg1 should be the progress in the fade as a percentage, 1-100.
	 */
	private static final int FADE_OUT = 7;
	/**
	 * If arg1 is 0, calls {@link PlaybackService#playPause()}.
	 * Otherwise, calls {@link PlaybackService#setCurrentSong(int)} with arg1.
	 */
	private static final int CALL_GO = 8;
	private static final int BROADCAST_CHANGE = 10;
	private static final int SAVE_STATE = 12;
	private static final int PROCESS_SONG = 13;
	private static final int PROCESS_STATE = 14;

	@Override
	public boolean handleMessage(Message message)
	{
		switch (message.what) {
		case CALL_GO:
			if (message.arg1 == 0)
				playPause();
			else
				setCurrentSong(message.arg1);
			break;
		case SAVE_STATE:
			// For unexpected terminations: crashes, task killers, etc.
			// In most cases onDestroy will handle this
			saveState(0);
			break;
		case PROCESS_SONG:
			processSong((Song)message.obj);
			break;
		case QUERY:
			runQuery((QueryTask)message.obj);
			break;
		case IDLE_TIMEOUT:
			if ((mState & FLAG_PLAYING) != 0) {
				mHandler.sendMessage(mHandler.obtainMessage(FADE_OUT, 100, 0));
				mFadeInProgress = true;
			}
			break;
		case FADE_OUT: {
			int progress = message.arg1 - 1;
			float volume;
			if (progress == 0) {
				mIdleStart = SystemClock.elapsedRealtime();
				unsetFlag(FLAG_PLAYING);
				volume = mBaseVolume;
				mFadeInProgress = false;
			} else {
				// Approximate an exponential curve with x^4
				// http://www.dr-lex.be/info-stuff/volumecontrols.html
				volume = Math.max((float)(Math.pow(progress / 100f, 4) * mBaseVolume), .01f);
				mHandler.sendMessageDelayed(mHandler.obtainMessage(FADE_OUT, progress, 0), 50);
			}
			if (mMediaPlayer != null)
				mMediaPlayer.setVolume(volume, volume);
			break;
		}
		case PROCESS_STATE:
			processNewState(message.arg1, message.arg2);
			break;
		case BROADCAST_CHANGE:
			broadcastChange(message.arg1, (Song)message.obj, message.getWhen());
			break;
		case RELEASE_WAKE_LOCK:
			if (mWakeLock != null && mWakeLock.isHeld())
				mWakeLock.release();
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
		if (!mMediaPlayerInitialized)
			return 0;
		return mMediaPlayer.getCurrentPosition();
	}

	/**
	 * Seek to a position in the current song.
	 *
	 * @param progress Proportion of song completed (where 1000 is the end of the song)
	 */
	public void seekToProgress(int progress)
	{
		if (!mMediaPlayerInitialized)
			return;
		long position = (long)mMediaPlayer.getDuration() * progress / 1000;
		mMediaPlayer.seekTo((int)position);
	}

	@Override
	public IBinder onBind(Intent intents)
	{
		return null;
	}

	@Override
	public void activeSongReplaced(int delta, Song song)
	{
		ArrayList<PlaybackActivity> list = sActivities;
		for (int i = list.size(); --i != -1; )
			list.get(i).replaceSong(delta, song);

		if (delta == 0)
			setCurrentSong(0);
	}

	/**
	 * Delete all the songs in the given media set. Should be run on a
	 * background thread.
	 *
	 * @param type One of the TYPE_* constants, excluding playlists.
	 * @param id The MediaStore id of the media to delete.
	 * @return The number of songs deleted.
	 */
	public int deleteMedia(int type, long id)
	{
		int count = 0;

		ContentResolver resolver = getContentResolver();
		String[] projection = new String [] { MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA };
		Cursor cursor = MediaUtils.buildQuery(type, id, projection, null).runQuery(resolver);

		if (cursor != null) {
			while (cursor.moveToNext()) {
				if (new File(cursor.getString(1)).delete()) {
					long songId = cursor.getLong(0);
					String where = MediaStore.Audio.Media._ID + '=' + songId;
					resolver.delete(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, where, null);
					mTimeline.removeSong(songId);
					++count;
				}
			}

			cursor.close();
		}

		return count;
	}

	/**
	 * Move to next or previous song or album in the queue.
	 *
	 * @param delta One of SongTimeline.SHIFT_*.
	 * @return The new current song.
	 */
	public Song shiftCurrentSong(int delta)
	{
		Song song = setCurrentSong(delta);
		userActionTriggered();
		return song;
	}

	/**
	 * Resets the idle timeout countdown. Should be called by a user action
	 * has been triggered (new song chosen or playback toggled).
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

		if (mFadeInProgress) {
			mMediaPlayer.setVolume(mBaseVolume, mBaseVolume);
			mFadeInProgress = false;
		}

		long idleStart = mIdleStart;
		if (idleStart != -1 && SystemClock.elapsedRealtime() - idleStart < IDLE_GRACE_PERIOD) {
			mIdleStart = -1;
			setFlag(FLAG_PLAYING);
		}
	}

	/**
	 * Run the query and add the results to the timeline. Should be called in the
	 * worker thread.
	 *
	 * @param query The query to run.
	 */
	public void runQuery(QueryTask query)
	{
		int count = mTimeline.addSongs(this, query);

		int text;

		switch (query.mode) {
		case SongTimeline.MODE_PLAY:
		case SongTimeline.MODE_PLAY_POS_FIRST:
		case SongTimeline.MODE_PLAY_ID_FIRST:
			text = R.plurals.playing;
			if (count != 0 && (mState & FLAG_PLAYING) == 0)
				setFlag(FLAG_PLAYING);
			break;
		case SongTimeline.MODE_PLAY_NEXT:
		case SongTimeline.MODE_ENQUEUE:
		case SongTimeline.MODE_ENQUEUE_ID_FIRST:
		case SongTimeline.MODE_ENQUEUE_POS_FIRST:
			text = R.plurals.enqueued;
			break;
		default:
			throw new IllegalArgumentException("Invalid add mode: " + query.mode);
		}

		Toast.makeText(this, getResources().getQuantityString(text, count, count), Toast.LENGTH_SHORT).show();
	}

	/**
	 * Run the query in the background and add the results to the timeline.
	 *
	 * @param query The query.
	 */
	public void addSongs(QueryTask query)
	{
		mHandler.sendMessage(mHandler.obtainMessage(QUERY, query));
	}

	/**
	 * Enqueues all the songs with the same album/artist/genre as the current
	 * song.
	 *
	 * This will clear the queue and place the first song from the group after
	 * the playing song.
	 *
	 * @param type The media type, one of MediaUtils.TYPE_ALBUM, TYPE_ARTIST,
	 * or TYPE_GENRE
	 */
	public void enqueueFromCurrent(int type)
	{
		Song current = mCurrentSong;
		if (current == null)
			return;

		long id;
		switch (type) {
		case MediaUtils.TYPE_ARTIST:
			id = current.artistId;
			break;
		case MediaUtils.TYPE_ALBUM:
			id = current.albumId;
			break;
		case MediaUtils.TYPE_GENRE:
			id = MediaUtils.queryGenreForSong(getContentResolver(), current.id);
			break;
		default:
			throw new IllegalArgumentException("Unsupported media type: " + type);
		}

		String selection = "_id!=" + current.id;
		QueryTask query = MediaUtils.buildQuery(type, id, Song.FILLED_PROJECTION, selection);
		query.mode = SongTimeline.MODE_PLAY_NEXT;
		addSongs(query);
	}

	/**
	 * Clear the song queue.
	 */
	public void clearQueue()
	{
		mTimeline.clearQueue();
	}

	/**
	 * Return the error message set when FLAG_ERROR is set.
	 */
	public String getErrorMessage()
	{
		return mErrorMessage;
	}

	@Override
	public void timelineChanged()
	{
		mHandler.removeMessages(SAVE_STATE);
		mHandler.sendEmptyMessageDelayed(SAVE_STATE, 5000);
	}

	@Override
	public void positionInfoChanged()
	{
		ArrayList<PlaybackActivity> list = sActivities;
		for (int i = list.size(); --i != -1; )
			list.get(i).onPositionInfoChanged();
	}

	private final ContentObserver mObserver = new ContentObserver(null) {
		@Override
		public void onChange(boolean selfChange)
		{
			MediaUtils.onMediaChange();
			onMediaChange();
		}
	};

	/**
	 * Return the PlaybackService instance, creating one if needed.
	 */
	public static PlaybackService get(Context context)
	{
		if (sInstance == null) {
			context.startService(new Intent(context, PlaybackService.class));

			while (sInstance == null) {
				try {
					synchronized (sWait) {
						sWait.wait();
					}
				} catch (InterruptedException ignored) {
				}
			}
		}

		return sInstance;
	}

	/**
	 * Returns true if a PlaybackService instance is active.
	 */
	public static boolean hasInstance()
	{
		return sInstance != null;
	}

	/**
	 * Add an Activity to the registered PlaybackActivities.
	 *
	 * @param activity The Activity to be added
	 */
	public static void addActivity(PlaybackActivity activity)
	{
		sActivities.add(activity);
	}

	/**
	 * Remove an Activity from the registered PlaybackActivities
	 *
	 * @param activity The Activity to be removed
	 */
	public static void removeActivity(PlaybackActivity activity)
	{
		sActivities.remove(activity);
	}

	/**
	 * Initializes the service state, loading songs saved from the disk into the
	 * song timeline.
	 *
	 * @return The loaded value for mState.
	 */
	public int loadState()
	{
		int state = 0;

		try {
			DataInputStream in = new DataInputStream(openFileInput(STATE_FILE));

			if (in.readLong() == STATE_FILE_MAGIC && in.readInt() == STATE_VERSION) {
				mPendingSeek = in.readInt();
				mPendingSeekSong = in.readLong();
				mTimeline.readState(in);
				state |= mTimeline.getShuffleMode() << SHIFT_SHUFFLE;
				state |= mTimeline.getFinishAction() << SHIFT_FINISH;
			}

			in.close();
		} catch (EOFException e) {
			Log.w("VanillaMusic", "Failed to load state", e);
		} catch (IOException e) {
			Log.w("VanillaMusic", "Failed to load state", e);
		}

		return state;
	}

	/**
	 * Save the service state to disk.
	 *
	 * @param pendingSeek The pendingSeek to store. Should be the current
	 * MediaPlayer position or 0.
	 */
	public void saveState(int pendingSeek)
	{
		try {
			DataOutputStream out = new DataOutputStream(openFileOutput(STATE_FILE, 0));
			Song song = mCurrentSong;
			out.writeLong(STATE_FILE_MAGIC);
			out.writeInt(STATE_VERSION);
			out.writeInt(pendingSeek);
			out.writeLong(song == null ? -1 : song.id);
			mTimeline.writeState(out);
			out.close();
		} catch (IOException e) {
			Log.w("VanillaMusic", "Failed to save state", e);
		}
	}

	/**
	 * Returns the shuffle mode for the given state.
	 *
	 * @param state The PlaybackService state to process.
	 * @return The shuffle mode. One of SongTimeline.SHUFFLE_*.
	 */
	public static int shuffleMode(int state)
	{
		return (state & MASK_SHUFFLE) >> SHIFT_SHUFFLE;
	}

	/**
	 * Returns the finish action for the given state.
	 *
	 * @param state The PlaybackService state to process.
	 * @return The finish action. One of SongTimeline.FINISH_*.
	 */
	public static int finishAction(int state)
	{
		return (state & MASK_FINISH) >> SHIFT_FINISH;
	}

	/**
	 * Create a PendingIntent for use with the notification.
	 *
	 * @param prefs Where to load the action preference from.
	 */
	public PendingIntent createNotificationAction(SharedPreferences prefs)
	{
		switch (Integer.parseInt(prefs.getString(PrefKeys.NOTIFICATION_ACTION, "0"))) {
		case NOT_ACTION_NEXT_SONG: {
			Intent intent = new Intent(this, PlaybackService.class);
			intent.setAction(PlaybackService.ACTION_NEXT_SONG_AUTOPLAY);
			return PendingIntent.getService(this, 0, intent, 0);
		}
		case NOT_ACTION_MINI_ACTIVITY: {
			Intent intent = new Intent(this, MiniPlaybackActivity.class);
			return PendingIntent.getActivity(this, 0, intent, 0);
		}
		default:
			Log.w("VanillaMusic", "Unknown value for notification_action. Defaulting to 0.");
			// fall through
		case NOT_ACTION_MAIN_ACTIVITY: {
			Intent intent = new Intent(this, LibraryActivity.class);
			intent.setAction(Intent.ACTION_MAIN);
			return PendingIntent.getActivity(this, 0, intent, 0);
		}
		}
	}

	/**
	 * Create a song notification. Call through the NotificationManager to
	 * display it.
	 *
	 * @param song The Song to display information about.
	 * @param state The state. Determines whether to show paused or playing icon.
	 */
	public Notification createNotification(Song song, int state)
	{
		boolean playing = (state & FLAG_PLAYING) != 0;

		RemoteViews views = new RemoteViews(getPackageName(), R.layout.notification);

		Bitmap cover = song.getCover(this);
		if (cover == null) {
			views.setImageViewResource(R.id.cover, R.drawable.fallback_cover);
		} else {
			views.setImageViewBitmap(R.id.cover, cover);
		}

		String title = song.title;

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			int playButton = playing ? R.drawable.pause : R.drawable.play;
			views.setImageViewResource(R.id.play_pause, playButton);

			ComponentName service = new ComponentName(this, PlaybackService.class);

			Intent playPause = new Intent(PlaybackService.ACTION_TOGGLE_PLAYBACK_NOTIFICATION);
			playPause.setComponent(service);
			views.setOnClickPendingIntent(R.id.play_pause, PendingIntent.getService(this, 0, playPause, 0));

			Intent next = new Intent(PlaybackService.ACTION_NEXT_SONG);
			next.setComponent(service);
			views.setOnClickPendingIntent(R.id.next, PendingIntent.getService(this, 0, next, 0));

			Intent close = new Intent(PlaybackService.ACTION_CLOSE_NOTIFICATION);
			close.setComponent(service);
			views.setOnClickPendingIntent(R.id.close, PendingIntent.getService(this, 0, close, 0));
		} else if (!playing) {
			title = getResources().getString(R.string.notification_title_paused, song.title);
		}

		views.setTextViewText(R.id.title, title);
		views.setTextViewText(R.id.artist, song.artist);

		if (mInvertNotification) {
			views.setTextColor(R.id.title, 0xffffffff);
			views.setTextColor(R.id.artist, 0xffffffff);
		}

		Notification notification = new Notification();
		notification.contentView = views;
		notification.icon = R.drawable.status_icon;
		notification.flags |= Notification.FLAG_ONGOING_EVENT;
		notification.contentIntent = mNotificationAction;
		return notification;
	}

	public void onAudioFocusChange(int type)
	{
		Log.d("VanillaMusic", "audio focus change: " + type);
		switch (type) {
		case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
			mDuckedLoss = (mState & FLAG_PLAYING) != 0;
			unsetFlag(FLAG_PLAYING);
			break;
		case AudioManager.AUDIOFOCUS_LOSS:
		case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
			mDuckedLoss = false;
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
				// On Honeycomb and above we have controls in the notification.
				// Ensure they are shown when music is paused from focus loss
				// so music can easily be started again if desired.
				mForceNotificationVisible = true;
			}
			unsetFlag(FLAG_PLAYING);
			break;
		case AudioManager.AUDIOFOCUS_GAIN:
			if (mDuckedLoss) {
				mDuckedLoss = false;
				setFlag(FLAG_PLAYING);
			}
			break;
		}
	}

	@Override
	public void onSensorChanged(SensorEvent se)
	{
		double x = se.values[0];
		double y = se.values[1];
		double z = se.values[2];

		double accel = Math.sqrt(x*x + y*y + z*z);
		double delta = accel - mAccelLast;
		mAccelLast = accel;

		double filtered = mAccelFiltered * 0.9f + delta;
		mAccelFiltered = filtered;

		if (filtered > mShakeThreshold) {
			long now = SystemClock.elapsedRealtime();
			if (now - mLastShakeTime > MIN_SHAKE_PERIOD) {
				mLastShakeTime = now;
				performAction(mShakeAction, null);
			}
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy)
	{
	}

	/**
	 * Execute the given action.
	 *
	 * @param action The action to execute.
	 * @param receiver Optional. If non-null, update the PlaybackActivity with
	 * new song or state from the executed action. The activity will still be
	 * updated by the broadcast if not passed here; passing it just makes the
	 * update immediate.
	 */
	public void performAction(Action action, PlaybackActivity receiver)
	{
		switch (action) {
		case Nothing:
			break;
		case Library:
			Intent intent = new Intent(this, LibraryActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
			break;
		case PlayPause: {
			int state = playPause();
			if (receiver != null)
				receiver.setState(state);
			break;
		}
		case NextSong: {
			Song song = shiftCurrentSong(SongTimeline.SHIFT_NEXT_SONG);
			if (receiver != null)
				receiver.setSong(song);
			break;
		}
		case PreviousSong: {
			Song song = shiftCurrentSong(SongTimeline.SHIFT_PREVIOUS_SONG);
			if (receiver != null)
				receiver.setSong(song);
			break;
		}
		case NextAlbum: {
			Song song = shiftCurrentSong(SongTimeline.SHIFT_NEXT_ALBUM);
			if (receiver != null)
				receiver.setSong(song);
			break;
		}
		case PreviousAlbum: {
			Song song = shiftCurrentSong(SongTimeline.SHIFT_PREVIOUS_ALBUM);
			if (receiver != null)
				receiver.setSong(song);
			break;
		}
		case Repeat: {
			int state = cycleFinishAction();
			if (receiver != null)
				receiver.setState(state);
			break;
		}
		case Shuffle: {
			int state = cycleShuffle();
			if (receiver != null)
				receiver.setState(state);
			break;
		}
		case EnqueueAlbum:
			enqueueFromCurrent(MediaUtils.TYPE_ALBUM);
			break;
		case EnqueueArtist:
			enqueueFromCurrent(MediaUtils.TYPE_ARTIST);
			break;
		case EnqueueGenre:
			enqueueFromCurrent(MediaUtils.TYPE_GENRE);
			break;
		case ClearQueue:
			clearQueue();
			Toast.makeText(this, R.string.queue_cleared, Toast.LENGTH_SHORT).show();
			break;
		case ToggleControls:
			// Handled in FullPlaybackActivity.performAction
			break;
		default:
			throw new IllegalArgumentException("Invalid action: " + action);
		}
	}

	/**
	 * Returns the position of the current song in the song timeline.
	 */
	public int getTimelinePosition()
	{
		return mTimeline.getPosition();
	}

	/**
	 * Returns the number of songs in the song timeline.
	 */
	public int getTimelineLength()
	{
		return mTimeline.getLength();
	}
}
