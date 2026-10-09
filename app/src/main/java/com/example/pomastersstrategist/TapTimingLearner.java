package com.example.pomastersstrategist;

import android.content.Context;
import android.content.SharedPreferences;

public final class TapTimingLearner {
    private final SharedPreferences p; private long gap;
    public TapTimingLearner(Context c){p=c.getSharedPreferences("timing_v1",Context.MODE_PRIVATE);gap=p.getLong("gap",95);}
    public synchronized long gap(){return gap;}
    public synchronized void report(boolean screenChanged){gap=screenChanged?Math.max(55,gap-2):Math.min(240,gap+9);p.edit().putLong("gap",gap).apply();}
}
