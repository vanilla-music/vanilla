package ch.blinkenlights.android.vanilla;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.net.Uri;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import android.util.Log;

public class SafRequestActivity extends AppCompatActivity {

	/**
	 * Launches a new SAFRequestactivity.
	 */
	public static void launch(Activity activity) {
		Intent intent = new Intent(activity, SafRequestActivity.class);
		activity.startActivity(intent);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		launchSafRequest();
	}

	private void launchSafRequest() {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
		//intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		startActivityForResult(intent, 0);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.v("VanillaMusic", "Got result: "+ data.getData());

		Uri grantUri = data.getData();
		getContentResolver().takePersistableUriPermission(grantUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

		for(Object x : getContentResolver().getPersistedUriPermissions()) { Log.v("VanillaMusic", "GRANT: "+x); }

		DocumentFile root = DocumentFile.fromTreeUri(this, grantUri);
		Log.v("VanillaMusic", "Root is: "+root);
		for(DocumentFile child : root.listFiles()) {
			Log.v("VanillaMusic", " -> "+ child.isDirectory() + ", -> "+child.getName());
		}
		finish();
	}
}
