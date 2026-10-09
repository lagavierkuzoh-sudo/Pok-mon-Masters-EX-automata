package com.example.pomastersstrategist;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.pomastersstrategist.core.MoveSpec;
import com.example.pomastersstrategist.core.PlannedAction;

import java.util.ArrayList;
import java.util.List;

public final class ActionExecutor {
    public interface Callback { void onDone(boolean dispatched); }
    private final ActionProfile profile; private final TapTimingLearner timing; private final Handler h=new Handler(Looper.getMainLooper());
    private volatile boolean busy=false;
    public ActionExecutor(Context c){profile=new ActionProfile(c);timing=new TapTimingLearner(c);}
    public boolean busy(){return busy;} public long learnedGap(){return timing.gap();}

    public void execute(PlannedAction a, Callback cb){
        if(a==null||busy){if(cb!=null)cb.onDone(false);return;}
        List<Integer> taps=new ArrayList<>();
        taps.add(profile.pairAction(a.pairIndex));
        if(a.move.kind== MoveSpec.Kind.SYNC){taps.add(ActionProfile.SYNC);}
        else if(a.move.kind== MoveSpec.Kind.MAX){
            taps.add(ActionProfile.MAX); if(a.move.slot>=1&&a.move.slot<=5)taps.add(profile.moveAction(a.move.slot));
        } else {
            if(a.move.target==MoveSpec.Target.ENEMY && a.targetIndex>=0)taps.add(profile.enemyAction(a.targetIndex));
            if(a.move.slot>=1&&a.move.slot<=5)taps.add(profile.moveAction(a.move.slot));
            if(a.move.target==MoveSpec.Target.ALLY && a.targetIndex>=0)taps.add(profile.pairAction(a.targetIndex));
        }
        for(int x:taps)if(!profile.has(x)){if(cb!=null)cb.onDone(false);return;}
        busy=true; float[] before=SharedState.latestFingerprint;
        runTap(taps,0,ok->{
            if(!ok){busy=false;if(cb!=null)cb.onDone(false);return;}
            h.postDelayed(()->{
                float[] after=SharedState.latestFingerprint;
                boolean changed=before!=null&&after!=null&&BattleVision.distance(before,after)>.008f;
                timing.report(changed); busy=false; if(cb!=null)cb.onDone(true);
            },Math.max(120,timing.gap()*2));
        });
    }
    private interface Done { void call(boolean ok); }
    private void runTap(List<Integer> t,int i,Done done){
        if(i>=t.size()){done.call(true);return;}int a=t.get(i);boolean ok=BotAccessibilityService.tap(profile.x(a),profile.y(a));
        if(!ok){done.call(false);return;}h.postDelayed(()->runTap(t,i+1,done),timing.gap());
    }
}
