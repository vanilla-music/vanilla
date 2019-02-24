package ch.blinkenlights.android.vanilla.ui.customelements;

import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;

import android.widget.TextView ;


/**
 * The Marquee effect needs an active focus. This custom Textview is always focussed, or at least it says so. This allows the marquee to permanently play.
 */
public class CustomTextView extends TextView {

	public CustomTextView(Context context) {
		super(context);
	}

	public CustomTextView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public CustomTextView(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@Override
	protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
		if(focused)
			super.onFocusChanged(focused, direction, previouslyFocusedRect);
	}

	@Override
	public void onWindowFocusChanged(boolean focused) {
		if(focused)
			super.onWindowFocusChanged(focused);
	}


	@Override
	public boolean isFocused() {
		return true;
	}
}
