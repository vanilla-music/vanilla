/*****************************************************************
 *  This file is part of 'bastp!' - the BuggyAndSloppyTagParser! *
 *                                                               *
 * (C) 2012 Adrian Ulrich                                        *
 *                                                               *
 * Released as 'Public Domain' software                          *
 *                                                               *
 * This is junk but the ID3v2 spec is even worse!                *
 *                                                               *
 *****************************************************************/

package ch.blinkenlights.bastp;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Enumeration;


public class LameHeader extends Common {
	
	public LameHeader() {
	}
	
	public HashMap getTags(RandomAccessFile s) throws IOException {
		return parseLameHeader(s, 0);
	}
	
	public HashMap parseLameHeader(RandomAccessFile s, long offset) throws IOException {
		HashMap tags = new HashMap();
		byte[] chunk = new byte[4];
		
		s.seek(offset + 0x24);
		s.read(chunk);
		
		String lameMark = new String(chunk, 0, chunk.length, "ISO-8859-1");
		
		if(lameMark.equals("Info") || lameMark.equals("Xing")) {
			s.seek(offset+0xAB);
			s.read(chunk);
			
			int raw = b2be32(chunk, 0);
			int gtrk_raw = raw >> 16;     /* first 16 bits are the raw track gain value */
			int galb_raw = raw & 0xFFFF;  /* the rest is for the album gain value       */
			
			float gtrk_val = (float)(gtrk_raw & 0x01FF)/10;
			float galb_val = (float)(galb_raw & 0x01FF)/10;
			
			gtrk_val = ((gtrk_raw&0x0200)!=0 ? -1*gtrk_val : gtrk_val);
			galb_val = ((galb_raw&0x0200)!=0 ? -1*galb_val : galb_val);
			
			if( (gtrk_raw&0xE000) == 0x2000 ) {
				addTagEntry(tags, "REPLAYGAIN_TRACK_GAIN", gtrk_val+" dB");
			}
			if( (gtrk_raw&0xE000) == 0x4000 ) {
				addTagEntry(tags, "REPLAYGAIN_ALBUM_GAIN", galb_val+" dB");
			}
			
		}
		
		return tags;
	}
	
}
