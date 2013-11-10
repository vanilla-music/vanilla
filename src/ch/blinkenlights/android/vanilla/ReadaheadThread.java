/*
 * Copyright (C) 2013 Adrian Ulrich <adrian@blinkenlights.ch>
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

import android.util.Log;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

class ReadaheadThread extends Thread {
	private String mCurrentPath = null;
	private boolean mPaused = true;
	private Thread mThread;


	public ReadaheadThread() {
	}

	/**
	 * Starts the persistent RA-Thread. This thread is never stopped: Destroying + creating
	 * a new thread just because the user clicked on 'pause' doesn't make much sense
	 */
	public void start() {
		mThread = new Thread(new Runnable() {
			public void run() {
				threadWorker();
			}
		});
		mThread.start();
	}

	/**
	 * Updates the current read-ahead source and wakes up the ra-thread
	 */
	public void setSource(String path) {
		mCurrentPath = path;
		mPaused = false;
		mThread.interrupt();
	}

	/**
	 * Tells the ra-thread to pause the read-ahead work
	 * Calling setSource with path = mCurrentPath will cause the
	 * thread to RESUME (the file is not closed by calling pause())
	 */
	public void pause() {
		mPaused = true;
	}

	/**
	 * Sleep for x milli seconds
	 */
	private static void sleep(int millis) {
		try { Thread.sleep(millis); }
		catch(InterruptedException e) {}
	}

	/**
	 * Our thread mainloop
	 * This thread will read from mCurrentPath until
	 * we hit an EOF or mPaused is set to false.
	 * The readahead speed is controlled by 'sleepTime'
	 */
	private void threadWorker() {
		String path = null;
		FileInputStream fis = null;
		byte[] scratch = new byte[8192];             // Read 8kB per call to read()
		int sleepTime = (int)((1f/(256f/8f))*1000f); // We try to read 256kB/s (with 8kB blocks)

		for(;;) {
			if(mPaused) {
				sleep(600*1000); /* Sleep 10 minutes */
				continue;
			}

			if(path != mCurrentPath) {
				// File changed: First we try to close the old FIS
				// fis can be null or already closed, we therefore do
				// not care about the result. (the GC would take care of it anyway)
				try { fis.close(); } catch(Exception e) {}
				// We can now try to open the new file.
				// Errors are not fatal, we will simply switch into paused-mode
				try {
					fis = new FileInputStream(mCurrentPath);
					path = mCurrentPath;
					Log.v("VanillaMusic", "readahead of "+path+" starts");
				} catch(FileNotFoundException e) {
					path = null;
					mPaused = true;
					continue;
				}
			}

			// 'fis' is now an open FileInputStream. Read 8kB per go until
			// we hit EOF
			try {
				int br = fis.read(scratch);
				if(br < 0) { // no more data -> EOF
					mPaused = true;
					Log.v("VanillaMusic", "readahead of "+path+" finished");
				}
			} catch(IOException e) {
				path = null; // io error?! switch into paused mode
				mPaused = true;
			}
			sleep(sleepTime);
		}
	}
	
}
