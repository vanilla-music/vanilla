package org.kreed.tumult;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Region;
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
		public void togglePlayback();
	}

	public CoverView(Context context)
	{
		super(context);

		mScroller = new Scroller(context);
	}

	public void setCoverSwapListener(CoverViewWatcher listener)
	{
		mListener = listener;
	}
	
	private RectF scale(Bitmap bitmap, int maxWidth, int maxHeight)
	{
		float bitmapWidth = bitmap.getWidth(); 
		float bitmapHeight = bitmap.getHeight(); 

		float drawableAspectRatio = bitmapHeight / bitmapWidth; 
		float viewAspectRatio = (float) maxHeight / maxWidth;
		float scale = drawableAspectRatio > viewAspectRatio ? maxHeight / bitmapWidth 
		                                                    : maxWidth / bitmapHeight;

		bitmapWidth *= scale;
		bitmapHeight *= scale;

		float left = (maxWidth - bitmapWidth) / 2; 
		float top = (maxHeight - bitmapHeight) / 2; 

		return new RectF(left, top, maxWidth - left, maxHeight - top);
	}
	
	private void drawText(Canvas canvas, String text, float left, float top, float width, float maxWidth, Paint paint)
	{
		float offset = Math.max(0, maxWidth - width) / 2;
		canvas.clipRect(left, top, left + maxWidth, top + paint.getTextSize() * 2, Region.Op.REPLACE);
		canvas.drawText(text, left + offset, top - paint.ascent(), paint);
	}

	private void createBitmap(int i)
	{
		Song song = mSongs[i];
		Bitmap bitmap = null;

		if (song != null) {
			int width = getWidth();
			int height = getHeight();
	
			bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
			Canvas canvas = new Canvas(bitmap);
			Paint paint = new Paint();
	
			Bitmap cover = song.coverPath == null ? null : BitmapFactory.decodeFile(song.coverPath);
			if (cover != null) {
				RectF dest = scale(cover, width, height);
				canvas.drawBitmap(cover, new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight()), dest, paint);
				cover.recycle();
				cover = null;
			}
	
			paint.setAntiAlias(true);
	
			String title = song.title == null ? "" : song.title;
			String album = song.album == null ? "" : song.album;
			String artist = song.artist == null ? "" : song.artist;
	
			float titleSize = 20;
			float subSize = 14;
			float padding = 10;
	
			paint.setTextSize(titleSize);
			float titleWidth = paint.measureText(title);
			paint.setTextSize(subSize);
			float albumWidth = paint.measureText(album);
			float artistWidth = paint.measureText(artist);
	
			float boxWidth = Math.max(titleWidth, Math.max(artistWidth, albumWidth)) + padding * 2;
			float boxHeight = titleSize + subSize * 2 + padding * 4;
	
			boxWidth = Math.min(boxWidth, width);
			boxHeight = Math.min(boxHeight, height);
	
			paint.setARGB(150, 0, 0, 0);
			float left = (width - boxWidth) / 2;
			float top = (height - boxHeight) / 2;
			float right = (width + boxWidth) / 2;
			float bottom = (height + boxHeight) / 2;
			canvas.drawRect(left, top, right, bottom, paint);
	
			float maxWidth = boxWidth - padding * 2;
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

	public void regenerateBitmaps()
	{
		if (getWidth() == 0 || getHeight() == 0)
			return;

		for (int i = mSongs.length; --i != -1; )
			createBitmap(i);
		reset();
	}
	
	public void setForwardSong(Song song)
	{
		if (mSongs[mSongs.length - 1] != null) {
			System.arraycopy(mSongs, 1, mSongs, 0, mSongs.length - 1);
			System.arraycopy(mBitmaps, 1, mBitmaps, 0, mBitmaps.length - 1);
			mBitmaps[mBitmaps.length - 1] = null;
			reset();
		}

		mSongs[mSongs.length - 1] = song;
		createBitmap(mSongs.length - 1);
	}

	public void setBackwardSong(Song song)
	{
		if (mSongs[0] != null) {
			System.arraycopy(mSongs, 0, mSongs, 1, mSongs.length - 1);
			System.arraycopy(mBitmaps, 0, mBitmaps, 1, mBitmaps.length - 1);
			mBitmaps[0] = null;
			reset();
		}

		mSongs[0] = song;
		createBitmap(0);
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

		for (int x = 0, i = 0; i != mBitmaps.length; ++i, x += width)
			if (mBitmaps[i] != null && clip.intersects(x, 0, x + width, height))
				canvas.drawBitmap(mBitmaps[i], x, 0, paint);
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
				mListener.togglePlayback();
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
