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
		mAbsolutePath = path;

		Uri uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMusic");
		DocumentFile df = DocumentFile.fromTreeUri(context, uri);
		if (!mAbsolutePath.equals("/")) {
			for (String d : mAbsolutePath.split("/")) {
				if ("".equals(d))
					continue;
				if (df == null)
					break;
				df = df.findFile(d);
			}
		}

		if (df == null)
			df = DocumentFile.fromFile(new File("/dev/null"));

		mDocumentFile = df;
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
			File path = new File(new File(mAbsolutePath), d.getName());
			list.add(new SafVsaImpl(mContext, path.getPath()));
			Log.v("VanillaMusic", "SAF::listFiles "+path.getPath()+", uri = "+d.getUri());
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
