/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.blinkenlights.android.vanilla;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;

/**
 * A ListView that supports dragging to reorder its elements.
 *
 * This implementation has some restrictions:
 *     Footers are unsupported
 *     All non-header views must have the same height
 *     The adapter must implement DragAdapter
 *
 * Dragging disabled by default. Enable it with
 * {@link DragListView#setEditable(boolean)}.
 *
 * This should really be built-in to Android. This implementation is SUPER-
 * HACKY. : /
 */
public class DragListView extends ListView implements Handler.Callback {
	/**
	 * Adapter that implements move and remove operations.
	 */
	public interface DragAdapter extends ListAdapter {
		/**
		 * Remove the element at position from and insert it at position to.
		 */
		public void move(int from, int to);
		/**
		 * Remove the element at the given position.
		 */
		public void remove(int position);
	}

	/**
	 * Sent to scroll the list up or down when the dragged view is near the
	 * top or bottom of the list.
	 */
	private static final int MSG_SCROLL = 0;
	/**
	 * Height of each row in dip.
	 */
	public static final int ROW_HEIGHT = 44;
	/**
	 * Padding for each row in dip.
	 */
	public static final int PADDING = 3;
	/**
	 * Background color of row while it is being dragged.
	 */
	public static final int DRAG_COLOR = 0xff005500;
	/**
	 * A handler running on the UI thread.
	 */
	private final Handler mHandler = new Handler(this);
	/**
	 * The system window manager instance.
	 */
	private WindowManager mWindowManager;
	/**
	 * The adapter that will be called to move/remove rows.
	 */
	private DragAdapter mAdapter;
	/**
	 * True to allow dragging; false otherwise.
	 */
	private boolean mEditable;
	/**
	 * Scaled height of each row in pixels.
	 */
	private final int mRowHeight;
	/**
	 * The view that is actually dragged around during a drag. (The original
	 * view is hidden).
	 */
	private ImageView mDragView;
	/**
	 * A copy of the dragged row's scrolling cache that is shown in mDragView.
	 */
	private Bitmap mDragBitmap;
	/**
	 * Window params for the drag view window. Used to move the window around.
	 */
	private WindowManager.LayoutParams mWindowParams;
	/**
	 * At which position is the item currently being dragged. Note that this
	 * takes in to account header items.
	 */
	private int mDragPos;
	/**
	 * At which position was the item being dragged originally
	 */
	private int mSrcDragPos;
	/**
	 * At what y offset inside the dragged view did the user grab it.
	 */
	private int mDragPointY;
	/**
	 * The difference between screen coordinates and coordinates in the drag
	 * view.
	 */
	private int mYOffset;
	/**
	 * The y coordinate of the top of the drag view after the last motion
	 * event.
	 */
	private int mLastMotionY;
	/**
	 * Default padding for rows.
	 */
	private final int mPadding;

	public DragListView(Context context, AttributeSet attrs)
	{
		super(context, attrs);

		float density = context.getResources().getDisplayMetrics().density;
		mPadding = (int)(PADDING * density);
		mRowHeight = (int)(ROW_HEIGHT * density);
	}

	/**
	 * This should be called instead of
	 * {@link ListView#setAdapter(android.widget.ListAdapter)}.
	 * DragListView requires a DragAdapter to handle move/remove callbacks
	 * from dragging.
	 *
	 * @param adapter The adapter to use. Will be passed to
	 * {@link ListView#setAdapter(android.widget.ListAdapter)}.
	 */
	public void setAdapter(DragAdapter adapter)
	{
		super.setAdapter(adapter);
		// Keep track of adapter here since getAdapter() will return a wrapper
		// when there are headers.
		mAdapter = adapter;
	}

