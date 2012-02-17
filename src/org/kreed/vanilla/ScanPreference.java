/*
 * Copyright (C) 2012 Christopher Eby <kreed@kreed.org>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.kreed.vanilla;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Environment;
import android.preference.Preference;
import android.util.AttributeSet;

/**
 * A preference that allows the MediaScanner to be triggered.
 */
public class ScanPreference extends Preference {
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(Intent.ACTION_MEDIA_SCANNER_STARTED)) {
				setSummary(R.string.scan_in_progress);
				setEnabled(false);
			} else if (intent.getAction().equals(Intent.ACTION_MEDIA_SCANNER_FINISHED)) {
				setSummary(R.string.finished_scanning);
				setEnabled(true);
				context.unregisterReceiver(this);
			}
		}
	};

	public ScanPreference(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		setTitle(R.string.media_scan);
		setSummary(R.string.tap_to_scan);
	}

	@Override
	public void onClick()
	{
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_STARTED);
		intentFilter.addAction(Intent.ACTION_MEDIA_SCANNER_FINISHED);
		intentFilter.addDataScheme("file");
		getContext().registerReceiver(mReceiver, intentFilter);

		Uri storage = Uri.parse("file://" + Environment.getExternalStorageDirectory());
		getContext().sendBroadcast(new Intent(Intent.ACTION_MEDIA_MOUNTED, storage));
	}
}
