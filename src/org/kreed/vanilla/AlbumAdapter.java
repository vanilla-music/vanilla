package org.kreed.vanilla;

import android.content.Context;

public class AlbumAdapter extends AbstractAdapter {
	public AlbumAdapter(Context context, Song[] allSongs)
	{
		super(context, Song.filter(allSongs, new Song.AlbumComparator()), MediaView.EXPANDER | MediaView.SECONDARY_LINE, Song.FIELD_ALBUM);
	}

	@Override
	protected void updateView(int position, MediaView view)
	{
		Song song = get(position);
		view.setPrimaryText(song.album);
		view.setSecondaryText(song.artist);
		view.setMediaId(song.albumId);
	}
}