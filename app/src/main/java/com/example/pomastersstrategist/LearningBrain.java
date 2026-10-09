package com.example.pomastersstrategist;

import android.content.Context;

import com.example.pomastersstrategist.core.StrategyProfile;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;

/**
 * Autonomous stage/team learner.
 *
 * The semantic planner provides strong prior knowledge. This class specializes that
 * prior by observing only game outcomes: enemy HP loss, ally HP loss, KOs and final
 * victory/defeat. There is no requirement for a human to press reward buttons.
 */
public final class LearningBrain {
    private static final int HISTORY=72, MAX_Q=60000;
    private static final double ALPHA=.18, GAMMA=.94;
    private static final class Q { double v; int n; Q(double v,int n){this.v=v;this.n=n;} }
    private static final class Step {String c,a;Step(String c,String a){this.c=c;this.a=a;}}
    private static final class Policy {
        StrategyProfile current=new StrategyProfile(),best=current.copy();
        double ema=0,bestEma=-999;int battles=0,wins=0,losses=0;
    }

    private final Map<String,Q> q=new HashMap<>();
    private final Map<String,Policy> policies=new HashMap<>();
    private final ArrayDeque<Step> episode=new ArrayDeque<>();
    private final File file; private final Random random=new Random();
    private int wins=0,losses=0,battles=0;
    private String stage="none",team="none",scope="none|none";

    public LearningBrain(Context c){file=new File(c.getFilesDir(),"strategist_brain_v3_selfplay.json");load();}

    public synchronized void begin(String stageSig,String teamSig){
        stage=stageSig==null?"none":stageSig;team=teamSig==null?"none":teamSig;scope=stage+"|"+team;episode.clear();
        policy();
    }
    private Policy policy(){Policy p=policies.get(scope);if(p==null){p=new Policy();policies.put(scope,p);}return p;}

    /** Exploration starts substantial on a new stage/team and decays automatically. */
    public synchronized double explorationRate(){
        int n=policy().battles;
        return Math.max(.035,.34*Math.exp(-n/55.0));
    }

    public synchronized double bonus(String context,String action){return bonus(context,action,false);}
    public synchronized double bonus(String context,String action,boolean selfPlay){
        Q x=q.get(key(context,action));double mean=x==null?0:x.v;
        if(!selfPlay)return mean;
        int n=x==null?0:x.n;
        double eps=explorationRate();
        // UCB exploration is restricted to actions the semantic planner already considers legal.
        double ucb=(.10+.55*eps)*Math.sqrt(Math.log(Math.max(2,policy().battles+2))/(n+1.0));
        return mean+ucb;
    }
    public synchronized void record(String context,String action){episode.addLast(new Step(context,action));while(episode.size()>HISTORY)episode.removeFirst();}
    public synchronized StrategyProfile strategy(boolean selfPlay){return policy().current.copy();}

    /** Optional diagnostic hook; autonomous SELF mode never needs this. */
    public synchronized void feedback(double reward){updateEpisode(Math.max(-1,Math.min(1,reward))*.35);save();}

    /** Dense shaping from visible HP movement and KOs. */
    public synchronized void reinforceRecent(double reward){
        reward=Math.max(-.45,Math.min(.45,reward));
        ArrayList<Step> list=new ArrayList<>(episode); double g=1.0; int used=0;
        for(int i=list.size()-1;i>=0 && used<5;i--,used++){
            Step s=list.get(i);String k=key(s.c,s.a);Q x=q.get(k);if(x==null)x=new Q(0,0);
            x.v=x.v+.11*(reward*g-x.v);x.n++;q.put(k,x);g*=.67;
        }
    }

    public synchronized void finish(int result, boolean selfPlay){
        if(result==0)return;
        battles++;if(result>0)wins++;else losses++;
        Policy p=policy();p.battles++;if(result>0)p.wins++;else p.losses++;
        updateEpisode(result>0?1.0:-1.0);
        double r=result>0?1:-1;p.ema=.82*p.ema+.18*r;
        if(p.ema>p.bestEma){p.bestEma=p.ema;p.best=p.current.copy();}

        if(selfPlay){
            double eps=explorationRate();
            // Losses trigger a stronger policy mutation. Even winning policies occasionally
            // explore a nearby strategy while epsilon is still high, then anneal toward best.
            if(result<0){
                p.current=p.best.mutated(random).mutated(random);
            }else if(random.nextDouble()<eps){
                StrategyProfile base=random.nextBoolean()?p.current:p.best;
                p.current=base.mutated(random);
            }else{
                if(p.ema>=p.bestEma-.04)p.best=p.current.copy();
                p.current=p.best.copy();
            }
        }else p.current=p.best.copy();

        episode.clear();trim();save();
    }

