/*
 * Copyright (C) 2018 Adrian Ulrich <adrian@blinkenlights.ch>
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
import android.content.Intent;
import android.content.pm.ShortcutManager;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Icon;
import android.os.Build;

/**
 * Provides some static System-related utility functions.
 */
public class SystemUtils {

	/**
	 * Installs (or prompts user to install) a shortcut launcher pointing to the given type and id
	 * combination. Launching the shortcut will cause vanilla to play the type / id combination.
	 *
	 * @param context the context to use.
	 * @param label the label of the shortcut.
	 * @param type the media type of the passed in id.
	 * @param id an id of a media type
	 */
	public static void installLauncherShortcut(Context context, String label, int type, long id) {
		Intent shortcut = new Intent(context, ShortcutPseudoActivity.class);
		shortcut.setAction(PlaybackService.ACTION_FROM_TYPE_ID_AUTOPLAY);
		shortcut.putExtra(LibraryAdapter.DATA_TYPE, type);
		shortcut.putExtra(LibraryAdapter.DATA_ID, id);

		if (label.length() == 0) {
			// Android does not allow empty labels.
			label = "?";
		}

		Bitmap cover = null;
		Song song = MediaUtils.getSongByTypeId(context, type, id);
		if (song != null) {
			cover = song.getSmallCover(context);
		}
		if (cover == null) {
			cover = BitmapFactory.decodeResource(context.getResources(), R.drawable.fallback_cover);
		}

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
			installShortcut(context, label, cover, shortcut);
		} else {
			installShortcutLegacy(context, label, cover, shortcut);
		}
	}

	/**
	 * Prompts the user to install a shortcut. This is only available on API >= 25.
	 *
	 * @param context the context to use.
	 * @param label the label to use for this shortcut.
	 * @param cover the icon to use for this shortcut.
	 * @param intent intent launched by the shortcut.
	 *
	 * @return true if the shortcut MAY have been created.
	 */
	private static boolean installShortcut(Context context, String label, Bitmap cover, Intent intent) {
		ShortcutManager manager = context.getSystemService(ShortcutManager.class);
		if (manager == null || !manager.isRequestPinShortcutSupported())
			return false;

		String uniqueId = "vanilla:shortcut:" + System.currentTimeMillis();
		ShortcutInfo pin = new ShortcutInfo.Builder(context, uniqueId)
			.setIntent(intent)
			.setShortLabel(label)
			.setIcon(Icon.createWithBitmap(cover))
			.build();

		manager.requestPinShortcut(pin, null);
		return true;
	}

	/**
	 * Adds a new icon to the launcher for legacy devices.
	 *
	 * @param context the context to use.
	 * @param label the label to use for this shortcut.
	 * @param cover the cover to use for the shortcut.
	 * @param intent the launcher intent.
	 *
	 * @return true if the shortcut request was dispatched
	 */
	private static boolean installShortcutLegacy(Context context, String label, Bitmap cover, Intent intent) {
		Intent add = new Intent();
		add.putExtra(Intent.EXTRA_SHORTCUT_INTENT, intent);
		add.putExtra(Intent.EXTRA_SHORTCUT_NAME, label);
		add.putExtra(Intent.EXTRA_SHORTCUT_ICON, cover);

		add.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
		context.sendBroadcast(add);
		return true;
	}
}
