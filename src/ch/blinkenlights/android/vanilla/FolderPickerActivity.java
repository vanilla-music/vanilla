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

import java.util.Arrays;
import java.util.ArrayList;
import java.io.File;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.MenuItem;
import android.view.Menu;
import android.widget.AdapterView;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.mobeta.android.dslv.DragSortListView;

public abstract class FolderPickerActivity extends Activity
	implements AdapterView.OnItemClickListener,
	           AdapterView.OnItemLongClickListener
{

	/**
	 * The path we should currently display
	 */
	private File mCurrentPath;
	/**
	 * Our listview
	 */
	private DragSortListView mListView;
	/**
	 * View displaying the current path
	 */
	private TextView mPathDisplay;
	/**
	 * Save button
	 */
	private Button mSaveButton;
	/**
	 * The array adapter of our listview
	 */
	private FolderPickerAdapter mListAdapter;
	/**
	 * True if folder-tri-state selection mode
	 * is enabled
	 */
	private boolean mTritastic;
	/**
	 * List of included dirs in tristate mode
	 */
	private ArrayList<String> mIncludedDirs;
	/**
	 * List of excluded dirs in tristate mode
	 */
	private ArrayList<String> mExcludedDirs;

	@Override  
	public void onCreate(Bundle savedInstanceState) {
		ThemeHelper.setTheme(this, R.style.BackActionBar);
		super.onCreate(savedInstanceState);

		setContentView(R.layout.folderpicker_content);

		mCurrentPath = new File("/");
		mListAdapter = new FolderPickerAdapter(this, 0);
		mPathDisplay = (TextView) findViewById(R.id.path_display);
		mListView    = (DragSortListView)findViewById(R.id.list);
		mSaveButton  = (Button) findViewById(R.id.save_button);

		mListView.setAdapter(mListAdapter);
		mListView.setOnItemClickListener(this);

		mSaveButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				onFolderPicked(mCurrentPath, mIncludedDirs, mExcludedDirs);
			}});
		// init defaults
		enableTritasticSelect(false, null, null);
	}

	/**
	 * Called after a folder was selected
	 *
	 * @param directory the selected directory
	 * @param included unique list of included directories in tristastic mode
	 * @param excluded unique list of excluded directories in triatastic mode
	 */
	public abstract void onFolderPicked(File directory, ArrayList<String> included, ArrayList<String> excluded);

	/**
	 * Enables tritastic selection, that is: user can select each
	 * directory to be in included, excluded or neutral state.
	 *
	 * @param enabled enables or disables this feature
	 * @param included initial list of included dirs
	 * @param excluded initial list of excluded dirs
	 */
	public void enableTritasticSelect(boolean enabled, ArrayList<String> included, ArrayList<String> excluded) {
		mTritastic = enabled;
		mIncludedDirs = (enabled ? included : null);
		mExcludedDirs = (enabled ? excluded : null);
		mListView.setOnItemLongClickListener(enabled ? this : null);
		mSaveButton.setText(enabled ? R.string.save : R.string.select);

		if (enabled)
			Toast.makeText(this, R.string.hint_long_press_to_modify_folder, Toast.LENGTH_SHORT).show();
	}

	/**
	 * Jumps to given directory
	 *
	 * @param directory the directory to jump to
	 */
	void setCurrentDirectory(File directory) {
		mCurrentPath = directory;
		refreshDirectoryList(true);
	}

	/**
	 * Called when we are displayed (again)
	 * This will always refresh the whole song list
	 */
	@Override
	public void onResume() {
		super.onResume();
		refreshDirectoryList(false);
	}
	
	/**
	 * Create a bare-bones actionbar
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if (item.getItemId() == android.R.id.home) {
			finish();
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}

	/**
	 * Called if user taps a row
	 */
	@Override
	public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
		FolderPickerAdapter.Item item = mListAdapter.getItem(pos);
		File newPath = null;

		if(pos == 0) {
			newPath = mCurrentPath.getParentFile();
		}
		else {
			newPath = new File(mCurrentPath, item.name);
		}

		if (newPath != null)
			setCurrentDirectory(newPath);
	}

	/**
	 * Called on long-click on a row
	 */
	@Override
	public boolean onItemLongClick(AdapterView<?> parent, View view, int pos, long id) {
		FolderPickerAdapter.Item item = mListAdapter.getItem(pos);

		if (item.file == null)
			return false;

		final String path = item.file.getAbsolutePath();
		final CharSequence[] options = new CharSequence[]{
			getResources().getString(R.string.folder_include),
			getResources().getString(R.string.folder_exclude),
			getResources().getString(R.string.folder_neutral)
		};

		AlertDialog.Builder builder = new AlertDialog.Builder(this)
			.setTitle(item.name)
			.setItems(options,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						mIncludedDirs.remove(path);
						mExcludedDirs.remove(path);
						switch (which) {
							case 0:
								mIncludedDirs.add(path);
								break;
							case 1:
								mExcludedDirs.add(path);
								break;
							default:
						}
						refreshDirectoryList(false);
					}
				});
		builder.create().show();
		return true;
	}

	/**
	 * display mCurrentPath in the dialog
	 */
	private void refreshDirectoryList(boolean scroll) {
		File path = mCurrentPath;
		File[]dirs = path.listFiles();
		
		mListAdapter.clear();
		mListAdapter.add(new FolderPickerAdapter.Item("../", null, 0));
		
		if(dirs != null) {
			Arrays.sort(dirs);
			for(File fentry: dirs) {
				if(fentry.isDirectory()) {
					int color = 0;
					if (mTritastic) {
						if (mIncludedDirs.contains(fentry.getAbsolutePath()))
							color = 0xff00c853;
						if (mExcludedDirs.contains(fentry.getAbsolutePath()))
							color = 0xffd50000;
					}
					FolderPickerAdapter.Item item = new FolderPickerAdapter.Item(fentry.getName(), fentry, color);
					mListAdapter.add(item);
				}
			}
		}
		else {
			Toast.makeText(this, "Failed to display " + path.getAbsolutePath(), Toast.LENGTH_SHORT).show();
		}
		mPathDisplay.setText(path.getAbsolutePath());
		if (scroll)
			mListView.setSelectionFromTop(0, 0);
	}
	
}
