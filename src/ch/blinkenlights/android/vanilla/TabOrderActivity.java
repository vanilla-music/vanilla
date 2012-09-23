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

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;

/**
 * The preferences activity in which one can change application preferences.
 */
public class TabOrderActivity extends Activity implements View.OnClickListener, OnItemClickListener {
	private TabOrderAdapter mAdapter;
	private DragListView mList;

	/**
	 * Initialize the activity, loading the preference specifications.
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setTitle(R.string.tabs);
		setContentView(R.layout.tab_order);

		mAdapter = new TabOrderAdapter(this);
		DragListView list = (DragListView)findViewById(R.id.list);
		list.setAdapter(mAdapter);
		list.setEditable(true);
		list.setOnItemClickListener(this);
		mList = list;
		load();

		findViewById(R.id.done).setOnClickListener(this);
		findViewById(R.id.restore_default).setOnClickListener(this);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if (item.getItemId() == android.R.id.home) {
			finish();
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onClick(View view)
	{
		switch (view.getId()) {
		case R.id.done:
			finish();
			break;
		case R.id.restore_default:
			restoreDefault();
			break;
		}
	}

	/**
	 * Restore the default tab order and visibility.
	 */
	public void restoreDefault()
	{
		mAdapter.setTabIds(LibraryPagerAdapter.DEFAULT_ORDER.clone());
		DragListView list = mList;
		for (int i = 0; i != LibraryPagerAdapter.MAX_ADAPTER_COUNT; ++i) {
			list.setItemChecked(i, true);
		}
		save();
	}

	/**
	 * Save tab order and visibility to SharedPreferences as a string.
	 */
	public void save()
	{
		int[] ids = mAdapter.getTabIds();
		DragListView list = mList;
		char[] out = new char[LibraryPagerAdapter.MAX_ADAPTER_COUNT];
		for (int i = 0; i != LibraryPagerAdapter.MAX_ADAPTER_COUNT; ++i) {
			out[i] = (char)(list.isItemChecked(i) ? 128 + ids[i] : 127 - ids[i]);
		}

		SharedPreferences.Editor editor = PlaybackService.getSettings(this).edit();
		editor.putString("tab_order", new String(out));
		editor.commit();
	}

	/**
	 * Load tab order settings from SharedPreferences and apply it to the
	 * activity.
	 */
	public void load()
	{
		String in = PlaybackService.getSettings(this).getString(PrefKeys.TAB_ORDER, null);
		if (in != null && in.length() == LibraryPagerAdapter.MAX_ADAPTER_COUNT) {
			char[] chars = in.toCharArray();
			int[] ids = new int[LibraryPagerAdapter.MAX_ADAPTER_COUNT];
			for (int i = 0; i != LibraryPagerAdapter.MAX_ADAPTER_COUNT; ++i) {
				int v = chars[i];
				v = v < 128 ? -(v - 127) : v - 128;
				if (v >= MediaUtils.TYPE_COUNT) {
					ids = null;
					break;
				}
				ids[i] = v;
			}

			if (ids != null) {
				mAdapter.setTabIds(ids);
				DragListView list = mList;
				for (int i = 0; i != LibraryPagerAdapter.MAX_ADAPTER_COUNT; ++i) {
					list.setItemChecked(i, chars[i] >= 128);
				}
			}

			return;
		}

		restoreDefault();
	}

	@Override
	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3)
	{
		save();
	}
}
