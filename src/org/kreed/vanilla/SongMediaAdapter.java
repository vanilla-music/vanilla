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
import android.provider.MediaStore;

public class SongMediaAdapter extends MediaAdapter {
	public SongMediaAdapter(Context context)
	{
		super(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, SONG_FIELDS, SONG_FIELD_KEYS, false);
	}

	@Override
	protected String getDefaultSelection()
	{
		return MediaStore.Audio.Media.IS_MUSIC + "!=0";
	}

	@Override
	protected String getSortOrder()
	{
		if (getLimiter() != null && getLimiter().length == 2)
			return MediaStore.Audio.Media.TRACK;
		return super.getSortOrder();
	}
}
