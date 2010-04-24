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

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.Scroller;

public final class CoverView extends View {
	private static final int STORE_SIZE = 3;
	private static int SNAP_VELOCITY = -1;

	private static int TEXT_SIZE = -1;
	private static int TEXT_SIZE_BIG;
	private static int PADDING;
	private static int TEXT_SIZE_MINI = -1;
	private static int PADDING_MINI;
	private static Bitmap SONG_ICON;
	private static Bitmap ALBUM_ICON;
	private static Bitmap ARTIST_ICON;

	/**
	 * The Handler with which to do background work. Must be initialized by
	 * the containing Activity.
	 */
	Handler mHandler;
	/**
	 * Whether or not to display song info on top of the cover art. Can be
	 * initialized by the containing Activity.
	 */
	boolean mSeparateInfo;

	private Song[] mSongs = new Song[3];
	private Bitmap[] mBitmaps = new Bitmap[3];
	private int mTimelinePos;
	private Scroller mScroller;
	private VelocityTracker mVelocityTracker;
	private float mLastMotionX;
	private float mStartX;
	private float mStartY;
	private int mTentativeCover = -1;

	public CoverView(Context context, AttributeSet attributes)
	{
		super(context, attributes);

		mScroller = new Scroller(context);

		if (SNAP_VELOCITY == -1)
			SNAP_VELOCITY = ViewConfiguration.get(context).getScaledMinimumFlingVelocity();
	}

