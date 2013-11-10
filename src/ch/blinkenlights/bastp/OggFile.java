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


public class OggFile extends Common {

	private static final int OGG_PAGE_SIZE    = 27;  // Static size of an OGG Page
	private static final int OGG_TYPE_COMMENT = 3;   // ID of 'VorbisComment's
	
	public OggFile() {
	}
	
	public HashMap getTags(RandomAccessFile s) throws IOException {
		long offset = 0;
		int  retry  = 64;
		HashMap tags = new HashMap();
		
		for( ; retry > 0 ; retry-- ) {
			long res[] = parse_ogg_page(s, offset);
			if(res[2] == OGG_TYPE_COMMENT) {
				tags = parse_ogg_vorbis_comment(s, offset+res[0], res[1]);
				break;
			}
			offset += res[0] + res[1];
		}
		return tags;
	}
	
	
	/* Parses the ogg page at offset 'offset' and returns
	** [header_size, payload_size, type]
	*/
	private long[] parse_ogg_page(RandomAccessFile s, long offset) throws IOException {
		long[] result   = new long[3];               // [header_size, payload_size]
		byte[] p_header = new byte[OGG_PAGE_SIZE];   // buffer for the page header 
		byte[] scratch;
		int bread       = 0;                         // number of bytes read
		int psize       = 0;                         // payload-size
		int nsegs       = 0;                         // Number of segments
		
		s.seek(offset);
		bread = s.read(p_header);
		if(bread != OGG_PAGE_SIZE)
			xdie("Unable to read() OGG_PAGE_HEADER");
		if((new String(p_header, 0, 5)).equals("OggS\0") != true)
			xdie("Invalid magic - not an ogg file?");
		
		nsegs = b2u(p_header[26]); 
		// debug("> file seg: "+nsegs);
		if(nsegs > 0) {
			scratch  = new byte[nsegs];
			bread    = s.read(scratch); 
			if(bread != nsegs)
				xdie("Failed to read segtable");
			
			for(int i=0; i<nsegs; i++) {
				psize += b2u(scratch[i]); 
			}
		}
		
		// populate result array
		result[0] = (s.getFilePointer() - offset);
		result[1] = psize;
		result[2] = -1;
		
		/* next byte is most likely the type -> pre-read */
		if(psize >= 1 && s.read(p_header, 0, 1) == 1) {
			result[2] = b2u(p_header[0]);
		}
		
		return result;
	}
	
	/* In 'vorbiscomment' field is prefixed with \3vorbis in OGG files
	** we check that this marker is present and call the generic comment
	** parset with the correct offset (+7) */
	private HashMap parse_ogg_vorbis_comment(RandomAccessFile s, long offset, long pl_len) throws IOException {
		final int pfx_len = 7;
		byte[] pfx        = new byte[pfx_len];
		
		if(pl_len < pfx_len)
			xdie("ogg vorbis comment field is too short!");
		
		s.seek(offset);
		s.read(pfx);
		
		if( (new String(pfx, 0, pfx_len)).equals("\3vorbis") == false )
			xdie("Damaged packet found!");
		
		return parse_vorbis_comment(s, offset+pfx_len, pl_len-pfx_len);
	}
	
};
