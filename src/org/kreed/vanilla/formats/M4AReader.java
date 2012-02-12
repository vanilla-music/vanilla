/*
 * Copyright (C) 2012 Christopher Eby <kreed@kreed.org>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.kreed.vanilla.formats;

import java.io.DataInputStream;
import java.io.IOException;
import org.kreed.vanilla.Song;

/**
 * Parser for iTunes-style metadata in MP4 files.
 */
public class M4AReader {
	// atom names
	private static final int MOOV = 0x6D6F6F76;
	private static final int UDTA = 0x75647461;
	private static final int META = 0x6D657461;
	private static final int ILST = 0x696C7374;
	private static final int DASH = 0x2D2D2D2D;
	private static final int NAME = 0x6E616D65;
	private static final int DATA = 0x64617461;

	/**
	 * Read metadata from the given input stream and store the results in the
	 * given Song. Currently only parses ReplayGain tags.
	 *
	 * @param song Song to store results in.
	 * @param in A DataInputStream initialized to the beginning of the given
	 * song's data.
	 */
	public static void read(Song song, DataInputStream in) throws IOException
	{
		while (readAtom(in, song) > 0) {}
	}

	/**
	 * Read an entire atom, reading children recursively if we are interested
	 * in the atom.
	 *
	 * @param song Song to store results in.
	 * @param in A DataInputStream initialized to the beginning of an atom.
	 * @return The number of bytes read, or -1 if if parent atoms should abort
	 * reading.
	 */
	private static int readAtom(DataInputStream in, Song song) throws IOException
	{
		int size = in.readInt();
		int atom = in.readInt();
		int remaining = size - 8;

		switch (atom) {
		case META:
			in.skipBytes(4); // 4 bytes of padding or something
			remaining -= 4;
			// fall though
		case MOOV:
		case UDTA:
		case ILST: {
			while (remaining > 0) {
				int read = readAtom(in, song);
				if (read == -1) // we can stop reading
					return -1;
				remaining -= read;
			}
			break;
		}
		case DASH:
			remaining = readFreeform(in, song, remaining);
			break;
		}

		in.skipBytes(remaining);
		if (atom == ILST)
			return -1; // we have the tags; we can stop
		return size;
	}

	/**
	 * Read a freeform (----) atom. Such atoms contain three child atoms: mean,
	 * name, and data. mean usually contains com.apple.iTunes, so we just
	 * ignore that.
	 *
	 * @param in A DataInputStream initialized to just after the header for a
	 * ---- atom.
	 * @param song Song to store results in.
	 * @param remaining The remaining bytes to read in the atom. Should be
	 * atom_size - 8
	 * @return The number of bytes from the atom that were not read.
	 */
	private static int readFreeform(DataInputStream in, Song song, int remaining) throws IOException
	{
		String name = null;
		String data = null;

		while (remaining > 8) {
			int size = in.readInt();
			int atom = in.readInt();
			remaining -= size;

			if (atom == NAME && size > 12) {
				in.skipBytes(4);
				byte[] nameBuffer = new byte[size - 12];
				in.readFully(nameBuffer);
				name = new String(nameBuffer);
			} else if (atom == DATA && size > 16) {
				in.skipBytes(8);
				byte[] dataBuffer = new byte[size - 16];
				in.readFully(dataBuffer);
				data = new String(dataBuffer);
			} else {
				in.skipBytes(size - 8);
			}
		}

		if (data != null) {
			if ("replaygain_track_gain".equals(name)) {
				song.trackGain = Song.parseMillibels(data);
			} else if ("replaygain_album_gain".equals(name)) {
				song.albumGain = Song.parseMillibels(data);
			}
		}

		return remaining;
	}
}
