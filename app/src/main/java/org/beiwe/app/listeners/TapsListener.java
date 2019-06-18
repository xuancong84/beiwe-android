package org.beiwe.app.listeners;

import android.annotation.SuppressLint;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import org.beiwe.app.*;
import org.beiwe.app.storage.TextFileManager;

public class TapsListener
{
	public static String header = "timestamp,in_app_name,orientation";
	private final Context context;
	private BackgroundService service;
	private InvisibleTouchView layerView;
	private DisplayMetrics displayMetrics;
	private UsageStatsManager usageStatsManager;

	public TapsListener(BackgroundService paramBackgroundService)
	{
		context = paramBackgroundService.getApplicationContext();
		service = paramBackgroundService;
		displayMetrics = service.getResources().getDisplayMetrics();
		usageStatsManager = (UsageStatsManager)this.context.getSystemService("usagestats");
		new Intent("android.intent.action.MAIN").addCategory("android.intent.category.HOME");
		addView();
	}

	void addView()
	{
		WindowManager.LayoutParams localLayoutParams;
//		int flags = FLAG_WATCH_OUTSIDE_TOUCH|SOFT_INPUT_ADJUST_PAN|FLAG_NOT_FOCUSABLE|FLAG_NOT_TOUCHABLE;
		if (Build.VERSION.SDK_INT >= 26)
			localLayoutParams = new WindowManager.LayoutParams(1, 1, 2038, 262184, -3);
		else
			localLayoutParams = new WindowManager.LayoutParams(1, 1, 2002, 262184, -3);

		WindowManager localWindowManager = (WindowManager)this.context.getSystemService(Context.WINDOW_SERVICE);
		this.layerView = new InvisibleTouchView(this.context);
		if (Build.VERSION.SDK_INT >= 23)
		{
			if (Settings.canDrawOverlays(this.context))
			{
				if (localWindowManager != null)
				{
					localWindowManager.addView(this.layerView, localLayoutParams);
					this.service.isTapAdded = true;
				}
			}
			else
				this.service.isTapAdded = false;
		} else if (localWindowManager != null) {
			localWindowManager.addView(this.layerView, localLayoutParams);
			this.service.isTapAdded = true;
		}
	}

	void removeView()
	{
		try {
			if (this.layerView == null)
				throw new Exception("layerView is null");
			WindowManager localWindowManager = (WindowManager)this.context.getSystemService(Context.WINDOW_SERVICE);
			if (localWindowManager == null)
				throw new Exception("localWindowManager is null");
			localWindowManager.removeView(this.layerView);
			this.service.isTapAdded = false;
			Log.d("Reading Service", "removing view");
			return;
		} catch (Exception localException) {
			Log.e("Reading", "Exception during removal");
		}
	}

	private class InvisibleTouchView extends View
	{
		InvisibleTouchView(Context paramContext)
		{
			super(paramContext);
		}

		private String last_appname = "";

		@SuppressLint({"ClickableViewAccessibility"})
		public boolean onTouchEvent(MotionEvent paramMotionEvent)
		{
			super.onTouchEvent(paramMotionEvent);
			if (paramMotionEvent != null){
				String appname = service.getForegroundAppName();
				String data = System.currentTimeMillis()
						+ "," + (appname.equals(last_appname)?"":appname)
						+ "," + context.getResources().getConfiguration().orientation;
				last_appname = appname;
				TextFileManager.getTapsLogFile().writeEncrypted(data);
				if(BuildConfig.APP_IS_DEV)
					Log.i("Taps", data);
			}
			return false;
		}
	}
}

