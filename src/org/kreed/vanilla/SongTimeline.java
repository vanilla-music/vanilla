/*
 * Copyright (C) 2010 Christopher Eby <kreed@kreed.org>
 *
 * This file is part of Vanilla Music Player.
 *
 * Vanilla Music Player is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Vanilla Music Player is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.kreed.vanilla;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

import android.content.Context;
import android.util.Log;

/**
 * Represents a series of songs that can be moved through backward or forward.
 * Automatically handles the fetching of new (random) songs when a song does not
 * exist at a requested position.
 */
public final class SongTimeline {
	/**
	 * Name of the state file.
	 */
	private static final String STATE_FILE = "state";
	/**
	 * Header for state file to help indicate if the file is in the right
	 * format.
	 */
	private static final long STATE_FILE_MAGIC = 0x8a9d3f2fca33L;

	/**
	 * All the songs currently contained in the timeline. Each Song object
	 * should be unique, even if it refers to the same media.
	 */
	private ArrayList<Song> mSongs = new ArrayList<Song>();
	/**
	 * The position of the current song (i.e. the playing song).
	 */
	private int mCurrentPos;
	/**
	 * The distance from mCurrentPos at which songs will be enqueued by
	 * chooseSongs. If 0, then songs will be enqueued at the position
	 * immediately following the current song. If 1, there will be one
	 * position between them, etc.
	 */
	private int mQueueOffset;
	/**
	 * When a repeat is engaged, this position will be rewound to.
	 */
	private int mRepeatStart = -1;
	/**
	 * The songs that will be repeated on the next repeat. We cache these so
	 * that, if they need to be shuffled, they are only shuffled once.
	 */
	private ArrayList<Song> mRepeatedSongs;
	/**
	 * Whether shuffling is enabled. Shuffling will shuffle sets of songs
	 * that are added with chooseSongs and shuffle sets of repeated songs.
	 */
	private boolean mShuffle;

	public interface Callback {
		/**
		 * Called when a song in the timeline has been replaced with a
		 * different song.
		 *
		 * @param delta The distance from the current song. Will always be -1,
		 * 0, or 1.
		 * @param song The new song at the position
		 */
		public void songReplaced(int delta, Song song);
	}
	/**
	 * The current Callback, if any.
	 */
	private Callback mCallback;

	/**
	 * Initializes the timeline with the state stored in the state file created
	 * by a call to save state.
	 * 
	 * @param context The Context to open the state file with
	 * @return The optional extra data, or -1 if loading failed
	 */
	public int loadState(Context context)
	{
		int extra = -1;

		try {
			DataInputStream in = new DataInputStream(context.openFileInput(STATE_FILE));
			if (in.readLong() == STATE_FILE_MAGIC) {
				int n = in.readInt();
				if (n > 0) {
					ArrayList<Song> songs = new ArrayList<Song>(n);
		
					for (int i = 0; i != n; ++i)
						songs.add(new Song(in.readLong(), in.readInt()));
		
					mSongs = songs;
					mCurrentPos = in.readInt();
					mRepeatStart = in.readInt();
					mShuffle = in.readBoolean();
					extra = in.readInt();
				}
			}

			in.close();
		} catch (IOException e) {
			Log.w("VanillaMusic", "Failed to load state", e);
		}

		return extra;
	}

