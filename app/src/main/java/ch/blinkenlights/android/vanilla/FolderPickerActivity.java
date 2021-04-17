/*
 * Copyright (C) 2013-2020 Adrian Ulrich <adrian@blinkenlights.ch>
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
import android.widget.Spinner;

import com.mobeta.android.dslv.DragSortListView;


public abstract class FolderPickerActivity extends Activity
	implements AdapterView.OnItemClickListener,
	           AdapterView.OnItemLongClickListener,
			   AdapterView.OnItemSelectedListener
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
	 * Tristate spinner
	 */
	private Spinner mSpinner;
	/**
	 * The array adapter of our listview
	 */
	private FolderPickerAdapter mListAdapter;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		ThemeHelper.setTheme(this, R.style.BackActionBar);
		super.onCreate(savedInstanceState);

		setContentView(R.layout.folderpicker_content);

		mListAdapter = new FolderPickerAdapter(this, 0);
		mListView    = (DragSortListView)findViewById(R.id.list);
		mPathDisplay = (EditText) findViewById(R.id.path_display);
		mSaveButton  = (Button) findViewById(R.id.save_button);
		mSpinner = (Spinner) findViewById(R.id.folder_picker_spinner);

		mListView.setAdapter(mListAdapter);
		mListView.setOnItemClickListener(this);

		mPathDisplay.addTextChangedListener(mTextWatcher);
		mSaveButton.setOnClickListener(mSaveButtonClickListener);

		mSpinner.setSelection(0);
		mSpinner.setOnItemSelectedListener(this);

		// init defaults
		enableTritasticSelect(false, null, null);
		enableTritasticSpinner(false);
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
		if (enabled) {
			Toast.makeText(this, R.string.hint_long_press_to_modify_folder, Toast.LENGTH_SHORT).show();
			mListAdapter.setIncludedDirs(included);
			mListAdapter.setExcludedDirs(excluded);
		}
		mListView.setOnItemLongClickListener(enabled ? this : null);
		mSaveButton.setText(enabled ? R.string.save : R.string.select);
	}

	/**
	 * Whether or not to enable the tristate spinner.
	 */
	public void enableTritasticSpinner(boolean enabled) {
		View view = findViewById(R.id.folder_picker_spinner_container);
		view.setVisibility(enabled ? View.VISIBLE : View.GONE);
	}

	/**
	 * Jumps to given directory
	 *
	 * @param dir the directory to jump to
	 */
	void setCurrentDir(File dir) {
		mSaveButton.setEnabled(dir.isDirectory());
		mSpinner.setEnabled(dir.isDirectory());
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

			// Since the folder changed, also update the spinner state.
			if (mListAdapter.getIncludedDirs().contains(label)) {
				mSpinner.setSelection(0);
			} else if (mListAdapter.getExcludedDirs().contains(label)) {
				mSpinner.setSelection(1);
			} else {
				mSpinner.setSelection(2);
			}
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
	 * Called if user interacts with the spinner.
	 */
	@Override
	public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
		FolderState state = FolderState.NEUTRAL;
		switch(position) {
		case 0:
			state = FolderState.INCLUDE;
			break;
		case 1:
			state = FolderState.EXCLUDE;
			break;
		}
		setFolderState(mListAdapter.getCurrentDir().getAbsolutePath(), state);
	}

	/**
	 * Called if user dismisses the spinner.
	 */
	@Override
	public void onNothingSelected(AdapterView<?> parent) {
		// noop.
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
						switch (which) {
							case 0:
								setFolderState(path, FolderState.INCLUDE);
								break;
							case 1:
								setFolderState(path, FolderState.EXCLUDE);
								break;
							default:
								setFolderState(path, FolderState.NEUTRAL);
						}
					}
				});
		builder.create().show();
		return true;
	}

	/**
	 * Enums to pass to setFolderState()
	 */
	enum FolderState {
		NEUTRAL,
		INCLUDE,
		EXCLUDE,
	}

	/**
	 * update included/excluded folders on the adapter.
	 *
	 * @param folder the folder to act on.
	 * @param state the state the passed in folder should have.
	 */
	private void setFolderState(String folder, FolderState state) {
		ArrayList<String> includedDirs = mListAdapter.getIncludedDirs();
		ArrayList<String> excludedDirs = mListAdapter.getExcludedDirs();
		includedDirs.remove(folder);
		excludedDirs.remove(folder);
		switch (state) {
		case INCLUDE:
			includedDirs.add(folder);
			break;
		case EXCLUDE:
			excludedDirs.add(folder);
			break;
		case NEUTRAL:
			// noop.
			break;
		}

		mListAdapter.setIncludedDirs(includedDirs);
		mListAdapter.setExcludedDirs(excludedDirs);
	}
}
