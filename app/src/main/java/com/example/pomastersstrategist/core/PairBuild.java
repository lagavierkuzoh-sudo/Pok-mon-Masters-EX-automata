package com.example.pomastersstrategist.core;

/** User-owned build information. Exact stats are optional but override estimation. */
public final class PairBuild {
    public String pairId;
    public int level = 200;
    public int moveLevel = 3;
    public boolean sixStarEx = true;
    public boolean exRoleUnlocked = false;
    public int superAwakening = 0;
    public String gridStyle = "BALANCED";
    public String luckySkill = "";
    public int exactHp = 0;
    public int exactAttack = 0;
    public int exactDefense = 0;
    public int exactSpAttack = 0;
    public int exactSpDefense = 0;
    public int exactSpeed = 0;

    public PairBuild() {}
    public PairBuild(String pairId) { this.pairId = pairId; }

    public double levelScale() {
        int lv = Math.max(1, Math.min(200, level));
        if (lv <= 140) return Math.max(0.35, lv / 140.0);
        // Fallback only when a pack lacks Lv.200 anchors; exact entered stats are preferred.
        return 1.0 + (lv - 140) * 0.0050;
    }

    private int choose140(PairDefinition d, int normal, int maxed) { return sixStarEx ? maxed : normal; }
    private int choose200(PairDefinition d, int normal, int maxed) { return sixStarEx ? maxed : normal; }

    /** Interpolate 140→200 when the current-data pack includes Lv.200 anchors; otherwise use the conservative fallback scale. */
    private double estimate(int b140,int b140Max,int b200,int b200Max) {
        int lv=Math.max(1,Math.min(200,level));
        double v140=sixStarEx?b140Max:b140;
        double v200=sixStarEx?b200Max:b200;
        if(lv<=140)return v140*Math.max(.35,lv/140.0);
        if(v200>0)return v140+(v200-v140)*((lv-140)/60.0);
        return v140*levelScale();
    }

    public double offense(PairDefinition d, String category) {
        boolean physical = "PHYSICAL".equalsIgnoreCase(category);
        int exact = physical ? exactAttack : exactSpAttack;
        if (exact > 0) return exact;
        double value = physical
                ? estimate(d.atk140,d.atk140Max,d.atk200,d.atk200Max)
                : estimate(d.spatk140,d.spatk140Max,d.spatk200,d.spatk200Max);
        if (superAwakening >= 1) value *= 1.10;
        return value;
    }

    public double speed(PairDefinition d) {
        if (exactSpeed > 0) return exactSpeed;
        double value=estimate(d.speed140,d.speed140Max,d.speed200,d.speed200Max);
        if(superAwakening>=1)value*=1.10;
        return value;
    }

    public double hp(PairDefinition d) {
        if(exactHp>0)return exactHp;
        double v=estimate(d.hp140,d.hp140Max,d.hp200,d.hp200Max);
        if(superAwakening>=1)v*=1.10;
        if(d.role!=null&&d.role.startsWith("SUPPORT")){
            if(superAwakening>=2)v+=50;
            if(superAwakening>=4)v+=100;
        }
        return v;
    }
    public double defense(PairDefinition d, boolean special) {
        int exact=special?exactSpDefense:exactDefense;
        if(exact>0)return exact;
        double v=special
                ? estimate(d.spdef140,d.spdef140Max,d.spdef200,d.spdef200Max)
                : estimate(d.def140,d.def140Max,d.def200,d.def200Max);
        if(superAwakening>=1)v*=1.10;
        if(d.role!=null&&d.role.startsWith("SUPPORT")&&superAwakening>=3)v+=20;
        return v;
    }

    /** Common lucky-skill damage hook. Exotic lucky skills remain a note instead of being guessed. */
    public double luckyDamageMultiplier(boolean critical) {
        if (luckySkill == null || luckySkill.trim().isEmpty()) return 1.0;
        String x=luckySkill.toLowerCase(java.util.Locale.US);
        if (critical && x.contains("critical strike")) {
            java.util.regex.Matcher m=java.util.regex.Pattern.compile("critical\\s+strike\\s+(\\d+)").matcher(x);
            if(m.find())try{return 1.0 + 0.10*Math.max(0,Math.min(9,Integer.parseInt(m.group(1))));}catch(Exception ignored){}
        }
        return 1.0;
    }

    public double gridMoveMultiplier() {
        if ("DAMAGE".equals(gridStyle)) return 1.08;
        if ("GAUGE".equals(gridStyle)) return 1.025;
        return 1.0;
    }

    public double gridSyncMultiplier() {
        if ("SYNC".equals(gridStyle)) return 1.12;
        if ("DAMAGE".equals(gridStyle)) return 1.04;
        return 1.0;
    }
}
