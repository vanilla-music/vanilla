/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ch.blinkenlights.android.vanilla;

import android.database.Cursor;
import android.provider.MediaStore;
import java.util.Arrays;

/**
 * Like android.widget.AlphabetIndexer, but handles MediaStore sorting order
 * (strips "a", "the", etc).
 */
public class MusicAlphabetIndexer {
	/**
	 * The indexing sections.
	 */
	private static final String[] ALPHABET =
		{ " ", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
		       "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z" };
	/**
	 * The result of {@link android.provider.MediaStore.Audio#keyFor(String)} called on each
	 * letter of the alphabet.
	 */
	private static String[] ALPHABET_KEYS = null;
	/**
	 * Cached section postions.
	 */
	private final int mAlphaMap[];
	/**
	 * Cursor that is used by the adapter of the list view.
	 */
	private Cursor mDataCursor;
	/**
	 * The index of the cursor column that this list is sorted on.
	 */
	private final int mColumnIndex;

	/**
	 * Constructs the indexer.
	 *
	 * @param sortedColumnIndex the column number in the cursor that is sorted
	 * alphabetically
	 */
	public MusicAlphabetIndexer(int sortedColumnIndex)
	{
		if (ALPHABET_KEYS == null) {
			String[] keys = new String[ALPHABET.length];
			for (int i = ALPHABET.length; --i != -1; ) {
				keys[i] = MediaStore.Audio.keyFor(ALPHABET[i]);
			}
			ALPHABET_KEYS = keys;
		}

		mColumnIndex = sortedColumnIndex;
		mAlphaMap = new int[ALPHABET.length];
	}

	/**
	 * Returns the latin alphabet.
	 */
	public static Object[] getSections()
	{
		return ALPHABET;
	}

	/**
	 * Sets a new cursor as the data set and resets the cache of indices.
	 *
	 * @param cursor the new cursor to use as the data set
	 */
	public void setCursor(Cursor cursor)
	{
		mDataCursor = cursor;
		Arrays.fill(mAlphaMap, -1);
	}

	/**
	 * Performs a binary search or cache lookup to find the first row that
	 * matches a given section's starting letter.
	 *
	 * @param sectionIndex the section to search for
	 * @return the row index of the first occurrence, or the nearest next letter.
	 * For instance, if searching for "T" and no "T" is found, then the first
	 * row starting with "U" or any higher letter is returned. If there is no
	 * data following "T" at all, then the list size is returned.
	 */
	public int getPositionForSection(int sectionIndex) {
		Cursor cursor = mDataCursor;
		if (cursor == null)
			return 0;

		if (sectionIndex <= 0)
			return 0;
		int count = cursor.getCount();
		if (sectionIndex >= ALPHABET.length)
			return count;

		int pos = mAlphaMap[sectionIndex];
		if (pos != -1)
			return pos;

		int savedCursorPos = cursor.getPosition();

		int start = mAlphaMap[sectionIndex - 1];
		int end = count;
		if (start == -1)
			start = 0;

		String targetLetter = ALPHABET_KEYS[sectionIndex];
		pos = (end + start) / 2;
		while (pos < end) {
			cursor.moveToPosition(pos);
			String curName   = cursor.getString(mColumnIndex);
			String curKey    = MediaStore.Audio.keyFor(curName);

			String curLetter = ( curKey != null && curKey.length() >= 3 ? curKey.substring(0, 3) : "\t~\t"); /* return fake info if there was no key */
			int diff         = curLetter.compareTo(targetLetter);
			if (diff != 0) {
				if (diff < 0) {
					start = pos + 1;
					if (start >= count) {
						pos = count;
						break;
					}
				} else {
					end = pos;
				}
			} else {
				// They're the same, but that doesn't mean it's the start
				if (start == pos) {
					// This is it
					break;
				} else {
					// Need to go further lower to find the starting row
					end = pos;
				}
			}
			pos = (start + end) / 2;
		}

		mAlphaMap[sectionIndex] = pos;
		cursor.moveToPosition(savedCursorPos);
		return pos;
	}

	/**
	 * Returns the section index for a given position in the list by querying the item
	 * and comparing it with all items in the section array.
	 */
	public int getSectionForPosition(int position)
	{
		int savedCursorPos = mDataCursor.getPosition();
		mDataCursor.moveToPosition(position);
		String key = MediaStore.Audio.keyFor(mDataCursor.getString(mColumnIndex));
		mDataCursor.moveToPosition(savedCursorPos);

		String[] alphabet = ALPHABET_KEYS;

		if (key != null) { // can this really be null? google thinks so :-/
			for (int i = 1, len = alphabet.length; i != len; ++i) {
				if (key.startsWith(alphabet[i]))
					return i;
			}
		}

		return 0;
	}
}
