/*
 * Copyright (C) 2013 Adrian Ulrich <adrian@blinkenlights.ch>
 * Copyright (C) 2017 Google Inc.
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Enumeration;


public class LameHeader extends Common {

	// Sampling rate version -> field mapping
	private static int[][] sampleRates = {
		{ 11025, 12000,  8000 }, // MPEG2.5 (idx = 0)
		{     0,     0,     0 }, // reserved (idx = 1)
		{ 22050, 24000, 16000 }, // MPEG2 (idx = 2)
		{ 44100, 48000, 32000 }, // MPEG1 (idx = 3)
	};

	// SamplesPerFrame layer -> version mapping
	private static int[][] samplesPerFrame = {
		// reserved, layer3, layer2, layer1
		{  0,        576,   1152,  384 }, // MPEG2.5
		{  0,          0,      0,    0 }, // RESERVED
		{  0,        576,   1152,  384 }, // MPEG2
		{  0,       1152,   1152,  384 }, // MPEG1
	};


	public LameHeader() {
	}
	
	public HashMap getTags(RandomAccessFile s) throws IOException {
		HashMap rgain = parseLameHeader(s, 0);
		HashMap tags = parseV1Header(s, s.length()-128);

		// Add replay gain info to returned object if available
		for (String k : Arrays.asList("REPLAYGAIN_TRACK_GAIN", "REPLAYGAIN_ALBUM_GAIN")) {
			if (rgain.containsKey(k))
				tags.put(k, rgain.get(k));
		}

		return tags;
	}

	/**
	 * Attempts to parse ID3v1(.1) information from given RandomAccessFile
	 *
	 * @param s the seekable RandomAccessFile
	 * @param offset position of the ID3v1 tag
	 */
	private HashMap parseV1Header(RandomAccessFile s, long offset) throws IOException {
		HashMap tags = new HashMap();
		byte[] tag  = new byte[3];
		byte[] year = new byte[4];
		byte[] str  = new byte[30];

		s.seek(offset);
		s.read(tag);

		if("TAG".equals(new String(tag))) {
			for (String name : Arrays.asList("TITLE", "ARTIST", "ALBUM")) {
				s.read(str);
				String value = new String(str, "ISO-8859-1").trim();
				if (value.length() > 0)
					addTagEntry(tags, name, value);
			}

			// year is a string for whatever reason...
			s.read(year);
			String y = new String(year).trim();
			if (y.length() > 0)
				addTagEntry(tags, "YEAR", y);

			s.skipBytes(28); // skip comment field
			s.read(tag);

			if (tag[0] == 0 && tag[1] != 0) // tag[0] == 0 -> is id3v1.1 compatible
				addTagEntry(tags, "TRACKNUMBER", String.format("%d", tag[1]));

			if (tag[2] != 0)
				addTagEntry(tags, "GENRE", String.format("%d", tag[2]));
		}


		return tags;
	}

	public HashMap parseLameHeader(RandomAccessFile s, long offset) throws IOException {
		HashMap tags = new HashMap();
		byte[] chunk = new byte[12];
		
		s.seek(offset + 0x24);
		s.read(chunk);
		
		String lameMark = new String(chunk, 0, 4, "ISO-8859-1");
		int flags = b2u(chunk[7]);

		if((flags & 0x01) !=0 ) { // header indicates that totalFrames field is present
			int total_frames = b2be32(chunk, 8);
			s.seek(offset);
			s.read(chunk);

			int mpeg_hdr = b2be32(chunk, 0);
			int srate_idx = (mpeg_hdr >> 10) & 3; // sampling rate index at bit 10-11
			int layer_idx = (mpeg_hdr >> 17) & 3; // layer index value bit 17-18
			int ver_idx   = (mpeg_hdr >> 19) & 3; // version index value bit 19-20

			// Try to calculate song duration if all indexes are sane
			if (ver_idx < sampleRates.length && srate_idx < sampleRates[ver_idx].length && layer_idx < samplesPerFrame[ver_idx].length) {
				int sample_rate = sampleRates[ver_idx][srate_idx];
				int sample_pfr  = samplesPerFrame[ver_idx][layer_idx];
				if (sample_rate > 0 && sample_pfr > 0) {
					double duration = ((double)sample_pfr / (double)sample_rate) * total_frames;
					tags.put("duration", (int)duration);
				}
			}

		}

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
