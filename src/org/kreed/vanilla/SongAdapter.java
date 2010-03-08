/*
 * Copyright (C) 2010 Christopher Eby <kreed@kreed.org>
 *
 * This file is part of Vanilla Music Player.
 *
 * Vanilla Music Player is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Vanilla Music Player is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.kreed.vanilla;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SongAdapter extends BaseAdapter implements Filterable {
	private Context mContext;
	private List<Song> mObjects;
	private Song[] mAllObjects;
	private ArrayFilter mFilter;
	private float mSize;
	private int mPadding;

	public SongAdapter(Context context)
	{
		mContext = context;
		mAllObjects = Song.getAllSongMetadata();
		Arrays.sort(mAllObjects, new Song.TitleComparator());

		DisplayMetrics metrics = context.getResources().getDisplayMetrics();
		mSize = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, metrics);
		mPadding = (int)mSize / 2;
	}

	@Override
	public boolean hasStableIds()
	{
		return true;
	}

	public View getView(int position, View convertView, ViewGroup parent)
	{
		LinearLayout view = null;
		try {
			view = (LinearLayout)convertView;
		} catch (ClassCastException e) {	
		}

		if (view == null) {
			view = new LinearLayout(mContext);
			view.setOrientation(LinearLayout.VERTICAL);
			view.setPadding(mPadding, mPadding, mPadding, mPadding);

			TextView title = new TextView(mContext);
			title.setSingleLine();
			title.setTextColor(Color.WHITE);
			title.setTextSize(mSize);
			title.setId(0);
			view.addView(title);

			TextView artist = new TextView(mContext);
			artist.setSingleLine();
			artist.setTextSize(mSize);
			artist.setId(1);
			view.addView(artist);
		}

		((TextView)view.findViewById(0)).setText(mObjects.get(position).title);
		((TextView)view.findViewById(1)).setText(mObjects.get(position).artist);
		return view;
	}

	public Filter getFilter()
	{
		if (mFilter == null)
			mFilter = new ArrayFilter();
		return mFilter;
	}

	private static final String[] mRanges = { ".", "[2abc]", "[3def]", "[4ghi]", "[5jkl]", "[6mno]", "[7pqrs]", "[8tuv]", "[9wxyz]"};
	private static Matcher createMatcher(String input)
	{
		String patternString = "";
		for (int i = 0, end = input.length(); i != end; ++i) {
			char c = input.charAt(i);
			int value = c - '1';
			if (value >= 0 && value < 9)
				patternString += mRanges[value];
			else
				patternString += c;
		}

		return Pattern.compile(patternString, Pattern.CASE_INSENSITIVE).matcher("");
	}

	private class ArrayFilter extends Filter {
		@Override
		protected FilterResults performFiltering(CharSequence filter)
		{
			FilterResults results = new FilterResults();

			if (filter == null || filter.length() == 0) {
				results.values = Arrays.asList(mAllObjects);
				results.count = mAllObjects.length;
			} else {
				String[] words = filter.toString().split("\\s+");
				Matcher[] matchers = new Matcher[words.length];
				for (int i = words.length; --i != -1; )
					matchers[i] = createMatcher(words[i]);

				int count = mAllObjects.length;
				ArrayList<Song> newValues = new ArrayList<Song>();
				newValues.ensureCapacity(count);

			outer:
				for (int i = 0; i != count; ++i) {
					Song song = mAllObjects[i];

					for (int j = matchers.length; --j != -1; ) {
						if (song.artist != null && matchers[j].reset(song.artist).find())
							continue;
						if (song.album != null && matchers[j].reset(song.album).find())
							continue;
						if (song.title != null && matchers[j].reset(song.title).find())
							continue;
						continue outer;
					}

					newValues.add(song);
				}

				newValues.trimToSize();

				results.values = newValues;
				results.count = newValues.size();
			}

			return results;
		}

		@SuppressWarnings("unchecked")
		@Override
		protected void publishResults(CharSequence constraint, FilterResults results)
		{
			mObjects = (List<Song>)results.values;
			if (results.count == 0)
				notifyDataSetInvalidated();
			else
				notifyDataSetChanged();
		}
	}

	public int getCount()
	{
		if (mObjects == null) {
			if (mAllObjects == null)
				return 0;
			return mAllObjects.length;
		}
		return mObjects.size();
	}

	public Object getItem(int position)
	{
		if (mObjects == null) {
			if (mAllObjects == null)
				return 0;
			return mAllObjects[position];
		}
		return mObjects.get(position);
	}

	public long getItemId(int position)
	{
		if (mObjects == null) {
			if (mAllObjects == null)
				return 0;
			return mAllObjects[position].id;
		}
		if (mObjects.isEmpty())
			return 0;
		return mObjects.get(position).id;
	}
}