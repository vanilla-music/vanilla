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

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.widget.ImageButton;

public class VanillaImageButton extends ImageButton {

    public static boolean mLightTheme = false;

    public VanillaImageButton(Context context) {
        super(context);
        updateImageTint(-1);
    }

    public VanillaImageButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        updateImageTint(-1);
    }

    public VanillaImageButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        updateImageTint(-1);
    }

    @Override
    public void setImageResource(int resId) {
        super.setImageResource(resId);
        this.updateImageTint(resId);
    }


    private void updateImageTint(int resHint) {
        boolean setFilter = mLightTheme == true;

        // These drawables should never be filtered:
        switch (resHint) {
            case R.drawable.repeat_active:
            case R.drawable.repeat_current_active:
            case R.drawable.stop_current_active:
            case R.drawable.shuffle_active:
            case R.drawable.shuffle_album_active:
            case R.drawable.random_active:
                setFilter = false;
        }

        if (setFilter == true) {
            this.setColorFilter(Color.argb(255, 130, 130, 130));
        } else {
            this.setColorFilter(null);
        }
    }

}
