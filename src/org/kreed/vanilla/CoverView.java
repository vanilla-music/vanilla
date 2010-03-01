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

import org.kreed.vanilla.IMusicPlayerWatcher;
import org.kreed.vanilla.IPlaybackService;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.Scroller;

public class CoverView extends View {
	private static final int SNAP_VELOCITY = 1000;
	private static final int STORE_SIZE = 3;

	private Scroller mScroller;
	private VelocityTracker mVelocityTracker;
	private float mLastMotionX;
	private float mStartX;
	private float mStartY;
	private IPlaybackService mService;

	Song[] mSongs = new Song[3];
	private Bitmap[] mBitmaps = new Bitmap[3];

	private int mTentativeCover = -1;

	public CoverView(Context context, AttributeSet attributes)
	{
		super(context, attributes);

		mScroller = new Scroller(context);
	}

	public void setPlaybackService(IPlaybackService service)
	{
		try {
			mService = service;
			mService.registerWatcher(mWatcher);
			refreshSongs();
		} catch (RemoteException e) {
		}
	}

	private static void drawText(Canvas canvas, String text, float left, float top, float width, float maxWidth, Paint paint)
	{
		float offset = Math.max(0, maxWidth - width) / 2;
		canvas.clipRect(left, top, left + maxWidth, top + paint.getTextSize() * 2, Region.Op.REPLACE);
		canvas.drawText(text, left + offset, top - paint.ascent(), paint);
	}

	private static RectF centerRect(float maxWidth, float maxHeight, float width, float height)
	{
		RectF rect = new RectF();
		rect.left = (maxWidth - width) / 2;
		rect.top = (maxHeight - height) / 2;
		rect.right = (maxWidth + width) / 2;
		rect.bottom = (maxHeight + height) / 2;
		return rect;
	}

	private static RectF bottomStretchRect(float maxWidth, float maxHeight, float width, float height)
	{
		RectF rect = new RectF();
		rect.left = 0;
		rect.top = maxHeight - height;
		rect.right = maxWidth;
		rect.bottom = maxHeight;
		return rect;
	}

	public static Bitmap createMiniBitmap(Song song, int width, int height)
	{
		if (song == null || width < 1 || height < 1)
			return null;

		Paint paint = new Paint();
		paint.setAntiAlias(true);

		String title = song.title == null ? "" : song.title;
		Bitmap cover = song.coverPath == null ? null : BitmapFactory.decodeFile(song.coverPath);

		float titleSize = 12;
		float padding = 2;

		paint.setTextSize(titleSize);
		float titleWidth = paint.measureText(title);

		float boxWidth = width;
		float boxHeight = Math.min(height, titleSize + padding * 2);

		Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
		Canvas canvas = new Canvas(bitmap);

		if (cover != null) {
			canvas.drawBitmap(cover, null, new Rect(0, 0, width, height), paint);
			cover.recycle();
			cover = null;
		}

		RectF boxRect = bottomStretchRect(width, height, boxWidth, boxHeight);

		paint.setARGB(150, 0, 0, 0);
		canvas.drawRect(boxRect, paint);

		float maxWidth = boxWidth - padding * 2;
		paint.setARGB(255, 255, 255, 255);
		boxRect.top += padding;
		boxRect.left += padding;

		paint.setTextSize(titleSize);
		drawText(canvas, title, boxRect.left, boxRect.top, titleWidth, maxWidth, paint);

		return bitmap;
	}

	public static Bitmap createBitmap(Song song, int width, int height)
	{
		if (song == null || width < 1 || height < 1)
			return null;

		Paint paint = new Paint();
		paint.setAntiAlias(true);

		String title = song.title == null ? "" : song.title;
		String album = song.album == null ? "" : song.album;
		String artist = song.artist == null ? "" : song.artist;
		Bitmap cover = song.coverPath == null ? null : BitmapFactory.decodeFile(song.coverPath);

		float titleSize = 20;
		float subSize = 14;
		float padding = 10;

		paint.setTextSize(titleSize);
		float titleWidth = paint.measureText(title);
		paint.setTextSize(subSize);
		float albumWidth = paint.measureText(album);
		float artistWidth = paint.measureText(artist);

		float boxWidth = Math.min(width, Math.max(titleWidth, Math.max(artistWidth, albumWidth)) + padding * 2);
		float boxHeight = Math.min(height, titleSize + subSize * 2 + padding * 4);

		float coverWidth;
		float coverHeight;

		if (cover == null) {
			coverWidth = 0;
			coverHeight = 0;
		} else {
			coverWidth = cover.getWidth();
			coverHeight = cover.getHeight();

			float drawableAspectRatio = coverHeight / coverWidth; 
			float viewAspectRatio = (float) height / width;
			float scale = drawableAspectRatio > viewAspectRatio ? height / coverWidth 
			                                                    : width / coverHeight;

			coverWidth *= scale;
			coverHeight *= scale;
		}

		int bitmapWidth = (int)Math.max(coverWidth, boxWidth);
		int bitmapHeight = (int)Math.max(coverHeight, boxHeight);

		Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.RGB_565);
		Canvas canvas = new Canvas(bitmap);

