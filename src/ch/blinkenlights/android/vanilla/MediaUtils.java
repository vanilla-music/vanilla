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

package ch.blinkenlights.android.vanilla;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Vector;
import java.util.zip.CRC32;

import junit.framework.Assert;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.database.MatrixCursor;
import android.media.MediaMetadataRetriever;
import android.util.Log;
import android.widget.Toast;


/**
 * Provides some static Song/MediaStore-related utility functions.
 */
public class MediaUtils {
	/**
	 * A special invalid media type.
	 */
	public static final int TYPE_INVALID = -1;
	/**
	 * Type indicating an id represents an artist.
	 */
	public static final int TYPE_ARTIST = 0;
	/**
	 * Type indicating an id represents an album.
	 */
	public static final int TYPE_ALBUM = 1;
	/**
	 * Type indicating an id represents a song.
	 */
	public static final int TYPE_SONG = 2;
	/**
	 * Type indicating an id represents a playlist.
	 */
	public static final int TYPE_PLAYLIST = 3;
	/**
	 * Type indicating ids represent genres.
	 */
	public static final int TYPE_GENRE = 4;
	/**
	 * Special type for files and folders. Most methods do not accept this type
	 * since files have no MediaStore id and require special handling.
	 */
	public static final int TYPE_FILE = 5;
	/**
	 * The number of different valid media types.
	 */
	public static final int TYPE_COUNT = 6;

	/**
	 * The default sort order for media queries. First artist, then album, then
	 * track number.
	 */
	public static final String DEFAULT_SORT = "artist_key,album_key,track";

	/**
	 * The default sort order for albums. First the album, then tracknumber
	 */
	public static final String ALBUM_SORT = "album_key,track";

	/**
	 * The default sort order for files. Simply use the path
	 */
	public static final String FILE_SORT = "_data";

	/**
	 * Cached random instance.
	 */
	private static Random sRandom;

	/**
	 * Shuffled list of all ids in the library.
	 */
	private static long[] sAllSongs;
	private static int sAllSongsIdx;

	/**
	 * Total number of songs in the music library, or -1 for uninitialized.
	 */
	private static int sSongCount = -1;

	/**
	 * Returns a cached random instanced, creating it if necessary.
	 */
	public static Random getRandom()
	{
		if (sRandom == null)
			sRandom = new Random();
		return sRandom;
	}

	/**
	 * Builds a query that will return all the songs represented by the given
	 * parameters.
	 *
	 * @param type MediaUtils.TYPE_ARTIST, TYPE_ALBUM, or TYPE_SONG.
	 * @param id The MediaStore id of the song, artist, or album.
	 * @param projection The columns to query.
	 * @param select An extra selection to pass to the query, or null.
	 * @return The initialized query.
	 */
	private static QueryTask buildMediaQuery(int type, long id, String[] projection, String select)
	{
		Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		StringBuilder selection = new StringBuilder();
		String sort = DEFAULT_SORT;

		switch (type) {
		case TYPE_SONG:
			selection.append(MediaStore.Audio.Media._ID);
			break;
		case TYPE_ARTIST:
			selection.append(MediaStore.Audio.Media.ARTIST_ID);
			break;
		case TYPE_ALBUM:
			selection.append(MediaStore.Audio.Media.ALBUM_ID);
			sort = ALBUM_SORT;
			break;
		default:
			throw new IllegalArgumentException("Invalid type specified: " + type);
		}

		selection.append('=');
		selection.append(id);
		selection.append(" AND length(_data) AND "+MediaStore.Audio.Media.IS_MUSIC);

		if (select != null) {
			selection.append(" AND ");
			selection.append(select);
		}

		QueryTask result = new QueryTask(media, projection, selection.toString(), null, sort);
		result.type = type;
		return result;
	}

	/**
	 * Builds a query that will return all the songs in the playlist with the
	 * given id.
	 *
	 * @param id The id of the playlist in MediaStore.Audio.Playlists.
	 * @param projection The columns to query.
	 * @param selection The selection to pass to the query, or null.
	 * @return The initialized query.
	 */
	public static QueryTask buildPlaylistQuery(long id, String[] projection, String selection)
	{
		Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external", id);
		String sort = MediaStore.Audio.Playlists.Members.PLAY_ORDER;
		QueryTask result = new QueryTask(uri, projection, selection, null, sort);
		result.type = TYPE_PLAYLIST;
		return result;
	}

