package com.example.pomastersstrategist.core;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public final class MoveSpec {
    public enum Kind { MOVE, SYNC, MAX }
    public enum Target { ENEMY, ALL_ENEMIES, SELF, ALLY, ALL_ALLIES, FIELD }

    public final String id;
    public final String name;
    public final Kind kind;
    public final int slot;
    public final String type;
    public final String category;
    public final int gauge;
    public final int uses;
    public final double power1;
    public final double power5;
    public final Target target;
    public final Set<String> tags;

    public MoveSpec(String id, String name, Kind kind, int slot, String type, String category,
                    int gauge, int uses, double power1, double power5, Target target,
                    Set<String> tags) {
        this.id = id;
        this.name = name;
        this.kind = kind;
        this.slot = slot;
        this.type = type == null ? "None" : type;
        this.category = category == null ? "STATUS" : category;
        this.gauge = Math.max(0, gauge);
        this.uses = uses;
        this.power1 = Math.max(0, power1);
        this.power5 = Math.max(this.power1, power5);
        this.target = target == null ? Target.ENEMY : target;
        this.tags = Collections.unmodifiableSet(new LinkedHashSet<>(tags == null ? Collections.emptySet() : tags));
    }

    public boolean has(String tag) { return tags.contains(tag); }

    public String tagValue(String prefix) {
        String p=prefix.endsWith(":")?prefix:prefix+":";
        for(String t:tags) if(t.startsWith(p)) return t.substring(p.length());
        return null;
    }

    public int tagInt(String prefix, int fallback) {
        String v=tagValue(prefix); if(v==null)return fallback;
        try{return Integer.parseInt(v);}catch(Exception e){return fallback;}
    }

    public double tagDouble(String prefix, double fallback) {
        String v=tagValue(prefix); if(v==null)return fallback;
        try{return Double.parseDouble(v);}catch(Exception e){return fallback;}
    }

    public double powerAtMoveLevel(int moveLevel) {
        int ml = Math.max(1, Math.min(5, moveLevel));
        return power1 + (power5 - power1) * ((ml - 1) / 4.0);
    }

    @Override public String toString() { return name; }
}
