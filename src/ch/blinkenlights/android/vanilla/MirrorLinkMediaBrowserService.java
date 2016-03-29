/*
 * Copyright (C) 2013 - 2015 Adrian Ulrich <adrian@blinkenlights.ch>
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

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ContentResolver;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.media.MediaDescription;
import android.media.MediaMetadata;
import android.media.browse.MediaBrowser;
import android.media.browse.MediaBrowser.MediaItem;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.service.media.MediaBrowserService;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Message;
import android.os.Looper;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles Music Playback through MirrorLink(tm) by implementing a MediaBrowserService.
 */

@TargetApi(21)
public class MirrorLinkMediaBrowserService extends MediaBrowserService
	implements Handler.Callback,
	           TimelineCallback {

	private static final String TAG = "MirrorLinkMediaBrowserService";
	// Action to change the repeat mode
	private static final String CUSTOM_ACTION_REPEAT = "ch.blinkenlights.android.vanilla.REPEAT";
	// Action to change the repeat mode
	private static final String CUSTOM_ACTION_SHUFFLE = "ch.blinkenlights.android.vanilla.SHUFFLE";

	// Media managers
	private MediaAdapter mArtistAdapter;
	private MediaAdapter mAlbumAdapter;
	private MediaAdapter mSongAdapter;
	private MediaAdapter mPlaylistAdapter;
	private MediaAdapter mGenreAdapter;
	private MediaAdapter[] mMediaAdapters = new MediaAdapter[MediaUtils.TYPE_GENRE + 1];
	private List<MediaBrowser.MediaItem> mQueryResult = new ArrayList<MediaBrowser.MediaItem>();

	private final List<MediaBrowser.MediaItem> mMediaRoot = new ArrayList<MediaBrowser.MediaItem>();

	// Media Session
	private MediaSession mSession;
	private Bundle mSessionExtras;

	// Indicates whether the service was started.
	private boolean mServiceStarted;

	private Looper mLooper;
	private Handler mHandler;

	@Override
	public void onCreate() {
		Log.d("VanillaMusic", "MediaBrowserService#onCreate");
		super.onCreate();

		HandlerThread thread = new HandlerThread("MediaBrowserService", Process.THREAD_PRIORITY_DEFAULT);
		thread.start();

		// Prep the Media Adapters (caches the top categories)
		mArtistAdapter = new MediaAdapter(this, MediaUtils.TYPE_ARTIST, null ,null);
		mAlbumAdapter = new MediaAdapter(this, MediaUtils.TYPE_ALBUM, null, null);
		mSongAdapter = new MediaAdapter(this, MediaUtils.TYPE_SONG, null, null);
		mPlaylistAdapter = new MediaAdapter(this, MediaUtils.TYPE_PLAYLIST, null, null);
		mGenreAdapter = new MediaAdapter(this, MediaUtils.TYPE_GENRE, null, null);
		mMediaAdapters[MediaUtils.TYPE_ARTIST] = mArtistAdapter;
		mMediaAdapters[MediaUtils.TYPE_ALBUM] = mAlbumAdapter;
		mMediaAdapters[MediaUtils.TYPE_SONG] = mSongAdapter;
		mMediaAdapters[MediaUtils.TYPE_PLAYLIST] = mPlaylistAdapter;
		mMediaAdapters[MediaUtils.TYPE_GENRE] = mGenreAdapter;

		// Fill and cache the top queries

		mMediaRoot.add(new MediaBrowser.MediaItem(
				new MediaDescription.Builder()
					.setMediaId(Integer.toString(MediaUtils.TYPE_ARTIST))
					.setTitle(getString(R.string.artists))
					.setIconUri(Uri.parse("android.resource://" +
							"ch.blinkenlights.android.vanilla/drawable/ic_menu_music_library"))
					.setSubtitle(getString(R.string.artists))
					.build(), MediaBrowser.MediaItem.FLAG_BROWSABLE
		));

		mMediaRoot.add(new MediaBrowser.MediaItem(
				new MediaDescription.Builder()
					.setMediaId(Integer.toString(MediaUtils.TYPE_ALBUM))
					.setTitle(getString(R.string.albums))
					.setIconUri(Uri.parse("android.resource://" +
							"ch.blinkenlights.android.vanilla/drawable/ic_menu_music_library"))
					.setSubtitle(getString(R.string.albums))
					.build(), MediaBrowser.MediaItem.FLAG_BROWSABLE
		));

		mMediaRoot.add(new MediaBrowser.MediaItem(
				new MediaDescription.Builder()
					.setMediaId(Integer.toString(MediaUtils.TYPE_SONG))
					.setTitle(getString(R.string.songs))
					.setIconUri(Uri.parse("android.resource://" +
							"ch.blinkenlights.android.vanilla/drawable/ic_menu_music_library"))
					.setSubtitle(getString(R.string.songs))
					.build(), MediaBrowser.MediaItem.FLAG_BROWSABLE
		));

		mMediaRoot.add(new MediaBrowser.MediaItem(
				new MediaDescription.Builder()
					.setMediaId(Integer.toString(MediaUtils.TYPE_GENRE))
					.setTitle(getString(R.string.genres))
					.setIconUri(Uri.parse("android.resource://" +
							"ch.blinkenlights.android.vanilla/drawable/ic_menu_music_library"))
					.setSubtitle(getString(R.string.genres))
					.build(), MediaBrowser.MediaItem.FLAG_BROWSABLE
		));

		mMediaRoot.add(new MediaBrowser.MediaItem(
				new MediaDescription.Builder()
					.setMediaId(Integer.toString(MediaUtils.TYPE_PLAYLIST))
					.setTitle(getString(R.string.playlists))
					.setIconUri(Uri.parse("android.resource://" +
							"ch.blinkenlights.android.vanilla/drawable/ic_menu_music_library"))
					.setSubtitle(getString(R.string.playlists))
					.build(), MediaBrowser.MediaItem.FLAG_BROWSABLE
		));


		// Start a new MediaSession
		mSession = new MediaSession(this, "VanillaMediaBrowserService");
		setSessionToken(mSession.getSessionToken());
		mSession.setCallback(new MediaSessionCallback());
		mSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
		mSessionExtras = new Bundle();
		mSession.setExtras(mSessionExtras);

		// Register with the PlaybackService
		PlaybackService.addTimelineCallback(this);

		// Make sure the PlaybackService is running
		if(!PlaybackService.hasInstance()) {
			Thread t = new Thread(new Runnable() {
				@Override
				public void run() {
					PlaybackService.get(MirrorLinkMediaBrowserService.this);
				}
			});
			t.start();
		}

		mLooper = thread.getLooper();
		mHandler = new Handler(mLooper, this);

		updatePlaybackState(null);
	}

	@Override
	public int onStartCommand(Intent startIntent, int flags, int startId) {
		Log.d("VanillaMusic", "MediaBrowserService#onStartCommand");
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		Log.d("VanillaMusic", "MediaBrowserService#onDestroy");
		mServiceStarted = false;
		PlaybackService.removeTimelineCallback(this);
		mSession.release();
	}

	/**
	 * Helper class to encode/decode item references
	 * derived from queries in a string
	 */
	private static class MediaID {
    	// Separators used to build MediaIDs for the MediaBrowserService
		public static final String ID_TYPE_ROOT = Integer.toString(MediaUtils.TYPE_INVALID);
		public static final String MEDIATYPE_SEPARATOR = "/";
		public static final String FILTER_SEPARATOR = "#";

		final int mType;
		final long mId;
		final String mLabel;

		public MediaID(int type, long id, String label) {
			mType = type;
			mId = id;
			mLabel = label;
		}

		public MediaID(String mediaId) {
			int type = MediaUtils.TYPE_INVALID;
			long id = -1;
			String label = null;
			if(mediaId != null) {
				String[] items = mediaId.split(MEDIATYPE_SEPARATOR);
				type = items.length > 0 ? Integer.parseInt(items[0]) : MediaUtils.TYPE_INVALID;
				if(items.length > 1) {
					items = items[1].split(FILTER_SEPARATOR);
					if(items.length >= 2) {
						label = items[1];
						id = Long.parseLong(items[0]);
					}
				}
			}
			mType = type;
			mId = id;
			mLabel = label;
		}

		public boolean isTopAdapter() {
			return mId == -1;
		}

		public boolean isInvalid() {
			return mType == MediaUtils.TYPE_INVALID;
		}

		@Override
		public String toString() {
		   	return toString(mType, mId, mLabel);
		}

		public static boolean isTopAdapter(String mediaId) {
			return mediaId.indexOf(MEDIATYPE_SEPARATOR) == -1;
		}

		public static String toString(int type, long id, String label) {
		   	return Integer.toString(type)
			   							 + (id == -1 ? "" : (
											   MEDIATYPE_SEPARATOR
											 + id
											 + (label == null ? "" :
												   FILTER_SEPARATOR
												 + label
											   )
											 )
										   );
		}
	}

	private static Limiter buildLimiterFromMediaID(MediaID parent) {
		Limiter limiter = null;
		String[] fields;
		Object data;
		if(!parent.isInvalid() && !parent.isTopAdapter()) {
			switch(parent.mType) {
				case MediaUtils.TYPE_ARTIST:
					// expand using a album query limited by artist
					fields = new String[] { parent.mLabel };
					data = String.format("%s=%d", MediaStore.Audio.Media.ARTIST_ID, parent.mId);
					limiter = new Limiter(MediaUtils.TYPE_ARTIST, fields, data);
				break;
				case MediaUtils.TYPE_ALBUM:
					// expand using a song query limited by album
					fields = new String[] { parent.mLabel };
					data = String.format("%s=%d",  MediaStore.Audio.Media.ALBUM_ID, parent.mId);
					limiter = new Limiter(MediaUtils.TYPE_SONG, fields, data);
				break;
				case MediaUtils.TYPE_GENRE:
					// expand using an artist limiter by genere
					fields = new String[] { parent.mLabel };
					data = parent.mId;
					limiter = new Limiter(MediaUtils.TYPE_GENRE, fields, data);
				break;
				case MediaUtils.TYPE_PLAYLIST:
					// don't build much, a a playlist is playable but not expandable
				case MediaUtils.TYPE_SONG:
					// don't build much, a song is playable but not expandable
				case MediaUtils.TYPE_INVALID:
				break;
			}
		}
		return limiter;
	}

	private QueryTask buildQueryFromMediaID(MediaID parent, boolean empty, boolean all)
	{
		String[] projection;

		if (parent.mType == MediaUtils.TYPE_PLAYLIST) {
			projection = empty ? Song.EMPTY_PLAYLIST_PROJECTION : Song.FILLED_PLAYLIST_PROJECTION;
		} else {
			projection = empty ? Song.EMPTY_PROJECTION : Song.FILLED_PROJECTION;
		}

		QueryTask query;
		if (all && (parent.mType != MediaUtils.TYPE_PLAYLIST)) {
			query = (mMediaAdapters[parent.mType]).buildSongQuery(projection);
			query.data = parent.mId;
			query.mode = SongTimeline.MODE_PLAY_ID_FIRST;
		} else {
			query = MediaUtils.buildQuery(parent.mType, parent.mId, projection, null);
			query.mode = SongTimeline.MODE_PLAY;
		}

		return query;
	}

	private void loadChildrenAsync( final MediaID parent,
									final Result<List<MediaItem>> result) {

		// Asynchronously load the music catalog in a separate thread
		final Limiter limiter = buildLimiterFromMediaID(parent);
		new AsyncTask<Void, Void, Integer>() {
			private static final int ASYNCTASK_SUCCEEDED = 1;
			private static final int ASYNCTASK_FAILED = 0;

			@Override
			protected Integer doInBackground(Void... params) {
				int result = ASYNCTASK_FAILED;
				try {
					mQueryResult.clear();
					clearLimiters();
					if(parent.isTopAdapter()) {
						runQuery(mQueryResult, parent.mType, mMediaAdapters[parent.mType]);
					} else if (limiter != null) {
						switch(limiter.type) {
							case MediaUtils.TYPE_ALBUM:
								mSongAdapter.setLimiter(limiter);
								runQuery(mQueryResult, MediaUtils.TYPE_SONG, mSongAdapter);
							break;
							case MediaUtils.TYPE_ARTIST:
								mAlbumAdapter.setLimiter(limiter);
								runQuery(mQueryResult, MediaUtils.TYPE_ALBUM, mAlbumAdapter);
							break;
							case MediaUtils.TYPE_SONG:
								mSongAdapter.setLimiter(limiter);
								runQuery(mQueryResult, MediaUtils.TYPE_SONG, mSongAdapter);
							break;
							case MediaUtils.TYPE_PLAYLIST:
								mPlaylistAdapter.setLimiter(limiter);
								runQuery(mQueryResult, MediaUtils.TYPE_PLAYLIST, mPlaylistAdapter);
							break;
							case MediaUtils.TYPE_GENRE:
								mSongAdapter.setLimiter(limiter);
								runQuery(mQueryResult, MediaUtils.TYPE_SONG, mSongAdapter);
							break;
						}
					}
					result = ASYNCTASK_SUCCEEDED;
				} catch (Exception e) {
					Log.d("VanillaMusic","Failed retrieving Media");
				}
				return Integer.valueOf(result);
			}

			@Override
			protected void onPostExecute(Integer current) {
				List<MediaBrowser.MediaItem> items = null;
				if (result != null) {
					items = mQueryResult;
					if (current == ASYNCTASK_SUCCEEDED) {
						result.sendResult(items);
					} else {
						result.sendResult(Collections.<MediaItem>emptyList());
					}
				}
			}
		}.execute();
	}

	private void clearLimiters() {
		for(MediaAdapter adapter : mMediaAdapters) {
			adapter.setLimiter(null);
		}
	}


	private String  subtitleForMediaType(int mediaType) {
		switch(mediaType) {
			case MediaUtils.TYPE_ARTIST:
				return getString(R.string.artists);
			case MediaUtils.TYPE_SONG:
				return getString(R.string.songs);
			case MediaUtils.TYPE_PLAYLIST:
				return getString(R.string.playlists);
			case MediaUtils.TYPE_GENRE:
				return getString(R.string.genres);
			case MediaUtils.TYPE_ALBUM:
				return getString(R.string.albums);
		}
		return "";
	}

	private void runQuery(List<MediaBrowser.MediaItem> populateMe, int mediaType, MediaAdapter adapter) {
		populateMe.clear();
		try {
			Cursor cursor = adapter.query();
			Context context = getApplicationContext();
			ContentResolver resolver = context.getContentResolver();

			if (cursor == null) {
				return;
			}

			final int flags = (mediaType == MediaUtils.TYPE_SONG || mediaType == MediaUtils.TYPE_PLAYLIST) ? MediaBrowser.MediaItem.FLAG_PLAYABLE : MediaBrowser.MediaItem.FLAG_BROWSABLE;
			final int count = cursor.getCount();
			for (int j = 0; j != count; ++j) {
				cursor.moveToPosition(j);
				final String id = cursor.getString(0);
				final String label = cursor.getString(2);
				long mediaId = Long.parseLong(id);

				Song song = MediaUtils.getSongByTypeId(resolver, mediaType, mediaId);
				MediaBrowser.MediaItem item = new MediaBrowser.MediaItem(
					new MediaDescription.Builder()
						.setMediaId(MediaID.toString(mediaType, mediaId, label))
						.setTitle(label)
						.setSubtitle(subtitleForMediaType(mediaType))
						.setIconBitmap(song.getSmallCover(context))
						.build(),
						flags);
				populateMe.add(item);
			}

			cursor.close();
		} catch (Exception e) {
			Log.d("VanillaMusic","Failed retrieving Media");
		}
	}

	/*
	 ** MediaBrowserService APIs
	 */

	@Override
	public BrowserRoot onGetRoot(String clientPackageName, int clientUid, Bundle rootHints) {
		return new BrowserRoot(MediaID.ID_TYPE_ROOT, null);
	}

	@Override
	public void onLoadChildren(final String parentMediaId, final Result<List<MediaItem>> result) {
		// Use result.detach to allow calling result.sendResult from another thread:
		result.detach();
		if (!MediaID.ID_TYPE_ROOT.equals(parentMediaId)) {
			loadChildrenAsync(new MediaID(parentMediaId), result);
		} else {
			result.sendResult(mMediaRoot);
		}
	}

	private void setSessionActive() {
		if (!mServiceStarted) {
			// The MirrorLinkMediaBrowserService needs to keep running even after the calling MediaBrowser
			// is disconnected. Call startService(Intent) and then stopSelf(..) when we no longer
			// need to play media.
			startService(new Intent(getApplicationContext(), MirrorLinkMediaBrowserService.class));
			mServiceStarted = true;
		}

		if (!mSession.isActive()) {
			mSession.setActive(true);
		}
	}

	private void setSessionInactive() {
		if(mServiceStarted) {
			// service is no longer necessary. Will be started again if needed.
			MirrorLinkMediaBrowserService.this.stopSelf();
			mServiceStarted = false;
		}

		if(mSession.isActive()) {
			mSession.setActive(false);
		}
	}

	private static final int MSG_PLAY = 1;
	private static final int MSG_PLAY_QUERY = 2;
	private static final int MSG_PAUSE = 3;
	private static final int MSG_STOP = 4;
	private static final int MSG_SEEKTO = 5;
	private static final int MSG_NEXTSONG = 6;
	private static final int MSG_PREVSONG = 7;
	private static final int MSG_SEEKFW = 8;
	private static final int MSG_SEEKBW = 9;
	private static final int MSG_REPEAT = 10;
	private static final int MSG_SHUFFLE = 11;
	private static final int MSG_UPDATE_STATE = 12;

	@Override
	public boolean handleMessage(Message message)
	{
		switch (message.what) {
		case MSG_PLAY:
			setSessionActive();

			if(PlaybackService.hasInstance()) {
				PlaybackService.get(MirrorLinkMediaBrowserService.this).play();
			}
		break;
		case MSG_PLAY_QUERY:
			setSessionActive();
			if(PlaybackService.hasInstance()) {
				QueryTask query = buildQueryFromMediaID(new MediaID((String)message.obj), false, true);
				PlaybackService.get(MirrorLinkMediaBrowserService.this).addSongs(query);
			}
		break;
		case MSG_PAUSE:
			if(PlaybackService.hasInstance()) {
				PlaybackService.get(MirrorLinkMediaBrowserService.this).pause();
			}
		break;
		case MSG_STOP:
			if(PlaybackService.hasInstance()) {
				PlaybackService.get(MirrorLinkMediaBrowserService.this).pause();
			}
			setSessionInactive();
		break;
		case MSG_SEEKTO:
			if(PlaybackService.hasInstance()) {
				PlaybackService.get(MirrorLinkMediaBrowserService.this).seekToProgress(message.arg1);
			}
		break;
		case MSG_NEXTSONG:
			if(PlaybackService.hasInstance()) {
				PlaybackService.get(MirrorLinkMediaBrowserService.this).performAction(Action.NextSong, null);
			}
		break;
		case MSG_PREVSONG:
			if(PlaybackService.hasInstance()) {
				PlaybackService.get(MirrorLinkMediaBrowserService.this).performAction(Action.PreviousSong, null);
			}
		break;
		case MSG_SEEKFW:
			if(PlaybackService.hasInstance()) {
				PlaybackService.get(MirrorLinkMediaBrowserService.this).performAction(Action.SeekForward, null);
			}
		break;
		case MSG_SEEKBW:
			if(PlaybackService.hasInstance()) {
				PlaybackService.get(MirrorLinkMediaBrowserService.this).performAction(Action.SeekBackward, null);
			}
		break;
		case MSG_REPEAT:
			if(PlaybackService.hasInstance()) {
				PlaybackService.get(MirrorLinkMediaBrowserService.this).performAction(Action.Repeat, null);
			}
		break;
		case MSG_SHUFFLE:
			if(PlaybackService.hasInstance()) {
				PlaybackService.get(MirrorLinkMediaBrowserService.this).performAction(Action.Shuffle, null);
			}
		break;
		case MSG_UPDATE_STATE:
			updatePlaybackState((String)message.obj);
		break;
		default:
			return false;
		}

		return true;
	}
	/*
	 ** MediaSession.Callback
	 */
	private final class MediaSessionCallback extends MediaSession.Callback {

		@Override
		public void onPlay() {
			mHandler.sendEmptyMessage(MSG_PLAY);
		}

		@Override
		public void onSeekTo(long position) {
			mHandler.sendMessage(mHandler.obtainMessage(MSG_SEEKTO, (int) position ,0));
		}

		@Override
		public void onPlayFromMediaId(final String mediaId, Bundle extras) {
			mHandler.sendMessage(mHandler.obtainMessage(MSG_PLAY_QUERY, mediaId));
		}

		@Override
		public void onPause() {
			mHandler.sendEmptyMessage(MSG_PAUSE);
		}

		@Override
		public void onStop() {
			mHandler.sendEmptyMessage(MSG_STOP);
		}

		@Override
		public void onSkipToNext() {
			mHandler.sendEmptyMessage(MSG_NEXTSONG);
		}

		@Override
		public void onSkipToPrevious() {
			mHandler.sendEmptyMessage(MSG_PREVSONG);
		}

		@Override
		public void onFastForward() {
			mHandler.sendEmptyMessage(MSG_SEEKFW);
		}

		@Override
		public void onRewind() {
			mHandler.sendEmptyMessage(MSG_SEEKBW);
		}

		@Override
		public void onCustomAction(String action, Bundle extras) {
			if (CUSTOM_ACTION_REPEAT.equals(action)) {
				mHandler.sendEmptyMessage(MSG_REPEAT);
			} else if (CUSTOM_ACTION_SHUFFLE.equals(action)) {
				mHandler.sendEmptyMessage(MSG_SHUFFLE);
			}
		}
	}

	/**
	 * Update the current media player state, optionally showing an error message.
	 *
	 * @param error if not null, error message to present to the user.
	 */
	private void updatePlaybackState(String error) {
		long position = PlaybackState.PLAYBACK_POSITION_UNKNOWN;
		int state = PlaybackState.STATE_PAUSED;

		if(PlaybackService.hasInstance()) {
			if (PlaybackService.get(this).isPlaying()) {
				state = PlaybackState.STATE_PLAYING;
			}
			position = PlaybackService.get(this).getPosition();
		}

		PlaybackState.Builder stateBuilder = new PlaybackState.Builder()
				.setActions(getAvailableActions());

		setCustomAction(stateBuilder);

		// If there is an error message, send it to the playback state:
		if (error != null) {
			// Error states are really only supposed to be used for errors that cause playback to
			// stop unexpectedly and persist until the user takes action to fix it.
			stateBuilder.setErrorMessage(error);
			state = PlaybackState.STATE_ERROR;
		}
		stateBuilder.setState(state, position, 1.0f, SystemClock.elapsedRealtime());
		mSession.setPlaybackState(stateBuilder.build());

	}
	// 'DriveSafe' icons need to meet contrast requirement, and as such are usually
	// monochrome in nature, hence the new repeat_inactive_service and shuffle_inactive_service
	// artwork

	private static final int[] FINISH_ICONS =
		{	R.drawable.repeat_inactive_service
		  , R.drawable.repeat_active
		  , R.drawable.repeat_current_active
		  , R.drawable.stop_current_active
		  , R.drawable.random_active };

	private static final int[] SHUFFLE_ICONS =
		{ 	R.drawable.shuffle_inactive_service
		  , R.drawable.shuffle_active
		  , R.drawable.shuffle_active
		  , R.drawable.shuffle_album_active };

	private void setCustomAction(PlaybackState.Builder stateBuilder) {
		if(PlaybackService.hasInstance()) {
			Bundle customActionExtras = new Bundle();
			final int finishMode = PlaybackService.finishAction(PlaybackService.get(this).getState());
			final int shuffleMode = PlaybackService.shuffleMode(PlaybackService.get(this).getState());

			stateBuilder.addCustomAction(new PlaybackState.CustomAction.Builder(
					CUSTOM_ACTION_REPEAT, getString(R.string.cycle_repeat_mode), FINISH_ICONS[finishMode])
					.setExtras(customActionExtras)
					.build());

			stateBuilder.addCustomAction(new PlaybackState.CustomAction.Builder(
					CUSTOM_ACTION_SHUFFLE, getString(R.string.cycle_shuffle_mode), SHUFFLE_ICONS[shuffleMode])
					.setExtras(customActionExtras)
					.build());
		}
	}

	private long getAvailableActions() {
		long actions =   PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PLAY_FROM_MEDIA_ID
					   | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SKIP_TO_NEXT;

		if(PlaybackService.hasInstance()) {
			if (PlaybackService.get(this).isPlaying()) {
				actions |= PlaybackState.ACTION_PAUSE;
				actions |= PlaybackState.ACTION_FAST_FORWARD;
				actions |= PlaybackState.ACTION_REWIND;
			}
		}
		return actions;
	}

	/**
	 * Implementation of the PlaybackService callbacks
	 */
	public void onTimelineChanged() {
		mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_STATE, null));
	}

	public void setState(long uptime, int state) {
		mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_STATE, null));
	}

	public void replaceSong(int delta, Song song) {
	}

	public void onMediaChange() {
	}

	public void recreate() {
	}

	public void setSong(long uptime, Song song) {
		mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_STATE, null));
		if(song == null) {
			if(PlaybackService.hasInstance()) {
				song = PlaybackService.get(this).getSong(0);
			}
		}

		if(song != null) {
			MediaMetadata metadata = new MediaMetadata.Builder()
				.putString(MediaMetadata.METADATA_KEY_MEDIA_ID, Long.toString(song.id))
				.putString(MediaMetadata.METADATA_KEY_ALBUM, song.album)
				.putString(MediaMetadata.METADATA_KEY_ARTIST, song.artist)
				.putLong(MediaMetadata.METADATA_KEY_DURATION, song.duration)
				.putString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI, "content://media/external/audio/media/" + Long.toString(song.id) + "/albumart")
				.putString(MediaMetadata.METADATA_KEY_TITLE, song.title)
				.putLong(MediaMetadata.METADATA_KEY_TRACK_NUMBER, song.trackNumber)
				.build();
			mSession.setMetadata(metadata);
		}
	}

	public void onPositionInfoChanged() {
		mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_STATE, null));
		// updatePlaybackState(null);
	}

	public void onError(String error) {
		mHandler.sendMessage(mHandler.obtainMessage(MSG_UPDATE_STATE, error));
		// updatePlaybackState(error);
	}

	public void onMediaChanged() {
		if(PlaybackService.hasInstance()) {
			setSong(0,PlaybackService.get(this).getSong(0));
		}

	}
}
