/*
 * Copyright (C) 2016 Xiao Bao Clark
 * Copyright (C) 2016 Adrian Ulrich
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

import ch.blinkenlights.android.medialibrary.MediaLibrary;

import android.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;
import android.util.Log;

public class SDScannerFragment extends Fragment
{
	private Timer mTimer;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.sdscanner_fragment, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		view.findViewById(R.id.start_button).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startButtonPressed(v);
			}
		});
	}

	@Override
	public void onResume() {
		super.onResume();
		Log.v("VanillaMusic", "onResume! "+mTimer);

		mTimer = new Timer();
		mTimer.scheduleAtFixedRate((new TimerTask() {
			@Override
			public void run() {
				getActivity().runOnUiThread(new Runnable(){
					public void run() {
						updateProgress();
					}
				});
			}}), 0, 120);
	}

	@Override
	public void onPause() {
		super.onPause();
		Log.v("VanillaMusic", "onPause "+mTimer);
		if (mTimer != null) {
			mTimer.cancel();
			mTimer = null;
		}
	}

	private void updateProgress() {
		View button = getActivity().findViewById(R.id.start_button);
		TextView progress = (TextView)getActivity().findViewById(R.id.progress_label);
		String scanText = MediaLibrary.describeScanProgress(getActivity());
		progress.setText(scanText);
		button.setEnabled(scanText == null);
	}

	public void startButtonPressed(View view) {
		MediaLibrary.scanLibrary(getActivity(), true, false);
		updateProgress();
	}

}
