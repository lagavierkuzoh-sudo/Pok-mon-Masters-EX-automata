package com.example.pomastersstrategist;

public final class BotConfig {
    private BotConfig(){}
    public static final String TARGET_PACKAGE="com.dena.a12026418";
    public static final int MODE_ASSIST=0, MODE_AUTO=1, MODE_SELFPLAY=2;

    /** v2.2 defaults to autonomous self-play after permissions/setup are complete. */
    public static volatile int mode=MODE_SELFPLAY;

    public static final long MIN_DECISION_MS=105;
    public static final float TERMINAL_DISTANCE=0.060f; // retained for AUTO/legacy labelled terminals

    // Autonomous episode handling.
    public static final long POST_TERMINAL_DELAY_MS=1800;
    public static final long RESULT_NAV_INTERVAL_MS=950;
    public static final long RESULT_TRANSITION_MS=1400;
    public static final int BAR_MISSING_FRAMES_FOR_KO=5;
    public static final int NO_BATTLE_FRAMES_FOR_RESULT=12;
}