	/**
	 * Builds a query that will return all the songs in the genre with the
	 * given id.
	 *
	 * @param id The id of the genre in MediaStore.Audio.Genres.
	 * @param projection The columns to query.
	 * @param selection The selection to pass to the query, or null.
	 * @param selectionArgs The arguments to substitute into the selection.
	 * @param sort The sort order.
	 * @param type The media type to query and return
	 * @param returnSongs returns matching songs instead of `type' if true
	 */
	public static QueryTask buildGenreQuery(long id, String[] projection, String selection, String[] selectionArgs, String sort, int type, boolean returnSongs)
	{
		// Note: This function works on a raw sql query with way too much internal
		// knowledge about the mediaProvider SQL table layout. Yes: it's ugly.
		// The reason for this mess is that android has a very crippled genre implementation
		// and does, for example, not allow us to query the albumbs beloging to a genre.

		Uri uri = MediaStore.Audio.Genres.Members.getContentUri("external", id);
		String[] clonedProjection = projection.clone(); // we modify the projection, but this should not be visible to the caller
		String sql = "";
		String authority = "audio";

		if (type == TYPE_ARTIST)
			authority = "artist_info";
		if (type == TYPE_ALBUM)
			authority = "album_info";

		// Our raw SQL query includes the album_info table (well: it's actually a view)
		// which shares some columns with audio.
		// This regexp should matche duplicate column names and forces them to use
		// the audio table as a source
		final String _FORCE_AUDIO_SRC = "(^|[ |,\\(])(_id|album(_\\w+)?|artist(_\\w+)?)";

		// Prefix the SELECTed rows with the current table authority name
		for (int i=0 ;i<clonedProjection.length; i++) {
			if (clonedProjection[i].equals("0") == false) // do not prefix fake rows
				clonedProjection[i] = (returnSongs ? "audio" : authority)+"."+clonedProjection[i];
		}

		sql += TextUtils.join(", ", clonedProjection);
		sql += " FROM audio_genres_map_noid, audio" + (authority.equals("audio") ? "" : ", "+authority);
		sql += " WHERE(audio._id = audio_id AND genre_id=?)";

		if (selection != null && selection.length() > 0)
			sql += " AND("+selection.replaceAll(_FORCE_AUDIO_SRC, "$1audio.$2")+")";

		if (type == TYPE_ARTIST)
			sql += " AND(artist_info._id = audio.artist_id)" + (returnSongs ? "" : " GROUP BY artist_info._id");

		if (type == TYPE_ALBUM)
			sql += " AND(album_info._id = audio.album_id)" + (returnSongs ? "" : " GROUP BY album_info._id");

		if (sort != null && sort.length() > 0)
			sql += " ORDER BY "+sort.replaceAll(_FORCE_AUDIO_SRC, "$1audio.$2");

		// We are now turning this into an sql injection. Fun times.
		clonedProjection[0] = sql +" --";

		QueryTask result = new QueryTask(uri, clonedProjection, selection, selectionArgs, sort);
		result.type = TYPE_GENRE;
		return result;
	}

	/**
	 * Creates a {@link QueryTask} for genres. The query will select only genres that have at least
	 * one song associated with them.
	 *
	 * @param projection The fields of the genre table that should be returned.
	 * @param selection Additional constraints for the query (added to the WHERE section). '?'s
	 * will be replaced by values in {@code selectionArgs}. Can be null.
	 * @param selectionArgs Arguments for {@code selection}. Can be null. See
	 * {@link android.content.ContentProvider#query(Uri, String[], String, String[], String)}
	 * @param sort How the returned genres should be sorted (added to the ORDER BY section)
	 * @return The QueryTask for the genres
	 */
	public static QueryTask buildGenreExcludeEmptyQuery(String[] projection, String selection, String[] selectionArgs, String sort) {
		/*
		 * An example SQLite query that we're building in this function
			SELECT DISTINCT _id, name
			FROM audio_genres
			WHERE
				EXISTS(
					SELECT audio_id, genre_id, audio._id
					FROM audio_genres_map, audio
					WHERE (genre_id == audio_genres._id)
						AND (audio_id == audio._id))
			ORDER BY name DESC
		 */
		Uri uri = MediaStore.Audio.Genres.getContentUri("external");
		StringBuilder sql = new StringBuilder();
		// Don't want multiple identical genres
		sql.append("DISTINCT ");

		// Add the projection fields to the query
		sql.append(TextUtils.join(", ", projection)).append(' ');

		sql.append("FROM audio_genres ");
		// Limit to genres that contain at least one valid song
		sql.append("WHERE EXISTS( ")
			.append("SELECT audio_id, genre_id, audio._id ")
			.append("FROM audio_genres_map, audio ")
			.append("WHERE (genre_id == audio_genres._id) AND (audio_id == audio._id) ")
			.append(") ");

		if (!TextUtils.isEmpty(selection))
			sql.append(" AND(" + selection + ") ");

		if(!TextUtils.isEmpty(sort))
			sql.append(" ORDER BY ").append(sort);

		// Ignore the framework generated query
		sql.append(" -- ");
		String[] injectedProjection = new String[1];
		injectedProjection[0] = sql.toString();

		// Don't pass the selection/sort as we've already added it to the query
		return new QueryTask(uri, injectedProjection, null, selectionArgs, null);
	}

