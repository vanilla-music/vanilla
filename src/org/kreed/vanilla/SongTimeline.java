/*
 * Copyright (C) 2010, 2011 Christopher Eby <kreed@kreed.org>
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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

/**
 * Represents a series of songs that can be moved through backward or forward.
 * Automatically handles the fetching of new (random) songs when a song does not
 * exist at a requested position.
 */
public final class SongTimeline {
	/**
	 * Stop playback.
	 *
	 * @see SongTimeline#setFinishAction(int)
	 */
	public static final int FINISH_STOP = 0;
	/**
	 * Repeat from the begining.
	 *
	 * @see SongTimeline#setFinishAction(int)
	 */
	public static final int FINISH_REPEAT = 1;
	/**
	 * Add random songs to the playlist.
	 *
	 * @see SongTimeline#setFinishAction(int)
	 */
	public static final int FINISH_RANDOM = 2;

	/**
	 * Clear the timeline and use only the provided songs.
	 *
	 * @see SongTimeline#addSongs(int,Cursor)
	 */
	public static final int MODE_PLAY = 0;
	/**
	 * Clear the queue and add the songs after the current song.
	 *
	 * @see SongTimeline#addSongs(int,Cursor)
	 */
	public static final int MODE_PLAY_NEXT = 1;
	/**
	 * Add the songs at the end of the timeline, clearing random songs.
	 *
	 * @see SongTimeline#addSongs(int,Cursor)
	 */
	public static final int MODE_ENQUEUE = 2;

	/**
	 * Name of the state file.
	 */
	private static final String STATE_FILE = "state";
	/**
	 * Header for state file to help indicate if the file is in the right
	 * format.
	 */
	private static final long STATE_FILE_MAGIC = 0xf89daa2fac33L;

	private Context mContext;

	/**
	 * All the songs currently contained in the timeline. Each Song object
	 * should be unique, even if it refers to the same media.
	 */
	private ArrayList<Song> mSongs;
	/**
	 * The position of the current song (i.e. the playing song).
	 */
	private int mCurrentPos;
	/**
	 * Whether shuffling is enabled. Shuffling will shuffle sets of songs
	 * that are added with chooseSongs and shuffle sets of repeated songs.
	 */
	private boolean mShuffle;
	/**
	 * What to do when the end of the playlist is reached.
	 * Must be one of SongTimeline.FINISH_*.
	 */
	private int mFinishAction;

	// for shuffleAll()
	private ArrayList<Song> mShuffledSongs;

	// for saveActiveSongs()
	private Song mSavedPrevious;
	private Song mSavedCurrent;
	private Song mSavedNext;

	public interface Callback {
		/**
		 * Called when an active song in the timeline is replaced by a method
		 * other than shiftCurrentSong()
		 *
		 * @param delta The distance from the current song. Will always be -1,
		 * 0, or 1.
		 * @param song The new song at the position
		 */
		public void activeSongReplaced(int delta, Song song);

		/**
		 * Called when the timeline state has changed and should be saved to
		 * storage.
		 */
		public void timelineChanged();
	}
	/**
	 * The current Callback, if any.
	 */
	private Callback mCallback;

	public SongTimeline(Context context)
	{
		mContext = context;
	}

	/**
	 * Compares the ids of songs.
	 */
	public static class IdComparator implements Comparator<Song> {
		@Override
		public int compare(Song a, Song b)
		{
			if (a.id == b.id)
				return 0;
			if (a.id > b.id)
				return 1;
			return -1;
		}
	}

	/**
	 * Compares the flags of songs.
	 */
	public static class FlagComparator implements Comparator<Song> {
		@Override
		public int compare(Song a, Song b)
		{
			return a.flags - b.flags;
		}
	}

