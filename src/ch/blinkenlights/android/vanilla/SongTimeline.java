/*
 * Copyright (C) 2012 Christopher Eby <kreed@kreed.org>
 * Copyright (C) 2015 Adrian Ulrich <adrian@blinkenlights.ch>
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

import android.content.Context;
import android.database.Cursor;

import junit.framework.Assert;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.ListIterator;

import ch.blinkenlights.android.medialibrary.MediaLibrary;

/**
 * Contains the list of currently playing songs, implements repeat and shuffle
 * support, and contains methods to fetch more songs from the MediaStore.
 */
public final class SongTimeline {
	/**
	 * Stop playback.
	 *
	 * @see SongTimeline#setFinishAction(int)
	 */
	public static final int FINISH_STOP = 0;
	/**
	 * Repeat from the beginning.
	 *
	 * @see SongTimeline#setFinishAction(int)
	 */
	public static final int FINISH_REPEAT = 1;
	/**
	 * Repeat the current song. This behavior is implemented entirely in
	 * {@link PlaybackService#onCompletion(android.media.MediaPlayer)};
	 * pressing the next or previous buttons will advance the song as normal;
	 * only allowing the song to play until the end will repeat it.
	 *
	 * @see SongTimeline#setFinishAction(int)
	 */
	public static final int FINISH_REPEAT_CURRENT = 2;
	/**
	 * Stop playback after current song. This behavior is implemented entirely
	 * in {@link PlaybackService#onCompletion(android.media.MediaPlayer)};
	 * pressing the next or previous buttons will advance the song as normal;
	 * only allowing the song to play until the end.
	 *
	 * @see SongTimeline#setFinishAction(int)
	 */
	public static final int FINISH_STOP_CURRENT = 3;
	/**
	 * Add random songs to the playlist.
	 *
	 * @see SongTimeline#setFinishAction(int)
	 */
	public static final int FINISH_RANDOM = 4;

	/**
	 * Icons corresponding to each of the finish actions.
	 */
	public static final int[] FINISH_ICONS =
		{ R.drawable.repeat_inactive, R.drawable.repeat_active, R.drawable.repeat_current_active, R.drawable.stop_current_active, R.drawable.random_active };

	/**
	 * Clear the timeline and use only the provided songs.
	 *
	 * @see SongTimeline#addSongs(Context, QueryTask)
	 */
	public static final int MODE_PLAY = 0;
	/**
	 * Clear the queue and add the songs after the current song.
	 *
	 * @see SongTimeline#addSongs(Context, QueryTask)
	 */
	public static final int MODE_FLUSH_AND_PLAY_NEXT = 1;
	/**
	 * Add the songs at the end of the timeline, clearing random songs.
	 *
	 * @see SongTimeline#addSongs(Context, QueryTask)
	 */
	public static final int MODE_ENQUEUE = 2;
	/**
	 * Like play mode, but make the song at the given position play first by
	 * removing the songs before the given position in the query and appending
	 * them to the end of the queue.
	 *
	 * Pass the position in QueryTask.data.
	 *
	 * @see SongTimeline#addSongs(Context, QueryTask)
	 */
	public static final int MODE_PLAY_POS_FIRST = 3;
	/**
	 * Like play mode, but make the song with the given id play first by
	 * removing the songs before the song in the query and appending
	 * them to the end of the queue. If there are multiple songs with
	 * the given id, picks the first song with that id.
	 *
	 * Pass the id in QueryTask.data.
	 *
	 * @see SongTimeline#addSongs(Context, QueryTask)
	 */
	public static final int MODE_PLAY_ID_FIRST = 4;
	/**
	 * Like enqueue mode, but make the song with the given id play first by
	 * removing the songs before the song in the query and appending
	 * them to the end of the queue. If there are multiple songs with
	 * the given id, picks the first song with that id.
	 *
	 * Pass the id in QueryTask.data.
	 *
	 * @see SongTimeline#addSongs(Context, QueryTask)
	 */
	public static final int MODE_ENQUEUE_ID_FIRST = 5;
	/**
	 * Like enqueue mode, but make the song at the given position play first by
	 * removing the songs before the given position in the query and appending
	 * them to the end of the queue.
	 *
	 * Pass the position in QueryTask.data.
	 *
	 * @see SongTimeline#addSongs(Context, QueryTask)
	 */
	public static final int MODE_ENQUEUE_POS_FIRST = 6;
	/**
	 * Enqueues the result as next item(s)
	 *
	 * Pass the position in QueryTask.data.
	 *
	 * @see SongTimeline#addSongs(Context, QueryTask)
	 */
	public static final int MODE_ENQUEUE_AS_NEXT = 7;

