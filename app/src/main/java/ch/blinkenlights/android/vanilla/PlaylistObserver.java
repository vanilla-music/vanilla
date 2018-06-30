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

package ch.blinkenlights.android.vanilla;

import ch.blinkenlights.android.medialibrary.MediaLibrary;
import ch.blinkenlights.android.medialibrary.LibraryObserver;

import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;

import java.io.File;
import android.util.Log;


public class PlaylistObserver implements Handler.Callback {
	/**
	 * Handler thread used to perform playlist sync.
	 */
	private Handler mHandler;
	/**
	 * Directory which holds observed playlists.
	 */
	private File mPlaylists = new File(Environment.getExternalStorageDirectory(), "Playlists");


	public PlaylistObserver() {
		// Create thread which will be used for background work.
		HandlerThread handlerThread = new HandlerThread("PlaylistWriter", Process.THREAD_PRIORITY_LOWEST);
		handlerThread.start();
		mHandler = new Handler(handlerThread.getLooper(), this);
		// Register to receive media library events.
		MediaLibrary.registerLibraryObserver(mObserver);
	}

	/**
	 * Unregisters this observer, the object must not be used anymore
	 * after this function was called.
	 */
	public void unregister() {
		MediaLibrary.unregisterLibraryObserver(mObserver);
	}

	@Override
	public boolean handleMessage(Message message) {
		return true;
	}

	/**
	 * Library observer callback which notifies us about media library
	 * events.
	 */
	private final LibraryObserver mObserver = new LibraryObserver() {
		@Override
		public void onChange(LibraryObserver.Type type, long id, boolean ongoing) {
			Log.v("VanillaMusic", "onChange type = "+type+", id = "+id+" ongoing = "+ongoing);
		}
	};

	// TODO:
	// Use FileObserver to track playlist changes?
	// how do we check modifications? write a shadow-dir in private app storage with same mtimes?
}
