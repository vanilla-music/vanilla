/*
 * Copyright (C) 2012 Christopher Eby <kreed@kreed.org>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package android.support.v4.view;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.widget.EdgeEffect;

/**
 * Wrapper around {@link EdgeEffect} for compatibility with older versions of
 * Android.
 */
@TargetApi(14)
public class EdgeEffectCompat {
	/**
	 * The wrapped EdgeEffect.
	 */
	private EdgeEffect mEdge;

	/**
	 * Call {@link EdgeEffect#EdgeEffect(Context)}.
	 */
	public EdgeEffectCompat(Context context)
	{
		mEdge = new EdgeEffect(context);
	}

	/**
	 * Call {@link EdgeEffect#onPull(float)}.
	 */
	public void onPull(float deltaDistance)
	{
		mEdge.onPull(deltaDistance);
	}

	/**
	 * Call {@link EdgeEffect#finish()}.
	 */
	public void finish()
	{
		mEdge.finish();
	}

	/**
	 * Call {@link EdgeEffect#isFinished()}.
	 */
	public boolean isFinished()
	{
		return mEdge.isFinished();
	}

	/**
	 * Call {@link EdgeEffect#onRelease()}.
	 */
	public void onRelease()
	{
		mEdge.onRelease();
	}

	/**
	 * Call {@link EdgeEffect#draw(Canvas)}.
	 */
	public boolean draw(Canvas canvas)
	{
		return mEdge.draw(canvas);
	}

	/**
	 * Call {@link EdgeEffect#setSize(int,int)}.
	 */
	public void setSize(int width, int height)
	{
		mEdge.setSize(width, height);
	}

	/**
	 * Call {@link View#getOverScrollMode()}.
	 *
	 * (This is unrelated to EdgeEffect; it's thrown in here since ViewPager
	 * also uses it.)
	 */
	public static int getOverScrollMode(View view)
	{
		return view.getOverScrollMode();
	}

	/**
	 * Call {@link View#canScrollHorizontally(int)}.
	 *
	 * (This is unrelated to EdgeEffect; it's thrown in here since ViewPager
	 * also uses it.)
	 */
	public static boolean canScrollHorizontally(View view, int direction)
	{
		return view.canScrollHorizontally(direction);
	}
}
