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

package org.kreed.vanilla;

import android.provider.MediaStore;
import android.widget.AlphabetIndexer;

/**
 * Handles comparisons in a different way because the Album, Song and Artist name
 * are stripped of some prefixes such as "a", "an", "the" and some symbols.
 */
class MusicAlphabetIndexer extends AlphabetIndexer {
	public MusicAlphabetIndexer(int sortedColumnIndex)
	{
		super(null, sortedColumnIndex, " ABCDEFGHIJKLMNOPQRSTUVWXYZ");
	}

	@Override
	protected int compare(String word, String letter)
	{
		String wordKey = MediaStore.Audio.keyFor(word);
		String letterKey = MediaStore.Audio.keyFor(letter);
		return wordKey.compareTo(letterKey);
	}
}
