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
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

public class SongAdapter extends BaseAdapter implements Filterable {
	private Context mContext;
	private List<Song> mObjects;
	private Song[] mAllObjects;
	private ArrayFilter mFilter;

	public SongAdapter(Context context)
	{
		mContext = context;
		mAllObjects = Song.getAllSongMetadata();
		Arrays.sort(mAllObjects, new Song.TitleComparator());
	}

	@Override
	public boolean hasStableIds()
	{
		return true;
	}

	public View getView(int position, View convertView, ViewGroup parent)
	{
		TextView view = null;
		try {
			view = (TextView)convertView;
		} catch (ClassCastException e) {	
		}
		
		if (view == null)
			view = new TextView(mContext);

		view.setText(mObjects.get(position).title);
		return view;
	}

	public Filter getFilter()
	{
		if (mFilter == null)
			mFilter = new ArrayFilter();
		return mFilter;
	}

	private static final String[] mRanges = { "[2abc]", "[3def]", "[4ghi]", "[5jkl]", "[6mno]", "[7pqrs]", "[8tuv]", "[9wxyz]"};
	private class ArrayFilter extends Filter {
		@Override
		protected FilterResults performFiltering(CharSequence filter)
		{
			FilterResults results = new FilterResults();

			if (filter == null || filter.length() == 0) {
				results.values = Arrays.asList(mAllObjects);
				results.count = mAllObjects.length;
			} else {
				String patternString = "";
				for (int i = 0, end = filter.length(); i != end; ++i) {
					char c = filter.charAt(i);
					int value = c - '2';
					if (value >= 0 && value < 8)
						patternString += mRanges[value];
					else
						patternString += c; 
				}

				Pattern pattern = Pattern.compile(patternString);
				Matcher matcher = pattern.matcher("");

				int count = mAllObjects.length;
				ArrayList<Song> newValues = new ArrayList<Song>();
				newValues.ensureCapacity(count);

				for (int i = 0; i != count; ++i) {
					Song value = mAllObjects[i];
					matcher.reset(value.title.toLowerCase());

					if (matcher.find())
						newValues.add(value);
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