	/**
	 * Returns a byte array representing the current state of the timeline.
	 * This can be passed to the appropriate constructor to initialize the
	 * timeline with this state.
	 *
	 * @param context The Context to open the state file with
	 * @param extra Optional extra data to be included. Should not be -1.
	 */
	public void saveState(Context context, int extra)
	{
		try {
			DataOutputStream out = new DataOutputStream(context.openFileOutput(STATE_FILE, 0));
			out.writeLong(STATE_FILE_MAGIC);

			synchronized (this) {
				ArrayList<Song> songs = mSongs;

				int size = songs.size();
				out.writeInt(size);

				for (int i = 0; i != size; ++i) {
					Song song = songs.get(i);
					out.writeLong(song.id);
					out.writeInt(song.flags);
				}

				out.writeInt(mCurrentPos);
				out.writeInt(mRepeatStart);
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
	 * Return whether repeating is enabled.
	 */
	public boolean isRepeating()
	{
		return mRepeatStart != -1;
	}

	/**
	 * Return the position of the current song (i.e. the playing song).
	 */
	public int getCurrentPosition()
	{
		synchronized (this) {
			return mCurrentPos;
		}
	}

	/**
	 * Set whether shuffling is enabled. Shuffling will shuffle sets of songs
	 * that are added with chooseSongs and shuffle sets of repeated songs.
	 */
	public void setShuffle(boolean shuffle)
	{
		mShuffle = shuffle;
	}

	/**
	 * Set whether to repeat.
	 * 
	 * When called with true, repeat will be enabled and the current song will
	 * become the starting point for repeats, where the position is rewound to
	 * when a repeat is engaged. A repeat is engaged when a randomly selected
	 * song is encountered (i.e. a non-user-chosen song).
	 */
	public void setRepeat(boolean repeat)
	{
		// Don't change anything if we are already doing what we want.
		if (repeat == (mRepeatStart != -1))
			return;

		synchronized (this) {
			if (repeat) {
				mRepeatStart = mCurrentPos;
				// Ensure that we will at least repeat one song (the current song),
				// even if all of our songs were selected randomly.
				getSong(0).flags &= ~Song.FLAG_RANDOM;
			} else {
				mRepeatStart = -1;
				mRepeatedSongs = null;
			}

			if (mCallback != null)
				mCallback.songReplaced(+1, getSong(+1));
		}
	}

	/**
	 * Retrieves a shuffled list of the songs to be repeated. This caches the
	 * results so that the repeated songs are shuffled only once.
	 *
	 * @param end The position just after the last song to be included in the
	 * repeated songs
	 */
	private ArrayList<Song> getShuffledRepeatedSongs(int end)
	{
		if (mRepeatedSongs == null) {
			ArrayList<Song> songs = new ArrayList<Song>(mSongs.subList(mRepeatStart, end));
			Collections.shuffle(songs, ContextApplication.getRandom());
			mRepeatedSongs = songs;
		}
		return mRepeatedSongs;
	}

	/**
	 * Returns the song <code>delta</code> places away from the current
	 * position.
	 */
	public Song getSong(int delta)
	{
		ArrayList<Song> timeline = mSongs;
		Song song;

		synchronized (this) {
			int pos = mCurrentPos + delta;
			if (pos < 0)
				return null;

			int size = timeline.size();
			if (pos > size)
				return null;

			if (pos == size) {
				song = new Song();
				timeline.add(song);
			} else {
				song = timeline.get(pos);
			}

			if (mRepeatStart != -1 && (song.flags & Song.FLAG_RANDOM) != 0) {
				if (delta == 1) {
					// We have reached a non-user-selected song; this song will
					// repeated in shiftCurrentSong so take alternative
					// measures
					if (mShuffle)
						song = getShuffledRepeatedSongs(mCurrentPos + delta).get(0);
					else
						song = timeline.get(mRepeatStart);
				} else if (delta == 0 && mRepeatStart < mCurrentPos) {
					// We have just been set to a position after the repeat
					// where a repeat is necessary. Rewind to the repeat
					// start, shuffling if needed
					if (mShuffle) {
						int j = mCurrentPos;
						ArrayList<Song> songs = getShuffledRepeatedSongs(j);
						for (int i = songs.size(); --i != -1 && --j != -1; )
							timeline.set(j, songs.get(i));
						mRepeatedSongs = null;
					}

					mCurrentPos = mRepeatStart;
					song = timeline.get(mRepeatStart);
					if (mCallback != null)
						mCallback.songReplaced(-1, getSong(-1));
				}
			}
		}

		if (!song.populate(false)) {
			song.randomize();
			if (!song.populate(false))
				return null;
		}

		return song;
	}

	/**
	 * Shift the current song by <code>delta</code> places.
	 *
	 * @return The Song at the new position
	 */
	public Song shiftCurrentSong(int delta)
	{
		synchronized (this) {
			mCurrentPos += delta;
			return getSong(0);
		}
	}

	/**
	 * Add a set of songs to the song timeline. There are two modes: play and
	 * enqueue. Play will play the first song in the set immediately and enqueue
	 * the remaining songs directly after it. Enqueue will place the set after
	 * the last enqueued item or after the currently playing item if the queue
	 * is empty.
	 *
	 * @param enqueue If true, enqueue the set. If false, play the set.
	 * @param type 1, 2, or 3, indicating artist, album, or song, respectively.
	 * @param id The MediaStore id of the artist, album, or song.
	 */
	public void chooseSongs(boolean enqueue, int type, long id)
	{
		long[] songs = Song.getAllSongIdsWith(type, id);
		if (songs == null || songs.length == 0)
			return;

		if (mShuffle) {
			Random random = ContextApplication.getRandom();
			for (int i = songs.length; --i != 0; ) {
				int j = random.nextInt(i + 1);
				long tmp = songs[j];
				songs[j] = songs[i];
				songs[i] = tmp;
			}
		}

		Song oldSong = getSong(+1);

		ArrayList<Song> timeline = mSongs;
		synchronized (this) {
			if (enqueue) {
				int i = mCurrentPos + mQueueOffset + 1;
				if (i < timeline.size())
					timeline.subList(i, timeline.size()).clear();

				for (int j = 0; j != songs.length; ++j)
					timeline.add(new Song(songs[j]));

				mQueueOffset += songs.length;
			} else {
				timeline.subList(mCurrentPos + 1, timeline.size()).clear();

				for (int j = 0; j != songs.length; ++j)
					timeline.add(new Song(songs[j]));

				mQueueOffset += songs.length - 1;
			}
		}

		mRepeatedSongs = null;
		Song newSong = getSong(+1);
		if (newSong != oldSong && mCallback != null)
			mCallback.songReplaced(+1, newSong);
	}

	/**
	 * Removes any songs greater than 10 songs before the current song (unless
	 * they are still necessary for repeating).
	 */
	public void purge()
	{
		synchronized (this) {
			while (mCurrentPos > 10 && mRepeatStart > 0) {
				mSongs.remove(0);
				--mCurrentPos;
				--mRepeatStart;
			}
		}
	}

	/**
	 * Finish enqueueing songs for the current session. Newly enqueued songs
	 * will be enqueued directly after the current song rather than after
	 * previously enqueued songs.
	 */
	public void finishEnqueueing()
	{
		synchronized (this) {
			mQueueOffset = 0;
		}
	}
}
