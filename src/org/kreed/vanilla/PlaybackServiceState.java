/*
 * Copyright (C) 2010 Christopher Eby <kreed@kreed.org>
 *
 * This file is part of Vanilla Music Player.
 *
 * Vanilla Music Player is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Vanilla Music Player is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.kreed.vanilla;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import android.content.Context;
import android.util.Log;

public class PlaybackServiceState {
	private static final String STATE_FILE = "state";
	private static final long STATE_FILE_MAGIC = 0x8a9d3f9fca31L;

	public int savedIndex;
	public long[] savedIds;
	public int savedSeek;

	public boolean load(Context context)
	{
		try {
			DataInputStream in = new DataInputStream(context.openFileInput(STATE_FILE));
			if (in.readLong() == STATE_FILE_MAGIC) {
				savedIndex = in.readInt();
				int n = in.readInt();

				if (n > 0) {
					savedIds = new long[n];
					for (int i = 0; i != n; ++i)
						savedIds[i] = in.readLong();
					savedSeek = in.readInt();

					return true;
				}

				in.close();
			}
		} catch (FileNotFoundException e) {
		} catch (IOException e) {
			Log.w("VanillaMusic", e);
		}

		return false;
	}

	public static void saveState(Context context, List<Song> songs, int index, int seek)
	{
		try {
			DataOutputStream out = new DataOutputStream(context.openFileOutput(STATE_FILE, 0));
			out.writeLong(STATE_FILE_MAGIC);
			out.writeInt(index);
			int n = songs == null ? 0 : songs.size();
			out.writeInt(n);
			for (int i = 0; i != n; ++i)
				out.writeLong(songs.get(i).id);
			out.writeInt(seek);
			out.close();
		} catch (IOException e) {
			Log.w("VanillaMusic", e);
		}
	}
}