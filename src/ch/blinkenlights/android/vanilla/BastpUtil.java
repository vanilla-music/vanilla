/*
 * Copyright (C) 2013 Adrian Ulrich <adrian@blinkenlights.ch>
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
	private RGLruCache rgCache;
	
	public BastpUtil() {
		rgCache = new RGLruCache(16); /* Cache up to 16 entries */
	}
	
	
	/** Returns the ReplayGain values of 'path' as <track,album>
	 */
	public float[] getReplayGainValues(String path) {
		float[] cached = rgCache.get(path);

		if(cached == null) {
			cached = getReplayGainValuesFromFile(path);
			rgCache.put(path, cached);
		}
		return cached;
	}
	
	
	
	/** Parse given file and return track,album replay gain values
	 */
	private float[] getReplayGainValuesFromFile(String path) {
		String[] keys = { "REPLAYGAIN_TRACK_GAIN", "REPLAYGAIN_ALBUM_GAIN" };
		float[] adjust= { 0f                     , 0f                      };
		HashMap tags  = (new Bastp()).getTags(path);
		
		for (int i=0; i<keys.length; i++) {
			String curKey = keys[i];
			if(tags.containsKey(curKey)) {
				String rg_raw = (String)((Vector)tags.get(curKey)).get(0);
				String rg_numonly = "";
				float rg_float = 0f;
				try {
					String nums = rg_raw.replaceAll("[^0-9.-]","");
					rg_float = Float.parseFloat(nums);
				} catch(Exception e) {}
				adjust[i] = rg_float;
			}
		}
		return adjust;
	}
	
	/** LRU cache for ReplayGain values
	 */
	private class RGLruCache extends LruCache<String, float[]> {
		public RGLruCache(int size) {
			super(size);
		}
	}

}

