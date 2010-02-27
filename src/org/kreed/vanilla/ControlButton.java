package org.kreed.vanilla;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.util.AttributeSet;
import android.widget.ImageView;

public class ControlButton extends ImageView {
	private static final int ACTIVE_TINT = Color.argb(100, 0, 255, 0);
	private static final int INACTIVE_TINT = Color.TRANSPARENT;
	private int mTint = Color.TRANSPARENT;

	public ControlButton(Context context, AttributeSet attrs)
	{
		super(context, attrs);

		setFocusable(true);
	}

	@Override
	protected void drawableStateChanged()
	{
		super.drawableStateChanged();

		int tint = isPressed() || isFocused() ? ACTIVE_TINT : INACTIVE_TINT;  
		if (tint != mTint) {
			setColorFilter(tint, PorterDuff.Mode.SRC_ATOP);
			mTint = tint;
		}
	}
}