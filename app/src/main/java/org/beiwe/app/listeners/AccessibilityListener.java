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

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Path;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import org.beiwe.app.BackgroundService;
import org.beiwe.app.storage.TextFileManager;
import org.beiwe.app.ui.DebugInterfaceActivity;

import java.util.ArrayList;
import java.util.List;

import static android.graphics.PixelFormat.TRANSLUCENT;
import static android.view.MotionEvent.*;
import static android.view.WindowManager.LayoutParams.*;

public class AccessibilityListener extends AccessibilityService {
	public static final String name = "accessibilityLog";
	public static final String header = "timestamp,packageName,className,text,orientation";
	public static boolean listen = false;
	public static AccessibilityListener mSelf = null;

	private static GestureDescription createClick(float x, float y, int duration_ms, boolean willContinue) {
		Path clickPath = new Path();
		clickPath.moveTo(x, y);
		GestureDescription.StrokeDescription clickStroke =
				new GestureDescription.StrokeDescription(clickPath, 0, duration_ms, willContinue);
		GestureDescription.Builder clickBuilder = new GestureDescription.Builder();
		clickBuilder.addStroke(clickStroke);
		return clickBuilder.build();
	}

	private static GestureDescription createLongClick(float x, float y, int duration_ms, boolean willContinue) {
		Path clickPath = new Path();
		clickPath.moveTo(x, y);
		clickPath.rLineTo(0.1f, 0.1f );
		GestureDescription.StrokeDescription clickStroke =
				new GestureDescription.StrokeDescription(clickPath, 0, duration_ms, willContinue);
		GestureDescription.Builder clickBuilder = new GestureDescription.Builder();
		clickBuilder.addStroke(clickStroke);
		return clickBuilder.build();
	}

	private GestureDescription createStroke(int duration_ms, boolean willContinue) {
		Path clickPath = new Path();
		clickPath.moveTo(lastX, lastY);
		for(P p : XYs)
			clickPath.lineTo(p.X, p.Y);
		GestureDescription.StrokeDescription clickStroke =
				new GestureDescription.StrokeDescription(clickPath, 0, duration_ms, willContinue);
		GestureDescription.Builder clickBuilder = new GestureDescription.Builder();
		clickBuilder.addStroke(clickStroke);
		return clickBuilder.build();
	}

	class P {
		float X, Y;
		P(float x, float y){ X=x; Y=y; }
	}
	private ArrayList <P> XYs = new ArrayList<P>();
	private float lastX, lastY;

	class AccessibilityOverlayView extends View {
		AccessibilityOverlayView(Context paramContext) { super(paramContext); }

		private long lastTime, nowTime;
		private int lastAction;

		@SuppressLint({"ClickableViewAccessibility"})
		public boolean onTouchEvent( MotionEvent e )
		{
			GestureDescription gesture;
			if ( e != null ) {
				DebugInterfaceActivity.smartLog( name, e.toString() );

				switch (e.getAction()){
					case ACTION_DOWN:
						lastTime = e.getEventTime();
//						localWindowManager.removeViewImmediate( overlayView );
//						gesture = createDown( e.getRawX(), e.getRawY(), true );
//						dispatchGesture( gesture, null, null );
//						localWindowManager.addView( overlayView, localLayoutParams );
						lastX = e.getRawX();
						lastY = e.getRawY();
						break;
					case ACTION_MOVE:
						XYs.add(new P(e.getRawX(), e.getRawY()));
//						nowTime = e.getEventTime();
//						localWindowManager.removeViewImmediate( overlayView );
//						gesture = createStroke( (int)(nowTime-lastTime), true );
//						dispatchGesture( gesture, null, null );
//						localWindowManager.addView( overlayView, localLayoutParams );
//						lastTime = nowTime;
						break;
					case ACTION_UP:
						nowTime = e.getEventTime();
						localWindowManager.removeViewImmediate(overlayView);
						if(XYs.isEmpty()){
							long duration = nowTime-lastTime;
							if( duration>500 )
								gesture = createLongClick( e.getRawX(), e.getRawY(), (int)duration, false );
							else
								gesture = createClick( e.getRawX(), e.getRawY(), 1, false );
						} else
							gesture = createStroke( (int)(nowTime-lastTime), false );
						dispatchGesture( gesture, null, null );
						XYs.clear();
						localWindowManager.addView( overlayView, localLayoutParams );
						break;
					case ACTION_OUTSIDE:
					default:
				}
				lastAction = e.getAction();
			}
			return true;
		}
	}

	private Context hContext;
	private AccessibilityOverlayView overlayView;
	private WindowManager localWindowManager;
	private WindowManager.LayoutParams localLayoutParams;

	@Override
	protected void onServiceConnected() {
		super.onServiceConnected();
		listen = true;
		mSelf = this;
		hContext = getApplicationContext();
		/* After reboot/power-on, the Android OS will resume all accessibility services before background services.
		 * Thus, many BackgroundService pointers (including BackgroundService.localHandle) will be null
		 * for a few seconds before the main service gets resumed by the OS. Here, we bring up the main
		 * service immediately to speed up service resumption after reboot. */
		if ( BackgroundService.localHandle == null )
			BootListener.startBackgroundService(hContext);
	}

	public static boolean isGestureMode = false;

