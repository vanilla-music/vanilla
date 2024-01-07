/*
 * Copyright (C) 2018 Adrian Ulrich <adrian@blinkenlights.ch>
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

package ch.blinkenlights.android.medialibrary;

/**
 * Receives callbacks if the media library changes
 */
public class LibraryObserver {
	/**
	 * Type of the event, to be used as a hint
	 * by the receiver
	 */
	public enum Type {
		SONG,          // Change affected song items.
		PLAYLIST,      // Change affected playlists.
		SCAN_PROGRESS, // Information about an ongoing scan.
	}
	/**
	 * Special hint values
	 */
	public class Value {
		public static final int UNKNOWN = -1;  // The exact id of the changed object is not know, may have affected all items.
		public static final int OUTDATED = -2; // Everything you know is wrong: Cached data must not be used nor trusted.
	}

	/**
	 * Return new LibraryObserver object.
	 */
	public LibraryObserver() {
	}

	/**
	 * Called if there was a change, expected
	 * to be overriden by the registered observer.
	 *
	 * @param type one of LibraryObserver.Type
	 * @param id hint of which id changed for type, -1 if unspecified.
	 * @param ongoing whether or not to expect more events soon.
	 */
	public void onChange(Type type, long id, boolean ongoing) {
		// NOOP, should be overriden.
	}
}
