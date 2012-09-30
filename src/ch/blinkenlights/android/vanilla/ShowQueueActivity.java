/*
 * Copyright (C) 2012 Adrian Ulrich <adrian@blinkenlights.ch>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package ch.blinkenlights.android.vanilla;

import java.util.ArrayList;
import java.util.Arrays;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import android.util.Log;

public class ShowQueueActivity extends Activity {
	private ListView mListView;
	private ShowQueueAdapter listAdapter;
	
	@Override  
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.showqueue_listview);
		
		mListView   = (ListView) findViewById(R.id.list);
		listAdapter = new ShowQueueAdapter(this, R.layout.showqueue_row);
		mListView.setAdapter(listAdapter);
		
		mListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				jumpToSong(position);
				finish();
			}});
		mListView.setOnItemLongClickListener(new OnItemLongClickListener() {
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				jumpToSong(position);
				/* This activity will stay open on longpress, so we have
				 * to update the playmerker ourselfs */
				listAdapter.highlightRow(position);
				listAdapter.notifyDataSetChanged();
				return true;
			}});

	}
	
	/*
	** Called when we are displayed (again)
	** This will always refresh the whole song list
	*/
	@Override
	public void onResume() {
		super.onResume();
		refreshSongQueueList();
	}
	
	/*
	** Tells the playback service to jump to a specific song
	*/
	private void jumpToSong(int id) {
		PlaybackService service = PlaybackService.get(this);
		service.jumpToQueuePosition(id);
	}
	
	private void refreshSongQueueList() {
		int i, stotal, spos;
		PlaybackService service = PlaybackService.get(this);
		
		stotal = service.getTimelineLength();   /* Total number of songs in queue */
		spos   = service.getTimelinePosition(); /* Current position in queue      */
		
		listAdapter.clear();                    /* Flush all existing entries...  */
		listAdapter.highlightRow(spos);         /* and highlight current position */
		
		for(i=0 ; i<stotal; i++) {
			listAdapter.add(service.getSongByQueuePosition(i));
		}
		mListView.setSelectionFromTop(spos, 0); /* scroll to currently playing song */
	}
	
	
	
}