	/**
	 * Set whether to allow elements to be reordered.
	 *
	 * @param editable True to allow reordering.
	 */
	public void setEditable(boolean editable)
	{
		mEditable = editable;
		if (!editable)
			stopDragging();
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent ev)
	{
		if (mEditable) {
			switch (ev.getAction()) {
			case MotionEvent.ACTION_DOWN:
				stopDragging();

				int x = (int)ev.getX();
				// The left quarter of the item is the grabber for dragging the item
				if (x < getWidth() / 4) {
					int item = pointToPosition(x, (int)ev.getY());
					if (item != AdapterView.INVALID_POSITION && item >= getHeaderViewsCount()) {
						startDragging(item, ev);
						return false;
					}
				}
				break;
			}
		}

		return super.onInterceptTouchEvent(ev);
	}

	@Override
	public boolean onTouchEvent(MotionEvent ev)
	{
		if (!mEditable || mDragView == null)
			return super.onTouchEvent(ev);

		switch (ev.getAction()) {
		case MotionEvent.ACTION_UP:
		case MotionEvent.ACTION_CANCEL:
			stopDragging();
			int offset = getHeaderViewsCount();
			if (mDragPos >= offset && mDragPos < getCount())
				mAdapter.move(mSrcDragPos - offset, mDragPos - offset);
			break;
		case MotionEvent.ACTION_MOVE:
			int y = (int)ev.getY() - mDragPointY;
			mLastMotionY = y;
			mWindowParams.x = 0;
			mWindowParams.y = y + mYOffset;
			mWindowManager.updateViewLayout(mDragView, mWindowParams);
			computeDragPosition(y);
			break;
		}

		return true;
	}

	/**
	 * Restore size and visibility for all list items
	 */
	private void unExpandViews()
	{
		int padding = mPadding;
		for (int i = 0, count = getChildCount(); i != count; ++i) {
			View view = getChildAt(i);
			ViewGroup.LayoutParams params = view.getLayoutParams();
			params.height = 0;
			view.setLayoutParams(params);
			view.setVisibility(View.VISIBLE);
			view.setPadding(padding, padding, padding, padding);
		}
	}

	/**
	 * Adjust visibility and size to make it appear as though
	 * an item is being dragged around and other items are making
	 * room for it.
	 *
	 * If dropping the item would result in it still being in the
	 * same place, then make the dragged list item's size normal,
	 * but make the item invisible.
	 * Otherwise, if the dragged list item is still on screen, make
	 * it as small as possible and expand the item below the insert
	 * point.
	 */
	private void doExpansion()
	{
		int firstVisibile = getFirstVisiblePosition();
		int childNum = mDragPos - firstVisibile;
		if (mDragPos > mSrcDragPos)
			childNum += 1;

		int headerCount = getHeaderViewsCount();
		int childCount = getChildCount();

		View dragSrcView = getChildAt(mSrcDragPos - firstVisibile);

		int start = firstVisibile < headerCount ? headerCount - firstVisibile : 0;
		int padding = mPadding;
		int rowHeight = mRowHeight;
		int nextHeight = rowHeight;

		for (int i = start; i != childCount; ++i) {
			View view = getChildAt(i);

			int height = nextHeight;
			nextHeight = rowHeight;
			int visibility = View.VISIBLE;
			int paddingBottom = padding;
			int paddingTop = padding;

			if (view == dragSrcView) {
				if (mDragPos == mSrcDragPos) {
					// hovering over the original location: show empty space
					visibility = View.INVISIBLE;
					height += 1;
				} else {
					// not hovering over it: show nothing
					// Ideally the item would be completely gone, but neither
					// setting its size to 0 nor settings visibility to GONE
					// has the desired effect.
					height = 1;
				}
				nextHeight -= 1;
			} else if (i == childNum) {
				// hovering over this row; expand it to "make room" for the
				// dragged item
				paddingTop += height;
				height *= 2;
			} else if (childNum == childCount && i == childCount - 1) {
				// hovering over the bottom of the list: we need to "make room"
				// at the bottom
				paddingBottom += height;
				height *= 2;
			}

			view.setPadding(padding, paddingTop, padding, paddingBottom);
			view.setVisibility(visibility);
			ViewGroup.LayoutParams params = view.getLayoutParams();
			params.height = height;
			view.setLayoutParams(params);
		}
	}

