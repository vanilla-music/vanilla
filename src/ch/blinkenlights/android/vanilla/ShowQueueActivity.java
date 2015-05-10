/*
 * Copyright (C) 2013-2014 Adrian Ulrich <adrian@blinkenlights.ch>
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

import java.util.ArrayList;

import android.app.Activity;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import com.mobeta.android.dslv.DragSortListView;

public class ShowQueueActivity extends PlaybackActivity
	implements DialogInterface.OnDismissListener
	{
	private DragSortListView mListView;
	private ShowQueueAdapter listAdapter;
	private PlaybackService mService;

	@Override  
	public void onCreate(Bundle savedInstanceState) {
		ThemeHelper.setTheme(this, R.style.BackActionBar);
		super.onCreate(savedInstanceState);

		setTitle(R.string.queue);
		setContentView(R.layout.showqueue_listview);

		mService    = PlaybackService.get(this);
		mListView   = (DragSortListView) findViewById(R.id.list);
		listAdapter = new ShowQueueAdapter(this, R.layout.draggable_row);
		mListView.setAdapter(listAdapter);
		mListView.setDropListener(onDrop);
		mListView.setRemoveListener(onRemove);

		mListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				mService.jumpToQueuePosition(position);
				finish();
			}});
		mListView.setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				mService.jumpToQueuePosition(position);
				return true;
			}});

	}

	/**
	 * Inflate the ActionBar menu
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, MENU_CLEAR_QUEUE, 0, R.string.dequeue_rest).setIcon(R.drawable.ic_menu_close_clear_cancel);
		menu.add(0, MENU_EMPTY_QUEUE, 0, R.string.empty_the_queue);
		menu.add(0, MENU_SAVE_AS_PLAYLIST, 0, R.string.save_as_playlist).setIcon(R.drawable.ic_menu_preferences);
		return true;
	}

	/**
	 *  Called after the user selected an action from the ActionBar
	 * 
	 * @param item The selected menu item
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				finish();
				break;
			case MENU_SAVE_AS_PLAYLIST:
				NewPlaylistDialog dialog = new NewPlaylistDialog(this, null, R.string.create, null);
				dialog.setOnDismissListener(this);
				dialog.show();
				break;
			case MENU_EMPTY_QUEUE:
				PlaybackService.get(this).emptyQueue();
				finish();
				break;
			default:
				return super.onOptionsItemSelected(item);
		}
		return true;
	}

	/*
	** Called when we are displayed (again)
	** This will always refresh the whole song list
	*/
	@Override
	public void onResume() {
		super.onResume();
		refreshSongQueueList(true);
	}

	/**
	 * Fired from adapter listview  if user moved an item
	 * @param from the item index that was dragged
	 * @param to the index where the item was dropped
	 */
	private DragSortListView.DropListener onDrop =
		new DragSortListView.DropListener() {
			@Override
			public void drop(int from, int to) {
				if (from != to) {
					mService.moveSongPosition(from, to);
				}
			}
		};

	/**
	 * Fired from adapter listview after user removed a song
	 * @param which index to remove from queue
	 */
	private DragSortListView.RemoveListener onRemove =
		new DragSortListView.RemoveListener() {
			@Override
			public void remove(int which) {
				mService.removeSongPosition(which);
			}
		};

	/**
	 * Fired if user dismisses the create-playlist dialog
	 *
	 * @param dialogInterface the dismissed interface dialog
	 */
	@Override
	public void onDismiss(DialogInterface dialogInterface) {
		NewPlaylistDialog dialog = (NewPlaylistDialog)dialogInterface;
		if (dialog.isAccepted()) {
			String playlistName = dialog.getText();
			long playlistId = Playlist.createPlaylist(getContentResolver(), playlistName);
			PlaylistTask playlistTask = new PlaylistTask(playlistId, playlistName);
			playlistTask.audioIds = new ArrayList<Long>();

			Song song;
			for (int i=0; ; i++) {
				song = mService.getSongByQueuePosition(i);
				if (song == null)
					break;
				playlistTask.audioIds.add(song.id);
			}
			mHandler.sendMessage(mHandler.obtainMessage(MSG_ADD_TO_PLAYLIST, playlistTask));
		}
	}

	/**
	 * Called when the song timeline has changed
	 */
	public void onTimelineChanged() {
		refreshSongQueueList(false);
	}

	/**
	 * Triggers a refresh of the queueview
	 * @param scroll enable or disable jumping to the currently playing item
	 */
	public void refreshSongQueueList(final boolean scroll) {
		runOnUiThread(new Runnable(){
			public void run() {
				int i, stotal, spos;
				stotal = mService.getTimelineLength();   /* Total number of songs in queue */
				spos   = mService.getTimelinePosition(); /* Current position in queue      */

				listAdapter.clear();                    /* Flush all existing entries...  */
				listAdapter.highlightRow(spos);         /* and highlight current position */

				for(i=0 ; i<stotal; i++) {
					listAdapter.add(mService.getSongByQueuePosition(i));
				}

				if (scroll == true)
					mListView.setSelectionFromTop(spos, 0); /* scroll to currently playing song */
			}
		});
	}
	
	
	
}
