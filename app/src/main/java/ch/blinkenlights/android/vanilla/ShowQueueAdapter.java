/*
 * Copyright (C) 2013-2019 Adrian Ulrich <adrian@blinkenlights.ch>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>. 
 */

package ch.blinkenlights.android.vanilla;

import android.content.Context;
import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.view.LayoutInflater;

import java.util.HashSet;
import java.util.Set;

public class ShowQueueAdapter extends BaseAdapter {
	/**
	 * The resource to pass to the inflater
	 */
	private int mResource;
	/**
	 * The position we are going to mark as 'active'
	 */
	private int mHighlightRow;
	/**
	 * The cached number of songs
	 */
	private int mSongCount;
	/**
	 * The context to use
	 */
	private Context mContext;
	/**
	 * The playback service reference to query
	 */
	private PlaybackService mService;
	/**
	 * List of songs selected in multi-selection mode
	 */
	private Set<Integer> mSelected;


	public ShowQueueAdapter(Context context, int resource) {
		super();
		mResource = resource;
		mContext = context;
		mHighlightRow = -1;
		mSelected = new HashSet<>();
	}

	/**
	* Configures our data source
	*
	* @param service the playback service instance to use
	* @param pos the row to highlight, setting this to -1 disables the feature
	*/
	public void setData(PlaybackService service, int pos) {
		mService = service;
		mHighlightRow = pos;
		mSongCount = service.getTimelineLength();
		notifyDataSetChanged();
	}

	/**
	 * Returns the number of songs in this adapter
	 *
	 * @return the number of songs in this adapter
	 */
	@Override
	public int getCount() {
		// Note: This is only updated by setData() to avoid races with the listView if
		//       the timeline changes: The listView checks if getCount() changed without
		//       a call to notifyDataSetChanged() and panics if it detected such a condition.
		//       This can happen to us as onLayout() might get called before setData() was called during
		//       a queue update. So we simply cache the count to avoid this crash and won't update it until
		//       setData() is called by our parent.
		return mSongCount;
	}

	/**
	 * Returns the song at given position
	 *
	 * @param pos the position to query
	 * @return a Song object
	 */
	@Override
	public Song getItem(int pos) {
		Song item = mService.getSongByQueuePosition(pos);
		return (item != null ? item : new Song(-1));
	}

	/**
	 * Returns the item id at `pos'
	 *
	 * @param pos the position to query
	 * @return the song.id at this position
	 */
	@Override
	public long getItemId(int pos) {
		return getItem(pos).id;
	}

	/**
	 * Returns always `true' as song.id's are stable
	 */
	@Override
	public boolean hasStableIds() {
		return true;
	}

	/**
	 * Change selection status of song at 'pos' and maintain a set of selected positions
	 *
	 * @param pos the position to toggle
	 * @return the previous selection status
	 */
	public boolean toggleSelectedAt(int pos) {
		Song song = getItem(pos);
		boolean isSelected = song.isSelected();

		if (isSelected) {
			mSelected.remove(pos);
		} else {
			mSelected.add(pos);
		}

		song.setSelected(!isSelected);
		notifyDataSetChanged();
		return isSelected;
	}
	/**
	 * Get the set of positions of selected songs
	 *
	 * @return a set of positions
	 */
	public Set<Integer> getSelections() {
		return new HashSet<>(mSelected);
	}

	/**
	 * Clear the selection status of all positions and empty selection set
	 *
	 */
	public void clearSelections() {
		for (int i: mSelected) {
			getItem(i).setSelected(false);
		}

		mSelected.clear();
		notifyDataSetChanged();
	}

	/**
	 * Returns the view
	 */
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		DraggableRow row;

		if (convertView != null) {
			row = (DraggableRow)convertView;
		} else {
			LayoutInflater inflater = ((Activity)mContext).getLayoutInflater();
			row = (DraggableRow)inflater.inflate(mResource, parent, false);
			row.setupLayout(DraggableRow.LAYOUT_DRAGGABLE);
		}

		Song song = getItem(position);

		if (song.isFilled()) {
			row.setText(song.title, song.album+" · "+song.artist);
			row.setDuration(song.duration);
			row.getCoverView().setCover(MediaUtils.TYPE_ALBUM, song.albumId, null);
		}

		row.highlightRow(position == mHighlightRow);

		if (song.selected) {
			row.setBackgroundColor(convertView.getResources().getColor(R.color.material_grey_600));
		} else {
			row.setBackgroundColor(R.styleable.DragSortListView_float_background_color);
		}

		return row;
	}

}
