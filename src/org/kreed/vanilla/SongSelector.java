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

import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.graphics.Color;
import android.graphics.drawable.PaintDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.AdapterView;
import android.widget.Filter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

public class SongSelector extends PlaybackActivity implements AdapterView.OnItemClickListener, TextWatcher, TabHost.OnTabChangeListener, Filter.FilterListener {
	private TabHost mTabHost;

	private View mSearchBox;
	private boolean mSearchBoxVisible;
	private TextView mTextFilter;
	private View mClearButton;

	private View mStatus;
	TextView mStatusText;

	private ViewGroup mLimiterViews;

	private String mDefaultAction;
	private boolean mDefaultIsLastAction;

	private long mLastActedId;

	MediaAdapter getAdapter(int tab)
	{
		ListView list = (ListView)mTabHost.getTabContentView().getChildAt(tab);
		return (MediaAdapter)list.getAdapter();
	}

	@Override
	public void onCreate(Bundle state)
	{
		super.onCreate(state);

		setContentView(R.layout.song_selector);

		mTabHost = (TabHost)findViewById(android.R.id.tabhost);
		mTabHost.setup();
		mTabHost.setOnTabChangedListener(this);

		Resources res = getResources();
		mTabHost.addTab(mTabHost.newTabSpec("tab_artists").setIndicator(res.getText(R.string.artists), res.getDrawable(R.drawable.tab_artists)).setContent(R.id.artist_list));
		mTabHost.addTab(mTabHost.newTabSpec("tab_albums").setIndicator(res.getText(R.string.albums), res.getDrawable(R.drawable.tab_albums)).setContent(R.id.album_list));
		mTabHost.addTab(mTabHost.newTabSpec("tab_songs").setIndicator(res.getText(R.string.songs), res.getDrawable(R.drawable.tab_songs)).setContent(R.id.song_list));
		mTabHost.addTab(mTabHost.newTabSpec("tab_playlists").setIndicator(res.getText(R.string.playlists), res.getDrawable(R.drawable.tab_playlists)).setContent(R.id.playlist_list));

		mSearchBox = findViewById(R.id.search_box);

		mTextFilter = (TextView)findViewById(R.id.filter_text);
		mTextFilter.addTextChangedListener(this);

		mClearButton = findViewById(R.id.clear_button);
		mClearButton.setOnClickListener(this);

		mLimiterViews = (ViewGroup)findViewById(R.id.limiter_layout);

		setupView(R.id.artist_list, new MediaAdapter(this, MediaUtils.TYPE_ARTIST, true, false));
		setupView(R.id.album_list, new MediaAdapter(this, MediaUtils.TYPE_ALBUM, true, false));
		setupView(R.id.song_list, new SongMediaAdapter(this, false, false));
		setupView(R.id.playlist_list, new MediaAdapter(this, MediaUtils.TYPE_PLAYLIST, false, true));

		mHandler.sendEmptyMessage(MSG_INIT);
	}

	@Override
	public void onStart()
	{
		super.onStart();

		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
		int inputType = settings.getBoolean("filter_suggestions", false) ? InputType.TYPE_CLASS_TEXT : InputType.TYPE_TEXT_VARIATION_FILTER;
		mTextFilter.setInputType(inputType);

		boolean status = settings.getBoolean("selector_status", false);
		if (status && mStatus == null) {
			mStatus = findViewById(R.id.status);
			if (mStatus == null)
				mStatus = ((ViewStub)findViewById(R.id.status_stub)).inflate();
			mStatusText = (TextView)mStatus.findViewById(R.id.status_text);
			mPlayPauseButton = (ControlButton)mStatus.findViewById(R.id.play_pause);
			ControlButton next = (ControlButton)mStatus.findViewById(R.id.next);

			mPlayPauseButton.setOnClickListener(this);
			next.setOnClickListener(this);

			if (ContextApplication.hasService()) {
				PlaybackService service = ContextApplication.getService();
				setState(service.getState());
				onSongChange(service.getSong(0));
			}
		} else if (!status && mStatus != null) {
			mStatus.setVisibility(View.GONE);
			mStatus = null;
		}

		int action = Integer.parseInt(settings.getString("default_action_int", "0"));
		mDefaultAction = action == 1 ? PlaybackService.ACTION_ENQUEUE_ITEMS : PlaybackService.ACTION_PLAY_ITEMS;
		mDefaultIsLastAction = action == 2;
		mLastActedId = 0;
	}

