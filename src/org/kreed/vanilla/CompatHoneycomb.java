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

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.FragmentTransaction;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

/**
 * Framework methods only in Honeycomb or above go here.
 */
public class CompatHoneycomb {
	/**
	 * Add ActionBar tabs for LibraryActivity.
	 *
	 * @param activity The activity to add to.
	 */
	public static void addActionBarTabs(final LibraryActivity activity)
	{
		ActionBar.TabListener listener = new ActionBar.TabListener() {
			private final LibraryActivity mActivity = activity;

			@Override
			public void onTabReselected(Tab tab, FragmentTransaction ft)
			{
			}

			@Override
			public void onTabSelected(Tab tab, FragmentTransaction ft)
			{
				mActivity.mViewPager.setCurrentItem(tab.getPosition());
			}

			@Override
			public void onTabUnselected(Tab tab, FragmentTransaction ft)
			{
			}
		};

		ActionBar ab = activity.getActionBar();
		ab.addTab(ab.newTab()
			.setText(R.string.artists)
			.setTabListener(listener));
		ab.addTab(ab.newTab()
			.setText(R.string.albums)
			.setTabListener(listener));
		ab.addTab(ab.newTab()
			.setText(R.string.songs)
			.setTabListener(listener));
		ab.addTab(ab.newTab()
			.setText(R.string.playlists)
			.setTabListener(listener));
		ab.addTab(ab.newTab()
			.setText(R.string.genres)
			.setTabListener(listener));
		ab.addTab(ab.newTab()
			.setText(R.string.files)
			.setTabListener(listener));
		ab.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
	}

	/**
	 * Call {@link ListView#setFastScrollAlwaysVisible(boolean)} on the given ListView.
	 */
	public static void setFastScrollAlwaysVisible(ListView view, boolean visible)
	{
		view.setFastScrollAlwaysVisible(visible);
	}

	/**
	 * Call {@link MenuItem#setActionView(View)} on the given MenuItem.
	 */
	public static void setActionView(MenuItem item, View view)
	{
		item.setActionView(view);
	}

	/**
	 * Call {@link MenuItem#setShowAsAction(int)} on the given MenuItem.
	 */
	public static void setShowAsAction(MenuItem item, int mode)
	{
		item.setShowAsAction(mode);
	}

	/**
	 * Select the ActionBar tab at the given position.
	 *
	 * @param activity The activity that owns the ActionBar.
	 * @param position The tab's position.
	 */
	public static void selectTab(Activity activity, int position)
	{
		ActionBar ab = activity.getActionBar();
		ab.selectTab(ab.getTabAt(position));
	}

	/**
	 * Call {@link android.provider.MediaStore.Audio.Genres#getContentUriForAudioId(String,int)}
	 * on the external volume.
	 */
	public static Uri getContentUriForAudioId(int id)
	{
		return MediaStore.Audio.Genres.getContentUriForAudioId("external", id);
	}
}
