/*
 * Copyright (C) 2014-2016 Adrian Ulrich <adrian@blinkenlights.ch>
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
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.CheckedTextView;
import android.widget.Checkable;

public class DraggableRow extends LinearLayout implements Checkable {
	/**
	 * True if the checkbox is checked
	 */
	private boolean mChecked;
	/**
	 * True if setupLayout has been called
	 */
	private boolean mLayoutSet;

	private TextView mTextView;
	private CheckedTextView mCheckBox;
	private View mPmark;
	private ImageView mDragger;
	private LazyCoverView mCoverView;

	/**
	 * Layout types for use with setupLayout
	 */
	public static final int LAYOUT_TEXTONLY   = 0;
	public static final int LAYOUT_CHECKBOXES = 1;
	public static final int LAYOUT_DRAGGABLE  = 2;
	public static final int LAYOUT_LISTVIEW   = 3;


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

	/**
	 * Sets up commonly used layouts - can only be called once per view
	 *
	 * @param type the layout type to use
	 */
	public void setupLayout(int type) {
		if (!mLayoutSet) {
			switch (type) {
				case LAYOUT_CHECKBOXES:
					mCheckBox.setVisibility(View.VISIBLE);
					showDragger(true);
					break;
				case LAYOUT_DRAGGABLE:
					highlightRow(false); // make this visible
					mCoverView.setVisibility(View.VISIBLE);
					showDragger(true);
					break;
				case LAYOUT_LISTVIEW:
					highlightRow(false); // make this visible
					mCoverView.setVisibility(View.VISIBLE);
					mDragger.setImageResource(R.drawable.arrow);
					break;
				case LAYOUT_TEXTONLY:
				default:
					break; // do not care
			}
			mLayoutSet = true;
		}
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
	 * We glue tags to the text view to make getTag() simpler and consistent
	 * with the on click listener interception
	 */
	@Override
	public void setTag(Object tag) {
		mTextView.setTag(tag);
	}

	@Override
	public Object getTag() {
		return mTextView.getTag();
	}

	/**
	 * Marks a row as highlighted
	 * @param state Enable or disable highlighting
	 */
	public void highlightRow(boolean state) {
		mPmark.setVisibility( state ? View.VISIBLE : View.INVISIBLE );
	}

	/**
	 * Change visibility of dragger element
	 *
	 * @param state shows or hides the dragger
	 */
	public void showDragger(boolean state) {
		mDragger.setVisibility( state ? View.VISIBLE : View.INVISIBLE );
	}

	public void setDraggerOnClickListener(View.OnClickListener listener) {
		TypedValue v = new TypedValue();
		getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, v, true);

		mDragger.setBackgroundResource(v.resourceId);
		mDragger.setOnClickListener(listener);
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
