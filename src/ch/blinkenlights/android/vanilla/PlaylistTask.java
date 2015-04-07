/*
 * Copyright (C) 2013 Adrian Ulrich <adrian@blinkenlights.ch>
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

import java.util.ArrayList;

/**
 * Represents a pending playlist manipulation
 */
public class PlaylistTask {

    /**
     * ID of this playlist to manipulate
     */
    public long playlistId;
    /**
     * Name of this playlist (used for the toast message)
     */
    public String name;
    /**
     * Populate playlist using this QueryTask
     */
    public QueryTask query;
    /**
     * Populate playlist using this audioIds
     */
    public ArrayList<Long> audioIds;


    public PlaylistTask(long playlistId, String name) {
        this.playlistId = playlistId;
        this.name = name;
    }

}
