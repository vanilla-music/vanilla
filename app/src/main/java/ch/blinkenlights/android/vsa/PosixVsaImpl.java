package ch.blinkenlights.android.vsa;

import android.net.Uri;
import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;


public class PosixVsaImpl implements Vsa {
	/**
	 * Absolute path.
	 */
	private final ArrayList<String> mAbsoultePath;

	PosixVsaImpl(Vsa vsa) {
		this(vsa.getAbsolutePath());
	}

	PosixVsaImpl(String path) {
		if (path.length() == 0) {
			throw new IllegalArgumentException("supplied path must not be empty.");
		}
		if (!path.startsWith("/")) {
			throw new IllegalArgumentException("must be an absolute path.");
		}

		ArrayList<String> parts = new ArrayList();
		for (String part : path.split("/")) {
			if (part.length() > 0) {
				parts.add(part);
			}
		}
		mAbsoultePath = parts;
	}

	@Override
	public String[] list() {
		final File file = new File(getAbsolutePath());
		return file.list();
	}

	@Override
	public String getAbsolutePath() {
		return TextUtils.join("/", mAbsoultePath.toArray());
	}

	@Override
	public String getName() {
		return mAbsoultePath.get(mAbsoultePath.size() - 1);
	}

	@Override
	public String getParent() {
		ArrayList<String> path = new ArrayList(mAbsoultePath);
		if (path.size() < 2) {
			return getAbsolutePath();
		}

		path.remove(path.size() - 1);
		return TextUtils.join("/", path.toArray());
	}

	@Override
	public boolean isDirectory() {
		final File file = new File(getAbsolutePath());
		return file.isDirectory();
	}
}
