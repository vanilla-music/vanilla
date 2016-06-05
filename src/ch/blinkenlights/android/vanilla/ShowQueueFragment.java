/*
 * Copyright (C) 2016 Adrian Ulrich <adrian@blinkenlights.ch>
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

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import com.mobeta.android.dslv.DragSortListView;


public class ShowQueueFragment extends Fragment
	implements TimelineCallback,
	           AdapterView.OnItemClickListener,
	           DragSortListView.DropListener,
	           DragSortListView.RemoveListener,
	           MenuItem.OnMenuItemClickListener
	{

	private DragSortListView mListView;
	private ShowQueueAdapter mListAdapter;
	private PlaybackService mService;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);

		View view = inflater.inflate(R.layout.showqueue_listview, container, false);
		Context context = getActivity();

		mListView    = (DragSortListView) view.findViewById(R.id.list);
		mListAdapter = new ShowQueueAdapter(context, R.layout.draggable_row);
		mListView.setAdapter(mListAdapter);
		mListView.setDropListener(this);
		mListView.setRemoveListener(this);
		mListView.setOnItemClickListener(this);
		mListView.setOnCreateContextMenuListener(this);

		PlaybackService.addTimelineCallback(this);
		return view;
	}

	@Override
	public void onDestroyView() {
		PlaybackService.removeTimelineCallback(this);
		super.onDestroyView();
	}

	@Override
	public void onResume() {
		super.onResume();

		// Get playback service if we can and must
		// This happens eg. during a rotate where the view
		// was destroyed
		if (mService == null && PlaybackService.hasInstance())
			mService = PlaybackService.get(getActivity());

		if (mService != null)
			refreshSongQueueList(true);
	}


	private final static int CTX_MENU_PLAY           = 100;
	private final static int CTX_MENU_ENQUEUE_ALBUM  = 101;
	private final static int CTX_MENU_ENQUEUE_ARTIST = 102;
	private final static int CTX_MENU_ENQUEUE_GENRE  = 103;
	private final static int CTX_MENU_REMOVE         = 104;

	/**
	 * Called by Android on long press. Builds the long press context menu.
	 */
	@Override
	public void onCreateContextMenu(ContextMenu menu, View listView, ContextMenu.ContextMenuInfo absInfo) {
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)absInfo;
		Song song = mService.getSongByQueuePosition(info.position);

		Intent intent = new Intent();
		intent.putExtra("id", song.id);
		intent.putExtra("type", MediaUtils.TYPE_SONG);
		intent.putExtra("position", info.position);
		menu.setHeaderTitle(song.title);
		menu.add(0, CTX_MENU_PLAY, 0, R.string.play).setIntent(intent).setOnMenuItemClickListener(this);
		menu.add(0, CTX_MENU_ENQUEUE_ALBUM, 0, R.string.enqueue_current_album).setIntent(intent).setOnMenuItemClickListener(this);
		menu.add(0, CTX_MENU_ENQUEUE_ARTIST, 0, R.string.enqueue_current_artist).setIntent(intent).setOnMenuItemClickListener(this);
		menu.add(0, CTX_MENU_ENQUEUE_GENRE, 0, R.string.enqueue_current_genre).setIntent(intent).setOnMenuItemClickListener(this);
		menu.addSubMenu(0, SlidingPlaybackActivity.CTX_MENU_ADD_TO_PLAYLIST, 0, R.string.add_to_playlist).getItem().setIntent(intent); // handled by fragment parent
		menu.add(0, CTX_MENU_REMOVE, 0, R.string.remove).setIntent(intent).setOnMenuItemClickListener(this);
	}

	/**
	 * Called by Android after the User selected a MenuItem.
	 *
	 * @param item The selected menu item.
	 */
	@Override
	public boolean onMenuItemClick(MenuItem item) {
		Intent intent = item.getIntent();
		int itemId = item.getItemId();
		int pos = intent.getIntExtra("position", -1);

		Song song = mService.getSongByQueuePosition(pos);
		switch (item.getItemId()) {
			case CTX_MENU_PLAY:
				onItemClick(null, null, pos, -1);
				break;
			case CTX_MENU_ENQUEUE_ALBUM:
				mService.enqueueFromSong(song, MediaUtils.TYPE_ALBUM);
				break;
			case CTX_MENU_ENQUEUE_ARTIST:
				mService.enqueueFromSong(song, MediaUtils.TYPE_ARTIST);
				break;
			case CTX_MENU_ENQUEUE_GENRE:
				mService.enqueueFromSong(song, MediaUtils.TYPE_GENRE);
				break;
			case CTX_MENU_REMOVE:
				remove(pos);
				break;
			default:
				throw new IllegalArgumentException("Unhandled menu id received!");
				// we could actually dispatch this to the hosting activity, but we do not need this for now.
		}
		return true;
	}


	/**
	 * Fired from adapter listview  if user moved an item
	 * @param from the item index that was dragged
	 * @param to the index where the item was dropped
	 */
	@Override
	public void drop(int from, int to) {
		if (from != to) {
			mService.moveSongPosition(from, to);
		}
	}

	/**
	 * Fired from adapter listview after user removed a song
	 * @param which index to remove from queue
	 */
	@Override
	public void remove(int which) {
		mService.removeSongPosition(which);
	}

	/**
	 * Called when an item in the listview gets clicked
	 */
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
			mService.jumpToQueuePosition(position);
	}

	/**
	 * Triggers a refresh of the queueview
	 * @param scroll enable or disable jumping to the currently playing item
	 */
	public void refreshSongQueueList(final boolean scroll) {
		getActivity().runOnUiThread(new Runnable(){
			public void run() {
				int i, stotal, spos;
				stotal = mService.getTimelineLength();   /* Total number of songs in queue */
				spos   = mService.getTimelinePosition(); /* Current position in queue      */

				mListAdapter.clear();                    /* Flush all existing entries...  */
				mListAdapter.highlightRow(spos);         /* and highlight current position */

				for(i=0 ; i<stotal; i++) {
					mListAdapter.add(mService.getSongByQueuePosition(i));
				}

				if(scroll)
					scrollToCurrentSong(spos);
			}
		});
	}

	/**
	 * Scrolls to the current song<br/>
	 * We suppress the new api lint check as lint thinks
	 * {@link android.widget.AbsListView#setSelectionFromTop(int, int)} was only added in
	 * {@link Build.VERSION_CODES#JELLY_BEAN}, but it was actually added in API
	 * level 1<br/>
	 * <a href="https://developer.android.com/reference/android/widget/AbsListView.html#setSelectionFromTop%28int,%20int%29">
	 *     Android reference: AbsListView.setSelectionFromTop()</a>
	 * @param currentSongPosition The position in {@link #mListView} of the current song
	 */
	@SuppressLint("NewApi")
	private void scrollToCurrentSong(int currentSongPosition){
		mListView.setSelectionFromTop(currentSongPosition, 0); /* scroll to currently playing song */
	}

	/**
	 * Called after a song has been set.
	 * We are only interested in this call if mService is null
	 * as this signals that the playback service just became ready
	 * (and wasn't during onResume())
	 */
	public void setSong(long uptime, Song song) {
		if (mService == null) {
			mService = PlaybackService.get(getActivity());
			onTimelineChanged();
		}
	}

	/**
	 * Called after the timeline changed
	 */
	public void onTimelineChanged() {
		if (mService != null)
			refreshSongQueueList(false);
	}

	// Unused Callbacks of TimelineCallback
	public void onPositionInfoChanged() {
	}
	public void onMediaChange() {
	}
	public void recreate() {
	}
	public void replaceSong(int delta, Song song) {
	}
	public void setState(long uptime, int state) {
	}
}
