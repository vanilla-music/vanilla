package org.kreed.vanilla;

import java.util.Arrays;
import java.util.HashMap;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AlbumAdapter extends AbstractAdapter {
	private static Song[] filter(Song[] songs)
	{
		HashMap<Integer, Song> albums = new HashMap<Integer, Song>();
		for (int i = songs.length; --i != -1; ) {
			Song song = songs[i];
			if (!albums.containsKey(song.albumId))
				albums.put(song.albumId, song);
		}
		Song[] result = albums.values().toArray(new Song[0]);
		Arrays.sort(result, new Song.AlbumComparator());
		return result;
	}

	public AlbumAdapter(Context context, Song[] allSongs)
	{
		super(context, filter(allSongs));
	}

	public View getView(int position, View convertView, ViewGroup parent)
	{
		LinearLayout view = null;
		try {
			view = (LinearLayout)convertView;
		} catch (ClassCastException e) {	
		}

		if (view == null) {
			view = new LinearLayout(mContext);
			view.setOrientation(LinearLayout.VERTICAL);
			view.setPadding(mPadding, mPadding, mPadding, mPadding);

			TextView title = new TextView(mContext);
			title.setSingleLine();
			title.setTextColor(Color.WHITE);
			title.setTextSize(mSize);
			title.setId(0);
			view.addView(title);

			TextView artist = new TextView(mContext);
			artist.setSingleLine();
			artist.setTextSize(mSize);
			artist.setId(1);
			view.addView(artist);
		}

		Song song = get(position);
		((TextView)view.findViewById(0)).setText(song.album);
		((TextView)view.findViewById(1)).setText(song.artist);
		return view;
	}
}