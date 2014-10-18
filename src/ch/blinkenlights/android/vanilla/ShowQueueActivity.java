/*
 * Copyright (C) 2013 Adrian Ulrich <adrian@blinkenlights.ch>
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
import java.util.Arrays;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import com.mobeta.android.dslv.DragSortListView;

public class ShowQueueActivity extends Activity {
	private DragSortListView mListView;
	private ShowQueueAdapter listAdapter;
	private PlaybackService mService;

	@Override  
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setTitle(R.string.queue);
		setContentView(R.layout.showqueue_listview);
		
		mService    = PlaybackService.get(this);
		mListView   = (DragSortListView) findViewById(R.id.list);
		listAdapter = new ShowQueueAdapter(this, R.layout.draggable_row);
		mListView.setAdapter(listAdapter);
		mListView.setFastScrollAlwaysVisible(true);
		mListView.setDropListener(onDrop);

		PlaybackService.addActivity(this);

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
	
	/*
	** Called when the user hits the ActionBar item
	** There is only one item (title) and it should quit this activity
	*/
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		finish();
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

	@Override
	public void onDestroy() {
		PlaybackService.removeActivity(this);
		super.onDestroy();
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
