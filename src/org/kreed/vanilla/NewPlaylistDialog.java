/*
 * Copyright (C) 2010 Christopher Eby <kreed@kreed.org>
 *
 * This file is part of Vanilla Music Player.
 *
 * Vanilla Music Player is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Library General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Vanilla Music Player is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.kreed.vanilla;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

/**
 * Simple dialog to prompt to user to enter a playlist name. Has an EditText to
 * enter the name and two buttons, create and cancel. Create changes to
 * overwrite if a name that already exists is selected.
 */
public class NewPlaylistDialog extends Dialog implements TextWatcher, View.OnClickListener {
	/**
	 * The create/overwrite button.
	 */
	private Button mPositiveButton;
	/**
	 * The text entry view.
	 */
	private EditText mText;
	/**
	 * Whether the dialog has been accepted. The dialog is accepted if create
	 * was clicked.
	 */
	private boolean mAccepted;

	public NewPlaylistDialog(Context context)
	{
		super(context);
	}

	@Override
	protected void onCreate(Bundle state)
	{
		super.onCreate(state);

		setContentView(R.layout.new_playlist_dialog);

		setTitle(R.string.choose_playlist_name);

		mText = (EditText)findViewById(R.id.playlist_name);
		mText.addTextChangedListener(this);

		mPositiveButton = (Button)findViewById(R.id.create);
		View negativeButton = findViewById(R.id.cancel);
		mPositiveButton.setOnClickListener(this);
		negativeButton.setOnClickListener(this);
	}

	/**
	 * Returns the playlist name currently entered in the dialog.
	 */
	public String getText()
	{
		return mText.getText().toString();
	}

	public void afterTextChanged(Editable s)
	{
		// do nothing
	}

	public void beforeTextChanged(CharSequence s, int start, int count, int after)
	{
		// do nothing
	}

	public void onTextChanged(CharSequence s, int start, int before, int count)
	{
		// Update the action button based on whether there is an
		// existing playlist with the given name.
		int res = Song.getPlaylist(s.toString()) == -1 ? R.string.create : R.string.overwrite;
		mPositiveButton.setText(res);
	}

	/**
	 * Returns whether the dialog has been accepted. The dialog is accepted
	 * when the create/overwrite button is clicked.
	 */
	public boolean isAccepted()
	{
		return mAccepted;
	}

	public void onClick(View view)
	{
		switch (view.getId()) {
		case R.id.create:
			mAccepted = true;
			// fall through
		case R.id.cancel:
			dismiss();
			break;
		}
	}
}