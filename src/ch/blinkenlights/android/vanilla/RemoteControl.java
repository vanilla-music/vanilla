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

import android.content.Context;
import android.os.Build;


public class RemoteControl {

	/**
	 * Returns a RemoteControl.Client implementation
	 */
	public RemoteControl.Client getClient(Context context) {
		return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP ?
			new RemoteControlImplLp(context) :
			new RemoteControlImplICS(context)  // legacy implementation, kept until we drop 4.x support
		);
	}

	/**
	 * Interface definition of our RemoteControl API
	 */
	public interface Client {
		public void initializeRemote();
		public void unregisterRemote();
		public void reloadPreference();
		public void updateRemote(Song song, int state, boolean keepPaused);
	}
}
