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

import ch.blinkenlights.android.vsa.Vsa;
import ch.blinkenlights.android.vsa.VsaInstance;

import android.content.Context;
import android.content.Intent;
import android.os.FileObserver;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
	private static final Pattern FILE_SEPARATOR = Pattern.compile(Vsa.separator);
	private static final Pattern GUESS_MUSIC = Pattern.compile("^(.+\\.(mp3|ogg|mka|opus|flac|aac|m4a|wav))$", Pattern.CASE_INSENSITIVE);
	private static final Pattern GUESS_IMAGE = Pattern.compile("^(.+\\.(gif|jpe?g|png|bmp|tiff?))$", Pattern.CASE_INSENSITIVE);
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
	private Vsa[] mFiles;
	/**
	 * The currently active filter, entered by the user from the search box.
	 */
	String[] mFilter;
	/**
	 * Excludes dot files and files not matching mFilter.
	 */

	/**
	 * Sorts folders before files first, then sorts by current sort mode.
	 */
	private final Comparator<Vsa> mFileComparator = new Comparator<Vsa>() {
		@Override
		public int compare(Vsa a, Vsa b)
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
		mInflater = (LayoutInflater)activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		if (limiter == null) {
			limiter = buildHomeLimiter(activity);
		}
		setLimiter(limiter);
		mSortEntries = SORT_RES_IDS;
	}

	@Override
	public Object query() {
		Vsa file = getLimiterPath();

		if (mFileObserver == null) {
			mFileObserver = new Observer(file.getAbsolutePath());
		}

		Vsa[] readdir = file.listFiles();
		if (readdir == null)
			readdir = new Vsa[]{};

		// Create a filtered list of what we got from readdir.
		ArrayList<Vsa> files = new ArrayList();
		for(Vsa entry : readdir) {
			String name = entry.getName().toLowerCase();
			boolean skip = false;

			if (mFilter != null) {
				for (String term : mFilter) {
					if (!name.contains(term)) {
					    skip = true;
						break;
					}
				}
			}
			if (!skip)
				files.add(entry);
		}

		Collections.sort(files, mFileComparator);
		if (file.getParentFile() != null) {
			files.add(0, file.getParentFile());
		}
		return files.toArray(new Vsa[files.size()]);
	}

	@Override
	public void commitQuery(Object data)
	{
		mFiles = (Vsa[])data;
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
		return FileUtils.songIdFromFile(mFiles[pos]);
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

		final Vsa file = mFiles[pos];
		final String title = (pointsToParentFolder(file) ? ".." : file.getName());

		holder.id = pos;
		holder.title = title;
		row.setText(title);
		row.getCoverView().setImageResource(getImageResourceForFile(file));
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

		if (limiter != null && limiter.data == null)
			limiter = null; // Filtering the fs root is like having no filter

		mLimiter = limiter;
	}

	@Override
	public Limiter getLimiter()
	{
		return mLimiter;
	}

	/**
	 * Returns a drawable resource id for given file.
	 * This function is rather fast as the file type is guessed
	 * based on the extension.
	 *
	 * @return resource id to use.
	 */
	private int getImageResourceForFile(Vsa file) {
		int res = R.drawable.file_document;
		if (pointsToParentFolder(file)) {
			res = R.drawable.arrow_up;
		} else if (file.isDirectory()) {
			res = R.drawable.folder;
		} else if (GUESS_MUSIC.matcher(file.getName()).matches()) {
			res = R.drawable.file_music;
		} else if (GUESS_IMAGE.matcher(file.getName()).matches()) {
			res = R.drawable.file_image;
		}
		return res;
	}

	/**
	 * Returns the path represented by this limiter
	 *
	 * @return the file of this limiter represents
	 */
	private Vsa getLimiterPath() {
		String path = (String)mLimiter.data;
		if (path == null)
			path = "/";
		return VsaInstance.fromPath(path);
	}

	/**
	 * Returns true if the filename of 'file' indicates that
	 * it points to the parent folder of the limiter.
	 *
	 * @return true if given file points to the parent folder
	 */
	private boolean pointsToParentFolder(Vsa file) {
		Vsa parent = getLimiterPath().getParentFile();
		return file.equals(parent);
	}

	/**
	 * Builds a limiter from the given folder. Only files contained in the
	 * given folder will be shown if the limiter is set on this adapter.
	 *
	 * @param file A File pointing to a folder.
	 * @return A limiter describing the given folder.
	 */
	public static Limiter buildLimiter(Vsa file) {
		String[] fields = FILE_SEPARATOR.split(file.getAbsolutePath().substring(1));
		return new Limiter(MediaUtils.TYPE_FILE, fields, file.getAbsolutePath());
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
	public Limiter buildLimiter(long id) {
		for (int i = 0; i < mFiles.length; i++) {
			if (id == getItemId(i)) {
				return buildLimiter(mFiles[i]);
			}
		}
		return null;
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
		Vsa file = mFiles[(int)holder.id];

		Intent intent = new Intent();
		intent.putExtra(LibraryAdapter.DATA_TYPE, MediaUtils.TYPE_FILE);
		intent.putExtra(LibraryAdapter.DATA_ID, getItemId((int)holder.id));
		intent.putExtra(LibraryAdapter.DATA_TITLE, holder.title);
		intent.putExtra(LibraryAdapter.DATA_EXPANDABLE, file.isDirectory());
		intent.putExtra(LibraryAdapter.DATA_FILE, file.getAbsolutePath());
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
		Vsa path = getLimiterPath();
		return MediaUtils.buildFileQuery(path.getAbsolutePath(), projection, true);
	}

	/**
	 * A row was clicked: this was dispatched by LibraryPagerAdapter
	 *
	 * @param intent likely created by createData()
	 */
	public void onItemClicked(Intent intent) {
		boolean isFolder = intent.getBooleanExtra(LibraryAdapter.DATA_EXPANDABLE, false);
		boolean isHeader = intent.getLongExtra(LibraryAdapter.DATA_ID, LibraryAdapter.INVALID_ID)
			== LibraryAdapter.HEADER_ID;

		if (!isHeader && FileUtils.canDispatchIntent(intent) && FileUtils.dispatchIntent(mActivity, intent))
			return;

		if (isFolder) {
			mActivity.onItemExpanded(intent);
		} else {
			mActivity.onItemClicked(intent);
		}
	}

	/**
	 * Context menu of a row: this was dispatched by LibraryPagerAdapter
	 *
	 * @param intent likely created by createData()
	 * @param view the parent view
	 * @param x x-coords of event
	 * @param y y-coords of event
	 */
	public boolean onCreateFancyMenu(Intent intent, View view, float x, float y) {
		String path = intent.getStringExtra(LibraryAdapter.DATA_FILE);
		boolean isParentRow = (path != null && pointsToParentFolder(VsaInstance.fromPath(path)));

		if (!isParentRow)
			return mActivity.onCreateFancyMenu(intent, view, x, y);
		// else: no context menu, but consume event.
		return true;
	}
}
