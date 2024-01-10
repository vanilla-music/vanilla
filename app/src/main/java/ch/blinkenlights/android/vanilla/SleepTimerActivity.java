package ch.blinkenlights.android.vanilla;

import android.app.ActionBar;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.TimePicker;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Shows the sleep timer
 */
public class SleepTimerActivity extends Activity
	implements View.OnClickListener
{

	private LinearLayout mPickerLayout;
	private NumberPicker mNumberHours;
	private NumberPicker mNumberMinutes;
	private TextView mCountdown;
	private Button mButtonOk;
	private boolean toEnable;
	private Timer timer;

	/**
	 * Initialize the activity
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		ThemeHelper.setTheme(this, R.style.BackActionBar);
		super.onCreate(savedInstanceState);

		setContentView(R.layout.sleep_timer);

		mNumberHours = (NumberPicker) findViewById(R.id.sleep_timer_hours);
		mNumberHours.setMaxValue(23);
		mNumberHours.setMinValue(0);

		mNumberMinutes = (NumberPicker) findViewById(R.id.sleep_timer_minutes);
		mNumberMinutes.setMaxValue(59);
		mNumberMinutes.setMinValue(0);

		mCountdown = (TextView) findViewById(R.id.sleep_timer_countdown);
		mPickerLayout = (LinearLayout) findViewById(R.id.sleep_timer_timer);

		mButtonOk = (Button) findViewById(R.id.sleep_timer_button_ok);
		mButtonOk.setOnClickListener(this);

		doGUI();
	}

	@Override
	protected void onPause() {
		super.onPause();
		if(timer != null) {
			timer.cancel();
			timer.purge();
		}
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if(timer != null) {
			timer.cancel();
			timer.purge();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		doGUI();
	}

	private void doGUI()
	{
		int current_timer = PlaybackService.get(this).getSleepTimer();
		if(current_timer < 0)
			setToEnable();
		else
			setToDisable(current_timer);
	}

	private void setToEnable()
	{
		mButtonOk.setText(R.string.sleep_timer_start);
		mPickerLayout.setVisibility(View.VISIBLE);
		mCountdown.setVisibility(View.GONE);

		mNumberHours.setValue(1);
		mNumberMinutes.setValue(0);

		timer = null;	// force delete

		toEnable = true;
	}

	private void setToDisable(int current)
	{
		mButtonOk.setText(R.string.sleep_timer_stop);
		mPickerLayout.setVisibility(View.GONE);
		mCountdown.setVisibility(View.VISIBLE);
		mCountdown.setText(timeForTimeout(current));

		timer = new Timer();
		timer.scheduleAtFixedRate(new CountdownUpdater(current, mCountdown), 0, 1000);

		toEnable = false;
	}

	@Override
	public void onClick(View view)
	{
		// only one clickable view
		int timeout = -1;
		if(toEnable)
			timeout = mNumberHours.getValue() * 3600 + mNumberMinutes.getValue() * 60;

		PlaybackService.get(this).setSleepTimer(timeout);
		doGUI();
	}

	@Override
	public boolean onOptionsItemSelected(@NonNull MenuItem item) {
		if(item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private String timeForTimeout(int seconds)
	{
		int hours = seconds / 3600;
		int minutes = (seconds%3600) / 60;
		seconds = seconds % 60;

		return String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds);
	}

	private class CountdownUpdater extends TimerTask {

		private int timeout;
		final private TextView textView;

		public CountdownUpdater(int timeout, TextView textView)
		{
			this.timeout = timeout;
			this.textView = textView;
		}

		@Override
		public void run()
		{
			runOnUiThread(new Runnable() {
				@Override
				public void run() {
					textView.setText(timeForTimeout(timeout));
				}
			});
			timeout -= 1;
			if(timeout == 0) {
				timer.cancel();
				timer.purge();
			}
		}
	}
}
