/*
 * Copyright (C) 2018 Adrian Ulrich <adrian@blinkenlights.ch>
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

package ch.blinkenlights.android.vanilla.ext;

import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.AdapterView;
import android.widget.ListView;


public class CoordClickListener
	implements
	    AdapterView.OnItemLongClickListener
	  , View.OnTouchListener {
	/**
	 * Interface to implement by the callback
	 */
	public interface Callback {
		boolean onItemLongClickWithCoords(AdapterView<?> parent, View view, int position, long id, float x, float y);
	}
	/**
	 * The registered callback consumer
	 */
	private Callback mCallback;
	/**
	 * Last X position seen.
	 */
	private float mPosX;
	/**
	 * Last Y position seen.
	 */
	private float mPosY;

	/**
	 * Create a new CoordClickListener instance
	 *
	 * @param cb the callback consumer.
	 */
	public CoordClickListener(Callback cb) {
		mCallback = cb;
	}

	/**
	 * Register a view for long click observation.
	 *
	 * @param view the view to listen for long clicks
	 */
	public void registerForOnItemLongClickListener(ListView view) {
		view.setOnItemLongClickListener(this);
		view.setOnTouchListener(this);
	}

	/**
	 * Implementation of onItemLongClickListener interface
	 */
	@Override
	public boolean onItemLongClick (AdapterView<?>parent, View view, int position, long id) {
		return mCallback.onItemLongClickWithCoords(parent, view, position, id, mPosX, mPosY);
	}

	/**
	 * Implementation of OnTouchListener interface
	 */
	@Override
	public boolean onTouch(View view, MotionEvent ev) {
		mPosX = ev.getX();
		mPosY = ev.getY();
		// Not handled: we just observe.
		return false;
	}
}
