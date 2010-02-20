package org.kreed.tumult;

import org.kreed.tumult.Song;

oneway interface IMusicPlayerWatcher {
	void previousSong(in Song playingSong, in Song nextForwardSong);
	void nextSong(in Song playingSong, in Song nextBackwardSong);
	void stateChanged(in int oldState, in int newState);
}