	/**
	 * Initializes the timeline with the state stored in the state file created
	 * by a call to save state.
	 *
	 * @return The optional extra data, or -1 if loading failed
	 */
	public int loadState()
	{
		int extra = -1;

		try {
			synchronized (this) {
				DataInputStream in = new DataInputStream(mContext.openFileInput(STATE_FILE));
				if (in.readLong() == STATE_FILE_MAGIC) {
					int n = in.readInt();
					if (n > 0) {
						ArrayList<Song> songs = new ArrayList<Song>(n);

						// Fill the selection with the ids of all the saved songs
						// and initialize the timeline with unpopulated songs.
						StringBuilder selection = new StringBuilder("_ID IN (");
						for (int i = 0; i != n; ++i) {
							long id = in.readLong();
							int flags = in.readInt();
							// Add the index so we can sort
							flags |= i << Song.FLAG_COUNT;
							songs.add(new Song(id, flags));

							if (i != 0)
								selection.append(',');
							selection.append(id);
						}
						selection.append(')');

						// Sort songs by id---this is the order the query will
						// return its results in.
						Collections.sort(songs, new IdComparator());

						ContentResolver resolver = mContext.getContentResolver();
						Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

						Cursor cursor = resolver.query(media, Song.FILLED_PROJECTION, selection.toString(), null, "_id");
						if (cursor != null) {
							cursor.moveToNext();

							// Loop through timeline entries, looking for a row
							// that matches the id. One row may match multiple
							// entries.
							Iterator<Song> it = songs.iterator();
							while (it.hasNext()) {
								Song e = it.next();
								while (cursor.getLong(0) < e.id)
									cursor.moveToNext();
								if (cursor.getLong(0) == e.id)
									e.populate(cursor);
								else
									// We weren't able to query this song.
									it.remove();
							}

							cursor.close();

							// Revert to the order the songs were saved in.
							Collections.sort(songs, new FlagComparator());

							mSongs = songs;
						}
					}

					mCurrentPos = Math.min(mSongs == null ? 0 : mSongs.size(), in.readInt());
					mFinishAction = in.readInt();
					mShuffle = in.readBoolean();
					extra = in.readInt();

					in.close();
				}
			}
		} catch (EOFException e) {
			Log.w("VanillaMusic", "Failed to load state", e);
		} catch (IOException e) {
			Log.w("VanillaMusic", "Failed to load state", e);
		}

		if (mSongs == null)
			mSongs = new ArrayList<Song>();

		return extra;
	}

	/**
	 * Returns a byte array representing the current state of the timeline.
	 * This can be passed to the appropriate constructor to initialize the
	 * timeline with this state.
	 *
	 * @param extra Optional extra data to be included. Should not be -1.
	 */
	public void saveState(int extra)
	{
		try {
			DataOutputStream out = new DataOutputStream(mContext.openFileOutput(STATE_FILE, 0));
			out.writeLong(STATE_FILE_MAGIC);

			synchronized (this) {
				ArrayList<Song> songs = mSongs;

				int size = songs.size();
				out.writeInt(size);

				for (int i = 0; i != size; ++i) {
					Song song = songs.get(i);
					if (song == null) {
						out.writeLong(-1);
						out.writeInt(0);
					} else {
						out.writeLong(song.id);
						out.writeInt(song.flags);
					}
				}

				out.writeInt(mCurrentPos);
				out.writeInt(mFinishAction);
				out.writeBoolean(mShuffle);
				out.writeInt(extra);
			}

			out.close();
		} catch (IOException e) {
			Log.w("VanillaMusic", "Failed to save state", e);
		}
	}

	/**
	 * Sets the current callback to <code>callback</code>.
	 */
	public void setCallback(Callback callback)
	{
		mCallback = callback;
	}

	/**
	 * Return whether shuffling is enabled.
	 */
	public boolean isShuffling()
	{
		return mShuffle;
	}

	/**
	 * Return the finish action.
	 *
	 * @see SongTimeline#setFinishAction(int)
	 */
	public int getFinishAction()
	{
		return mFinishAction;
	}

