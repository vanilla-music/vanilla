/*
 * Copyright (C) 2011 Christopher Eby <kreed@kreed.org>
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

package ch.blinkenlights.android.vanilla;

import java.io.Serializable;

/**
 * Limiter is a constraint for MediaAdapter and FileSystemAdapter used when
 * a row is "expanded".
 */
public class Limiter implements Serializable {
	private static final long serialVersionUID = -4729694243900202614L;

	/**
	 * The type of the limiter. One of MediaUtils.TYPE_ARTIST, TYPE_ALBUM,
	 * TYPE_GENRE, or TYPE_FILE.
	 */
	public final int type;
	/**
	 * Each element will be given a separate view each representing a higher
	 * different limiters. The first element is the broadest limiter, the last
	 * the most specific. For example, an album limiter would look like:
	 * { "Some Artist", "Some Album" }
	 * Or a file limiter:
	 * { "sdcard", "Music", "folder" }
	 */
	public final String[] names;
	/**
	 * The data for the limiter. This varies according to the type of the
	 * limiter.
	 */
	public final Object data;

	/**
	 * Create a limiter with the given data. All parameters initialize their
	 * corresponding fields in the class.
	 */
	public Limiter(int type, String[] names, Object data)
	{
		this.type = type;
		this.names = names;
		this.data = data;
	}
}