/* SD Scanner - A manual implementation of the SD rescan process, compatible
 * with Android 4.4
 *
 * Copyright (C) 2013-2014 Jeremy Erickson
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

package com.gmail.jerickson314.sdscanner;

import android.app.Activity;
import android.app.FragmentManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.MediaStore;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;

import ch.blinkenlights.android.vanilla.R;

public class MainActivity extends Activity
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
        progressLabel.setText(progressText.toString(this));
    }

    @Override
    public void updateDebugMessages(UIStringGenerator debugMessages) {
        TextView debugLabel = (TextView)findViewById(R.id.debug_label);
        debugLabel.setText(debugMessages.toString(this));
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
        if (!TextUtils.isEmpty(getIntent().getAction()) &&
                getIntent().getAction().equals(Intent.ACTION_RUN)) {
            finish();
        }
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        FragmentManager fm = getFragmentManager();
        mScanFragment = (ScanFragment) fm.findFragmentByTag("scan");

        if (mScanFragment == null) {
            mScanFragment = new ScanFragment();
            fm.beginTransaction().add(mScanFragment, "scan").commit();
        }

        // Setup with values from fragment.
        updateProgressNum(mScanFragment.getProgressNum());
        updateProgressText(mScanFragment.getProgressText());
        updateDebugMessages(mScanFragment.getDebugMessages());
        updateStartButtonEnabled(mScanFragment.getStartButtonEnabled());

        // Update path from preferences
        SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
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
    }

    @Override
    public void onStart() {
        super.onStart();

        if (!mScanFragment.getHasStarted() && !TextUtils.isEmpty(getIntent().getAction()) &&
                getIntent().getAction().equals(Intent.ACTION_RUN)) {
            try {
                startScan();
            }
            catch (IOException ex) {
                // We currently do nothing.
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();

        // Write setting to preferences
        EditText pathText = (EditText) findViewById(R.id.path_widget);
        CheckBox restrictCheckbox = (CheckBox) findViewById(R.id.restrict_checkbox);

        SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
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
