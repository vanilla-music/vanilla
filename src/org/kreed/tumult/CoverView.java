package org.kreed.tumult;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.Scroller;

public class CoverView extends View {
	private static final int SNAP_VELOCITY = 1000;

	private Scroller mScroller;
	private VelocityTracker mVelocityTracker;
	private float mLastMotionX;
	private float mStartX;
	private float mStartY;

	private CoverViewWatcher mListener;

	Song[] mSongs = new Song[3];
	private Bitmap[] mBitmaps = new Bitmap[3];

	private int mTentativeCover = -1;

	public interface CoverViewWatcher {
		public void next();
		public void previous();
		public void clicked();
	}

	public CoverView(Context context, AttributeSet attributes)
	{
		super(context, attributes);

		mScroller = new Scroller(context);
	}

	public void setWatcher(CoverViewWatcher listener)
	{
		mListener = listener;
	}

	public Song getActiveSong()
	{
		return mSongs[1];
	}

	private void drawText(Canvas canvas, String text, float left, float top, float width, float maxWidth, Paint paint)
	{
		float offset = Math.max(0, maxWidth - width) / 2;
		canvas.clipRect(left, top, left + maxWidth, top + paint.getTextSize() * 2, Region.Op.REPLACE);
		canvas.drawText(text, left + offset, top - paint.ascent(), paint);
	}

	private RectF centerRect(float maxWidth, float maxHeight, float width, float height)
	{
		RectF rect = new RectF();
		rect.left = (maxWidth - width) / 2;
		rect.top = (maxHeight - height) / 2;
		rect.right = (maxWidth + width) / 2;
		rect.bottom = (maxHeight + height) / 2;
		return rect;
	}

	private void createBitmap(int i)
	{
		Song song = mSongs[i];
		Bitmap bitmap = null;

		if (song != null) {
			int width = getWidth();
			int height = getHeight();

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

			bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.RGB_565);
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
		}
		
		Bitmap oldBitmap = mBitmaps[i];
		mBitmaps[i] = bitmap;
		if (oldBitmap != null)
			oldBitmap.recycle();
	}
	
	public void setSongs(Song[] songs)
	{
		mSongs = songs;
		regenerateBitmaps();
	}

	public void setSong(int delta, Song song)
	{
		int i = 1 + delta;
		mSongs[i] = song;
		createBitmap(i);
	}

	public void regenerateBitmaps()
	{
		if (getWidth() == 0 || getHeight() == 0)
			return;

		for (int i = mSongs.length; --i != -1; )
			createBitmap(i);
		reset();
	}
	
	public void shiftBackward()
	{
		System.arraycopy(mSongs, 1, mSongs, 0, mSongs.length - 1);
		System.arraycopy(mBitmaps, 1, mBitmaps, 0, mBitmaps.length - 1);
		mSongs[mSongs.length - 1] = null;
		mBitmaps[mBitmaps.length - 1] = null;
		reset();
	}

	public void shiftForward()
	{
		System.arraycopy(mSongs, 0, mSongs, 1, mSongs.length - 1);
		System.arraycopy(mBitmaps, 0, mBitmaps, 1, mBitmaps.length - 1);
		mSongs[0] = null;
		mBitmaps[0] = null;
		reset();
	}

	public void reset()
	{
		if (!mScroller.isFinished())
			mScroller.abortAnimation();
		scrollTo(getWidth(), 0);
		postInvalidate();
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh)
	{
		regenerateBitmaps();
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

		for (int x = 0, i = 0; i != mBitmaps.length; ++i, x += width) {
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
				mListener.clicked();
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
			if (mListener != null) {
				if (mTentativeCover == 2)
					mListener.next();
				else if (mTentativeCover == 0)
					mListener.previous();
			}
			mTentativeCover = -1;
		}
	}
}
