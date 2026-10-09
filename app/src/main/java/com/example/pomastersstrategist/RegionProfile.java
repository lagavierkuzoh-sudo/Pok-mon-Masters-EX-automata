package com.example.pomastersstrategist;

import android.content.Context;
import android.content.SharedPreferences;

public final class RegionProfile {
    public static final String[] NAMES={"GAUGE","HP-1","HP-2","HP-3","ENEMY-HP-L","ENEMY-HP-C","ENEMY-HP-R"};
    private final SharedPreferences p;
    public RegionProfile(Context c){p=c.getSharedPreferences("battle_regions",Context.MODE_PRIVATE);}
    public boolean has(int i){return p.contains("l"+i)&&p.contains("t"+i)&&p.contains("r"+i)&&p.contains("b"+i);}
    public void set(int i,float x1,float y1,float x2,float y2){
        float l=Math.min(x1,x2),r=Math.max(x1,x2),t=Math.min(y1,y2),b=Math.max(y1,y2);
        p.edit().putFloat("l"+i,l).putFloat("t"+i,t).putFloat("r"+i,r).putFloat("b"+i,b).apply();
    }
    public float[] get(int i){return new float[]{p.getFloat("l"+i,0),p.getFloat("t"+i,0),p.getFloat("r"+i,0),p.getFloat("b"+i,0)};}
}
