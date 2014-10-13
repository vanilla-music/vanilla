/*
 * Copyright (C) 2014 Adrian Ulrich <adrian@blinkenlights.ch>
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

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.TextView;
import android.widget.Button;


public class AudioPickerActivity extends PlaybackActivity {

	private Uri mUri;

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
        
		Intent intent = getIntent();
		if (intent == null) {
			finish();
			return;
		}

		mUri = intent.getData();
		if (mUri == null || mUri.getScheme().equals("file") == false) { // we do not support streaming
			finish();
			return;
		}

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.audiopicker);

		TextView filePath = (TextView)findViewById(R.id.filepath);
		filePath.setText(mUri.getLastPathSegment());

		// Bind all 3 clickbuttons
		Button cancelButton = (Button)findViewById(R.id.cancel);
		cancelButton.setOnClickListener(this);
		Button enqueueButton = (Button)findViewById(R.id.enqueue);
		enqueueButton.setOnClickListener(this);
		enqueueButton.setEnabled( PlaybackService.hasInstance() ); // only active if vanilla is still running
		Button playButton = (Button)findViewById(R.id.play);
		playButton.setOnClickListener(this);

	}

	@Override
	public void onClick(View view)
	{
		int mode;

		switch(view.getId()) {
			case R.id.play:
				mode = SongTimeline.MODE_PLAY;
				break;
			case R.id.enqueue:
				mode = SongTimeline.MODE_ENQUEUE;
				break;
			default:
				finish();
				return;
		}

		String path = mUri.getPath();
		PlaybackService service = PlaybackService.get(this);

		QueryTask query = MediaUtils.buildFileQuery(path, Song.FILLED_PROJECTION);
		query.mode = mode;

		service.addSongs(query);

		finish();
	}

}
