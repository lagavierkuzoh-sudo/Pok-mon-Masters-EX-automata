package com.example.pomastersstrategist;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * Lightweight visual state extraction. Manually calibrated regions are still honored,
 * but v2.2 can fall back to screen-only estimates for HP bars and the move gauge so
 * autonomous training can continue without human reward labels or terminal labels.
 */
public final class BattleVision {
    public static final class Result {
        public final float[] fingerprint;
        public final double gauge;
        public final double[] hp;
        public final double[] enemyHp;
        public final int allyBarsDetected;
        public final int enemyBarsDetected;
        public final double battleConfidence;
        Result(float[] f,double g,double[] h,double[] e,int a,int en,double bc){fingerprint=f;gauge=g;hp=h;enemyHp=e;allyBarsDetected=a;enemyBarsDetected=en;battleConfidence=bc;}
    }
    private static final int FW=24,FH=14;
    private BattleVision(){}

    public static Result analyze(Bitmap src,RegionProfile regions){
        Bitmap small=Bitmap.createScaledBitmap(src,FW,FH,true);
        float[] f=new float[FW*FH*3]; int q=0; float[] hsv=new float[3];
        for(int y=0;y<FH;y++)for(int x=0;x<FW;x++){
            int c=small.getPixel(x,y); Color.colorToHSV(c,hsv);
            f[q++]=hsv[0]/360f; f[q++]=hsv[1]; f[q++]=hsv[2];
        }
        if(small!=src)small.recycle();

        Bitmap scan=Bitmap.createScaledBitmap(src,160,Math.max(260,Math.min(360,(int)Math.round(160*src.getHeight()/(double)src.getWidth()))),true);
        double gauge=regions.has(0)?fill(src,regions.get(0),false):autoGauge(scan);
        double[] hp=new double[3],enemy=new double[3];int ad=0,ed=0;
        for(int i=0;i<3;i++){
            hp[i]=regions.has(i+1)?fill(src,regions.get(i+1),true):autoHp(scan,false,i);
            enemy[i]=regions.has(i+4)?fill(src,regions.get(i+4),true):autoHp(scan,true,i);
            if(hp[i]>=0)ad++;if(enemy[i]>=0)ed++;
        }
        if(scan!=src)scan.recycle();
        double confidence=Math.min(1.0,(ad+ed)/6.0*.88 + (gauge>=0?.12:0));
        return new Result(f,gauge,hp,enemy,ad,ed,confidence);
    }

    // Calibrated colored-bar occupancy estimator.
    private static double fill(Bitmap b,float[] r,boolean hp){
        int w=b.getWidth(),h=b.getHeight(); int l=clamp((int)(r[0]*w),0,w-1), rr=clamp((int)(r[2]*w),l+1,w);
        int t=clamp((int)(r[1]*h),0,h-1), bb=clamp((int)(r[3]*h),t+1,h);
        int cols=Math.max(1,rr-l), active=0; float[] hsv=new float[3];
        for(int x=l;x<rr;x++){
            int good=0,total=0;
            for(int y=t;y<bb;y+=Math.max(1,(bb-t)/8)){
                int c=b.getPixel(x,y); Color.colorToHSV(c,hsv); total++;
                boolean colored=hp ? hpColor(hsv) : gaugeColor(hsv);
                if(colored)good++;
            }
            if(good>=Math.max(1,total/3))active++;
        }
        return Math.max(0,Math.min(1,active/(double)cols));
    }

    /**
     * Screen-only fallback: each of the three slots occupies roughly one third of the
     * portrait battle scene. We search for a persistent horizontal HP-colored run in
     * the enemy (upper) or ally (middle/lower) band. Absolute geometry is deliberately
     * loose; calibrated regions override it whenever available.
     */
    private static double autoHp(Bitmap b,boolean enemy,int slot){
        int w=b.getWidth(),h=b.getHeight();
        int x0=clamp((int)((slot/3.0)*w+w*.015),0,w-1);
        int x1=clamp((int)(((slot+1)/3.0)*w-w*.015),x0+1,w);
        int y0=(int)(h*(enemy?.055:.53)), y1=(int)(h*(enemy?.37:.79));
        int best=0,bestY=-1;
        float[] hsv=new float[3];
        for(int y=y0+2;y<y1-2;y++){
            int r0=longestHpRun(b,x0,x1,y-1,hsv),r1=longestHpRun(b,x0,x1,y,hsv),r2=longestHpRun(b,x0,x1,y+1,hsv);
            int stable=Math.min(r1,Math.max(r0,r2));
            if(stable>best){best=stable;bestY=y;}
        }
        // Reject icons/text/small effects. A real HP bar is normally a wide contiguous run.
        if(bestY<0 || best<w*.055)return -1;
        double expected=w*.235;
        return Math.max(.015,Math.min(1.0,best/expected));
    }

    private static int longestHpRun(Bitmap b,int x0,int x1,int y,float[] hsv){
        int best=0,cur=0;
        for(int x=x0;x<x1;x++){
            Color.colorToHSV(b.getPixel(x,y),hsv);
            if(hpColor(hsv)){cur++;if(cur>best)best=cur;}else cur=0;
        }
        return best;
    }
    private static boolean hpColor(float[] hsv){
        float h=hsv[0],s=hsv[1],v=hsv[2];
        // Green/yellow/orange/red HP fills; avoids blue/cyan UI and most neutral text.
        return s>.42f&&v>.40f&&(h<35f||(h>=38f&&h<155f)||h>342f);
    }

    private static double autoGauge(Bitmap b){
        int w=b.getWidth(),h=b.getHeight();float[] hsv=new float[3];int best=0;
        int y0=(int)(h*.91),y1=(int)(h*.995);
        for(int y=y0;y<y1;y++){
            int cur=0,rowBest=0;
            for(int x=(int)(w*.04);x<(int)(w*.96);x++){
                Color.colorToHSV(b.getPixel(x,y),hsv);
                if(gaugeColor(hsv)){cur++;if(cur>rowBest)rowBest=cur;}else cur=0;
            }
            if(rowBest>best)best=rowBest;
        }
        if(best<w*.07)return -1;
        return Math.max(.01,Math.min(1,best/(w*.82)));
    }
    private static boolean gaugeColor(float[] hsv){return hsv[1]>.28f&&hsv[2]>.42f&&hsv[0]>=155f&&hsv[0]<=235f;}

    private static int clamp(int v,int lo,int hi){return Math.max(lo,Math.min(hi,v));}
    public static float distance(float[]a,float[]b){if(a==null||b==null||a.length!=b.length)return Float.MAX_VALUE;double s=0;for(int i=0;i<a.length;i++)s+=Math.abs(a[i]-b[i]);return(float)(s/a.length);}

    /**
     * Stage signature deliberately ignores the lower UI-heavy rows. That makes the same stage
     * more stable across changing move buttons, HP values and animation flashes.
     */
    public static String signature(float[]f){
        if(f==null||f.length<FW*FH*3)return"none";
        long h=1469598103934665603L;
        for(int y=1;y<9;y++)for(int x=2;x<22;x++){
            int i=(y*FW+x)*3;
            int hue=(int)(f[i]*5), sat=(int)(f[i+1]*3), val=(int)(f[i+2]*5);
            int q=hue | (sat<<3) | (val<<5);
            h^=q;h*=1099511628211L;
        }
        return Long.toHexString(h);
    }
}
