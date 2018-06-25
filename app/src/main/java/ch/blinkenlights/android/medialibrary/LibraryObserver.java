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
		ANY,         // Any type may have changed
		SONG,        // Change only affected song entries
		PLAYLIST,    // Change only affected playlists
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
	 * @param ongoing whether or not to expect more events soon.
	 */
	public void onChange(Type type, boolean ongoing) {
		// NOOP, should be overriden.
	}
}
