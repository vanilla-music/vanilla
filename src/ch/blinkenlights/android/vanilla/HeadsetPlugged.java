/*
* Copyright (C) 2013 Adrian Ulrich
*
* This file is part of Vanilla.
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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.content.ComponentName;

public class HeadsetPlugged extends Activity {
    private Intent bb_service_intent;
    @Override
    public void onCreate(Bundle savedInstanceState) {
        ComponentName ss_ok;
        super.onCreate(savedInstanceState);
        bb_service_intent = new Intent(getApplicationContext(), HeadsetPluggedService.class);
        ss_ok = startService(bb_service_intent);
        Log.v("Vanilla", "HeadsetPlugged ss_ok = "+ss_ok);
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