	/**
	 * Designates audiobook playback mode
	 */
	public static final int MODE_AUDIOBOOK = 8;

	/**
	 * Disable shuffle.
	 *
	 * @see SongTimeline#setShuffleMode(int)
	 */
	public static final int SHUFFLE_NONE = 0;
	/**
	 * Randomize order of songs.
	 *
	 * @see SongTimeline#setShuffleMode(int)
	 */
	public static final int SHUFFLE_SONGS = 1;
	/**
	 * Randomize order of albums, preserving the order of tracks inside the
	 * albums.
	 *
	 * @see SongTimeline#setShuffleMode(int)
	 */
	public static final int SHUFFLE_ALBUMS = 2;

	/**
	 * Icons corresponding to each of the shuffle actions.
	 */
	public static final int[] SHUFFLE_ICONS =
		{ R.drawable.shuffle_inactive, R.drawable.shuffle_active, R.drawable.shuffle_album_active };

	/**
	 * Move current position to the previous album.
	 *
	 * @see SongTimeline#shiftCurrentSong(int)
	 */
	public static final int SHIFT_PREVIOUS_ALBUM = -2;
	/**
	 * Move current position to the previous song.
	 *
	 * @see SongTimeline#shiftCurrentSong(int)
	 */
	public static final int SHIFT_PREVIOUS_SONG = -1;
	/**
	 * Noop
	 * @see SongTimeline#shiftCurrentSong(int)
	 */
	public static final int SHIFT_KEEP_SONG = 0;
	/**
	 * Move current position to the next song.
	 *
	 * @see SongTimeline#shiftCurrentSong(int)
	 */
	public static final int SHIFT_NEXT_SONG = 1;
	/**
	 * Move current position to the next album.
	 *
	 * @see SongTimeline#shiftCurrentSong(int)
	 */
	public static final int SHIFT_NEXT_ALBUM = 2;

	private final Context mContext;
	/**
	 * All the songs currently contained in the timeline. Each Song object
	 * should be unique, even if it refers to the same media.
	 */
	private ArrayList<Song> mSongs = new ArrayList<Song>(12);
	/**
	 * The position of the current song (i.e. the playing song).
	 */
	private int mCurrentPos;
	/**
	 * How to shuffle/whether to shuffle. One of SongTimeline.SHUFFLE_*.
	 */
	private int mShuffleMode;
	/**
	 * What to do when the end of the playlist is reached.
	 * Must be one of SongTimeline.FINISH_*.
	 */
	private int mFinishAction;
	/**
	 * Prepared (shuffled) replacement playlist
	 */
	private ArrayList<Song> mShuffleCache;
	/**
	 * Hash code of mSongs while mShuffleCache was generated
	 */
	private int mShuffleTicket;
	/**
	 * The last song we added randomly by calling MediaUtils.getRandomSong()
	 */
	private Song mLastRandomSong;

	// for saveActiveSongs()
	private Song mSavedPrevious;
	private Song mSavedCurrent;
	private Song mSavedNext;
	private int mSavedPos;
	private int mSavedSize;

	/**
	 * Interface to respond to timeline changes.
	 */
	public interface Callback {
		/**
		 * Called when an active song in the timeline is replaced by a method
		 * other than shiftCurrentSong()
		 *
		 * @param delta The distance from the current song. Will always be -1,
		 * 0, or 1.
		 * @param song The new song at the position
		 */
		void activeSongReplaced(int delta, Song song);

		/**
		 * Called when the timeline state has changed and should be saved to
		 * storage.
		 */
		void timelineChanged();