		if (cover != null) {
			RectF rect = centerRect(bitmapWidth, bitmapHeight, coverWidth, coverHeight);
			canvas.drawBitmap(cover, null, rect, paint);
			cover.recycle();
			cover = null;
		}

		RectF boxRect = centerRect(bitmapWidth, bitmapHeight, boxWidth, boxHeight);

		paint.setARGB(150, 0, 0, 0);
		canvas.drawRect(boxRect, paint);

		float maxWidth = boxWidth - padding * 2;
		paint.setARGB(255, 255, 255, 255);
		boxRect.top += padding;
		boxRect.left += padding;

		paint.setTextSize(titleSize);
		drawText(canvas, title, boxRect.left, boxRect.top, titleWidth, maxWidth, paint);
		boxRect.top += titleSize + padding;

		paint.setTextSize(subSize);
		drawText(canvas, album, boxRect.left, boxRect.top, albumWidth, maxWidth, paint);
		boxRect.top += subSize + padding;

		drawText(canvas, artist, boxRect.left, boxRect.top, artistWidth, maxWidth, paint);

		return bitmap;
	}

	private void createBitmap(int i)
	{
		Bitmap oldBitmap = mBitmaps[i];
		mBitmaps[i] = createBitmap(mSongs[i], getWidth(), getHeight());
		if (oldBitmap != null)
			oldBitmap.recycle();
	}

	public void clearSongs()
	{
		for (int i = STORE_SIZE; --i != -1; ) {
			mSongs[i] = null;
			if (mBitmaps[i] != null) {
				mBitmaps[i].recycle();
				mBitmaps[i] = null;
			}
		}
	}

	private void refreshSongs()
	{
		mHandler.sendEmptyMessage(1);
		mHandler.sendEmptyMessage(2);
		mHandler.sendEmptyMessage(0);
	}

	private void shiftCover(int delta)
	{
		if (mService == null)
			return;

		try {
			mService.setCurrentSong(delta);

			int from = delta > 0 ? 1 : 0;
			int to = delta > 0 ? 0 : 1;
			int i = delta > 0 ? STORE_SIZE - 1 : 0;

			System.arraycopy(mSongs, from, mSongs, to, STORE_SIZE - 1);
			System.arraycopy(mBitmaps, from, mBitmaps, to, STORE_SIZE - 1);
			mSongs[i] = null;
			mBitmaps[i] = null;
			reset();

			mHandler.sendEmptyMessage(i);
		} catch (RemoteException e) {
		}
	}

	public void nextCover()
	{
		shiftCover(1);
	}

	public void previousCover()
	{
		shiftCover(-1);
	}

	public void togglePlayback()
	{
		if (mService == null)
			return;

		try {
			mService.togglePlayback();
		} catch (RemoteException e) {
		}
	}

	public void reset()
	{
		if (!mScroller.isFinished())
			mScroller.abortAnimation();
		scrollTo(getWidth(), 0);
		postInvalidate();
	}

	@Override
	protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight)
	{
		if (width == 0 || height == 0)
			return;

		for (int i = STORE_SIZE; --i != -1; )
			createBitmap(i);
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
		paint = null;
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
				int availableToScroll = scrollX - (mBitmaps[0] == null ? width : 0);
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
				velocityTracker.computeCurrentVelocity(1000);
				int velocity = (int) velocityTracker.getXVelocity();
	
				int min = mBitmaps[0] == null ? 1 : 0;
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

	public void computeScroll()
	{
		if (mScroller.computeScrollOffset()) {
			scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
			postInvalidate();
		} else if (mTentativeCover != -1) {
			shiftCover(mTentativeCover - 1);
			mTentativeCover = -1;
		}
	}

	private Handler mHandler = new Handler() {
		public void handleMessage(Message message) {
			try {
				int i = message.what;
				int delta = i - STORE_SIZE / 2;
				mSongs[i] = mService.getSong(delta);
				createBitmap(i);
				if (delta == 0)
					reset();
			} catch (RemoteException e) {
			}
		}
	};

	private IMusicPlayerWatcher mWatcher = new IMusicPlayerWatcher.Stub() {
		public void songChanged(Song playingSong)
		{
			Song currentSong = mSongs[STORE_SIZE / 2];
			if (currentSong == null) {
				mSongs[STORE_SIZE / 2] = currentSong;
				createBitmap(STORE_SIZE / 2);
				reset();
			} else if (currentSong.equals(playingSong))
				return;
			refreshSongs();
		}

		public void stateChanged(int oldState, int newState)
		{
		}
	};
}