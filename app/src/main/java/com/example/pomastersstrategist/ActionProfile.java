package com.example.pomastersstrategist;

import android.content.Context;
import android.content.SharedPreferences;

public final class ActionProfile {
    public static final String[] NAMES={"PAIR-1","PAIR-2","PAIR-3","MOVE-1","MOVE-2","MOVE-3","MOVE-4","TERA-BLAST","ENEMY-L","ENEMY-C","ENEMY-R","SYNC","MAX","NEXT","REPLAY","OK","BACK","X"};
    public static final int PAIR1=0,PAIR2=1,PAIR3=2,MOVE1=3,MOVE2=4,MOVE3=5,MOVE4=6,TERA_BLAST=7,ENEMY_L=8,ENEMY_C=9,ENEMY_R=10,SYNC=11,MAX=12,NEXT=13,REPLAY=14,OK=15,BACK=16,X=17;
    private final SharedPreferences p;
    public ActionProfile(Context c){p=c.getSharedPreferences("action_profile_v2",Context.MODE_PRIVATE);}
    public int size(){return NAMES.length;} public String name(int i){return NAMES[i];}
    public boolean has(int i){return p.contains("x"+i)&&p.contains("y"+i);} public float x(int i){return p.getFloat("x"+i,-1);} public float y(int i){return p.getFloat("y"+i,-1);}
    public void set(int i,float x,float y){p.edit().putFloat("x"+i,x).putFloat("y"+i,y).apply();}
    public int pairAction(int slot){return PAIR1+Math.max(0,Math.min(2,slot));}
    public int moveAction(int slot){return slot==5?TERA_BLAST:MOVE1+Math.max(0,Math.min(3,slot-1));}
    public int enemyAction(int target){return target<=0?ENEMY_L:(target==1?ENEMY_C:ENEMY_R);}
}
