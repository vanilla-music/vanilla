/*
 * Copyright (C) 2017 Adrian Ulrich <adrian@blinkenlights.ch>
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

import android.os.Bundle;
import android.content.SharedPreferences;

import java.io.File;
import java.util.ArrayList;

public class FilebrowserStartActivity extends FolderPickerActivity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle(R.string.filebrowser_start);

		// Make sure that we display the current selection
		File startPath = FileUtils.getFilesystemBrowseStart(this);
		setCurrentDir(startPath);
	}


	@Override
	public void onFolderPicked(File directory, ArrayList<String> a, ArrayList<String> b) {
		SharedPreferences.Editor editor = SharedPrefHelper.getSettings(this).edit();
		editor.putString(PrefKeys.FILESYSTEM_BROWSE_START, directory.getAbsolutePath());
		editor.apply();
		finish();
	}

}
