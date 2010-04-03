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

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.PaintDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Filter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

public class SongSelector extends Dialog implements AdapterView.OnItemClickListener, TextWatcher, View.OnClickListener, TabHost.OnTabChangeListener, Filter.FilterListener {
	private TabHost mTabHost;
	private TextView mTextFilter;
	private View mClearButton;

	private ViewGroup mLimiterViews;

	private int mDefaultAction;
	private boolean mDefaultIsLastAction;

	private long mLastActedId;

	private ListView getList(int tab)
	{
		return (ListView)mTabHost.getTabContentView().getChildAt(tab);
	}

	private MediaAdapter getAdapter(int tab)
	{
		return (MediaAdapter)getList(tab).getAdapter();
	}

	private void initializeList(int id, Uri store, String[] fields, String[] fieldKeys)
	{
		ListView view = (ListView)findViewById(id);
		view.setOnItemClickListener(this);
		view.setOnCreateContextMenuListener(this);
		view.setAdapter(new MediaAdapter(getContext(), store, fields, fieldKeys, true));
	}

	public SongSelector(Context context)
	{
		super(context, android.R.style.Theme_NoTitleBar);
	}

	@Override
	public void onCreate(Bundle state)
	{
		setContentView(R.layout.song_selector);

		mTabHost = (TabHost)findViewById(android.R.id.tabhost);
		mTabHost.setup();
		mTabHost.setOnTabChangedListener(this);

		Resources res = getContext().getResources();
		mTabHost.addTab(mTabHost.newTabSpec("tab_artists").setIndicator(res.getText(R.string.artists), res.getDrawable(R.drawable.tab_artists)).setContent(R.id.artist_list));
		mTabHost.addTab(mTabHost.newTabSpec("tab_albums").setIndicator(res.getText(R.string.albums), res.getDrawable(R.drawable.tab_albums)).setContent(R.id.album_list));
		mTabHost.addTab(mTabHost.newTabSpec("tab_songs").setIndicator(res.getText(R.string.songs), res.getDrawable(R.drawable.tab_songs)).setContent(R.id.song_list));

		mTextFilter = (TextView)findViewById(R.id.filter_text);
		mTextFilter.addTextChangedListener(this);

		mClearButton = findViewById(R.id.clear_button);
		mClearButton.setOnClickListener(this);

		mLimiterViews = (ViewGroup)findViewById(R.id.limiter_layout);

		new Handler().post(new Runnable() {
			public void run()
			{
				initializeList(R.id.artist_list, MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI, MediaAdapter.ARTIST_FIELDS, MediaAdapter.ARTIST_FIELD_KEYS);
				initializeList(R.id.album_list, MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, MediaAdapter.ALBUM_FIELDS, MediaAdapter.ALBUM_FIELD_KEYS);

				ListView view = (ListView)findViewById(R.id.song_list);
				view.setOnItemClickListener(SongSelector.this);
				view.setOnCreateContextMenuListener(SongSelector.this);
				view.setAdapter(new SongMediaAdapter(getContext()));
			}
		});
	}

	@Override
	public void onStart()
	{
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getContext());
		int inputType = settings.getBoolean("filter_suggestions", false) ? InputType.TYPE_CLASS_TEXT : InputType.TYPE_TEXT_VARIATION_FILTER;
		mTextFilter.setInputType(inputType);

		mDefaultAction = Integer.parseInt(settings.getString("default_action_int", "0"));
		mLastActedId = 0;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		if (super.onKeyDown(keyCode, event))
			return true;

