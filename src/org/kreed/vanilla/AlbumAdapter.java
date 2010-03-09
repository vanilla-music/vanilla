package org.kreed.vanilla;

import android.content.Context;
import android.widget.TextView;

public class AlbumAdapter extends AbstractAdapter {
	public AlbumAdapter(Context context, Song[] allSongs)
	{
		super(context, Song.filter(allSongs, new Song.AlbumComparator()), 0, 2);
	}

	@Override
	protected void updateText(int position, TextView upper, TextView lower)
	{
		Song song = get(position);
		upper.setText(song.album);
		lower.setText(song.artist);
	}
}