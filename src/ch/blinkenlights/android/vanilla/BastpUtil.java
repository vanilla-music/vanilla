/*
 * Copyright (C) 2013-2016 Adrian Ulrich <adrian@blinkenlights.ch>
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
 
package ch.blinkenlights.android.vanilla;

import android.util.LruCache;
import ch.blinkenlights.bastp.Bastp;
import java.util.HashMap;
import java.util.Vector;

public class BastpUtil {
	/**
	 * Our global instance cache
	 */
	private RGLruCache rgCache;
	/**
	 * What we return & cache
	 */
	public class GainValues {
		public float base;
		public float album;
		public float track;
	}
	/**
	 * LRU cache for ReplayGain values
	 */
	private class RGLruCache extends LruCache<String, GainValues> {
		public RGLruCache(int size) {
			super(size);
		}
	}


	public BastpUtil() {
		rgCache = new RGLruCache(64); /* Cache up to 64 entries */
	}

	/**
	 * Returns a GainValues object for `path'
	 */
	public GainValues getReplayGainValues(String path) {
		if(path == null) {
			// path must not be null
			path = "//null\\";
		}

		GainValues cached = rgCache.get(path);
		if(cached == null) {
			cached = getReplayGainValuesFromFile(path);
			rgCache.put(path, cached);
		}
		return cached;
	}

	/**
	 *  Parse given file and return track,album replay gain values
	 */
	private GainValues getReplayGainValuesFromFile(String path) {
		HashMap tags  = (new Bastp()).getTags(path);
		GainValues gv = new GainValues();

		// normal replay gain, add 5dB difference
		if(tags.containsKey("REPLAYGAIN_TRACK_GAIN"))
			gv.track = getFloatFromString((String)((Vector)tags.get("REPLAYGAIN_TRACK_GAIN")).get(0));
		if(tags.containsKey("REPLAYGAIN_ALBUM_GAIN"))
			gv.album = getFloatFromString((String)((Vector)tags.get("REPLAYGAIN_ALBUM_GAIN")).get(0));

		// likely OPUS
		if(tags.containsKey("R128_BASTP_BASE_GAIN"))
			gv.base = 0.0f + getFloatFromString((String)((Vector)tags.get("R128_BASTP_BASE_GAIN")).get(0)) / 256.0f;
		if(tags.containsKey("R128_TRACK_GAIN"))
			gv.track = 5.0f + getFloatFromString((String)((Vector)tags.get("R128_TRACK_GAIN")).get(0)) / 256.0f;
		if(tags.containsKey("R128_ALBUM_GAIN"))
			gv.album = 5.0f + getFloatFromString((String)((Vector)tags.get("R128_ALBUM_GAIN")).get(0)) / 256.0f;

		return gv;
	}

	/**
	 * Parses common replayGain string values
	 */
	private float getFloatFromString(String rg_raw) {
		float rg_float = 0f;
		try {
			String nums = rg_raw.replaceAll("[^0-9.-]","");
			rg_float = Float.parseFloat(nums);
		} catch(Exception e) {}
		return rg_float;
	}

}

