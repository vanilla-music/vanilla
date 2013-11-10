/*
 * Copyright (C) 2010, 2011 Christopher Eby <kreed@kreed.org>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package ch.blinkenlights.android.vanilla;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.os.FileObserver;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Pattern;

/**
 * A list adapter that provides a view of the filesystem. The active directory
 * is set through a {@link Limiter} and rows are displayed using MediaViews.
 */
public class FileSystemAdapter
	extends BaseAdapter
	implements LibraryAdapter
	         , View.OnClickListener
{
	private static final Pattern SPACE_SPLIT = Pattern.compile("\\s+");
	private static final Pattern FILE_SEPARATOR = Pattern.compile(File.separator);

	/**
	 * The owner LibraryActivity.
	 */
	final LibraryActivity mActivity;
	/**
	 * A LayoutInflater to use.
	 */
	private final LayoutInflater mInflater;
	/**
	 * The currently active limiter, set by a row expander being clicked.
	 */
	private Limiter mLimiter;
	/**
	 * The files and folders in the current directory.
	 */
	private File[] mFiles;
	/**
	 * The folder icon shown for folder rows.
	 */
	private final Drawable mFolderIcon;
	/**
	 * The currently active filter, entered by the user from the search box.
	 */
	String[] mFilter;
	/**
	 * Excludes dot files and files not matching mFilter.
	 */
	private final FilenameFilter mFileFilter = new FilenameFilter() {
		@Override
		public boolean accept(File dir, String filename)
		{
			if (filename.charAt(0) == '.')
				return false;
			if (mFilter != null) {
				filename = filename.toLowerCase();
				for (String term : mFilter) {
					if (!filename.contains(term))
						return false;
				}
			}
			return true;
		}
	};
	/**
	 * Sorts folders before files first, then sorts alphabetically by name.
	 */
	private final Comparator<File> mFileComparator = new Comparator<File>() {
		@Override
		public int compare(File a, File b)
		{
			boolean aIsFolder = a.isDirectory();
			boolean bIsFolder = b.isDirectory();
			if (bIsFolder == aIsFolder) {
				return a.getName().compareToIgnoreCase(b.getName());
			} else if (bIsFolder) {
				return 1;
			}
			return -1;
		}
	};
	/**
	 * The Observer instance for the current directory.
	 */
	private Observer mFileObserver;

	/**
	 * Create a FileSystemAdapter.
	 *
	 * @param activity The LibraryActivity that will contain this adapter.
	 * Called on to requery this adapter when the contents of the directory
	 * change.
	 * @param limiter An initial limiter to set. If none is given, will be set
	 * to the external storage directory.
	 */
	public FileSystemAdapter(LibraryActivity activity, Limiter limiter)
	{
		mActivity = activity;
		mLimiter = limiter;
		mFolderIcon = activity.getResources().getDrawable(R.drawable.folder);
		mInflater = (LayoutInflater)activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		if (limiter == null) {
			limiter = buildLimiter( activity.getFilesystemBrowseStart() );
		}
		setLimiter(limiter);
	}

	@Override
	public Object query()
	{
		File file = mLimiter == null ? new File("/") : (File)mLimiter.data;

		if (mFileObserver == null) {
			mFileObserver = new Observer(file.getPath());
		}

		File[] files = file.listFiles(mFileFilter);
		if (files != null)
			Arrays.sort(files, mFileComparator);
		return files;
	}

	@Override
	public void commitQuery(Object data)
	{
		mFiles = (File[])data;
		notifyDataSetInvalidated();
	}

	@Override
	public void clear()
	{
		mFiles = null;
		notifyDataSetInvalidated();
	}

	@Override
	public int getCount()
	{
		if (mFiles == null)
			return 0;
		return mFiles.length;
	}

	@Override
	public Object getItem(int pos)
	{
		return mFiles[pos];
	}

	@Override
	public long getItemId(int pos)
	{
		return pos;
	}

	private static class ViewHolder {
		public int id;
		public TextView text;
		public View divider;
		public ImageView arrow;
	}

	@Override
	public View getView(int pos, View convertView, ViewGroup parent)
	{
		View view;
		ViewHolder holder;

		if (convertView == null) {
			view = mInflater.inflate(R.layout.library_row_expandable, null);
			holder = new ViewHolder();
			holder.text = (TextView)view.findViewById(R.id.text);
			holder.divider = view.findViewById(R.id.divider);
			holder.arrow = (ImageView)view.findViewById(R.id.arrow);
			holder.text.setOnClickListener(this);
			holder.arrow.setOnClickListener(this);
			view.setTag(holder);
		} else {
			view = convertView;
			holder = (ViewHolder)view.getTag();
		}

		File file = mFiles[pos];
		boolean isDirectory = file.isDirectory();
		holder.id = pos;
		holder.text.setText(file.getName());
		holder.divider.setVisibility(isDirectory ? View.VISIBLE : View.GONE);
		holder.arrow.setVisibility(isDirectory ? View.VISIBLE : View.GONE);
		holder.text.setCompoundDrawablesWithIntrinsicBounds(isDirectory ? mFolderIcon : null, null, null, null);
		return view;
	}

	@Override
	public void setFilter(String filter)
	{
		if (filter == null)
			mFilter = null;
		else
			mFilter = SPACE_SPLIT.split(filter.toLowerCase());
	}

	@Override
	public void setLimiter(Limiter limiter)
	{
		if (mFileObserver != null)
			mFileObserver.stopWatching();
		mFileObserver = null;
		mLimiter = limiter;
	}

	@Override
	public Limiter getLimiter()
	{
		return mLimiter;
	}

	/**
	 * Builds a limiter from the given folder. Only files contained in the
	 * given folder will be shown if the limiter is set on this adapter.
	 *
	 * @param file A File pointing to a folder.
	 * @return A limiter describing the given folder.
	 */
	public static Limiter buildLimiter(File file)
	{
		String[] fields = FILE_SEPARATOR.split(file.getPath().substring(1));
		return new Limiter(MediaUtils.TYPE_FILE, fields, file);
	}

	@Override
	public Limiter buildLimiter(long id)
	{
		return buildLimiter(mFiles[(int)id]);
	}

	@Override
	public int getMediaType()
	{
		return MediaUtils.TYPE_FILE;
	}

	/**
	 * FileObserver that reloads the files in this adapter.
	 */
	private class Observer extends FileObserver {
		public Observer(String path)
		{
			super(path, FileObserver.CREATE | FileObserver.DELETE | FileObserver.MOVED_TO | FileObserver.MOVED_FROM);
			startWatching();
		}

		@Override
		public void onEvent(int event, String path)
		{
			mActivity.mPagerAdapter.postRequestRequery(FileSystemAdapter.this);
		}
	}

	@Override
	public Intent createData(View view)
	{
		ViewHolder holder = (ViewHolder)view.getTag();
		File file = mFiles[holder.id];

		Intent intent = new Intent();
		intent.putExtra(LibraryAdapter.DATA_TYPE, MediaUtils.TYPE_FILE);
		intent.putExtra(LibraryAdapter.DATA_ID, (long)holder.id);
		intent.putExtra(LibraryAdapter.DATA_TITLE, holder.text.getText().toString());
		intent.putExtra(LibraryAdapter.DATA_EXPANDABLE, file.isDirectory());

		String path;
		try {
			path = file.getCanonicalPath();
		} catch (IOException e) {
			path = file.getAbsolutePath();
			Log.e("VanillaMusic", "Failed to canonicalize path", e);
		}
		intent.putExtra(LibraryAdapter.DATA_FILE, path);
		return intent;
	}

	@Override
	public void onClick(View view)
	{
		Intent intent = createData((View)view.getParent());
		boolean isFolder = intent.getBooleanExtra(LibraryAdapter.DATA_EXPANDABLE, false);
		if (isFolder) {
			mActivity.onItemExpanded(intent);
		} else {
			mActivity.onItemClicked(intent);
		}
	}
}
