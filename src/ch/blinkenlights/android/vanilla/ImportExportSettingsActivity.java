/*
 * Copyright (C) 2015 Xiao Bao Clark <xiao@xbc.nz>
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

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.util.Map;

public class ImportExportSettingsActivity extends Activity implements View.OnClickListener {

	private SharedPreferences mPreferences;
	private static final File SETTINGS_FILE = new File(Environment.getExternalStorageDirectory(),
			"vanilla_settings");
	private View mImportButton;
	private View mExportButton;
	private CharSequence mStatusFormat;

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		ThemeHelper.setTheme(this, R.style.BackActionBar);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.import_export_settings_activity);

		mPreferences = PreferenceManager.getDefaultSharedPreferences(this);

		TextView filePathTextView = (TextView) findViewById(R.id.file_path);
		filePathTextView.setText(getString(R.string.import_export_settings_file, SETTINGS_FILE
				.getPath()));

		mImportButton = findViewById(R.id.import_settings);
		mExportButton = findViewById(R.id.export_settings);

		mImportButton.setOnClickListener(this);
		mExportButton.setOnClickListener(this);

		mStatusFormat = getString(R.string.import_export_settings_status);
		updateFileState();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			finish();
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onClick(final View v) {
		boolean export = v == mExportButton;
		Throwable error = null;
		try {
			int successToastId;
			if (export) {
				exportPreferences(mPreferences, new FileOutputStream(SETTINGS_FILE));
				successToastId = R.string.export_settings_toast;
			} else {
				importPreferences(mPreferences, new FileInputStream(SETTINGS_FILE));
				successToastId = R.string.import_settings_toast;
			}

			Toast.makeText(this, successToastId, Toast.LENGTH_SHORT).show();

			updateFileState();
		} catch (IOException e) {
			error = e;
		}

		if (error != null) {
			int errorId = (export ? R.string.import_settings_error :
					R.string.export_settings_error);
			onTerribleFailure(getString(errorId), error);
		}
	}

	private void updateFileState() {

		TextView stateTextView = (TextView) findViewById(R.id.file_state);

		// Compare current settings to saved
		final SettingsFileState settingsFileState;
		try {
			settingsFileState = SettingsFileState.getState(SETTINGS_FILE, mPreferences);
		} catch (IOException e) {
			// This is generic IO error (we didn't attempt any packing/unpacking)
			e.printStackTrace();
			stateTextView.setText(R.string.settings_error_reading_file);
			return;
		}

		stateTextView.setText(String.format(mStatusFormat.toString(), getString(settingsFileState
				.statusTextId)));
		mImportButton.setEnabled(settingsFileState != SettingsFileState.DOES_NOT_EXIST);
	}

	/**
	 * Replaces the view with an error message and a stack trace
	 *
	 * @param message The message to show. Displayed in red.
	 * @param error The throwable that caused the error.
	 */
	private void onTerribleFailure(String message, Throwable error) {

		final ViewGroup rootView = (ViewGroup) findViewById(R.id.settings_root);

		rootView.removeAllViews();
		LayoutInflater.from(this).inflate(R.layout.import_export_settings_error, rootView, true);

		TextView errorMessageView = (TextView) rootView.findViewById(R.id.error_message);
		errorMessageView.setText(message);

		TextView errorDetailsView = (TextView) rootView.findViewById(R.id.error_details);
		errorDetailsView.setText(Log.getStackTraceString(error));
	}

	private static void exportPreferences(SharedPreferences pref, OutputStream file) throws
			IOException {
		ObjectOutputStream output = null;
		try {
			output = new ObjectOutputStream(file);
			output.writeObject(pref.getAll());
		} finally {
			if (output != null) {
				output.close();
			}
		}
	}


	@SuppressWarnings("unchecked")
	private static Map<String, Object> readPreferenceMap(InputStream inputStream) throws
			IOException {

		ObjectInputStream input = null;
		try {
			input = new ObjectInputStream(inputStream);
			Object entriesRaw = input.readObject();
			return (Map<String, Object>) entriesRaw;
		} catch (ClassCastException e) {
			throw new IOException(e);
		} catch (ClassNotFoundException e) {
			throw new IOException(e);
		} finally {
			closeNoThrow(input);
		}
	}

	private static void importPreferences(SharedPreferences pref, InputStream file) throws IOException {
		SharedPreferences.Editor prefEdit = pref.edit();
		prefEdit.clear();
		Map<String, ?> entries = readPreferenceMap(file);
		for (Map.Entry<String, ?> entry : entries.entrySet()) {
			Object v = entry.getValue();
			String key = entry.getKey();

			if (v instanceof Boolean)
				prefEdit.putBoolean(key, (Boolean) v);
			else if (v instanceof Float)
				prefEdit.putFloat(key, (Float) v);
			else if (v instanceof Integer)
				prefEdit.putInt(key, (Integer) v);
			else if (v instanceof Long)
				prefEdit.putLong(key, (Long) v);
			else if (v instanceof String)
				prefEdit.putString(key, ((String) v));
		}
		prefEdit.commit();

	}

	private static void closeNoThrow(Closeable closeable) {
		if (closeable != null) {
			try {
				closeable.close();
			} catch (IOException e) {
				// No throw
			}
		}
	}

	private enum SettingsFileState {
		DOES_NOT_EXIST(R.string.settings_no_file),
		SAME_AS_CURRENT(R.string.settings_file_same_as_current),
		DIFFERS_FROM_CURRENT(R.string.settings_file_differs_from_current);

		private final int statusTextId;

		SettingsFileState(final int statusTextId) {

			this.statusTextId = statusTextId;
		}

		private static SettingsFileState getState(File file, SharedPreferences current) throws IOException {
			if (!file.exists()) {
				return DOES_NOT_EXIST;
			}

			final Map<String, ?> stored = readPreferenceMap(new FileInputStream(file));

			Log.d("VanillaMusic", "stored: " + stored.toString());
			Log.d("VanillaMusic", "current: " + current.toString());

			if (stored.equals(current.getAll())) {
				return SAME_AS_CURRENT;
			} else {
				return DIFFERS_FROM_CURRENT;
			}
		}
	}

}
