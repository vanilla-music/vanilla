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

package org.kreed.vanilla;

import android.annotation.TargetApi;
import android.media.MediaMetadataRetriever;

/**
 * Wrapper around MediaMetadataRetriever to allow compatibility with older
 * versions of Android.
 */
@TargetApi(10)
public class CompatMetadata {
	/**
	 * The wrapped MediaMetadataRetriever instance.
	 */
	private final MediaMetadataRetriever mData;

	/**
	 * Create an MediaMetadataRetriever attached to the file at the given path.
	 */
	public CompatMetadata(String path)
	{
		MediaMetadataRetriever data = new MediaMetadataRetriever();
		data.setDataSource(path);
		mData = data;
	}

	/**
	 * Call {@link MediaMetadataRetriever#extractMetadata(int)}.
	 */
	public String extractMetadata(int keyCode)
	{
		return mData.extractMetadata(keyCode);
	}

	/**
	 * Call {@link MediaMetadataRetriever#release()}.
	 */
	public void release()
	{
		mData.release();
	}
}
