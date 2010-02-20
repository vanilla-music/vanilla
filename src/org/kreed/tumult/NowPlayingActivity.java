package org.kreed.tumult;

import org.kreed.tumult.CoverView.CoverViewWatcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

public class NowPlayingActivity extends Activity implements CoverViewWatcher, ServiceConnection, View.OnClickListener, SeekBar.OnSeekBarChangeListener, View.OnFocusChangeListener {
	private IPlaybackService mService;
	
	private ViewGroup mLayout;
	private CoverView mCoverView;
	private LinearLayout mMessageBox;
	private View mControlsTop;
	private View mControlsBottom;

	private ImageButton mPreviousButton;
	private ImageButton mPlayPauseButton;
	private ImageButton mNextButton;
	private SeekBar mSeekBar;
	private TextView mSeekText;

	private int mState;
	private long mStartTime;
	private int mDuration;
	private boolean mSeekBarTracking;

	private static final int MENU_PREFS = 2;
	private static final int MENU_QUEUE = 3;

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);

		setContentView(R.layout.nowplaying);

		mCoverView = (CoverView)findViewById(R.id.cover_view);
		mCoverView.setWatcher(this);
		
		mLayout = (ViewGroup)mCoverView.getParent();
		
		mControlsTop = findViewById(R.id.controls_top);
		mControlsBottom = findViewById(R.id.controls_bottom);
		
		mPreviousButton = (ImageButton)findViewById(R.id.previous);
		mPreviousButton.setOnClickListener(this);
		mPreviousButton.setOnFocusChangeListener(this);
		mPlayPauseButton = (ImageButton)findViewById(R.id.play_pause);
		mPlayPauseButton.setOnClickListener(this);
		mPlayPauseButton.setOnFocusChangeListener(this);
		mNextButton = (ImageButton)findViewById(R.id.next);
		mNextButton.setOnClickListener(this);
		mNextButton.setOnFocusChangeListener(this);
		
		mSeekText = (TextView)findViewById(R.id.seek_text);
		mSeekBar = (SeekBar)findViewById(R.id.seek_bar);
		mSeekBar.setMax(1000);
		mSeekBar.setOnSeekBarChangeListener(this);
		mSeekBar.setOnFocusChangeListener(this);
	}
	
	public void setState(int state)
	{
		mState = state;

		switch (state) {
		case MusicPlayer.STATE_NORMAL:
		case MusicPlayer.STATE_PLAYING:
			if (mMessageBox != null) {
				mLayout.removeView(mMessageBox);
				mMessageBox = null;
			}
			mPlayPauseButton.setImageResource(state == MusicPlayer.STATE_PLAYING ? R.drawable.pause : R.drawable.play);
			break;
		case MusicPlayer.STATE_NO_MEDIA:
			mMessageBox = new LinearLayout(this);
			mMessageBox.setGravity(Gravity.CENTER);
			TextView text = new TextView(this);
			text.setText("No songs found on your device.");
			text.setGravity(Gravity.CENTER);
			LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT);
			layoutParams.gravity = Gravity.CENTER;
			text.setLayoutParams(layoutParams);
			mMessageBox.addView(text);
			mLayout.addView(mMessageBox);
			break;
		}
	}
	
	@Override
	public void onResume()
	{
		super.onResume();

		reconnect();
	}

	private void reconnect()
	{
		Intent intent = new Intent(this, PlaybackService.class);
		startService(intent);
		bindService(intent, this, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onPause()
	{
		super.onPause();

		unbindService(this);
	}

	private void refreshSongs()
	{
		try {
			mCoverView.setSongs(mService.getCurrentSongs());
		} catch (RemoteException e) {
			Log.e("Tumult", "RemoteException", e);
		}
	}

	public void onServiceConnected(ComponentName name, IBinder service)
	{
		mService = IPlaybackService.Stub.asInterface(service);
		try {
			mService.registerWatcher(mWatcher);
			refreshSongs();
			setState(mService.getState());
		} catch (RemoteException e) {
			Log.i("Tumult", "Failed to initialize connection to playback service", e);
		}
	}

	public void onServiceDisconnected(ComponentName name)
	{
		mService = null;
		reconnect();
	}

	private IMusicPlayerWatcher mWatcher = new IMusicPlayerWatcher.Stub() {
		public void nextSong(final Song playingSong, final Song forwardSong)
		{
			if (mCoverView.mSongs[1] != null && mCoverView.mSongs[1].path.equals(playingSong.path)) {
				mCoverView.setForwardSong(forwardSong);
			} else {
				runOnUiThread(new Runnable() {
					public void run()
					{
						refreshSongs();
					}
				});
			}
		}

		public void previousSong(final Song playingSong, final Song backwardSong)
		{
			if (mCoverView.mSongs[1] != null && mCoverView.mSongs[1].path.equals(playingSong.path)) {
				mCoverView.setBackwardSong(backwardSong);
			} else {
				runOnUiThread(new Runnable() {
					public void run()
					{
						refreshSongs();
					}
				});
			}
		}

		public void stateChanged(final int oldState, final int newState)
		{
			runOnUiThread(new Runnable() {
				public void run()
				{
					setState(newState);					
				}
			});
		}

		public void mediaLengthChanged(long startTime, int duration)
		{
			mStartTime = startTime;
			mDuration = duration;
		}
	};

	public void next()
	{
		mCoverView.setForwardSong(null);
		try {
			mService.nextSong();
		} catch (RemoteException e) {
		}
	}

	public void previous()
	{
		mCoverView.setBackwardSong(null);
		try {
			mService.previousSong();
		} catch (RemoteException e) {
		}
	}
	
	private void togglePlayback()
	{
		try {
			mService.togglePlayback();
		} catch (RemoteException e) {
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		menu.add(0, MENU_PREFS, 0, "Preferences");
		menu.add(0, MENU_QUEUE, 0, "Add to Queue");
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		switch (item.getItemId()) {
		case MENU_PREFS:
			startActivity(new Intent(this, PreferencesActivity.class));
			break;
		case MENU_QUEUE:
			onSearchRequested();
			break;
		}

		return true;
	}

	@Override
	public boolean onSearchRequested()
	{
		startActivity(new Intent(this, SongSelector.class));
		return false;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event)
	{
		switch (keyCode) {
		case KeyEvent.KEYCODE_DPAD_CENTER:
		case KeyEvent.KEYCODE_ENTER:
			clicked();
			return true;
		}

		return false;
	}

	public void clicked()
	{
		mControlsTop.setVisibility(View.VISIBLE);
		mControlsBottom.setVisibility(View.VISIBLE);
		
		if (mStartTime == 0) {
			try {
				mStartTime = mService.getStartTime();
				mDuration = mService.getDuration();
			} catch (RemoteException e) {
				return;
			}
		}
		
		updateProgress();
		sendHideMessage();
	}
	
	private String stringForTime(int ms)
	{
		int seconds = ms / 1000;

		int hours = seconds / 3600;
		seconds -= hours * 3600;
		int minutes = seconds / 60;
		seconds -= minutes * 60;

		if (hours > 0)
			return String.format("%d:%02d:%02d", hours, minutes, seconds);
		else
			return String.format("%02d:%02d", minutes, seconds);
	}

	private void updateProgress()
	{
		if (mState != MusicPlayer.STATE_PLAYING || mControlsTop.getVisibility() != View.VISIBLE)
			return;
		
		long position = System.currentTimeMillis() - mStartTime;
		if (!mSeekBarTracking)
			mSeekBar.setProgress((int)(1000 * position / mDuration));
		mSeekText.setText(stringForTime((int)position) + " / " + stringForTime(mDuration));
		
		long next = 1000 - position % 1000;
		mHandler.sendMessageDelayed(mHandler.obtainMessage(UPDATE_PROGRESS), next);
	}
	
	private void sendHideMessage()
	{
		Message message = mHandler.obtainMessage(HIDE);
		mHandler.removeMessages(HIDE);
		mHandler.sendMessageDelayed(message, 3000);
	}

	public void onClick(View view)
	{
		sendHideMessage();

		if (view == mNextButton) {
			next();
		} else if (view == mPreviousButton) {
			previous();
		} else if (view == mPlayPauseButton) {
			togglePlayback();
		}
	}
	
	private static final int HIDE = 0;
	private static final int UPDATE_PROGRESS = 1;

	private Handler mHandler = new Handler() {
		public void handleMessage(Message message) {
			switch (message.what) {
			case HIDE:
				mControlsTop.setVisibility(View.GONE);
				mControlsBottom.setVisibility(View.GONE);
				break;
			case UPDATE_PROGRESS:
				updateProgress();
				break;
			}
		}
	};

	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
	{
		if (fromUser) {
			try {
				mService.seekToProgress(progress);
			} catch (RemoteException e) {
			}
		}
	}

	public void onStartTrackingTouch(SeekBar seekBar)
	{
		mHandler.removeMessages(HIDE);
		mSeekBarTracking = true;
	}

	public void onStopTrackingTouch(SeekBar seekBar)
	{
		sendHideMessage();
		mSeekBarTracking = false;
	}

	public void onFocusChange(View v, boolean hasFocus)
	{
		if (hasFocus)
			sendHideMessage();
	}
}