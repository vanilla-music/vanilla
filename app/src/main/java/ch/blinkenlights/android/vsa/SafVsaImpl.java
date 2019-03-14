package ch.blinkenlights.android.vsa;

import android.net.Uri;

public class SafVsaImpl implements Vsa {
	/**
	 * Parent of this instance.
	 */
	private final SafVsaImpl mParentFile;
	/**
	 * Whether or not this instance represents a directory.
	 */
	private final boolean mIsDirectory;
	/**
	 * Name of this file.
	 */
	private final String mName;
	/**
	 * Absolute path.
	 */
	private final String mAbsoultePath;

	@Override
	public Vsa[] listFiles() {
		return null;
	}

	@Override
	public String getPath() {
		return mAbsoultePath;
	}

	@Override
	public String getAbsolutePath() {
		return mAbsoultePath;
	}

	@Override
	public String getName() {
		return mName;
	}

	@Override
	public Vsa getParentFile() {
		return mParentFile;
	}

	@Override
	public boolean isDirectory() {
		return mIsDirectory;
	}
}
