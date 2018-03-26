package ch.blinkenlights.android.vanilla;

import android.graphics.Paint;
import android.text.style.LineHeightSpan;

/**
 * A Span to make two lines overlap. See https://stackoverflow.com/a/39432495.
 */
public class LineOverlapSpan implements LineHeightSpan {
	@Override
	public void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight, Paint.FontMetricsInt fm) {
		fm.bottom += fm.top;
		fm.descent += fm.top;
	}
}
