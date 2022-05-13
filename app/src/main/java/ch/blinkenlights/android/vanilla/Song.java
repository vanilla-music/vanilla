/*
 * Copyright (C) 2010, 2011 Christopher Eby <kreed@kreed.org>
 * Copyright (C) 2019 Adrian Ulrich <adrian@blinkenlights.ch>
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

import ch.blinkenlights.android.medialibrary.MediaLibrary;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;

/**
 * Represents a Song backed by the MediaStore. Includes basic metadata and
 * utilities to retrieve songs from the MediaStore.
 */
public class Song implements Comparable<Song> {
	/**
	 * Indicates that this song was randomly selected from all songs.
	 */
	public static final int FLAG_RANDOM = 0x1;
	/**
	 * If set, this song has no cover art. If not set, this song may or may not
	 * have cover art.
	 */
	public static final int FLAG_NO_COVER = 0x2;
	/**
	 * The number of flags.
	 */
	public static final int FLAG_COUNT = 2;


	public static final String[] EMPTY_PROJECTION = {
		MediaLibrary.SongColumns._ID,
	};

    /**
     * _id, path, title, album, albumartist, album_id, albumartist_id, duration, song_number, disc_number, flags
     */
    public static final String[] FILLED_PROJECTION = {
		MediaLibrary.SongColumns._ID,
		MediaLibrary.SongColumns.PATH,
		MediaLibrary.SongColumns.TITLE,
		MediaLibrary.AlbumColumns.ALBUM,
		MediaLibrary.ContributorColumns.ALBUMARTIST,
		MediaLibrary.SongColumns.ALBUM_ID,
		MediaLibrary.ContributorColumns.ALBUMARTIST_ID,
		MediaLibrary.SongColumns.DURATION,
		MediaLibrary.SongColumns.SONG_NUMBER,
		MediaLibrary.SongColumns.DISC_NUMBER,
		MediaLibrary.SongColumns.FLAGS,
	};

	public static final String[] EMPTY_PLAYLIST_PROJECTION = {
		MediaLibrary.PlaylistSongColumns.SONG_ID,
	};

	/**
	 * _id, path, title, album, albumartist, album_id, albumartist_id, duration, song_number, disc_number, flags
	 */
	public static final String[] FILLED_PLAYLIST_PROJECTION = {
		MediaLibrary.PlaylistSongColumns.SONG_ID,
		MediaLibrary.SongColumns.PATH,
		MediaLibrary.SongColumns.TITLE,
		MediaLibrary.AlbumColumns.ALBUM,
		MediaLibrary.ContributorColumns.ALBUMARTIST,
		MediaLibrary.SongColumns.ALBUM_ID,
		MediaLibrary.ContributorColumns.ALBUMARTIST_ID,
		MediaLibrary.SongColumns.DURATION,
		MediaLibrary.SongColumns.SONG_NUMBER,
		MediaLibrary.SongColumns.DISC_NUMBER,
		MediaLibrary.SongColumns.FLAGS,
	};

	/**
	 * The cache instance.
	 */
	private static CoverCache sCoverCache = null;

	/**
	 * Id of this song in the MediaStore
	 */
	public long id;
	/**
	 * Id of this song's album in the MediaStore
	 */
	public long albumId;
	/**
	 * Id of this song's artist in the MediaStore
	 */
	public long albumArtistId;

	/**
	 * Path to the data for this song
	 */
	public String path;

	/**
	 * Song title
	 */
	public String title;
	/**
	 * Album name
	 */
	public String album;
	/**
	 * Album Artist name
	 */
	public String albumArtist;

	/**
	 * Length of the song in milliseconds.
	 */
	public long duration;
	/**
	 * The position of the song in its album.
	 */
	public int trackNumber;
	/**
	 * The disc number where this song is present.
	*/
	public int discNumber;

	/**
	 * Song flags. Currently {@link #FLAG_RANDOM} or {@link #FLAG_NO_COVER}.
	 */
	public int flags;

	/**
	 * Initialize the song with the specified id. Call populate to fill fields
	 * in the song.
	 */
	public Song(long id)
	{
		this.id = id;
	}

	/**
	 * Initialize the song with the specified id and flags. Call populate to
	 * fill fields in the song.
	 */
	public Song(long id, int flags)
	{
		this.id = id;
		this.flags = flags;
	}

