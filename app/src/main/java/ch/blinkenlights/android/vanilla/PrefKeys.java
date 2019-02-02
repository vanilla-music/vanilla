/*
 * Copyright (C) 2012-2015 Adrian Ulrich <adrian@blinkenlights.ch>
 * Copyright (C) 2012 Christopher Eby <kreed@kreed.org>
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

/**
 * SharedPreference keys. Must be kept in sync with {@link PrefDefaults}.
 */
public class PrefKeys {
	private PrefKeys() {
		// Private constructor to hide implicit one.
	}

	public static final String COVER_LONGPRESS_ACTION = "cover_longpress_action";
	public static final String COVER_PRESS_ACTION = "cover_press_action";
	public static final String DEFAULT_ACTION_INT = "default_action_int";
	public static final String DEFAULT_PLAYLIST_ACTION = "default_playlist_action";
	public static final String COVERLOADER_ANDROID = "coverloader_android";
	public static final String COVERLOADER_VANILLA = "coverloader_vanilla";
	public static final String COVERLOADER_SHADOW = "coverloader_shadow";
	public static final String COVERLOADER_INLINE = "coverloader_inline";
	public static final String COVER_ON_LOCKSCREEN = "cover_on_lockscreen";
	public static final String DISABLE_LOCKSCREEN = "disable_lockscreen";
	public static final String DISPLAY_MODE = "display_mode";
	public static final String DOUBLE_TAP = "double_tap";
	public static final String ENABLE_SHAKE = "enable_shake";
	public static final String HEADSET_ONLY = "headset_only";
	public static final String HEADSET_PAUSE = "headset_pause";
	public static final String IDLE_TIMEOUT = "idle_timeout";
	public static final String LIBRARY_PAGE = "library_page";
	public static final String MEDIA_BUTTON = "media_button";
	public static final String MEDIA_BUTTON_BEEP = "media_button_beep";
	public static final String NOTIFICATION_ACTION = "notification_action";
	public static final String NOTIFICATION_VISIBILITY = "notification_visibility";
	public static final String NOTIFICATION_NAG = "notification_nag";
	public static final String PLAYBACK_ON_STARTUP = "playback_on_startup";
	public static final String SCROBBLE = "scrobble";
	public static final String SHAKE_ACTION = "shake_action";
	public static final String SHAKE_THRESHOLD = "shake_threshold";
	public static final String STOCK_BROADCAST = "stock_broadcast";
	public static final String SWIPE_DOWN_ACTION = "swipe_down_action";
	public static final String SWIPE_UP_ACTION = "swipe_up_action";
	public static final String TAB_ORDER = "tab_order";
	public static final String USE_IDLE_TIMEOUT = "use_idle_timeout";
	public static final String VISIBLE_CONTROLS = "visible_controls";
	public static final String VISIBLE_EXTRA_INFO = "visible_extra_info";
	public static final String ENABLE_TRACK_REPLAYGAIN = "enable_track_replaygain";
	public static final String ENABLE_ALBUM_REPLAYGAIN = "enable_album_replaygain";
	public static final String REPLAYGAIN_BUMP = "replaygain_bump";
	public static final String REPLAYGAIN_UNTAGGED_DEBUMP = "replaygain_untagged_debump";
	public static final String ENABLE_READAHEAD = "enable_readahead";
	public static final String SELECTED_THEME = "selected_theme";
	public static final String FILESYSTEM_BROWSE_START = "filesystem_browse_start";
	public static final String VOLUME_DURING_DUCKING = "volume_during_ducking";
	public static final String AUTOPLAYLIST_PLAYCOUNTS = "playcounts_autoplaylist";
	public static final String IGNORE_AUDIOFOCUS_LOSS = "ignore_audiofocus_loss";
	public static final String ENABLE_SCROLL_TO_SONG = "enable_scroll_to_song";
	public static final String QUEUE_ENABLE_SCROLL_TO_SONG = "queue_enable_scroll_to_song";
	public static final String KEEP_SCREEN_ON = "keep_screen_on";
	public static final String PLAYLIST_SYNC_MODE = "playlist_sync_mode";
	public static final String PLAYLIST_SYNC_FOLDER = "playlist_sync_folder";
	public static final String PLAYLIST_EXPORT_RELATIVE_PATHS = "playlist_export_relative_paths";
	public static final String JUMP_TO_ENQUEUED_ON_PLAY = "jump_to_enqueued_on_play";
}