	/**
	 * Builds a query with the given information.
	 *
	 * @param type Type the id represents. Must be one of the Song.TYPE_*
	 * constants.
	 * @param id The id of the element in the MediaStore content provider for
	 * the given type.
	 * @param selection An extra selection to be passed to the query. May be
	 * null. Must not be used with type == TYPE_SONG or type == TYPE_PLAYLIST
	 */
	public static QueryTask buildQuery(int type, long id, String[] projection, String selection)
	{
		switch (type) {
		case TYPE_ARTIST:
		case TYPE_ALBUM:
		case TYPE_SONG:
			return buildMediaQuery(type, id, projection, selection);
		case TYPE_PLAYLIST:
			return buildPlaylistQuery(id, projection, selection);
		case TYPE_GENRE:
			return buildGenreQuery(id, projection, selection, null,  MediaStore.Audio.Genres.Members.TITLE_KEY, TYPE_SONG, true);
		default:
			throw new IllegalArgumentException("Specified type not valid: " + type);
		}
	}

	/**
	 * Query the MediaStore to determine the id of the genre the song belongs
	 * to.
	 *
	 * @param resolver A ContentResolver to use.
	 * @param id The id of the song to query the genre for.
	 */
	public static long queryGenreForSong(ContentResolver resolver, long id)
	{
		String[] projection = { "_id" };
		Uri uri = MediaStore.Audio.Genres.getContentUriForAudioId("external", (int)id);
		Cursor cursor = queryResolver(resolver, uri, projection, null, null, null);

		if (cursor != null) {
			if (cursor.moveToNext())
				return cursor.getLong(0);
			cursor.close();
		}
		return 0;
	}

	/**
	 * Shuffle an array using Fisher-Yates algorithm.
	 *
	 * @param list The array. It will be shuffled in place.
	 */
	public static void shuffle(long[] list)
	{
		Random random = getRandom();
		for (int i = list.length; --i != -1; ) {
			int j = random.nextInt(i + 1);
			long tmp = list[j];
			list[j] = list[i];
			list[i] = tmp;
		}
	}

	/**
	 * Shuffle a Song list using Collections.shuffle().
	 *
	 * @param albumShuffle If true, preserve the order of tracks inside albums.
	 */
	public static void shuffle(List<Song> list, boolean albumShuffle)
	{
		int size = list.size();
		if (size < 2)
			return;

		Random random = getRandom();
		if (albumShuffle) {
			List<Song> tempList = new ArrayList<Song>(list);
			Collections.sort(tempList);

			// Build map of albumId to start index in sorted list
			Map<Long, Integer> albumStartIndices = new HashMap<Long, Integer>();
			int index = 0;
			for (Song song : tempList) {
				if (!albumStartIndices.containsKey(song.albumId)) {
					albumStartIndices.put(song.albumId, index);
				}
				index++;
			}

			//Extract album list and shuffle
			List<Long> shuffledAlbums = new ArrayList<Long>(albumStartIndices.keySet());
			Collections.shuffle(shuffledAlbums, random);

			//Build Song list from album list
			list.clear();
			for (Long albumId : shuffledAlbums) {
				int songIndex = albumStartIndices.get(albumId);
				Song song = tempList.get(songIndex);
				do {
					list.add(song);
					songIndex++;
					if (songIndex < size) {
						song = tempList.get(songIndex);
					} else {
						break;
					}
				} while (albumId == song.albumId);
			}
		} else {
			Collections.shuffle(list, random);
		}
	}

	/**
	 * Determine if any songs are available from the library.
	 *
	 * @param resolver A ContentResolver to use.
	 * @return True if it's possible to retrieve any songs, false otherwise. For
	 * example, false could be returned if there are no songs in the library.
	 */
	public static boolean isSongAvailable(ContentResolver resolver)
	{
		if (sSongCount == -1) {
			Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
			String selection = MediaStore.Audio.Media.IS_MUSIC;
			selection += " AND length(_data)";
			Cursor cursor = queryResolver(resolver, media, new String[]{"count(_id)"}, selection, null, null);
			if (cursor == null) {
				sSongCount = 0;
			} else {
				cursor.moveToFirst();
				sSongCount = cursor.getInt(0);
				cursor.close();
			}
		}

		return sSongCount != 0;
	}

