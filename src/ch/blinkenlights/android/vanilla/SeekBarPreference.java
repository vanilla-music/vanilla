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

package ch.blinkenlights.android.vanilla;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;
import android.widget.TextView;

/**
 * SeekBar preference to set the shake force threshold.
 */
public class SeekBarPreference extends DialogPreference implements SeekBar.OnSeekBarChangeListener {
	/**
	 * The current value.
	 */
	private int mValue;
	
	/**
	 * Our context (needed for getResources())
	 */
	private Context mContext;
	
	/**
	 * TextView to display current threshold.
	 */
	private TextView mValueText;

	public SeekBarPreference(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		mContext = context;
	}

	@Override
	public CharSequence getSummary()
	{
		return getSummary(mValue);
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
	 * Create the summary for the given value.
	 *
	 * @param value The force threshold.
	 * @return A string representation of the threshold.
	 */
	private String getSummary(int value)
	{
		if ("shake_threshold".equals(getKey())) {
			return String.valueOf(value / 10.0f);
		} else if("replaygain_bump".equals(getKey())) {
			return String.format("%+.1fdB", 2*(value-75)/10f);
		} else if("replaygain_untagged_debump".equals(getKey())) {
			String summary = (String)mContext.getResources().getText(R.string.replaygain_untagged_debump_summary);
			return String.format("%s %.1fdB", summary, (value-150)/10f);
		} else {
			return String.format("%d%% (%+.1fdB)", value, 20 * Math.log10(Math.pow(value / 100.0, 3)));
		}
	}

	@Override
	protected View onCreateDialogView()
	{
		View view = super.onCreateDialogView();

		mValueText = (TextView)view.findViewById(R.id.value);
		mValueText.setText(getSummary(mValue));

		SeekBar seekBar = (SeekBar)view.findViewById(R.id.seek_bar);
		seekBar.setMax(150);
		seekBar.setProgress(mValue);
		seekBar.setOnSeekBarChangeListener(this);

		return view;
	}

	@Override
	protected void onDialogClosed(boolean positiveResult)
	{
		notifyChanged();
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
	{
		if (fromUser) {
			mValue = progress;
			mValueText.setText(getSummary(progress));
			persistInt(progress);
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
