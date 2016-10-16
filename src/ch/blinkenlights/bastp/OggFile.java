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


public class OggFile extends Common implements PageInfo.PageParser {

	private static final int OGG_PAGE_SIZE           = 27;  // Static size of an OGG Page
	private static final int OGG_TYPE_IDENTIFICATION = 1;   // Identification header
	private static final int OGG_TYPE_COMMENT        = 3;   // ID of 'VorbisComment's
	
	public OggFile() {
	}
	
	public HashMap getTags(RandomAccessFile s) throws IOException {
		long offset = 0;
		int  retry  = 64;
		boolean need_tags = true;
		boolean need_id = true;

		HashMap tags = new HashMap();
		HashMap identification = new HashMap();
		
		for( ; retry > 0 ; retry-- ) {
			PageInfo pi = parse_stream_page(s, offset);
			if(pi.type == OGG_TYPE_IDENTIFICATION) {
				identification = parse_ogg_vorbis_identification(s, offset+pi.header_len, pi.payload_len);
				need_id = false;
			} else if(pi.type == OGG_TYPE_COMMENT) {
				tags = parse_ogg_vorbis_comment(s, offset+pi.header_len, pi.payload_len);
				need_tags = false;
			}
			offset += pi.header_len + pi.payload_len;
			if (need_tags == false && need_id == false) {
				break;
			}
		}

		// Calculate duration in seconds
		// Note that this calculation is WRONG: We would have to get the last
		// packet to calculate the real length - but this is goot enough.
		if (identification.containsKey("bitrate_nominal")) {
			int br_nom = (Integer)identification.get("bitrate_nominal") / 8;
			long file_length = s.length();
			if (file_length > 0 && br_nom > 0) {
				tags.put("duration", (int)(file_length/br_nom));
			}
		}

		return tags;
	}
	
	
	/**
	 * Parses the ogg page at offset 'offset'
	 */
	public PageInfo parse_stream_page(RandomAccessFile s, long offset) throws IOException {
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

		PageInfo pi    = new PageInfo();
		pi.header_len  = (s.getFilePointer() - offset);
		pi.payload_len = psize;
		pi.type        = -1;

		/* next byte is most likely the type -> pre-read */
		if(psize >= 1 && s.read(p_header, 0, 1) == 1) {
			pi.type = b2u(p_header[0]);
		}

		return pi;
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
		
		return parse_vorbis_comment(s, this, offset+pfx_len, pl_len-pfx_len);
	}

	/*
	 ** Returns a hashma with parsed vorbis identification header data
	 **/
	private HashMap parse_ogg_vorbis_identification(RandomAccessFile s, long offset, long pl_len) throws IOException {
		/* Structure:
		 * 7 bytes of \1vorbis
		 * 4 bytes version
		 * 1 byte channels
		 * 4 bytes sampling rate
		 * 4 bytes bitrate max
		 * 4 bytes bitrate nominal
		 * 4 bytes bitrate min
		 **/
		HashMap id_hash = new HashMap();
		byte[] buff = new byte[28];

		if(pl_len >= buff.length) {
			s.seek(offset);
			s.read(buff);
			id_hash.put("version"         , b2le32(buff, 7));
			id_hash.put("channels"        , b2u(buff[11]));
			id_hash.put("sampling_rate"   , b2le32(buff, 12));
			id_hash.put("bitrate_minimal" , b2le32(buff, 16));
			id_hash.put("bitrate_nominal" , b2le32(buff, 20));
			id_hash.put("bitrate_maximal" , b2le32(buff, 24));
		}

		return id_hash;
	}

};
