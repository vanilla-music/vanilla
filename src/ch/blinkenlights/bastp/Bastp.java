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


package ch.blinkenlights.bastp;

import ch.blinkenlights.bastp.OggFile;
import ch.blinkenlights.bastp.FlacFile;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.util.HashMap;


public class Bastp {

	public Bastp() {
	}

	public HashMap getTags(String fname) {
		HashMap tags = new HashMap();
		try {
			RandomAccessFile ra = new RandomAccessFile(fname, "r");
			tags = getTags(ra);
			ra.close();
		}
		catch(Exception e) {
			/* we dont' care much: SOMETHING went wrong. d'oh! */
		}
		
		return tags;
	}
	
	public HashMap getTags(RandomAccessFile s) {
		HashMap tags = new HashMap();
		byte[] file_ff = new byte[4];
		
		try {
			s.read(file_ff);
			String magic = new String(file_ff);
			if(magic.equals("fLaC")) {
				tags = (new FlacFile()).getTags(s);
				tags.put("type", "FLAC");
			}
			else if(magic.equals("OggS")) {
				// This may be an Opus OR an Ogg Vorbis file
				tags = (new OpusFile()).getTags(s);
				if (tags.size() > 0) {
					tags.put("type", "OPUS");
				} else {
					tags = (new OggFile()).getTags(s);
					tags.put("type", "OGG");
				}
			}
			else if(file_ff[0] == -1 && file_ff[1] == -5) { /* aka 0xfffb in real languages */
				tags = (new LameHeader()).getTags(s);
				tags.put("type", "MP3/Lame");
			}
			else if(magic.substring(0,3).equals("ID3")) {
				tags = (new ID3v2File()).getTags(s);
				if(tags.containsKey("_hdrlen")) {
					Long hlen = Long.parseLong( tags.get("_hdrlen").toString(), 10 );
					HashMap lameInfo = (new LameHeader()).parseLameHeader(s, hlen);
					/* add tags from lame header if not already present */
					inheritTag("REPLAYGAIN_TRACK_GAIN", lameInfo, tags);
					inheritTag("REPLAYGAIN_ALBUM_GAIN", lameInfo, tags);
					inheritTag("duration", lameInfo, tags);
				}
				tags.put("type", "MP3/ID3v2");
			}

		}
		catch (IOException e) {
		}
		return tags;
	}
	
	private void inheritTag(String key, HashMap from, HashMap to) {
		if(!to.containsKey(key) && from.containsKey(key)) {
			to.put(key, from.get(key));
		}
	}
	
}

