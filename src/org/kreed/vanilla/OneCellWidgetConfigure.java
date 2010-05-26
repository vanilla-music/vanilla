/*
 * Copyright (C) 2010 Christopher Eby <kreed@kreed.org>
 *
 * This file is part of Vanilla Music Player.
 *
 * Vanilla Music Player is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Vanilla Music Player is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
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