	/**
	 * Return true if this song was retrieved from randomSong().
	 */
	public boolean isRandom()
	{
		return (flags & FLAG_RANDOM) != 0;
	}

	/**
	 * Returns true if the song is filled
	 */
	public boolean isFilled()
	{
		return (id != -1 && path != null);
	}

	/**
	 * Populate fields with data from the supplied cursor.
	 *
	 * @param cursor Cursor queried with FILLED_PROJECTION projection
	 */
	public void populate(Cursor cursor)
	{
		id = cursor.getLong(0);
		path = cursor.getString(1);
		title = cursor.getString(2);
		album = cursor.getString(3);
		albumArtist = cursor.getString(4);
		albumId = cursor.getLong(5);
		albumArtistId = cursor.getLong(6);
		duration = cursor.getLong(7);
		trackNumber = cursor.getInt(8);
		discNumber = cursor.getInt(9);

		// Read and interpret the media library flags of this entry.
		// There is no 1:1 mapping, so we must check each flag on its own.
		int libraryFlags = cursor.getInt(10);
		if ((libraryFlags & MediaLibrary.SONG_FLAG_NO_ALBUM) != 0) {
			// Note that we only set, never unset: the song may already
			// have the flag set for other reasons.
			flags |= FLAG_NO_COVER;
		}
	}

	/**
	 * Get the id of the given song.
	 *
	 * @param song The Song to get the id from.
	 * @return The id, or 0 if the given song is null.
	 */
	public static long getId(Song song)
	{
		if (song == null)
			return 0;
		return song.id;
	}

	/**
	 * @return track and disc number of this song within its album
	 */
	public String getTrackAndDiscNumber() {
		String result = Integer.toString(trackNumber);
		if (discNumber > 0) {
			result += String.format(" (%d💿)", discNumber);
		}
		return result;
	}

	/**
	 * Query the large album art for this song.
	 *
	 * @param context A context to use.
	 * @return The album art or null if no album art could be found
	 */
	public Bitmap getLargeCover(Context context) {
		return getCoverInternal(context, CoverCache.SIZE_LARGE);
	}

	/**
	 * Query the medium album art for this song.
	 *
	 * @param context A context to use.
	 * @return The album art or null if no album art could be found
	 */
	public Bitmap getMediumCover(Context context) {
		return getCoverInternal(context, CoverCache.SIZE_MEDIUM);
	}

	/**
	 * Query the small album art for this song.
	 *
	 * @param context A context to use.
	 * @return The album art or null if no album art could be found
	 */
	public Bitmap getSmallCover(Context context) {
		return getCoverInternal(context, CoverCache.SIZE_SMALL);
	}

	/**
	 * Internal implementation of getCover
	 *
	 * @param context A context to use.
	 * @param size The desired cover size
	 * @return The album art or null if no album art could be found
	 */
	private Bitmap getCoverInternal(Context context, int size) {
		if (CoverCache.mCoverLoadMode == 0 || id <= -1 || (flags & FLAG_NO_COVER) != 0)
			return null;

		if (sCoverCache == null)
			sCoverCache = new CoverCache(context.getApplicationContext());

		Bitmap cover = sCoverCache.getCoverFromSong(context, this, size);

		if (cover == null)
			flags |= FLAG_NO_COVER;
		return cover;
	}

	@Override
	public String toString()
	{
		return String.format("%d %d %s", id, albumId, path);
	}

	/**
	 * Compares the virtual order of two song objects.
	 */
	@Override
	public int compareTo(Song other) {
		if (albumId != other.albumId)
			return (albumId > other.albumId ? 1 : -1); // albumId is long, avoid overflow fun.

		if (discNumber != other.discNumber)
			return discNumber - other.discNumber;

		if (trackNumber != other.trackNumber)
			return trackNumber - other.trackNumber;

		// else: is equal
		return 0;
	}

	/**
	 * Overrides 'equals' for the Song object.
	 */
    @Override
    public boolean equals(Object obj) {
		if (obj == null)
			return false;

		if (!(obj instanceof Song))
			return false;

		// Java requires that equals will only return
		// true if the hash codes match. So we must always
		// check this - for now, this is actually the only
		// check we do.
		return obj.hashCode() == hashCode();
	}

	/**
	 * Return hash code of this object, just takes the hash code
	 * of the id long (which is bound to the path).
	 */
	@Override
	public int hashCode() {
		return Long.valueOf(id).hashCode();
	}
}
