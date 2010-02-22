package org.kreed.vanilla;

import android.app.Application;
import android.content.Context;

public class ContextApplication extends Application {
	private static ContextApplication instance;

	public ContextApplication()
	{
		instance = this;
	}
	
	public static Context getContext()
	{
		return instance;
	}
}