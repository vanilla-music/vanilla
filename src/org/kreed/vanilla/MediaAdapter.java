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
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;

public class MediaAdapter extends BaseAdapter implements Filterable {
	private Context mContext;
	private OnClickListener mExpanderListener;

	private List<SongData> mObjects;
	private SongData[] mAllObjects;
	private ArrayFilter mFilter;
	private long mLimiter;
	private SongData mLimiterData;
	private CharSequence mPublishedFilter;
	private long mPublishedLimiter;

	private int mPrimaryField;
	private int mSecondaryField;

	public MediaAdapter(Context context, SongData[] allObjects, int primaryField, int secondaryField, View.OnClickListener expanderListener)
	{
		mContext = context;
		mAllObjects = allObjects;
		mPrimaryField = primaryField;
		mSecondaryField = secondaryField;
		mExpanderListener = expanderListener;
	}

	public final boolean hasExpanders()
	{
		return mExpanderListener != null;
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

		if (view == null)
			view = new MediaView(mContext);

		view.updateMedia(get(position));

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
		protected class ArrayFilterResults extends FilterResults {
			public long limiter;

			public ArrayFilterResults(List<SongData> list, long limiter)
			{
				values = list;
				count = list.size();
				this.limiter = limiter;
			}
		}

		@Override
		protected FilterResults performFiltering(CharSequence filter)
		{
			List<SongData> list;

			if (filter != null && filter.length() == 0)
				filter = null;

			if ((filter == null && mPublishedFilter == null || mPublishedFilter != null && mPublishedFilter.equals(filter)) && mLimiter == mPublishedLimiter) {
				list = mObjects;
			} else if (filter == null && mLimiter == -1) {
				list = Arrays.asList(mAllObjects);
			} else {
				Matcher[] matchers = null;
				if (filter != null) {
					String[] words = filter.toString().split("\\s+");
					matchers = new Matcher[words.length];
					for (int i = words.length; --i != -1; )
						matchers[i] = createMatcher(words[i]);
				}

				int limiterField = limiterField(mLimiter);
				long limiterId = limiterId(mLimiter);

				int count = mAllObjects.length;
				ArrayList<SongData> newValues = new ArrayList<SongData>();
				newValues.ensureCapacity(count);

			outer:
				for (int i = 0; i != count; ++i) {
					SongData song = mAllObjects[i];

					if (mLimiter != -1 && song.getFieldId(limiterField) != limiterId)
						continue;

					if (filter != null) {
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

				list = newValues;
			}

			return new ArrayFilterResults(list, mLimiter);
		}

		@SuppressWarnings("unchecked")
		@Override
		protected void publishResults(CharSequence filter, FilterResults results)
		{
			mObjects = (List<SongData>)results.values;
			mPublishedFilter = filter == null || filter.length() == 0 ? null : filter;
			mPublishedLimiter = ((ArrayFilterResults)results).limiter;

			if (results.count == 0)
				notifyDataSetInvalidated();
			else
				notifyDataSetChanged();
		}
	}

	public void hideAll()
	{
		mObjects = new ArrayList<SongData>();
		notifyDataSetInvalidated();
	}

	public void setLimiter(long limiter, SongData data)
	{
		mLimiter = limiter;
		mLimiterData = data;
		getFilter().filter(mPublishedFilter);
	}

	public final long getLimiter()
	{
		return mLimiter;
	}

	public final int getLimiterField()
	{
		if (mLimiter == -1)
			return -1;
		return limiterField(mLimiter);
	}

	public final SongData getLimiterData()
	{
		return mLimiterData;
	}

	private static final int ID_SHIFT = 2;
	private static final int FIELD_MASK = ~(~0 << ID_SHIFT);

	public static long makeLimiter(int field, SongData data)
	{
		return (data.getFieldId(field) << ID_SHIFT) + (field & FIELD_MASK);
	}

	public static int limiterField(long limiter)
	{
		return (int)(limiter & FIELD_MASK);
	}

	public static long limiterId(long limiter)
	{
		return limiter >> ID_SHIFT;
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

	public SongData get(int i)
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
		SongData song = get(i);
		if (song == null)
			return 0;
		return song.getFieldId(mPrimaryField);
	}

	public Intent buildSongIntent(int action, int pos)
	{
		SongData song = get(pos);
		if (song == null)
			return null;

		Intent intent = new Intent(mContext, PlaybackService.class);
		intent.putExtra("type", mPrimaryField);
		intent.putExtra("action", action);
		intent.putExtra("id", song.getFieldId(mPrimaryField));
		intent.putExtra("title", song.getField(mPrimaryField));
		return intent;
	}

	private static float mTextSize = -1;
	private static Bitmap mExpander = null;

	private int mViewHeight = -1;

	public class MediaView extends View {
		private SongData mData;
		private boolean mExpanderPressed;

		public MediaView(Context context)
		{
			super(context);

			if (mExpander == null)
				mExpander = BitmapFactory.decodeResource(context.getResources(), R.drawable.expander_arrow);
			if (mTextSize == -1)
				mTextSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, context.getResources().getDisplayMetrics());
			if (mViewHeight == -1)
				mViewHeight = measureHeight();
		}

		private int measureHeight()
		{
			int expanderHeight;
			int textHeight;

			if (mExpanderListener != null)
				expanderHeight = mExpander.getHeight() + (int)mTextSize;
			else
				expanderHeight = 0;

			if (mSecondaryField != -1)
				textHeight = (int)(7 * mTextSize / 2);
			else
				textHeight = (int)(2 * mTextSize);

			return Math.max(expanderHeight, textHeight);
		}

		@Override
		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
		{
			setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), mViewHeight);
		}

		@Override
		public void onDraw(Canvas canvas)
		{
			if (mData == null)
				return;

			int width = getWidth();
			int height = getHeight();
			float padding = mTextSize / 2;

			Paint paint = new Paint();
			paint.setTextSize(mTextSize);
			paint.setAntiAlias(true);

			if (mExpanderListener != null) {
				width -= padding * 3 + mExpander.getWidth();
				canvas.drawBitmap(mExpander, width + padding * 2, (height - mExpander.getHeight()) / 2, paint);
			}

			canvas.clipRect(padding, 0, width - padding, height);

			float allocatedHeight;

			if (mSecondaryField != -1) {
				allocatedHeight = height / 2 - padding * 3 / 2;

				paint.setColor(Color.GRAY);
				canvas.drawText(mData.getField(mSecondaryField), padding, height / 2 + padding / 2 + (allocatedHeight - mTextSize) / 2 - paint.ascent(), paint);
			} else {
				allocatedHeight = height - padding * 2;
			}

			paint.setColor(Color.WHITE);
			canvas.drawText(mData.getField(mPrimaryField), padding, padding + (allocatedHeight - mTextSize) / 2 - paint.ascent(), paint);
		}

		public final void updateMedia(SongData data)
		{
			mData = data;
			invalidate();
		}

		public final int getPrimaryField()
		{
			return mPrimaryField;
		}

		public final SongData getExpanderData()
		{
			return mData;
		}

		public final boolean isExpanderPressed()
		{
			return mExpanderPressed;
		}

		@Override
		public boolean onTouchEvent(MotionEvent event)
		{
			mExpanderPressed = event.getX() > getWidth() - mExpander.getWidth() - 3 * mTextSize / 2;
			return false;
		}
	}
}