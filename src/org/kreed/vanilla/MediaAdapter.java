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

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.MediaStore;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.Filter;
import android.widget.FilterQueryProvider;

public class MediaAdapter extends CursorAdapter implements FilterQueryProvider {
	public static final String[] ARTIST_FIELDS = { MediaStore.Audio.Artists.ARTIST };
	public static final String[] ARTIST_FIELD_KEYS = { MediaStore.Audio.Artists.ARTIST_KEY };
	public static final String[] ALBUM_FIELDS = { MediaStore.Audio.Albums.ARTIST, MediaStore.Audio.Albums.ALBUM };
	// Why is there no artist_key column constant in the album MediaStore? The column does seem to exist.
	public static final String[] ALBUM_FIELD_KEYS = { "artist_key", MediaStore.Audio.Albums.ALBUM_KEY };
	public static final String[] SONG_FIELDS = { MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.TITLE };
	public static final String[] SONG_FIELD_KEYS = { MediaStore.Audio.Media.ARTIST_KEY, MediaStore.Audio.Media.ALBUM_KEY, MediaStore.Audio.Media.TITLE_KEY };

	private Uri mStore;
	private String[] mFields;
	private String[] mFieldKeys;
	private boolean mExpandable;
	private String[] mLimiter;
	private CharSequence mConstraint;

	public MediaAdapter(Context context, Uri store, String[] fields, String[] fieldKeys, boolean expandable)
	{
		super(context, null, true);

		mStore = store;
		mFields = fields;
		mFieldKeys = fieldKeys;
		mExpandable = expandable;

		setFilterQueryProvider(this);

		changeCursor(runQuery(null));
	}

	public void filter(CharSequence constraint, Filter.FilterListener listener)
	{
		mConstraint = constraint;
		getFilter().filter(constraint, listener);
	}

	protected String getDefaultSelection()
	{
		return null;
	}

	protected String getSortOrder()
	{
		return mFieldKeys[mFieldKeys.length - 1];
	}

	public Cursor runQuery(CharSequence constraint)
	{
		ContentResolver resolver = ContextApplication.getContext().getContentResolver();

		StringBuilder selection = new StringBuilder();
		String[] selectionArgs;
		String limiter;

		String defaultSelection = getDefaultSelection();
		if (defaultSelection != null)
			selection.append(defaultSelection);

		if (mLimiter != null) {
			int i = Math.min(mLimiter.length, mFields.length) - 1;
			if (selection.length() != 0)
				selection.append(" AND ");
			selection.append(mFields[i]);
			selection.append(" = ?");
			limiter = mLimiter[i];
		} else {
			limiter = null;
		}

		if (constraint != null && constraint.length() != 0) {
			String[] constraints = constraint.toString().split("\\s+");
			int size = constraints.length;
			if (limiter != null)
				++size;
			selectionArgs = new String[size];
			int i = 0;
			if (limiter != null) {
				selectionArgs[0] = limiter;
				i = 1;
			}
			String keys = mFieldKeys[0];
			for (int j = 1; j != mFieldKeys.length; ++j)
				keys += "||" + mFieldKeys[j];
			for (int j = 0; j != constraints.length; ++i, ++j) {
				selectionArgs[i] = '%' + MediaStore.Audio.keyFor(constraints[j]) + '%';

				if (j != 0 || selection.length() != 0)
					selection.append(" AND ");
                selection.append(keys);
                selection.append(" LIKE ?");
			}
		} else {
			if (limiter != null)
				selectionArgs = new String[] { limiter };
			else
				selectionArgs = null;
		}

		String[] projection;
		if (mFields.length == 1)
			projection = new String[] { BaseColumns._ID, mFields[0] };
		else
			projection = new String[] { BaseColumns._ID, mFields[mFields.length - 1], mFields[0] };

		return resolver.query(mStore, projection, selection.toString(), selectionArgs, getSortOrder());
	}

	public final boolean hasExpanders()
	{
		return mExpandable;
	}

	public final void setLimiter(String[] limiter, boolean async)
	{
		mLimiter = limiter;
		if (async)
			getFilter().filter(mConstraint);
		else
			changeCursor(runQuery(mConstraint));
	}

	public final String[] getLimiter()
	{
		return mLimiter;
	}

	public final int getLimiterLength()
	{
		if (mLimiter == null)
			return 0;
		return mLimiter.length;
	}

	@Override
	public void bindView(View view, Context context, Cursor cursor)
	{
		((MediaView)view).updateMedia(cursor);
	}

	@Override
	public View newView(Context context, Cursor cursor, ViewGroup parent)
	{
		return new MediaView(context);
	}

	private static float mTextSize = -1;
	private static Bitmap mExpander = null;

	private int mViewHeight = -1;

	public class MediaView extends View {
		private long mId;
		private String mTitle;
		private String mSubTitle;
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

			if (mExpandable)
				expanderHeight = mExpander.getHeight() + (int)mTextSize;
			else
				expanderHeight = 0;

			if (mFields.length > 1)
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
			if (mTitle == null)
				return;

			int width = getWidth();
			int height = getHeight();
			float padding = mTextSize / 2;

			Paint paint = new Paint();
			paint.setTextSize(mTextSize);
			paint.setAntiAlias(true);

			if (mExpandable) {
				width -= padding * 3 + mExpander.getWidth();
				canvas.drawBitmap(mExpander, width + padding * 2, (height - mExpander.getHeight()) / 2, paint);
			}

			canvas.clipRect(padding, 0, width - padding, height);

			float allocatedHeight;

			if (mSubTitle != null) {
				allocatedHeight = height / 2 - padding * 3 / 2;

				paint.setColor(Color.GRAY);
				canvas.drawText(mSubTitle, padding, height / 2 + padding / 2 + (allocatedHeight - mTextSize) / 2 - paint.ascent(), paint);
			} else {
				allocatedHeight = height - padding * 2;
			}

			paint.setColor(Color.WHITE);
			canvas.drawText(mTitle, padding, padding + (allocatedHeight - mTextSize) / 2 - paint.ascent(), paint);
		}

		public final int getFieldCount()
		{
			return mFields.length;
		}

		public final long getMediaId()
		{
			return mId;
		}

		public final String getTitle()
		{
			return mTitle;
		}

		public final boolean isExpanderPressed()
		{
			return mExpanderPressed;
		}

		public final void updateMedia(Cursor cursor)
		{
			mId = cursor.getLong(0);
			mTitle = cursor.getString(1);
			if (mFields.length > 1)
				mSubTitle = cursor.getString(2);
			invalidate();
		}

		public final String[] getLimiter()
		{
			ContentResolver resolver = ContextApplication.getContext().getContentResolver();
			String selection = mFields[mFields.length - 1] + " = ?";
			String[] selectionArgs = { mTitle };
			String[] projection = new String[mFields.length + 1];
			projection[0] = BaseColumns._ID;
			System.arraycopy(mFields, 0, projection, 1, mFields.length);

			Cursor cursor = resolver.query(mStore, projection, selection, selectionArgs, null);
			cursor.moveToNext();
			String[] result = new String[cursor.getColumnCount() - 1];
			for (int i = result.length; --i != -1; )
				result[i] = cursor.getString(i + 1);

			return result;
		}

		@Override
		public boolean onTouchEvent(MotionEvent event)
		{
			mExpanderPressed = event.getX() > getWidth() - mExpander.getWidth() - 3 * mTextSize / 2;
			return false;
		}
	}
}