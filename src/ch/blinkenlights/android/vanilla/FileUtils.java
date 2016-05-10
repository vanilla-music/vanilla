/*
 * Copyright (C) 2015-2016 Adrian Ulrich <adrian@blinkenlights.ch>
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

import java.io.File;
import java.net.URI;
import java.net.URLConnection;
import java.net.URISyntaxException;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;


/**
 * Provides some static File-related utility functions.
 */
public class FileUtils {

	/**
	 * Checks if dispatching this intent to an external application makes sense
	 *
	 * @param intent The intent to examine
	 * @return bool true if the intent could be dispatched
	 */
	public static boolean canDispatchIntent(Intent intent) {
		boolean canDispatch = false;

		int type = intent.getIntExtra(LibraryAdapter.DATA_TYPE, MediaUtils.TYPE_INVALID);
		boolean isFolder = intent.getBooleanExtra(LibraryAdapter.DATA_EXPANDABLE, false);
		String path = intent.getStringExtra(LibraryAdapter.DATA_FILE);
		if (type == MediaUtils.TYPE_FILE && isFolder == false) {
			try {
				URI uri = new URI("file", path, null);
				String mimeGuess = URLConnection.guessContentTypeFromName(uri.toString());
				if (mimeGuess != null && mimeGuess.matches("^(image|text)/.+")) {
					canDispatch = true;
				}
			} catch (URISyntaxException e) {
				Log.e("VanillaMusic", "failed to encode "+path+": "+e);
			}
		}
		return canDispatch;
	}

	/**
	 * Opens an intent in an external application
	 *
	 * @param activity The library activity to use
	 * @param intent The intent to examine and launch
	 * @return bool true if the intent was dispatched
	 */
	public static boolean dispatchIntent(LibraryActivity activity, Intent intent) {
		boolean handled = true;

		String path = intent.getStringExtra(LibraryAdapter.DATA_FILE);
		String mimeGuess = URLConnection.guessContentTypeFromName(path);
		File file = new File(path);
		Uri uri = Uri.fromFile(file);

		Intent extView = new Intent(Intent.ACTION_VIEW);
		extView.setDataAndType(uri, mimeGuess);
		try {
			activity.startActivity(extView);
		} catch (Exception ActivityNotFoundException) {
			handled = false;
		}
		return handled;
	}

	/**
	 * Called by FileSystem adapter to get the start folder
	 * for browsing directories
	 */
	public static File getFilesystemBrowseStart(Context context) {
		SharedPreferences prefs = PlaybackService.getSettings(context);
		String folder = prefs.getString(PrefKeys.FILESYSTEM_BROWSE_START, PrefDefaults.FILESYSTEM_BROWSE_START);
		return new File( folder.equals("") ? Environment.getExternalStorageDirectory().getAbsolutePath() : folder );
	}
}
