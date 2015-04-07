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

package ch.blinkenlights.android.vanilla;

import android.content.SharedPreferences;

/**
 * Various actions that can be passed to {@link PlaybackService#performAction(Action, PlaybackActivity)}.
 */
enum Action {
	/**
	 * Dummy action: do nothing.
	 */
	Nothing,
	/**
	 * Open the library activity.
	 */
	Library,
	/**
	 * If playing music, pause. Otherwise, start playing.
	 */
	PlayPause,
	/**
	 * Skip to the next song.
	 */
	NextSong,
	/**
	 * Go back to the previous song.
	 */
	PreviousSong,
	/**
	 * Skip to the first song from the next album.
	 */
	NextAlbum,
	/**
	 * Skip to the last song from the previous album.
	 */
	PreviousAlbum,
	/**
	 * Cycle the repeat mode.
	 */
	Repeat,
	/**
	 * Cycle the shuffle mode.
	 */
	Shuffle,
	/**
	 * Enqueue the rest of the current album.
	 */
	EnqueueAlbum,
	/**
	 * Enqueue the rest of the songs by the current artist.
	 */
	EnqueueArtist,
	/**
	 * Enqueue the rest of the songs in the current genre.
	 */
	EnqueueGenre,
	/**
	 * Clear the queue of all remaining songs.
	 */
	ClearQueue,
	/**
	 * Show the queue.
	 */
	ShowQueue,
	/**
	 * Toggle the controls in the playback activity.
	 */
	ToggleControls;

	/**
	 * Retrieve an action from the given SharedPreferences.
	 *
	 * @param prefs The SharedPreferences instance to load from.
	 * @param key The preference key to load.
	 * @param def The value to return if the key is not found or cannot be loaded.
	 * @return The loaded action or def if no action could be loaded.
	 */
	public static Action getAction(SharedPreferences prefs, String key, Action def)
	{
		try {
			String pref = prefs.getString(key, null);
			if (pref == null)
				return def;
			return Enum.valueOf(Action.class, pref);
		} catch (Exception e) {
			return def;
		}
	}
}