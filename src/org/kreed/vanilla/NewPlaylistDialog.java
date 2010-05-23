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
	/**
	 * The text to display initially. When the EditText contains this text, the
	 * positive button will be disabled.
	 */
	private String mInitialText;
	/**
	 * The resource containing the string describing the default positive
	 * action (e.g. "Create" or "Rename").
	 */
	private int mActionRes;

	/**
	 * Create a NewPlaylistDialog.
	 *
	 * @param context A Context to use.
	 * @param initialText The text to show initially. The positive button is
	 * disabled when the EditText contains this text.
	 * @param actionText A string resource describing the default positive
	 * action (e.g. "Create").
	 */
	public NewPlaylistDialog(Context context, String initialText, int actionText)
	{
		super(context);
		mInitialText = initialText;
		mActionRes = actionText;
	}

	@Override
	protected void onCreate(Bundle state)
	{
		super.onCreate(state);

		setContentView(R.layout.new_playlist_dialog);

		setTitle(R.string.choose_playlist_name);

		mPositiveButton = (Button)findViewById(R.id.create);
		mPositiveButton.setOnClickListener(this);
		mPositiveButton.setText(mActionRes);
		View negativeButton = findViewById(R.id.cancel);
		negativeButton.setOnClickListener(this);

		mText = (EditText)findViewById(R.id.playlist_name);
		mText.addTextChangedListener(this);
		mText.setText(mInitialText);
		mText.requestFocus();
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

	public void onTextChanged(CharSequence text, int start, int before, int count)
	{
		String string = text.toString();
		if (string.equals(mInitialText)) {
			mPositiveButton.setEnabled(false);
		} else {
			mPositiveButton.setEnabled(true);
			// Update the action button based on whether there is an
			// existing playlist with the given name.
			int res = Playlist.getPlaylist(string) == -1 ? mActionRes : R.string.overwrite;
			mPositiveButton.setText(res);
		}
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