	/**
	 * Returns a shuffled array contaning the ids of all the songs on the
	 * device's library.
	 *
	 * @param resolver A ContentResolver to use.
	 */
	private static long[] queryAllSongs(ContentResolver resolver)
	{
		Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		String selection = MediaStore.Audio.Media.IS_MUSIC;
		selection += " AND length(_data)";
		Cursor cursor = queryResolver(resolver, media, Song.EMPTY_PROJECTION, selection, null, null);
		if (cursor == null || cursor.getCount() == 0) {
			sSongCount = 0;
			return null;
		}

		int count = cursor.getCount();
		long[] ids = new long[count];
		for (int i = 0; i != count; ++i) {
			if (!cursor.moveToNext())
				return null;
			ids[i] = cursor.getLong(0);
		}
		sSongCount = count;
		cursor.close();

		shuffle(ids);

		return ids;
	}

	/**
	 * Runs a query on the passed content resolver.
	 * Catches (and returns null on) SecurityException (= user revoked read permission)
	 *
	 * @param resolver The content resolver to use
	 * @param uri the uri to query
	 * @param projection the projection to use
	 * @param selection the selection to use
	 * @param selectionArgs arguments for the selection
	 * @param sortOrder sort order of the returned result
	 *
	 * @return a cursor or null
	 */
	public static Cursor queryResolver(ContentResolver resolver, Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)
	{
		Cursor cursor = null;
		try {
			cursor = resolver.query(uri, projection, selection, selectionArgs, sortOrder);
		} catch(java.lang.SecurityException e) {
			// we do not have read permission - just return a null cursor
		}
		return cursor;
	}

	public static void onMediaChange()
	{
		sSongCount = -1;
		sAllSongs = null;
	}

