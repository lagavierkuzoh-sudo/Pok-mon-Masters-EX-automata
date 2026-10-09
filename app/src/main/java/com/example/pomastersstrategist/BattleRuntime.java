package com.example.pomastersstrategist;

import android.content.Context;

import com.example.pomastersstrategist.core.BattlePlanner;
import com.example.pomastersstrategist.core.BattleState;
import com.example.pomastersstrategist.core.PairDefinition;
import com.example.pomastersstrategist.core.PlannedAction;
import com.example.pomastersstrategist.core.StrategyProfile;
import com.example.pomastersstrategist.core.TeamBuild;

/** Runtime bridge between screen observations, semantic planner and autonomous learner. */
public final class BattleRuntime {
    private final PairCatalog catalog; private final TeamConfigStore teamStore; private final LearningBrain brain; private final BattlePlanner planner=new BattlePlanner();
    private TeamBuild team; private PairDefinition[] defs=new PairDefinition[3]; private BattleState state;
    private String stageSig="none"; private boolean begun=false; private PlannedAction suggestion;
    private double[] priorHp={-1,-1,-1}, priorEnemyHp={-1,-1,-1};
    private final int[] missingAlly=new int[3],missingEnemy=new int[3];
    private final boolean[] seenAlly=new boolean[3],seenEnemy=new boolean[3];
    private double cumulativeEnemyLoss=0,cumulativeAllyLoss=0,lastBattleConfidence=0;
    private int observedBattleFrames=0,noBattleFrames=0;

    public BattleRuntime(Context c,PairCatalog catalog,LearningBrain brain){this.catalog=catalog;this.brain=brain;teamStore=new TeamConfigStore(c);reset();}
    public synchronized void refreshTeam(){
        team=teamStore.load(catalog);for(int i=0;i<3;i++)defs[i]=catalog.get(team.slots[i].pairId);state=BattleState.initial(team,defs);
        begun=false;stageSig="none";suggestion=null;priorHp=new double[]{-1,-1,-1};priorEnemyHp=new double[]{-1,-1,-1};
        for(int i=0;i<3;i++){missingAlly[i]=missingEnemy[i]=0;seenAlly[i]=seenEnemy[i]=false;}
        cumulativeEnemyLoss=cumulativeAllyLoss=0;lastBattleConfidence=0;observedBattleFrames=noBattleFrames=0;
    }
    public synchronized void reset(){refreshTeam();}

    public synchronized void onVision(BattleVision.Result v){
        if(v==null)return;
        lastBattleConfidence=v.battleConfidence;
        if(v.battleConfidence>=.24){observedBattleFrames++;noBattleFrames=0;}else if(observedBattleFrames>0)noBattleFrames++;

        if(!begun&&v.fingerprint!=null&&v.battleConfidence>=.24){stageSig=BattleVision.signature(v.fingerprint);brain.begin(stageSig,team.signature());begun=true;}
        if(v.gauge>=0)state.gauge=Math.max(0,Math.min(6,v.gauge*6.0));

        double allyLoss=0,enemyLoss=0; int enemyKos=0;
        for(int i=0;i<3;i++){
            if(v.hp[i]>=0){
                double nh=Math.max(0,Math.min(1,v.hp[i]));seenAlly[i]=true;missingAlly[i]=0;
                if(priorHp[i]>=0){
                    double d=Math.max(0,Math.min(.60,priorHp[i]-nh));
                    if(d>.025)state.timesHit[i]++;
                    allyLoss+=d;cumulativeAllyLoss+=d;
                }
                state.hp[i]=nh;priorHp[i]=nh;
            }else if(seenAlly[i]&&v.allyBarsDetected>0){
                missingAlly[i]++;
                if(missingAlly[i]>=BotConfig.BAR_MISSING_FRAMES_FOR_KO && state.hp[i]>.001){
                    double d=Math.max(0,priorHp[i]);allyLoss+=d;cumulativeAllyLoss+=d;state.hp[i]=0;priorHp[i]=0;
                }
            }
        }
        for(int i=0;i<3;i++){
            if(v.enemyHp[i]>=0){
                double nh=Math.max(0,Math.min(1,v.enemyHp[i]));seenEnemy[i]=true;missingEnemy[i]=0;
                if(priorEnemyHp[i]>=0){
                    double d=Math.max(0,Math.min(.70,priorEnemyHp[i]-nh));enemyLoss+=d;cumulativeEnemyLoss+=d;
                    if(priorEnemyHp[i]>.05 && nh<=.02)enemyKos++;
                }
                state.enemyHp[i]=nh;priorEnemyHp[i]=nh;
            }else if(seenEnemy[i]&&v.enemyBarsDetected>0){
                missingEnemy[i]++;
                if(missingEnemy[i]>=BotConfig.BAR_MISSING_FRAMES_FOR_KO && state.enemyHp[i]>.001){
                    double d=Math.max(0,priorEnemyHp[i]);enemyLoss+=d;cumulativeEnemyLoss+=d;
                    if(priorEnemyHp[i]>.05)enemyKos++;
                    state.enemyHp[i]=0;priorEnemyHp[i]=0;
                }
            }
        }

        // Self-generated dense reward. No human +/- feedback is required.
        if(begun && (enemyLoss>.008 || allyLoss>.008 || enemyKos>0)){
            double r=Math.min(.45,enemyLoss*.70 + enemyKos*.18 - allyLoss*.20);
            if(Math.abs(r)>.01)brain.reinforceRecent(r);
        }
    }