	@Override
	protected void onServiceReady()
	{
		super.onServiceReady();

		if (mStatusText != null)
			onSongChange(ContextApplication.getService().getSong(0));
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			if (mSearchBoxVisible) {
				mTextFilter.setText("");
				setSearchBoxVisible(false);
			} else {
				sendFinishEnqueueing();
				finish();
			}
			return true;
		case KeyEvent.KEYCODE_SEARCH:
			setSearchBoxVisible(!mSearchBoxVisible);
			return true;
		default:
			if (super.onKeyDown(keyCode, event))
				return true;

			if (mTextFilter.onKeyDown(keyCode, event)) {
				if (!mSearchBoxVisible)
					setSearchBoxVisible(true);
				else
					mTextFilter.requestFocus();
				return true;
			}
		}

		return false;
	}

	private void sendSongIntent(MediaAdapter.MediaView view, String action)
	{
		int res = PlaybackService.ACTION_PLAY_ITEMS.equals(action) ? R.string.playing : R.string.enqueued;
		String text = getResources().getString(res, view.getTitle());
		Toast.makeText(this, text, Toast.LENGTH_SHORT).show();

		long id = view.getMediaId();

		Intent intent = new Intent(this, PlaybackService.class);
		intent.setAction(action);
		intent.putExtra("type", view.getMediaType());
		intent.putExtra("id", id);
		startService(intent);

		mLastActedId = id;
	}

	/**
	 * Tell the PlaybackService that we are finished enqueueing songs.
	 */
	private void sendFinishEnqueueing()
	{
		Intent intent = new Intent(this, PlaybackService.class);
		intent.setAction(PlaybackService.ACTION_FINISH_ENQUEUEING);
		startService(intent);
	}

	private void expand(MediaAdapter.MediaView view)
	{
		String[] limiter = view.getLimiter();

		getAdapter(limiter.length).setLimiter(limiter, false);
		mTabHost.setCurrentTab(limiter.length);

		for (int i = limiter.length + 1; i < 3; ++i)
			getAdapter(i).setLimiter(limiter, true);
	}

	public void onItemClick(AdapterView<?> list, View view, int pos, long id)
	{
		MediaAdapter.MediaView mediaView = (MediaAdapter.MediaView)view;
		if (mediaView.isExpanderPressed())
			expand(mediaView);
		else if (id == mLastActedId)
			startActivity(new Intent(this, FullPlaybackActivity.class));
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

			TextView view = new TextView(this);
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

	@Override
	public void onClick(View view)
	{
		if (view == mClearButton) {
			if (mTextFilter.getText().length() == 0)
				setSearchBoxVisible(false);
			else
				mTextFilter.setText("");
		} else if (view.getTag() != null) {
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
					adapter.setLimiter(limiter, true);
			}

			updateLimiterViews();
		} else {
			super.onClick(view);
		}
	}

	private static final int MENU_PLAY = 0;
	private static final int MENU_ENQUEUE = 1;
	private static final int MENU_EXPAND = 2;
	private static final int MENU_ADD_TO_PLAYLIST = 3;
	private static final int MENU_NEW_PLAYLIST = 4;
	private static final int MENU_DELETE = 5;

	@Override
	public void onCreateContextMenu(ContextMenu menu, View listView, ContextMenu.ContextMenuInfo absInfo)
	{
		MediaAdapter.MediaView view = (MediaAdapter.MediaView)((AdapterView.AdapterContextMenuInfo)absInfo).targetView;
		int type = view.getMediaType();
		int id = (int)view.getMediaId();

		menu.setHeaderTitle(view.getTitle());
		menu.add(0, MENU_PLAY, 0, R.string.play);
		menu.add(0, MENU_ENQUEUE, 0, R.string.enqueue);
		SubMenu playlistMenu = menu.addSubMenu(0, MENU_ADD_TO_PLAYLIST, 0, R.string.add_to_playlist);
		if (view.hasExpanders())
			menu.add(0, MENU_EXPAND, 0, R.string.expand);
		menu.add(0, MENU_DELETE, 0, R.string.delete);

		playlistMenu.add(type, MENU_NEW_PLAYLIST, id, R.string.new_playlist);
		Playlist[] playlists = Playlist.getPlaylists();
		if (playlists != null) {
			for (int i = 0; i != playlists.length; ++i)
				playlistMenu.add(type, (int)playlists[i].id + 100, id, playlists[i].name);
		}
	}

	/**
	 * Add a set of songs to a playlists. Sets can be all songs from an artist,
	 * album, playlist, or a single song. Displays a Toast notifying of
	 * success.
	 *
	 * @param playlistId The MediaStore.Audio.Playlists id of the playlist to
	 * be modified.
	 * @param type The type of media the mediaId represents; one of the
	 * Song.TYPE_* constants.
	 * @param mediaId The MediaStore id of the element to be added.
	 * @param title The title of the playlist being added to (used for the
	 * Toast).
	 */
	private void addToPlaylist(long playlistId, int type, long mediaId, CharSequence title)
	{
		long[] ids = MediaUtils.getAllSongIdsWith(type, mediaId);
		Playlist.addToPlaylist(playlistId, ids);

		String message = getResources().getQuantityString(R.plurals.added_to_playlist, ids.length, ids.length, title);
		Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
	}

	/**
	 * Delete the media with the specified type and id and show a Toast
	 * informing the user of this.
	 *
	 * @param type The type of media; one of the MediaUtils.TYPE_* constants.
	 * @param id The MediaStore id of the media.
	 * @param title The title of the playlist, to be displayed in the Toast.
	 * Only used when deleting a playlist.
	 */
	private void delete(int type, long id, String title)
	{
		if (type == MediaUtils.TYPE_PLAYLIST) {
			Playlist.deletePlaylist(id);
			String message = getResources().getString(R.string.playlist_deleted, title);
			Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
		} else {
			int count = MediaUtils.deleteMedia(type, id);
			String message = getResources().getQuantityString(R.plurals.deleted, count, count);
			Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		String action = PlaybackService.ACTION_PLAY_ITEMS;
		int id = item.getItemId();
		int type = item.getGroupId();
		int mediaId = item.getOrder();

		switch (id) {
		case MENU_EXPAND:
			expand((MediaAdapter.MediaView)((AdapterView.AdapterContextMenuInfo)item.getMenuInfo()).targetView);
			break;
		case MENU_ENQUEUE:
			action = PlaybackService.ACTION_ENQUEUE_ITEMS;
			// fall through
		case MENU_PLAY:
			if (mDefaultIsLastAction)
				mDefaultAction = action;
			sendSongIntent((MediaAdapter.MediaView)((AdapterView.AdapterContextMenuInfo)item.getMenuInfo()).targetView, action);
			break;
		case MENU_NEW_PLAYLIST: {
			NewPlaylistDialog dialog = new NewPlaylistDialog(this);
			Message message = mHandler.obtainMessage(MSG_NEW_PLAYLIST, type, mediaId);
			message.obj = dialog;
			dialog.setDismissMessage(message);
			dialog.show();
			break;
		}
		case MENU_DELETE: {
			MediaAdapter.MediaView view = (MediaAdapter.MediaView)((AdapterView.AdapterContextMenuInfo)item.getMenuInfo()).targetView;
			type = view.getMediaType();
			if (type != MediaUtils.TYPE_PLAYLIST)
				Toast.makeText(this, R.string.deleting, Toast.LENGTH_SHORT).show();
			Message message = mHandler.obtainMessage(MSG_DELETE, type, (int)view.getMediaId());
			message.obj = view.getTitle();
			mHandler.sendMessage(message);
			break;
		}
		default:
			if (id > 100)
				addToPlaylist(id - 100, type, mediaId, item.getTitle());
			return false;
		}

		return true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		menu.add(0, MENU_PLAYBACK, 0, R.string.playback_view).setIcon(android.R.drawable.ic_menu_gallery);
		menu.add(0, MENU_SEARCH, 0, R.string.search).setIcon(android.R.drawable.ic_menu_search);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId()) {
		case MENU_SEARCH:
			setSearchBoxVisible(!mSearchBoxVisible);
			return true;
		case MENU_PLAYBACK:
			sendFinishEnqueueing();
			startActivity(new Intent(this, FullPlaybackActivity.class));
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	public void onFilterComplete(int count)
	{
		CharSequence text = mTextFilter.getText();
		for (int i = 3; --i != -1; )
			getAdapter(i).filter(text, null);
	}

	/**
	 * Hook up a ListView to this Activity and the supplied adapter
	 *
	 * @param id The id of the ListView
	 * @param adapter The adapter to be used
	 */
	private void setupView(int id, final MediaAdapter adapter)
	{
		final ListView view = (ListView)findViewById(id);
		view.setOnItemClickListener(this);
		view.setOnCreateContextMenuListener(this);
		view.setAdapter(adapter);
	}

	/**
	 * Perform the initialization that may be done in the background outside
	 * of onCreate.
	 */
	private static final int MSG_INIT = 10;
	/**
	 * Call addToPlaylist with the parameters from the given message. The
	 * message must contain the type and id of the media to be added in
	 * arg1 and arg2, respectively. The obj field must be a NewPlaylistDialog
	 * that the name will be taken from.
	 */
	private static final int MSG_NEW_PLAYLIST = 11;
	/**
	 * Delete the songs in the set of media with the specified type and id,
	 * given as arg1 and arg2, respectively. If type is a playlist, the
	 * playlist itself will be deleted, not the songs it contains. The obj
	 * field should contain the playlist name (as a String) if type is a
	 * playlist.
	 */
	private static final int MSG_DELETE = 12;

	@Override
	public boolean handleMessage(Message message)
	{
		switch (message.what) {
		case MSG_INIT:
			ContentResolver resolver = getContentResolver();
			Observer observer = new Observer(mHandler);
			resolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer);
			break;
		case MSG_NEW_PLAYLIST:
			NewPlaylistDialog dialog = (NewPlaylistDialog)message.obj;
			if (dialog.isAccepted()) {
				String name = dialog.getText();
				long playlistId = Playlist.createPlaylist(name);
				addToPlaylist(playlistId, message.arg1, message.arg2, name);
			}
			break;
		case MSG_DELETE:
			delete(message.arg1, message.arg2, (String)message.obj);
			break;
		default:
			return super.handleMessage(message);
		}

		return true;
	}

	private class Observer extends ContentObserver {
		public Observer(Handler handler)
		{
			super(handler);
		}

		@Override
		public void onChange(boolean selfChange)
		{
			runOnUiThread(new Runnable() {
				public void run()
				{
					for (int i = 0; i != 4; ++i)
						getAdapter(i).requery();
				}
			});
		}
	};

	private void setSearchBoxVisible(boolean visible)
	{
		mSearchBoxVisible = visible;
		mSearchBox.setVisibility(visible ? View.VISIBLE : View.GONE);
		if (visible)
			mSearchBox.requestFocus();
	}

	/**
	 * Call to update the status text for a newly-playing song.
	 */
	private void onSongChange(final Song song)
	{
		if (mStatusText != null) {
			runOnUiThread(new Runnable() {
				public void run()
				{
					mStatusText.setText(song == null ? getResources().getText(R.string.none) : song.title);
				}
			});
		}
	}

	@Override
	public void receive(Intent intent)
	{
		if (mStatusText != null) {
			Song song = intent.getParcelableExtra("song");
			onSongChange(song);
		}
	}
}
