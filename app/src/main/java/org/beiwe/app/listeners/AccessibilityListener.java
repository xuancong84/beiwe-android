package org.beiwe.app.listeners;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Context;
import android.util.Log;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import org.beiwe.app.BackgroundService;
import org.beiwe.app.BuildConfig;
import org.beiwe.app.storage.TextFileManager;

import java.util.List;

public class AccessibilityListener extends AccessibilityService {
	public static String header = "timestamp,packageName,className,text,orientation";
	public static boolean listen = false;
	public static AccessibilityListener mSelf = null;

	@Override
	protected void onServiceConnected() {
		super.onServiceConnected();
		listen = true;
		mSelf = this;
		/* After reboot/power-on, the Android OS will resume all accessibility services before background services.
		 * Thus, many BackgroundService pointers (including BackgroundService.localHandle) will be null
		 * for a few seconds before the main service gets resumed by the OS. Here, we bring up the main
		 * service immediately to speed up service resumption after reboot. */
		if ( BackgroundService.localHandle == null )
			BootListener.startBackgroundService( getApplicationContext() );
	}

	public static boolean isEnabled(Context context){
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
		if(text.startsWith("[") && text.endsWith("]") && text.length()<=16)
			return text;
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
				return text;
			default:
				if(text.length()==1){
					char ch = text.charAt(0);
					if(Character.isAlphabetic(ch)) return "a";
					if(Character.isDigit(ch)) return "0";
					return ".";
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
			TextFileManager.getAccessibilityLogFile().writeEncrypted(data);;
			if( BuildConfig.APP_IS_DEV )
				Log.i("Gesture:onAccessibilityEvent", data);
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
