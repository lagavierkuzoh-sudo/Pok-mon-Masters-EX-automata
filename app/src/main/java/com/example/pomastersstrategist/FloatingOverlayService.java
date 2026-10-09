package com.example.pomastersstrategist;

import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class FloatingOverlayService extends Service {
    private WindowManager wm; private LinearLayout panel; private TextView status,reason; private GridLayout actionGrid,regionGrid; private View calibration; private final Handler ui=new Handler(Looper.getMainLooper());
    private ActionProfile actions; private RegionProfile regions; private int regionCal=-1; private float firstX,firstY; private boolean firstPoint=false;
    private final Runnable refresher=new Runnable(){@Override public void run(){if(status!=null){status.setText(statusText());reason.setText(SharedState.runtime(FloatingOverlayService.this).reason());}ui.postDelayed(this,450);}};
    @Override public void onCreate(){super.onCreate();SharedState.init(this);actions=SharedState.actions(this);regions=SharedState.regions(this);wm=(WindowManager)getSystemService(WINDOW_SERVICE);show();ui.post(refresher);}
    private Button b(String s){Button x=new Button(this);x.setText(s);x.setTextSize(10);x.setMinHeight(0);x.setMinWidth(0);x.setPadding(5,1,5,1);return x;}
    private WindowManager.LayoutParams lp(int w,int h,int flags){return new WindowManager.LayoutParams(w,h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,flags, PixelFormat.TRANSLUCENT);}
    private void show(){
        panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(6,4,6,4);panel.setBackgroundColor(0xDA11151A);
        LinearLayout top=new LinearLayout(this);Button mode=b(modeName()),reset=b("RESET"),cal=b("CAL");top.addView(mode);top.addView(reset);top.addView(cal);panel.addView(top);
        status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(10);panel.addView(status);reason=new TextView(this);reason.setTextColor(0xFFB0BEC5);reason.setTextSize(9);panel.addView(reason);
        actionGrid=new GridLayout(this);actionGrid.setColumnCount(4);actionGrid.setVisibility(View.GONE);
        for(int i=0;i<actions.size();i++){final int a=i;Button x=b(labelAction(i));x.setOnClickListener(v->{if(actions.has(a))BotAccessibilityService.tap(actions.x(a),actions.y(a));else toast("Long-press to calibrate");});x.setOnLongClickListener(v->{beginActionCal(a);return true;});actionGrid.addView(x);}panel.addView(actionGrid);
        regionGrid=new GridLayout(this);regionGrid.setColumnCount(4);regionGrid.setVisibility(View.GONE);
        for(int i=0;i<RegionProfile.NAMES.length;i++){final int r=i;Button x=b(labelRegion(i));x.setOnClickListener(v->beginRegionCal(r));regionGrid.addView(x);}panel.addView(regionGrid);
        mode.setOnClickListener(v->{BotConfig.mode=(BotConfig.mode+1)%3;mode.setText(modeName());toast(modeName()+" mode");});
        reset.setOnClickListener(v->{SharedState.runtime(this).reset();toast("Internal episode reset");});
        cal.setOnClickListener(v->{boolean show=actionGrid.getVisibility()!=View.VISIBLE;actionGrid.setVisibility(show?View.VISIBLE:View.GONE);regionGrid.setVisibility(show?View.VISIBLE:View.GONE);});
        WindowManager.LayoutParams p=lp(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);p.gravity= Gravity.TOP|Gravity.START;p.x=5;p.y=80;wm.addView(panel,p);
    }
    private String modeName(){return BotConfig.mode==BotConfig.MODE_ASSIST?"ASSIST":(BotConfig.mode==BotConfig.MODE_AUTO?"AUTO":"SELF");}
    private String labelAction(int i){return actions.name(i)+(actions.has(i)?" ✓":" ?");} private String labelRegion(int i){return RegionProfile.NAMES[i]+(regions.has(i)?" ✓":" auto");}
    private void refreshLabels(){for(int i=0;i<actionGrid.getChildCount();i++)((Button)actionGrid.getChildAt(i)).setText(labelAction(i));for(int i=0;i<regionGrid.getChildCount();i++)((Button)regionGrid.getChildAt(i)).setText(labelRegion(i));}
    private void beginActionCal(int a){if(calibration!=null)return;panel.setVisibility(View.GONE);calibration=new View(this);calibration.setBackgroundColor(0x01000000);calibration.setOnTouchListener((v,e)->{if(e.getActionMasked()==MotionEvent.ACTION_DOWN){float nx=e.getRawX()/getResources().getDisplayMetrics().widthPixels,ny=e.getRawY()/getResources().getDisplayMetrics().heightPixels;actions.set(a,nx,ny);BotAccessibilityService.tap(nx,ny);endCal();toast(actions.name(a)+" calibrated");return true;}return true;});wm.addView(calibration,lp(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN));toast("Tap real "+actions.name(a));}
    private void beginRegionCal(int r){if(calibration!=null)return;regionCal=r;firstPoint=false;panel.setVisibility(View.GONE);calibration=new View(this);calibration.setBackgroundColor(0x01000000);calibration.setOnTouchListener((v,e)->{if(e.getActionMasked()!=MotionEvent.ACTION_DOWN)return true;float nx=e.getRawX()/getResources().getDisplayMetrics().widthPixels,ny=e.getRawY()/getResources().getDisplayMetrics().heightPixels;if(!firstPoint){firstX=nx;firstY=ny;firstPoint=true;toast("Now tap opposite corner");}else{regions.set(regionCal,firstX,firstY,nx,ny);endCal();toast(RegionProfile.NAMES[regionCal]+" region saved");}return true;});wm.addView(calibration,lp(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN));toast("Tap top-left of "+RegionProfile.NAMES[r]);}
    private void endCal(){if(calibration!=null){wm.removeView(calibration);calibration=null;}panel.setVisibility(View.VISIBLE);refreshLabels();}
    private String statusText(){return modeName()+" | "+SharedState.runtime(this).status()+"\n"+SharedState.brain(this).stats()+" | tapGap="+SharedState.executor(this).learnedGap()+"ms";}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_SHORT).show();}
    @Override public void onDestroy(){ui.removeCallbacks(refresher);if(calibration!=null)wm.removeView(calibration);if(panel!=null)wm.removeView(panel);SharedState.brain(this).save();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
