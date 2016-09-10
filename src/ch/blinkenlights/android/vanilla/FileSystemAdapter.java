/*
 * Copyright (C) 2010, 2011 Christopher Eby <kreed@kreed.org>
 * Copyright (C) 2015-2016 Adrian Ulrich <adrian@blinkenlights.ch>
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
			limiter = buildLimiter( FileUtils.getFilesystemBrowseStart(activity) );
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
		notifyDataSetChanged();
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

	@Override
	public View getView(int pos, View convertView, ViewGroup parent)
	{
		DraggableRow row;
		ViewHolder holder;

		if (convertView == null) {
			row = (DraggableRow)mInflater.inflate(R.layout.draggable_row, parent, false);
			row.setupLayout(DraggableRow.LAYOUT_LISTVIEW);

			holder = new ViewHolder();
			row.setTag(holder);
			row.getCoverView().setImageDrawable(mFolderIcon);
		} else {
			row = (DraggableRow)convertView;
			holder = (ViewHolder)row.getTag();
		}

		File file = mFiles[pos];
		boolean isDirectory = file.isDirectory();
		holder.id = pos;

		row.getTextView().setText(file.getName());
		row.getCoverView().setVisibility(isDirectory ? View.VISIBLE : View.GONE);
		row.showDragger(isDirectory);
		return row;
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
		// If the settings tell us to limit the browser to the home directory and its descendants.
		if (FileUtils.getFilesystemBrowseStartLimit(mActivity)) {
			if (limiter == null) {
				return;
			}

			// Don't allow to get past BrowseStart
			if (mLimiter != null) {
				File browseStart = FileUtils.getFilesystemBrowseStart(mActivity);

				File newLimiterFile = (File)limiter.data;

				boolean isDescendantOfBrowserStart = false;

				// If the new limiter is browseStart itself, consider it a descendant.
				if (newLimiterFile.compareTo(browseStart) == 0) {
					isDescendantOfBrowserStart = true;
				}
				else {
					File currentParent = newLimiterFile.getParentFile();
					while (currentParent != null) {
						if (currentParent.compareTo(browseStart) == 0) {
							isDescendantOfBrowserStart = true;
							break;
						}

						currentParent = currentParent.getParentFile();
					}
				}

				// Don't allow navigating to non-descendants of BrowseStart.
				if (!isDescendantOfBrowserStart)
				{
					// Default back to BrowseStart
					limiter = buildLimiter(browseStart);
				}
			}
		}


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
		File file = mFiles[(int)holder.id];

		Intent intent = new Intent();
		intent.putExtra(LibraryAdapter.DATA_TYPE, MediaUtils.TYPE_FILE);
		intent.putExtra(LibraryAdapter.DATA_ID, holder.id);
		intent.putExtra(LibraryAdapter.DATA_TITLE, ((DraggableRow)view).getTextView().getText().toString());
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

	/**
	 * A row was clicked: this was dispatched by LibraryPagerAdapter
	 *
	 * @param View view which was clicked
	 */
	public void onViewClicked(View view) {
		Intent intent = createData(view);
		boolean isFolder = intent.getBooleanExtra(LibraryAdapter.DATA_EXPANDABLE, false);

		if (FileUtils.canDispatchIntent(intent) && FileUtils.dispatchIntent(mActivity, intent))
			return;

		if (isFolder) {
			mActivity.onItemExpanded(intent);
		} else {
			mActivity.onItemClicked(intent);
		}
	}
}
