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

import android.content.Context;

public class ArtistAdapter extends AbstractAdapter {
	public ArtistAdapter(Context context, Song[] allSongs)
	{
		super(context, Song.filter(allSongs, new Song.ArtistComparator()), MediaView.EXPANDER, Song.FIELD_ARTIST);
	}

	@Override
	protected void updateView(int position, MediaView view)
	{
		Song song = get(position);
		view.setPrimaryText(song.artist);
		view.setMediaId(song.artistId);
	}
}