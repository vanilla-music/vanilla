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

import org.kreed.vanilla.R;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ImageView;

public class MiniPlaybackActivity extends PlaybackActivity implements View.OnClickListener {
	private View mOpenButton;
	private View mKillButton;
	private View mPreviousButton;
	private ImageView mPlayPauseButton;
	private View mNextButton;

	@Override
	public void onCreate(Bundle state)
	{
		super.onCreate(state);

		requestWindowFeature(Window.FEATURE_NO_TITLE); 
		setContentView(R.layout.mini_playback);

		mCoverView = (CoverView)findViewById(R.id.cover_view);

		mOpenButton = findViewById(R.id.open_button);
		mOpenButton.setOnClickListener(this);
		mKillButton = findViewById(R.id.kill_button);
		mKillButton.setOnClickListener(this);
		mPreviousButton = findViewById(R.id.previous);
		mPreviousButton.setOnClickListener(this);
		mPlayPauseButton = (ImageView)findViewById(R.id.play_pause);
		mPlayPauseButton.setOnClickListener(this);
		mNextButton = findViewById(R.id.next);
		mNextButton.setOnClickListener(this);
	}

	@Override
	protected void setState(int state)
	{
		if (state == PlaybackService.STATE_NO_MEDIA)
			finish();

		mPlayPauseButton.setImageResource(state == PlaybackService.STATE_PLAYING ? R.drawable.pause : R.drawable.play);
	}

	public void onClick(View view)
	{
		if (view == mKillButton) {
			ContextApplication.quit(this);
		} else if (view == mOpenButton) {
			startActivity(new Intent(this, FullPlaybackActivity.class));
			finish();
		} else {
			try {
				if (view == mNextButton)
					mCoverView.go(1);
				else if (view == mPreviousButton)
					mCoverView.go(-1);
				else if (view == mPlayPauseButton)
					mCoverView.go(0);
			} catch (RemoteException e) {
				Log.e("VanillaMusic", "service dead", e);
				finish();
			}
		}
	}
}

/*
 * Custom layout that acts like a very simple vertical LinearLayout with
 * special case: CoverViews will be made square at all costs.
 * 
 * I would prefer this to be a nested class, but it does not seem like
 * Android's layout inflater supports referencing nested classes in XML.
 */
class MiniPlaybackActivityLayout extends ViewGroup {
	private int mCoverSize;

	public MiniPlaybackActivityLayout(Context context, AttributeSet attrs)
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