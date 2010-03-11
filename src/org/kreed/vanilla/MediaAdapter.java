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
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;

public class MediaAdapter extends BaseAdapter implements Filterable {
	private Context mContext;
	private OnClickListener mExpanderListener;

	private List<Song> mObjects;
	private Song[] mAllObjects;
	private ArrayFilter mFilter;
	private int mLimiterField = -1;
	private Song mLimiterMedia;
	private CharSequence mLastFilter;

	private int mPrimaryField;
	private int mSecondaryField;

	public MediaAdapter(Context context, Song[] allObjects, int primaryField, int secondaryField)
	{
		mContext = context;
		mAllObjects = allObjects;
		mPrimaryField = primaryField;
		mSecondaryField = secondaryField;
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

	public View getView(int position, View convertView, ViewGroup parent)
	{
		MediaView view = null;
		try {
			view = (MediaView)convertView;
		} catch (ClassCastException e) {
		}

		if (view == null) {
			int flags = 0;
			if (mSecondaryField != -1)
				flags |= MediaView.SECONDARY_LINE;
			if (mExpanderListener != null)
				flags |= MediaView.EXPANDER;

			view = new MediaView(mContext, flags);

			if (mExpanderListener != null)
				view.setupExpander(mPrimaryField, mExpanderListener);
		}

		view.updateMedia(get(position), mPrimaryField, mSecondaryField);

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

			boolean noFilter = filter == null || filter.length() == 0;

			if (noFilter && mLimiterField == -1) {
				results.values = Arrays.asList(mAllObjects);
				results.count = mAllObjects.length;
			} else {
				Matcher[] matchers = null;
				if (!noFilter) {
					String[] words = filter.toString().split("\\s+");
					matchers = new Matcher[words.length];
					for (int i = words.length; --i != -1; )
						matchers[i] = createMatcher(words[i]);
				}

				int limiterId = mLimiterField == -1 ? 0 : mLimiterMedia.getFieldId(mLimiterField);

				int count = mAllObjects.length;
				ArrayList<Song> newValues = new ArrayList<Song>();
				newValues.ensureCapacity(count);

			outer:
				for (int i = 0; i != count; ++i) {
					Song song = mAllObjects[i];

					if (mLimiterField != -1 && song.getFieldId(mLimiterField) != limiterId)
						continue;

					if (!noFilter) {
						for (int j = matchers.length; --j != -1; ) {
							if (matchers[j].reset(song.artist).find())
								continue;
							if (mPrimaryField > 1 && matchers[j].reset(song.album).find())
								continue;
							if (mPrimaryField > 2 && matchers[j].reset(song.title).find())
								continue;
							continue outer;
						}
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
			mLastFilter = constraint;
			if (results.count == 0)
				notifyDataSetInvalidated();
			else
				notifyDataSetChanged();
		}
	}

	public void setLimiter(int field, Song media)
	{
		mLimiterField = field;
		mLimiterMedia = media;

		mObjects = new ArrayList<Song>();
		notifyDataSetInvalidated();

		getFilter().filter(mLastFilter);
	}

	public int getLimiterField()
	{
		return mLimiterField;
	}

	public Song getLimiterMedia()
	{
		return mLimiterMedia;
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
		if (i >= mObjects.size())
			return null;
		return mObjects.get(i);
	}

	public Object getItem(int i)
	{
		return get(i);
	}

	public long getItemId(int i)
	{
		Song song = get(i);
		if (song == null)
			return 0;
		return song.getFieldId(mPrimaryField);
	}

	public Intent buildSongIntent(int action, int pos)
	{
		Song song = get(pos);
		if (song == null)
			return null;

		Intent intent = new Intent(mContext, PlaybackService.class);
		intent.putExtra("type", mPrimaryField);
		intent.putExtra("action", action);
		intent.putExtra("id", song.getFieldId(mPrimaryField));
		intent.putExtra("title", song.getField(mPrimaryField));
		return intent;
	}
}