    public synchronized PlannedAction plan(boolean selfPlay){
        StrategyProfile strategy=brain.strategy(selfPlay);
        suggestion=planner.plan(state,team,defs,strategy,(ctx,key)->brain.bonus(ctx,key,selfPlay));
        return suggestion;
    }
    public synchronized void executed(PlannedAction a){if(a==null)return;String ctx=state.contextBucket();brain.record(ctx,a.key());planner.apply(state,team,defs,a);suggestion=a;}
    public synchronized void feedback(double r){brain.feedback(r);} // optional diagnostics; self-play does not require it
    public synchronized void finish(int result,boolean selfPlay){if(result==0||state.battleOver)return;state.battleOver=true;brain.finish(result,selfPlay);}

    /** Direct terminal inference from visually observed combatants. */
    public synchronized int inferTerminal(){
        if(!begun||observedBattleFrames<5)return 0;
        int enemySeen=0,enemyAlive=0,allySeen=0,allyAlive=0;
        for(int i=0;i<3;i++){
            if(seenEnemy[i]){enemySeen++;if(state.enemyHp[i]>.025)enemyAlive++;}
            if(seenAlly[i]){allySeen++;if(state.hp[i]>.025)allyAlive++;}
        }
        if(enemySeen>0&&enemyAlive==0)return +1;
        if(allySeen>0&&allyAlive==0)return -1;
        return 0;
    }

    /** Result screen usually removes combat HP/gauge UI. Wait several frames before using this fallback. */
    public synchronized boolean resultTransitionReady(){return begun&&observedBattleFrames>=8&&noBattleFrames>=BotConfig.NO_BATTLE_FRAMES_FOR_RESULT;}
    public synchronized int inferTransitionOutcome(){
        if(!resultTransitionReady())return 0;
        // Dense visual history is the tie-breaker when the final KO frame was hidden by an animation.
        if(cumulativeEnemyLoss>cumulativeAllyLoss+.18)return +1;
        if(cumulativeAllyLoss>cumulativeEnemyLoss+.18)return -1;
        int ea=0,aa=0;for(int i=0;i<3;i++){if(seenEnemy[i]&&state.enemyHp[i]>.08)ea++;if(seenAlly[i]&&state.hp[i]>.08)aa++;}
        if(ea==0&&aa>0)return +1;if(aa==0&&ea>0)return -1;
        return 0;
    }

    public synchronized boolean hasObservedBattle(){return observedBattleFrames>=5;}
    public synchronized double outcomeSignal(){return cumulativeEnemyLoss-cumulativeAllyLoss;}
    public synchronized double battleConfidence(){return lastBattleConfidence;}

    public synchronized String status(){
        String p=suggestion==null?"waiting":suggestion.toString();
        return "g="+String.format(java.util.Locale.US,"%.1f",state.gauge)+" cd="+state.syncCountdown+" hp="+(int)(state.hp[0]*100)+"/"+(int)(state.hp[1]*100)+"/"+(int)(state.hp[2]*100)+" foe="+enemyHpText()+" vis="+String.format(java.util.Locale.US,"%.2f",lastBattleConfidence)+" | "+p;
    }
    private String enemyHpText(){StringBuilder b=new StringBuilder();for(int i=0;i<3;i++){if(i>0)b.append('/');b.append(state.enemyHp[i]<0?"?":String.valueOf((int)(state.enemyHp[i]*100)));}return b.toString();}
    public synchronized String reason(){return suggestion==null?"":suggestion.reason;}
    public synchronized TeamBuild team(){return team;}
    public synchronized PairDefinition[] defs(){return defs.clone();}
    public synchronized String stageSig(){return stageSig;}
}
