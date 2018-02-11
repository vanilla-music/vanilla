/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.support.v4.view;

import android.os.Build;
import android.view.View;


/**
 * Bare bones compat library to support ICS (API level 15)
 * for Vanilla Music
 */
public class ViewPagerIcsCompat {
	// From ViewPagerCompat
	private static final long FAKE_FRAME_TIME = 10;

	public static int getImportantForAccessibility(View view) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			return view.getImportantForAccessibility();
		} else {
			return -1; // never returned by real implementation
		}
	}

	public static void postInvalidateOnAnimation(View view) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			view.postInvalidateOnAnimation();
		} else {
			view.postInvalidateDelayed(FAKE_FRAME_TIME);
		}
	}

	public static void postOnAnimation(View view, Runnable action) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			view.postOnAnimation(action);
		} else {
			view.postDelayed(action, FAKE_FRAME_TIME);
		}
	}
}
