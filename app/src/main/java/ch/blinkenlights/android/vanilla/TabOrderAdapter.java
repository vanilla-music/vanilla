/*
 * Copyright (C) 2011 Christopher Eby <kreed@kreed.org>
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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.BaseAdapter;

/**
 * CursorAdapter backed by MediaStore playlists.
 */
public class TabOrderAdapter extends BaseAdapter {
	private final TabOrderActivity mActivity;
	private final LayoutInflater mInflater;
	private int[] mTabIds;

	/**
	 * Create a tab order adapter.
	 *
	 * @param activity The activity that will own this adapter. The activity
	 * will be notified when items have been moved.
	 */
	public TabOrderAdapter(TabOrderActivity activity)
	{
		mActivity = activity;
		mInflater = (LayoutInflater)activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}

	/**
	 * Set the array containing the order of tab ids.
	 */
	public void setTabIds(int[] ids)
	{
		mTabIds = ids;
		notifyDataSetChanged();
	}

	/**
	 * Returns the array containing the order of tab ids. Do not modify the array.
	 */
	public int[] getTabIds()
	{
		return mTabIds;
	}

	@Override
	public int getCount()
	{
		return LibraryPagerAdapter.MAX_ADAPTER_COUNT;
	}

	@Override
	public Object getItem(int position)
	{
		return null;
	}

	@Override
	public long getItemId(int position)
	{
		return mTabIds[position];
	}

	@Override
	public View getView(int position, View convert, ViewGroup parent)
	{
		DraggableRow view;
		if (convert == null) {
			view = (DraggableRow)mInflater.inflate(R.layout.draggable_row, null);
		} else {
			view = (DraggableRow)convert;
		}
		view.getTextView().setText(LibraryPagerAdapter.TITLES[mTabIds[position]]);
		view.showCheckBox(true);
		return view;
	}

	@Override
	public boolean hasStableIds()
	{
		return true;
	}
}
