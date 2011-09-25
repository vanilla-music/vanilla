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

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;

/**
 * A preference that provides a volume slider dialog.
 *
 * The position in the slider is saved as float ranging from 0 to 1 on a
 * roughly exponential scale.
 */
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

		SeekBar seekBar = new SeekBar(getContext());
		seekBar.setPadding(20, 20, 20, 20);
		seekBar.setLayoutParams(params);
		seekBar.setMax(1000);
		seekBar.setProgress((int)(Math.pow(getPersistedFloat(1.0f), 0.25f) * 1000));
		seekBar.setOnSeekBarChangeListener(this);
		builder.setView(seekBar);
	}

	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
	{
		// Approximate an exponential curve with x^4. Produces a value from 0.0 - 1.0.
		if (fromUser && shouldPersist()) {
			float value = seekBar.getProgress() / 1000.0f;
			value *= value;
			value *= value;
			persistFloat(value);
		}
	}

	public void onStartTrackingTouch(SeekBar seekBar)
	{
	}

	public void onStopTrackingTouch(SeekBar seekBar)
	{
	}
}
