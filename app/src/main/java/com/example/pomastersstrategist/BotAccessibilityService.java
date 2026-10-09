package com.example.pomastersstrategist;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.view.accessibility.AccessibilityEvent;

public class BotAccessibilityService extends AccessibilityService {
    private static volatile BotAccessibilityService instance; private static volatile String foreground="";
    @Override protected void onServiceConnected(){instance=this;} @Override public void onDestroy(){if(instance==this)instance=null;super.onDestroy();}
    @Override public void onAccessibilityEvent(AccessibilityEvent e){if(e!=null&&e.getPackageName()!=null)foreground=e.getPackageName().toString();}
    @Override public void onInterrupt(){}
    public static boolean ready(){return instance!=null;} public static boolean targetForeground(){return BotConfig.TARGET_PACKAGE.equals(foreground);}
    public static boolean tap(float nx,float ny){BotAccessibilityService s=instance;if(s==null||!targetForeground())return false;int w=s.getResources().getDisplayMetrics().widthPixels,h=s.getResources().getDisplayMetrics().heightPixels;Path p=new Path();p.moveTo(Math.max(0,Math.min(1,nx))*w,Math.max(0,Math.min(1,ny))*h);return s.dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,16)).build(),null,null);}
}
