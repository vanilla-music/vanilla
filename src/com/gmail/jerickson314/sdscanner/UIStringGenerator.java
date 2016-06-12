/* SD Scanner - A manual implementation of the SD rescan process, compatible
 * with Android 4.4
 *
 * Copyright (C) 2013-2014 Jeremy Erickson
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

package com.gmail.jerickson314.sdscanner;

import android.app.Activity;
import java.util.ArrayList;
import java.util.Iterator;

public class UIStringGenerator {
    private static interface SubGenerator {
        public String toString(Activity activity);
    }

    private static class ResourceSubGenerator implements SubGenerator {
        int mResId;

        public ResourceSubGenerator(int resId) {
            mResId = resId;
        }

        public String toString(Activity activity) {
            return activity.getString(mResId);
        }
    }

    private static class StringSubGenerator implements SubGenerator {
        String mString;

        public StringSubGenerator(String string) {
            mString = string;
        }

        public String toString(Activity activity) {
            return mString;
        }
    }

    ArrayList<SubGenerator> mSubGenerators = new ArrayList<SubGenerator>();

    public void addSubGenerator(int resId) {
        mSubGenerators.add(new ResourceSubGenerator(resId));
    }

    public void addSubGenerator(String string) {
        mSubGenerators.add(new StringSubGenerator(string));
    }

    public UIStringGenerator(int resId) {
        addSubGenerator(resId);
    }

    public UIStringGenerator(int resId, String string) {
        addSubGenerator(resId);
        addSubGenerator(string);
    }

    public UIStringGenerator(String string) {
        addSubGenerator(string);
    }

    public UIStringGenerator() {
        // Doing nothing results in empty string.
    }

    public String toString(Activity activity) {
        StringBuilder toReturn = new StringBuilder();
        Iterator<SubGenerator> iterator = mSubGenerators.iterator();
        while (iterator.hasNext()) {
            toReturn.append(iterator.next().toString(activity));
        }
        return toReturn.toString();
    }
}
