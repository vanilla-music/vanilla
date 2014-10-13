/*
* Copyright (C) 2013 Adrian Ulrich
*
* This file is part of VanillaPlug.
*
* VanillaPlug is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* VanillaPlug is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU General Public License for more details.
*
*/

package ch.blinkenlights.android.vanilla;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ComponentName;
import android.util.Log;
import android.media.AudioManager;

public class HeadsetPluggedService extends Service {
    private final IBinder HeadsetPluggedBinder = new LocalBinder();
    @Override
    public IBinder onBind(Intent i) {
        return HeadsetPluggedBinder;
    }
    public class LocalBinder extends Binder {
        public HeadsetPluggedService getService() {
            return HeadsetPluggedService.this;
        }
    }
    @Override
    public void onCreate() {
        IntentFilter inf = new IntentFilter();
        inf.addAction("android.intent.action.HEADSET_PLUG");
        registerReceiver(HeadsetPluggedReceiver, inf);
    }
    public void onDestroy() {
        unregisterReceiver(HeadsetPluggedReceiver);
    }
    private final BroadcastReceiver HeadsetPluggedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String ia = intent.getAction();
            int state = intent.getIntExtra("state", -1);
            Log.v("Vanilla", " HeadsetPlugged Intent "+ia+" received with state "+state);
            if(state == 1) { /* Headset was plugged in */
                AudioManager aM = (AudioManager)ctx.getSystemService(Context.AUDIO_SERVICE);
                if(aM != null && !aM.isMusicActive()) {
                    ComponentName service = new ComponentName(getPackageName(),"ch.blinkenlights.android.vanilla.PlaybackService");
                    Intent x = new Intent(PlaybackService.ACTION_PLAY).setComponent(service);
                    x.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startService(x);
                }
            }
        }
    };
}
