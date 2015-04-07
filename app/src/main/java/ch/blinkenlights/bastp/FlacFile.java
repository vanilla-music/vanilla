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
import java.util.Enumeration;


public class FlacFile extends Common {
	private static final int FLAC_TYPE_STREAMINFO = 0; // Basic info about the stream
	private static final int FLAC_TYPE_COMMENT = 4;   // ID of 'VorbisComment's
	
	public FlacFile() {
	}
	
	public HashMap getTags(RandomAccessFile s) throws IOException {
		int xoff  = 4;  // skip file magic
		int retry = 64;
		int r[];
		boolean need_infos = true;
		boolean need_tags = true;
		HashMap infos = new HashMap();
		HashMap tags = new HashMap();
		
		for(; retry > 0; retry--) {
			r = parse_metadata_block(s, xoff);
			if(r[2] == FLAC_TYPE_STREAMINFO) {
				infos = parse_streaminfo_block(s, xoff+r[0], r[1]);
				need_infos = false;
			}
			if(r[2] == FLAC_TYPE_COMMENT) {
				tags = parse_vorbis_comment(s, xoff+r[0], r[1]);
				need_tags = false;
			}
			
			if(r[3] != 0 || (need_tags == false && need_infos == false))
				break; // eof reached
			
			// else: calculate next offset
			xoff += r[0] + r[1];
		}

		// Copy duration to final hashmap if found in infoblock
		if(infos.containsKey("duration")) {
			tags.put("duration", infos.get("duration"));
		}

		return tags;
	}
	
	/* Parses the metadata block at 'offset' and returns
	** [header_size, payload_size, type, stop_after]
	*/
	private int[] parse_metadata_block(RandomAccessFile s, long offset) throws IOException {
		int[] result   = new int[4];
		byte[] mb_head = new byte[4];
		int stop_after = 0;
		int block_type = 0;
		int block_size = 0;
		
		s.seek(offset);
		
		if( s.read(mb_head) != 4 )
			xdie("failed to read metadata block header");
		
		block_size = b2be32(mb_head,0);                         // read whole header as 32 big endian
		block_type = (block_size >> 24) & 127;                  // BIT 1-7 are the type
		stop_after = (((block_size >> 24) & 128) > 0 ? 1 : 0 ); // BIT 0 indicates the last-block flag
		block_size = (block_size & 0x00FFFFFF);                 // byte 1-7 are the size
		
		// debug("size="+block_size+", type="+block_type+", is_last="+stop_after);
		
		result[0] = 4; // hardcoded - only returned to be consistent with OGG parser
		result[1] = block_size;
		result[2] = block_type;
		result[3] = stop_after;
		
		return result;
	}

	/*
	 ** Returns a hashma with parsed vorbis identification header data
	 **/
	private HashMap parse_streaminfo_block(RandomAccessFile s, long offset, long pl_len) throws IOException {
		HashMap id_hash = new HashMap();
		byte[] buff = new byte[18];

		if(pl_len >= buff.length) {
			s.seek(offset);
			s.read(buff);
			id_hash.put("blocksize_minimal", (b2be32(buff, 0)  >> 16));
			id_hash.put("blocksize_maximal", (b2be32(buff, 0)  & 0x0000FFFF));
			id_hash.put("framesize_minimal", (b2be32(buff, 4)  >> 8));
			id_hash.put("framesize_maximal", (b2be32(buff, 7)  >> 8));
			id_hash.put("sampling_rate",     (b2be32(buff, 10) >> 12));
			id_hash.put("channels",          ((b2be32(buff, 10) >> 9) & 7) + 1); // 3 bits
			id_hash.put("num_samples",       b2be32(buff, 14)); // fixme: this is actually 36 bit: the 4 hi bits are discarded due to java
			if((Integer)id_hash.get("sampling_rate") > 0) {
				int duration = (Integer)id_hash.get("num_samples") / (Integer)id_hash.get("sampling_rate");
				id_hash.put("duration", (int)duration);
			}
		}
		return id_hash;
	}

}
