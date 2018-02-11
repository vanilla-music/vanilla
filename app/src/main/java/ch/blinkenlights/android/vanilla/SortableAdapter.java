/*
 * Copyright (C) 2017 Adrian Ulrich <adrian@blinkenlights.ch>
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

import android.widget.BaseAdapter;

/**
 * Abstract adapter that implements sorting for its data.
 */
public abstract class SortableAdapter extends BaseAdapter {
    /**
     * The human-readable descriptions for each sort mode.
     */
    int[] mSortEntries;
    /**
     * The index of the current sort mode in mSortValues, or
     * the inverse of the index (in which case sort should be descending
     * instead of ascending).
     */
    int mSortMode;

    /**
     * Return the available sort modes for this adapter.
     *
     * @return An array containing the resource ids of the sort mode strings.
     */
    public int[] getSortEntries() {
        return mSortEntries;
    }

    /**
     * Set the sorting mode. The adapter should be re-queried after changing
     * this.
     *
     * @param mode The index of the sort mode in the sort entries array. If this
     * is negative, the inverse of the index will be used and sort order will
     * be reversed.
     */
    public void setSortMode(int mode) {
        mSortMode = getIndexForSortMode(mode) < mSortEntries.length ? mode : 0;
    }

    /**
     * Return the current sort mode set on this adapter.
     * This is the index or the bitwise inverse of the index if the sort is descending.
     */
    public int getSortMode() {
        return mSortMode;
    }

    /**
     * Return the current sort mode index.
     */
    public int getSortModeIndex() {
        return getIndexForSortMode(mSortMode);
    }

    /**
     * Return if the current sort mode is reversed.
     */
    public boolean isSortDescending() {
        return mSortMode < 0;
    }

    /**
     * Returns the sort mode that should be used if no preference is saved. This
     * may very based on the active limiter.
     */
    abstract public int getDefaultSortMode();

    /**
     * Return the key used for loading/saving the sort mode for this adapter.
     */
    abstract public String getSortSettingsKey();

    private int getIndexForSortMode(int mode) {
        return mode < 0 ? ~mode : mode; // 'negative' modes are actually inverted indexes
    }
}
