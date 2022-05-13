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

import android.app.DialogFragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import ch.blinkenlights.android.medialibrary.MediaMetadataExtractor;

/**
 * A dialog to show details about a track/song
 */
public class TrackDetailsDialog extends DialogFragment {
	private static String SONG_ID = "song_id";

	private HandlerThread mHandlerThread;

	public TrackDetailsDialog() {
	}

	/**
	 * Creates a new dialog to show details about the given song
	 *
	 * @param songId ID of song this dialog is for
	 * @return a new dialog
	 */
	public static TrackDetailsDialog newInstance(long songId) {
		TrackDetailsDialog dialog = new TrackDetailsDialog();
		Bundle args = new Bundle();
		args.putLong(SONG_ID, songId);
		dialog.setArguments(args);
		return dialog;
	}

	/**
	 * Creates and shows a dialog containing details about the given song
	 *
	 * @param manager the FragmentManager to add the newly created dialog to
	 * @param songId ID of song to show dialog for
	 */
	public static void show(FragmentManager manager, long songId) {
		newInstance(songId).show(manager, "TrackDetailsDialog");
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mHandlerThread = new HandlerThread(getClass().getName());
		mHandlerThread.start();
		setStyle(DialogFragment.STYLE_NO_TITLE, 0);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_track_details, container);
		final TextView titleView = view.findViewById(R.id.title);
		final TextView artistView = view.findViewById(R.id.artist);
		final TextView albumView = view.findViewById(R.id.album);
		final TextView genreView = view.findViewById(R.id.genre);
		final TextView trackView = view.findViewById(R.id.track);
		final TextView yearView = view.findViewById(R.id.year);
		final TextView composerView = view.findViewById(R.id.composer);
		final TextView pathView = view.findViewById(R.id.path);
		final TextView formatView = view.findViewById(R.id.format);

		final long songId = getArguments().getLong(SONG_ID);
		Handler handler = new Handler(mHandlerThread.getLooper());
		handler.post(new Runnable() {
			@Override
			public void run() {
				final Song song = MediaUtils.getSongByTypeId(getActivity(), MediaUtils.TYPE_SONG, songId);
				if (song == null) {
					return;
				}
				final MediaMetadataExtractor metadata = new MediaMetadataExtractor(song.path);

				getActivity().runOnUiThread(new Runnable() {
					@Override
					public void run() {
						titleView.setText(song.title);
						artistView.setText(song.albumArtist);
						albumView.setText(song.album);
						genreView.setText(metadata.getFirst(MediaMetadataExtractor.GENRE));
						trackView.setText(song.getTrackAndDiscNumber());
						yearView.setText(metadata.getFirst(MediaMetadataExtractor.YEAR));
						composerView.setText(metadata.getFirst(MediaMetadataExtractor.COMPOSER));
						pathView.setText(song.path);
						formatView.setText(metadata.getFormat());
					}
				});
			}
		});

		return view;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mHandlerThread.quit();
	}
}
