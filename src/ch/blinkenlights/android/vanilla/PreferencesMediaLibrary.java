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
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import java.util.Timer;
import java.util.TimerTask;
import android.util.Log;

public class PreferencesMediaLibrary extends Fragment
{
	/**
	 * The ugly timer which fires every 200ms
	 */
	private Timer mTimer;
	/**
	 * Our start button
	 */
	private View mStartButton;
	/**
	 * The debug / progress text describing the scan status
	 */
	private TextView mProgress;
	/**
	 * The number of songs on this device
	 */;
	private TextView mStatsSongs;
	/**
	 * The number of hours of music we have
	 */
	private TextView mStatsPlaytime;
	/**
	 * Checkbox for full scan
	 */
	private CheckBox mFullScanCheck;
	/**
	 * Checkbox for drop
	 */
	private CheckBox mDropDbCheck;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.medialibrary_preferences, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		mStartButton = (View)view.findViewById(R.id.start_button);
		mProgress = (TextView)view.findViewById(R.id.media_stats_progress);
		mStatsSongs = (TextView)view.findViewById(R.id.media_stats_songs);
		mStatsPlaytime = (TextView)view.findViewById(R.id.media_stats_playtime);
		mFullScanCheck = (CheckBox)view.findViewById(R.id.media_scan_full);
		mDropDbCheck = (CheckBox)view.findViewById(R.id.media_scan_drop_db);

		mStartButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				startButtonPressed(v);
			}
		});
	}

	@Override
	public void onResume() {
		super.onResume();
		mTimer = new Timer();
		// Yep: its as ugly as it seems: we are POLLING
		// the database.
		mTimer.scheduleAtFixedRate((new TimerTask() {
			@Override
			public void run() {
				getActivity().runOnUiThread(new Runnable(){
					public void run() {
						updateProgress();
					}
				});
			}}), 0, 200);
	}

	@Override
	public void onPause() {
		super.onPause();
		if (mTimer != null) {
			mTimer.cancel();
			mTimer = null;
		}
	}

	/**
	 * Updates the view of this fragment with current information
	 */
	private void updateProgress() {
		Context context = getActivity();
		String scanText = MediaLibrary.describeScanProgress(getActivity());
		mProgress.setText(scanText);
		mStartButton.setEnabled(scanText == null);

		Integer songCount = MediaLibrary.getLibrarySize(context);
		mStatsSongs.setText(songCount.toString());

		Float playtime = calculateDuration(context) / 3600000F;
		mStatsPlaytime.setText(playtime.toString());
	}

	/**
	 * Queries the media library and calculates the total amount of playtime in ms
	 *
	 * @param context the context to use
	 * @return the play time of the library in ms
	 */
	public int calculateDuration(Context context) {
		int duration = 0;
		Cursor cursor = MediaLibrary.queryLibrary(context, MediaLibrary.TABLE_SONGS, new String[]{"SUM("+MediaLibrary.SongColumns.DURATION+")"}, null, null, null);
		if (cursor != null) {
			if (cursor.moveToFirst()) {
				duration = cursor.getInt(0);
			}
			cursor.close();
		}
		return duration;
	}

	/**
	 * Called when the user hits the start button
	 *
	 * @param view the view which was pressed
	 */
	public void startButtonPressed(View view) {
		MediaLibrary.scanLibrary(getActivity(), mFullScanCheck.isChecked(), mDropDbCheck.isChecked());
		updateProgress();
	}

}
