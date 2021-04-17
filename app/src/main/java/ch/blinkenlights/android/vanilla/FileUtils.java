/*
 * Copyright (C) 2015-2020 Adrian Ulrich <adrian@blinkenlights.ch>
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

import java.io.File;
import java.net.URI;
import java.net.URLConnection;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.StringTokenizer;

import java.nio.file.Path;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;
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
	 * Attempts to generate a relative path from a base, to a destination.
	 * Will attempt to perform directory traversal to find a common path.
	 * Upon failure to relativize, returns the original destination.
	 *
	 * @param base The File the path is relative to e.g. /a/b/c/
	 * @param destination The File the resultant path should point to e.g. /a/b/c/d.mp3
	 * @return A relative or absolute path pointing to the destination.
	 */
	public static String relativize(File base, File destination) {
		if (destination.isAbsolute() != base.isAbsolute())
			return destination.getPath();

		File commonParentFile = base;
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
	 * @param base The File the path may be relative to e.g. /a/b/c/
	 * @param destination The File the resultant path should point to e.g. d.mp3
	 * @return A relative or absolute path pointing to the destination.
	 */
	public static String resolve(File base, File destination) {
		String path = destination.getPath();

		try {
			if (!destination.isAbsolute()) {
				path = new File(base, path).getAbsolutePath();
				final ArrayList<String> pathComponents = new ArrayList<>();
				final StringTokenizer pathTokenizer = new StringTokenizer(path, File.separator);

				while (pathTokenizer.hasMoreTokens()) {
					final String pathComponent = pathTokenizer.nextToken();

					if (NAME_PARENT_FOLDER.equals(pathComponent)) {
						final int lastComponentPosition = pathComponents.size() - 1;

						if (lastComponentPosition >= 0
							&& !NAME_PARENT_FOLDER.equals(pathComponents.get(lastComponentPosition))) {
							pathComponents.remove(lastComponentPosition);
						}
					} else {
						pathComponents.add(pathComponent);
					}
				}
				path = File.separator + TextUtils.join(File.separator, pathComponents);
			}
		} catch (SecurityException ex) {
			// Ignore.
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
		if (type == MediaUtils.TYPE_FILE && !isFolder) {
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

		if (folder.equals("")) {
			folder = Environment.getExternalStorageDirectory().getAbsolutePath();
		}
		return new File(folder);
	}

	/**
	 * Return the file extension for a given filename (including dot).
	 * Empty string is returned if there is no extension.
	 */
	public static String getFileExtension(String filename) {
		int index = filename.lastIndexOf('.');
		return index > 0 ? filename.substring(index) : "";
	}

	/**
	 * Returns a list of directores contained in 'dir' which are very likely to exist, based
	 * on what Android told us about the existence of external media dirs.
	 * This is required as users otherwise may end up in folders they can not navigate out of.
	 */
	public static ArrayList<File> getFallbackDirectories(Context context, File dir) {
		HashSet<File> result = new HashSet<>();

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			Path prefix = dir.toPath();
			for (File f : context.getExternalMediaDirs()) {
				Path p = f.toPath();
				if (p.getNameCount() <= prefix.getNameCount())
					continue;
				if (!p.startsWith(prefix))
					continue;
				Path sp = p.subpath(prefix.getNameCount(), prefix.getNameCount()+1);
				result.add(new File(dir, sp.toString()));
			}
		} else {
			// java.nio.Paths was only added in API 26 *sigh*.
			switch (dir.toString()) {
			case "/":
				result.add(new File("/storage"));
				break;
			case "/storage/emulated":
				result.add(new File("/storage/emulated/0"));
				break;
			}
		}
		return new ArrayList<File>(result);
	}

	/**
	 * Returns the ID which the media library uses for a file.
	 *
	 * @param the file to get the id for.
	 * @return the id this file would have in the library.
	 */
	public static long songIdFromFile(File file) {
		return MediaLibrary.hash63(file.getAbsolutePath());
	}
}
