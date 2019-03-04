package ch.blinkenlights.android.vanilla.theming;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;

import com.larswerkman.holocolorpicker.ColorPicker;
import com.larswerkman.holocolorpicker.SaturationBar;
import com.larswerkman.holocolorpicker.ValueBar;

import java.util.ArrayList;

import ch.blinkenlights.android.vanilla.PrefDefaults;
import ch.blinkenlights.android.vanilla.PrefKeys;
import ch.blinkenlights.android.vanilla.PreferencesTheme;
import ch.blinkenlights.android.vanilla.R;
import ch.blinkenlights.android.vanilla.SharedPrefHelper;
import ch.blinkenlights.android.vanilla.ThemeHelper;

public class ColorPickerDialog extends Dialog {

	public Activity activity;
	private LinearLayout prefs;
	private LinearLayout picker_layout;
	private boolean prefsVisible=true;
	private Button preferences;
	private Button save;
	private ColorPicker picker;

	private int[] colorPreferences;

	private static int rows = 3;
	private static int colums = 3;
	private int [][] colorArray = new int[rows][colums];

	public ColorPickerDialog(Activity a) {
		super(a);
		activity = a;

	}

	public ColorPickerDialog(Activity a, int[] colors) {
		super(a);
		activity = a;

		int colorPos=0;

		ArrayList<Integer> colorList = new ArrayList<>();

		for (int i :colors) {
			colorList.add(i);
		}

		for (int i = 0; i < rows; i++) {
			for (int j = 0; j < colums; j++) {
				if(colorPos<=colorList.size()){
					colorArray[i][j]=colorList.get(colorPos);
					colorPos++;
				}
			}
		}

	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.color_picker_dialog);
		setTitle(activity.getString(R.string.color_picker_title));

		prefs = (LinearLayout) findViewById(R.id.picker_prefs);
		picker_layout = (LinearLayout) findViewById(R.id.picker_layout);

		save = (Button) findViewById(R.id.Save);
		preferences = (Button) findViewById(R.id.Preferences);
		createPaletteArrayView();

		if(!prefsVisible){
			picker_layout.setVisibility(View.VISIBLE);
			prefs.setVisibility(View.GONE);
			preferences.setText(R.string.switch_to_color_picker);
		}else{
			picker_layout.setVisibility(View.GONE);
			prefs.setVisibility(View.VISIBLE);
			preferences.setText(R.string.switch_to_color_picker);
		}


		ThemeHelper.setAccentColor(activity, save, preferences );


		picker = (ColorPicker) findViewById(R.id.picker);
		ValueBar valueBar = (ValueBar) findViewById(R.id.valuebar);
		SaturationBar saturationBar = (SaturationBar) findViewById(R.id.saturationbar);
		picker.addValueBar(valueBar);
		picker.addSaturationBar(saturationBar);

		SharedPreferences settings = SharedPrefHelper.getSettings(activity);
		int color = Color.parseColor(settings.getString(PrefKeys.COLOR_APP_ACCENT, PrefDefaults.COLOR_APP_ACCENT));
		picker.setOldCenterColor(color);
		picker.setColor(color);

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
				if(prefsVisible){
					picker_layout.setVisibility(View.VISIBLE);
					prefs.setVisibility(View.GONE);
					preferences.setText(R.string.switch_to_color_picker);
				}else{
					picker_layout.setVisibility(View.GONE);
					prefs.setVisibility(View.VISIBLE);
					preferences.setText(R.string.switch_to_color_picker);
				}
				prefsVisible = !prefsVisible;

			}
		});
	}

	public void setColorChangedCallback(final PreferencesTheme pt){

		picker.setOnColorChangedListener(new ColorPicker.OnColorChangedListener() {
			@Override
			public void onColorChanged(int color) {

				SharedPreferences settings = SharedPrefHelper.getSettings(pt.getActivity());
				SharedPreferences.Editor editor = settings.edit();

				String cOrigin = settings.getString(PrefKeys.COLOR_APP_ACCENT, PrefDefaults.COLOR_APP_ACCENT);
				editor.putString(PrefKeys.COLOR_APP_ACCENT, String.format("#%06X", 0xFFFFFF & color));
				editor.apply();

				ThemeHelper.setAccentColor(pt.getActivity());
				pt.colorPref.setIcon(pt.generateCustomColorPreview());

				editor.putString(PrefKeys.COLOR_APP_ACCENT, cOrigin);
				editor.apply();


				save.setTextColor(color);
				preferences.setTextColor(color);
			}
		});
	}


	private void addColorCircle(ImageView iv, final int color){
		iv.setImageBitmap(generateCustomColorPreview(500,color));
		iv.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				picker.setColor(color);
			}
		});
	}

	private Bitmap generateCustomColorPreview(int size, int color){

		final int half = size / 2;
		final int radius = (int) ((size / 2)*0.9);



		Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_4444);
		bitmap.setHasAlpha(true);

		Canvas canvas = new Canvas(bitmap);

		Paint paint = new Paint();

		paint.setStyle(Paint.Style.FILL);
		paint.setColor(color);
		canvas.drawCircle(half, half, radius, paint);

		return bitmap;
	}

	private void createPaletteArrayView(){

		LinearLayout parent=findViewById(R.id.picker_prefs);
		parent.setGravity(Gravity.CENTER | Gravity.TOP);

		for (int i = 0; i < colorArray.length; i++) {
			LinearLayout row = new LinearLayout(this.getContext());
			row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,(float)colums));
			row.setOrientation(LinearLayout.HORIZONTAL);
			for (int j = 0; j < colorArray[i].length ; j++) {
				if(colorArray[i][j]!=0){
					ImageView iv = new ImageView(this.getContext());
					LinearLayout.LayoutParams parms = new LinearLayout.LayoutParams(200,200, j);
					iv.setLayoutParams(parms);
					addColorCircle(iv, colorArray[i][j]);
					LinearLayout stupid = new LinearLayout(this.getContext());
					stupid.addView(iv);
					row.addView(stupid);
				}
			}
			parent.addView(row);
		}

	}

}
