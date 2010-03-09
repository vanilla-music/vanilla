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
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

public abstract class AbstractAdapter extends BaseAdapter implements Filterable {
	public static final int ONE_LINE = 0x1;

	private Context mContext;
	private List<Song> mObjects;
	private Song[] mAllObjects;
	private ArrayFilter mFilter;
	private float mSize;
	private int mPadding;
	private int mDrawFlags;
	private int mFieldCount;
	private OnClickListener mExpanderListener;

	public AbstractAdapter(Context context, Song[] allObjects, int drawFlags, int numFields)
	{
		mContext = context;
		mAllObjects = allObjects;
		mDrawFlags = drawFlags;
		mFieldCount = numFields;

		DisplayMetrics metrics = context.getResources().getDisplayMetrics();
		mSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, metrics);
		mPadding = (int)mSize / 2;
	}

	public void setExpanderListener(View.OnClickListener listener)
	{
		mExpanderListener = listener;
	}

	@Override
	public boolean hasStableIds()
	{
		return true;
	}

	protected abstract void updateText(int position, TextView upper, TextView lower);

	public View getView(int position, View convertView, ViewGroup parent)
	{
		RelativeLayout view = null;
		try {
			view = (RelativeLayout)convertView;
		} catch (ClassCastException e) {
		}

		if (view == null) {
			view = new RelativeLayout(mContext);
			view.setPadding(mPadding, mPadding, mPadding, mPadding);

			RelativeLayout.LayoutParams params;

			if (mExpanderListener != null) {
				params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
				params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
				params.addRule(RelativeLayout.CENTER_VERTICAL);

				ImageView button = new ImageView(mContext);
				button.setImageResource(R.drawable.expander_arrow);
				button.setId(3);
				button.setLayoutParams(params);
				button.setTag(R.id.list, mFieldCount);
				button.setTag(R.id.song, get(position));
				button.setOnClickListener(mExpanderListener);

				view.addView(button);
			}

			params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
			params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
			params.addRule(RelativeLayout.LEFT_OF, 3);

			TextView title = new TextView(mContext);
			title.setSingleLine();
			title.setTextColor(Color.WHITE);
			title.setTextSize(mSize);
			title.setId(1);
			title.setLayoutParams(params);
			view.addView(title);

			if ((mDrawFlags & ONE_LINE) == 0) {
				params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
				params.addRule(RelativeLayout.BELOW, 1);
				params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
				params.addRule(RelativeLayout.LEFT_OF, 3);

				TextView artist = new TextView(mContext);
				artist.setSingleLine();
				artist.setTextSize(mSize);
				artist.setId(2);
				artist.setLayoutParams(params);
				view.addView(artist);
			}
		}

		updateText(position, (TextView)view.findViewById(1),(TextView)view.findViewById(2));

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
						if (matchers[j].reset(song.artist).find())
							continue;
						if (mFieldCount > 1 && matchers[j].reset(song.album).find())
							continue;
						if (mFieldCount > 2 && matchers[j].reset(song.title).find())
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

	public Song get(int i)
	{
		if (mObjects == null) {
			if (mAllObjects == null)
				return null;
			return mAllObjects[i];
		}
		return mObjects.get(i);
	}

	public Object getItem(int i)
	{
		return get(i);
	}

	public long getItemId(int i)
	{
		if (mObjects != null && mObjects.isEmpty())
			return 0;
		return get(i).id;
	}
}