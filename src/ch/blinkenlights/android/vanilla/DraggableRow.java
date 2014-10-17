/*
 * Copyright (C) 2014 Adrian Ulrich <adrian@blinkenlights.ch>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>. 
 */

package ch.blinkenlights.android.vanilla;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.CheckedTextView;
import android.widget.Checkable;
import android.util.Log;

public class DraggableRow extends LinearLayout implements Checkable {
	private boolean mShowCheckBox;
	private boolean mChecked;
	private CheckedTextView mCheckBox;

	public DraggableRow(Context context, AttributeSet attrs) {
		super(context, attrs);
	}


	@Override 
	public void onFinishInflate() {
		mCheckBox = (CheckedTextView)this.findViewById(R.id.checkbox);
	}

	@Override
	public boolean isChecked() {
		return mChecked;
	}

	@Override
	public void setChecked(boolean checked) {
		mChecked = checked;
		mCheckBox.setChecked(mChecked);
	}

	@Override
	public void toggle() {
		setChecked(!mChecked);
	}


	/**
	 * Changes the visibility of the checkbox
	 * @param state controlls if the checkbox is shown or hidden
	 */
	public void showCheckBox(boolean state) {
		if (mShowCheckBox != state) {
			mCheckBox.setVisibility( state ? View.VISIBLE : View.GONE);
			mShowCheckBox = state;
		}
	}

}
