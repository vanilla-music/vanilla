/*
 * Copyright (C) 2010 Christopher Eby <kreed@kreed.org>
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

import java.util.Arrays;

/**
 * A key/value map that discards old items. When the capacity of the cache is
 * reached, the oldest item (by insertion time) will be discarded.
 *
 * Keys should be non-negative.
 *
 * @param <E> The type of the values. (Keys will be longs).
 */
public class Cache<E> {
	/**
	 * The keys contained in the cache, stored in the order of insertion.
	 */
	private final long[] mKeys;
	/**
	 * The values contained in the cache, stored in a location corresponding
	 * to the keys in mKeys.
	 */
	private final Object[] mValues;

	/**
	 * Create a Cache.
	 *
	 * @param capacity The capacity of the cache. This is fixed and may not be
	 * changed after construction.
	 */
	public Cache(int capacity)
	{
		mKeys = new long[capacity];
		mValues = new Object[capacity];
		Arrays.fill(mKeys, -1);
	}

	/**
	 * Calculate the number of items in the cache.
	 *
	 * @return The number of items in the cache.
	 */
	private int count()
	{
		long[] keys = mKeys;
		int count = keys.length;
		while (--count != -1 && keys[count] == -1);
		return count + 1;
	}

	/**
	 * Find the index of the given key.
	 *
	 * @param key The key to search for.
	 * @return The index, or -1 if the key was not found.
	 */
	private int indexOf(long key)
	{
		long[] keys = mKeys;
		for (int i = keys.length; --i != -1; )
			if (keys[i] == key)
				return i;
		return -1;
	}

	/**
	 * Retrieve the value with the given key.
	 *
	 * @param key The key to search with.
	 * @return The value, or null if the given key is not contained in this
	 * cache.
	 */
	@SuppressWarnings("unchecked")
	public E get(long key)
	{
		if (key < 0)
			throw new IllegalArgumentException("Keys must be non-negative.");

		int i = indexOf(key);
		return i == -1 ? null : (E)mValues[i];
	}

	/**
	 * Reset the age of the item with the given key so that it will be
	 * discarded last.
	 *
	 * @param key The key of the item to touch.
	 */
	public void touch(long key)
	{
		if (key < 0)
			throw new IllegalArgumentException("Keys must be non-negative.");

		long[] keys = mKeys;
		Object[] values = mValues;

		int oldPos = indexOf(key);
		int newPos = count() - 1;

		if (oldPos != newPos && oldPos != -1) {
			Object value = values[oldPos];
			System.arraycopy(keys, oldPos + 1, keys, oldPos, newPos - oldPos);
			System.arraycopy(values, oldPos + 1, values, oldPos, newPos - oldPos);
			keys[newPos] = key;
			values[newPos] = value;
		}
	}

	/**
	 * Discard the oldest item in the cache. Does nothing if the cache is not
	 * full.
	 *
	 * @return The item that was discarded, or null if the cache is not full.
	 */
	@SuppressWarnings("unchecked")
	public E discardOldest()
	{
		int count = count();
		// Cache is not full.
		if (count != mKeys.length)
			return null;

		E removed = (E)mValues[0];
		System.arraycopy(mKeys, 1, mKeys, 0, count - 1);
		System.arraycopy(mValues, 1, mValues, 0, count - 1);
		mKeys[mKeys.length - 1] = -1;

		return removed;
	}

	/**
	 * Insert an item into the cache. If the cache is full, the oldest item
	 * will be discarded.
	 *
	 * @param key The key to place the item at. Must be a duplicate of an
	 * existing key in the cache.
	 * @param value The item.
	 * @return The discarded item, or null if no items were discarded.
	 */
	public E put(long key, E value)
	{
		if (key < 0)
			throw new IllegalArgumentException("Keys must be non-negative.");
		
		E discarded = discardOldest();
		int count = count();
		mKeys[count] = key;
		mValues[count] = value;
		return discarded;
	}

	/**
	 * Clear the keys and return the values to be cleared by the caller.
	 *
	 * @return The values in the cache, untouched.
	 */
	public Object[] clear()
	{
		Arrays.fill(mKeys, -1);
		return mValues;
	}
}
