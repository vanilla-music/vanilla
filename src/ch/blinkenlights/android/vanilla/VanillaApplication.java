package ch.blinkenlights.android.vanilla;

import android.app.Application;

import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;

public class VanillaApplication extends Application {

	@Override
	public void onCreate() {
		super.onCreate();
		
		// Create global configuration and initialize ImageLoader with this configuration
		// Refer to https://github.com/nostra13/Android-Universal-Image-Loader for usage details
        DisplayImageOptions defaultOptions = new DisplayImageOptions.Builder()
    		.showStubImage(R.drawable.musicnotes)
    		.showImageForEmptyUri(R.drawable.musicnotes)
    		.showImageOnFail(R.drawable.musicnotes)
    		.cacheInMemory()
    		.cacheOnDisc()
    		.build();
		ImageLoaderConfiguration config = new ImageLoaderConfiguration.Builder(getApplicationContext())
			.memoryCacheSize(1024 * 1024)
			.defaultDisplayImageOptions(defaultOptions)
			//.enableLogging()
			.build();
		ImageLoader.getInstance().init(config);
	}
}
