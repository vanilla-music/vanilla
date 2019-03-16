package ch.blinkenlights.android.vsa;

public class VsaInstance {

	public static Vsa fromPath(String path) {
		return new PosixVsaImpl(path);
	}

	public static Vsa fromPath(Vsa base, String item) {
		return new PosixVsaImpl(base.getAbsolutePath() + Vsa.separator + item);
	}
}
