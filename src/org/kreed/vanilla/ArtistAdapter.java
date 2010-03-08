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
import java.util.HashMap;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class ArtistAdapter extends AbstractAdapter {
	private static Song[] filter(Song[] songs)
	{
		HashMap<Integer, Song> artists = new HashMap<Integer, Song>();
		for (int i = songs.length; --i != -1; ) {
			Song song = songs[i];
			if (!artists.containsKey(song.artistId))
				artists.put(song.artistId, song);
		}
		Song[] result = artists.values().toArray(new Song[0]);
		Arrays.sort(result, new Song.ArtistComparator());
		return result;
	}

	public ArtistAdapter(Context context, Song[] allSongs)
	{
		super(context, filter(allSongs));
	}

	public View getView(int position, View convertView, ViewGroup parent)
	{
		TextView view = null;
		try {
			view = (TextView)convertView;
		} catch (ClassCastException e) {	
		}

		if (view == null) {
			view = new TextView(mContext);
			view.setPadding(mPadding, mPadding, mPadding, mPadding);
			view.setSingleLine();
			view.setTextColor(Color.WHITE);
			view.setTextSize(mSize);
		}

		view.setText(get(position).artist);
		return view;
	}
}