	public void toggleGestureMode(View view){
		try {
			if (!isGestureMode && Build.VERSION.SDK_INT >= 22) {
				int flags = FLAG_WATCH_OUTSIDE_TOUCH | SOFT_INPUT_ADJUST_PAN | FLAG_NOT_FOCUSABLE | FLAG_FULLSCREEN;
				localLayoutParams = new WindowManager.LayoutParams(
						ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
						WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, TRANSLUCENT);

				localWindowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);

				if (localWindowManager != null && (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(hContext))) {
					overlayView = new AccessibilityOverlayView(BackgroundService.localHandle.getApplicationContext());
					localWindowManager.addView(overlayView, localLayoutParams);
				}
			} else
				localWindowManager.removeViewImmediate(overlayView);
			isGestureMode = !isGestureMode;
		}catch (Exception e){}
	}

	public static boolean isEnabled(Context context){
//		if(5+3==8)
//			return true;
		if ( BackgroundService.accessibilityManager == null )
			return false;
		List<AccessibilityServiceInfo> runningServices =
				BackgroundService.accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityEvent.TYPES_ALL_MASK);
		for ( AccessibilityServiceInfo info : runningServices ){
			if (info.getId().startsWith(context.getPackageName()))
				return true;
		}
		return false;
	}

	public static String removeTrivialFields(String S){
		if(S==null)
			return "";
		String [] its = S.split(";");
		for(int x=0, X=its.length; x<X; ++x){
			String [] kv = its[x].split(":");
			if(kv.length != 2) continue;
			String kv1 = kv[1].trim();
			switch(kv1){
				case "[]":
				case "-1":
				case "null":
					its[x] = "";
					break;
			}
			if(kv1.startsWith("null"))
				its[x] = kv1.substring(4);
		}
		return String.join(";", its);
	}

	public static String traverseWindowInfo(AccessibilityWindowInfo info) {
		if(info==null)
			return "windowInfo=null";
		String ret = TextFileManager.CS2S(info.getTitle());
		for(int x=0, X=info.getChildCount(); x<X; ++x)
			ret += (x==0?"=(":"+(")+ traverseWindowInfo(info.getChild(x))+")";
		return ret;
	}

	public String traverseNodeInfo( AccessibilityNodeInfo info ) {
		if(info==null)
			return "nodeInfo=null";
		String ret = "CLS="+TextFileManager.CS2S(info.getClassName())+",PKG="+TextFileManager.CS2S(info.getPackageName())
				+",Text="+TextFileManager.CS2S(info.getText())+TextFileManager.DELIMITER;
		for(int x=0, X=info.getChildCount(); x<X; ++x) {
			try {
				AccessibilityNodeInfo child = info.getChild(x);
				ret += (x == 0 ? "=(" : "+(") + traverseNodeInfo(child) + ")";
			}catch (Exception e){
				ret += (x == 0 ? "=(" : "+(") + "exception)";
			}
		}
		return ret;
	}

	public static AccessibilityNodeInfo getRoot( AccessibilityNodeInfo info ) {
		while(info.getParent()!=null)
			info = info.getParent();
		return info;
	}

	public static String convertKeyChar( String text ){
		String L = "", R = "";
		if(text.startsWith("[") && text.endsWith("]")) {
			L = "[";
			R = "]";
			text = text.substring(1, text.length()-1);
		}
		switch (text.toLowerCase()){
			case "back":
			case "delete":
			case "backspace":
			case "enter":
			case "recent apps":
			case "overview":
			case "home":
			case "clear all":
			case "navigate up":
			case "close all recent apps":
				return L+text+R;
			default:
				if(text.length()==1){
					char ch = text.charAt(0);
					if(Character.isAlphabetic(ch)) return L+"a"+R;
					if(Character.isDigit(ch)) return L+"0"+R;
					return L+"."+R;
				}
		}
		return "[OTHER]";
	}

	private static String last_package_name = "", last_class_name = "";
	public void logi( String tag, Object object ){
		if(object instanceof String) {
			String s = (String)object;
			for (int x = 0, X = s.length() / 4000; x <= X; ++x) {
				if (x == X)
					Log.i(tag, s.substring(x * 4000));
				else
					Log.i(tag, s.substring(x * 4000, (x + 1) * 4000));
			}
		} else {
			AccessibilityEvent event = (AccessibilityEvent)object;
			String msg = TextFileManager.CS2S(event.getContentDescription());
			if( msg.isEmpty() )
				msg = TextFileManager.CS2S(event.getText());
			String package_name = TextFileManager.CS2S(event.getPackageName());
			String class_name = TextFileManager.CS2S(event.getClassName());
			String data = System.currentTimeMillis()
					+ TextFileManager.DELIMITER + (package_name.equals(last_package_name)?"":package_name)
					+ TextFileManager.DELIMITER + (class_name.equals(last_class_name)?"":class_name)
					+ TextFileManager.DELIMITER + convertKeyChar(msg)
					+ TextFileManager.DELIMITER + getResources().getConfiguration().orientation;
			last_package_name = package_name;
			last_class_name = class_name;
			TextFileManager.getAccessibilityLogFile().writeEncrypted(data);
		}
	}

	@Override
	public void onAccessibilityEvent(AccessibilityEvent event) {
		if( listen && BackgroundService.localHandle != null) {
			switch (event.getEventType()) {
				case AccessibilityEvent.TYPE_VIEW_CLICKED:
				case AccessibilityEvent.TYPE_VIEW_LONG_CLICKED:
				case AccessibilityEvent.TYPE_VIEW_HOVER_ENTER:
					logi("Gesture:onAccessibilityEvent", event);
			}
		}
	}

	@Override
	public void onInterrupt() {
//		Log.i("Gesture","location03");
	}

	@Override
	protected boolean onGesture(int gestureId){
//		Log.i("Gesture","location04");
		return super.onGesture(gestureId);
	}

	@Override
	protected boolean onKeyEvent (KeyEvent event){
		return super.onKeyEvent(event);
//		Log.i("Gesture","location05");
	}
}
