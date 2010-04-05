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

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;

public class VolumePreference extends DialogPreference implements SeekBar.OnSeekBarChangeListener {
	public VolumePreference(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}

	@Override
	protected void onPrepareDialogBuilder(Builder builder)
	{
		// setting is applied instantly; no way to cancel
		builder.setNegativeButton(null, null);

		ViewGroup.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

		LinearLayout layout = new LinearLayout(getContext());
		layout.setLayoutParams(params);

		SeekBar seekBar = new SeekBar(getContext());
		seekBar.setPadding(20, 20, 20, 20);
		seekBar.setLayoutParams(params);
		seekBar.setMax(1000);
		seekBar.setProgress((int)(Math.pow(getPersistedFloat(1.0f) / 3, 0.25f) * 1000));
		seekBar.setOnSeekBarChangeListener(this);
		layout.addView(seekBar);

		builder.setView(layout);
	}

	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
	{
		// Approximate an exponential curve with x^4. Produces a value from 0.0 - 3.0.
		if (fromUser && shouldPersist()) {
			float value = seekBar.getProgress() / 1000.0f;
			value *= value;
			value *= value;
			persistFloat(value * 3);
		}
	}

	public void onStartTrackingTouch(SeekBar seekBar)
	{		
	}

	public void onStopTrackingTouch(SeekBar seekBar)
	{
	}
}