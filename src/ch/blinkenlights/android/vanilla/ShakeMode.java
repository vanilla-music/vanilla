package ch.blinkenlights.android.vanilla;

import java.util.HashMap;
import java.util.Map;

enum ShakeMode {
	ALWAYS("Always"),
    WHEN_SCREEN_ON("WhenScreenOn");
	
	private static final Map<String, ShakeMode> REVERSE_LOOKUP =
			new HashMap<String, ShakeMode>(ShakeMode.values().length);
	
	static {
		for (ShakeMode mode : ShakeMode.values()) {
			REVERSE_LOOKUP.put(mode.asString(), mode);
		}
	}
	
	private final String shakeModeEntry;
    
    ShakeMode(String shakeModeEntry) {
		this.shakeModeEntry = shakeModeEntry;
	}
    
    String asString() {
    	return shakeModeEntry;
    }
    
    public static ShakeMode fromString(final String shakeModeString) {
    	ShakeMode mode = REVERSE_LOOKUP.get(shakeModeString);
    	if (mode == null) {
    		mode = ALWAYS;
    	}
    	return mode;
    }
}
