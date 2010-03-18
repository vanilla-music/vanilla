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
import android.graphics.Color;
import android.graphics.drawable.PaintDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.Filter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

public class SongSelector extends TabActivity implements AdapterView.OnItemClickListener, TextWatcher, View.OnClickListener, TabHost.OnTabChangeListener, Filter.FilterListener {
	private TabHost mTabHost;
	private TextView mTextFilter;
	private View mClearButton;

	private ViewGroup mLimiterViews;

	private int mDefaultAction;
	private boolean mDefaultIsLastAction;

	private ListView getList(int tab)
	{
		return (ListView)mTabHost.getTabContentView().getChildAt(tab);
	}

	private MediaAdapter getAdapter(int tab)
	{
		return (MediaAdapter)getList(tab).getAdapter();
	}

	private void initializeList(int id, SongData[] songs, int lineA, int lineB, View.OnClickListener expanderListener)
	{
		ListView view = (ListView)findViewById(id);
		view.setOnItemClickListener(this);
		view.setOnCreateContextMenuListener(this);
		view.setAdapter(new MediaAdapter(SongSelector.this, songs, lineA, lineB, this));
	}

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);

		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.song_selector);

		mTabHost = getTabHost();
		mTabHost.setOnTabChangedListener(this);

		Resources res = getResources();
		mTabHost.addTab(mTabHost.newTabSpec("tab_artists").setIndicator(res.getText(R.string.artists), res.getDrawable(R.drawable.tab_artists)).setContent(R.id.artist_list));
		mTabHost.addTab(mTabHost.newTabSpec("tab_albums").setIndicator(res.getText(R.string.albums), res.getDrawable(R.drawable.tab_albums)).setContent(R.id.album_list));
		mTabHost.addTab(mTabHost.newTabSpec("tab_songs").setIndicator(res.getText(R.string.songs), res.getDrawable(R.drawable.tab_songs)).setContent(R.id.song_list));

		mTextFilter = (TextView)findViewById(R.id.filter_text);
		mTextFilter.addTextChangedListener(this);

		mClearButton = findViewById(R.id.clear_button);
		mClearButton.setOnClickListener(this);

		mLimiterViews = (ViewGroup)findViewById(R.id.limiter_layout);

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this); 
		int inputType;
		if (settings.getBoolean("phone_input", false))
			inputType = InputType.TYPE_CLASS_PHONE;
		else if (!settings.getBoolean("filter_suggestions", false))
			inputType = InputType.TYPE_TEXT_VARIATION_FILTER;
		else
			inputType = InputType.TYPE_CLASS_TEXT;
		mTextFilter.setInputType(inputType);

		String action = settings.getString("default_action", "Play");
		if (action.equals("Last Used Action")) {
			mDefaultIsLastAction = true;
			mDefaultAction = PlaybackService.ACTION_PLAY;
		} else if (action.equals("Enqueue")) {
			mDefaultAction = PlaybackService.ACTION_ENQUEUE;
		} else {
			mDefaultAction = PlaybackService.ACTION_PLAY;
		}

		new Handler().post(new Runnable() {
			public void run()
			{
				SongData[] songs = SongData.getAllSongData();

				initializeList(R.id.artist_list, SongData.filter(songs, new SongData.ArtistComparator()), SongData.FIELD_ARTIST, -1, SongSelector.this);
				initializeList(R.id.album_list, SongData.filter(songs, new SongData.AlbumComparator()), SongData.FIELD_ALBUM, SongData.FIELD_ARTIST, SongSelector.this);
				Arrays.sort(songs, new SongData.TitleComparator());
				initializeList(R.id.song_list, songs, SongData.FIELD_TITLE, SongData.FIELD_ARTIST, null);
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

	public void onTextChanged(CharSequence text, int start, int before, int count)
	{
		MediaAdapter adapter = getAdapter(mTabHost.getCurrentTab());
		if (adapter != null)
			adapter.getFilter().filter(text, this);
	}

	private void updateLimiterViews()
	{
		if (mLimiterViews == null)
			return;

		mLimiterViews.removeAllViews();

		MediaAdapter adapter = getAdapter(mTabHost.getCurrentTab());
		if (adapter == null)
			return;

		SongData.Field limiter = adapter.getLimiter();
		if (limiter == null)
			return;

		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		params.leftMargin = 5;
		for (int i = SongData.FIELD_ARTIST; i <= limiter.field; ++i) {
			PaintDrawable background = new PaintDrawable(Color.GRAY);
			background.setCornerRadius(5);

			TextView view = new TextView(this);
			view.setSingleLine();
			view.setEllipsize(TextUtils.TruncateAt.MARQUEE);
			view.setText(limiter.data.getField(i) + " | X");
			view.setTextColor(Color.WHITE);
			view.setBackgroundDrawable(background);
			view.setLayoutParams(params);
			view.setPadding(5, 2, 5, 2);
			view.setTag(new SongData.Field(i, null));
			view.setOnClickListener(this);
			mLimiterViews.addView(view);
		}
	}

	public void onTabChanged(String tabId)
	{
		updateLimiterViews();
	}

	public void onClick(View view)
	{
		if (view == mClearButton) {
			mTextFilter.setText("");
		} else {
			SongData.Field data = null;
			try {
				data = (SongData.Field)view.getTag();
			} catch (ClassCastException e) {
			}

			if (data == null)
				return;

			if (view instanceof TextView) {
				SongData.Field limiter = getAdapter(mTabHost.getCurrentTab()).getLimiter();
				int field = data.field - 1;
				if (limiter.field == 0)
					limiter = null;
				else
					limiter.field = field;
				for (int i = 3; --i != -1; ) {
					MediaAdapter adapter = getAdapter(i);
					SongData.Field currentLimiter = adapter.getLimiter();
					if (currentLimiter != null && currentLimiter.field > field)
						adapter.setLimiter(limiter);
				}
				updateLimiterViews();
			} else {
				SongData.Field limiter = new SongData.Field(data);
				for (int i = data.field; i != 3; ++i) {
					MediaAdapter adapter = getAdapter(i);
					adapter.hideAll();
					adapter.setLimiter(limiter);
				}
				mTabHost.setCurrentTab(data.field);
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
		Intent intent = item.getIntent();
		if (mDefaultIsLastAction)
			mDefaultAction = intent.getIntExtra("action", PlaybackService.ACTION_PLAY);
		sendSongIntent(intent);
		return true;
	}

	public void onFilterComplete(int count)
	{
		CharSequence text = mTextFilter.getText();
		for (int i = 3; --i != -1; )
			getAdapter(i).getFilter().filter(text);
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event)
	{
		return PlaybackServiceActivity.handleKeyLongPress(this, keyCode);
	}
}