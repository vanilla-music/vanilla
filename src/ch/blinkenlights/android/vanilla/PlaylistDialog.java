/*
 * Copyright (C) 2016 Adrian Ulrich <adrian@blinkenlights.ch>
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

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;

public class PlaylistDialog extends DialogFragment
	implements DialogInterface.OnClickListener
{

	/**
	 * A class implementing our callback interface
	 */
	private Callback mCallback;
	/**
	 * The data passed to the callback
	 */
	private PlaylistDialog.Data mData;
	/**
	 * Array of all found playlist names
	 */
	private String[] mItemName;
	/**
	 * Array of all found playlist values
	 */
	private long[] mItemValue;
	/**
	 * Index of 'create playlist' button
	 */
	private final int BUTTON_CREATE_PLAYLIST = 0;
	/**
	 * Our callback interface
	 */
	public interface Callback {
		void updatePlaylistFromPlaylistDialog(PlaylistDialog.Data data);
	}
	/**
	 * Our data structure
	 */
	public class Data {
		public String name;
		public long id;
		public Intent sourceIntent;
		public MediaAdapter allSource;
	}

	/**
	 * Creates a new playlist dialog to assemble a playlist using an intent
	 */
	PlaylistDialog(Callback callback, Intent intent, MediaAdapter allSource) {
		mCallback = callback;
		mData = new PlaylistDialog.Data();
		mData.sourceIntent = intent;
		mData.allSource = allSource;
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		Cursor cursor = Playlist.queryPlaylists(getActivity().getContentResolver());
		if (cursor == null)
			return null;

		int count = cursor.getCount();
		mItemName = new String[1+count];
		mItemValue = new long[1+count];

		// Index 0 is always 'New Playlist...'
		mItemName[0] = getResources().getString(R.string.new_playlist);
		mItemValue[0] = -1;

		for (int i = 0 ; i < count; i++) {
			cursor.moveToPosition(i);
			mItemValue[1+i] = cursor.getLong(0);
			mItemName[1+i] = cursor.getString(1);
		}

		// All names are now known: we can show the dialog
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(R.string.add_to_playlist)
			.setItems(mItemName, this);
		return builder.create();
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		switch (which) {
			case BUTTON_CREATE_PLAYLIST:
				PlaylistInputDialog newDialog = new PlaylistInputDialog(new PlaylistInputDialog.Callback() {
					@Override
					public void onSuccess(String input) {
						mData.id = -1;
						mData.name = input;
						mCallback.updatePlaylistFromPlaylistDialog(mData);
					}
				}, "", R.string.create);
				newDialog.show(getFragmentManager(), "PlaylistInputDialog");
				break;
			default:
				mData.id = mItemValue[which];
				mData.name = mItemName[which];
				mCallback.updatePlaylistFromPlaylistDialog(mData);
		}
		dialog.dismiss();
	}
}
