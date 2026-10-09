package com.example.pomastersstrategist.core;

import java.util.Arrays;

public final class TeamBuild {
    public final PairBuild[] slots = new PairBuild[]{new PairBuild(), new PairBuild(), new PairBuild()};

    public String signature() {
        StringBuilder b = new StringBuilder();
        for (PairBuild p : slots) {
            b.append(p.pairId).append('@').append(p.level).append('/').append(p.moveLevel)
             .append('/').append(p.sixStarEx ? 'E' : '-')
             .append(p.exRoleUnlocked ? 'R' : '-')
             .append('/').append(p.superAwakening).append('/').append(p.gridStyle)
             .append("/H").append(p.exactHp).append("/A").append(p.exactAttack).append("/D").append(p.exactDefense)
             .append("/S").append(p.exactSpAttack).append("/Q").append(p.exactSpDefense).append("/V").append(p.exactSpeed)
             .append("/L").append(p.luckySkill == null ? "" : p.luckySkill).append('|');
        }
        return Integer.toHexString(b.toString().hashCode()) + ":" + b;
    }

    public double moveGaugeBarsPerSecond(PairDefinition[] defs, int[] speedRanks, boolean accelerated) {
        double[] speeds=new double[3];
        for(int i=0;i<3;i++){
            PairDefinition d=defs[i]; PairBuild p=slots[i];
            if(d==null||p==null){speeds[i]=0;continue;}
            double base=p.speed(d);
            int rank=speedRanks==null?0:Math.max(-6,Math.min(6,speedRanks[i]));
            speeds[i]=base*speedRankMultiplier(rank);
        }
        Arrays.sort(speeds); // low, middle, high
        double weighted=(speeds[0]+2.0*speeds[1]+3.0*speeds[2])/3.0;
        double bars=(750.0+weighted)/7500.0;
        if(accelerated) bars*=1.5;
        return Math.max(.08,Math.min(.95,bars));
    }

    private static double speedRankMultiplier(int r){
        switch(r){
            case -6:return .38;case -5:return .41;case -4:return .45;case -3:return .50;case -2:return .55;case -1:return .66;
            case 1:return 1.50;case 2:return 1.80;case 3:return 2.00;case 4:return 2.20;case 5:return 2.40;case 6:return 2.60;
            default:return 1.0;
        }
    }

    @Override public String toString() { return Arrays.toString(slots); }
}
