/*****************************************************************
 *  This file is part of 'bastp!' - the BuggyAndSloppyTagParser! *
 *                                                               *
 * (C) 2012 Adrian Ulrich                                        *
 *                                                               *
 * Released as 'Public Domain' software                          *
 *                                                               *
 *                                                               *
 *****************************************************************/


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
			}
			else if(magic.equals("OggS")) {
				tags = (new OggFile()).getTags(s);
			}
			else if(file_ff[0] == -1 && file_ff[1] == -5) { /* aka 0xfffb in real languages */
				tags = (new LameHeader()).getTags(s);
			}
			else if(magic.substring(0,3).equals("ID3")) {
				tags = (new ID3v2File()).getTags(s);
				if(tags.containsKey("_hdrlen")) {
					Long hlen = Long.parseLong( tags.get("_hdrlen").toString(), 10 );
					HashMap lameInfo = (new LameHeader()).parseLameHeader(s, hlen);
					/* add gain tags if not already present */
					inheritTag("REPLAYGAIN_TRACK_GAIN", lameInfo, tags);
					inheritTag("REPLAYGAIN_ALBUM_GAIN", lameInfo, tags);
				}
			}
			tags.put("_magic", magic);
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

