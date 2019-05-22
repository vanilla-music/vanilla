package ch.blinkenlights.android.vsa;

import androidx.documentfile.provider.DocumentFile;

import android.content.Context;
import android.net.Uri;

import java.util.ArrayList;
import java.io.File;

import android.util.Log;

public class SafVsaImpl implements Vsa {
	/**
	 * Context of this class.
	 */
	private final Context mContext;
	/**
	 * Backing document file of this instance.
	 */
	private final DocumentFile mDocumentFile;
	/**
	 * Absolute path of this DocumentFile.
	 */
	private final String mAbsolutePath;

	SafVsaImpl(Context context, Vsa vsa) {
		this(context, vsa.getAbsolutePath());
	}

	SafVsaImpl(Context context, String path) {
		mContext = context;

		File file = new File(path);
		String rootGrant = "content://com.android.externalstorage.documents/tree/primary%3A";

		mAbsolutePath = file.getAbsolutePath();
		if (mAbsolutePath.equals("/")) {
			/*
			 * Wir müssen beim rootGrant starten: URLs assembeln scheint nicht zu funktionieren.
			 */
			Uri uri = Uri.parse(rootGrant);
			mDocumentFile = DocumentFile.fromTreeUri(context, uri).findFile("Download");
		} else {
			mDocumentFile = DocumentFile.fromFile(file);
		}
	}

	@Override
	public String[] list() {
		ArrayList<String> list = new ArrayList();
		for (DocumentFile d : mDocumentFile.listFiles()) {
			list.add(d.getName());
			Log.v("VanillaMusic", "SAF::list() "+d.getName());
		}
		return list.toArray(new String[list.size()]);
	}

	@Override
	public Vsa[] listFiles() {
		ArrayList<Vsa> list = new ArrayList();
		for (DocumentFile d : mDocumentFile.listFiles()) {
			String path = mAbsolutePath + "/" + d.getName();
			list.add(new SafVsaImpl(mContext, path));
			Log.v("VanillaMusic", "SAF::listFiles "+path+", uri = "+d.getUri());
		}
		return list.toArray(new Vsa[list.size()]);
	}

	@Override
	public String getAbsolutePath() {
		return mAbsolutePath;
	}

	@Override
	public String getName() {
		return mDocumentFile.getName();
	}

	@Override
	public String getParent() {
		File file = new File(mAbsolutePath);
		return file.getParent();
	}

	@Override
	public Vsa getParentFile() {
		String parent = getParent();
		if (parent == null)
			return null;
		return new SafVsaImpl(mContext, parent);
	}

	@Override
	public boolean isDirectory() {
		return mDocumentFile.isDirectory();
	}

	@Override
	public long length() {
		return mDocumentFile.length();
	}

	@Override
	public long lastModified() {
		return mDocumentFile.lastModified();
	}

	@Override
	public boolean equals(Object file) {
		if (file == null || !(file instanceof SafVsaImpl))
			return false;
		return getAbsolutePath().equals(((Vsa)file).getAbsolutePath());
		}
}
