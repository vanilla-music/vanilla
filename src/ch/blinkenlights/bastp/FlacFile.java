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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Hashtable;
import java.util.Enumeration;


public class FlacFile extends Common {
	private static final int FLAC_TYPE_COMMENT = 4;   // ID of 'VorbisComment's
	
	public FlacFile() {
	}
	
	public Hashtable getTags(RandomAccessFile s) throws IOException {
		int xoff  = 4;  // skip file magic
		int retry = 64;
		int r[];
		Hashtable tags = new Hashtable();
		
		for(; retry > 0; retry--) {
			r = parse_metadata_block(s, xoff);
			
			if(r[2] == FLAC_TYPE_COMMENT) {
				tags = parse_vorbis_comment(s, xoff+r[0], r[1]);
				break;
			}
			
			if(r[3] != 0)
				break; // eof reached
			
			// else: calculate next offset
			xoff += r[0] + r[1];
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
	
}
