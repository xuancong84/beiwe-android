package org.beiwe.app.listeners;

import android.accessibilityservice.AccessibilityService;
import android.graphics.PixelFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityRecord;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.FrameLayout;

import org.beiwe.app.BackgroundService;
import org.beiwe.app.R;
import org.beiwe.app.storage.TextFileManager;

import java.util.List;

public class AccessibilityListener extends AccessibilityService {
	public static String header = "timestamp,,";
	public static AccessibilityListener service_handle = null;
	public static BackgroundService backgroundService = null;
	public static boolean show, listen;
	public static int level;

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

	public static String CS2S(CharSequence seq){
		return (seq==null?"":(String)seq);
	}

	public static String traverseWindowInfo(AccessibilityWindowInfo info) {
		if(info==null)
			return "windowInfo=null";
		String ret = CS2S(info.getTitle());
		for(int x=0, X=info.getChildCount(); x<X; ++x)
			ret += (x==0?"=(":"+(")+ traverseWindowInfo(info.getChild(x))+")";
		return ret;
	}

	public String traverseNodeInfo(AccessibilityNodeInfo info) {
		if(info==null)
			return "nodeInfo=null";
		String ret = "CLS="+CS2S(info.getClassName())+",PKG="+CS2S(info.getPackageName())
				+",Text="+CS2S(info.getText())+",";
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

	public static AccessibilityNodeInfo getRoot(AccessibilityNodeInfo info){
		while(info.getParent()!=null)
			info = info.getParent();
		return info;
	}

	public static String convertKeyChar(String text){
		switch (text){
			case "Back":
			case "Delete":
			case "Backspace":
			case "Enter":
			case "Recent apps":
			case "Overview":
			case "Home":
			case "Close all recent apps":
				return text;
			default:
				if(text.length()==1){
					char ch = text.charAt(0);
					if(Character.isAlphabetic(ch)) return "a";
					if(Character.isDigit(ch)) return "0";
					return ".";
				}
		}
		return "[Other]";
	}

	public void logi(String tag, Object object){
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
			String ch = convertKeyChar(CS2S(event.getContentDescription()));
			String data = System.currentTimeMillis()+","+CS2S(event.getPackageName())+","+ch+","
					+ getBaseContext().getResources().getConfiguration().orientation;
			TextFileManager fileManager = TextFileManager.getAccessibilityLogFile();
			if( fileManager != null )
				fileManager.writeEncrypted(data);
			Log.i("Gesture:onAccessibilityEvent", data);
		}
	}

	@Override
	public void onAccessibilityEvent(AccessibilityEvent event) {
		if(listen) {
			switch (event.getEventType()) {
				case AccessibilityEvent.TYPE_VIEW_CLICKED:
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

	protected void onServiceConnected() {
		super.onServiceConnected();
		level = 0;
		listen = true;
		service_handle = this;
	}
}
