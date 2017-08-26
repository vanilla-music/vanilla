/*
 * Copyright (C) 2015 Adrian Ulrich <adrian@blinkenlights.ch>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>. 
 */


package ch.blinkenlights.android.vanilla;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.Equalizer;
import android.net.Uri;
import android.os.Build;

import java.io.FileInputStream;
import java.io.IOException;


import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.decoder.DecoderCounters;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.audio.AudioRendererEventListener;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.source.ExtractorMediaSource;

import android.util.Log;

public class VanillaMediaPlayer extends SimpleExoPlayer implements AudioRendererEventListener {

	private Context mContext;
	private String mDataSource;
	private boolean mHasNextMediaPlayer;
	private float mReplayGain = Float.NaN;
	private float mDuckingFactor = Float.NaN;
	private boolean mIsDucking = false;

	private int mAudioSessionId = C.AUDIO_SESSION_ID_UNSET;

	/**
	 * Constructs a new VanillaMediaPlayer class
	 */
	public VanillaMediaPlayer(Context context, DefaultTrackSelector trackSelector, DefaultLoadControl loadControl) {
		super(context, trackSelector, loadControl, null, 0, 0); // fixme: is 0 ok here ?
		mContext = context;
		setAudioStreamType(C.STREAM_TYPE_MUSIC);
		setAudioDebugListener(this);
	}

	public boolean isPlaying() {
		return getPlaybackState() == STATE_READY;
	}

	/**
	 * Resets the media player to an unconfigured state
	 */
	public void reset() {
		mDataSource = null;
		mHasNextMediaPlayer = false;
		stop();
	}

	/**
	 * Releases the media player and frees any claimed AudioEffect
	 */
	public void release() {
		mDataSource = null;
		mHasNextMediaPlayer = false;
		super.release();
	}

	/**
	 * Sets the data source to use
	 */
	public void setDataSource(String path) throws IOException, IllegalArgumentException, SecurityException, IllegalStateException {
		// The MediaPlayer function expects a file:// like string but also accepts *most* absolute unix paths (= paths with no colon)
		// We could therefore encode the path into a full URI, but a much quicker way is to simply use
		// setDataSource(FileDescriptor) as the framework code would end up calling this function anyways (MediaPlayer.java:1100 (6.0))

		DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(mContext, "VanillaMusic");
		DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
		ExtractorMediaSource mediaSource = new ExtractorMediaSource(Uri.parse("file://"+path), dataSourceFactory, extractorsFactory, null, null);

		prepare(mediaSource);
		mDataSource = path;
	}

	/**
	 * Returns the configured data source, may be null
	 */
	public String getDataSource() {
		return mDataSource;
	}

	/**
	 * Sets the next media player data source
	 */
	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	public void setNextMediaPlayer(VanillaMediaPlayer next) {
//		super.setNextMediaPlayer(next);
//		mHasNextMediaPlayer = (next != null);
	}

	/**
	 * Returns true if a 'next' media player has been configured
	 * via setNextMediaPlayer(next)
	 */
	public boolean hasNextMediaPlayer() {
		return mHasNextMediaPlayer;
	}

	/**
	 * Creates a new AudioEffect for our AudioSession
	 */
	private void openAudioFx() {
	Log.v("VanillaMusic", "OPEN audioFX "+mAudioSessionId);
		int id = mAudioSessionId;
		if (id == C.AUDIO_SESSION_ID_UNSET)
			return;

		Intent i = new Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION);
		i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, id);
		i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, mContext.getPackageName());
		mContext.sendBroadcast(i);
	}

	/**
	 * Releases a previously claimed audio session id
	 */
	public void closeAudioFx() {
	Log.v("VanillaMusic", "CLOSE audioFX "+mAudioSessionId);
		int id = mAudioSessionId;
		if (id == C.AUDIO_SESSION_ID_UNSET)
			return;

		Intent i = new Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
		i.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, id);
		i.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, mContext.getPackageName());
		mContext.sendBroadcast(i);
	}

	/**
	 * Sets the desired scaling due to replay gain.
	 * @param replayGain the factor to adjust the volume by. Must be between 0 and 1 (inclusive)
	 *                    or {@link Float#NaN} to disable replay gain scaling
	 */
	public void setReplayGain(float replayGain) {
		mReplayGain = replayGain;
		updateVolume();
	}

	/**
	 * Sets whether we are ducking or not. Ducking is when we temporarily decrease the volume for
	 * a transient sound to play from another application, such as a notification's beep.
	 * @param isDucking true if we are ducking, false if we are not
	 */
	public void setIsDucking(boolean isDucking) {
		mIsDucking = isDucking;
		updateVolume();
	}

	/**
	 * Sets the desired scaling while ducking.
	 * @param duckingFactor the factor to adjust the volume by while ducking. Must be between 0
	 *                         and 1 (inclusive) or {@link Float#NaN} to disable ducking completely
	 *
	 * See also {@link #setIsDucking(boolean)}
	 */
	public void setDuckingFactor(float duckingFactor) {
		mDuckingFactor = duckingFactor;
		updateVolume();
	}
	/**
	 * Sets the volume, using the replay gain and ducking if appropriate
	 */
	private void updateVolume() {
		float volume = 1.0f;
		if (!Float.isNaN(mReplayGain)) {
			volume = mReplayGain;
		}
		if(mIsDucking && !Float.isNaN(mDuckingFactor)) {
			volume *= mDuckingFactor;
		}

		setVolume(volume);
	}


	////// AudioRendererEventListener
	@Override
	public void onAudioDisabled(DecoderCounters counters) {
		Log.v("VanillaMusic", "onAudioDisabled");
	}

	@Override
	public void onAudioEnabled(DecoderCounters counters) {
	Log.v("VanillaMusic", "onAudioEnabled");
	}

	@Override
	public void onAudioTrackUnderrun(int bufferSize, long bufferSizeMs, long elapsedSinceLastFeedMs) {
	Log.v("VanillaMusic", "track underrun!");
	}

	@Override
	public void onAudioInputFormatChanged(Format format) {
	Log.v("VanillaMusic", "format change!");
	}

	@Override
	public void onAudioDecoderInitialized(String decoderName, long initializedTimestampMs, long initializationDurationMs) {
	Log.v("VanillaMusic", "onAudioDecoderInitialized!");
	}

	@Override
	public void onAudioSessionId(int audioSessionId) {
		//mAudioSessionId = audioSessionId;
		Log.v("VanillaMusic", "Audio session id changed to: "+audioSessionId);
	}
}
