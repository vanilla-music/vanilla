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
import android.content.res.Resources;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
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

/**
 * MediaAdapter provides an adapter backed by a MediaStore content provider.
 * It generates simple one- or two-line text views to display each media
 * element.
 * 
 * Filtering is supported, as is a more specific type of filtering referred to
 * as limiting. Limiting is separate from filtering; a new filter will not
 * erase an active filter. Limiting is intended to allow only media belonging
 * to a specific group to be displayed, e.g. only songs from a certain artist.
 * See MediaView.getLimiter and setLimiter for details.
 */
public class MediaAdapter extends CursorAdapter implements FilterQueryProvider {
	/**
	 * The type of media represented by this adapter. Must be one of the
	 * Song.FIELD_* constants. Determines which content provider to query for
	 * media and what fields to display.
	 */
	int mType;
	Uri mStore;
	String[] mFields;
	private String[] mFieldKeys;
	boolean mExpandable;
	private String[] mLimiter;
	private CharSequence mConstraint;

	/**
	 * Construct a MediaAdapter representing the given <code>type</code> of
	 * media.
	 *
	 * @param context A Context to use
	 * @param type The type of media to represent. Must be one of the
	 * Song.TYPE_* constants. This determines which content provider to query
	 * and what fields to display in the views.
	 * @param expandable Whether an expand arrow should be shown to the right
	 * of the views' text
	 * @param requery If true, automatically update the adapter when the
	 * provider backing it changes
	 */
	public MediaAdapter(Context context, int type, boolean expandable, boolean requery)
	{
		super(context, null, requery);

		mType = type;
		mExpandable = expandable;

		switch (type) {
		case Song.TYPE_ARTIST:
			mStore = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;
			mFields = new String[] { MediaStore.Audio.Artists.ARTIST };
			mFieldKeys = new String[] { MediaStore.Audio.Artists.ARTIST_KEY };
			break;
		case Song.TYPE_ALBUM:
			mStore = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
			mFields = new String[] { MediaStore.Audio.Albums.ARTIST, MediaStore.Audio.Albums.ALBUM };
			// Why is there no artist_key column constant in the album MediaStore? The column does seem to exist.
			mFieldKeys = new String[] { "artist_key", MediaStore.Audio.Albums.ALBUM_KEY };
			break;
		case Song.TYPE_SONG:
			mStore = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
			mFields = new String[] { MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.TITLE };
			mFieldKeys = new String[] { MediaStore.Audio.Media.ARTIST_KEY, MediaStore.Audio.Media.ALBUM_KEY, MediaStore.Audio.Media.TITLE_KEY };
			break;
		case Song.TYPE_PLAYLIST:
			mStore = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI;
			mFields = new String[] { MediaStore.Audio.Playlists.NAME };
			mFieldKeys = null;
			break;
		default:
			throw new IllegalArgumentException("Invalid value for type: " + type);
		}

		setFilterQueryProvider(this);
		requery();

		if (mPaint == null) {
			Resources res = context.getResources();
			mExpander = BitmapFactory.decodeResource(res, R.drawable.expander_arrow);
			mTextSize = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, res.getDisplayMetrics());

			mPaint = new Paint();
			mPaint.setTextSize(mTextSize);
			mPaint.setAntiAlias(true);
		}
	}

	public final void requery()
	{
		changeCursor(runQuery(mConstraint));
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
		String[] source = mFieldKeys == null ? mFields : mFieldKeys;
		return source[source.length - 1];
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
			String[] needles;

			// If we are using sorting keys, we need to change our constraint
			// into a list of collation keys. Otherwise, just split the
			// constraint with no modification.
			if (mFieldKeys != null) {
				String colKey = MediaStore.Audio.keyFor(constraint.toString());
				String spaceColKey = DatabaseUtils.getCollationKey(" ");
				needles = colKey.split(spaceColKey);
			} else {
				needles = constraint.toString().split("\\s+");
			}

			int size = needles.length;
			if (limiter != null)
				++size;
			selectionArgs = new String[size];
			int i = 0;
			if (limiter != null) {
				selectionArgs[0] = limiter;
				i = 1;
			}

			String[] keySource = mFieldKeys == null ? mFields : mFieldKeys;
			String keys = keySource[0];
			for (int j = 1; j != keySource.length; ++j)
				keys += "||" + keySource[j];

			for (int j = 0; j != needles.length; ++i, ++j) {
				selectionArgs[i] = '%' + needles[j] + '%';

				if (i != 0)
					selection.append(" AND ");
				selection.append(keys);
				selection.append(" LIKE ?");
			}
		} else if (limiter != null) {
			selectionArgs = new String[] { limiter };
		} else {
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
			requery();
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

	static int mTextSize;
	static Bitmap mExpander;
	static Paint mPaint;

	int mViewHeight = -1;
	DashPathEffect mDashEffect;
	RadialGradient mDividerGradient;

	public class MediaView extends View {
		private long mId;
		private String mTitle;
		private String mSubTitle;
		private boolean mExpanderPressed;

		public MediaView(Context context)
		{
			super(context);

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
			int padding = mTextSize / 2;

			Paint paint = mPaint;

			if (mExpandable) {
				Bitmap expander = mExpander;
				width -= padding * 4 + expander.getWidth();

				if (mDashEffect == null)
					mDashEffect = new DashPathEffect(new float[] { 3, 3 }, 0);

				paint.setColor(Color.GRAY);
				paint.setPathEffect(mDashEffect); 
				canvas.drawLine(width, padding, width, height - padding, paint);
				paint.setPathEffect(null); 
				canvas.drawBitmap(expander, width + padding * 2, (height - expander.getHeight()) / 2, paint);
			}

			canvas.save();
			canvas.clipRect(padding, 0, width - padding, height);

			int allocatedHeight;

			if (mSubTitle != null) {
				allocatedHeight = height / 2 - padding * 3 / 2;

				paint.setColor(Color.GRAY);
				canvas.drawText(mSubTitle, padding, height / 2 + padding / 2 + (allocatedHeight - mTextSize) / 2 - paint.ascent(), paint);
			} else {
				allocatedHeight = height - padding * 2;
			}

			paint.setColor(Color.WHITE);
			canvas.drawText(mTitle, padding, padding + (allocatedHeight - mTextSize) / 2 - paint.ascent(), paint);

			width = getWidth();

			if (mDividerGradient == null)
				mDividerGradient = new RadialGradient(width / 2, height, width / 2, Color.WHITE, Color.BLACK, Shader.TileMode.CLAMP);

			paint.setShader(mDividerGradient);
			canvas.restore();
			canvas.drawLine(0, height, width, height, paint);
			paint.setShader(null);
		}

		public final int getFieldCount()
		{
			return mFields.length;
		}

		public final long getMediaId()
		{
			return mId;
		}

		/**
		 * Returns the type of media contained in the adapter containing this
		 * view. Will be one of the Song.TYPE_* constants.
		 */
		public int getMediaType()
		{
			return mType;
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
			ContentResolver resolver = getContext().getContentResolver();
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
			if (mExpandable)
				mExpanderPressed = event.getX() > getWidth() - mExpander.getWidth() - 2 * mTextSize;
			return false;
		}
	}
}
