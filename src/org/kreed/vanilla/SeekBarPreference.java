/*
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

package org.kreed.vanilla;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * A dialog preference that contains a SeekBar.
 */
public class SeekBarPreference extends DialogPreference implements SeekBar.OnSeekBarChangeListener {
	/**
	 * The persisted value.
	 */
	private int mValue;
	/**
	 * The current position of the seek bar.
	 */
	private int mProgress;
	/**
	 * TextView to display current threshold.
	 */
	private TextView mValueText;

	public SeekBarPreference(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}

	@Override
	public CharSequence getSummary()
	{
		String summary = (String)super.getSummary();
		String status = getStatus();
		if (summary == null) {
			return status;
		} else {
			return String.format(summary, status);
		}
	}

	@Override
	protected Object onGetDefaultValue(TypedArray a, int index)
	{
		return a.getInt(index, 100);
	}

	@Override
	protected void onSetInitialValue(boolean restoreValue, Object defaultValue)
	{
		mValue = restoreValue ? getPersistedInt(mValue) : (Integer)defaultValue;
	}

	/**
	 * Get the status text, displayed above the seek bar.
	 */
	private String getStatus()
	{
		String key = getKey();
		if ("shake_threshold".equals(key)) {
			return String.valueOf(mValue / 10.0f);
		} else if ("volume_mb".equals(key)) {
			return String.format("%d%% (%+.1fdB)", mProgress, mValue / 100.0);
		} else {
			return String.format("%+.1fdB", mValue / 100.0);
		}
	}

	@Override
	protected View onCreateDialogView()
	{
		View view = super.onCreateDialogView();

		mValueText = (TextView)view.findViewById(R.id.value);
		mValueText.setText(getStatus());

		SeekBar seekBar = (SeekBar)view.findViewById(R.id.seek_bar);
		seekBar.setMax(getKey().startsWith("replaygain") ? 200 : 150);
		seekBar.setProgress(valueToProgress(mValue));
		seekBar.setOnSeekBarChangeListener(this);

		return view;
	}

	@Override
	protected void onDialogClosed(boolean positiveResult)
	{
		notifyChanged();
	}

	/**
	 * Convert a seek bar position to the persisted value.
	 */
	private int progressToValue(int progress)
	{
		String key = getKey();
		if ("shake_threshold".equals(key)) {
			return progress;
		} else {
			int value = (int)(6000 * Math.log10(progress / 100.0));
			if (value < -12000)
				value = -12000;
			return value;
		}
	}

	/**
	 * Convert a persisted value to a seek bar position.
	 */
	private int valueToProgress(int value)
	{
		String key = getKey();
		if ("shake_threshold".equals(key)) {
			return value;
		} else {
			return (int)(Math.pow(10, value / 6000.0) * 100);
		}
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
	{
		if (fromUser) {
			int value = progressToValue(progress);
			persistInt(value);
			mValue = value;
			mProgress = progress;
			mValueText.setText(getStatus());
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar)
	{
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar)
	{
	}
}
