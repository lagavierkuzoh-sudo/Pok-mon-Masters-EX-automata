package com.example.pomastersstrategist.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Compact semantic state used by the planner. Values are intentionally battle-relevant, not a memory mirror. */
public final class BattleState {
    public double gauge = 6.0;
    public int syncCountdown = 9;
    /** Number of allied sync buffs already accumulated. */
    public int syncBuff = 0;
    public int alliedActions = 0;
    public final double[] hp = new double[]{1,1,1};
    /** Enemy HP fractions when calibrated; -1 means unknown/not visible. */
    public final double[] enemyHp = new double[]{-1,-1,-1};
    public final int[] timesHit = new int[]{0,0,0};
    public final int[] pairMoveCount = new int[]{0,0,0};
    /** Stat ranks, -6..+6. */
    public final int[] atk = new int[]{0,0,0};
    public final int[] spatk = new int[]{0,0,0};
    public final int[] def = new int[]{0,0,0};
    public final int[] spdef = new int[]{0,0,0};
    public final int[] speed = new int[]{0,0,0};
    public final int[] crit = new int[]{0,0,0};
    /** Physical/Special Moves Up Next ranks. They are consumed by a matching damaging move. */
    public final int[] physicalNext = new int[]{0,0,0};
    public final int[] specialNext = new int[]{0,0,0};
    public final int[] syncNext = new int[]{0,0,0};
    public final boolean[] terastallized = new boolean[]{false,false,false};

    public int electricTerrainSteps = 0;
    public int fightingZoneSteps = 0;
    public int gaugeAccelerationSteps = 0;
    /** Generic current/future weather/terrain/zone/circle states imported from knowledge packs. */
    public final Map<String,Integer> fieldSteps = new HashMap<>();

    public boolean enemyParalyzed = false;
    public boolean enemyFlinched = false;
    public boolean enemyTrapped = false;
    public boolean enemyConfused = false;
    public int enemySpDefDown = 0;
    public int enemyDefDown = 0;
    public int enemyAtkDown = 0;
    public int enemySpAtkDown = 0;
    public int enemySpeedDown = 0;

    public boolean maxUsed = false;
    public boolean red1996OriginalUsed = false;
    public boolean firstSyncUsed = false;
    public boolean battleOver = false;
    /** Last semantic action key; included in the learning context so sequences can be learned. */
    public String lastActionKey = "none";
    public final Map<String,Integer> usesLeft = new HashMap<>();

    public BattleState copy() {
        BattleState b = new BattleState();
        b.gauge=gauge; b.syncCountdown=syncCountdown; b.syncBuff=syncBuff; b.alliedActions=alliedActions;
        System.arraycopy(hp,0,b.hp,0,3); System.arraycopy(enemyHp,0,b.enemyHp,0,3); System.arraycopy(timesHit,0,b.timesHit,0,3); System.arraycopy(pairMoveCount,0,b.pairMoveCount,0,3);
        System.arraycopy(atk,0,b.atk,0,3); System.arraycopy(spatk,0,b.spatk,0,3); System.arraycopy(def,0,b.def,0,3);
        System.arraycopy(spdef,0,b.spdef,0,3); System.arraycopy(speed,0,b.speed,0,3); System.arraycopy(crit,0,b.crit,0,3);
        System.arraycopy(physicalNext,0,b.physicalNext,0,3); System.arraycopy(specialNext,0,b.specialNext,0,3); System.arraycopy(syncNext,0,b.syncNext,0,3);
        System.arraycopy(terastallized,0,b.terastallized,0,3);
        b.electricTerrainSteps=electricTerrainSteps; b.fightingZoneSteps=fightingZoneSteps; b.gaugeAccelerationSteps=gaugeAccelerationSteps;
        b.fieldSteps.putAll(fieldSteps);
        b.enemyParalyzed=enemyParalyzed; b.enemyFlinched=enemyFlinched;b.enemyTrapped=enemyTrapped;b.enemyConfused=enemyConfused;
        b.enemySpDefDown=enemySpDefDown; b.enemyDefDown=enemyDefDown;b.enemyAtkDown=enemyAtkDown;b.enemySpAtkDown=enemySpAtkDown;b.enemySpeedDown=enemySpeedDown;
        b.maxUsed=maxUsed; b.red1996OriginalUsed=red1996OriginalUsed; b.firstSyncUsed=firstSyncUsed; b.battleOver=battleOver; b.lastActionKey=lastActionKey;
        b.usesLeft.putAll(usesLeft);
        return b;
    }

    public String useKey(int pair, MoveSpec move) { return pair + ":" + move.id; }
    public int usesRemaining(int pair, MoveSpec move) {
        if (move.uses < 0) return Integer.MAX_VALUE;
        return usesLeft.getOrDefault(useKey(pair, move), move.uses);
    }
    public void spendUse(int pair, MoveSpec move) {
        if (move.uses < 0) return;
        usesLeft.put(useKey(pair, move), Math.max(0, usesRemaining(pair, move)-1));
    }

