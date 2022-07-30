package ch.blinkenlights.android.vanilla;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TimePicker;

/**
 * Shows the sleep timer
 */
public class SleepTimerActivity extends Activity
	implements View.OnClickListener
{

	private TimePicker mTimePicker;
	private Button mButtonOk;
	private Button mButtonCancel;
	private boolean toEnable;

	/**
	 * Initialize the activity
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		ThemeHelper.setTheme(this, R.style.PopupDialog);
		super.onCreate(savedInstanceState);


		setContentView(R.layout.sleep_timer);

		mTimePicker = (TimePicker) findViewById(R.id.sleep_timer_picker);
		// we're picking a timeout, not an actual time
		mTimePicker.setIs24HourView(true);
		mButtonOk = (Button) findViewById(R.id.sleep_timer_button_ok);
		mButtonOk.setOnClickListener(this);
		mButtonCancel = (Button) findViewById(R.id.sleep_timer_button_cancel);
		mButtonCancel.setOnClickListener(this);

		Intent intent = getIntent();
		int current_timer = intent.getIntExtra("sleep_timeout", -1);

		if(current_timer < 0) {
			setToEnable();
		} else {
			setToDisable(current_timer);
		}
	}

	private void setToEnable()
	{
		mButtonOk.setText(R.string.sleep_timer_start);
		mTimePicker.setCurrentHour(1);
		mTimePicker.setCurrentMinute(0);
		mTimePicker.setEnabled(true);
		toEnable = true;
	}

	private void setToDisable(int current)
	{
		int hours = current / 3600;
		int minutes = (current % 3600) / 60;

		mButtonOk.setText(R.string.sleep_timer_stop);
		mTimePicker.setCurrentHour(hours);
		mTimePicker.setCurrentMinute(minutes);
		mTimePicker.setEnabled(false);
		toEnable = false;
	}

	@Override
	public void onClick(View view)
	{
		if(view == mButtonCancel) {
			setResult(RESULT_CANCELED);
		} else {

			int timeout = -1;    // default to disable
			if (toEnable) {
				timeout = mTimePicker.getCurrentHour() * 3600 + mTimePicker.getCurrentMinute() * 60;
			}

			Intent result = new Intent();
			result.putExtra("sleep_timeout", timeout);
			setResult(RESULT_OK, result);
		}
		finish();
	}
}
