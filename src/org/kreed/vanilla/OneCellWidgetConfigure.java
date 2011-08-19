/*
 * Copyright (C) 2010 Christopher Eby <kreed@kreed.org>
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

package org.kreed.vanilla;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.CheckBox;

/**
 * A configuration dialog for the OneCellWidget. Displays the double tap
 * preference.
 */
public class OneCellWidgetConfigure extends Activity implements OnClickListener {
	/**
	 * The id of the widget we are configuring.
	 */
	private int mAppWidgetId;
	/**
	 * The check box for the double-tap-opens-players preference.
	 */
	private CheckBox mDoubleTap;

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);

		setResult(RESULT_CANCELED);

		setContentView(R.layout.one_cell_widget_configure);

		mDoubleTap = (CheckBox)findViewById(R.id.double_tap);
		findViewById(R.id.place).setOnClickListener(this);

		Bundle extras = getIntent().getExtras();
		if (extras != null)
			mAppWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

		if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID)
			finish();
	}

	public void onClick(View view)
	{
		boolean doubleTap = mDoubleTap.isChecked();

		// save the setting
		SharedPreferences.Editor prefs = PreferenceManager.getDefaultSharedPreferences(this).edit();
		prefs.putBoolean("double_tap_" + mAppWidgetId, doubleTap);
		prefs.commit();

		AppWidgetManager manager = AppWidgetManager.getInstance(this);
		new OneCellWidget().onUpdate(this, manager, new int[] { mAppWidgetId });

		Intent resultValue = new Intent();
		resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
		setResult(RESULT_OK, resultValue);
		finish();
	}
}