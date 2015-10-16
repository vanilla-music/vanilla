/*
 * Copyright (C) 2015 Adrian Ulrich <adrian@blinkenlights.ch>
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

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

class ReadaheadThread implements Handler.Callback {

	/**
	 * How many bytes we are going to read per run
	 */
	private static final int BYTES_PER_READ = 32768;
	/**
	 * How many milliseconds to wait between reads. 125*32768 = ~256kb/s. This should be fast enough for flac files
	 */
	private static final int MS_DELAY_PER_READ = 125;
	/**
	 * Our message handler
	 */
	private Handler mHandler;
	/**
	 * The global (current) file input stream
	 */
	private FileInputStream mFis;
	/**
	 * The filesystem path used to create the current mFis
	 */
	private String mPath;
	/**
	 * Scratch space to read junk data
	 */
	private byte[] mScratch;


	public ReadaheadThread() {
		mScratch = new byte[BYTES_PER_READ];
		HandlerThread handlerThread = new HandlerThread("ReadaheadThread", Process.THREAD_PRIORITY_LOWEST);
		handlerThread.start();
		mHandler = new Handler(handlerThread.getLooper(), this);
	}

	/**
	 * Aborts all current in-flight RPCs, pausing the readahead operation
	 */
	public void pause() {
		mHandler.removeMessages(MSG_SET_PATH);
		mHandler.removeMessages(MSG_READ_CHUNK);
	}

	/**
	 * Starts a new readahead operation. Will resume if `path' equals
	 * the currently open file
	 *
	 * @param path The path to read ahead
	 */
	public void setSource(String path) {
		pause(); // cancell all in-flight rpc's
		mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_SET_PATH, path), 1000);
	}

	private static final int MSG_SET_PATH = 1;
	private static final int MSG_READ_CHUNK = 2;
	@Override
	public boolean handleMessage(Message message) {
		switch (message.what) {
			case MSG_SET_PATH: {
				String path = (String)message.obj;

				if (mFis != null && mPath.equals(path) == false) {
					// current file does not match requested one: clean it
					try {
						mFis.close();
					} catch (IOException e) {
						Log.e("VanillaMusic", "Failed to close file: "+e);
					}
					mFis = null;
					mPath = null;
				}

				if (mFis == null) {
					// need to open new input stream
					try {
						FileInputStream fis = new FileInputStream(path);
						mFis = fis;
						mPath = path;
					} catch (FileNotFoundException e) {
						Log.e("VanillaMusic", "Failed to open file "+path+": "+e);
					}
				}

				if (mFis != null) {
					mHandler.sendEmptyMessage(MSG_READ_CHUNK);
				}
				break;
			}
			case MSG_READ_CHUNK: {
				int bytesRead = -1;
				try {
					bytesRead = mFis.read(mScratch);
				} catch (IOException e) {
					// fs error or eof: stop in any case
				}
				if (bytesRead >= 0) {
					mHandler.sendEmptyMessageDelayed(MSG_READ_CHUNK, MS_DELAY_PER_READ);
				} else {
					Log.d("VanillaMusic", "Readahead for "+mPath+" finished");
				}
			}
			default: {
				break;
			}
		}
		return true;
	}

}
