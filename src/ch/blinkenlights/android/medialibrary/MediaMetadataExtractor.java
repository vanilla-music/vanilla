/*
 * Copyright (C) 2016 Adrian Ulrich <adrian@blinkenlights.ch>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>. 
 */

package ch.blinkenlights.android.medialibrary;

import ch.blinkenlights.bastp.Bastp;
import android.media.MediaMetadataRetriever;
import java.util.HashMap;
import java.util.ArrayList;

public class MediaMetadataExtractor extends HashMap<String, ArrayList<String>> {
	// Well known tags
	public final static String ALBUM        = "ALBUM";
	public final static String ALBUMARTIST  = "ALBUM_ARTIST";
	public final static String ARTIST       = "ARTIST";
	public final static String BITRATE      = "BITRATE";
	public final static String COMPOSER     = "COMPOSER";
	public final static String DISC_COUNT   = "DISC_COUNT";
	public final static String DISC_NUMBER  = "DISC_NUMBER";
	public final static String DURATION     = "DURATION";
	public final static String GENRE        = "GENRE";
	public final static String MIME_TYPE    = "MIME";
	public final static String TRACK_COUNT  = "TRACK_COUNT";
	public final static String TRACK_NUMBER = "TRACK_NUM";
	public final static String TITLE        = "TITLE";
	public final static String YEAR         = "YEAR";

	/**
	 * Constructor for MediaMetadataExtractor
	 *
	 * @param path the path to scan
	 */
	public MediaMetadataExtractor(String path) {
		extractMetadata(path);
	}

	/**
	 * Returns the first element matching this key, null on if not found
	 *
	 * @param key the key to look up
	 * @return the value of the first entry, null if the key was not found
	 */
	public String getFirst(String key) {
		String result = null;
		if (containsKey(key))
			result = get(key).get(0);
		return result;
	}

	/**
	 * Attempts to populate this instance with tags found in given path
	 *
	 * @param path the path to parse
	 * @return void, but populates `this'
	 */
	private void extractMetadata(String path) {
		if (isEmpty() == false)
			throw new IllegalStateException("Expected to be called on a clean HashMap");

		HashMap bastpTags = (new Bastp()).getTags(path);
		MediaMetadataRetriever mediaTags = new MediaMetadataRetriever();
		try {
			mediaTags.setDataSource(path);
		} catch (Exception e) { /* we will later just check the contents of mediaTags */ }

		// Check if this is an useable audio file
		if (mediaTags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == null ||
		    mediaTags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO) != null ||
		    mediaTags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION) == null) {
		    mediaTags.release();
			return;
		}

		// Bastp can not read the duration and bitrates, so we always get it from the system
		ArrayList<String> duration = new ArrayList<String>(1);
		duration.add(mediaTags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
		this.put(DURATION, duration);

		ArrayList<String> bitrate = new ArrayList<String>(1);
		bitrate.add(mediaTags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));
		this.put(BITRATE, bitrate);

		ArrayList<String> mime = new ArrayList<String>(1);
		mime.add(mediaTags.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE));
		this.put(MIME_TYPE, mime);


		// ...but we are using bastp for FLAC, OGG and OPUS as it handles them well
		// Everything else goes to the framework (such as pcm, m4a and mp3)
		String bastpType = (bastpTags.containsKey("type") ? (String)bastpTags.get("type") : "");
		switch (bastpType) {
			case "FLAC":
			case "OGG":
			case "OPUS":
				populateSelf(bastpTags);
				break;
			default:
				populateSelf(mediaTags);
		}

		mediaTags.release();
	}

	/**
	 * Populates `this' with tags read from bastp
	 *
	 * @param bastp A hashmap as returned by bastp
	 */
	private void populateSelf(HashMap bastp) {
		// mapping between vorbiscomment -> constant
		String[] map = new String[]{ "TITLE", TITLE, "ARTIST", ARTIST, "ALBUM", ALBUM, "ALBUMARTIST", ALBUMARTIST, "COMPOSER", COMPOSER, "GENRE", GENRE,
		                             "TRACKNUMBER", TRACK_NUMBER, "TRACKTOTAL", TRACK_COUNT, "DISCNUMBER", DISC_NUMBER, "DISCTOTAL", DISC_COUNT,
		                             "YEAR", YEAR };
		for (int i=0; i<map.length; i+=2) {
			if (bastp.containsKey(map[i])) {
				ArrayList<String> tags = (ArrayList<String>)bastp.get(map[i]);
				put(map[i+1], tags);
			}
		}
	}

	/**
	 * Populates `this' with tags read from the MediaMetadataRetriever
	 *
	 * @param tags a MediaMetadataRetriever object
	 */
	private void populateSelf(MediaMetadataRetriever tags) {
		int[] mediaMap = new int[] { MediaMetadataRetriever.METADATA_KEY_TITLE, MediaMetadataRetriever.METADATA_KEY_ARTIST, MediaMetadataRetriever.METADATA_KEY_ALBUM,
		                             MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, MediaMetadataRetriever.METADATA_KEY_COMPOSER, MediaMetadataRetriever.METADATA_KEY_GENRE,
		                             MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER, MediaMetadataRetriever.METADATA_KEY_YEAR };
		String[] selfMap = new String[]{  TITLE, ARTIST, ALBUM, ALBUMARTIST, COMPOSER, GENRE, TRACK_NUMBER, YEAR };
		for (int i=0; i<selfMap.length; i++) {
			String data = tags.extractMetadata(mediaMap[i]);
			if (data != null) {
				ArrayList<String> md = new ArrayList<String>(1);
				md.add(data);
				put(selfMap[i], md);
			}
		}
	}


}
