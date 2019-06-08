/*
 * Copyright (C) 2012-2015 Adrian Ulrich <adrian@blinkenlights.ch>
 * Copyright (C) 2012 Christopher Eby <kreed@kreed.org>
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

package ch.blinkenlights.android.vanilla;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceFragment;
import android.preference.CheckBoxPreference;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewFragment;
import android.content.Context;
import android.content.Intent;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.util.TypedValue;
import java.util.ArrayList;
import java.util.List;

/**
 * The preferences activity in which one can change application preferences.
 */
public class PreferencesActivity extends PreferenceActivity
	implements SharedPreferences.OnSharedPreferenceChangeListener
{

	/**
	 * The package name of our external helper app
	 */
	private static final String VPLUG_PACKAGE_NAME = "ch.blinkenlights.android.vanillaplug";

	/**
	 * Initialize the activity, loading the preference specifications.
	 */
	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		ThemeHelper.setTheme(this, R.style.BackActionBar);
		super.onCreate(savedInstanceState);
		SharedPrefHelper.getSettings(this).registerOnSharedPreferenceChangeListener(this);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		SharedPrefHelper.getSettings(this).unregisterOnSharedPreferenceChangeListener(this);
	}

	@Override
	public void onBuildHeaders(List<Header> target)
	{
		ArrayList<Header> tmp = new ArrayList<Header>();
		loadHeadersFromResource(R.xml.preference_headers, tmp);

		for(Header obj : tmp) {
			// Themes are 5.x only, so do not add PreferencesTheme on holo devices
			if (!ThemeHelper.usesHoloTheme() || !obj.fragment.equals(PreferencesTheme.class.getName()))
				target.add(obj);
		}
	}

	@Override
	public void onSharedPreferenceChanged (SharedPreferences sharedPreferences, String key) {
		if (PrefKeys.SELECTED_THEME.equals(key)) {
			// this gets called by all preference instances: we force them to redraw
			// themselfes if the theme changed
			recreate();
		}
	}

	public static class AudioFragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.preference_audio);
		}
	}

	public static class ReplayGainFragment extends PreferenceFragment {
		CheckBoxPreference cbTrackReplayGain;
		CheckBoxPreference cbAlbumReplayGain;
		SeekBarPreference sbGainBump;
		SeekBarPreference sbUntaggedDebump;

		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);

			addPreferencesFromResource(R.xml.preference_replaygain);
			cbTrackReplayGain = (CheckBoxPreference)findPreference(PrefKeys.ENABLE_TRACK_REPLAYGAIN);
			cbAlbumReplayGain = (CheckBoxPreference)findPreference(PrefKeys.ENABLE_ALBUM_REPLAYGAIN);
			sbGainBump = (SeekBarPreference)findPreference(PrefKeys.REPLAYGAIN_BUMP);
			sbUntaggedDebump = (SeekBarPreference)findPreference(PrefKeys.REPLAYGAIN_UNTAGGED_DEBUMP);

			Preference.OnPreferenceClickListener pcListener = new Preference.OnPreferenceClickListener() {
				public boolean onPreferenceClick(Preference preference) {
					updateConfigWidgets();
					return false;
				}
			};
			
			cbTrackReplayGain.setOnPreferenceClickListener(pcListener);
			cbAlbumReplayGain.setOnPreferenceClickListener(pcListener);
			updateConfigWidgets();
		}

		private void updateConfigWidgets() {
			boolean rgOn = (cbTrackReplayGain.isChecked() || cbAlbumReplayGain.isChecked());
			sbGainBump.setEnabled(rgOn);
			sbUntaggedDebump.setEnabled(rgOn);
		}
	}

	public static class EqualizerFragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);

			Context context = getActivity();
			int mAudioSession = 0;
			if (PlaybackService.hasInstance()) {
				PlaybackService service = PlaybackService.get(context);
				mAudioSession = service.getAudioSession();
			}

			try {
				final Intent effects = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
				effects.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName());
				effects.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, mAudioSession);
				startActivityForResult(effects, 0);
			} catch (Exception e) {
				// ignored. Whee!
			}

			FragmentManager fragmentManager = getFragmentManager();
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !fragmentManager.isStateSaved()) {
				fragmentManager.popBackStack();
			}
		}
	}

	public static class PlaybackFragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.preference_playback);
		}
	}

	public static class LibraryFragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.preference_library);
		}
	}

	public static class NotificationsFragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.preference_notifications);
		}
	}

	public static class ShakeFragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.preference_shake);
		}
	}

	public static class CoverArtFragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.preference_coverart);
		}
	}

	public static class MiscFragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.preference_misc);
		}
	}

	public static class PlaylistFragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);
			addPreferencesFromResource(R.xml.preference_playlist);
		}
	}

	public static class HelpFragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);

			Activity activity = getActivity();
			Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/vanilla-music/vanilla-music.github.io/wiki"));
			if (intent != null) {
				startActivity(intent);
			}
			activity.finish();
		}
	}

	public static class AboutFragment extends WebViewFragment {
		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
		{
			WebView view = (WebView)super.onCreateView(inflater, container, savedInstanceState);
			view.getSettings().setJavaScriptEnabled(true);

			TypedValue value = new TypedValue();
			getActivity().getTheme().resolveAttribute(R.attr.overlay_foreground_color, value, true);
			String fontColor = TypedValue.coerceToString(value.type, value.data);
			view.loadUrl("file:///android_asset/about.html?"+Uri.encode(fontColor));
			view.setBackgroundColor(Color.TRANSPARENT);
			return view;
		}
	}

	public static class HeadsetLaunchFragment extends PreferenceFragment {
		@Override
		public void onCreate(Bundle savedInstanceState)
		{
			super.onCreate(savedInstanceState);

			Activity activity = getActivity();
			Intent intent = activity.getPackageManager().getLaunchIntentForPackage(VPLUG_PACKAGE_NAME);

			if (intent != null) {
				startActivity(intent);
				FragmentManager fragmentManager = getFragmentManager();
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !fragmentManager.isStateSaved()) {
					fragmentManager.popBackStack();
				}
			} else {
				// package is not installed, ask user to install it
				new AlertDialog.Builder(activity)
				.setTitle(R.string.headset_launch_title)
				.setMessage(R.string.headset_launch_app_missing)
				.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						Intent marketIntent = new Intent(Intent.ACTION_VIEW);
						marketIntent.setData(Uri.parse("market://details?id="+VPLUG_PACKAGE_NAME));
						startActivity(marketIntent);
						getActivity().onBackPressed();
					}
				})
				.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						getActivity().onBackPressed();
					}
				})
				.show();
			}
		}
	}

	@Override
	protected boolean isValidFragment(String fragmentName) {
		return true;
	}


}
