package org.kreed.tumult;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.preference.PreferenceManager;

public class MusicPlayer implements Runnable, MediaPlayer.OnCompletionListener, SharedPreferences.OnSharedPreferenceChangeListener {
	private static final int NOTIFICATION_ID = 2;
	
	public static final int STATE_NORMAL = 0;
	public static final int STATE_NO_MEDIA = 1;
	public static final int STATE_PLAYING = 2;

	public IPlaybackService.Stub mBinder = new IPlaybackService.Stub() {
		public Song[] getCurrentSongs()
		{
			return new Song[] { getSong(-1), getSong(0), getSong(1) };
		}
		
		public int getState()
		{
			return mState;
		}
		
		public long getStartTime()
		{
			if (mMediaPlayer == null)
				return 0;
			return MusicPlayer.this.getStartTime();
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

		public void seekToProgress(int progress)
		{
			if (mMediaPlayer == null || !mMediaPlayer.isPlaying())
				return;
			
			long position = (long)mMediaPlayer.getDuration() * progress / 1000;
			mMediaPlayer.seekTo((int)position);
			mediaLengthChanged();
		}
	};
	
	public void queueSong(int songId)
	{
		if (mHandler == null)
			return;
		mHandler.sendMessage(mHandler.obtainMessage(QUEUE_ITEM, ITEM_SONG, songId));
	}

	public void stopQueueing()
	{
		if (mHandler == null)
			return;
		mHandler.sendMessage(mHandler.obtainMessage(QUEUE_ITEM, ITEM_RESET, 0));
	}
	
	private PlaybackService mService;
	private RemoteCallbackList<IMusicPlayerWatcher> mWatchers;
	
	private boolean mHeadsetOnly = true;
	
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

	public MusicPlayer(PlaybackService service)
	{
		mService = service;		
		mWatchers = new RemoteCallbackList<IMusicPlayerWatcher>();
		mSongTimeline = new ArrayList<Song>();

		new Thread(this).start();
	}

	private static final int SET_SONG = 0;
	private static final int PLAY_PAUSE = 1;
	private static final int HEADSET_PLUGGED = 2;
	private static final int HEADSET_PREF_CHANGED = 3;
	private static final int QUEUE_ITEM = 4;
	private static final int TRACK_CHANGED = 5;
	private static final int RELEASE_WAKE_LOCK = 6;

	private static final int ITEM_SONG = 0;
	private static final int ITEM_RESET = 1;
	
	public void run()
	{
		Looper.prepare();

		mMediaPlayer = new MediaPlayer();
		mRandom = new Random();

		mHandler = new Handler() {
			public void handleMessage(Message message)
			{
				switch (message.what) {
				case SET_SONG:
					setCurrentSong(message.arg1);
					break;
				case PLAY_PAUSE:
					if (mCurrentSong == -1) {
						setCurrentSong(+1);
						return;
					}
			
					setPlaying(!mMediaPlayer.isPlaying());
					break;
				case HEADSET_PLUGGED:
					boolean plugged = message.arg1 == 1;
					if (plugged != mPlugged) {
						mPlugged = plugged;
						if (mCurrentSong == -1 || mPlugged == mMediaPlayer.isPlaying())
							return;
						setPlaying(mPlugged);
					}
					break;
				case HEADSET_PREF_CHANGED:
					mHeadsetOnly = message.arg1 == 1;
					if (mHeadsetOnly && !mPlugged && mMediaPlayer.isPlaying())
						pause();
					break;
				case QUEUE_ITEM:
					switch (message.arg1) {
					case ITEM_SONG:
						int i = mCurrentSong + 1 + mQueuePos++;
						Song song = new Song(message.arg2);
						Toast.makeText(Tumult.getContext(), "Enqueued " + song.title, Toast.LENGTH_SHORT).show();

						if (i < mSongTimeline.size())
							mSongTimeline.set(i, song);
						else
							mSongTimeline.add(song);
						break;
					case ITEM_RESET:
						mQueuePos = 0;
						break;
					}
					break;
				case TRACK_CHANGED:
					setCurrentSong(+1);
					break;
				case RELEASE_WAKE_LOCK:
					if (mWakeLock.isHeld())
						mWakeLock.release();
					break;
				}
			}
		};

		mService.registerReceiver(mReceiver, new IntentFilter(Intent.ACTION_HEADSET_PLUG));

		PowerManager powerManager = (PowerManager)mService.getSystemService(Context.POWER_SERVICE);
		mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TumultSongChangeLock");
		
		mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		mMediaPlayer.setWakeMode(mService, PowerManager.PARTIAL_WAKE_LOCK);
		mMediaPlayer.setOnCompletionListener(this);
		retrieveSongs();
		
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(mService);
		mHeadsetOnly = settings.getBoolean("headset_only", false);
		settings.registerOnSharedPreferenceChangeListener(this);
		
		mHandler.post(new Runnable() {
			public void run() 
			{
				setCurrentSong(1);
			}
		});
		
		Looper.loop();
	}

	public void release()
	{
		if (mMediaPlayer != null) {
			pause();
			mMediaPlayer.release();
			mMediaPlayer = null;
		}

		if (mWakeLock != null && mWakeLock.isHeld())
			mWakeLock.release();
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
		if (mSongs == null && mState == STATE_NORMAL)
			setState(STATE_NO_MEDIA);
	}

	private void play()
	{
		if (mHeadsetOnly && !mPlugged)
			return;

		mMediaPlayer.start();

		Song song = getSong(0);

		RemoteViews views = new RemoteViews(mService.getPackageName(), R.layout.statusbar);
		views.setImageViewResource(R.id.icon, R.drawable.status_icon);
		views.setTextViewText(R.id.title, song.title);
		views.setTextViewText(R.id.artist, song.artist);

		Notification notification = new Notification();
		notification.contentView = views;
		notification.icon = R.drawable.status_icon;
		notification.flags |= Notification.FLAG_ONGOING_EVENT;
		Intent intent = new Intent(mService, NowPlayingActivity.class);
		notification.contentIntent = PendingIntent.getActivity(mService, 0, intent, 0);

		mService.startForegroundCompat(NOTIFICATION_ID, notification);
		
		setState(STATE_PLAYING);
	}
	
	private void pause()
	{
		mMediaPlayer.pause();
		mService.stopForegroundCompat(NOTIFICATION_ID);
		setState(STATE_NORMAL);
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

		Song newSong = getSong(delta);
		int i = mWatchers.beginBroadcast();
		while (--i != -1) {
			try {
				if (delta < 0)
					mWatchers.getBroadcastItem(i).previousSong(song, newSong);
				else
					mWatchers.getBroadcastItem(i).nextSong(song, newSong);
			} catch (RemoteException e) {
				// Null elements will be removed automatically
			}
		}
		mWatchers.finishBroadcast();

		try {
			mMediaPlayer.reset();
			mMediaPlayer.setDataSource(song.path);
			mMediaPlayer.prepare();
			play();
		} catch (IOException e) {
			Log.e("Tumult", "IOException", e);
		}

		mediaLengthChanged();

		getSong(+2);

		while (mCurrentSong > 15) {
			mSongTimeline.remove(0);
			--mCurrentSong;
		}
	}
	
	private long getStartTime()
	{
		int position = mMediaPlayer.getCurrentPosition();
		return System.currentTimeMillis() - position;	
	}

	private void mediaLengthChanged()
	{
		long start = getStartTime();
		int duration = mMediaPlayer.getDuration();

		int i = mWatchers.beginBroadcast();
		while (--i != -1) {
			try {
				mWatchers.getBroadcastItem(i).mediaLengthChanged(start, duration);
			} catch (RemoteException e) {
				// Null elements will be removed automatically
			}
		}
		mWatchers.finishBroadcast();
	}

	public void onCompletion(MediaPlayer player)
	{
		mWakeLock.acquire(15000);
		mHandler.sendEmptyMessage(TRACK_CHANGED);
		mHandler.sendEmptyMessage(RELEASE_WAKE_LOCK);
	}

	private Song randomSong()
	{
		return new Song(mSongs[mRandom.nextInt(mSongs.length)]);
	}

	private synchronized Song getSong(int delta)
	{
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

	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context content, Intent intent)
		{
			if (intent.getAction().equals(Intent.ACTION_HEADSET_PLUG) && mHandler != null) {
				int plugged = intent.getIntExtra("state", 0) == 130 ? 1 : 0;
				mHandler.sendMessage(mHandler.obtainMessage(HEADSET_PLUGGED, plugged, 0));
			}
		}
	};

	public void onSharedPreferenceChanged(SharedPreferences settings, String key)
	{
		if ("headset_only".equals(key) && mHandler != null) {
			int arg = settings.getBoolean(key, false) ? 1 : 0;
			mHandler.sendMessage(mHandler.obtainMessage(HEADSET_PREF_CHANGED, arg, 0));
		}
	}
}
