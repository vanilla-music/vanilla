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

import java.util.Arrays;

import android.content.Context;
import android.widget.TextView;

public class SongAdapter extends AbstractAdapter {
	private static Song[] sort(Song[] songs)
	{
		Arrays.sort(songs, new Song.TitleComparator());
		return songs;
	}

	public SongAdapter(Context context, Song[] allSongs)
	{
		super(ContextApplication.getContext(), sort(allSongs), 0, 3);
	}

	@Override
	protected void updateText(int position, TextView upper, TextView lower)
	{
		Song song = get(position);
		upper.setText(song.title);
		lower.setText(song.artist);
	}

	public long getItemId(int i)
	{
		Song song = get(i);
		if (song == null)
			return 0;
		return song.id;
	}
}