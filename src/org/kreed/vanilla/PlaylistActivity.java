/*
 * Copyright (C) 2011 Christopher Eby <kreed@kreed.org>
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

package org.kreed.vanilla;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;

/**
 * The playlist activity where playlist songs can be viewed and reordered.
 */
public class PlaylistActivity extends Activity
	implements View.OnClickListener
	         , AbsListView.OnItemClickListener
	         , DialogInterface.OnClickListener
{
	private Looper mLooper;
	private DragListView mListView;
	private PlaylistAdapter mAdapter;

	private long mPlaylistId;
	private String mPlaylistName;
	private boolean mEditing;

	private Button mEditButton;
	private Button mDeleteButton;

	@Override
	public void onCreate(Bundle state)
	{
		super.onCreate(state);

		HandlerThread thread = new HandlerThread(getClass().getName());
		thread.start();

		MediaView.init(this);

		setContentView(R.layout.playlist_activity);

		DragListView view = (DragListView)findViewById(R.id.playlist);
		view.setCacheColorHint(Color.BLACK);
		view.setDivider(null);
		view.setFastScrollEnabled(true);
		view.setOnItemClickListener(this);
		mListView = view;

		View header = LayoutInflater.from(this).inflate(R.layout.playlist_buttons, null);
		mEditButton = (Button)header.findViewById(R.id.edit);
		mEditButton.setOnClickListener(this);
		mDeleteButton = (Button)header.findViewById(R.id.delete);
		mDeleteButton.setOnClickListener(this);
		view.addHeaderView(header);

		mLooper = thread.getLooper();
		mAdapter = new PlaylistAdapter(this, mLooper);
		view.setAdapter(mAdapter);

		onNewIntent(getIntent());
	}

	@Override
	public void onDestroy()
	{
		mLooper.quit();
		super.onDestroy();
	}

	@Override
	public void onNewIntent(Intent intent)
	{
		long id = intent.getLongExtra("playlist", 0);
		String title = intent.getStringExtra("title");
		mAdapter.setPlaylistId(id);
		setTitle(title);
		mPlaylistId = id;
		mPlaylistName = title;
	}

	/**
	 * Enable or disable edit mode, which allows songs to be reordered and
	 * removed.
	 *
	 * @param editing True to enable edit mode.
	 */
	public void setEditing(boolean editing)
	{
		mListView.setEditable(editing);
		mAdapter.setEditable(editing);
		int visible = editing ? View.GONE : View.VISIBLE;
		mDeleteButton.setVisibility(visible);
		mEditButton.setText(editing ? R.string.done : R.string.edit);
		mEditing = editing;
	}

	@Override
	public void onClick(View view)
	{
		switch (view.getId()) {
		case R.id.edit:
			setEditing(!mEditing);
			break;
		case R.id.delete: {
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			String message = getResources().getString(R.string.delete_playlist, mPlaylistName);
			builder.setMessage(message);
			builder.setPositiveButton(R.string.delete, this);
			builder.setNegativeButton(R.string.cancel, this);
			builder.show();
			break;
		}
		}
	}

	@Override
	public void onItemClick(AdapterView<?> adapterView, View view, int position, long id)
	{
		if (view instanceof MediaView) {
			MediaView mediaView = (MediaView)view;
			if (mediaView.isRightBitmapPressed()) {
				mAdapter.remove(id);
			} else if (!mEditing) {
				QueryTask query = MediaUtils.buildPlaylistQuery(mPlaylistId, Song.FILLED_PLAYLIST_PROJECTION, null);
				PlaybackService.get(this).addSongs(SongTimeline.MODE_PLAY_POS_FIRST, query, position - mListView.getHeaderViewsCount());
			}
		}
	}

	@Override
	public void onClick(DialogInterface dialog, int which)
	{
		if (which == DialogInterface.BUTTON_POSITIVE) {
			Playlist.deletePlaylist(getContentResolver(), mPlaylistId);
			finish();
		}
		dialog.dismiss();
	}
}
