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
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.CheckedTextView;
import android.widget.Checkable;

public class DraggableRow extends LinearLayout implements Checkable {
	private boolean mShowCheckBox;
	private boolean mShowCover;
	private boolean mHighlighted;
	private boolean mChecked;

	private TextView mTextView;
	private CheckedTextView mCheckBox;
	private View mPmark;
	private ImageView mDragger;
	private LazyCoverView mCoverView;


	public DraggableRow(Context context, AttributeSet attrs) {
		super(context, attrs);
	}


	@Override 
	public void onFinishInflate() {
		mCheckBox  = (CheckedTextView)this.findViewById(R.id.checkbox);
		mTextView  = (TextView)this.findViewById(R.id.text);
		mPmark     = (View)this.findViewById(R.id.pmark);
		mDragger   = (ImageView)this.findViewById(R.id.dragger);
		mCoverView = (LazyCoverView)this.findViewById(R.id.cover);
		super.onFinishInflate();
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
	 * Marks a row as highlighted
	 * @param state Enable or disable highlighting
	 */
	public void highlightRow(boolean state) {
		if (mHighlighted != state) {
			mPmark.setVisibility( state ? View.VISIBLE : View.INVISIBLE );
			mHighlighted = state;
		}
	}

	/**
	 * Changes the visibility of the checkbox
	 *
	 * @param state show or destroy the checkbox
	 */
	public void showCheckBox(boolean state) {
		if (mShowCheckBox != state) {
			mCheckBox.setVisibility( state ? View.VISIBLE : View.GONE );
			mShowCheckBox = state;
		}
	}

	/**
	 * Change the visibility of the cover view
	 *
	 * @param state show or hide the cover view
	 */
	public void showCoverView(boolean state) {
		if (mShowCover != state) {
			mCoverView.setVisibility( state ? View.VISIBLE : View.GONE );
			mShowCover = state;
		}
	}

	/**
	 * Change visibility of dragger element
	 *
	 * @param state shows or hides the dragger
	 */
	public void showDragger(boolean state) {
		mDragger.setVisibility( state ? View.VISIBLE : View.INVISIBLE );
	}

	/**
	 * Returns an instance of our textview
	 */
	public TextView getTextView() {
		return mTextView;
	}

	/**
	 * Returns an instance of our coverview
	 */
	public LazyCoverView getCoverView() {
		return mCoverView;
	}

}