	private static void loadTextSizes()
	{
		DisplayMetrics metrics = ContextApplication.getContext().getResources().getDisplayMetrics();
		TEXT_SIZE = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, metrics);
		TEXT_SIZE_BIG = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, metrics);
		PADDING = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, metrics);
	}

	private static void loadMiniTextSizes()
	{
		DisplayMetrics metrics = ContextApplication.getContext().getResources().getDisplayMetrics();
		TEXT_SIZE_MINI = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, metrics);
		PADDING_MINI = (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, metrics);
	}

	private static void loadIcons()
	{
		Resources res = ContextApplication.getContext().getResources();
		Bitmap bitmap = BitmapFactory.decodeResource(res, R.drawable.tab_songs_selected);
		SONG_ICON = Bitmap.createScaledBitmap(bitmap, TEXT_SIZE, TEXT_SIZE, false);
		bitmap.recycle();
		bitmap = BitmapFactory.decodeResource(res, R.drawable.tab_albums_selected);
		ALBUM_ICON = Bitmap.createScaledBitmap(bitmap, TEXT_SIZE, TEXT_SIZE, false);
		bitmap.recycle();
		bitmap = BitmapFactory.decodeResource(res, R.drawable.tab_artists_selected);
		ARTIST_ICON = Bitmap.createScaledBitmap(bitmap, TEXT_SIZE, TEXT_SIZE, false);
		bitmap.recycle();
	}

	/**
	 * Query the service for initial song info.
	 */
	public void initialize()
	{
		mTimelinePos = ContextApplication.service.getTimelinePos();
		refreshSongs();
	}

	private static void drawText(Canvas canvas, String text, int left, int top, int width, int maxWidth, Paint paint)
	{
		canvas.save();
		int offset = Math.max(0, maxWidth - width) / 2;
		canvas.clipRect(left, top, left + maxWidth, top + paint.getTextSize() * 2);
		canvas.drawText(text, left + offset, top - paint.ascent(), paint);
		canvas.restore();
	}

	public static Bitmap createMiniBitmap(Song song, int width, int height)
	{
		if (song == null || width < 1 || height < 1)
			return null;

		if (TEXT_SIZE_MINI == -1)
			loadMiniTextSizes();

		int textSize = TEXT_SIZE_MINI;
		int padding = PADDING_MINI;

		Paint paint = new Paint();
		paint.setAntiAlias(true);
		paint.setTextSize(textSize);

		String title = song.title == null ? "" : song.title;
		Bitmap cover = song.getCover();

		int titleWidth = (int)paint.measureText(title);

		int boxWidth = width;
		int boxHeight = Math.min(height, textSize + padding * 2);

		Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
		Canvas canvas = new Canvas(bitmap);

		if (cover != null) {
			canvas.drawBitmap(cover, null, new Rect(0, 0, width, height), paint);
			cover.recycle();
			cover = null;
		}

		int left = 0;
		int top = height - boxHeight;
		int right = width;
		int bottom = height;

		paint.setARGB(150, 0, 0, 0);
		canvas.drawRect(left, top, right, bottom, paint);

		int maxWidth = boxWidth - padding * 2;
		paint.setARGB(255, 255, 255, 255);
		top += padding;
		left += padding;

		drawText(canvas, title, left, top, titleWidth, maxWidth, paint);

		return bitmap;
	}

	private static Bitmap createOverlappingBitmap(Song song, int width, int height)
	{
		if (song == null || width < 1 || height < 1)
			return null;

		if (TEXT_SIZE == -1)
			loadTextSizes();

		Paint paint = new Paint();
		paint.setAntiAlias(true);

		String title = song.title == null ? "" : song.title;
		String album = song.album == null ? "" : song.album;
		String artist = song.artist == null ? "" : song.artist;
		Bitmap cover = song.getCover();

		int titleSize = TEXT_SIZE_BIG;
		int subSize = TEXT_SIZE;
		int padding = PADDING;

		paint.setTextSize(titleSize);
		int titleWidth = (int)paint.measureText(title);
		paint.setTextSize(subSize);
		int albumWidth = (int)paint.measureText(album);
		int artistWidth = (int)paint.measureText(artist);

		int boxWidth = Math.min(width, Math.max(titleWidth, Math.max(artistWidth, albumWidth)) + padding * 2);
		int boxHeight = Math.min(height, titleSize + subSize * 2 + padding * 4);

		int coverWidth;
		int coverHeight;

		if (cover == null) {
			coverWidth = 0;
			coverHeight = 0;
		} else {
			coverWidth = cover.getWidth();
			coverHeight = cover.getHeight();

			float scale;
			if ((float)coverWidth / coverHeight > (float)width / height)
				scale = (float)width / coverWidth;
			else
				scale = (float)height / coverHeight;

			coverWidth *= scale;
			coverHeight *= scale;
		}

		int bitmapWidth = Math.max(coverWidth, boxWidth);
		int bitmapHeight = Math.max(coverHeight, boxHeight);

		Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.RGB_565);
		Canvas canvas = new Canvas(bitmap);

		if (cover != null) {
			Rect rect = new Rect(0, 0, bitmapWidth, bitmapHeight);
			canvas.drawBitmap(cover, null, rect, paint);
			cover.recycle();
			cover = null;
		}

		int left = (bitmapWidth - boxWidth) / 2;
		int top = (bitmapHeight - boxHeight) / 2;
		int right = (bitmapWidth + boxWidth) / 2;
		int bottom = (bitmapHeight + boxHeight) / 2;

		paint.setARGB(150, 0, 0, 0);
		canvas.drawRect(left, top, right, bottom, paint);

		int maxWidth = boxWidth - padding * 2;
		paint.setARGB(255, 255, 255, 255);
		top += padding;
		left += padding;

		paint.setTextSize(titleSize);
		drawText(canvas, title, left, top, titleWidth, maxWidth, paint);
		top += titleSize + padding;

		paint.setTextSize(subSize);
		drawText(canvas, album, left, top, albumWidth, maxWidth, paint);
		top += subSize + padding;

		drawText(canvas, artist, left, top, artistWidth, maxWidth, paint);

		return bitmap;
	}

	private static Bitmap createSeparatedBitmap(Song song, int width, int height)
	{
		if (song == null || width < 1 || height < 1)
			return null;

		if (TEXT_SIZE == -1)
			loadTextSizes();
		if (SONG_ICON == null)
			loadIcons();

		boolean horizontal = width > height;

		Paint paint = new Paint();
		paint.setAntiAlias(true);

		String title = song.title == null ? "" : song.title;
		String album = song.album == null ? "" : song.album;
		String artist = song.artist == null ? "" : song.artist;
		Bitmap cover = song.getCover();

		int textSize = TEXT_SIZE;
		int padding = PADDING;

		int coverWidth;
		int coverHeight;

		if (cover == null) {
			coverWidth = 0;
			coverHeight = 0;
		} else {
			coverWidth = cover.getWidth();
			coverHeight = cover.getHeight();

			float scale;
			if ((float)coverWidth / coverHeight > (float)width / height)
				scale = (float)width / coverWidth;
			else
				scale = (float)height / coverHeight;

			coverWidth *= scale;
			coverHeight *= scale;
		}

		paint.setTextSize(textSize);
		int titleWidth = (int)paint.measureText(title);
		int albumWidth = (int)paint.measureText(album);
		int artistWidth = (int)paint.measureText(artist);

		int maxBoxWidth = horizontal ? width - coverWidth : width;
		int maxBoxHeight = horizontal ? height : height - coverHeight;
		int boxWidth = Math.min(maxBoxWidth, textSize + Math.max(titleWidth, Math.max(artistWidth, albumWidth)) + padding * 3);
		int boxHeight = Math.min(maxBoxHeight, textSize * 3 + padding * 4);

		int bitmapWidth = (int)(horizontal ? coverWidth + boxWidth : Math.max(coverWidth, boxWidth));
		int bitmapHeight = (int)(horizontal ? Math.max(coverHeight, boxHeight) : coverHeight + boxHeight);

		Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.RGB_565);
		Canvas canvas = new Canvas(bitmap);

		if (cover != null) {
			RectF rect = new RectF(0, 0, coverWidth, coverHeight);
			canvas.drawBitmap(cover, null, rect, paint);
			cover.recycle();
			cover = null;
		}

		int top;
		int left;

		if (horizontal) {
			top = (bitmapHeight - boxHeight) / 2;
			left = padding + coverWidth;
		} else {
			top = padding + coverHeight;
			left = padding;
		}

		int maxWidth = boxWidth - padding * 3 - textSize;
		paint.setARGB(255, 255, 255, 255);

		canvas.drawBitmap(SONG_ICON, left, top, paint);
		drawText(canvas, title, left + padding + textSize, top, maxWidth, maxWidth, paint);
		top += textSize + padding;

		canvas.drawBitmap(ALBUM_ICON, left, top, paint);
		drawText(canvas, album, left + padding + textSize, top, maxWidth, maxWidth, paint);
		top += textSize + padding;

		canvas.drawBitmap(ARTIST_ICON, left, top, paint);
		drawText(canvas, artist, left + padding + textSize, top, maxWidth, maxWidth, paint);

		return bitmap;
	}

	private void createBitmap(int i)
	{
		Bitmap oldBitmap = mBitmaps[i];
		if (mSeparateInfo)
			mBitmaps[i] = createSeparatedBitmap(mSongs[i], getWidth(), getHeight());
		else
			mBitmaps[i] = createOverlappingBitmap(mSongs[i], getWidth(), getHeight());
		postInvalidate();
		if (oldBitmap != null)
			oldBitmap.recycle();
	}

	private void refreshSongs()
	{
		postLoadSong(1, null);
		postLoadSong(2, null);
		postLoadSong(0, null);
	}

	public void go(int delta)
	{
		int i = delta > 0 ? STORE_SIZE - 1 : 0;

		if (mSongs[i] == null)
			return;

		int from = delta > 0 ? 1 : 0;
		int to = delta > 0 ? 0 : 1;
		System.arraycopy(mSongs, from, mSongs, to, STORE_SIZE - 1);
		System.arraycopy(mBitmaps, from, mBitmaps, to, STORE_SIZE - 1);
		mSongs[i] = null;
		mBitmaps[i] = null;

		postLoadSong(i, null);

		mTimelinePos += delta;
		reset();
		invalidate();
	}

	public void reset()
	{
		if (!mScroller.isFinished())
			mScroller.abortAnimation();
		scrollTo(getWidth(), 0);
		postInvalidate();
	}

	private void regenerateBitmaps()
	{
		for (int i = STORE_SIZE; --i != -1; )
			createBitmap(i);
	}

	public void toggleDisplayMode()
	{
		mSeparateInfo = !mSeparateInfo;
		regenerateBitmaps();
		postInvalidate();
	}

	@Override
	protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight)
	{
		if (width == 0 || height == 0)
			return;

		regenerateBitmaps();
		reset();
	}

	@Override
	protected void onDraw(Canvas canvas)
	{
		super.onDraw(canvas);

		Rect clip = canvas.getClipBounds();
		Paint paint = new Paint();
		int width = getWidth();
		int height = getHeight();

		canvas.drawColor(Color.BLACK);

		for (int x = 0, i = 0; i != STORE_SIZE; ++i, x += width) {
			if (mBitmaps[i] != null && clip.intersects(x, 0, x + width, height)) {
				int xOffset = (width - mBitmaps[i].getWidth()) / 2;
				int yOffset = (height - mBitmaps[i].getHeight()) / 2;
				canvas.drawBitmap(mBitmaps[i], x + xOffset, yOffset, paint);
			}
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev)
	{
		// based on code from com.android.launcher.Workspace
		if (mVelocityTracker == null)
			mVelocityTracker = VelocityTracker.obtain();
 		mVelocityTracker.addMovement(ev);

 		float x = ev.getX();
 		int scrollX = getScrollX();
 		int width = getWidth();
 
 		switch (ev.getAction()) {
 		case MotionEvent.ACTION_DOWN:
 			if (!mScroller.isFinished())
				mScroller.abortAnimation();

 			mStartX = x;
 			mStartY = ev.getY();
			mLastMotionX = x;
			break;
		case MotionEvent.ACTION_MOVE:
			int deltaX = (int) (mLastMotionX - x);
			mLastMotionX = x;

			if (deltaX < 0) {
				int availableToScroll = scrollX - (mTimelinePos == 0 ? width : 0);
				if (availableToScroll > 0)
					scrollBy(Math.max(-availableToScroll, deltaX), 0);
			} else if (deltaX > 0) {
				int availableToScroll = getWidth() * 2 - scrollX;
				if (availableToScroll > 0)
					scrollBy(Math.min(availableToScroll, deltaX), 0);
			}
			break;
		 case MotionEvent.ACTION_UP:
			if (Math.abs(mStartX - x) + Math.abs(mStartY - ev.getY()) < 10) {
				performClick();
			} else {
				VelocityTracker velocityTracker = mVelocityTracker;
				velocityTracker.computeCurrentVelocity(250);
				int velocity = (int) velocityTracker.getXVelocity();

				int min = mTimelinePos == 0 ? 1 : 0;
				int max = 2;
				int nearestCover = (scrollX + width / 2) / width;
				int whichCover = Math.max(min, Math.min(nearestCover, max));

				if (velocity > SNAP_VELOCITY && whichCover != min)
					--whichCover;
				else if (velocity < -SNAP_VELOCITY && whichCover != max)
					++whichCover;

				int newX = whichCover * width;
				int delta = newX - scrollX;
				mScroller.startScroll(scrollX, 0, delta, 0, Math.abs(delta) * 2);
				if (whichCover != 1)
					mTentativeCover = whichCover;

				postInvalidate();
			}

			if (mVelocityTracker != null) {
				mVelocityTracker.recycle();
				mVelocityTracker = null;
			}

			break;
 		}
		return true;
	}

	@Override
	public void computeScroll()
	{
		if (mScroller.computeScrollOffset()) {
			scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
			postInvalidate();
		} else if (mTentativeCover != -1) {
			int delta = mTentativeCover - 1;
			mTentativeCover = -1;
			mHandler.sendMessage(mHandler.obtainMessage(PlaybackActivity.MSG_SET_SONG, delta, 0));
			go(delta);
		}
	}

	/**
	 * Load a song in the given position. If the song is null, queries the
	 * PlaybackService for the song at that position.
	 */
	void loadSong(int i, Song song)
	{
		mSongs[i] = song == null ? ContextApplication.service.getSong(i - STORE_SIZE / 2) : song;
		createBitmap(i);
	}

	/**
	 * Post a Handler message to call loadSong.
	 */
	private void postLoadSong(final int i, final Song song)
	{
		mHandler.post(new Runnable() {
			public void run()
			{
				loadSong(i, song);
			}
		});
	}

	/**
	 * Handle an intent broadcasted by the PlaybackService. This must be called
	 * to react to song changes in PlaybackService.
	 *
	 * @param intent The intent that was broadcast
	 */
	public void receive(Intent intent)
	{
		String action = intent.getAction();
		if (PlaybackService.EVENT_REPLACE_SONG.equals(action)) {
			int i = STORE_SIZE / 2 + intent.getIntExtra("pos", 0);
			Song song = intent.getParcelableExtra("song");
			postLoadSong(i, song);
		} else if (PlaybackService.EVENT_CHANGED.equals(action)) {
			Song currentSong = mSongs[STORE_SIZE / 2];
			Song playingSong = intent.getParcelableExtra("song");
			mTimelinePos = intent.getIntExtra("pos", 0);
			if (currentSong == null || !currentSong.equals(playingSong))
				refreshSongs();
		}
	}
}