	/**
	 * Set whether shuffling is enabled. Will shuffle the current set of songs
	 * when enabling shuffling if random mode is not enabled.
	 */
	public void setShuffle(boolean shuffle)
	{
		if (shuffle == mShuffle)
			return;

		synchronized (this) {
			saveActiveSongs();
			mShuffle = shuffle;
			if (shuffle && mFinishAction != FINISH_RANDOM) {
				shuffleAll();
				ArrayList<Song> songs = mShuffledSongs;
				mShuffledSongs = null;
				int i = songs.indexOf(mSavedCurrent);
				songs.set(i, songs.get(mCurrentPos));
				songs.set(mCurrentPos, mSavedCurrent);
				mSongs = songs;
			}
			broadcastChangedSongs();
		}

		changed();
	}

	/**
	 * Set what to do when the end of the playlist is reached. Must be one of
	 * SongTimeline.FINISH_* (stop, repeat, or add random song).
	 */
	public void setFinishAction(int action)
	{
		saveActiveSongs();
		mFinishAction = action;
		broadcastChangedSongs();
		changed();
	}

	/**
	 * Shuffle all the songs in the timeline, storing the result in
	 * mShuffledSongs.
	 *
	 * @return The first song from the shuffled songs.
	 */
	private Song shuffleAll()
	{
		ArrayList<Song> songs = new ArrayList<Song>(mSongs);
		Collections.shuffle(songs, MediaUtils.getRandom());
		mShuffledSongs = songs;
		return songs.get(0);
	}

	/**
	 * Returns the song <code>delta</code> places away from the current
	 * position. Returns null if there is a problem retrieving the song.
	 *
	 * @param delta The offset from the current position. Must be -1, 0, or 1.
	 */
	public Song getSong(int delta)
	{
		assert(delta >= -1 && delta <= 1);

		ArrayList<Song> timeline = mSongs;
		Song song = null;

		synchronized (this) {
			int pos = mCurrentPos + delta;
			int size = timeline.size();

			if (pos < 0) {
				if (size == 0 || mFinishAction == FINISH_RANDOM)
					return null;
				song = timeline.get(Math.max(0, size - 1));
			} else if (pos > size) {
				return null;
			} else if (pos == size) {
				switch (mFinishAction) {
				case FINISH_STOP:
				case FINISH_REPEAT:
					if (size == 0)
						// empty queue
						return null;
					else if (mShuffle)
						song = shuffleAll();
					else
						song = timeline.get(0);
					break;
				case FINISH_RANDOM:
					song = MediaUtils.randomSong(mContext);
					timeline.add(song);
					break;
				default:
					throw new IllegalStateException("Invalid finish action: " + mFinishAction);
				}
			} else {
				song = timeline.get(pos);
			}
		}

		if (song == null)
			// we have no songs in the library
			return null;

		return song;
	}

	/**
	 * Shift the current song by <code>delta</code> places.
	 *
	 * @param delta The delta. Must be -1, 0, 1.
	 * @return The Song at the new position
	 */
	public Song shiftCurrentSong(int delta)
	{
		assert(delta >= -1 && delta <= 1);

		synchronized (this) {
			int pos = mCurrentPos + delta;

			if (mFinishAction != FINISH_RANDOM && pos == mSongs.size()) {
				if (mShuffle && mSongs.size() > 0) {
					if (mShuffledSongs == null)
						shuffleAll();
					mSongs = mShuffledSongs;
				}

				pos = 0;
			} else if (pos < 0) {
				if (mFinishAction == FINISH_RANDOM)
					pos = 0;
				else
					pos = Math.max(0, mSongs.size() - 1);
			}

			mCurrentPos = pos;
			mShuffledSongs = null;
		}

		if (delta != 0)
			changed();

		return getSong(0);
	}

