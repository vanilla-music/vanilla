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
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.gmail.jerickson314.sdscanner.ScanFragment;
import com.gmail.jerickson314.sdscanner.UIStringGenerator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Fragment version of the MainActivity from the SD Scanner app
 */
public class SDScannerFragment extends Fragment
		implements ScanFragment.ScanProgressCallbacks
{
	private static ScanFragment mScanFragment;

	/**
	 * List of common directories with media files
	 */
	private File[] mScanTargetStages = {};

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		mScanTargetStages = getScanTargets(context);
	}

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
	public void updateStartButtonEnabled(boolean startButtonEnabled) {
		Button startButton = (Button)findViewById(R.id.start_button);
		startButton.setEnabled(startButtonEnabled);
	}

	@Override
	public void updatePath(String path) {
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

		// Make debug output scrollable.
		TextView debugLabel = (TextView)findViewById(R.id.debug_label);
		debugLabel.setMovementMethod(new ScrollingMovementMethod());

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

		if (mScanFragment == null)
			mScanFragment = (ScanFragment) fm.findFragmentByTag("scan");

		if (mScanFragment == null)
			mScanFragment = new ScanFragment();

		fm.beginTransaction().add(mScanFragment, "scan").commit();
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

	public void startButtonPressed(View view) throws IOException {
		mScanFragment.startScan(mScanTargetStages);
	}

	/**
	 * Creates a list of directories that can be scanned. Uses
	 * {@link Environment#getExternalStorageDirectory} and {@link Context#getExternalFilesDirs}
	 * (on KITKAT+).
	 *
	 * @param context The context
	 * @return A list of unique directories that can be scanned.
	 */
	private File[] getScanTargets(Context context) {
		ArrayList<File> scanTargets = new ArrayList<>();
		// Put canonical paths into a HashSet to avoid duplicates
		HashSet<String> possibleTargets = new HashSet<>();
		try {
			possibleTargets.add(Environment.getExternalStorageDirectory().getCanonicalPath());
		} catch (IOException e) {
			// Shouldn't happen, but just in case it does add the external storage dir directly
			scanTargets.add(Environment.getExternalStorageDirectory());
		}

		File sdcard1 = new File("/storage/sdcard1");
		if (sdcard1.exists()) {
			try {
				possibleTargets.add(sdcard1.getCanonicalPath());
			} catch (IOException e) {
				// Shouldn't happen, but just in case it does add the "storage/sdcard1" dir directly
				scanTargets.add(sdcard1);
			}
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			// Attempt to divine external storage directories from 'getExternalFilesDirs'
			File[] externalFilesDirs = context.getExternalFilesDirs(null);
			String packageDirSuffix = "/Android/data/" + context.getPackageName() + "/files";
			for (int i = 0; i < externalFilesDirs.length; i++) {
				try {
					String storageDir = externalFilesDirs[i].getCanonicalPath().replace(packageDirSuffix, "");
					possibleTargets.add(storageDir);
				} catch (IOException e) {

				}
			}
		}
		for (String possibleTarget : possibleTargets) {
			File file = new File(possibleTarget);
			if (file.exists()) {
				scanTargets.add(file);
			}
		}
		return scanTargets.toArray(new File[scanTargets.size()]);
	}
}
