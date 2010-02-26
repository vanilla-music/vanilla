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