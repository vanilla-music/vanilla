/*
 * Copyright (C) 2012 Christopher Eby <kreed@kreed.org>
 * Copyright (C) 2015 Adrian Ulrich <adrian@blinkenlights.ch>
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
	 * The maximum value to use
	 */
	private int mMaxValue;
	/**
	 * The initially configured value (updated on dialog close)
	 */
	private int mInitialValue;
	/**
	 * Steps to take for the value
	 */
	private int mSteps;
	/**
	 * The format to use for the summary
	 */
	private String mSummaryFormat;
	/**
	 * The text to use in the summary
	 */
	private String mSummaryText;
	/**
	 * Text to display if the value equals zero
	 */
	private String mZeroText;
	/**
	 * Add given value to summary value
	 */
	private float mSummaryValueAddition;
	/**
	 * Multiply summary value by this value
	 */
	private float mSummaryValueMultiplication;
	/**
	 * TextView to display current summary
	 */
	private TextView mValueText;

	public SeekBarPreference(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		mContext = context;
		initDefaults(attrs);
	}


	/**
	 * Configures the view using the SeekBarPreference XML attributes
	 *
	 * @param attrs An AttributeSet
	 */
	private void initDefaults(AttributeSet attrs) {
		TypedArray a = getContext().obtainStyledAttributes(attrs, R.styleable.SeekBarPreference);

		mMaxValue = a.getInteger(R.styleable.SeekBarPreference_sbpMaxValue, 0);
		mSteps = a.getInteger(R.styleable.SeekBarPreference_sbpSteps, 1);
		mSummaryValueMultiplication = a.getFloat(R.styleable.SeekBarPreference_sbpSummaryValueMultiplication, 0f);
		mSummaryValueAddition = a.getFloat(R.styleable.SeekBarPreference_sbpSummaryValueAddition, 0f);
		mSummaryFormat = a.getString(R.styleable.SeekBarPreference_sbpSummaryFormat);
		mSummaryFormat = (mSummaryFormat == null ? "%s %.1f" : mSummaryFormat);
		mSummaryText = a.getString(R.styleable.SeekBarPreference_sbpSummaryText);
		mSummaryText = (mSummaryText == null ? "" : mSummaryText);
		mZeroText = a.getString(R.styleable.SeekBarPreference_sbpSummaryZeroText); // unlike other strings, this may be null
		a.recycle();
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
		mInitialValue = mValue = restoreValue ? getPersistedInt(mValue) : (Integer)defaultValue;
	}

	/**
	 * Create the summary for the given value.
	 *
	 * @param value The force threshold.
	 * @return A string representation of the threshold.
	 */
	private String getSummary(int value) {
		float fValue = (float)value;

		if (mSummaryValueAddition != 0f)
			fValue = fValue + mSummaryValueAddition;
		if (mSummaryValueMultiplication != 0f)
			fValue = fValue * mSummaryValueMultiplication;

		String result = String.format(mSummaryFormat, mSummaryText, fValue);

		if (fValue == 0f && mZeroText != null)
			result = mZeroText;

		return result;
	}

	@Override
	protected View onCreateDialogView()
	{
		View view = super.onCreateDialogView();

		mValueText = (TextView)view.findViewById(R.id.value);
		mValueText.setText(getSummary(mValue));

		SeekBar seekBar = (SeekBar)view.findViewById(R.id.seek_bar);

		seekBar.setMax(mMaxValue);
		seekBar.setProgress(mValue);
		seekBar.setOnSeekBarChangeListener(this);

		return view;
	}

	@Override
	protected void onDialogClosed(boolean positiveResult) {
		if (positiveResult) {
			mInitialValue = mValue;
		} else {
			// User aborted: Set remembered start value
			setValue(mInitialValue);
		}
		notifyChanged();
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
	{
		if (fromUser) {
			progress = (progress/mSteps) * mSteps;
			seekBar.setProgress(progress);
			setValue(progress);
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

	private void setValue(int value) {
		mValue = value;
		mValueText.setText(getSummary(value));
		persistInt(value);
	}
}
