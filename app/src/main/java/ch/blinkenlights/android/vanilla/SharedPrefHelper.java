package ch.blinkenlights.android.vanilla;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public final class SharedPrefHelper {

	/**
	 * Cached app-wide SharedPreferences instance.
	 */
	private static SharedPreferences sSettings;

	private SharedPrefHelper(){};

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


}
