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
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class ArtistAdapter extends AbstractAdapter {
	public ArtistAdapter(Context context, Song[] allSongs)
	{
		super(context, Song.filter(allSongs, new Song.ArtistComparator()));
	}

	@Override
	public int getAllowedFields()
	{
		return 1;
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