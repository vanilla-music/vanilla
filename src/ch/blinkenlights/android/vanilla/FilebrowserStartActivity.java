/*
 * Copyright (C) 2013 Adrian Ulrich <adrian@blinkenlights.ch>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>. 
 */

package ch.blinkenlights.android.vanilla;

import java.util.ArrayList;
import java.util.Arrays;
import java.io.File;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.MenuItem;
import android.view.Menu;
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
	** Create a bare-bones actionbar
	*/
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		return true;
	}
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if (item.getItemId() == android.R.id.home) {
			finish();
			return true;
		} else {
			return super.onOptionsItemSelected(item);
		}
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
