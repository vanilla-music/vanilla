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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Vector;

public class Common {
	private static final long MAX_PKT_SIZE = 524288;
	
	public void xdie(String reason) throws IOException {
		throw new IOException(reason);
	}
	
	/*
	** Returns a 32bit int from given byte offset in LE
	*/
	public int b2le32(byte[] b, int off) {
		int r = 0;
		for(int i=0; i<4; i++) {
			r |= ( b2u(b[off+i]) << (8*i) );
		}
		return r;
	}
	
	public int b2be32(byte[] b, int off) {
		return swap32(b2le32(b, off));
	}
	
	public int swap32(int i) {
		return((i&0xff)<<24)+((i&0xff00)<<8)+((i&0xff0000)>>8)+((i>>24)&0xff);
	}
	
	/*
	** convert 'byte' value into unsigned int
	*/
	public int b2u(byte x) {
		return (x & 0xFF);
	}
	
	/*
	** Printout debug message to STDOUT
	*/
	public void debug(String s) {
		System.out.println("DBUG "+s);
	}
	
	public HashMap parse_vorbis_comment(RandomAccessFile s, long offset, long payload_len) throws IOException {
		HashMap tags = new HashMap();
		int comments   = 0;                // number of found comments 
		int xoff       = 0;                // offset within 'scratch'
		int can_read   = (int)(payload_len > MAX_PKT_SIZE ? MAX_PKT_SIZE : payload_len);
		byte[] scratch = new byte[can_read];
		
		// seek to given position and slurp in the payload
		s.seek(offset);
		s.read(scratch);
		
		// skip vendor string in format: [LEN][VENDOR_STRING] 
		xoff    += 4 + b2le32(scratch, xoff); // 4 = LEN = 32bit int 
		comments = b2le32(scratch, xoff);
		xoff    += 4;
		
		// debug("comments count = "+comments);
		for(int i=0; i<comments; i++) {
			
			int clen = (int)b2le32(scratch, xoff);
			xoff += 4+clen;
			
			if(xoff > scratch.length)
				xdie("string out of bounds");
			
			String   tag_raw = new String(scratch, xoff-clen, clen);
			String[] tag_vec = tag_raw.split("=",2);
			String   tag_key = tag_vec[0].toUpperCase();
			
			addTagEntry(tags, tag_key, tag_vec[1]);
		}
		return tags;
	}
	
	public void addTagEntry(HashMap tags, String key, String value) {
		if(tags.containsKey(key)) {
			((Vector)tags.get(key)).add(value); // just add to existing vector
		}
		else {
			Vector vx = new Vector();
			vx.add(value);
			tags.put(key, vx);
		}
	}
	
}
