/*
 * Copyright (C) 2013-2017 Adrian Ulrich <adrian@blinkenlights.ch>
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

import android.content.Context;
import android.app.Activity;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.ImageView;
import android.graphics.drawable.Drawable;

import java.io.File;
import java.util.Arrays;
import java.util.ArrayList;

public class FolderPickerAdapter
	extends ArrayAdapter<FolderPickerAdapter.Item>
{

	public static class Item {
		String name;
		File file;
		int color;
		public Item(String name, File file, int color) {
			this.name = name;
			this.file = file;
			this.color = color;
		}
	}

	/**
	 * Our layout inflater instance
	 */
	private final LayoutInflater mInflater;
	/**
	 * The external storage directory as reported by the OS
	 */
	final private File mStorageDir;
	/**
	 * The filesystem root
	 */
	final private File mFsRoot = new File("/");
	/**
	 * The currently set directory
	 */
	private File mCurrentDir;
	/**
	 * A list of paths marked as 'included'
	 */
	private ArrayList<String> mIncludedDirs = new ArrayList<String>();
	/**
	 * A list of paths marked as 'excluded'
	 */
	private ArrayList<String> mExcludedDirs = new ArrayList<String>();


	public FolderPickerAdapter(Context context, int resource) {
		super(context, resource);
		mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		mStorageDir = Environment.getExternalStorageDirectory();
		mCurrentDir = mStorageDir;
	}

	@Override
	public View getView(int pos, View convertView, ViewGroup parent) {
		DraggableRow row;

		if (convertView == null) {
			row = (DraggableRow)mInflater.inflate(R.layout.draggable_row, parent, false);
			row.setupLayout(DraggableRow.LAYOUT_LISTVIEW);
			row.getCoverView().setImageResource(R.drawable.folder);

		} else {
			row = (DraggableRow)convertView;
		}

		Item item = (Item)getItem(pos);
		int res = (item.file == null ? R.drawable.arrow_up : R.drawable.folder);
		row.setText(item.name);
		row.getCoverView().setImageResource(res);
		row.getCoverView().setColorFilter(item.color);
		return row;
	}

	/**
	 * Returns the currently set directory
	 */
	public File getCurrentDir() {
		return mCurrentDir;
	}

	/**
	 * Changes the currently active directory
	 */
	public void setCurrentDir(File dir) {
		mCurrentDir = dir;
		refresh();
	}

	/**
	 * Returns the list of included directories
	 *
	 * @return arraylist - may be null
	 */
	public ArrayList<String> getIncludedDirs() {
		return mIncludedDirs;
	}

	/**
	 * Sets the included dirlist
	 *
	 * @param list the arraylist to use
	 */
	public void setIncludedDirs(ArrayList<String> list) {
		mIncludedDirs = verifyDirs(list);
		refresh();
	}

	/**
	 * Returns the list of excluded directories
	 *
	 * @return arraylist - may be null
	 */
	public ArrayList<String> getExcludedDirs() {
		return mExcludedDirs;
	}

	/**
	 * Sets the excluded dirlist
	 *
	 * @param list the arraylist to use
	 */
	public void setExcludedDirs(ArrayList<String> list) {
		mExcludedDirs = verifyDirs(list);
		refresh();
	}

	/**
	 * Returns list, weeding out non-existing or invalid dirs
	 *
	 * @param list the list to check
	 * @return list the checked list
	 */
	private ArrayList<String> verifyDirs(ArrayList<String> list) {
		ArrayList<String> result = new ArrayList<String>();
		for (String path : list) {
			File file = new File(path);
			if (file.isDirectory())
				result.add(path);
		}
		return result;
	}

	/**
	 * Refreshes the current ArrayList
	 */
	private void refresh() {
		File path = mCurrentDir;
		File[]dirs = path.listFiles();

		clear();

		if (!mFsRoot.equals(path))
			add(new FolderPickerAdapter.Item("..", null, 0));

		// Hack alert: Android >= 6.0's default storage root directory
		// is usually not readable. That's not a big issue but
		// can be very annoying for users who browse around.
		// We are therefore detecting this and will 'simulate'
		// the existence of the default storage root.
		if (dirs == null && mStorageDir.getParentFile().equals(path)) {
			dirs = new File[] { mStorageDir };
		}

		if (dirs != null) {
			Arrays.sort(dirs);
			for(File fentry: dirs) {
				if(fentry.isDirectory()) {
					int color = 0;
					if (mIncludedDirs.contains(fentry.getAbsolutePath()))
						color = 0xff00c853;
					if (mExcludedDirs.contains(fentry.getAbsolutePath()))
						color = 0xffd50000;
					Item item = new Item(fentry.getName(), fentry, color);
					add(item);
				}
			}
		}
	}
}
