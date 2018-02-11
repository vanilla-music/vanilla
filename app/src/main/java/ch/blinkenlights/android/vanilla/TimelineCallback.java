/*
 * Copyright (C) 2016 Adrian Ulrich <adrian@blinkenlights.ch>
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

public interface TimelineCallback {
	/**
	 * Called when the song timeline position/size has changed
	 */
	void onPositionInfoChanged();
	/**
	 * The library contents changed and should be invalidated
	 */
	void onMediaChange();
	/**
	 * Notification about a change in the timeline
	 */
	void onTimelineChanged();
	/**
	 * Updates song at 'delta'
	 */
	void replaceSong(int delta, Song song);
	/**
	 * Sets the currently active song
	 */
	void setSong(long uptime, Song song);
	/**
	 * Sets the current playback state
	 */
	void setState(long uptime, int state);
	/**
	 * The view/activity should re-create itself due to a theme change
	 */
	void recreate();
}