		mTextFilter.requestFocus();
		return mTextFilter.onKeyDown(keyCode, event);
	}

	private void sendSongIntent(MediaAdapter.MediaView view, int action)
	{
		int res = action == PlaybackService.ACTION_PLAY ? R.string.playing : R.string.enqueued;
		String text = getContext().getResources().getString(res, view.getTitle());
		Toast.makeText(getContext(), text, Toast.LENGTH_SHORT).show();

		long id = view.getMediaId();
		int field = view.getFieldCount();

		Intent intent = new Intent(getContext(), PlaybackService.class);
		intent.putExtra("type", field);
		intent.putExtra("action", action);
		intent.putExtra("id", id);
		getContext().startService(intent);

		mLastActedId = id;
	}

	private void expand(MediaAdapter.MediaView view)
	{
		String[] limiter = view.getLimiter();

		for (int i = limiter.length; i != 3; ++i)
			getAdapter(i).setLimiter(limiter);

		mTabHost.setCurrentTab(limiter.length);
	}

	public void onItemClick(AdapterView<?> list, View view, int pos, long id)
	{
		MediaAdapter.MediaView mediaView = (MediaAdapter.MediaView)view;
		if (mediaView.isExpanderPressed())
			expand(mediaView);
		else if (id == mLastActedId)
			dismiss();
		else
			sendSongIntent(mediaView, mDefaultAction);
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
			adapter.filter(text, this);
	}

	private void updateLimiterViews()
	{
		if (mLimiterViews == null)
			return;

		mLimiterViews.removeAllViews();

		MediaAdapter adapter = getAdapter(mTabHost.getCurrentTab());
		if (adapter == null)
			return;

		String[] limiter = adapter.getLimiter();
		if (limiter == null)
			return;

		LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
		params.leftMargin = 5;
		for (int i = 0; i != limiter.length; ++i) {
			PaintDrawable background = new PaintDrawable(Color.GRAY);
			background.setCornerRadius(5);

			TextView view = new TextView(getContext());
			view.setSingleLine();
			view.setEllipsize(TextUtils.TruncateAt.MARQUEE);
			view.setText(limiter[i] + " | X");
			view.setTextColor(Color.WHITE);
			view.setBackgroundDrawable(background);
			view.setLayoutParams(params);
			view.setPadding(5, 2, 5, 2);
			view.setTag(i);
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
			int i = (Integer)view.getTag();
			String[] limiter;
			if (i == 0) {
				limiter = null;
			} else {
				String[] oldLimiter = getAdapter(mTabHost.getCurrentTab()).getLimiter();
				limiter = new String[i];
				System.arraycopy(oldLimiter, 0, limiter, 0, i);
			}

			for (int j = 3; --j != -1; ) {
				MediaAdapter adapter = getAdapter(j);
				if (adapter.getLimiterLength() > i)
					adapter.setLimiter(limiter);
			}

			updateLimiterViews();
		}
	}

	private static final int MENU_PLAY = 0;
	private static final int MENU_ENQUEUE = 1;
	private static final int MENU_EXPAND = 2;

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenu.ContextMenuInfo absInfo)
	{
		menu.add(0, MENU_PLAY, 0, R.string.play);
		menu.add(0, MENU_ENQUEUE, 0, R.string.enqueue);
		if (((MediaAdapter)((ListView)view).getAdapter()).hasExpanders())
			menu.add(0, MENU_EXPAND, 0, R.string.expand);
	}

	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		MediaAdapter.MediaView view = (MediaAdapter.MediaView)((AdapterView.AdapterContextMenuInfo)item.getMenuInfo()).targetView;
		int action = PlaybackService.ACTION_PLAY;
		switch (item.getItemId()) {
		case MENU_EXPAND:
			expand(view);
			break;
		case MENU_ENQUEUE:
			action = PlaybackService.ACTION_ENQUEUE;
			// fall through
		case MENU_PLAY:
			if (mDefaultIsLastAction)
				mDefaultAction = action;
			sendSongIntent(view, action);
			break;
		default:
			return false;
		}

		return true;
	}

	// this is needed because due to some bug in Dialog, onContextItemSelected
	// is not called when it should be
	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item)
	{
		return onContextItemSelected(item);
	}

	public void onFilterComplete(int count)
	{
		CharSequence text = mTextFilter.getText();
		for (int i = 3; --i != -1; )
			getAdapter(i).filter(text, null);
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event)
	{
		return PlaybackActivity.handleKeyLongPress(getContext(), keyCode);
	}
}