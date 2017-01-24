/*
 * Copyright (C) 2015 - 2017 Adrian Ulrich <adrian@blinkenlights.ch>
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
	 * Our message handler
	 */
	private Handler mHandler;
	/**
	 * The global (current) file input stream
	 */
	private FileInputStream mInputStream;
	/**
	 * The filesystem path used to create the current mInputStream
	 */
	private String mPath;
	/**
	 * The calculated delay between `BYTES_PER_READ' sized
	 * read operations
	 */
	private long mReadDelay;
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
		mHandler.removeMessages(MSG_SET_SONG);
		mHandler.removeMessages(MSG_READ_CHUNK);
	}

	/**
	 * Starts a new readahead operation. Will resume if `song.path' equals
	 * the currently open file
	 *
	 * @param path The path to read ahead
	 */
	public void setSong(final Song song) {
		pause(); // cancell all in-flight rpc's
		mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_SET_SONG, song), 1000);
	}

	private static final int MSG_SET_SONG = 1;
	private static final int MSG_READ_CHUNK = 2;
	@Override
	public boolean handleMessage(Message message) {
		switch (message.what) {
			case MSG_SET_SONG: {
				Song song = (Song)message.obj;

				if (mInputStream != null && !mPath.equals(song.path)) {
					// current file does not match requested one: clean it
					try {
						mInputStream.close();
					} catch (IOException e) {
						Log.e("VanillaMusic", "Failed to close file: "+e);
					}
					mPath = null;
					mReadDelay = 0;
					mInputStream = null;
				}

				if (mInputStream == null) {
					// need to open new input stream
					try {
						mPath = song.path;
						mInputStream = new FileInputStream(mPath);
						double requiredReads = mInputStream.available() / BYTES_PER_READ;

						if (requiredReads > 1) {
							mReadDelay = (long)(0.90 * song.duration / requiredReads); // run ~10% ahead
						}
					} catch (FileNotFoundException e) {
						Log.e("VanillaMusic", "Failed to song "+ song +": "+e);
					} catch (IOException e) {
						Log.e("VanillaMusic", "IO Exception on "+ song +": "+e);
					}
				}

				if (mInputStream != null) {
					mHandler.sendEmptyMessage(MSG_READ_CHUNK);
				}
				break;
			}
			case MSG_READ_CHUNK: {
				int bytesRead = -1;
				try {
					bytesRead = mInputStream.read(mScratch);
				} catch (IOException e) {
					// fs error or eof: stop in any case
				}
				if (bytesRead >= 0) {
					mHandler.sendEmptyMessageDelayed(MSG_READ_CHUNK, mReadDelay);
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