	/**
	 * Add the songs from the given cursor to the song timeline.
	 *
	 * @param mode How to add the songs. One of SongTimeline.MODE_*.
	 * @param cursor The cursor to fill from.
	 * @return The number of songs that were added.
	 */
	public int addSongs(int mode, Cursor cursor)
	{
		if (cursor == null)
			return 0;
		int count = cursor.getCount();
		if (count == 0)
			return 0;

		ArrayList<Song> timeline = mSongs;
		synchronized (this) {
			saveActiveSongs();

			switch (mode) {
			case MODE_ENQUEUE: {
				int i = timeline.size();
				while (--i > mCurrentPos) {
					if (timeline.get(i).isRandom())
						timeline.remove(i);
				}
				break;
			}
			case MODE_PLAY_NEXT:
				timeline.subList(mCurrentPos + 1, timeline.size()).clear();
				break;
			case MODE_PLAY:
				timeline.clear();
				mCurrentPos = 0;
				break;
			default:
				throw new IllegalArgumentException("Invalid mode: " + mode);
			}

			int start = timeline.size();

			for (int j = 0; j != count; ++j) {
				cursor.moveToPosition(j);
				Song song = new Song(-1);
				song.populate(cursor);
				timeline.add(song);
			}

			if (mShuffle)
				Collections.shuffle(timeline.subList(start, timeline.size()), MediaUtils.getRandom());

			broadcastChangedSongs();
		}

		cursor.close();

		changed();

		return count;
	}

	/**
	 * Removes any songs greater than 10 songs before the current song when in
	 * random mode.
	 */
	public void purge()
	{
		synchronized (this) {
			if (mFinishAction == FINISH_RANDOM) {
				while (mCurrentPos > 10) {
					mSongs.remove(0);
					--mCurrentPos;
				}
			}
		}
	}

	/**
	 * Clear the song queue.
	 */
	public void clearQueue()
	{
		synchronized (this) {
			if (mCurrentPos + 1 < mSongs.size())
				mSongs.subList(mCurrentPos + 1, mSongs.size()).clear();
		}

		mCallback.activeSongReplaced(+1, getSong(+1));

		changed();
	}

	/**
	 * Save the active songs for use with broadcastChangedSongs().
	 *
	 * @see SongTimeline#broadcastChangedSongs()
	 */
	private void saveActiveSongs()
	{
		mSavedPrevious = getSong(-1);
		mSavedCurrent = getSong(0);
		mSavedNext = getSong(+1);
	}

	/**
	 * Broadcast the active songs that have changed since the last call to
	 * saveActiveSongs()
	 *
	 * @see SongTimeline#saveActiveSongs()
	 */
	private void broadcastChangedSongs()
	{
		Song previous = getSong(-1);
		Song current = getSong(0);
		Song next = getSong(+1);

		if (mCallback != null) {
			if (Song.getId(mSavedPrevious) != Song.getId(previous))
				mCallback.activeSongReplaced(-1, previous);
			if (Song.getId(mSavedNext) != Song.getId(next))
				mCallback.activeSongReplaced(1, next);
		}
		if (Song.getId(mSavedCurrent) != Song.getId(current)) {
			if (mCallback != null)
				mCallback.activeSongReplaced(0, current);
		}
	}

	/**
	 * Remove the song with the given id from the timeline.
	 *
	 * @param id The MediaStore id of the song to remove.
	 */
	public void removeSong(long id)
	{
		synchronized (this) {
			ArrayList<Song> songs = mSongs;

			saveActiveSongs();

			int i = mCurrentPos;
			while (--i >= 0) {
				if (Song.getId(songs.get(i)) == id) {
					songs.remove(i);
					--mCurrentPos;
				}
			}

			for (i = mCurrentPos; i < songs.size(); ++i) {
				if (Song.getId(songs.get(i)) == id)
					songs.remove(i);
			}

			broadcastChangedSongs();
		}

		changed();
	}

	/**
	 * Broadcasts that the timeline state has changed.
	 */
	private void changed()
	{
		if (mCallback != null)
			mCallback.timelineChanged();
	}

	/**
	 * Return true if the finish action is to stop at the end of the queue and
	 * the current song is the last in the queue.
	 */
	public boolean isEndOfQueue()
	{
		synchronized (this) {
			return mFinishAction == FINISH_STOP && mCurrentPos == mSongs.size() - 1;
		}
	}
}
