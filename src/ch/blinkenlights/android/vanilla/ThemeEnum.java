package ch.blinkenlights.android.vanilla;

import android.util.SparseArray;

public enum ThemeEnum {
    LIGHT_STANDARD(0),DARK_STANDARD(1),LIGHT_GREYISH(2),DARK_GREYISH(3),DARK_BLACK(4),LIGHT_ORANGE(5),DARK_ORANGE(6),LIGHT_BLUE(7),DARK_BLUE(8),LIGHT_RED(9),DARK_RED(10);

    private static SparseArray<ThemeEnum> map = new SparseArray<>();

    static {
        for (ThemeEnum themeEnum : ThemeEnum.values()) {
            map.put(themeEnum._index, themeEnum);
        }
    }

    private int _index;

    ThemeEnum(final int index) {
        _index = index;
    }

    public static ThemeEnum valueOf(int index) {
        return map.get(index);
    }
}

