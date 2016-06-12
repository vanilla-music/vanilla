/* SD Scanner - A manual implementation of the SD rescan process, compatible
 * with Android 4.4
 *
 * Copyright (C) 2013-2014 Jeremy Erickson
 * Copyright (C) 2016 Xiao Bao Clark
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package ch.blinkenlights.android.vanilla;

import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.gmail.jerickson314.sdscanner.ScanFragment;
import com.gmail.jerickson314.sdscanner.UIStringGenerator;

import java.io.File;
import java.io.IOException;

/**
 * Fragment version of the MainActivity from the SD Scanner app
 */
public class SDScannerFragment extends Fragment
		implements ScanFragment.ScanProgressCallbacks
{
	ScanFragment mScanFragment;

	@Override
	public void updateProgressNum(int progressNum) {
		ProgressBar progressBar = (ProgressBar)findViewById(R.id.progress_bar);
		progressBar.setProgress(progressNum);
	}

	@Override
	public void updateProgressText(UIStringGenerator progressText) {
		TextView progressLabel = (TextView)findViewById(R.id.progress_label);
		progressLabel.setText(progressText.toString(getActivity()));
	}

	@Override
	public void updateDebugMessages(UIStringGenerator debugMessages) {
		TextView debugLabel = (TextView)findViewById(R.id.debug_label);
		debugLabel.setText(debugMessages.toString(getActivity()));
	}

	@Override
	public void updatePath(String path) {
		EditText pathText = (EditText) findViewById(R.id.path_widget);
		pathText.setText(path);
	}

	@Override
	public void updateStartButtonEnabled(boolean startButtonEnabled) {
		Button startButton = (Button)findViewById(R.id.start_button);
		startButton.setEnabled(startButtonEnabled);
	}

	public void updateRestrictCheckboxChecked(boolean checked) {
		CheckBox restrictCheckbox = (CheckBox) findViewById(R.id.restrict_checkbox);
		restrictCheckbox.setChecked(checked);
	}

	@Override
	public void signalFinished() {

	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.sdscanner_fragment, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		// Setup with values from fragment.
		updateProgressNum(mScanFragment.getProgressNum());
		updateProgressText(mScanFragment.getProgressText());
		updateDebugMessages(mScanFragment.getDebugMessages());
		updateStartButtonEnabled(mScanFragment.getStartButtonEnabled());

		// Update path from preferences
		SharedPreferences preferences = getActivity().getPreferences(Context.MODE_PRIVATE);
		try {
			updatePath(preferences.getString("path",
					Environment.getExternalStorageDirectory().getCanonicalPath()));
			updateRestrictCheckboxChecked(preferences.getBoolean(
					"restrict_db_scan", false));
		}
		catch (IOException Ex) {
			// Should never happen, but getCanonicalPath() declares the throw.
			updatePath("");
			updateRestrictCheckboxChecked(false);
		}

		// Make debug output scrollable.
		TextView debugLabel = (TextView)findViewById(R.id.debug_label);
		debugLabel.setMovementMethod(new ScrollingMovementMethod());

		view.findViewById(R.id.path_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				try {
					defaultButtonPressed(v);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		});

		view.findViewById(R.id.start_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				try {
					startButtonPressed(v);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		});
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		FragmentManager fm = getFragmentManager();
		mScanFragment = (ScanFragment) fm.findFragmentByTag("scan");

		if (mScanFragment == null) {
			mScanFragment = new ScanFragment();
			fm.beginTransaction().add(mScanFragment, "scan").commit();
		}
		mScanFragment.setScanProgressCallbacks(this);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mScanFragment.setScanProgressCallbacks(null);
	}

	private View findViewById(int viewId) {
		return getView().findViewById(viewId);
	}

	@Override
	public void onStop() {
		super.onStop();

		// Write setting to preferences
		EditText pathText = (EditText) findViewById(R.id.path_widget);
		CheckBox restrictCheckbox = (CheckBox) findViewById(R.id.restrict_checkbox);

		SharedPreferences preferences = getActivity().getPreferences(Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = preferences.edit();
		editor.putString("path", pathText.getText().toString());
		editor.putBoolean("restrict_db_scan", restrictCheckbox.isChecked());
		editor.commit();
	}

	public void defaultButtonPressed(View view) throws IOException {
		updatePath(Environment.getExternalStorageDirectory().getCanonicalPath());
	}

	public void startButtonPressed(View view) throws IOException {
		startScan();
	}

	public void startScan() throws IOException {
		EditText pathText = (EditText) findViewById(R.id.path_widget);
		File path = new File(pathText.getText().toString());
		CheckBox restrictCheckbox = (CheckBox) findViewById(R.id.restrict_checkbox);

		mScanFragment.startScan(path.getCanonicalFile(), restrictCheckbox.isChecked());
	}

}