	/**
	 * Creates and sends share intent across the system. Includes all eligible songs found
	 * within this type and id (e.g. all songs in album, all songs for this artist etc.)
	 * @param ctx context to execute resolving on
	 * @param type media type to look for e.g. {@link MediaUtils#TYPE_SONG}
	 * @param id id of item to send
	 */
	public static void shareMedia(Context ctx, int type, long id) {
		if (type == TYPE_INVALID || id <= 0) { // invalid
			return;
		}

		ContentResolver resolver = ctx.getContentResolver();
		String[] projection = new String [] { MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA };
		Cursor cursor = buildQuery(type, id, projection, null).runQuery(resolver);
		if(cursor == null) {
			return;
		}

		try {
			while (cursor.moveToNext()) { // for all songs resolved...
				File songFile = new File(cursor.getString(1));
				Intent share = new Intent(Intent.ACTION_SEND);
				share.setType("audio/*");
				share.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(songFile));
				ctx.startActivity(Intent.createChooser(share, ctx.getResources().getString(R.string.sendto)));
			}
		} catch (ActivityNotFoundException ex) {
			Toast.makeText(ctx, R.string.no_receiving_apps, Toast.LENGTH_SHORT).show();
		} finally {
			cursor.close();
		}
	}

	/**
	 * Returns the first matching song (or NULL) of given type + id combination
	 *
	 * @param resolver A ContentResolver to use.
	 * @param type The MediaTye to query
	 * @param id The id of given type to query
	 */
	public static Song getSongByTypeId(ContentResolver resolver, int type, long id) {
		Song song = new Song(-1);
		QueryTask query = buildQuery(type, id, Song.FILLED_PROJECTION, null);
		Cursor cursor = query.runQuery(resolver);
		if (cursor != null) {
			if (cursor.getCount() > 0) {
				cursor.moveToPosition(0);
				song.populate(cursor);
			}
			cursor.close();
		}
		return song.isFilled() ? song : null;
	}

	/**
	 * Returns a song randomly selected from all the songs in the Android
	 * MediaStore.
	 *
	 * @param resolver A ContentResolver to use.
	 */
	public static Song randomSong(ContentResolver resolver)
	{
		long[] songs = sAllSongs;

		if (songs == null) {
			songs = queryAllSongs(resolver);
			if (songs == null)
				return null;
			sAllSongs = songs;
			sAllSongsIdx = 0;
		} else if (sAllSongsIdx == sAllSongs.length) {
			sAllSongsIdx = 0;
			shuffle(sAllSongs);
		}

		Song result = getSongByTypeId(resolver, MediaUtils.TYPE_SONG, sAllSongs[sAllSongsIdx]);
		sAllSongsIdx++;
		return result;
	}

	/**
	 * Delete the given file or directory recursively.
	 *
	 * @return True if successful; false otherwise.
	 */
	public static boolean deleteFile(File file)
	{
		File[] children = file.listFiles();
		if (children != null) {
			for (File child : children) {
				deleteFile(child);
			}
		}
		return file.delete();
	}


	/**
	* This is an ugly hack: The tries to 'guess' if given path
	* is also accessible using a fuse mount
	*/
	private static String sanitizeMediaPath(String path) {

		String exPath  = Environment.getExternalStorageDirectory().getAbsolutePath();
		File exStorage = new File(exPath+"/Android");
		long exLastmod = exStorage.lastModified();

		if(exLastmod > 0 && path != null) {
			String pfx = path;
			while(true) {
				if((new File(pfx+"/Android")).lastModified() == exLastmod) {
					String guessPath = exPath + path.substring(pfx.length());
					if( (new File(guessPath)).exists() ) {
						path = guessPath;
						break;
					}
				}

				pfx = (new File(pfx)).getParent();
				if(pfx == null)
					break; /* hit root */
			}
		}

		return path;
	}

	/**
	* Adds a final slash if the path points to an existing directory
	*/
	private static String addDirEndSlash(String path) {
		if(path.length() > 0 && path.charAt(path.length()-1) != '/') {
			if( (new File(path)).isDirectory() ) {
				path += "/";
			}
		}
		return path;
	}

	/**
	 * Build a query that will contain all the media under the given path.
	 *
	 * @param path The path, e.g. /mnt/sdcard/music/
	 * @param projection The columns to query
	 * @return The initialized query.
	 */
	public static QueryTask buildFileQuery(String path, String[] projection)
	{
		/* make sure that the path is:
		   -> fixed-up to point to the real mountpoint if user browsed to the mediadir symlink
		   -> terminated with a / if it is a directory
		   -> ended with a % for the LIKE query
		*/
		path = addDirEndSlash(sanitizeMediaPath(path)) + "%";
		final String query = "_data LIKE ? AND "+MediaStore.Audio.Media.IS_MUSIC;
		String[] qargs = { path };

		Uri media = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
		QueryTask result = new QueryTask(media, projection, query, qargs, FILE_SORT);
		result.type = TYPE_FILE;
		return result;
	}

	/**
	 * Returns a (possibly empty) Cursor for given file path
	 * @param path The path to the file to be queried
	 * @return A new Cursor object
	 * */
	public static Cursor getCursorForFileQuery(String path) {
		MatrixCursor matrixCursor = new MatrixCursor(Song.FILLED_PROJECTION);
		MediaMetadataRetriever data = new MediaMetadataRetriever();

		try {
			data.setDataSource(path);
		} catch (Exception e) {
				Log.w("VanillaMusic", "Failed to extract metadata from " + path);
		}

		String title = data.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
		String album = data.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
		String artist = data.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
		String duration = data.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);

		if (duration != null) { // looks like we will be able to play this file
			// Vanilla requires each file to be identified by its unique id in the media database.
			// However: This file is not in the database, so we are going to roll our own
			// using the negative crc32 sum of the path value. While this is not perfect
			// (the same file may be accessed using various paths) it's the fastest method
			// and far good enough.
			CRC32 crc = new CRC32();
			crc.update(path.getBytes());
			Long songId = (Long)(2+crc.getValue())*-1; // must at least be -2 (-1 defines Song-Object to be empty)

			// Build minimal fake-database entry for this file
			Object[] objData = new Object[] { songId, path, "", "", "", 0, 0, 0, 0 };

			if (title != null)
				objData[2] = title;
			if (album != null)
				objData[3] = album;
			if (artist != null)
				objData[4] = artist;
			if (duration != null)
				objData[7] = Long.parseLong(duration, 10);

			matrixCursor.addRow(objData);
		}

		return matrixCursor;
	}

	/**
	 * Retrieve ID of specified media type for requested song. This works only for
	 * media-oriented types: {@link #TYPE_ARTIST}, {@link #TYPE_ALBUM}, {@link #TYPE_SONG}
	 * @param song requested song
	 * @param mType media type e.g. {@link #TYPE_ARTIST}
     * @return ID of media type, {@link #TYPE_INVALID} if unsupported
     */
	public static long getCurrentIdForType(Song song, int mType)
	{
		if(song == null)
			return TYPE_INVALID;

		switch(mType) {
			case TYPE_ARTIST:
				return song.artistId;
			case TYPE_ALBUM:
				return song.albumId;
			case TYPE_SONG:
				return song.id;
			default:
				return TYPE_INVALID;
		}
	}

}
