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
	 * The string (but also valid path!) to use to indicate the current directory
	 */
	public static final String NAME_CURRENT_FOLDER = ".";
	/**
	 * The string (but also valid path!) to use to indicate the parent directory
	 */
	public static final String NAME_PARENT_FOLDER = "..";

	private FileUtils() {
		// Private constructor to hide implicit one.
	}

	/**
	 * Converts common path separator characters ('/' or '\\') to the value of File.separatorChar.
	 *
	 * @param path The path to convert directory separators within.
	 * @return A path with all valid separators normalized to the system's.
	 */
	public static String normalizeDirectorySeparators(String path) {
		final StringBuilder sb = new StringBuilder(path);

		for (int i = 0; i < sb.length(); i++) {
			final char originalChar = sb.charAt(i);
			if (originalChar == '/' || originalChar == '\\') {
				sb.setCharAt(i, File.separatorChar);
			}
		}
		return sb.toString();
	}

	/**
	 * Utility function to prepend n-parent directory traversals to a path.
	 *
	 * @param path A relative path to traverse upwards from within.
	 * @param traversalCount The number of directories to navigate upwards.
	 * @return The result of creating the relative path.
	 */
	private static String traversePathUpwards(String path, int traversalCount) {
		if (traversalCount < 0)
			throw new IllegalArgumentException("Cannot descend into the directory structure.");

		if (traversalCount == 0)
			return path;

		StringBuilder sb = new StringBuilder((FileUtils.NAME_PARENT_FOLDER.length() + 1) * traversalCount +
			path.length());

		for (int i = 0; i < traversalCount; i++) {
			sb.append(FileUtils.NAME_PARENT_FOLDER);
			sb.append(File.separatorChar);
		}
		sb.append(path);
		return sb.toString();
	}

	/**
	 * Attempts to generate a relative path from a source File to a destination.
	 * Will attempt to perform directory traversal to find a common path.
	 * Upon failure to relativize, returns the original destination.
	 *
	 * @param source The File the path is relative to e.g. /a/b/c/
	 * @param destination The File the path is pointing to e.g. /a/b/c/d.mp3
	 * @return A relative or absolute path pointing to the destination.
	 */
	public static String relativize(File source, File destination) {
		if (destination.isAbsolute() != source.isAbsolute())
			return destination.getPath();

		File commonParentFile = source;
		final String destinationPath = destination.getPath();
		int traversalCount = 0;

		if (commonParentFile.equals(destination)) {
			return FileUtils.NAME_CURRENT_FOLDER;
		}

		do {
			String parentPath = commonParentFile.getPath();
			if (!parentPath.endsWith(File.separator)) {
				parentPath += File.separatorChar;
			}

			if (destinationPath.startsWith(parentPath)) {
				return traversePathUpwards(
					destinationPath.substring(parentPath.length()),
					traversalCount);
			}
			++traversalCount;
		} while ((commonParentFile = commonParentFile.getParentFile()) != null);
		return destination.getPath();
	}

	/**
	 * Attempts to resolve a path from a base, if the destination is relative.
	 * Upon failure to resolve, returns the original destination.
	 *
	 * @param source The File the path may be relative to e.g. /a/b/c/
	 * @param destination The File the path is pointing to e.g. /a/b/c/d.mp3
	 * @return A relative or absolute path pointing to the destination.
	 */
	public static String resolve(File source, File destination) {
		String path;

		try {
			if (destination.isAbsolute()) {
				path = destination.getPath();
			} else {
				path = new File(source,
						destination.getPath())
					.getAbsolutePath();
			}
		} catch (SecurityException ex) {
			path = destination.getPath();
		}
		return path;
	}


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
		SharedPreferences prefs = SharedPrefHelper.getSettings(context);
		String folder = prefs.getString(PrefKeys.FILESYSTEM_BROWSE_START, PrefDefaults.FILESYSTEM_BROWSE_START);
		return new File( folder.equals("") ? Environment.getExternalStorageDirectory().getAbsolutePath() : folder );
	}

	/**
	 * Return the file extension for a given filename (including dot).
	 * Empty string is returned if there is no extension.
	 */
	public static String getFileExtension(String filename) {
		int index = filename.lastIndexOf('.');
		return index > 0 ? filename.substring(index) : "";
	}
}
