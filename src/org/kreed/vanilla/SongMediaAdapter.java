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

/**
 * Subclasses MediaAdapter to represent songs. Providers some song-specific
 * logic over plain MediaAdapter: exclusion of non-music media files and
 * better sorting inside albums.
 */
public class SongMediaAdapter extends MediaAdapter {
	/**
	 * Construct a MediaAdapter backed by MediaStore.Audio.Media.
	 *
	 * @param context A Context to use
	 * @param expandable Whether an expander arrow should by shown to the right
	 * of views
	 * @param requery If true, automatically update the adapter when the
	 * provider changes
	 */
	public SongMediaAdapter(Context context, boolean expandable, boolean requery)
	{
		super(context,
		      MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
		      new String[] { MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.TITLE },
		      new String[] { MediaStore.Audio.Media.ARTIST_KEY, MediaStore.Audio.Media.ALBUM_KEY, MediaStore.Audio.Media.TITLE_KEY },
		      expandable, requery);
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
