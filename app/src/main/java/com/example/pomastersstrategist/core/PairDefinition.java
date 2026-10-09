package com.example.pomastersstrategist.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class PairDefinition {
    public final String id;
    public final String trainer;
    public final String pokemon;
    public final String displayName;
    public final String role;
    public final String exRole;
    public final String type;
    public final int hp140, atk140, def140, spatk140, spdef140, speed140;
    public final int hp140Max, atk140Max, def140Max, spatk140Max, spdef140Max, speed140Max;
    /** Optional current-data anchors. Zero means the pack did not provide that Lv.200 stat set. */
    public final int hp200, atk200, def200, spatk200, spdef200, speed200;
    public final int hp200Max, atk200Max, def200Max, spatk200Max, spdef200Max, speed200Max;
    public final List<MoveSpec> moves;
    public final MoveSpec syncMove;
    public final List<MoveSpec> maxMoves;
    public final Set<String> passiveTags;
    public final Set<String> themes;
    public final String sourceNote;

    public PairDefinition(String id, String trainer, String pokemon, String role, String exRole, String type,
                          int[] stats140, int[] stats140Max, int[] stats200, int[] stats200Max,
                          List<MoveSpec> moves, MoveSpec syncMove, List<MoveSpec> maxMoves,
                          Set<String> passiveTags, Set<String> themes, String sourceNote) {
        this.id = id;
        this.trainer = trainer;
        this.pokemon = pokemon;
        this.displayName = trainer + " & " + pokemon;
        this.role = role == null ? "UNKNOWN" : role;
        this.exRole = exRole == null ? "NONE" : exRole;
        this.type = type == null ? "Unknown" : type;
        int[] b = safeStats(stats140, new int[]{600,300,200,300,200,300});
        int[] m = safeStats(stats140Max, b);
        int[] b200 = safeStats(stats200, new int[]{0,0,0,0,0,0});
        int[] m200 = safeStats(stats200Max, b200);
        hp140=b[0]; atk140=b[1]; def140=b[2]; spatk140=b[3]; spdef140=b[4]; speed140=b[5];
        hp140Max=m[0]; atk140Max=m[1]; def140Max=m[2]; spatk140Max=m[3]; spdef140Max=m[4]; speed140Max=m[5];
        hp200=b200[0]; atk200=b200[1]; def200=b200[2]; spatk200=b200[3]; spdef200=b200[4]; speed200=b200[5];
        hp200Max=m200[0]; atk200Max=m200[1]; def200Max=m200[2]; spatk200Max=m200[3]; spdef200Max=m200[4]; speed200Max=m200[5];
        this.moves = Collections.unmodifiableList(new ArrayList<>(moves == null ? Collections.emptyList() : moves));
        this.syncMove = syncMove;
        this.maxMoves = Collections.unmodifiableList(new ArrayList<>(maxMoves == null ? Collections.emptyList() : maxMoves));
        this.passiveTags = Collections.unmodifiableSet(new LinkedHashSet<>(passiveTags == null ? Collections.emptySet() : passiveTags));
        this.themes = Collections.unmodifiableSet(new LinkedHashSet<>(themes == null ? Collections.emptySet() : themes));
        this.sourceNote = sourceNote == null ? "" : sourceNote;
    }

    /** Schema-1/schema-2 constructor when only Lv.140 anchors are available. */
    public PairDefinition(String id, String trainer, String pokemon, String role, String exRole, String type,
                          int[] stats140, int[] stats140Max, List<MoveSpec> moves,
                          MoveSpec syncMove, List<MoveSpec> maxMoves, Set<String> passiveTags,
                          Set<String> themes, String sourceNote) {
        this(id,trainer,pokemon,role,exRole,type,stats140,stats140Max,null,null,moves,syncMove,maxMoves,passiveTags,themes,sourceNote);
    }

    /** Backward-compatible constructor for tests/custom code. */
    public PairDefinition(String id, String trainer, String pokemon, String role, String exRole, String type,
                          int[] stats140, int[] stats140Max, List<MoveSpec> moves,
                          MoveSpec syncMove, List<MoveSpec> maxMoves, Set<String> passiveTags,
                          String sourceNote) {
        this(id,trainer,pokemon,role,exRole,type,stats140,stats140Max,null,null,moves,syncMove,maxMoves,passiveTags,Collections.emptySet(),sourceNote);
    }

    private static int[] safeStats(int[] s,int[] fallback) {
        if (s == null || s.length < 6) return fallback.clone();
        return s.clone();
    }

    public boolean hasLv200Stats(){return hp200>0 && speed200>0;}
    public boolean hasPassive(String tag) { return passiveTags.contains(tag); }
    public boolean hasTheme(String theme){for(String x:themes)if(x.equalsIgnoreCase(theme))return true;return false;}

    public MoveSpec moveById(String id) {
        for (MoveSpec m : moves) if (m.id.equals(id)) return m;
        if (syncMove != null && syncMove.id.equals(id)) return syncMove;
        for (MoveSpec m : maxMoves) if (m.id.equals(id)) return m;
        return null;
    }
}
