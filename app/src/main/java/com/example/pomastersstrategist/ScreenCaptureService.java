package com.example.pomastersstrategist;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import com.example.pomastersstrategist.core.PlannedAction;

import java.nio.ByteBuffer;

public class ScreenCaptureService extends Service {
    public static final String EXTRA_RESULT_CODE="resultCode",EXTRA_DATA="data"; private static final int NOTIFY=73;
    private HandlerThread thread; private Handler handler; private MediaProjection projection; private VirtualDisplay display; private ImageReader reader;
    private volatile boolean processing=false; private long lastDecision=0,lastTerminal=0;
    private AutonomousLoop autonomous;

    @Override public void onCreate(){super.onCreate();SharedState.init(this);autonomous=new AutonomousLoop(this);createChannel();startForeground(NOTIFY,notification("Autonomous strategist ready"));}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(projection!=null||intent==null)return START_STICKY; int resultCode=intent.getIntExtra(EXTRA_RESULT_CODE,0); Intent data=intent.getParcelableExtra(EXTRA_DATA);if(data==null){stopSelf();return START_NOT_STICKY;}
        MediaProjectionManager m=(MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);projection=m.getMediaProjection(resultCode,data);projection.registerCallback(new MediaProjection.Callback(){@Override public void onStop(){stopSelf();}},new Handler(getMainLooper()));
        DisplayMetrics dm=new DisplayMetrics();WindowManager wm=(WindowManager)getSystemService(WINDOW_SERVICE);if(Build.VERSION.SDK_INT>=30){dm.widthPixels=wm.getCurrentWindowMetrics().getBounds().width();dm.heightPixels=wm.getCurrentWindowMetrics().getBounds().height();dm.densityDpi=getResources().getDisplayMetrics().densityDpi;}else wm.getDefaultDisplay().getRealMetrics(dm);
        thread=new HandlerThread("masters-strategist-capture");thread.start();handler=new Handler(thread.getLooper());reader=ImageReader.newInstance(dm.widthPixels,dm.heightPixels, PixelFormat.RGBA_8888,2);display=projection.createVirtualDisplay("MastersStrategist",dm.widthPixels,dm.heightPixels,dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader.getSurface(),null,handler);reader.setOnImageAvailableListener(this::onFrame,handler);return START_STICKY;
    }

    private void onFrame(ImageReader ir){
        Image image=null;Bitmap frame=null;
        try{
            image=ir.acquireLatestImage();if(image==null||processing)return;processing=true;
            Image.Plane p=image.getPlanes()[0];ByteBuffer buffer=p.getBuffer();int ps=p.getPixelStride(),rs=p.getRowStride(),pad=rs-ps*image.getWidth();
            Bitmap padded=Bitmap.createBitmap(image.getWidth()+pad/ps,image.getHeight(),Bitmap.Config.ARGB_8888);padded.copyPixelsFromBuffer(buffer);frame=Bitmap.createBitmap(padded,0,0,image.getWidth(),image.getHeight());padded.recycle();

            BattleVision.Result vision=BattleVision.analyze(frame,SharedState.regions(this));
            SharedState.latestFingerprint=vision.fingerprint;SharedState.latestFrameTime=System.currentTimeMillis();
            BattleRuntime rt=SharedState.runtime(this);rt.onVision(vision);
            long now=System.currentTimeMillis();

            if(BotConfig.mode==BotConfig.MODE_SELFPLAY){
                autonomous.onFrame(frame,vision,rt);
                if(autonomous.blocksPlanning())return;
                // Do not issue battle actions on menus/results just because the last simulated state has legal moves.
                if(vision.battleConfidence<.18)return;
            }else{
                int terminal=SharedState.terminal(this).classify(vision.fingerprint);
                if(terminal!=0&&now-lastTerminal>2500){rt.finish(terminal,false);lastTerminal=now;return;}
            }

            boolean learning=BotConfig.mode==BotConfig.MODE_SELFPLAY;
            PlannedAction planned=rt.plan(learning);
            if((BotConfig.mode==BotConfig.MODE_AUTO||BotConfig.mode==BotConfig.MODE_SELFPLAY)&&planned!=null&&now-lastDecision>=BotConfig.MIN_DECISION_MS&&!SharedState.executor(this).busy()&&BotAccessibilityService.ready()&&BotAccessibilityService.targetForeground()){
                lastDecision=now;final PlannedAction action=planned;
                SharedState.executor(this).execute(action,ok->{if(ok)SharedState.runtime(this).executed(action);});
            }
        }catch(Exception ignored){}
        finally{if(frame!=null&&!frame.isRecycled())frame.recycle();if(image!=null)image.close();processing=false;}
    }

    private Notification notification(String s){return new Notification.Builder(this,"masters_strategist").setContentTitle("Masters Strategist SELF").setContentText(s).setSmallIcon(android.R.drawable.ic_media_play).setOngoing(true).build();}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26){NotificationChannel c=new NotificationChannel("masters_strategist","Masters Strategist", NotificationManager.IMPORTANCE_LOW);getSystemService(NotificationManager.class).createNotificationChannel(c);}}
    @Override public void onDestroy(){if(reader!=null)reader.close();if(display!=null)display.release();if(projection!=null)projection.stop();if(thread!=null)thread.quitSafely();SharedState.brain(this).save();super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
