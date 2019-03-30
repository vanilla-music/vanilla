package ch.blinkenlights.android.vsa;

import android.content.Context;

public class VsaInstance {

	public static Vsa fromPath(Context context, String path) {
		return new SafVsaImpl(context, path);
		//		return new PosixVsaImpl(path);
	}

	public static Vsa fromPath(Context context, Vsa base, String item) {
		return new SafVsaImpl(context, base.getAbsolutePath() + Vsa.separator + item);
		//return new PosixVsaImpl(base.getAbsolutePath() + Vsa.separator + item);
	}
}
