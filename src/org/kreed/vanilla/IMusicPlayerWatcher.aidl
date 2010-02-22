package org.kreed.vanilla;

import org.kreed.vanilla.Song;

oneway interface IMusicPlayerWatcher {
	void songChanged(in Song playingSong);
	void stateChanged(in int oldState, in int newState);
}