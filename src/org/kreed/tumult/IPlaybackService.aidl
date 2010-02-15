package org.kreed.tumult;

import org.kreed.tumult.Song;
import org.kreed.tumult.IMusicPlayerWatcher;

interface IPlaybackService {
	void registerWatcher(IMusicPlayerWatcher watcher);

	Song[] getCurrentSongs();

	void previousSong();
	void togglePlayback();
	void nextSong();
}