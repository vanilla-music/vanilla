/*
 * Copyright (C) 2010 Christopher Eby <kreed@kreed.org>
 *
 * This file is part of Vanilla Music Player.
 *
 * Vanilla Music Player is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Vanilla Music Player is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.kreed.vanilla;

import java.util.Arrays;

import org.kreed.vanilla.R;

import android.app.TabActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

public class SongSelector extends TabActivity implements AdapterView.OnItemClickListener, TextWatcher, View.OnClickListener {
	private TabHost mTabHost;
	private TextView mTextFilter;
	private View mClearButton;
	private MediaAdapter[] mAdapters = new MediaAdapter[3];

	private int mDefaultAction;

	private void initializeListView(int id, BaseAdapter adapter)
	{
		ListView view = (ListView)findViewById(id);
		view.setOnItemClickListener(this);
		view.setOnCreateContextMenuListener(this);
		view.setAdapter(adapter);
	}

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);

		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.song_selector);

		mTabHost = getTabHost();

		Resources res = getResources();
		mTabHost.addTab(mTabHost.newTabSpec("tab_artists").setIndicator(res.getText(R.string.artists), res.getDrawable(R.drawable.tab_artists)).setContent(R.id.artist_list));
		mTabHost.addTab(mTabHost.newTabSpec("tab_albums").setIndicator(res.getText(R.string.albums), res.getDrawable(R.drawable.tab_albums)).setContent(R.id.album_list));
		mTabHost.addTab(mTabHost.newTabSpec("tab_songs").setIndicator(res.getText(R.string.songs), res.getDrawable(R.drawable.tab_songs)).setContent(R.id.song_list));

		mTextFilter = (TextView)findViewById(R.id.filter_text);
		mTextFilter.addTextChangedListener(this);

		mClearButton = findViewById(R.id.clear_button);
		mClearButton.setOnClickListener(this);

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this); 
		int inputType;
		if (settings.getBoolean("phone_input", false))
			inputType = InputType.TYPE_CLASS_PHONE;
		else if (!settings.getBoolean("filter_suggestions", false))
			inputType = InputType.TYPE_TEXT_VARIATION_FILTER;
		else
			inputType = InputType.TYPE_CLASS_TEXT;
		mTextFilter.setInputType(inputType);

		mDefaultAction = settings.getString("default_action", "Play").equals("Play") ? PlaybackService.ACTION_PLAY : PlaybackService.ACTION_ENQUEUE;

		new Handler().post(new Runnable() {
			public void run()
			{
				Song[] songs = Song.getAllSongMetadata();

				mAdapters[0] = new MediaAdapter(SongSelector.this, Song.filter(songs, new Song.ArtistComparator()), Song.FIELD_ARTIST, -1);
				mAdapters[0].setExpanderListener(SongSelector.this);
				initializeListView(R.id.artist_list, mAdapters[0]);

				mAdapters[1] = new MediaAdapter(SongSelector.this, Song.filter(songs, new Song.AlbumComparator()), Song.FIELD_ALBUM, Song.FIELD_ARTIST);
				mAdapters[1].setExpanderListener(SongSelector.this);
				initializeListView(R.id.album_list, mAdapters[1]);

				Arrays.sort(songs, new Song.TitleComparator());
				mAdapters[2] = new MediaAdapter(SongSelector.this, songs, Song.FIELD_TITLE, Song.FIELD_ARTIST);
				initializeListView(R.id.song_list, mAdapters[2]);
			}
		});
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		if (super.onKeyDown(keyCode, event))
			return true;

		mTextFilter.requestFocus();
		return mTextFilter.onKeyDown(keyCode, event);
	}

	private void sendSongIntent(Intent intent)
	{
		int action = intent.getIntExtra("action", PlaybackService.ACTION_PLAY) == PlaybackService.ACTION_PLAY ? R.string.playing : R.string.enqueued;
		String title = intent.getStringExtra("title");
		String text = getResources().getString(action, title);
		Toast.makeText(this, text, Toast.LENGTH_SHORT).show();

		intent.removeExtra("title");
		startService(intent);
	}

	public void onItemClick(AdapterView<?> list, View view, int pos, long id)
	{
		sendSongIntent(((MediaAdapter)list.getAdapter()).buildSongIntent(mDefaultAction, pos));
	}

	public void afterTextChanged(Editable editable)
	{
	}

	public void beforeTextChanged(CharSequence s, int start, int count, int after)
	{
	}

	public void onTextChanged(CharSequence s, int start, int before, int count)
	{
		MediaAdapter adapter = mAdapters[mTabHost.getCurrentTab()];
		if (adapter != null)
			adapter.getFilter().filter(s);
	}

	public void onClick(View view)
	{
		if (view == mClearButton) {
			mTextFilter.setText("");
		} else {
			Object fieldObj = view.getTag(R.id.field);
			if (fieldObj != null) {
				int field = (Integer)fieldObj;
				int id = (Integer)view.getTag(R.id.id);
				mTabHost.setCurrentTab(field);
				for (int i = field; i != 3; ++i)
					mAdapters[i].setLimiter(field, id);
			}
		}
	}

	private static final int MENU_PLAY = 0;
	private static final int MENU_ENQUEUE = 1;

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo info)
	{
		MediaAdapter adapter = (MediaAdapter)((ListView)view).getAdapter();
		int pos = (int)((AdapterView.AdapterContextMenuInfo)info).position;
		menu.add(0, MENU_PLAY, 0, R.string.play).setIntent(adapter.buildSongIntent(PlaybackService.ACTION_PLAY, pos));
		menu.add(0, MENU_ENQUEUE, 0, R.string.enqueue).setIntent(adapter.buildSongIntent(PlaybackService.ACTION_ENQUEUE, pos));
	}

	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		sendSongIntent(item.getIntent());
		return true;
	}
}