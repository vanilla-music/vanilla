package org.kreed.vanilla;

import org.kreed.vanilla.IMusicPlayerWatcher;
import org.kreed.vanilla.IPlaybackService;
import org.kreed.vanilla.R;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;

public class RemoteActivity extends Activity implements ServiceConnection, View.OnClickListener {
	private CoverView mCoverView;

	private View mOpenButton;
	private View mKillButton;
	private View mPreviousButton;
	private ImageButton mPlayPauseButton;
	private View mNextButton;

	@Override
	public void onCreate(Bundle state)
	{
		super.onCreate(state);

		requestWindowFeature(Window.FEATURE_NO_TITLE); 
		setContentView(R.layout.remote_dialog);

		mCoverView = (CoverView)findViewById(R.id.cover_view);

		mOpenButton = findViewById(R.id.open_button);
		mOpenButton.setOnClickListener(this);
		mKillButton = findViewById(R.id.kill_button);
		mKillButton.setOnClickListener(this);
		mPreviousButton = findViewById(R.id.previous);
		mPreviousButton.setOnClickListener(this);
		mPlayPauseButton = (ImageButton)findViewById(R.id.play_pause);
		mPlayPauseButton.setOnClickListener(this);
		mNextButton = findViewById(R.id.next);
		mNextButton.setOnClickListener(this);
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

	public void onServiceConnected(ComponentName name, IBinder binder)
	{
		IPlaybackService service = IPlaybackService.Stub.asInterface(binder);
		mCoverView.setPlaybackService(service);
		try {
			service.registerWatcher(mWatcher);
			setState(service.getState());
		} catch (RemoteException e) {
		}
	}

	public void onServiceDisconnected(ComponentName name)
	{
		reconnect();
	}

	public void onClick(View view)
	{
		if (view == mKillButton) {
			stopService(new Intent(this, PlaybackService.class));
			finish();
		} else if (view == mOpenButton) {
			startActivity(new Intent(this, NowPlayingActivity.class));
			finish();
		} else if (view == mNextButton) {
			mCoverView.nextCover();
		} else if (view == mPreviousButton) {
			mCoverView.previousCover();
		} else if (view == mPlayPauseButton) {
			mCoverView.togglePlayback();
		}
	}

	private void setState(int state)
	{
		if (state == PlaybackService.STATE_NO_MEDIA)
			finish();

		mPlayPauseButton.setImageResource(state == PlaybackService.STATE_PLAYING ? R.drawable.pause : R.drawable.play);
	}

	private IMusicPlayerWatcher mWatcher = new IMusicPlayerWatcher.Stub() {
		public void songChanged(Song playingSong)
		{
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
}