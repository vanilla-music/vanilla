package org.kreed.tumult;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

/*
 * RemoteLayout acts like a very simple vertical LinearLayout with special
 * case: all CoverViews placed will be made square at all costs.
 */

public class RemoteLayout extends ViewGroup {
	private int mCoverSize;

	public RemoteLayout(Context context, AttributeSet attrs)
	{
		super(context, attrs);
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		int maxHeight = MeasureSpec.getSize(heightMeasureSpec);

		int measuredHeight = 0;
		int measuredWidth = 0;

		View coverView = null;
		for (int i = getChildCount(); --i != -1; ) {
			View view = getChildAt(i);
			if (view instanceof CoverView) {
				coverView = view;
			} else {
				int spec = MeasureSpec.makeMeasureSpec(maxHeight - measuredHeight, MeasureSpec.AT_MOST);
				view.measure(widthMeasureSpec, spec);
				measuredHeight += view.getMeasuredHeight();
				if (view.getMeasuredWidth() > measuredWidth)
					measuredWidth = view.getMeasuredWidth();
			}
		}

		if (coverView != null) {
			if (measuredHeight + measuredWidth > maxHeight) {
				mCoverSize = maxHeight - measuredHeight;
				measuredHeight = maxHeight;
			} else {
				mCoverSize = measuredWidth;
				measuredHeight += measuredWidth;
			}
		}

		setMeasuredDimension(measuredWidth, measuredHeight);
	}

	@Override
	protected void onLayout(boolean arg0, int arg1, int arg2, int arg3, int arg4)
	{
		int layoutWidth = getMeasuredWidth();
		int top = 0;

		for (int i = 0, end = getChildCount(); i != end; ++i) {
			View view = getChildAt(i);
			if (view instanceof CoverView) {
				view.layout(0, top, layoutWidth, top + mCoverSize);
				top += mCoverSize;
			} else {
				int height = view.getMeasuredHeight();
				int width = view.getMeasuredWidth();
				int left = (layoutWidth - width) / 2;
				view.layout(left, top, layoutWidth - left, top + height);
				top += height;
			}
		}
	}
}