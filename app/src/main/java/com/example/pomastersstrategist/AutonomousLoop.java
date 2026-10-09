package com.example.pomastersstrategist;

import android.content.Context;
import android.graphics.Bitmap;

/**
 * Autonomous episode manager. It receives only visual evidence, determines when an
 * episode is over, applies win/loss learning, navigates result screens, and resets
 * the simulated battle state when the next battle appears.
 *
 * No + / - labels and no manually taught victory/defeat screenshots are required.
 */
public final class AutonomousLoop {
    private final ActionProfile actions;
    private boolean navigating=false;
    private boolean battleSeen=false;
    private long terminalAt=0,lastNavTap=0;
    private int navAttempt=0,lastResult=0;

    public AutonomousLoop(Context c){actions=SharedState.actions(c);}

    public synchronized boolean blocksPlanning(){return navigating;}
    public synchronized int lastResult(){return lastResult;}

    public synchronized void onFrame(Bitmap frame, BattleVision.Result v, BattleRuntime rt){
        if(BotConfig.mode!=BotConfig.MODE_SELFPLAY||v==null)return;
        long now=System.currentTimeMillis();

        if(!navigating){
            if(v.battleConfidence>=.28)battleSeen=true;
            int result=rt.inferTerminal();
            if(result==0 && battleSeen && rt.resultTransitionReady())result=rt.inferTransitionOutcome();
            if(result!=0){
                rt.finish(result,true);
                navigating=true;terminalAt=now;lastNavTap=0;navAttempt=0;lastResult=result;
                return;
            }
            return;
        }

        // Once the result flow has actually started a fresh battle, create a fresh
        // internal state/episode before allowing the planner to act again.
        if(now-terminalAt>2400 && v.battleConfidence>=.42){
            rt.reset();
            rt.onVision(v); // seed the fresh episode from the already captured first battle frame
            navigating=false;battleSeen=true;navAttempt=0;
            return;
        }

        if(now-terminalAt<BotConfig.POST_TERMINAL_DELAY_MS || now-lastNavTap<BotConfig.RESULT_NAV_INTERVAL_MS)return;
        lastNavTap=now;

        // If the user calibrated explicit result buttons, cycle through safe result-flow
        // actions. Otherwise visually locate the largest colored button near the bottom.
        int[] sequence={ActionProfile.NEXT,ActionProfile.OK,ActionProfile.REPLAY,ActionProfile.OK,ActionProfile.REPLAY,ActionProfile.NEXT};
        int a=sequence[Math.min(navAttempt,sequence.length-1)];navAttempt++;
        if(actions.has(a)){
            BotAccessibilityService.tap(actions.x(a),actions.y(a));
            return;
        }
        float[] p=AutoButtonFinder.findPrimary(frame);
        if(p!=null)BotAccessibilityService.tap(p[0],p[1]);
    }
}
