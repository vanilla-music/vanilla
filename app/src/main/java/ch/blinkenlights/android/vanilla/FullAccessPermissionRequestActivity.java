/*
 * Copyright (C) 2015-2023 Adrian Ulrich <adrian@blinkenlights.ch>
 * Copyright (C) 2023 Azer Abdullaev <azer.abdullaev.berlin+git@gmail.com>
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

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.content.pm.PackageManager;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.view.LayoutInflater;


public class FullAccessPermissionRequestActivity extends Activity {

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestPermissions(getNeededPermissions(), 1);
	}

	private static String[] getNeededPermissions() {
		return new String[] { Manifest.permission.MANAGE_EXTERNAL_STORAGE };
	}
}
