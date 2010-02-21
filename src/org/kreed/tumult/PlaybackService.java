package org.kreed.tumult;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Random;

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
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

public class PlaybackService extends Service implements Runnable, MediaPlayer.OnCompletionListener, SharedPreferences.OnSharedPreferenceChangeListener {	
	private static final int NOTIFICATION_ID = 2;

	public static final int STATE_NORMAL = 0;
	public static final int STATE_NO_MEDIA = 1;
	public static final int STATE_PLAYING = 2;

	private RemoteCallbackList<IMusicPlayerWatcher> mWatchers;
	private NotificationManager mNotificationManager;
	private Method mStartForeground;
	private Method mStopForeground;

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
		mNotificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		try {
			mStartForeground = getClass().getMethod("startForeground", new Class[] { int.class, Notification.class });
			mStopForeground = getClass().getMethod("stopForeground", new Class[] { boolean.class });
		} catch (NoSuchMethodException e) {
			// Running on an older platform.
			mStartForeground = mStopForeground = null;
		}

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
		stopForegroundCompat(NOTIFICATION_ID);

		if (mWakeLock != null && mWakeLock.isHeld())
			mWakeLock.release();
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
			}
		}
	};

	public void onSharedPreferenceChanged(SharedPreferences settings, String key)
	{
		if (mHandler == null)
			return;

		if ("headset_only".equals(key)) {
			int arg = settings.getBoolean(key, false) ? 1 : 0;
			mHandler.sendMessage(mHandler.obtainMessage(HEADSET_PREF_CHANGED, arg, 0));
		} else if ("remote_player".equals(key)) {
			int arg = settings.getBoolean(key, true) ? 1 : 0;
			mHandler.sendMessage(mHandler.obtainMessage(REMOTE_PLAYER_PREF_CHANGED, arg, 0));
		}
	}

	private boolean mHeadsetOnly;
	private boolean mUseRemotePlayer;

	private Handler mHandler;
	private MediaPlayer mMediaPlayer;
	private Random mRandom;
	private PowerManager.WakeLock mWakeLock;

	private int[] mSongs;
	private ArrayList<Song> mSongTimeline;
	private int mCurrentSong = -1;
	private int mQueuePos = 0;
	private boolean mPlugged = true;
	private int mState = STATE_NORMAL;

	private static final int SET_SONG = 0;
	private static final int PLAY_PAUSE = 1;
	private static final int HEADSET_PLUGGED = 2;
	private static final int HEADSET_PREF_CHANGED = 3;
	private static final int QUEUE_ITEM = 4;
	private static final int TRACK_CHANGED = 5;
	private static final int RELEASE_WAKE_LOCK = 6;
	private static final int HANDLE_PLAY = 7;
	private static final int HANDLE_PAUSE = 8;
	private static final int RETRIEVE_SONGS = 9;
	private static final int REMOTE_PLAYER_PREF_CHANGED = 10;

	public void run()
	{
		Looper.prepare();

		mMediaPlayer = new MediaPlayer();
		mSongTimeline = new ArrayList<Song>();
		mRandom = new Random();

		mHandler = new Handler() {
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
					boolean plugged = message.arg1 == 1;
					if (plugged != mPlugged) {
						mPlugged = plugged;
						if (mCurrentSong == -1)
							return;
						if (!plugged && mMediaPlayer.isPlaying())
							setPlaying(false);
					}
					break;
				case HEADSET_PREF_CHANGED:
					mHeadsetOnly = message.arg1 == 1;
					if (mHeadsetOnly && !mPlugged && mMediaPlayer.isPlaying())
						pause();
					break;
				case QUEUE_ITEM:
					if (message.arg1 == -1) {
						mQueuePos = 0;
					} else {
						Song song = new Song(message.arg1);
						Toast.makeText(Tumult.getContext(), "Enqueued " + song.title, Toast.LENGTH_SHORT).show();

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
					startForegroundCompat(NOTIFICATION_ID, createNotification());
					break;
				case HANDLE_PAUSE:
					setState(STATE_NORMAL);
					stopForegroundCompat(0);
					break;
				case RETRIEVE_SONGS:
					retrieveSongs();
					break;
				case REMOTE_PLAYER_PREF_CHANGED:
					mUseRemotePlayer = message.arg1 == 1;
					if (mState == STATE_PLAYING)
						startForegroundCompat(NOTIFICATION_ID, createNotification());
					break;
				}
			}
		};

		IntentFilter filter = new IntentFilter();
		filter.addAction(Intent.ACTION_HEADSET_PLUG);
		filter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
		filter.addAction(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
		registerReceiver(mReceiver, filter);

		PowerManager powerManager = (PowerManager)getSystemService(POWER_SERVICE);
		mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TumultSongChangeLock");

		mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		mMediaPlayer.setWakeMode(this, PowerManager.PARTIAL_WAKE_LOCK);
		mMediaPlayer.setOnCompletionListener(this);
		retrieveSongs();

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		mHeadsetOnly = settings.getBoolean("headset_only", false);
		mUseRemotePlayer = settings.getBoolean("remote_player", true);
		settings.registerOnSharedPreferenceChangeListener(this);

		setCurrentSong(1);

		mNotificationManager.notify(NOTIFICATION_ID, createNotification());

		Looper.loop();
	}


	public void setState(int state)
	{
		if (mState == state)
			return;

		int oldState = mState;
		mState = state;

		int i = mWatchers.beginBroadcast();
		while (--i != -1) {
			try {
				mWatchers.getBroadcastItem(i).stateChanged(oldState, mState);
			} catch (RemoteException e) {
				// Null elements will be removed automatically
			}
		}
		mWatchers.finishBroadcast();
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

	private Notification createNotification()
	{
		Song song = getSong(0);

		RemoteViews views = new RemoteViews(getPackageName(), R.layout.statusbar);
		views.setImageViewResource(R.id.icon, R.drawable.status_icon);
		views.setTextViewText(R.id.title, song.title);
		views.setTextViewText(R.id.artist, song.artist);

		Notification notification = new Notification();
		notification.contentView = views;
		notification.icon = R.drawable.status_icon;
		notification.flags |= Notification.FLAG_ONGOING_EVENT;
		Intent intent = new Intent(this, mUseRemotePlayer ? RemoteActivity.class : NowPlayingActivity.class);
		notification.contentIntent = PendingIntent.getActivity(this, 0, intent, 0);

		return notification;
	}

	private void play()
	{
		if (mHeadsetOnly && !mPlugged)
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

		try {
			mMediaPlayer.reset();
			mMediaPlayer.setDataSource(song.path);
			mMediaPlayer.prepare();
			if (mState == STATE_PLAYING)
				play();
		} catch (IOException e) {
			Log.e("Tumult", "IOException", e);
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
	}

	public void onCompletion(MediaPlayer player)
	{
		mWakeLock.acquire();
		mHandler.sendEmptyMessage(TRACK_CHANGED);
		mHandler.sendEmptyMessage(RELEASE_WAKE_LOCK);
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
}