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

import java.util.ArrayList;
import java.io.File;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.MenuItem;
import android.view.Menu;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.EditText;
import android.widget.Toast;

import com.mobeta.android.dslv.DragSortListView;


public abstract class FolderPickerActivity extends Activity
	implements AdapterView.OnItemClickListener,
	           AdapterView.OnItemLongClickListener
{

	/**
	 * Our listview
	 */
	private DragSortListView mListView;
	/**
	 * View displaying the current path
	 */
	private EditText mPathDisplay;
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

	@Override
	public void onCreate(Bundle savedInstanceState) {
		ThemeHelper.setTheme(this, R.style.BackActionBar);
		super.onCreate(savedInstanceState);

		setContentView(R.layout.folderpicker_content);

		mListAdapter = new FolderPickerAdapter(this, 0);
		mListView    = (DragSortListView)findViewById(R.id.list);
		mPathDisplay = (EditText) findViewById(R.id.path_display);
		mSaveButton  = (Button) findViewById(R.id.save_button);

		mListView.setAdapter(mListAdapter);
		mListView.setOnItemClickListener(this);

		mPathDisplay.addTextChangedListener(mTextWatcher);
		mSaveButton.setOnClickListener(mSaveButtonClickListener);

		// init defaults
		enableTritasticSelect(false, null, null);
	}

	/**
	 * Callback for Save button click events
	 */
	private final View.OnClickListener mSaveButtonClickListener = new View.OnClickListener() {
		public void onClick(View v) {
			onFolderPicked(mListAdapter.getCurrentDir(),
			               mListAdapter.getIncludedDirs(),
				       mListAdapter.getExcludedDirs());
		}
	};

	/**
	 * Callback for EditText change events
	 */
	private final TextWatcher mTextWatcher = new TextWatcher() {
		@Override
		public void afterTextChanged(Editable s) {
			final File dir = new File(s.toString());
			setCurrentDir(dir);
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {
		}
	};

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
		mListAdapter.setIncludedDirs(enabled ? included : null);
		mListAdapter.setExcludedDirs(enabled ? excluded : null);
		mListView.setOnItemLongClickListener(enabled ? this : null);
		mSaveButton.setText(enabled ? R.string.save : R.string.select);

		if (enabled)
			Toast.makeText(this, R.string.hint_long_press_to_modify_folder, Toast.LENGTH_SHORT).show();
	}

	/**
	 * Jumps to given directory
	 *
	 * @param dir the directory to jump to
	 */
	void setCurrentDir(File dir) {
		mSaveButton.setEnabled(dir.isDirectory());
		mListAdapter.setCurrentDir(dir);
		mListView.setSelectionFromTop(0, 0);

		final File labelDir = new File(mPathDisplay.getText().toString());
		if (!dir.equals(labelDir)) {
			// Only update the text field if the actual dir doesn't equal
			// the currently displayed path, even if the text isn't exactly
			// the same. We do this to avoid 'jumps' where we would replace
			// '/storage/x/' with '/storage/x'.
			final String label = dir.getAbsolutePath();
			mPathDisplay.setText(label);
			mPathDisplay.setSelection(label.length());
		}
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
		File curPath = mListAdapter.getCurrentDir();
		File newPath = null;

		if(item.file == null) {
			// This is the '..' entry
			newPath = curPath.getParentFile();
		}
		else {
			newPath = new File(curPath, item.name);
		}

		if (newPath != null)
			setCurrentDir(newPath);
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
						ArrayList<String> includedDirs = mListAdapter.getIncludedDirs();
						ArrayList<String> excludedDirs = mListAdapter.getExcludedDirs();
						includedDirs.remove(path);
						excludedDirs.remove(path);
						switch (which) {
							case 0:
								includedDirs.add(path);
								break;
							case 1:
								excludedDirs.add(path);
								break;
							default:
						}
						mListAdapter.setIncludedDirs(includedDirs);
						mListAdapter.setExcludedDirs(excludedDirs);
					}
				});
		builder.create().show();
		return true;
	}

	/**
	 * Override onBackPressed, so that the fileexplorer moves to the parent directory, until there is no parent. If there is no parent, close fileexplorer
	 */
	@Override
	public void onBackPressed(){
		File curPath = mListAdapter.getCurrentDir();
		File newPath = curPath.getParentFile();

		if (newPath != null){
			setCurrentDir(newPath);
		}else{
			super.onBackPressed();
			finish();
		}
	}
}
