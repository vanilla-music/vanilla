package ch.blinkenlights.android.vanilla.theming;

import android.app.Activity;
import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.ValueBar;

import ch.blinkenlights.android.vanilla.PrefDefaults;
import ch.blinkenlights.android.vanilla.PrefKeys;
import ch.blinkenlights.android.vanilla.R;
import ch.blinkenlights.android.vanilla.SharedPrefHelper;
import ch.blinkenlights.android.vanilla.ThemeHelper;

public class ColorPickerDialog extends Dialog {

	public Activity activity;

	public ColorPickerDialog(Activity a) {
		super(a);
		activity = a;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.color_picker_dialog);
		setTitle(activity.getString(R.string.color_picker_title));

		Button save = (Button) findViewById(R.id.Save);
		Button preferences = (Button) findViewById(R.id.Preferences);
		preferences.setVisibility(View.INVISIBLE);

		ThemeHelper.setAccentColor(activity, save, preferences );


		final ColorPicker picker = (ColorPicker) findViewById(R.id.picker);
		ValueBar valueBar = (ValueBar) findViewById(R.id.valuebar);
		picker.addValueBar(valueBar);

		SharedPreferences settings = SharedPrefHelper.getSettings(activity);
		int color = Color.parseColor(settings.getString(PrefKeys.COLOR_APP_ACCENT, PrefDefaults.COLOR_APP_ACCENT));
		picker.setOldCenterColor(color);

		save.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				SharedPreferences.Editor editor = SharedPrefHelper.getSettings(activity).edit();
				editor.putString(PrefKeys.COLOR_APP_ACCENT, String.format("#%06X", 0xFFFFFF & picker.getColor()));
				editor.apply();
				dismiss();
			}
		});

		preferences.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
			}
		});
	}

}
