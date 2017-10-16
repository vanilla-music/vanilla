/*
 * Copyright (C) 2017 Adrian Ulrich <adrian@blinkenlights.ch>
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

import ch.blinkenlights.android.medialibrary.MediaLibrary;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.Context;
import android.content.ComponentName;
import android.database.ContentObserver;
import android.os.Build;

import android.util.Log;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;


@TargetApi(21)
public class ScheduledLibraryUpdate extends JobService {
	/**
	 * The unique job id of this package - do not change
	 * as we would otherwise end up with multiple scheduled
	 * jobs (until the next reboot happens)
	 */
	private final static int JOB_ID_UPDATE = 9;
	/**
	 * The job parameters of the currently running job
	 */
	private JobParameters mJobParams;


	/**
	 * Schedules a new media library scan job.
	 *
	 * @return true if job was scheduled, false otherwise
	 */
	public static boolean scheduleUpdate(Context context) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
			return false; // JobScheduler requires API 21

		JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

		if (scheduler.getPendingJob(JOB_ID_UPDATE) != null)
			return false; // no need to re-schedule the job

		ComponentName componentName = new ComponentName(context, ScheduledLibraryUpdate.class);
		JobInfo job = new JobInfo.Builder(JOB_ID_UPDATE, componentName)
			.setRequiresCharging(true)
			.setRequiresDeviceIdle(true)
			.setPeriodic(3600000 * 32) // run at most every ~32 hours
			.build();

		scheduler.schedule(job);

		xlog("Job with id "+JOB_ID_UPDATE+" scheduled for execution");
		return true;
	}


	/**
	 * Called by the scheduler to launch the job
	 *
	 * @param params the parameters of this job
	 * @return true if the job has been launched
	 */
	@Override
	public boolean onStartJob(JobParameters params) {
		xlog("++ onStartJob called on "+params.getJobId());

		if (params.getJobId() != JOB_ID_UPDATE)
			return false; // orphaned job, do not start

		final boolean fullScan = (Math.random() > 0.7);

		mJobParams = params;
		MediaLibrary.registerContentObserver(mObserver);
		MediaLibrary.startLibraryScan(this, fullScan, false);

		xlog("++ onStartJob called on "+params.getJobId()+", running full scan? -> "+fullScan);
		return true;
	}

	/**
	 * Called by the scheduler to abort the job
	 *
	 * @param params the parameters of the job to abort
	 * @return false as we do not want to get backed-off
	 */
	@Override
	public boolean onStopJob(JobParameters params) {
		xlog("++ onStopJob called on "+params.getJobId());
		finalizeScan();
		return false;
	}

	/**
	 * Aborts a running scan job
	 */
	private void finalizeScan() {
		xlog("++ finalize called");
		MediaLibrary.unregisterContentObserver(mObserver);
		MediaLibrary.abortLibraryScan(this);
		mJobParams = null;
	}

	/**
	 * The content observer registered to the media library.
	 * The observer will receive callbacks for changed songs,
	 * the last callback will have `ongoing` set to `false`,
	 * which indicates that our job completed.
	 */
	private final ContentObserver mObserver = new ContentObserver(null) {
		@Override
		public void onChange(boolean ongoing) {
			xlog("CHANGE: "+ongoing);
			if (!ongoing) {
				jobFinished(mJobParams, false);
				finalizeScan();
			}
		}
	};

	private static void xlog(String str) {
		try {
		String sdf = (new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")).format(new Date());
		FileWriter fw = new FileWriter("/sdcard/vanilla-log.txt", true);
		Log.v("VanillaMusic", str);
		fw.write(String.format("%s: %s\n", sdf, str));
		fw.close();
		} catch(Exception e) {
			Log.v("VanillaMusic", "LOGFAIL: "+e);
		}
	}
}
