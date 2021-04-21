/*
 * Copyright (C) 2015 Adrian Ulrich <adrian@blinkenlights.ch>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>. 
 */

package ch.blinkenlights.android.vanilla;

import android.annotation.TargetApi;
import android.content.Context;

@TargetApi(21)
public class RemoteControlImplLp implements RemoteControl.Client {
	/**
	 * This is just a placeholder implementation: On API 21, media buttons are handled in MediaSessionTracker.
	 */
	public RemoteControlImplLp(Context context) {
	}

	public void initializeRemote() {
	}

	public void unregisterRemote() {
	}

	public void reloadPreference() {
	}

	public void updateRemote(Song song, int state, boolean keepPaused) {
	}
}
