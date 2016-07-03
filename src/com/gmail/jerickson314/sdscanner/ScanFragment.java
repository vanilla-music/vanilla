/* SD Scanner - A manual implementation of the SD rescan process, compatible
 * with Android 4.4.
 *
 * This file contains the fragment that actually performs all scan activity
 * and retains state across configuration changes.
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
import android.app.Fragment;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.TreeSet;

import ch.blinkenlights.android.vanilla.R;

public class ScanFragment extends Fragment {

    private static final String[] MEDIA_PROJECTION =
        {MediaStore.MediaColumns.DATA,
         MediaStore.MediaColumns.DATE_MODIFIED,
         MediaStore.MediaColumns.SIZE};

    private static final String[] STAR = {"*"};

    private static final int DB_RETRIES = 3;

    Context mApplicationContext;

    ArrayList<String> mPathNames;
    TreeSet<File> mFilesToProcess;
    int mLastGoodProcessedIndex;

    private Handler mHandler = new Handler();

    int mProgressNum;
    UIStringGenerator mProgressText =
            new UIStringGenerator(R.string.progress_unstarted_label);
    UIStringGenerator mDebugMessages = new UIStringGenerator();
    boolean mStartButtonEnabled;
    boolean mHasStarted = false;

    ArrayList<File> mDirectoryScanList;

    /**
     * Callback interface used by the fragment to update the Activity.
     */
    public static interface ScanProgressCallbacks {
        void updateProgressNum(int progressNum);
        void updateProgressText(UIStringGenerator progressText);
        void updateDebugMessages(UIStringGenerator debugMessages);
        void updatePath(String path);
        void updateStartButtonEnabled(boolean startButtonEnabled);
        void signalFinished();
    }

    private ScanProgressCallbacks mCallbacks;

    private void updateProgressNum(int progressNum) {
        mProgressNum = progressNum;
        if (mCallbacks != null) {
            mCallbacks.updateProgressNum(mProgressNum);
        }
    }

    private void updateProgressText(int resId) {
        updateProgressText(new UIStringGenerator(resId));
    }

    private void updateProgressText(int resId, String string) {
        updateProgressText(new UIStringGenerator(resId, string));
    }

    private void updateProgressText(UIStringGenerator progressText) {
        mProgressText = progressText;
        if (mCallbacks != null) {
            mCallbacks.updateProgressText(mProgressText);
        }
    }

    private void addDebugMessage(int resId, String string) {
        mDebugMessages.addSubGenerator(resId);
        mDebugMessages.addSubGenerator(string + "\n");
        if (mCallbacks != null) {
            mCallbacks.updateDebugMessages(mDebugMessages);
        }
    }

    private void addDebugMessage(String debugMessage) {
        mDebugMessages.addSubGenerator(debugMessage + "\n");
        if (mCallbacks != null) {
            mCallbacks.updateDebugMessages(mDebugMessages);
        }
    }

    private void resetDebugMessages() {
        mDebugMessages = new UIStringGenerator();
        if (mCallbacks != null) {
            mCallbacks.updateDebugMessages(mDebugMessages);
        }
    }

    private void updateStartButtonEnabled(boolean startButtonEnabled) {
        mStartButtonEnabled = startButtonEnabled;
        if (mCallbacks != null) {
            mCallbacks.updateStartButtonEnabled(mStartButtonEnabled);
        }
    }

    private void signalFinished() {
        if (mCallbacks != null) {
            mCallbacks.signalFinished();
        }
    }

    public int getProgressNum() {
        return mProgressNum;
    }

    public UIStringGenerator getProgressText() {
        return mProgressText;
    }

    public UIStringGenerator getDebugMessages() {
        return mDebugMessages;
    }

    public boolean getStartButtonEnabled() {
        return mStartButtonEnabled;
    }

    public boolean getHasStarted() {
        return mHasStarted;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if(activity instanceof ScanProgressCallbacks) {
            mCallbacks = (ScanProgressCallbacks) activity;
        }
        mApplicationContext = activity.getApplicationContext();
    }

    public void setScanProgressCallbacks(ScanProgressCallbacks callbacks) {
        mCallbacks = callbacks;
    }

    public ScanFragment() {
        super();

        // Set correct initial values.
        mProgressNum = 0;
        mStartButtonEnabled = true;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Retain this fragment across configuration changes.
        setRetainInstance(true);
    }

    // Purely for debugging and not normally used, so does not translate
    // strings.
    public void listPathNamesOnDebug() {
        StringBuffer listString = new StringBuffer();
        listString.append("\n\nScanning paths:\n");
        Iterator<String> iterator = mPathNames.iterator();
        while (iterator.hasNext()) {
            listString.append(iterator.next() + "\n");
        }
        addDebugMessage(listString.toString());
    }

    public void advanceScanner() {
        if (mDirectoryScanList != null && mDirectoryScanList.isEmpty() == false) {
            File nextDir = mDirectoryScanList.remove(0);
            startScan(nextDir, false);
        } else {
            updateProgressNum(0);
            updateProgressText(R.string.progress_completed_label);
            updateStartButtonEnabled(true);
            signalFinished();
        }
    }

    public void startMediaScanner(){
        //listPathNamesOnDebug();
        if (mPathNames.size() == 0) {
            advanceScanner();
        }
        else {
            MediaScannerConnection.scanFile(
                mApplicationContext,
                mPathNames.toArray(new String[mPathNames.size()]),
                null,
                new MediaScannerConnection.OnScanCompletedListener() {
                    public void onScanCompleted(String path, Uri uri) {
                        mHandler.post(new Updater(path));
                    }
                });
        }
    }

    public void startScan(File[] pathList) {
        mDirectoryScanList = new ArrayList<File>();
        for (File f : pathList) {
            if (f.exists() && f.isDirectory())
                mDirectoryScanList.add(f);
        }
        advanceScanner();
    }

    public void startScan(File path, boolean restrictDbUpdate) {
        mHasStarted = true;
        updateStartButtonEnabled(false);
        updateProgressText(R.string.progress_filelist_label);
        mFilesToProcess = new TreeSet<File>();
        resetDebugMessages();
        if (path.exists()) {
            this.new PreprocessTask().execute(new ScanParameters(path, restrictDbUpdate));
        }
        else {
            updateProgressText(R.string.progress_error_bad_path_label);
            updateStartButtonEnabled(true);
            signalFinished();
        }
    }

    static class ProgressUpdate {
        public enum Type {
            DATABASE, STATE, DEBUG
        }

        Type mType;

        public Type getType() {
            return mType;
        }

        int mResId;

        public int getResId() {
            return mResId;
        }

        String mString;

        public String getString() {
            return mString;
        }

        int mProgress;

        public int getProgress() {
            return mProgress;
        }

        public ProgressUpdate(Type type, int resId, String string,
                              int progress) {
            mType = type;
            mResId = resId;
            mString = string;
            mProgress = progress;
        }
    }

    static ProgressUpdate debugUpdate(int resId, String string) {
        return new ProgressUpdate(ProgressUpdate.Type.DEBUG, resId, string, 0);
    }

    static ProgressUpdate debugUpdate(int resId) {
        return debugUpdate(resId, "");
    }

    static ProgressUpdate databaseUpdate(String file, int progress) {
        return new ProgressUpdate(ProgressUpdate.Type.DATABASE, 0, file,
                                  progress);
    }

    static ProgressUpdate stateUpdate(int resId) {
        return new ProgressUpdate(ProgressUpdate.Type.STATE, resId, "", 0);
    }

    static class ScanParameters {
        File mPath;
        boolean mRestrictDbUpdate;

        public ScanParameters(File path, boolean restrictDbUpdate) {
            mPath = path;
            mRestrictDbUpdate = restrictDbUpdate;
        }

        public File getPath() {
            return mPath;
        }

        public boolean shouldScan(File file, boolean fromDb)
                throws IOException {
            // Empty directory check.
            if (file.isDirectory()) {
                File[] files = file.listFiles();
                if (files == null || files.length == 0) {
                    Log.w("SDScanner", "Scan of empty directory " +
                          file.getCanonicalPath() + " skipped to avoid bug.");
                    return false;
                }
            }
            if (!mRestrictDbUpdate && fromDb) {
                return true;
            }
            while (file != null) {
                if (file.equals(mPath)) {
                    return true;
                }
                file = file.getParentFile();
            }
            // If we fell through here, got up to root without encountering the
            // path to scan.
            if (!fromDb) {
                Log.w("SDScanner", "File " + file.getCanonicalPath() +
                      " outside of scan directory skipped.");
            }
            return false;
        }
    }

    class PreprocessTask extends AsyncTask<ScanParameters, ProgressUpdate, Void> {

        private void recursiveAddFiles(File file, ScanParameters scanParameters)
                throws IOException {
            if (!scanParameters.shouldScan(file, false)) {
                // If we got here, there file was either outside the scan
                // directory, or was an empty directory.
                return;
            }
            if (!mFilesToProcess.add(file)) {
                // Avoid infinite recursion caused by symlinks.
                // If mFilesToProcess already contains this file, add() will 
                // return false.
                return;
            }
            if (file.isDirectory()) {
                boolean nomedia = new File(file, ".nomedia").exists();
                // Only recurse downward if not blocked by nomedia.
                if (!nomedia) {
                    File[] files = file.listFiles();
                    if (files != null) {
                        for (File nextFile : files) {
                            recursiveAddFiles(nextFile.getCanonicalFile(),
                                              scanParameters);
                        }
                    }
                    else {
                        publishProgress(debugUpdate(
                                R.string.skipping_folder_label,
                                " " + file.getPath()));
                    }
                }
            }
        }

        protected void dbOneTry(ScanParameters parameters) {
            Cursor cursor = mApplicationContext.getContentResolver().query(
                    MediaStore.Files.getContentUri("external"),
                    MEDIA_PROJECTION,
                    //STAR,
                    null,
                    null,
                    null);
            int data_column =
                    cursor.getColumnIndex(MediaStore.MediaColumns.DATA);
            int modified_column =
                    cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED);
            int size_column =
                    cursor.getColumnIndex(MediaStore.MediaColumns.SIZE);
            int totalSize = cursor.getCount();
            int currentItem = 0;
            int reportFreq = 0;
            // Used to calibrate reporting frequency
            long startTime = SystemClock.currentThreadTimeMillis();
            while (cursor.moveToNext()) {
                currentItem++;
                try {
                    File file = new File(cursor.getString(data_column)).getCanonicalFile();
                    // Ignore non-file backed playlists (size == 0). Fixes playlist removal on scan
                    // for 4.1
                    boolean validSize = cursor.getInt(size_column) > 0;
                    if (validSize && (!file.exists() ||
                             file.lastModified() / 1000L >
                             cursor.getLong(modified_column))
                             && parameters.shouldScan(file, true)) {
                        // Media scanner handles these cases.
                        // Is a set, so OK if already present.
                        mFilesToProcess.add(file);
                    }
                    else {
                        // Don't want to waste time scanning an up-to-date
                        // file.
                        mFilesToProcess.remove(file);
                    }
                    if (reportFreq == 0) {
                        // Calibration phase
                        if (SystemClock.currentThreadTimeMillis() - startTime > 25) {
                            reportFreq = currentItem + 1;
                        }
                    }
                    else if (currentItem % reportFreq == 0) {
                        publishProgress(databaseUpdate(file.getPath(),
                                        (100 * currentItem) / totalSize));
                    }
                }
                catch (IOException ex) {
                    // Just ignore it for now.
                }
            }
            // Don't need the cursor any more.
            cursor.close();
        }

        @Override
        protected Void doInBackground(ScanParameters... parameters) {
            try {
                recursiveAddFiles(parameters[0].getPath(), parameters[0]);
            }
            catch (IOException Ex) {
                // Do nothing.
            }
            // Parse database
            publishProgress(stateUpdate(R.string.progress_database_label));
            boolean dbSuccess = false;
            int numRetries = 0;
            while (!dbSuccess && numRetries < DB_RETRIES) {
                dbSuccess = true;
                try {
                    dbOneTry(parameters[0]);
                }
                catch (Exception Ex) {
                    // For any of these errors, try again.
                    numRetries++;
                    dbSuccess = false;
                    if (numRetries < DB_RETRIES) {
                        publishProgress(stateUpdate(
                                R.string.db_error_retrying));
                        SystemClock.sleep(1000);
                    }
                }
            }
            if (numRetries > 0) {
                if (dbSuccess) {
                    publishProgress(debugUpdate(R.string.db_error_recovered));
                }
                else {
                    publishProgress(debugUpdate(R.string.db_error_failure));
                }
            }
            // Prepare final path list for processing.
            mPathNames = new ArrayList<String>(mFilesToProcess.size());
            Iterator<File> iterator = mFilesToProcess.iterator();
            while (iterator.hasNext()) {
                mPathNames.add(iterator.next().getPath());
            }
            mLastGoodProcessedIndex = -1;

            return null;
        }

        @Override
        protected void onProgressUpdate(ProgressUpdate... progress) {
            switch (progress[0].getType()) {
            case DATABASE:
                updateProgressText(R.string.database_proc,
                                   " " + progress[0].getString());
                updateProgressNum(progress[0].getProgress());
                break;
            case STATE:
                updateProgressText(progress[0].getResId());
                updateProgressNum(0);
                break;
            case DEBUG:
                addDebugMessage(progress[0].getResId(), progress[0].getString());
            }
        }

        @Override
        protected void onPostExecute(Void result) {
            startMediaScanner();
        }
    }

    class Updater implements Runnable {
        String mPathScanned;

        public Updater(String path) {
            mPathScanned = path;
        }

        public void run() {
            if (mLastGoodProcessedIndex + 1 < mPathNames.size() &&
                mPathNames.get(mLastGoodProcessedIndex
                              + 1).equals(mPathScanned)) {
                mLastGoodProcessedIndex++;
            }
            else {
                int newIndex = mPathNames.indexOf(mPathScanned);
                if (newIndex > -1) {
                    mLastGoodProcessedIndex = newIndex;
                }
            }
            int progress = (100 * (mLastGoodProcessedIndex + 1))
                           / mPathNames.size();
            if (progress == 100) {
                advanceScanner();
            }
            else {
                updateProgressNum(progress);
                updateProgressText(R.string.final_proc, " " + mPathScanned);
            }
        }
    }
}
