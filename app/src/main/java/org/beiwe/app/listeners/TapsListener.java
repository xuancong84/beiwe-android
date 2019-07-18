package org.beiwe.app.listeners;

/*************************************************************************
 *
 * MOH Office of Healthcare Transformation (MOHT) CONFIDENTIAL
 *
 *  Copyright 2018-2019
 *  All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains
 * the property of MOH Office of Healthcare Transformation.
 * The intellectual and technical concepts contained
 * herein are proprietary to MOH Office of Healthcare Transformation
 * and may be covered by Singapore, U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 * Dissemination of this information or reproduction of this material
 * is strictly forbidden unless prior written permission is obtained
 * from MOH Office of Healthcare Transformation.
 */

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import org.beiwe.app.*;
import org.beiwe.app.storage.TextFileManager;

public class TapsListener {
	public static final String name = "tapsLog";
	public static final String header = "timestamp,in_app_name,orientation";
	private final Context context;
	private BackgroundService service;
	private InvisibleTouchView layerView;
	private WindowManager localWindowManager;

	public TapsListener(BackgroundService paramBackgroundService)
	{
		context = paramBackgroundService.getApplicationContext();
		service = paramBackgroundService;
		localWindowManager = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
		layerView = new InvisibleTouchView(context);
		new Intent("android.intent.action.MAIN").addCategory("android.intent.category.HOME");
		addView();
	}

	public void addView()
	{
		if ( this == null || service.isTapAdded ) return;
//		int flags = FLAG_WATCH_OUTSIDE_TOUCH|SOFT_INPUT_ADJUST_PAN|FLAG_NOT_FOCUSABLE|FLAG_NOT_TOUCHABLE;
		WindowManager.LayoutParams localLayoutParams = new WindowManager.LayoutParams(
				1, 1, Build.VERSION.SDK_INT>=26?2038:2002, 262184, -3);

		if ( Build.VERSION.SDK_INT >= 23 )
		{
			if (Settings.canDrawOverlays(context))
			{
				if ( localWindowManager != null )
				{
					localWindowManager.addView(layerView, localLayoutParams);
					service.isTapAdded = true;
				}
			}
			else
				service.isTapAdded = false;
		} else if (localWindowManager != null) {
			localWindowManager.addView(layerView, localLayoutParams);
			service.isTapAdded = true;
		}
	}

	public void removeView()
	{
		if ( this == null || layerView == null || localWindowManager == null ) return;
		localWindowManager.removeView(layerView);
		service.isTapAdded = false;
	}

	private class InvisibleTouchView extends View
	{
		InvisibleTouchView(Context paramContext)
		{
			super(paramContext);
		}

		private String last_appname = "";

		@SuppressLint({"ClickableViewAccessibility"})
		public boolean onTouchEvent( MotionEvent paramMotionEvent )
		{
			super.onTouchEvent( paramMotionEvent );
			if ( paramMotionEvent != null ){
				String appname = TextFileManager.CS2S(service.getForegroundAppName());
				String data = System.currentTimeMillis()
						+ TextFileManager.DELIMITER + ( appname.equals(last_appname)?"":appname )
						+ TextFileManager.DELIMITER + context.getResources().getConfiguration().orientation;
				last_appname = appname;
				TextFileManager.getTapsLogFile().writeEncrypted( data );
			}
			return false;
		}
	}
}

