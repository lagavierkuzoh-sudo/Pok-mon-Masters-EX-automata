package com.example.pomastersstrategist;

import android.graphics.Bitmap;
import android.graphics.Color;

import java.util.ArrayDeque;

/**
 * Finds a large bright/saturated result/menu button in the lower half of the screen.
 * It is only used after the autonomous episode detector has already decided that the
 * battle ended, so it never scans/taps arbitrary controls during combat.
 */
public final class AutoButtonFinder {
    private AutoButtonFinder(){}

    public static float[] findPrimary(Bitmap src){
        if(src==null)return null;
        final int W=120;
        final int H=Math.max(180,Math.min(240,(int)Math.round(W*(src.getHeight()/(double)src.getWidth()))));
        Bitmap b=Bitmap.createScaledBitmap(src,W,H,true);
        boolean[] on=new boolean[W*H];
        float[] hsv=new float[3];
        int y0=(int)(H*.52);
        for(int y=y0;y<H;y++)for(int x=2;x<W-2;x++){
            int c=b.getPixel(x,y);Color.colorToHSV(c,hsv);
            // Result buttons are normally colored and luminous; reject dark backgrounds and white text.
            on[y*W+x]=hsv[1]>.28f && hsv[2]>.53f;
        }
        boolean[] seen=new boolean[on.length];
        ArrayDeque<Integer> q=new ArrayDeque<>();
        double bestScore=-1;int bestCx=-1,bestCy=-1;
        for(int y=y0;y<H;y++)for(int x=2;x<W-2;x++){
            int start=y*W+x;if(!on[start]||seen[start])continue;
            seen[start]=true;q.add(start);int n=0,minX=x,maxX=x,minY=y,maxY=y,sumX=0,sumY=0;
            while(!q.isEmpty()){
                int p=q.removeFirst(),px=p%W,py=p/W;n++;sumX+=px;sumY+=py;
                if(px<minX)minX=px;if(px>maxX)maxX=px;if(py<minY)minY=py;if(py>maxY)maxY=py;
                if(px>0)push(p-1,on,seen,q);if(px<W-1)push(p+1,on,seen,q);if(py>y0)push(p-W,on,seen,q);if(py<H-1)push(p+W,on,seen,q);
            }
            int cw=maxX-minX+1,ch=maxY-minY+1;
            if(cw<W*.12 || ch<H*.018 || n<W*H*.0025)continue;
            double fill=n/(double)(cw*ch);
            if(fill<.18)continue;
            double cx=sumX/(double)n,cy=sumY/(double)n;
            double score=n*(.8+cy/H)*(1.0+Math.min(2.0,cw/(double)Math.max(1,ch))*.12);
            // Prefer buttons not glued to the extreme edges.
            if(cx<W*.08||cx>W*.92)score*=.45;
            if(score>bestScore){bestScore=score;bestCx=(int)Math.round(cx);bestCy=(int)Math.round(cy);}
        }
        b.recycle();
        if(bestCx<0)return null;
        return new float[]{bestCx/(float)W,bestCy/(float)H};
    }
    private static void push(int p,boolean[] on,boolean[] seen,ArrayDeque<Integer>q){if(p>=0&&p<on.length&&on[p]&&!seen[p]){seen[p]=true;q.addLast(p);}}
}
