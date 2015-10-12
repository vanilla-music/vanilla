/*
 * Copyright (C) 2015 Adrian Ulrich <adrian@blinkenlights.ch>
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
 * SharedPreference default values. Must be kept in sync with keys in res/xml/prefs_*.xml.
 */
public class PrefDefaults {
	public static final Action  COVER_LONGPRESS_ACTION = Action.PlayPause;
	public static final Action  COVER_PRESS_ACTION = Action.ToggleControls;
	public static final String  DEFAULT_ACTION_INT = "7";
	public static final String  DEFAULT_PLAYLIST_ACTION = "0";
	public static final boolean COVERLOADER_ANDROID = true;
	public static final boolean COVERLOADER_VANILLA = true;
	public static final boolean COVERLOADER_SHADOW = true;
	public static final boolean COVER_ON_LOCKSCREEN = true;
	public static final boolean DISABLE_LOCKSCREEN = false;
	public static final String DISPLAY_MODE = "2";
	public static final boolean DOUBLE_TAP = false;
	public static final boolean ENABLE_SHAKE = false;
	public static final boolean HEADSET_ONLY = false;
	public static final boolean CYCLE_CONTINUOUS_SHUFFLING = false;
	public static final boolean HEADSET_PAUSE = true;
	public static final int     IDLE_TIMEOUT = 3600;
	public static final int     LIBRARY_PAGE = 0;
	public static final boolean MEDIA_BUTTON = true;
	public static final boolean MEDIA_BUTTON_BEEP = true;
	public static final String  NOTIFICATION_ACTION = "0";
	public static final String  NOTIFICATION_MODE = "1";
	public static final boolean NOTIFICATION_NAG = false;
	public static final boolean PLAYBACK_ON_STARTUP = false;
	public static final boolean SCROBBLE = false;
	public static final Action  SHAKE_ACTION = Action.NextSong;
	public static final int     SHAKE_THRESHOLD = 80;
	public static final boolean STOCK_BROADCAST = false;
	public static final Action  SWIPE_DOWN_ACTION = Action.Nothing;
	public static final Action  SWIPE_UP_ACTION = Action.Nothing;
	public static final String  TAB_ORDER = null;
	public static final boolean USE_IDLE_TIMEOUT = false;
	public static final boolean VISIBLE_CONTROLS = true;
	public static final boolean VISIBLE_EXTRA_INFO = false;
	public static final boolean ENABLE_TRACK_REPLAYGAIN = false;
	public static final boolean ENABLE_ALBUM_REPLAYGAIN = false;
	public static final int     REPLAYGAIN_BUMP = 75; // seek bar is 150 -> 75 == middle == 0
	public static final int     REPLAYGAIN_UNTAGGED_DEBUMP = 150; // seek bar is 150 -> == 0
	public static final boolean ENABLE_READAHEAD = false;
	public static final boolean USE_DARK_THEME = false;
	public static final String  FILESYSTEM_BROWSE_START = "";
	public static final int     VOLUME_DURING_DUCKING = 50;
	public static final int     AUTOPLAYLIST_PLAYCOUNTS = 0;
}
