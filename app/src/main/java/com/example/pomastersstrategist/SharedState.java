package com.example.pomastersstrategist;

import android.content.Context;

public final class SharedState {
    private SharedState(){}
    private static PairCatalog catalog; private static LearningBrain brain; private static BattleRuntime runtime; private static TerminalClassifier terminal; private static ActionExecutor executor; private static ActionProfile actions; private static RegionProfile regions;
    public static volatile float[] latestFingerprint; public static volatile long latestFrameTime;
    public static synchronized void init(Context c){Context a=c.getApplicationContext();if(catalog==null)catalog=new PairCatalog(a);if(brain==null)brain=new LearningBrain(a);if(runtime==null)runtime=new BattleRuntime(a,catalog,brain);if(terminal==null)terminal=new TerminalClassifier(a);if(executor==null)executor=new ActionExecutor(a);if(actions==null)actions=new ActionProfile(a);if(regions==null)regions=new RegionProfile(a);}
    public static PairCatalog catalog(Context c){init(c);return catalog;} public static LearningBrain brain(Context c){init(c);return brain;} public static BattleRuntime runtime(Context c){init(c);return runtime;} public static TerminalClassifier terminal(Context c){init(c);return terminal;} public static ActionExecutor executor(Context c){init(c);return executor;} public static ActionProfile actions(Context c){init(c);return actions;} public static RegionProfile regions(Context c){init(c);return regions;}
    public static synchronized void reloadCatalogAndTeam(Context c){init(c);catalog.reload();runtime.refreshTeam();}
}
