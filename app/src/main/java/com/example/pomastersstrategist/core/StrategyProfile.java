package com.example.pomastersstrategist.core;

import java.util.Random;

/** High-level policy knobs evolved per stage/team by LearningBrain. */
public final class StrategyProfile {
    public double healThreshold = 0.40;
    public double ashBuddyGaugeMin = 5.0;
    public double setupBias = 1.0;
    public double fieldBias = 1.0;
    public double survivalBias = 1.0;
    public double supportFirstSyncBias = 1.0;
    public double syncBias = 1.0;
    public double maxEarlyBias = 1.0;
    public double gaugeSafety = 1.0;
    public int preferredFirstSync = -1;

    public StrategyProfile copy() {
        StrategyProfile s = new StrategyProfile();
        s.healThreshold=healThreshold; s.ashBuddyGaugeMin=ashBuddyGaugeMin; s.setupBias=setupBias;
        s.fieldBias=fieldBias;s.survivalBias=survivalBias;s.supportFirstSyncBias=supportFirstSyncBias;
        s.syncBias=syncBias; s.maxEarlyBias=maxEarlyBias; s.gaugeSafety=gaugeSafety; s.preferredFirstSync=preferredFirstSync;
        return s;
    }

    public StrategyProfile mutated(Random r) {
        StrategyProfile s = copy();
        s.healThreshold = clamp(s.healThreshold + (r.nextDouble()-0.5)*0.12, .20, .70);
        s.ashBuddyGaugeMin = clamp(s.ashBuddyGaugeMin + (r.nextDouble()-0.5)*1.4, 2.5, 6.0);
        s.setupBias = clamp(s.setupBias * (0.90 + r.nextDouble()*0.20), .65, 1.55);
        s.fieldBias = clamp(s.fieldBias * (0.88 + r.nextDouble()*0.24), .60, 1.70);
        s.survivalBias = clamp(s.survivalBias * (0.88 + r.nextDouble()*0.24), .55, 1.80);
        s.supportFirstSyncBias = clamp(s.supportFirstSyncBias * (0.88 + r.nextDouble()*0.24), .55, 1.80);
        s.syncBias = clamp(s.syncBias * (0.90 + r.nextDouble()*0.20), .70, 1.55);
        s.maxEarlyBias = clamp(s.maxEarlyBias * (0.85 + r.nextDouble()*0.30), .50, 1.70);
        s.gaugeSafety = clamp(s.gaugeSafety * (0.90 + r.nextDouble()*0.20), .65, 1.55);
        if (r.nextDouble() < .20) s.preferredFirstSync = r.nextInt(4) - 1;
        return s;
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
}
