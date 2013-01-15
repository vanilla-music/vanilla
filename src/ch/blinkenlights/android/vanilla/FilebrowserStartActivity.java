/*
 * Copyright (C) 2013 Adrian Ulrich <adrian@blinkenlights.ch>
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

import java.util.ArrayList;
import java.util.Arrays;
import java.io.File;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Button;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;
import android.content.SharedPreferences;


public class FilebrowserStartActivity extends PlaybackActivity {
	
	private ListView mListView;
	private TextView mPathDisplay;
	private Button mSaveButton;
	private FilebrowserStartAdapter mListAdapter;
	private String mCurrentPath;
	private SharedPreferences.Editor mPrefEditor;
	
	@Override  
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setTitle(R.string.filebrowser_start);
		setContentView(R.layout.filebrowser_content);
		
		mCurrentPath = (String)getFilesystemBrowseStart().getAbsolutePath();
		mPrefEditor  = PlaybackService.getSettings(this).edit();
		mListAdapter = new FilebrowserStartAdapter(this, R.layout.showqueue_row);
		mPathDisplay = (TextView) findViewById(R.id.path_display);
		mListView    = (ListView) findViewById(R.id.list);
		mSaveButton  = (Button) findViewById(R.id.save_button);
		
		mListView.setAdapter(mListAdapter);
		mListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				jumpToDirectory(position);
			}});
		
		mSaveButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				mPrefEditor.putString("filesystem_browse_start", mCurrentPath);
				mPrefEditor.commit();
				finish();
			}});
	}
	
	/*
	** Called when we are displayed (again)
	** This will always refresh the whole song list
	*/
	@Override
	public void onResume() {
		super.onResume();
		refreshDirectoryList();
	}
	
	/*
	** Enters selected directory at 'pos'
	*/
	private void jumpToDirectory(int pos) {
		String dirent = mListAdapter.getItem(pos);
		
		if(pos == 0) {
			mCurrentPath = (new File(mCurrentPath)).getParent();
		}
		else {
			mCurrentPath += "/" + dirent;
		}
		
		/* let java fixup any strange paths */
		mCurrentPath = (new File(mCurrentPath == null ? "/" : mCurrentPath)).getAbsolutePath();
		
		refreshDirectoryList();
	}
	
	/*
	** display mCurrentPath in the dialog
	*/
	private void refreshDirectoryList() {
		File path = new File(mCurrentPath);
		File[]dirs = path.listFiles();
		
		mListAdapter.clear();
		mListAdapter.add("../");
		
		if(dirs != null) {
			Arrays.sort(dirs);
			for(File fentry: dirs) {
				if(fentry.isDirectory()) {
					mListAdapter.add(fentry.getName());
				}
			}
		}
		else {
			Toast.makeText(this, "Failed to display "+mCurrentPath, Toast.LENGTH_SHORT).show();
		}
		mPathDisplay.setText(mCurrentPath);
		mListView.setSelectionFromTop(0, 0);
	}
	
}
