package com.example.pomastersstrategist.core;

public final class PlannedAction {
    public final int pairIndex;
    public final MoveSpec move;
    public final int targetIndex;
    public double score;
    public String reason;

    public PlannedAction(int pairIndex, MoveSpec move, int targetIndex) {
        this.pairIndex = pairIndex;
        this.move = move;
        this.targetIndex = targetIndex;
    }

    public String key() { return pairIndex + ":" + move.kind + ":" + move.id + ":" + targetIndex; }

    @Override public String toString() {
        return "P" + (pairIndex + 1) + " " + move.name + (targetIndex >= 0 ? " → " + (targetIndex + 1) : "") +
                " [" + String.format(java.util.Locale.US, "%.0f", score) + "]";
    }
}
