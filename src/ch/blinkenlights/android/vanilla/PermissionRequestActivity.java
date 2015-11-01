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


import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;


public class PermissionRequestActivity extends Activity {

	// 'dangerous' permissions not granted by the manifest on versions >= M
	private static final String[] NEEDED_PERMISSIONS = { Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE };

	@TargetApi(Build.VERSION_CODES.M)
	@Override
	public void onCreate(Bundle savedInstanceState) {
		ThemeHelper.setTheme(this, R.style.VanillaBase);
		super.onCreate(savedInstanceState);

		setTitle(R.string.app_name);
		//	Fixme: This should probably be some welcome dialog with a button to launch askForPermissions
		//	setContentView(R.layout.showqueue_listview);

		requestPermissions(NEEDED_PERMISSIONS, 0);
	}


	/**
	 * Called by Activity after the user interacted with the permission request
	 * Will launch the main activity if all permissions were granted, exits otherwise
	 *
	 * @param requestCode The code set by requestPermissions
	 * @param permissions Names of the permissions we got granted or denied
	 * @param grantResults Results of the permission requests
	 */
	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		int grantedPermissions = 0;
		for (int result : grantResults) {
			if (result == PackageManager.PERMISSION_GRANTED)
				grantedPermissions++;
		}

		if (grantedPermissions == grantResults.length) {
			Intent intent = getPackageManager().getLaunchIntentForPackage(getPackageName());
			intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
			// Hack: We *kill* ourselfs (while launching the main activity) to get startet
			// in a new process: This works around a bug/feature in 6.0 that would cause us
			// to get 'partial read' permissions (eg: reading from the content provider works
			// but reading from /sdcard doesn't)
			android.os.Process.killProcess(android.os.Process.myPid());
		}
		finish();
	}


	/**
	 * Launches a permission request dialog if needed
	 *
	 * @param activity The activitys context to use for the permission check
	 * @return boolean true if we showed a permission request dialog
	 */ 
	public static boolean requestPermissions(Activity activity) {
		boolean havePermissions = havePermissions(activity);

		if (havePermissions == false)
			activity.startActivity(new Intent(activity, PermissionRequestActivity.class));

		return !havePermissions;
	}

	/**
	 * Checks if all required permissions have been granted
	 *
	 * @param context The context to use
	 * @return boolean true if all permissions have been granded
	 */
	private static boolean havePermissions(Context context) {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			for (String permission : NEEDED_PERMISSIONS) {
				if (context.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
					return false;
				}
			}
		} // else: granted during installation
		return true;
	}

}
