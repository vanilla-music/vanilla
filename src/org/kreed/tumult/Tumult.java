package org.kreed.tumult;

import android.app.Application;
import android.content.Context;

public class Tumult extends Application {
	private static Tumult instance;

	public Tumult()
	{
		instance = this;
	}
	
	public static Context getContext()
	{
		return instance;
	}
}
