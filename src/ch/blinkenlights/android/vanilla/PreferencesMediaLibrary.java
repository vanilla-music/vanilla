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

import ch.blinkenlights.android.medialibrary.MediaLibrary;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class PreferencesMediaLibrary extends Fragment implements View.OnClickListener
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
	 * The cancel button
	 */
	private View mCancelButton;
	/**
	 * The edit-media-folders button
	 */
	private View mEditButton;
	/**
	 * The debug / progress text describing the scan status
	 */
	private TextView mProgressText;
	/**
	 * The progress bar
	 */
	private ProgressBar mProgressBar;
	/**
	 * The number of tracks on this device
	 */
	private TextView mStatsTracks;
	/**
	 * The number of hours of music we have
	 */
	private TextView mStatsLibraryPlaytime;
	/**
	 * The total listening time
	 */
	private TextView mStatsListenPlaytime;
	/**
	 * A list of scanned media directories
	 */
	private TextView mMediaDirectories;
	/**
	 * Checkbox for full scan
	 */
	private CheckBox mFullScanCheck;
	/**
	 * Checkbox for drop
	 */
	private CheckBox mDropDbCheck;
	/**
	 * Checkbox for album group option
	 */
	private CheckBox mGroupAlbumsCheck;
	/**
	 * Checkbox for targreader flavor
	 */
	private CheckBox mForceBastpCheck;
	/**
	 * Set if we should start a full scan due to option changes
	 */
	private boolean mFullScanPending;
	/**
	 * Set if we are in the edit dialog
	 */
	private boolean mIsEditingDirectories;
	/**
	 * The original state of our media folders.
	 */
	private String mInitialMediaFolders;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.medialibrary_preferences, container, false);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		mStartButton = view.findViewById(R.id.start_button);
		mCancelButton = view.findViewById(R.id.cancel_button);
		mEditButton = view.findViewById(R.id.edit_button);
		mProgressText = (TextView)view.findViewById(R.id.media_stats_progress_text);
		mProgressBar = (ProgressBar)view.findViewById(R.id.media_stats_progress_bar);
		mStatsTracks = (TextView)view.findViewById(R.id.media_stats_tracks);
		mStatsLibraryPlaytime = (TextView)view.findViewById(R.id.media_stats_library_playtime);
		mStatsListenPlaytime = (TextView)view.findViewById(R.id.media_stats_listen_playtime);
		mMediaDirectories = (TextView)view.findViewById(R.id.media_directories);
		mFullScanCheck = (CheckBox)view.findViewById(R.id.media_scan_full);
		mDropDbCheck = (CheckBox)view.findViewById(R.id.media_scan_drop_db);
		mGroupAlbumsCheck = (CheckBox)view.findViewById(R.id.media_scan_group_albums);
		mForceBastpCheck = (CheckBox)view.findViewById(R.id.media_scan_force_bastp);

		// Bind onClickListener to some elements
		mStartButton.setOnClickListener(this);
		mCancelButton.setOnClickListener(this);
		mEditButton.setOnClickListener(this);
		mGroupAlbumsCheck.setOnClickListener(this);
		mForceBastpCheck.setOnClickListener(this);
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

		// We got freshly created and user didn't have a chance
		// to edit anything: remember this state
		if (mInitialMediaFolders == null)
			mInitialMediaFolders = getMediaFoldersDescription();

		// Returned from edit dialog
		if (mIsEditingDirectories) {
			mIsEditingDirectories = false;
			// trigger a scan if user changed its preferences
			// in the edit dialog
			if (!mInitialMediaFolders.equals(getMediaFoldersDescription()))
				mFullScanPending = true;
		}

		updatePreferences(null);
	}

	@Override
	public void onPause() {
		super.onPause();
		if (mTimer != null) {
			mTimer.cancel();
			mTimer = null;
		}

		// User exited this view -> scan if needed
		if (mFullScanPending && !mIsEditingDirectories) {
			MediaLibrary.startLibraryScan(getActivity(), true, true);
			mInitialMediaFolders = null;
			mFullScanPending = false;
		}
	}

	@Override
	public void onClick(View view) {
		switch (view.getId()) {
			case R.id.start_button:
				startButtonPressed(view);
				break;
			case R.id.cancel_button:
				cancelButtonPressed(view);
				break;
			case R.id.edit_button:
				editButtonPressed(view);
				break;
			case R.id.media_scan_group_albums:
			case R.id.media_scan_force_bastp:
				confirmUpdatePreferences((CheckBox)view);
				break;
		}
	}

	/**
	 * Wrapper for updatePreferences() which warns the user about
	 * possible consequences.
	 *
	 * @param checkbox the checkbox which was changed
	 */
	private void confirmUpdatePreferences(final CheckBox checkbox) {
		if (mFullScanPending) {
			// User was already warned, so we can just dispatch this
			// without nagging again
			updatePreferences(checkbox);
			return;
		}

		new AlertDialog.Builder(getActivity())
			.setTitle(R.string.media_scan_preferences_change_title)
			.setMessage(R.string.media_scan_preferences_change_message)
			.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					mFullScanPending = true;
					confirmUpdatePreferences(checkbox);
				}
			})
			.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					// restore old condition if use does not want to proceed
					checkbox.setChecked(!checkbox.isChecked());
				}
			})
			.show();
	}

	/**
	 * Initializes and updates the scanner preferences
	 *
	 * @param checkbox the item to update, may be null
	 */
	private void updatePreferences(CheckBox checkbox) {
		MediaLibrary.Preferences prefs = MediaLibrary.getPreferences(getActivity());

		if (checkbox == mGroupAlbumsCheck)
			prefs.groupAlbumsByFolder = mGroupAlbumsCheck.isChecked();
		if (checkbox == mForceBastpCheck)
			prefs.forceBastp = mForceBastpCheck.isChecked();

		MediaLibrary.setPreferences(getActivity(), prefs);

		mGroupAlbumsCheck.setChecked(prefs.groupAlbumsByFolder);
		mForceBastpCheck.setChecked(prefs.forceBastp);
		mMediaDirectories.setText(getMediaFoldersDescription());
	}

	/**
	 * Returns a textual representation of the media folder state
	 *
	 * @return string describing our directory scan preferences
	 */
	private String getMediaFoldersDescription() {
		MediaLibrary.Preferences prefs = MediaLibrary.getPreferences(getActivity());
		String description = "";
		for (String path : prefs.mediaFolders) {
			description += "✔ " + path + "\n";
		}
		for (String path : prefs.blacklistedFolders) {
			description += "✘ " + path + "\n";
		}
		return description;
	}

	/**
	 * Updates the view of this fragment with current information
	 */
	private void updateProgress() {
		Context context = getActivity();
		MediaLibrary.ScanProgress progress = MediaLibrary.describeScanProgress(context);

		boolean idle = !progress.isRunning;
		mProgressText.setText(progress.lastFile);
		mProgressBar.setMax(progress.total);
		mProgressBar.setProgress(progress.seen);

		mStartButton.setEnabled(idle);
		mEditButton.setEnabled(idle);
		mDropDbCheck.setEnabled(idle);
		mFullScanCheck.setEnabled(idle);
		mForceBastpCheck.setEnabled(idle);
		mGroupAlbumsCheck.setEnabled(idle);

		mCancelButton.setVisibility(idle ? View.GONE : View.VISIBLE);
		mProgressText.setVisibility(idle ? View.GONE : View.VISIBLE);
		mProgressBar.setVisibility(idle ? View.GONE : View.VISIBLE);

		Integer songCount = MediaLibrary.getLibrarySize(context);
		mStatsTracks.setText(String.format(Locale.ENGLISH, "%d", songCount));

		float libraryPlaytime = calculateSongSum(context, MediaLibrary.SongColumns.DURATION) / 3600000F;
		mStatsLibraryPlaytime.setText(String.format(Locale.ENGLISH, "%.1f", libraryPlaytime));

		float listenPlaytime = calculateSongSum(context, MediaLibrary.SongColumns.PLAYCOUNT+"*"+MediaLibrary.SongColumns.DURATION) / 3600000F;
		mStatsListenPlaytime.setText(String.format(Locale.ENGLISH, "%.1f", listenPlaytime));
	}

	/**
	 * Queries the media library and calculates the sum of given column.
	 *
	 * @param context the context to use
	 * @param column the column to sum up
	 * @return the play time of the library in ms
	 */
	public long calculateSongSum(Context context, String column) {
		long duration = 0;
		Cursor cursor = MediaLibrary.queryLibrary(context, MediaLibrary.TABLE_SONGS, new String[]{"SUM("+column+")"}, null, null, null);
		if (cursor != null) {
			if (cursor.moveToFirst()) {
				duration = cursor.getLong(0);
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
		MediaLibrary.startLibraryScan(getActivity(), mFullScanCheck.isChecked(), mDropDbCheck.isChecked());
		updateProgress();
	}

	/**
	 * Called when the user hits the cancel button
	 *
	 * @param view the view which was pressed
	 */
	public void cancelButtonPressed(View view) {
		MediaLibrary.abortLibraryScan(getActivity());
		updateProgress();
	}

	/**
	 * Called when the user hits the edit button
	 *
	 * @param view the view which was pressed
	 */
	private void editButtonPressed(final View view) {
		if (mFullScanPending) {
			// no need to nag if we are scanning anyway
			startMediaFoldersSelection();
			return;
		}

		new AlertDialog.Builder(getActivity())
			.setTitle(R.string.media_scan_preferences_change_title)
			.setMessage(R.string.media_scan_preferences_change_message)
			.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					startMediaFoldersSelection();
				}
			})
			.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {}
			})
			.show();
	}

	/**
	 * Launches the edit dialog
	 */
	private void startMediaFoldersSelection() {
			mIsEditingDirectories = true;
			startActivity(new Intent(getActivity(), MediaFoldersSelectionActivity.class));
	}

}
