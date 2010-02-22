package org.kreed.vanilla;

import org.kreed.vanilla.Song;
import org.kreed.vanilla.IMusicPlayerWatcher;

interface IPlaybackService {
	void registerWatcher(IMusicPlayerWatcher watcher);
	void unregisterWatcher(IMusicPlayerWatcher watcher);

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