    private void updateEpisode(double reward){
        ArrayList<Step> list=new ArrayList<>(episode); double g=1;
        for(int i=list.size()-1;i>=0;i--){
            Step s=list.get(i);String k=key(s.c,s.a);Q x=q.get(k);if(x==null)x=new Q(0,0);
            x.v=x.v+ALPHA*(reward*g-x.v);x.n++;q.put(k,x);g*=GAMMA;
        }
    }
    private String key(String c,String a){return stage+"|"+team+"|"+c+"|"+a;}
    private void trim(){if(q.size()<=MAX_Q)return;Iterator<String>it=q.keySet().iterator();while(q.size()>MAX_Q&&it.hasNext()){it.next();it.remove();}}
    public synchronized String stats(){Policy p=policy();return "W"+wins+" L"+losses+" / "+battles+" | stage "+p.wins+"-"+p.losses+" | eps="+String.format(java.util.Locale.US,"%.2f",explorationRate())+" | q="+q.size();}
    public synchronized int wins(){return wins;} public synchronized int losses(){return losses;}

    private JSONObject strategyJson(StrategyProfile s)throws Exception{JSONObject o=new JSONObject();o.put("heal",s.healThreshold);o.put("buddy",s.ashBuddyGaugeMin);o.put("setup",s.setupBias);o.put("field",s.fieldBias);o.put("survival",s.survivalBias);o.put("supportFirst",s.supportFirstSyncBias);o.put("sync",s.syncBias);o.put("max",s.maxEarlyBias);o.put("gauge",s.gaugeSafety);o.put("first",s.preferredFirstSync);return o;}
    private StrategyProfile strategyFrom(JSONObject o){StrategyProfile s=new StrategyProfile();if(o==null)return s;s.healThreshold=o.optDouble("heal",s.healThreshold);s.ashBuddyGaugeMin=o.optDouble("buddy",s.ashBuddyGaugeMin);s.setupBias=o.optDouble("setup",1);s.fieldBias=o.optDouble("field",1);s.survivalBias=o.optDouble("survival",1);s.supportFirstSyncBias=o.optDouble("supportFirst",1);s.syncBias=o.optDouble("sync",1);s.maxEarlyBias=o.optDouble("max",1);s.gaugeSafety=o.optDouble("gauge",1);s.preferredFirstSync=o.optInt("first",-1);return s;}

    public synchronized void save(){
        try{
            JSONObject root=new JSONObject();root.put("wins",wins);root.put("losses",losses);root.put("battles",battles);
            JSONArray pa=new JSONArray();for(Map.Entry<String,Policy>e:policies.entrySet()){Policy p=e.getValue();JSONObject o=new JSONObject();o.put("k",e.getKey());o.put("ema",p.ema);o.put("bestEma",p.bestEma);o.put("battles",p.battles);o.put("wins",p.wins);o.put("losses",p.losses);o.put("current",strategyJson(p.current));o.put("best",strategyJson(p.best));pa.put(o);}root.put("policies",pa);
            JSONArray a=new JSONArray();for(Map.Entry<String,Q>e:q.entrySet()){JSONObject o=new JSONObject();o.put("k",e.getKey());o.put("v",e.getValue().v);o.put("n",e.getValue().n);a.put(o);}root.put("q",a);
            try(FileWriter w=new FileWriter(file)){w.write(root.toString());}
        }catch(Exception ignored){}
    }
    private void load(){
        if(!file.exists())return;
        try(FileReader r=new FileReader(file)){
            StringBuilder b=new StringBuilder();char[]c=new char[8192];int n;while((n=r.read(c))>0)b.append(c,0,n);JSONObject root=new JSONObject(b.toString());
            wins=root.optInt("wins");losses=root.optInt("losses");battles=root.optInt("battles");
            JSONArray pa=root.optJSONArray("policies");if(pa!=null)for(int i=0;i<pa.length();i++){JSONObject o=pa.getJSONObject(i);Policy p=new Policy();p.ema=o.optDouble("ema");p.bestEma=o.optDouble("bestEma",-999);p.battles=o.optInt("battles");p.wins=o.optInt("wins");p.losses=o.optInt("losses");p.current=strategyFrom(o.optJSONObject("current"));p.best=strategyFrom(o.optJSONObject("best"));policies.put(o.getString("k"),p);}
            JSONArray a=root.optJSONArray("q");if(a!=null)for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);q.put(o.getString("k"),new Q(o.getDouble("v"),o.optInt("n")));}
        }catch(Exception ignored){}
    }
    public synchronized void reset(){q.clear();policies.clear();episode.clear();wins=losses=battles=0;scope="none|none";stage=team="none";if(file.exists())file.delete();}
}