	/**
	 * Computes the drag position based on where the drag view is hovering.
	 * Expands views and updates scrolling when this position changes.
	 *
	 * @param y The y coordinate of the top of the drag view.
	 * @return The scrolling speed in pixels
	 */
	private int computeDragPosition(int y)
	{
		// This assumes uniform height for all non-header rows
		int firstVisible = getFirstVisiblePosition();
		int topPos = Math.max(getHeaderViewsCount(), firstVisible);
		int dragHeight = mRowHeight;
		View view = getChildAt(topPos - firstVisible);
		int viewMiddle = view.getTop() + dragHeight / 2;
		int dragPos = Math.min(getCount() - 1, topPos + Math.max(0, y - viewMiddle + dragHeight) / dragHeight);

		if (dragPos != mDragPos) {
			mDragPos = dragPos;
			doExpansion();
		}

		int height = getHeight();
		int upperBound = height / 4;
		int lowerBound = height * 3 / 4;

		if (y > lowerBound && (getLastVisiblePosition() < getCount() - 1 || getChildAt(getChildCount() - 1).getBottom() > getBottom()))
			return y > (height + lowerBound) / 2 ? 16 : 4;
		else if (y < upperBound && (getFirstVisiblePosition() != 0 || getChildAt(0).getTop() < 0))
			return y < upperBound / 2 ? -16 : -4;

		return 0;
	}

	/**
	 * Start a drag operation.
	 *
	 * @param row The row number of the item to drag
	 * @param ev The touch event that started this drag.
	 */
	private void startDragging(int row, MotionEvent ev)
	{
		int y = (int)ev.getY();

		View item = getChildAt(row - getFirstVisiblePosition());
		mDragPointY = y - item.getTop();
		mYOffset = (int)ev.getRawY() - y;

		mWindowParams = new WindowManager.LayoutParams();
		mWindowParams.gravity = Gravity.TOP | Gravity.LEFT;
		mWindowParams.x = 0;
		mWindowParams.y = y - mDragPointY + mYOffset;

		mWindowParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
		mWindowParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
		mWindowParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
				| WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
				| WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
				| WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
				| WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
		mWindowParams.windowAnimations = 0;

		int color = item.getDrawingCacheBackgroundColor();
		item.setDrawingCacheBackgroundColor(0xff005500);
		item.buildDrawingCache();
		// Create a copy of the drawing cache so that it does not get recycled
		// by the framework when the list tries to clean up memory
		Bitmap bitmap = Bitmap.createBitmap(item.getDrawingCache());
		item.setDrawingCacheBackgroundColor(color);
		item.destroyDrawingCache();
		mDragBitmap = bitmap;

		Context context = getContext();
		ImageView view = new ImageView(context);
		view.setPadding(0, 0, 0, 0);
		view.setImageBitmap(bitmap);

		mWindowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
		mWindowManager.addView(view, mWindowParams);
		mDragView = view;

		mSrcDragPos = row;
		// Force expansion on next motion event
		mDragPos = INVALID_POSITION;

		mHandler.sendEmptyMessageDelayed(MSG_SCROLL, 50);
	}

	/**
	 * Stop a drag operation.
	 */
	private void stopDragging()
	{
		if (mDragView != null) {
			mDragView.setVisibility(GONE);
			mWindowManager.removeView(mDragView);
			mDragView.setImageDrawable(null);
			mDragView = null;
		}
		if (mDragBitmap != null) {
			mDragBitmap.recycle();
			mDragBitmap = null;
		}
		unExpandViews();
		mHandler.removeMessages(MSG_SCROLL);
	}

	@Override
	public boolean handleMessage(Message message)
	{
		if (message.what == MSG_SCROLL) {
			if (mDragPos != INVALID_POSITION) {
				int speed = computeDragPosition(mLastMotionY);
				if (speed != 0) {
					View view = getChildAt(0);
					if (view != null) {
						int pos = view.getTop();
						setSelectionFromTop(getFirstVisiblePosition(), pos - speed);
					}
				}
			}
			mHandler.sendEmptyMessageDelayed(MSG_SCROLL, 50);
		}

		return true;
	}
}
