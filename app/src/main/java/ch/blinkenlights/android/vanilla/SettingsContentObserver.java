package ch.blinkenlights.android.vanilla;

import android.content.Context;
import android.database.ContentObserver;
import android.media.AudioManager;
import android.os.Handler;
import android.util.Log;

public class SettingsContentObserver extends ContentObserver {
	private float volumeLimit = 1.0f;
	private int previousVolume;
	private Context context;

	public SettingsContentObserver(Context c, Handler handler) {
		super(handler);
		context=c;

		AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
		previousVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
	}

	public int GetCurrentVolume()
	{
		return previousVolume;
	}

	public void SetCurrentVolume(int newVolume)
	{
		AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
		audio.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0);
	}

	public void SetVolumeLimit(float newVolumeLimit)
	{
		volumeLimit = newVolumeLimit / 100.0f;

		AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
		int currentVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
		int maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		if(volumeLimit < 1.0f && currentVolume > (maxVolume * volumeLimit))
		{
			int desiredValue = (int) (currentVolume < (maxVolume * volumeLimit) ? currentVolume : maxVolume * volumeLimit);
			SetCurrentVolume(desiredValue);
		}
	}

	@Override
	public boolean deliverSelfNotifications() {
		return super.deliverSelfNotifications();
	}

	@Override
	public void onChange(boolean selfChange) {
		super.onChange(selfChange);

		AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
		int currentVolume = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
		int minVolume = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC);
		int maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);

		int delta=previousVolume-currentVolume;

		if(delta>0)
		{
			Log.d("VanillaMusic", "Decreased");
			previousVolume=currentVolume;
		}
		else if(delta<0)
		{
			Log.d("VanillaMusic", "Increased");
			previousVolume=currentVolume;
		}

		if(volumeLimit < 1.0f && currentVolume > (maxVolume * volumeLimit))
		{
			int desiredValue = (int) (currentVolume < (maxVolume * volumeLimit) ? currentVolume : maxVolume * volumeLimit);
			SetCurrentVolume(desiredValue);
		}
	}
}
