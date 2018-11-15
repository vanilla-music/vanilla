/*
 * Copyright (C) 2018 Toby Hsieh
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.blinkenlights.android.vanilla;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Toast;

/**
 * A dialog for the user to input a specific time to jump to for the current song
 */
public class JumpToTimeDialog extends DialogFragment implements DialogInterface.OnClickListener {
	private EditText hoursView;
	private EditText minutesView;
	private EditText secondsView;

	/**
	 * Callback interface for an activity that shows JumpToTimeDialog
	 */
	public interface OnPositionSubmitListener {
		/**
		 * Called when the user submits a position to jump/seek to for the current song.
		 *
		 * @param position position to seek/jump to in milliseconds
		 */
		void onPositionSubmit(int position);
	}

	/**
	 * Creates and shows the dialog
	 *
	 * @param manager the FragmentManager to add the newly created dialog to
	 */
	public static void show(FragmentManager manager) {
		new JumpToTimeDialog().show(manager, "JumpToTimeDialog");
	}

	@Override
	public Dialog onCreateDialog(Bundle savedInstanceState) {
		// Watcher that moves to the next EditText when 2 digits are inserted
		TextWatcher textWatcher = new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				EditText editText = (EditText) getDialog().getCurrentFocus();
				if (editText.length() == 2) {
					View view = editText.focusSearch(View.FOCUS_RIGHT);
					if (view != null) {
						view.requestFocus();
					}
				}
			}

			@Override
			public void afterTextChanged(Editable s) {
			}
		};

		View view = LayoutInflater.from(getActivity()).inflate(R.layout.duration_input, null);
		hoursView = view.findViewById(R.id.hours);
		hoursView.addTextChangedListener(textWatcher);
		minutesView = view.findViewById(R.id.minutes);
		minutesView.addTextChangedListener(textWatcher);
		secondsView = view.findViewById(R.id.seconds);
		secondsView.addTextChangedListener(textWatcher);

		Dialog dialog = new AlertDialog.Builder(getActivity())
			.setTitle(R.string.jump_to_time)
			.setView(view)
			.setPositiveButton(android.R.string.ok, this)
			.setNegativeButton(android.R.string.cancel, null)
			.create();
		hoursView.requestFocus();
		dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
		return dialog;
	}

	@Override
	public void onClick(DialogInterface dialog, int which) {
		if (which == DialogInterface.BUTTON_POSITIVE) {
			Activity activity = getActivity();
			try {
				int hours = parseInteger(hoursView.getText().toString());
				int minutes = parseInteger(minutesView.getText().toString());
				int seconds = parseInteger(secondsView.getText().toString());
				int position = (hours * 3600 + minutes * 60 + seconds) * 1000;
				((OnPositionSubmitListener) activity).onPositionSubmit(position);
			} catch (NumberFormatException e) {
				Toast.makeText(activity, R.string.error_invalid_position, Toast.LENGTH_SHORT).show();
			}
		}
	}

	/**
	 * Parses the given string as an integer. This returns 0 if the given string is empty.
	 *
	 * @param s the string to parse
	 * @return the integer result
	 */
	static int parseInteger(String s) {
		if (s.length() == 0) {
			return 0;
		}
		return Integer.parseInt(s);
	}
}