		/**
		 * Called when the length of the timeline has changed.
		 */
		void positionInfoChanged();
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
	 * Initializes the timeline with data read from the stream. Data should have
	 * been saved by a call to {@link SongTimeline#writeState(DataOutputStream)}.
	 *
	 * @param in The stream to read from.
	 */
	public void readState(DataInputStream in) throws IOException
	{
		synchronized (this) {
			int n = in.readInt();
			if (n > 0) {
				ArrayList<Song> songs = new ArrayList<Song>(n);

				// Fill the selection with the ids of all the saved songs
				// and initialize the timeline with unpopulated songs.
				StringBuilder selection = new StringBuilder(MediaLibrary.SongColumns._ID+" IN (");
				for (int i = 0; i != n; ++i) {
					long id = in.readLong();
					if (id == -1)
						continue;

					// Add the index to the flags so we can sort
					int flags = in.readInt() & ~(~0 << Song.FLAG_COUNT) | i << Song.FLAG_COUNT;
					songs.add(new Song(id, flags));

					if (i != 0)
						selection.append(',');
					selection.append(id);
				}
				selection.append(')');

				// Sort songs by id---this is the order the query will
				// return its results in.
				Collections.sort(songs, new IdComparator());

				QueryTask query = new QueryTask(MediaLibrary.VIEW_SONGS_ALBUMS_ARTISTS, Song.FILLED_PROJECTION, selection.toString(), null, MediaLibrary.SongColumns._ID);
				Cursor cursor = query.runQuery(mContext);
				if (cursor != null) {
					if (cursor.getCount() != 0) {
						cursor.moveToNext();

						// Loop through timeline entries, looking for a row
						// that matches the id. One row may match multiple
						// entries.
						Iterator<Song> it = songs.iterator();
						while (it.hasNext()) {
							Song e = it.next();
							while (cursor.getLong(0) < e.id && !cursor.isLast())
								cursor.moveToNext();
							if (cursor.getLong(0) == e.id)
								e.populate(cursor);
						}
					}

					cursor.close();

					// The query may have returned zero results or we might 
					// have failed to populate some songs: Get rid of all
					// uninitialized items
					Iterator<Song> it = songs.iterator();
					while (it.hasNext()) {
						Song e = it.next();
						if (e.isFilled() == false) {
							it.remove();
						}
					}

					// Revert to the order the songs were saved in.
					Collections.sort(songs, new FlagComparator());

					mSongs = songs;
				}
			}

			mCurrentPos = Math.min(mSongs == null ? 0 : mSongs.size(), Math.abs(in.readInt()));
			mFinishAction = in.readInt();
			mShuffleMode = in.readInt();

			// Guard against corruption
			if (mFinishAction < 0 || mFinishAction >= FINISH_ICONS.length)
				mFinishAction = 0;
			if (mShuffleMode < 0 || mShuffleMode >= SHUFFLE_ICONS.length)
				mShuffleMode = 0;
		}
	}

	/**
	 * Writes the current songs and state to the given stream.
	 *
	 * @param out The stream to write to.
	 */
	public void writeState(DataOutputStream out) throws IOException
	{
		// Must update PlaybackService.STATE_VERSION when changing behavior
		// here.
		synchronized (this) {
			ArrayList<Song> songs = mSongs;

			int size = songs.size();
			out.writeInt(size);

			for (int i = 0; i != size; ++i) {
				Song song = songs.get(i);
				if (song == null) {
					out.writeLong(-1);
				} else {
					out.writeLong(song.id);
					out.writeInt(song.flags);
				}
			}

			out.writeInt(mCurrentPos);
			out.writeInt(mFinishAction);
			out.writeInt(mShuffleMode);
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
	 * Return the current shuffle mode.
	 *
	 * @return The shuffle mode. One of SongTimeline.SHUFFLE_*.
	 */
	public int getShuffleMode()
	{
		return mShuffleMode;
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
	 * Set how to shuffle. Will shuffle the current set of songs when enabling
	 * shuffling if random mode is not enabled.
	 *
	 * @param mode One of SongTimeline.MODE_*
	 */
	public void setShuffleMode(int mode)
	{
		if (mode == mShuffleMode)
			return;

		synchronized (this) {
			saveActiveSongs();
			mShuffleMode = mode;
			if (mode != SHUFFLE_NONE && mFinishAction != FINISH_RANDOM && !mSongs.isEmpty()) {
				ArrayList<Song> songs = getShuffledTimeline(false);
				mCurrentPos = songs.indexOf(mSavedCurrent);
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
		synchronized (this) {
			saveActiveSongs();

			if (mFinishAction == FINISH_RANDOM) {
				// Remove the last song if we are going out of RANDOM mode and we
				// are currently playing the 2nd last one.
				int lastSongPos = getLength() - 1;
				if (getPosition()+1 == lastSongPos) {
					Song lastSong = mSongs.get(lastSongPos);
					if (lastSong.isRandom() && lastSong.equals(mLastRandomSong)) {
						mSongs.remove(lastSongPos);
					}
				}
				// forget about the last random song, even if it survived (eg: was switching modes while not playing
				// the 2nd last song -> the last song is now considered to be part of the queue)
				mLastRandomSong = null;
			}

			mFinishAction = action;
			broadcastChangedSongs();
			changed();
		}
	}

	/**
	 * Returns a shuffled (according to mShuffleMode) version
	 * of the timeline. The returned result will get cached
	 *
	 * @param cached the function may return a cached version if true
	 * @return a *copy* of a shuffled version of the timeline
	 */
	private ArrayList<Song> getShuffledTimeline(boolean cached)
	{
		if (cached == false)
			mShuffleCache = null;

		if (mShuffleCache == null) {
			ArrayList<Song> songs = new ArrayList<Song>(mSongs);
			MediaUtils.shuffle(songs, mShuffleMode == SHUFFLE_ALBUMS);
			mShuffleCache = songs;
			mShuffleTicket = mSongs.hashCode();
		}
		return new ArrayList<Song>(mShuffleCache);
	}

	/**
	 * Shuffles the current timeline but keeps the current
	 * queue position
	 */
	private void reshuffleTimeline()
	{
		synchronized (this) {
			saveActiveSongs();
			ArrayList<Song> songs = getShuffledTimeline(false);
			int newPosition = songs.indexOf(mSavedCurrent);
			Collections.swap(songs, newPosition, mCurrentPos);
			mSongs = songs;
			broadcastChangedSongs();
		}
		changed();
	}

	/**
	 * Returns the song <code>delta</code> places away from the current
	 * position. Returns null if there is a problem retrieving the song.
	 *
	 * @param delta The offset from the current position. Must be -1, 0, or 1.
	 */
	public Song getSong(int delta)
	{
		Assert.assertTrue(delta >= -1 && delta <= 1);

		ArrayList<Song> timeline = mSongs;
		Song song;

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
				if (mFinishAction == FINISH_RANDOM) {
					song = MediaUtils.getRandomSong(mContext);
					if (song == null)
						return null;
					timeline.add(song);
					mLastRandomSong = song;
					// Keep the queue at 20 items to avoid growing forever
					// Note that we do not broadcast the addition of this song, as it
					// was virtually 'always there'
					shrinkQueue(20);
				} else {
					if (size == 0)
						// empty queue
						return null;
					else if (mShuffleMode != SHUFFLE_NONE)
						song = getShuffledTimeline(true).get(0);
					else
						song = timeline.get(0);
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
	 * Internal implementation for shiftCurrentSong. Does all the work except
	 * broadcasting the timeline change: updates mCurrentPos and handles
	 * shuffling, repeating, and random mode.
	 *
	 * @param delta -1 to move to the previous song or 1 for the next.
	 */
	private void shiftCurrentSongInternal(int delta)
	{
		int pos = mCurrentPos + delta;

		if (mFinishAction != FINISH_RANDOM && pos == mSongs.size()) {
			if (mShuffleMode != SHUFFLE_NONE && !mSongs.isEmpty()) {
				mSongs = getShuffledTimeline(true);
			}

			pos = 0;
		} else if (pos < 0) {
			if (mFinishAction == FINISH_RANDOM)
				pos = 0;
			else
				pos = Math.max(0, mSongs.size() - 1);
		}

		mCurrentPos = pos;
	}
	
	/**
	 * Hard-Jump to given queue position
	*/
	public Song setCurrentQueuePosition(int pos) {
		synchronized (this) {
			saveActiveSongs();
			mCurrentPos = pos;
			broadcastChangedSongs();
		}
		changed();
		return getSong(0);
	}
	
	/**
	 * Returns 'Song' at given position in queue
	*/
	public Song getSongByQueuePosition(int id) {
		Song song = null;
		synchronized (this) {
			if (mSongs.size() > id)
				song = mSongs.get(id);
		}
		return song;
	}
	
	/**
	 * Move to the next or previous song or album.
	 *
	 * @param delta One of SongTimeline.SHIFT_*.
	 * @return The Song at the new position
	 */
	public Song shiftCurrentSong(int delta)
	{
		synchronized (this) {
			if (delta == SHIFT_KEEP_SONG) {
				// void
			}
			else if (delta == SHIFT_PREVIOUS_SONG || delta == SHIFT_NEXT_SONG) {
				shiftCurrentSongInternal(delta);
			} else {
				Song song = getSong(0);
				long currentAlbum = song.albumId;
				long currentSong = song.id;
				delta = delta > 0 ? 1 : -1;
				do {
					shiftCurrentSongInternal(delta);
					song = getSong(0);
				} while (currentAlbum == song.albumId && currentSong != song.id);
			}
		}

		if (delta != SHIFT_KEEP_SONG)
			changed();
		return getSong(0);
	}

	/**
	 * Run the given query and add the results to the song timeline.
	 *
	 * @param context A context to use.
	 * @param query The query to be run. The mode variable must be initialized
	 * to one of SongTimeline.MODE_*. The type and data variables may also need
	 * to be initialized depending on the given mode.
	 * @return The number of songs that were added.
	 */
	public int addSongs(Context context, QueryTask query)
	{
		Cursor cursor = query.runQuery(context);
		if (cursor == null) {
			return 0;
		}

		int mode = query.mode;
		int type = query.type;
		long data = query.data;

		int count = cursor.getCount(); // Items found by query
		int added = 0;                 // Items actually added to the queue

		if (count == 0 && type == MediaUtils.TYPE_FILE && query.selectionArgs.length == 1) {
			String pathQuery = query.selectionArgs[0];
			pathQuery = pathQuery.substring(0,pathQuery.length()-1); // remove '%' -> this used to be an sql query!
			cursor.close(); // close old version
			cursor = MediaUtils.getCursorForFileQuery(pathQuery);
			count = cursor.getCount();
		}

		if (count == 0) {
			cursor.close();
			return 0;
		}

		ArrayList<Song> timeline = mSongs;
		synchronized (this) {
			saveActiveSongs();

			switch (mode) {
			case MODE_ENQUEUE:
			case MODE_ENQUEUE_POS_FIRST:
			case MODE_ENQUEUE_ID_FIRST:
			case MODE_ENQUEUE_AS_NEXT:
				if (mFinishAction == FINISH_RANDOM) {
					int j = timeline.size();
					while (--j > mCurrentPos) {
						if (timeline.get(j).isRandom())
							timeline.remove(j);
					}
				}
				break;
			case MODE_FLUSH_AND_PLAY_NEXT:
				timeline.subList(mCurrentPos + 1, timeline.size()).clear();
				break;
			case MODE_PLAY:
			case MODE_PLAY_POS_FIRST:
			case MODE_PLAY_ID_FIRST:
			case MODE_AUDIOBOOK:
				timeline.clear();
				mCurrentPos = 0;
				break;
			default:
				throw new IllegalArgumentException("Invalid mode: " + mode);
			}

			int start = timeline.size();

			Song jumpSong = null;
			int addAtPos = mCurrentPos + 1;

			/* Check if addAtPos is out-of-bounds OR if
			 * the request does not want to work at the current
			 * playlist position anyway
			 */
			if (addAtPos > start || mode != MODE_ENQUEUE_AS_NEXT) {
				addAtPos = start;
			}

			for (int j = 0; j != count; ++j) {
				cursor.moveToPosition(j);

				Song song = new Song(-1);
				song.populate(cursor);
				if (song.isFilled() == false) {
					// Song vanished from device for some reason: we are silently skipping it.
					continue;
				}

				timeline.add(addAtPos++, song);
				added++;

				if (jumpSong == null) {
					if ((mode == MODE_PLAY_POS_FIRST || mode == MODE_ENQUEUE_POS_FIRST) && j == data) {
						jumpSong = song;
					} else if(mode == MODE_AUDIOBOOK) {
						if(Song.getId(song) == ((Audiobook)query.modeData).getSongID()) {
							jumpSong = song;
						}
					} else if (mode == MODE_PLAY_ID_FIRST || mode == MODE_ENQUEUE_ID_FIRST) {
						long id;
						switch (type) {
						case MediaUtils.TYPE_ARTIST:
							id = song.artistId;
							break;
						case MediaUtils.TYPE_ALBUM:
							id = song.albumId;
							break;
						case MediaUtils.TYPE_SONG:
							id = song.id;
							break;
						default:
							throw new IllegalArgumentException("Unsupported id type: " + type);
						}
						if (id == data)
							jumpSong = song;
					}
				}
			}

			cursor.close();
			if(MODE_AUDIOBOOK == mode) {
				setShuffleMode(SHUFFLE_NONE);
			}
			if (mShuffleMode != SHUFFLE_NONE)
				MediaUtils.shuffle(timeline.subList(start, timeline.size()), mShuffleMode == SHUFFLE_ALBUMS);

			if (jumpSong != null) {
				int jumpPos = timeline.indexOf(jumpSong);
				if(MODE_AUDIOBOOK == mode) {
					((Audiobook)query.modeData).setSong(jumpSong);
					((Audiobook)query.modeData).setTimelineIndex(jumpPos);
				} else {
					if (jumpPos != start) {
						// Get the sublist twice to avoid a ConcurrentModificationException.
						timeline.addAll(timeline.subList(start, jumpPos));
						timeline.subList(start, jumpPos).clear();
					}
				}
			}

			broadcastChangedSongs();
		}

		changed();

		return added;
	}

	/**
	 * Removes any songs greater than `len' songs before the current song.
	 */
	private void shrinkQueue(int len) {
		synchronized (this) {
			while (mCurrentPos > len) {
				mSongs.remove(0);
				mCurrentPos--;
			}
		}
		changed();
	}

	/**
	 * Clear the song queue.
	 */
	public void clearQueue()
	{
		synchronized (this) {
			saveActiveSongs();
			if (mCurrentPos + 1 < mSongs.size())
				mSongs.subList(mCurrentPos + 1, mSongs.size()).clear();
			broadcastChangedSongs();
		}

		changed();
	}

	/**
	 * Empty the song queue (clear the whole queue).
	 */
	public void emptyQueue()
	{
		synchronized (this) {
			saveActiveSongs();
			mSongs.clear();
			mCurrentPos = 0;
			broadcastChangedSongs();
		}

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
		mSavedPos = mCurrentPos;
		mSavedSize = mSongs.size();
	}

	/**
	 * Broadcast the active songs that have changed since the last call to
	 * saveActiveSongs()
	 *
	 * @see SongTimeline#saveActiveSongs()
	 */
	private void broadcastChangedSongs()
	{
		if (mCallback == null) return;

		Song previous = getSong(-1);
		Song current = getSong(0);
		Song next = getSong(+1);

		if (Song.getId(mSavedPrevious) != Song.getId(previous))
			mCallback.activeSongReplaced(-1, previous);
		if (Song.getId(mSavedNext) != Song.getId(next))
			mCallback.activeSongReplaced(1, next);
		if (Song.getId(mSavedCurrent) != Song.getId(current))
			mCallback.activeSongReplaced(0, current);

		if (mCurrentPos != mSavedPos || mSongs.size() != mSavedSize)
			mCallback.positionInfoChanged();
	}

	/**
	 * Remove the song with the given id from the timeline.
	 *
	 * @param id The MediaStore id of the song to remove.
	 */
	public void removeSong(long id)
	{
		synchronized (this) {
			saveActiveSongs();

			ArrayList<Song> songs = mSongs;
			ListIterator<Song> it = songs.listIterator();
			while (it.hasNext()) {
				int i = it.nextIndex();
				if (Song.getId(it.next()) == id) {
					if (i < mCurrentPos)
						--mCurrentPos;
					it.remove();
				}
			}

			if (getSong(1) == null)
				mCurrentPos = 0;

			broadcastChangedSongs();
		}

		changed();
	}

	/**
	 * Removes song in timeline at given position
	 * @param pos index to use
	 */
	public void removeSongPosition(int pos) {
		synchronized (this) {
			ArrayList<Song> songs = mSongs;

			if (songs.size() <= pos) // may happen if we race with purge()
				return;

			saveActiveSongs();

			songs.remove(pos);
			if (pos < mCurrentPos)
				mCurrentPos--;
			if (getSong(1) == null) // wrap around if this was the last song
				mCurrentPos = 0;

			broadcastChangedSongs();
		}
		changed();
	}

	/**
	 * Moves a song in the timeline to a new position
	 * @param from index to move from
	 * @param to index to move to
	 */
	public void moveSongPosition(int from, int to) {
		synchronized (this) {
			ArrayList<Song> songs = mSongs;

			if (songs.size() <= from || songs.size() <= to) // may happen if we race with purge()
				return;

			saveActiveSongs();

			Song tmp = songs.remove(from);
			songs.add(to, tmp);

			if (mCurrentPos == from) {
				mCurrentPos = to; // active song was dragged to 'to'
			} else if (from > mCurrentPos && to <= mCurrentPos) {
				mCurrentPos++;
			} else if (from < mCurrentPos && to >= mCurrentPos) {
				mCurrentPos--;
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
		// Invalidate shuffle cache if the timeline *contents* changed in the meantime
		if (mShuffleCache != null && mShuffleTicket != mSongs.hashCode())
			mShuffleCache = null;

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

	/**
	 * Returns the position of the current song in the timeline.
	 */
	public int getPosition()
	{
		return mCurrentPos;
	}

	/**
	 * Returns the current number of songs in the timeline.
	 */
	public int getLength()
	{
		return mSongs.size();
	}
}
