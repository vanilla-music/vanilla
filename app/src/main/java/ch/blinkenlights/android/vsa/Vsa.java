/*
 * Copyright (C) 2019 Adrian Ulrich <adrian@blinkenlights.ch>
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

package ch.blinkenlights.android.vsa;

public interface Vsa {
	final static String separator = "/";

	/**
	 * Returns an array of strings naming directories and files contained
	 * in this directory.
	 */
	String[] list();

	/**
	 * Similar to {@code list}, but returns Vsa objects instead of dirent names.
	 */
	Vsa[] listFiles();

	/**
	 * Returns the absolute pathname of this object.
	 */
	String getAbsolutePath();

	/**
	 * Returns the filename of this object.
	 */
	String getName();

	/**
	 * Returns whether or not this object is a directory.
	 */
	boolean isDirectory();

	/**
	 * Returns the absolute path of this objects parent or {@code null}
	 * if there is no parent.
	 */
	String getParent();

	/**
	 * Returns the parent of this object, based on its path.
	 * Returns {@code null} if there is no parent.
	 */
	Vsa getParentFile();

	long lastModified();

	long length();

	boolean equals(Object file);
}
