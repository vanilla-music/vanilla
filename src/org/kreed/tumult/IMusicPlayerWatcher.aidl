package org.kreed.tumult;

import org.kreed.tumult.Song;

oneway interface IMusicPlayerWatcher {
	void songChanged(in Song playingSong);
	void stateChanged(in int oldState, in int newState);
}