package org.kreed.tumult;

import org.kreed.tumult.CoverView.CoverViewWatcher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.TextView;

public class NowPlayingActivity extends Activity implements CoverViewWatcher, ServiceConnection {
	private IPlaybackService mService;
	private CoverView mCoverView;
	private LinearLayout mMessageBox;

	private static final int MENU_PREVIOUS = 0;
	private static final int MENU_NEXT = 1;
	private static final int MENU_PREFS = 2;
	private static final int MENU_QUEUE = 3;

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);

		mCoverView = new CoverView(this);
		mCoverView.setCoverSwapListener(this);;
		setContentView(mCoverView);
//		Bundle extras = getIntent().getExtras();
	}
	
	public void setState(int state)
	{
		switch (state) {
		case MusicPlayer.STATE_NORMAL:
			setContentView(mCoverView);
			mMessageBox = null;
			break;
		case MusicPlayer.STATE_NO_MEDIA:
			mMessageBox = new LinearLayout(this);
			mMessageBox.setGravity(Gravity.CENTER);
			TextView text = new TextView(this);
			text.setText("No songs found on your device.");
			text.setGravity(Gravity.CENTER);
			LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
			layoutParams.gravity = Gravity.CENTER;
			text.setLayoutParams(layoutParams);
			mMessageBox.addView(text);
			setContentView(mMessageBox);
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
	
	public void togglePlayback()
	{
		try {
			mService.togglePlayback();
		} catch (RemoteException e) {
		}
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		menu.add(0, MENU_PREVIOUS, 0, "Previous");
		menu.add(0, MENU_NEXT, 0, "Next");
		menu.add(0, MENU_PREFS, 0, "Preferences");
		menu.add(0, MENU_QUEUE, 0, "Add to Queue");
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(final MenuItem item)
	{
		new Thread(new Runnable() {
			public void run()
			{
				switch (item.getItemId()) {
				case MENU_PREVIOUS:
					previous();
					break;
				case MENU_NEXT:
					next();
					break;
				case MENU_PREFS:
					startActivity(new Intent(NowPlayingActivity.this, PreferencesActivity.class));
					break;
				case MENU_QUEUE:
					onSearchRequested();
					break;
				}
			}
		}).start();

		return true;
	}
	
	@Override
	public boolean onSearchRequested()
	{
		startActivity(new Intent(this, SongSelector.class));
		return false;
	}
}