    public static BattleState initial(TeamBuild team, PairDefinition[] defs) {
        BattleState b = new BattleState();
        for (int i=0;i<3;i++) {
            PairDefinition d = defs[i];
            if (d == null) continue;
            for (MoveSpec m : d.moves) if (m.uses >= 0) b.usesLeft.put(b.useKey(i,m),m.uses);
            if (d.syncMove != null && d.syncMove.uses >= 0) b.usesLeft.put(b.useKey(i,d.syncMove),d.syncMove.uses);
            for (MoveSpec m : d.maxMoves) if (m.uses >= 0) b.usesLeft.put(b.useKey(i,m),m.uses);

            if (d.hasPassive("FIGHTING_ZONE_ENTRY_SYNC")) b.setField("Fighting Zone",8);
            if (d.hasPassive("ENTRY_SPATK6")) b.spatk[i] = 6;
            if (d.hasPassive("AUTO_TERA_ENTRY")) b.terastallized[i] = true;
            if (d.hasPassive("HEAD_START_1")) b.syncCountdown = Math.max(0, b.syncCountdown-1);
            if (d.hasPassive("HEAD_START_2")) b.syncCountdown = Math.max(0, b.syncCountdown-2);

            // Generic imported passive hooks.
            for(String tag:d.passiveTags){
                if(tag.startsWith("ENTRY_FIELD:"))b.setField(tag.substring("ENTRY_FIELD:".length()),8);
                if(tag.startsWith("ENTRY_ATK:"))b.atk[i]=clampRank(parseInt(tag,6));
                if(tag.startsWith("ENTRY_SPATK:"))b.spatk[i]=clampRank(parseInt(tag,6));
                if(tag.startsWith("ENTRY_DEF:"))b.def[i]=clampRank(parseInt(tag,6));
                if(tag.startsWith("ENTRY_SPDEF:"))b.spdef[i]=clampRank(parseInt(tag,6));
                if(tag.startsWith("ENTRY_SPEED:"))b.speed[i]=clampRank(parseInt(tag,6));
                if(tag.startsWith("ENTRY_CRIT:"))b.crit[i]=Math.max(0,Math.min(3,parseInt(tag,3)));
                if(tag.equals("AUTO_TERA_ENTRY"))b.terastallized[i]=true;
            }
        }
        return b;
    }

    private static int parseInt(String tag,int d){int k=tag.indexOf(':');if(k<0)return d;try{return Integer.parseInt(tag.substring(k+1));}catch(Exception e){return d;}}
    public static int clampRank(int v){return Math.max(-6,Math.min(6,v));}

    public boolean hasField(String name){
        if(name==null)return false;
        if("Electric Terrain".equalsIgnoreCase(name))return electricTerrainSteps>0;
        if("Fighting Zone".equalsIgnoreCase(name))return fightingZoneSteps>0;
        return fieldSteps.getOrDefault(name.toLowerCase(java.util.Locale.US),0)>0;
    }
    public int fieldRemaining(String name){
        if(name==null)return 0;
        if("Electric Terrain".equalsIgnoreCase(name))return electricTerrainSteps;
        if("Fighting Zone".equalsIgnoreCase(name))return fightingZoneSteps;
        return fieldSteps.getOrDefault(name.toLowerCase(java.util.Locale.US),0);
    }
    public void setField(String name,int steps){
        if(name==null||name.trim().isEmpty())return;
        if("Electric Terrain".equalsIgnoreCase(name)){electricTerrainSteps=Math.max(electricTerrainSteps,steps);return;}
        if("Fighting Zone".equalsIgnoreCase(name)){fightingZoneSteps=Math.max(fightingZoneSteps,steps);return;}
        String k=name.toLowerCase(java.util.Locale.US);fieldSteps.put(k,Math.max(fieldSteps.getOrDefault(k,0),steps));
    }

    /** Advance existing field durations by one semantic action and add estimated gauge regeneration. */
    public void tickFields(double gaugeRecovery) {
        if (electricTerrainSteps > 0) electricTerrainSteps--;
        if (fightingZoneSteps > 0) fightingZoneSteps--;
        if (gaugeAccelerationSteps > 0) gaugeAccelerationSteps--;
        Iterator<Map.Entry<String,Integer>> it=fieldSteps.entrySet().iterator();
        while(it.hasNext()){
            Map.Entry<String,Integer> e=it.next();int n=e.getValue()-1;if(n<=0)it.remove();else e.setValue(n);
        }
        gauge = Math.min(6.0, gauge + Math.max(0,gaugeRecovery));
    }
    public void tickFields(){tickFields(gaugeAccelerationSteps>0?1.0:.55);}

    public String contextBucket() {
        int g=(int)Math.floor(gauge);
        int s=syncCountdown<=0?0:(syncCountdown<=3?1:2);
        double min=Math.min(hp[0],Math.min(hp[1],hp[2]));
        int h=min<.3?0:(min<.6?1:2);
        int fh=0;for(String k:fieldSteps.keySet())fh=31*fh+k.hashCode();
        int next=0;for(int i=0;i<3;i++)next+=physicalNext[i]+specialNext[i]+syncNext[i];
        double eh=1.0; boolean ek=false; for(double v:enemyHp)if(v>=0){ek=true;eh=Math.min(eh,v);}
        int eb=!ek?3:(eh<.08?0:(eh<.30?1:(eh<.65?2:3)));
        int la=lastActionKey==null?0:(lastActionKey.hashCode()&0x3ff);
        return "g"+g+"s"+s+"h"+h+"eh"+eb+"e"+(electricTerrainSteps>0?1:0)+"f"+(fightingZoneSteps>0?1:0)+"x"+Integer.toHexString(fh)+"b"+Math.min(3,syncBuff)+"n"+Math.min(9,next)+"a"+Integer.toHexString(la);
    }
}
