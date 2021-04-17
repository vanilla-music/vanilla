/*
 * Copyright (C) 2016-2018 Adrian Ulrich <adrian@blinkenlights.ch>
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

import ch.blinkenlights.android.vanilla.ui.FancyMenu;
import ch.blinkenlights.android.vanilla.ui.FancyMenuItem;
import ch.blinkenlights.android.vanilla.ext.CoordClickListener;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import com.mobeta.android.dslv.DragSortListView;


public class ShowQueueFragment extends Fragment
	implements TimelineCallback,
			   AdapterView.OnItemClickListener,
			   CoordClickListener.Callback,
			   DragSortListView.DropListener,
			   DragSortListView.RemoveListener,
			   FancyMenu.Callback
	{

	private DragSortListView mListView;
	private ShowQueueAdapter mListAdapter;
	private boolean mIsPopulated;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		super.onCreateView(inflater, container, savedInstanceState);

		View view = inflater.inflate(R.layout.showqueue_listview, container, false);
		Context context = getActivity();

		mListAdapter = new ShowQueueAdapter(context, R.layout.draggable_row);
		mListView = (DragSortListView) view.findViewById(R.id.list);
		mListView.setAdapter(mListAdapter);
		mListView.setDropListener(this);
		mListView.setRemoveListener(this);
		mListView.setOnItemClickListener(this);

		CoordClickListener ccl = new CoordClickListener(this);
		ccl.registerForOnItemLongClickListener(mListView);

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

		// Update the song list if we are not populated and
		// have an usable PlaybackService instance.
		// This is the case if the Application already fully
		// started up, but just lost this view (due to rotation).
		if (!mIsPopulated && PlaybackService.hasInstance()) {
			refreshSongQueueList(true);
		}
	}


	private final static int CTX_MENU_PLAY            = 100;
	private final static int CTX_MENU_ENQUEUE_ALBUM   = 101;
	private final static int CTX_MENU_ENQUEUE_ARTIST  = 102;
	private final static int CTX_MENU_ENQUEUE_GENRE   = 103;
	private final static int CTX_MENU_REMOVE          = 104;
	private final static int CTX_MENU_SHOW_DETAILS    = 105;
	private final static int CTX_MENU_ADD_TO_PLAYLIST = 106;
	private final static int CTX_MENU_MOVE_TO_TOP     = 107;
	private final static int CTX_MENU_MOVE_TO_BOTTOM  = 108;

	/**
	 * Called on long-click on a adapeter row
	 */
	@Override
	public boolean onItemLongClickWithCoords(AdapterView<?> parent, View view, int pos, long id, float x, float y) {
		Song song = playbackService().getSongByQueuePosition(pos);

		Intent intent = new Intent();
		intent.putExtra("id", song.id);
		intent.putExtra("type", MediaUtils.TYPE_SONG);
		intent.putExtra("position", pos);

		FancyMenu fm = new FancyMenu(getActivity(), this);
		fm.setHeaderTitle(song.title);

		fm.add(CTX_MENU_PLAY, 0, R.drawable.menu_play, R.string.play).setIntent(intent);

		fm.addSpacer(0);
		fm.add(CTX_MENU_ENQUEUE_ALBUM, 0, R.drawable.menu_enqueue, R.string.enqueue_current_album).setIntent(intent);
		fm.add(CTX_MENU_ENQUEUE_ARTIST, 0, R.drawable.menu_enqueue, R.string.enqueue_current_artist).setIntent(intent);
		fm.add(CTX_MENU_ENQUEUE_GENRE, 0, R.drawable.menu_enqueue, R.string.enqueue_current_genre).setIntent(intent);
		fm.add(CTX_MENU_ADD_TO_PLAYLIST, 0, R.drawable.menu_add_to_playlist, R.string.add_to_playlist).setIntent(intent);
		fm.addSpacer(0);
		fm.add(CTX_MENU_MOVE_TO_TOP, 0, R.drawable.menu_move_to_top, R.string.move_to_top).setIntent(intent);
		fm.add(CTX_MENU_MOVE_TO_BOTTOM, 0, R.drawable.menu_move_to_bottom, R.string.move_to_bottom).setIntent(intent);

		fm.addSpacer(0);
		fm.add(CTX_MENU_SHOW_DETAILS, 0, R.drawable.menu_details, R.string.details).setIntent(intent);
		fm.add(CTX_MENU_REMOVE, 90, R.drawable.menu_remove, R.string.remove).setIntent(intent);

		fm.show(view, x, y);
		return true;
	}

	/**
	 * Callback for FancyMenu clicks.
	 *
	 * @param item The selected menu item.
	 */
	@Override
	public boolean onFancyItemSelected(FancyMenuItem item) {
		Intent intent = item.getIntent();
		int pos = intent.getIntExtra("position", -1);

		PlaybackService service = playbackService();
		Song song = service.getSongByQueuePosition(pos);
		switch (item.getItemId()) {
			case CTX_MENU_PLAY:
				onItemClick(null, null, pos, -1);
				break;
			case CTX_MENU_ENQUEUE_ALBUM:
				service.enqueueFromSong(song, MediaUtils.TYPE_ALBUM);
				break;
			case CTX_MENU_ENQUEUE_ARTIST:
				service.enqueueFromSong(song, MediaUtils.TYPE_ARTIST);
				break;
			case CTX_MENU_ENQUEUE_GENRE:
				service.enqueueFromSong(song, MediaUtils.TYPE_GENRE);
				break;
			case CTX_MENU_SHOW_DETAILS:
				TrackDetailsDialog.show(getFragmentManager(), song.id);
				break;
			case CTX_MENU_REMOVE:
				remove(pos);
				break;
		    case CTX_MENU_ADD_TO_PLAYLIST:
				PlaylistDialog.Callback callback = ((PlaylistDialog.Callback)getActivity());
				PlaylistDialog dialog = PlaylistDialog.newInstance(callback, intent, null);
				dialog.show(getFragmentManager(), "PlaylistDialog");
				break;
			case CTX_MENU_MOVE_TO_TOP:
				service.moveSongPosition(pos, 0);
				break;
			case CTX_MENU_MOVE_TO_BOTTOM:
				service.moveSongPosition(pos, service.getTimelineLength() - 1);
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
			playbackService().moveSongPosition(from, to);
		}
	}

	/**
	 * Fired from adapter listview after user removed a song
	 * @param which index to remove from queue
	 */
	@Override
	public void remove(int which) {
		playbackService().removeSongPosition(which);
	}

	/**
	 * Called when an item in the listview gets clicked
	 */
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		playbackService().jumpToQueuePosition(position);
	}

	/**
	 * Triggers a refresh of the queueview
	 * @param scroll enable or disable jumping to the currently playing item
	 */
	private void refreshSongQueueList(final boolean scroll) {
		final PlaybackService service = playbackService();
		final int pos = service.getTimelinePosition();
		getActivity().runOnUiThread(new Runnable(){
			public void run() {
				mListAdapter.setData(service, pos);

				if(scroll) {
					// check that we really need to jump to this song, i.e. it is not visible in list right now
					int min = mListView.getFirstVisiblePosition();
					int max = mListView.getLastVisiblePosition();
					if (pos < min || pos > max) // it's out of visible range, scroll
						scrollToCurrentSong(pos);
				}
			}
		});
		mIsPopulated = true;
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
	 * Shortcut function to get the current playback service
	 * using our parent activity as context
	 */
	private PlaybackService playbackService() {
		return PlaybackService.get(getActivity());
	}

	/**
	 * Called after a song has been set.
	 *
	 * We are generally not interested in such events as we do not display
	 * any playback state - only the queue (which has its changes announced
	 * using `onTimelineChanged()'.
	 * However: We are still interested in `setSong()' if we are unpopulated:
	 * Such an event will then indicate that the PlaybackService just finished
	 * its startup and is ready to be queried.
	 */
	public void setSong(long uptime, Song song) {
		if (PlaybackService.hasInstance()) {
			boolean scroll = SharedPrefHelper
				.getSettings(getActivity().getApplicationContext())
				.getBoolean(PrefKeys.QUEUE_ENABLE_SCROLL_TO_SONG,
							PrefDefaults.QUEUE_ENABLE_SCROLL_TO_SONG);

			if (!mIsPopulated || scroll) {
				refreshSongQueueList(scroll);
			}
		}
	}

	/**
	 * Called after the timeline changed
	 */
	public void onTimelineChanged() {
		if (PlaybackService.hasInstance()) {
			refreshSongQueueList(false);
		}
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
