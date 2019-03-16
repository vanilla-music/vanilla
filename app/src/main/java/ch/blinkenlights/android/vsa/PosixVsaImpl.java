package ch.blinkenlights.android.vsa;

import java.io.File;
import java.util.ArrayList;

public class PosixVsaImpl implements Vsa {
	/**
	 * Backing file of this instance.
	 */
	private final File mFile;

	PosixVsaImpl(Vsa vsa) {
		this(vsa.getAbsolutePath());
	}

	PosixVsaImpl(String path) {
		mFile = new File(path);
	}

	@Override
	public String[] list() {
		return mFile.list();
	}

	@Override
	public Vsa[] listFiles() {
		ArrayList<Vsa> list = new ArrayList();
		String[] dirents = list();
		if (dirents == null)
			return null;

		for (String entry : dirents) {
			list.add(new PosixVsaImpl(getAbsolutePath() + Vsa.separator + entry));
		}
		return list.toArray(new Vsa[list.size()]);
	}

	@Override
	public String getAbsolutePath() {
		return mFile.getAbsolutePath();
	}

	@Override
	public String getName() {
		return mFile.getName();
	}

	@Override
	public String getParent() {
		return mFile.getParent();
	}

	@Override
	public Vsa getParentFile() {
		String parent = getParent();
		if (parent == null)
			return null;
		return new PosixVsaImpl(parent);
	}

	@Override
	public boolean isDirectory() {
		return mFile.isDirectory();
	}

	@Override
	public long length() {
		return mFile.length();
	}

	@Override
	public long lastModified() {
		return mFile.lastModified();
	}

	@Override
	public boolean equals(Object file) {
		if (file == null || !(file instanceof PosixVsaImpl))
			return false;
		return getAbsolutePath().equals(((Vsa)file).getAbsolutePath());
		}
}
