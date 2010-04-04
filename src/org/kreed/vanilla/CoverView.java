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

import org.kreed.vanilla.IPlaybackService;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
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
	private final int SNAP_VELOCITY;

	private Scroller mScroller;
	private VelocityTracker mVelocityTracker;
	private float mLastMotionX;
	private float mStartX;
	private float mStartY;
	private IPlaybackService mService;

	private boolean mSeparateInfo;

	Song[] mSongs = new Song[3];
	private Bitmap[] mBitmaps = new Bitmap[3];

	private int mTentativeCover = -1;

	public CoverView(Context context, AttributeSet attributes)
	{
		super(context, attributes);

		mScroller = new Scroller(context);
		SNAP_VELOCITY = ViewConfiguration.get(context).getScaledMinimumFlingVelocity();
	}

	public boolean hasSeparateInfo()
	{
		return mSeparateInfo;
	}

	public void setSeparateInfo(boolean separate)
	{
		mSeparateInfo = separate;
	}

	public void setPlaybackService(IPlaybackService service)
	{
		try {
			mService = service;
			if (mService.isLoaded())
				refreshSongs();
		} catch (RemoteException e) {
		}
	}

	private static void drawText(Canvas canvas, String text, float left, float top, float width, float maxWidth, Paint paint)
	{
		canvas.save();
		float offset = Math.max(0, maxWidth - width) / 2;
		canvas.clipRect(left, top, left + maxWidth, top + paint.getTextSize() * 2);
		canvas.drawText(text, left + offset, top - paint.ascent(), paint);
		canvas.restore();
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

		DisplayMetrics metrics = ContextApplication.getContext().getResources().getDisplayMetrics();
		float titleSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, metrics);
		float padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, metrics);

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

	private Bitmap createOverlappingBitmap(Song song, int width, int height)
	{
		if (song == null || width < 1 || height < 1)
			return null;

		Paint paint = new Paint();
		paint.setAntiAlias(true);

		String title = song.title == null ? "" : song.title;
		String album = song.album == null ? "" : song.album;
		String artist = song.artist == null ? "" : song.artist;
		Bitmap cover = song.coverPath == null ? null : BitmapFactory.decodeFile(song.coverPath);

		DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
		float titleSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20, metrics);
		float subSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, metrics);
		float padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, metrics);

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

	private Bitmap createSeparatedBitmap(Song song, int width, int height)
	{
		if (song == null || width < 1 || height < 1)
			return null;

		boolean horizontal = width > height;

		Paint paint = new Paint();
		paint.setAntiAlias(true);

		String title = song.title == null ? "" : song.title;
		String album = song.album == null ? "" : song.album;
		String artist = song.artist == null ? "" : song.artist;
		Bitmap cover = song.coverPath == null ? null : BitmapFactory.decodeFile(song.coverPath);

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

		DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
		float textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 14, metrics);
		float padding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, metrics);

		paint.setTextSize(textSize);
		float titleWidth = paint.measureText(title);
		float albumWidth = paint.measureText(album);
		float artistWidth = paint.measureText(artist);

		float maxBoxWidth = horizontal ? width - coverWidth : width;
		float maxBoxHeight = horizontal ? height : height - coverHeight;
		float boxWidth = Math.min(maxBoxWidth, textSize + Math.max(titleWidth, Math.max(artistWidth, albumWidth)) + padding * 3);
		float boxHeight = Math.min(maxBoxHeight, textSize * 3 + padding * 4);

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

		Drawable drawable;
		float top = padding;
		float left = padding;

		if (horizontal) {
			top = (height - boxHeight) / 2;
			left += coverWidth;
		} else {
			top += coverHeight;
		}

		float maxWidth = boxWidth - padding * 3 - textSize;
		paint.setARGB(255, 255, 255, 255);

		drawable = getResources().getDrawable(R.drawable.tab_songs_selected);
		drawable.setBounds((int)left, (int)top, (int)(left + textSize), (int)(top + textSize));
		drawable.draw(canvas);
		drawText(canvas, title, left + padding + textSize, top, maxWidth, maxWidth, paint);
		top += textSize + padding;

		drawable = getResources().getDrawable(R.drawable.tab_albums_selected);
		drawable.setBounds((int)left, (int)top, (int)(left + textSize), (int)(top + textSize));
		drawable.draw(canvas);
		drawText(canvas, album, left + padding + textSize, top, maxWidth, maxWidth, paint);
		top += textSize + padding;

		drawable = getResources().getDrawable(R.drawable.tab_artists_selected);
		drawable.setBounds((int)left, (int)top, (int)(left + textSize), (int)(top + textSize));
		drawable.draw(canvas);
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

		int i = delta > 0 ? STORE_SIZE - 1 : 0;

		if (mSongs[i] == null)
			return;

		int from = delta > 0 ? 1 : 0;
		int to = delta > 0 ? 0 : 1;

		System.arraycopy(mSongs, from, mSongs, to, STORE_SIZE - 1);
		System.arraycopy(mBitmaps, from, mBitmaps, to, STORE_SIZE - 1);
		mSongs[i] = null;
		mBitmaps[i] = null;
		reset();
		invalidate();

		mHandler.sendMessage(mHandler.obtainMessage(SET_SONG, delta, 0));
		mHandler.sendEmptyMessage(i);
	}

	public void go(int delta) throws RemoteException
	{
		if (mService == null)
			throw new RemoteException();
		mHandler.sendMessage(mHandler.obtainMessage(GO, delta, 0));
	}

	public void reset()
	{
		if (!mScroller.isFinished())
			mScroller.abortAnimation();
		scrollTo(getWidth(), 0);
		invalidate();
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
				int availableToScroll = getWidth() * (mBitmaps[0] == null ? 1 : 2) - scrollX;
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

				int min = mBitmaps[0] == null ? 1 : 0;
				int max = mBitmaps[2] == null ? 1 : 2;
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

	private static final int GO = 10;
	private static final int SET_SONG = 11;
	private static final int SET_COVER = 12;

	private Handler mHandler = new Handler() {
		public void handleMessage(Message message) {
			try {
				switch (message.what) {
				case GO:
					if (message.arg1 == 0)
						mService.toggleFlag(PlaybackService.FLAG_PLAYING);
					else
						shiftCover(message.arg1);
					break;
				case SET_SONG:
					try {
						mService.setCurrentSong(message.arg1);
					} catch (RemoteException e) {
						mService = null;
					}
					break;
				case SET_COVER:
					mSongs[STORE_SIZE / 2] = (Song)message.obj;
					createBitmap(STORE_SIZE / 2);
					reset();
					break;
				default:
					int i = message.what;
					int delta = i - STORE_SIZE / 2;
					mSongs[i] = mService.getSong(delta);
					createBitmap(i);
					if (delta == 0)
						reset();
					break;
				}
			} catch (RemoteException e) {
				mService = null;
			}
		}
	};

	public void onReceive(Intent intent)
	{
		String action = intent.getAction();
		if (PlaybackService.EVENT_REPLACE_SONG.equals(action)) {
			if (intent.getBooleanExtra("all", false))
				refreshSongs();
			else
				mHandler.sendEmptyMessage(2);
		} else if (PlaybackService.EVENT_CHANGED.equals(action)) {
			Song currentSong = mSongs[STORE_SIZE / 2];
			Song playingSong = intent.getParcelableExtra("song");
			if (currentSong == null)
				mHandler.sendMessage(mHandler.obtainMessage(SET_COVER, playingSong));
			else if (!currentSong.equals(playingSong))
				refreshSongs();
		}
	}
}