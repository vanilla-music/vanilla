package org.kreed.tumult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

public class SongAdapter extends BaseAdapter implements Filterable {
	private Context mContext;
	private final Object mLock = new Object();
	private List<StringPair> mObjects;
	private List<StringPair> mOriginalValues;
	private ArrayFilter mFilter;
	
	private class StringPair implements Comparable<StringPair> {
		public int id;
		public String value;
		public int compareTo(StringPair another)
		{
			return value.compareTo(another.value);
		}
	}

	public SongAdapter(Context context)
	{
		mContext = context;
		querySongs();
	}

	private void querySongs()
	{
		Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		String[] projection = { MediaStore.Audio.Media._ID,
				MediaStore.Audio.Media.TITLE,
				MediaStore.Audio.Media.ALBUM,
				MediaStore.Audio.Media.ARTIST };
		String selection = MediaStore.Audio.Media.IS_MUSIC + "!=0";

		ContentResolver resolver = mContext.getContentResolver();
		Cursor cursor = resolver.query(media, projection, selection, null, null);

		if (cursor == null)
			return;
		
		mObjects = new ArrayList<StringPair>(cursor.getCount());

		while (cursor.moveToNext()) {
			StringPair pair = new StringPair();
			pair.id = cursor.getInt(0);
			pair.value = cursor.getString(3) + " / " + cursor.getString(2) + " / " + cursor.getString(1);
			mObjects.add(pair);
		}

		cursor.close();

		Collections.sort(mObjects);
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

		view.setText(mObjects.get(position).value);
		return view;
	}

	public Filter getFilter()
	{
		if (mFilter == null)
			mFilter = new ArrayFilter();
		return mFilter;
	}

	private static final String[] mRanges = { "[01 ]", "[2abc]", "[3def]", "[4ghi]", "[5jkl]", "[6mno]", "[7pqrs]", "[8tuv]", "[9wxyz]"};
	private class ArrayFilter extends Filter {
		@Override
		protected FilterResults performFiltering(CharSequence filter)
		{
			FilterResults results = new FilterResults();

			if (mOriginalValues == null) {
				synchronized (mLock) {
					mOriginalValues = new ArrayList<StringPair>(mObjects);
				}
			}

			if (filter == null || filter.length() == 0) {
				synchronized (mLock) {
					ArrayList<StringPair> list = new ArrayList<StringPair>(mOriginalValues);
					results.values = list;
					results.count = list.size();
				}
			} else {
				String patternString = "";
				for (int i = 0, end = filter.length(); i != end; ++i) {
					char c = filter.charAt(i);
					int value = c - '1';
					if (value >= 0 && value < 9)
						patternString += mRanges[value];
					else
						patternString += c; 
				}

				Pattern pattern = Pattern.compile(patternString);
				Matcher matcher = pattern.matcher("");

				List<StringPair> values = mOriginalValues;
				int count = values.size();

				ArrayList<StringPair> newValues = new ArrayList<StringPair>();
				newValues.ensureCapacity(count);

				int i;
				for (i = 0; i != count; ++i) {
					StringPair value = values.get(i);
					matcher.reset(value.value.toLowerCase());

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
			mObjects = (List<StringPair>) results.values;
			if (results.count == 0)
				notifyDataSetInvalidated();
			else
				notifyDataSetChanged();
		}
	}

	public int getCount()
	{
		if (mObjects == null)
			return 0;
		return mObjects.size();
	}

	public Object getItem(int position)
	{
		if (mObjects == null)
			return null;
		return mObjects.get(position);
	}

	public long getItemId(int position)
	{
		if (mObjects == null || mObjects.isEmpty())
			return 0;
		return mObjects.get(position).id;
	}
}