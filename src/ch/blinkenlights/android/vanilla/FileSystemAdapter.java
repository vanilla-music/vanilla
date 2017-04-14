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
	extends SortableAdapter
	implements LibraryAdapter
{
	private static final Pattern SPACE_SPLIT = Pattern.compile("\\s+");
	private static final Pattern FILE_SEPARATOR = Pattern.compile(File.separator);
	private static final Pattern GUESS_MUSIC = Pattern.compile("^([^\\.]+|.+\\.(mp3|ogg|mka|opus|flac|aac|m4a|wav))$", Pattern.CASE_INSENSITIVE);
	private static final Pattern GUESS_IMAGE = Pattern.compile("^([^\\.]+|.+\\.(gif|jpe?g|png|bmp|tiff?))$", Pattern.CASE_INSENSITIVE);

	/**
	 * Sort by filename.
	 */
	private static final int SORT_NAME = 0;
	/**
	 * Sort by file size.
	 */
	private static final int SORT_SIZE = 1;
	/**
	 * Sort by file modification time.
	 */
	private static final int SORT_TIME = 2;
	/**
	 * Sort by file extension.
	 */
	private static final int SORT_EXT = 3;
	/**
	 * The IDs of human-readable descriptions for each sort mode.
	 * Must be consistent with SORT_* fields.
	 */
	private static final int[] SORT_RES_IDS = new int[] {
			R.string.filename,
			R.string.file_size,
			R.string.file_time,
			R.string.extension };

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
	 * Sorts folders before files first, then sorts by current sort mode.
	 */
	private final Comparator<File> mFileComparator = new Comparator<File>() {
		@Override
		public int compare(File a, File b)
		{
			boolean aIsFolder = a.isDirectory();
			boolean bIsFolder = b.isDirectory();
			if (bIsFolder == aIsFolder) {
				int mode = aIsFolder ? SORT_NAME : getSortModeIndex();
				int order;
				switch (mode) {
					case SORT_SIZE:
						order = Long.valueOf(a.length()).compareTo(Long.valueOf(b.length()));
						break;
					case SORT_TIME:
						order = Long.valueOf(a.lastModified())
								.compareTo(Long.valueOf(b.lastModified()));
						break;
					case SORT_EXT:
						order = FileUtils.getFileExtension(a.getName())
								.compareToIgnoreCase(FileUtils.getFileExtension(b.getName()));
						break;
					case SORT_NAME:
						order = a.getName().compareToIgnoreCase(b.getName());
						break;
					default:
						throw new IllegalArgumentException("Invalid sort mode: " + mode);
				}
				return (isSortDescending() ? -1 : 1) * order;
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
			limiter = buildHomeLimiter(activity);
		}
		setLimiter(limiter);
		mSortEntries = SORT_RES_IDS;
	}

	@Override
	public Object query()
	{
		File file = getLimiterPath();

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
		} else {
			row = (DraggableRow)convertView;
			holder = (ViewHolder)row.getTag();
		}

		holder.id = pos;

		final File file = mFiles[pos];
		row.getTextView().setText(file.getName());
		row.showDragger(file.isDirectory());
		row.getCoverView().setImageDrawable(getDrawableForFile(file));
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
	 * Returns a drawable for given file.
	 * This function is rather fast as the file type is guessed
	 * based on the extension.
	 *
	 * @return drawable for the guessed mime type
	 */
	private Drawable getDrawableForFile(File file) {
		int res = R.drawable.file_document;
		if (file.isDirectory()) {
			res = R.drawable.folder;
		} else if (GUESS_MUSIC.matcher(file.getName()).matches()) {
			res = R.drawable.file_music;
		} else if (GUESS_IMAGE.matcher(file.getName()).matches()) {
			res = R.drawable.file_image;
		}
		return mActivity.getResources().getDrawable(res);
	}

	/**
	 * Returns the unixpath represented by this limiter
	 *
	 * @return the file of this limiter represents
	 */
	private File getLimiterPath() {
		return mLimiter == null ? new File("/") : (File)mLimiter.data;
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

	/**
	 * Builds the limiter pointing to our home directory
	 *
	 * @param context the context to use
	 * @return A limiter which is configured as 'home' directory
	 */
	public static Limiter buildHomeLimiter(Context context) {
		return buildLimiter(FileUtils.getFilesystemBrowseStart(context));
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
			if (path != null) // Android bug? We seem to receive MOVE_SELF events
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

	@Override
	public int getDefaultSortMode() {
		return SORT_NAME;
	}

	@Override
	public String getSortSettingsKey() {
		return "sort_filesystem";
	}

	/**
	 * Returns all songs represented by this adapter.
	 * Note that this will do a recursive query!
	 *
	 * @param projection the projection to use
	 * @return a query task
	 */
	@Override
	public QueryTask buildSongQuery(String[] projection) {
		File path = getLimiterPath();
		return MediaUtils.buildFileQuery(path.getPath(), projection);
	}

	/**
	 * A row was clicked: this was dispatched by LibraryPagerAdapter
	 *
	 * @param intent likely created by createData()
	 */
	public void onItemClicked(Intent intent) {
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
