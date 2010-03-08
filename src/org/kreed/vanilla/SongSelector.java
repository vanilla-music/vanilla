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
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TextView;

public class SongSelector extends TabActivity implements AdapterView.OnItemClickListener, TextWatcher, View.OnClickListener {
	private Handler mHandler;
	private TabHost mTabHost;
	private TextView mTextFilter;
	private AbstractAdapter[] mAdapters = new AbstractAdapter[3];

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);

		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.song_selector);

		mHandler = new Handler();
		mTabHost = getTabHost();

		Resources res = getResources();
		mTabHost.addTab(mTabHost.newTabSpec("tab_artists").setIndicator(res.getText(R.string.artists), res.getDrawable(R.drawable.tab_artists)).setContent(R.id.artist_list));
		mTabHost.addTab(mTabHost.newTabSpec("tab_albums").setIndicator(res.getText(R.string.albums), res.getDrawable(R.drawable.tab_albums)).setContent(R.id.album_list));
		mTabHost.addTab(mTabHost.newTabSpec("tab_songs").setIndicator(res.getText(R.string.songs), res.getDrawable(R.drawable.tab_songs)).setContent(R.id.song_list));

		final Song[] songs = Song.getAllSongMetadata();
		mAdapters[0] = new ArtistAdapter(this, songs);

		ListView artistView = (ListView)findViewById(R.id.artist_list);
		artistView.setAdapter(mAdapters[0]);

		mTextFilter = (TextView)findViewById(R.id.filter_text);
		mTextFilter.addTextChangedListener(this);
		
		View clearButton = findViewById(R.id.clear_button);
		clearButton.setOnClickListener(this);

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this); 
		int inputType;
		if (settings.getBoolean("phone_input", false))
			inputType = InputType.TYPE_CLASS_PHONE;
		else if (!settings.getBoolean("filter_suggestions", false))
			inputType = InputType.TYPE_TEXT_VARIATION_FILTER;
		else
			inputType = InputType.TYPE_CLASS_TEXT;
		mTextFilter.setInputType(inputType);

		mHandler.post(new Runnable() {
			public void run()
			{
				mAdapters[1] = new AlbumAdapter(SongSelector.this, songs);
				mAdapters[2] = new SongAdapter(SongSelector.this, songs);

				ListView albumView = (ListView)findViewById(R.id.album_list);
				albumView.setAdapter(mAdapters[1]);

				ListView songView = (ListView)findViewById(R.id.song_list);
				songView.setAdapter(mAdapters[2]);
				songView.setOnItemClickListener(SongSelector.this);
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

	public void onItemClick(AdapterView<?> list, View view, int pos, long id)
	{
		Intent intent = new Intent(this, PlaybackService.class);
		intent.putExtra("songId", (int)id);
		startService(intent);
	}

	public void afterTextChanged(Editable editable)
	{
	}

	public void beforeTextChanged(CharSequence s, int start, int count, int after)
	{
	}

	public void onTextChanged(CharSequence s, int start, int before, int count)
	{
		AbstractAdapter adapter = mAdapters[mTabHost.getCurrentTab()];
		if (adapter != null)
			adapter.getFilter().filter(s);
	}

	public void onClick(View view)
	{
		mTextFilter.setText("");
	}
}