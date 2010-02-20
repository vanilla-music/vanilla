package org.kreed.tumult;

import org.kreed.tumult.Song;
import org.kreed.tumult.IMusicPlayerWatcher;

interface IPlaybackService {
	void registerWatcher(IMusicPlayerWatcher watcher);

	Song[] getCurrentSongs();
	Song getSong(int delta);
	int getState();
	int getPosition();
	int getDuration();

	void previousSong();
	void togglePlayback();
	void nextSong();
	void seekToProgress(int progress);
}