package com.example.pomastersstrategist.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Semantic expert planner + bounded lookahead. The learner only biases choices
 * that are already legal and strategically plausible, so the bot starts with
 * game knowledge instead of learning basic mechanics by random failure.
 */
public final class BattlePlanner {
    public interface BonusProvider { double bonus(String context, String actionKey); }
    private final int depth;
    private final int beamWidth;

    /** Seven semantic actions lets Sprint first-sync plans see the accelerated second sync while keeping the beam bounded on phone. */
    public BattlePlanner() { this(7, 36); }
    public BattlePlanner(int depth, int beamWidth) {
        this.depth=Math.max(1,depth); this.beamWidth=Math.max(4,beamWidth);
    }

    public PlannedAction plan(BattleState state, TeamBuild team, PairDefinition[] defs,
                              StrategyProfile strategy, BonusProvider bonusProvider) {
        List<Node> frontier = new ArrayList<>();
        frontier.add(new Node(state.copy(), null, 0));
        PlannedAction bestFirst = null;
        double bestScore = -1e30;
        String rootContext = state.contextBucket();

        for (int d=0; d<depth; d++) {
            List<Node> next = new ArrayList<>();
            for (Node n : frontier) {
                List<PlannedAction> legal = legalActions(n.state, team, defs, strategy);
                for (PlannedAction a : legal) {
                    BattleState sim=n.state.copy();
                    double immediate = scoreAction(sim, team, defs, strategy, a);
                    if (d==0 && bonusProvider != null)
                        immediate += bonusProvider.bonus(rootContext, a.key()) * 175.0;
                    apply(sim, team, defs, a);
                    double total = n.score + immediate * Math.pow(.80, d);
                    PlannedAction first = n.first == null ? a : n.first;
                    next.add(new Node(sim, first, total));
                    if (total > bestScore) { bestScore=total; bestFirst=first; }
                }
            }
            next.sort(Comparator.comparingDouble((Node x)->x.score).reversed());
            if (next.size()>beamWidth) next = new ArrayList<>(next.subList(0,beamWidth));
            if (next.isEmpty()) break;
            frontier=next;
        }
        if (bestFirst != null) {
            bestFirst.score = bestScore;
            bestFirst.reason = reason(bestFirst, state, team, defs);
        }
        return bestFirst;
    }

    public List<PlannedAction> rankedImmediate(BattleState state, TeamBuild team, PairDefinition[] defs,
                                                StrategyProfile strategy, BonusProvider bonusProvider) {
        List<PlannedAction> a=legalActions(state,team,defs,strategy);
        String c=state.contextBucket();
        for (PlannedAction p:a) {
            p.score=scoreAction(state.copy(),team,defs,strategy,p)+(bonusProvider==null?0:bonusProvider.bonus(c,p.key())*175);
            p.reason=reason(p,state,team,defs);
        }
        a.sort(Comparator.comparingDouble((PlannedAction x)->x.score).reversed());
        return a;
    }

    private static final class Node {
        BattleState state; PlannedAction first; double score;
        Node(BattleState s, PlannedAction f, double sc){state=s;first=f;score=sc;}
    }

    public List<PlannedAction> legalActions(BattleState s, TeamBuild team, PairDefinition[] defs, StrategyProfile strategy) {
        if (s.battleOver) return Collections.emptyList();
        List<PlannedAction> out=new ArrayList<>();
        for(int i=0;i<3;i++) {
            PairDefinition d=defs[i]; PairBuild b=team.slots[i];
            if(d==null || b==null || s.hp[i]<=.01) continue;
            for(MoveSpec m:d.moves) if(isLegal(s,i,d,m)) addTargetVariants(out,s,i,m);
            if(d.syncMove!=null && s.syncCountdown<=0 && isLegal(s,i,d,d.syncMove)) addTargetVariants(out,s,i,d.syncMove);
            if(!s.maxUsed) for(MoveSpec m:d.maxMoves) if(isLegal(s,i,d,m)) addTargetVariants(out,s,i,m);
        }
        return out;
    }


    private void addTargetVariants(List<PlannedAction> out,BattleState s,int user,MoveSpec m){
        if(m.target==MoveSpec.Target.ENEMY){
            boolean known=false;
            for(double hp:s.enemyHp)if(hp>=0){known=true;break;}
            if(known){
                for(int t=0;t<3;t++)if(s.enemyHp[t]<0 || s.enemyHp[t]>.015)out.add(new PlannedAction(user,m,t));
                return;
            }
        }
        out.add(new PlannedAction(user,m,defaultTarget(s,m,user)));
    }

    private int defaultTarget(BattleState s, MoveSpec m, int user) {
        if(m.target==MoveSpec.Target.ALLY) {
            int best=0; for(int i=1;i<3;i++) if(s.hp[i]<s.hp[best]) best=i; return best;
        }
        if(m.target==MoveSpec.Target.ENEMY) return 1; // center by default; executor may use calibrated explicit targeting
        return -1;
    }

    private boolean isLegal(BattleState s,int pair,PairDefinition d,MoveSpec m) {
        if(s.usesRemaining(pair,m)<=0) return false;
        if(m.kind==MoveSpec.Kind.MOVE && s.gauge + .05 < m.gauge) return false;
        if(m.kind==MoveSpec.Kind.SYNC && s.syncCountdown>0) return false;
        if(m.kind==MoveSpec.Kind.MAX && s.maxUsed) return false;

        String req=m.tagValue("REQUIRES_FIELD");
        if(req!=null && !s.hasField(req)) return false;
        if(m.has("REQUIRES_ELECTRIC_TERRAIN") && !s.hasField("Electric Terrain")) return false;
        if(m.has("ASH_BUDDY") && s.pairMoveCount[pair] < 3) return false;
        if(m.has("CYNTHIA_SUPERIOR") && s.timesHit[pair] < 2) return false;
        if(m.has("RED1996_REQUIRES_SPATK") && s.spatk[pair]<=0) return false;
        if(m.has("REQUIRES_TERA") && !s.terastallized[pair]) return false;
        if(m.has("RED1996_ORIGINAL") && s.red1996OriginalUsed) return false;
        if(m.has("RED1996_GLORIOUS") && !s.red1996OriginalUsed) return false;
        int reqSync=m.tagInt("REQUIRES_SYNC_BUFF",0); if(reqSync>0 && s.syncBuff<reqSync)return false;
        if(m.has("REQUIRES_SPATK_POS")&&s.spatk[pair]<=0)return false;
        if(m.has("REQUIRES_ATK_POS")&&s.atk[pair]<=0)return false;
        String count=m.tagValue("REQUIRES_MOVE_COUNT");
        if(count!=null)try{if(s.pairMoveCount[pair]<Integer.parseInt(count))return false;}catch(Exception ignored){}
        String hits=m.tagValue("REQUIRES_HITS");
        if(hits!=null)try{if(s.timesHit[pair]<Integer.parseInt(hits))return false;}catch(Exception ignored){}
        return true;
    }

    private double scoreAction(BattleState s,TeamBuild team,PairDefinition[] defs,StrategyProfile st,PlannedAction a) {
        PairDefinition d=defs[a.pairIndex]; PairBuild b=team.slots[a.pairIndex]; MoveSpec m=a.move;
        double score=0;
        double power=m.powerAtMoveLevel(b.moveLevel);
        if(m.has("ASH_BUDDY")) power *= (0.45 + 0.55*Math.max(0,Math.min(1,s.gauge/6.0)));
        double damageScore=0;

        if(power>0) {
            double offense=b.offense(d,m.category);
            double statBuff="PHYSICAL".equalsIgnoreCase(m.category) ? s.atk[a.pairIndex] : s.spatk[a.pairIndex];
            double buffMul=statRankDamageMultiplier((int)statBuff);
            boolean guaranteedCrit=(d.hasPassive("CRIT_ALWAYS")||m.has("SYNC_ALWAYS_CRIT")||d.hasPassive("CLUTCH_CRIT")||s.crit[a.pairIndex]>=3);
            double critMul=(guaranteedCrit?1.35:1.0) * b.luckyDamageMultiplier(guaranteedCrit);
            double fieldMul=teamMasterMultiplier(defs,d,m.category) * fieldMultiplier(s,d,m);
            double defenseMul=1.0;
            if("SPECIAL".equalsIgnoreCase(m.category)) defenseMul*=1.0+s.enemySpDefDown*.055;
            if("PHYSICAL".equalsIgnoreCase(m.category)) defenseMul*=1.0+s.enemyDefDown*.055;
            double grid=m.kind==MoveSpec.Kind.SYNC?b.gridSyncMultiplier():b.gridMoveMultiplier();
            double sa=superAwakeningMultiplier(d,b,m);
            double next=nextMoveMultiplier(s,a.pairIndex,m);
            double syncBuffMul=1.0 + .50*Math.max(0,s.syncBuff);
            double tera=1.0;
            if(s.terastallized[a.pairIndex] && d.type.equalsIgnoreCase(m.type) && (m.has("TERA_SAME_TYPE_POWER")||m.has("TERA_BLAST"))) tera=1.20;

            damageScore = power * Math.max(.42,offense/360.0)*buffMul*critMul*fieldMul*defenseMul*grid*sa*next*syncBuffMul*tera;
            if(m.target==MoveSpec.Target.ALL_ENEMIES) damageScore*=1.30;
            else if(m.target==MoveSpec.Target.ENEMY && a.targetIndex>=0) damageScore*=targetDamagePriority(s,a.targetIndex);
            if(m.kind==MoveSpec.Kind.SYNC) {
                damageScore*=st.syncBias;
                if(b.sixStarEx && roleIs(d,"STRIKE")) damageScore*=1.28; // AoE strategic value
                if(b.sixStarEx && roleIs(d,"TECH")) damageScore*=1.5;
                if(b.sixStarEx && m.has("SYNC_EX_AOE")) damageScore*=1.28;
                // Some hand-authored packs explicitly encode Tech effect even when role name is nonstandard.
                if(b.sixStarEx && m.has("SYNC_EX_POWER_1_5") && !roleIs(d,"TECH")) damageScore*=1.5;
            }
            if(m.kind==MoveSpec.Kind.MAX) damageScore*=1.0 + .12*st.maxEarlyBias;
            score += damageScore;
        }

        if(m.target==MoveSpec.Target.ENEMY && a.targetIndex>=0 && a.targetIndex<3){
            double eh=s.enemyHp[a.targetIndex];
            if(eh>=0 && eh<.20) score += (0.20-eh)*520.0; // prefer converting damage into an actual KO
            if(a.targetIndex==1) score += 10; // center is usually the boss, only a mild prior; learning can override it
        }

        // Resource pressure. Exact entered Speed feeds the lookahead's regeneration model, so this penalty can be modest.
        score -= m.gauge * 25.0 * st.gaugeSafety;
        if(s.gauge-m.gauge<.75 && power<250) score-=50*st.gaugeSafety;

        // Fields: evaluate how many teammates/moves can exploit the field instead of using a flat constant.
        if(m.has("SET_ELECTRIC_TERRAIN")) score += fieldSetupValue("Electric Terrain",s,team,defs)*st.fieldBias;
        if(m.has("SET_FIGHTING_ZONE")) score += fieldSetupValue("Fighting Zone",s,team,defs)*st.fieldBias;
        String genericField=m.tagValue("SET_FIELD");
        if(genericField!=null) score += fieldSetupValue(genericField,s,team,defs)*st.fieldBias;

        // Legacy high-detail tags.
        if(m.has("BUFF_SPATK_6") && s.spatk[a.pairIndex]<6) score+=190*st.setupBias;
        if(m.has("BUFF_SPATK_4_6") && s.spatk[a.pairIndex]<6) score+=215*st.setupBias;
        if(m.has("BUFF_ATK_6") && s.atk[a.pairIndex]<6) score+=165*st.setupBias;
        if(m.has("BUFF_CRIT_3") && s.crit[a.pairIndex]<3) score+=130*st.setupBias;
        if(m.has("BUFF_SPEED_IF_HP50") && s.hp[a.pairIndex]>=.5 && s.speed[a.pairIndex]<2) score+=75*st.setupBias;
        if(m.has("TEAM_DEF_SPDEF_4_6") && teamNeedsDefense(s,team,defs)) score+=215*st.setupBias*st.survivalBias;
        if(m.has("GAUGE_PLUS_6")) score+=Math.max(0,6-s.gauge)*38;
        if(m.has("MOVE_GAUGE_ACCEL")) score+=gaugeAccelValue(team,defs,s);
        if(m.has("TEAM_NEXT_2")) score+=205;
        if(m.has("PARALYZE_ALL")&&!s.enemyParalyzed) score+=165;
        if(m.has("PARALYZE")&&!s.enemyParalyzed) score+=65;
        if(m.has("FLINCH")&&!s.enemyFlinched) score+=75;
        if(m.has("FLINCH_30")&&!s.enemyFlinched) score+=28;
        if(m.has("TRAP")&&!s.enemyTrapped) score+=55;
        if(m.has("CONFUSE")&&!s.enemyConfused) score+=45;
        if(m.has("DEBUFF_SPDEF_6")&&s.enemySpDefDown<6) score+=230;
        if(m.has("SYNC_CD_MINUS_3")&&s.syncCountdown>0) score+=165;
        if(m.has("MAX_CD3")&&s.syncCountdown>0) score+=155;
        if(m.has("SET_CIRCLES_3")) score+=190*st.setupBias;

        // Generic semantic tags emitted by the data compiler / custom packs.
        score += genericSetupScore(s,team,defs,st,a);

        // Healing and survival.
        if(m.has("HEAL_ALLY_40")) {
            double hp=s.hp[Math.max(0,a.targetIndex)];
            if(hp<st.healThreshold) score+=(420+(st.healThreshold-hp)*520)*st.survivalBias;
            else score-=270;
        }
        if(m.has("HEAL_SELF_20")) {
            double hp=s.hp[a.pairIndex]; score += hp<.72 ? 145*st.survivalBias : -105;
        }
        if(m.has("FULL_HEAL_SELF")) {
            double hp=s.hp[a.pairIndex]; score += hp<.58 ? (540 + (1-hp)*280)*st.survivalBias : -370;
        }

        // Pair-specific sequencing that generic tags cannot fully express.
        if(m.has("ASH_BUDDY")) {
            score += s.gauge>=st.ashBuddyGaugeMin ? 285 : -245;
            score += s.hasField("Electric Terrain") ? 145 : 0;
        }
        if(m.has("RED1996_ORIGINAL")) score += 380;
        if(m.has("RED1996_GLORIOUS")) score += s.hasField("Electric Terrain") ? 360 : 200;
        if(m.has("CYNTHIA_SUPERIOR")) score += 280;
        if(m.has("TERA_BLAST")) score += 90;

        if(m.kind==MoveSpec.Kind.SYNC) {
            // A ready sync is not just damage: it permanently raises team damage. First-sync roles can be decisive.
            score += 300*st.syncBias;
            if(!s.firstSyncUsed) {
                int extra=firstSyncExtraBuffs(d,b);
                int cd=firstSyncCountdownReduction(d,b);
                score += extra * 800.0 * st.supportFirstSyncBias;
                score += cd * 75.0;
                String ff=firstSyncField(d,b,m);
                if(ff!=null) score += .80*fieldSetupValue(ff,s,team,defs)*st.fieldBias;
                if(d.hasPassive("RED1996_FIRST_SYNC")) {
                    score += 800.0*st.supportFirstSyncBias + 3*75.0;
                    score += .80*fieldSetupValue("Electric Terrain",s,team,defs)*st.fieldBias;
                }
                if(st.preferredFirstSync==a.pairIndex) score+=220;
            }
        }

        // Once a sync is ready, delaying it costs real-time tempo and enemy turns. Allow exceptional moves to override, but strongly prefer syncing now.
        if(s.syncCountdown<=0 && m.kind!=MoveSpec.Kind.SYNC) score -= 700.0;

        // Cheap actions are valuable for countdown cycling, but not enough to override meaningful setup/damage.
        if(m.kind==MoveSpec.Kind.MOVE && m.gauge<=1 && s.syncCountdown>0) score += 28*(10-s.syncCountdown);
        return score;
    }

    private double genericSetupScore(BattleState s,TeamBuild team,PairDefinition[] defs,StrategyProfile st,PlannedAction a){
        MoveSpec m=a.move; int i=a.pairIndex; double z=0;
        z += buffScore(m,"BUFF_SELF_ATK",s.atk[i],150,st.setupBias);
        z += buffScore(m,"BUFF_SELF_SPATK",s.spatk[i],170,st.setupBias);
        z += buffScore(m,"BUFF_SELF_CRIT",s.crit[i],120,st.setupBias);
        z += buffScore(m,"BUFF_SELF_SPEED",s.speed[i],90,st.setupBias);
        z += teamBuffScore(m,"BUFF_TEAM_ATK",s.atk,165,st.setupBias);
        z += teamBuffScore(m,"BUFF_TEAM_SPATK",s.spatk,180,st.setupBias);
        z += teamBuffScore(m,"BUFF_TEAM_DEF",s.def,165,st.setupBias*st.survivalBias);
        z += teamBuffScore(m,"BUFF_TEAM_SPDEF",s.spdef,165,st.setupBias*st.survivalBias);
        z += teamBuffScore(m,"BUFF_TEAM_SPEED",s.speed,110,st.setupBias);
        z += debuffScore(m,"DEBUFF_ENEMY_DEF",s.enemyDefDown,145);
        z += debuffScore(m,"DEBUFF_ENEMY_SPDEF",s.enemySpDefDown,155);
        z += debuffScore(m,"DEBUFF_ENEMY_ATK",s.enemyAtkDown,90*st.survivalBias);
        z += debuffScore(m,"DEBUFF_ENEMY_SPATK",s.enemySpAtkDown,90*st.survivalBias);
        z += debuffScore(m,"DEBUFF_ENEMY_SPEED",s.enemySpeedDown,65);
        int gp=m.tagInt("GAUGE_PLUS",0); if(gp>0)z+=Math.min(gp,Math.max(0,6-s.gauge))*38;
        int cd=m.tagInt("SYNC_CD_MINUS",0); if(cd>0&&s.syncCountdown>0)z+=cd*58;
        int pn=m.tagInt("TEAM_PHYSICAL_NEXT",0); if(pn>0)z+=pn*90;
        int sn=m.tagInt("TEAM_SPECIAL_NEXT",0); if(sn>0)z+=sn*90;
        int yn=m.tagInt("TEAM_SYNC_NEXT",0); if(yn>0)z+=yn*105;
        double healSelf=m.tagDouble("HEAL_SELF",0);if(healSelf>0){double hp=s.hp[i];z+=hp<st.healThreshold?healSelf*900*st.survivalBias:-120;}
        double healAlly=m.tagDouble("HEAL_ALLY",0);if(healAlly>0&&a.targetIndex>=0){double hp=s.hp[a.targetIndex];z+=hp<st.healThreshold?healAlly*1050*st.survivalBias:-140;}
        if(m.has("PARALYZE")&&!s.enemyParalyzed)z+=65;
        if(m.has("FLINCH")&&!s.enemyFlinched)z+=75;
        if(m.has("TRAP")&&!s.enemyTrapped)z+=55;
        if(m.has("CONFUSE")&&!s.enemyConfused)z+=45;
        return z;
    }

    private static double buffScore(MoveSpec m,String key,int current,double unit,double bias){
        int n=m.tagInt(key,0);if(n<=0)return 0;int max=key.contains("CRIT")?3:6;return Math.max(0,Math.min(max,current+n)-current)*(unit/Math.max(1,n))*bias;
    }
    private static double teamBuffScore(MoveSpec m,String key,int[] current,double unit,double bias){
        int n=m.tagInt(key,0);if(n<=0)return 0;double need=0;for(int x:current)need+=Math.max(0,Math.min(6,x+n)-x)/(double)n;return unit*(need/3.0)*bias;
    }
    private static double debuffScore(MoveSpec m,String key,int current,double unit){int n=m.tagInt(key,0);if(n<=0)return 0;return Math.max(0,Math.min(6,current+n)-current)*(unit/Math.max(1,n));}

    public void apply(BattleState s, TeamBuild team, PairDefinition[] defs, PlannedAction a) {
        PairDefinition d=defs[a.pairIndex]; PairBuild b=team.slots[a.pairIndex]; MoveSpec m=a.move;

        // Spend/cycle first, then let time pass during the action animation. New effects are applied after that tick.
        if(m.kind==MoveSpec.Kind.MOVE) {
            s.gauge=Math.max(0,s.gauge-m.gauge);
            s.syncCountdown=Math.max(0,s.syncCountdown-1);
            s.pairMoveCount[a.pairIndex]++;
            s.alliedActions++;
        } else if(m.kind==MoveSpec.Kind.SYNC) {
            s.syncBuff++;
            s.syncCountdown=9;
            if(!s.firstSyncUsed) {
                s.syncBuff += firstSyncExtraBuffs(d,b);
                s.syncCountdown=Math.max(0,s.syncCountdown-firstSyncCountdownReduction(d,b));
            }
        } else if(m.kind==MoveSpec.Kind.MAX) {
            s.maxUsed=true;
            if(d.hasPassive("MAX_CD3")||m.has("MAX_CD3")) s.syncCountdown=Math.max(0,s.syncCountdown-3);
        }

        double barsPerSec=team.moveGaugeBarsPerSecond(defs,s.speed,s.gaugeAccelerationSteps>0);
        // A semantic action includes UI queueing + its battle animation. 2.35 s tracks practical gauge recovery better than a fixed +0.55 bar.
        s.tickFields(Math.min(1.45,barsPerSec*2.35));
        s.spendUse(a.pairIndex,m);

        if(m.kind==MoveSpec.Kind.SYNC) {
            if(!s.firstSyncUsed) {
                String field=firstSyncField(d,b,m); if(field!=null)s.setField(field,10);
            }
            if(d.hasPassive("FIGHTING_ZONE_ENTRY_SYNC")) s.setField("Fighting Zone",8);
            if(d.hasPassive("RED1996_FIRST_SYNC")) { s.syncCountdown=Math.max(0,s.syncCountdown-3); s.setField("Electric Terrain",8); s.syncBuff++; }
            if(d.hasPassive("AUTO_TERA_FIRST_SYNC")) s.terastallized[a.pairIndex]=true;
            s.firstSyncUsed=true;
        }

        // Legacy high-detail effects.
        if(m.has("SET_ELECTRIC_TERRAIN")) { s.setField("Electric Terrain",d.hasPassive("RED_ET_ACCEL_HEAL")?12:8); if(d.hasPassive("RED_ET_ACCEL_HEAL")) s.gaugeAccelerationSteps=Math.max(s.gaugeAccelerationSteps,10); }
        if(m.has("SET_FIGHTING_ZONE")) s.setField("Fighting Zone",8);
        if(m.has("BUFF_SPATK_6")) s.spatk[a.pairIndex]=6;
        if(m.has("BUFF_SPATK_4_6")) s.spatk[a.pairIndex]=s.hp[a.pairIndex]>=.5?6:BattleState.clampRank(s.spatk[a.pairIndex]+4);
        if(m.has("BUFF_ATK_6")) s.atk[a.pairIndex]=6;
        if(m.has("BUFF_CRIT_3")) s.crit[a.pairIndex]=3;
        if(m.has("BUFF_SPEED_IF_HP50")&&s.hp[a.pairIndex]>=.5)s.speed[a.pairIndex]=Math.max(s.speed[a.pairIndex],2);
        if(m.has("TEAM_DEF_SPDEF_4_6")) {
            int amount=s.hasField("Fighting Zone")?6:4; for(int i=0;i<3;i++){s.def[i]=Math.max(s.def[i],amount);s.spdef[i]=Math.max(s.spdef[i],amount);}
        }
        if(m.has("GAUGE_PLUS_6")) s.gauge=6;
        if(m.has("ASH_BUDDY")) s.gauge=0;
        if(m.has("MOVE_GAUGE_ACCEL")) s.gaugeAccelerationSteps=Math.max(s.gaugeAccelerationSteps,8);
        if(m.has("TEAM_NEXT_2"))for(int i=0;i<3;i++){s.physicalNext[i]=Math.min(10,s.physicalNext[i]+2);s.specialNext[i]=Math.min(10,s.specialNext[i]+2);}
        if(m.has("PARALYZE")||m.has("PARALYZE_ALL")) s.enemyParalyzed=true;
        if(m.has("FLINCH"))s.enemyFlinched=true;
        if(m.has("TRAP"))s.enemyTrapped=true;
        if(m.has("CONFUSE"))s.enemyConfused=true;
        if(m.has("DEBUFF_SPDEF_6")) s.enemySpDefDown=6;
        if(m.has("DEBUFF_DEF_SPDEF_PARALYZED")&&s.enemyParalyzed){s.enemyDefDown=Math.min(6,s.enemyDefDown+1);s.enemySpDefDown=Math.min(6,s.enemySpDefDown+1);}
        if(m.has("SYNC_CD_MINUS_3")) s.syncCountdown=Math.max(0,s.syncCountdown-3);
        if(m.has("HEAL_ALLY_40")&&a.targetIndex>=0) s.hp[a.targetIndex]=Math.min(1,s.hp[a.targetIndex]+.40);
        if(m.has("HEAL_SELF_20")) s.hp[a.pairIndex]=Math.min(1,s.hp[a.pairIndex]+.20);
        if(m.has("FULL_HEAL_SELF")) s.hp[a.pairIndex]=1.0;
        if(m.has("RED1996_ORIGINAL")) s.red1996OriginalUsed=true;

        applyGenericTags(s,a);

        // Consume matching Next effects only after their multiplier was available to this action.
        if(m.power1>0 || m.power5>0) {
            if(m.kind==MoveSpec.Kind.SYNC)s.syncNext[a.pairIndex]=0;
            else if("PHYSICAL".equalsIgnoreCase(m.category))s.physicalNext[a.pairIndex]=0;
            else if("SPECIAL".equalsIgnoreCase(m.category))s.specialNext[a.pairIndex]=0;
        }
        s.lastActionKey=a.key();
    }

    private void applyGenericTags(BattleState s,PlannedAction a){
        MoveSpec m=a.move;int i=a.pairIndex;
        String f=m.tagValue("SET_FIELD");if(f!=null)s.setField(f,m.tagInt("FIELD_STEPS",8));
        addRank(s.atk,i,m.tagInt("BUFF_SELF_ATK",0));addRank(s.spatk,i,m.tagInt("BUFF_SELF_SPATK",0));
        addRank(s.def,i,m.tagInt("BUFF_SELF_DEF",0));addRank(s.spdef,i,m.tagInt("BUFF_SELF_SPDEF",0));addRank(s.speed,i,m.tagInt("BUFF_SELF_SPEED",0));
        int cr=m.tagInt("BUFF_SELF_CRIT",0);if(cr>0)s.crit[i]=Math.max(0,Math.min(3,s.crit[i]+cr));
        addTeamRank(s.atk,m.tagInt("BUFF_TEAM_ATK",0));addTeamRank(s.spatk,m.tagInt("BUFF_TEAM_SPATK",0));
        addTeamRank(s.def,m.tagInt("BUFF_TEAM_DEF",0));addTeamRank(s.spdef,m.tagInt("BUFF_TEAM_SPDEF",0));addTeamRank(s.speed,m.tagInt("BUFF_TEAM_SPEED",0));
        int tc=m.tagInt("BUFF_TEAM_CRIT",0);if(tc>0)for(int j=0;j<3;j++)s.crit[j]=Math.max(0,Math.min(3,s.crit[j]+tc));
        s.enemyDefDown=Math.min(6,s.enemyDefDown+m.tagInt("DEBUFF_ENEMY_DEF",0));
        s.enemySpDefDown=Math.min(6,s.enemySpDefDown+m.tagInt("DEBUFF_ENEMY_SPDEF",0));
        s.enemyAtkDown=Math.min(6,s.enemyAtkDown+m.tagInt("DEBUFF_ENEMY_ATK",0));
        s.enemySpAtkDown=Math.min(6,s.enemySpAtkDown+m.tagInt("DEBUFF_ENEMY_SPATK",0));
        s.enemySpeedDown=Math.min(6,s.enemySpeedDown+m.tagInt("DEBUFF_ENEMY_SPEED",0));
        int gp=m.tagInt("GAUGE_PLUS",0);if(gp>0)s.gauge=Math.min(6,s.gauge+gp);
        int cd=m.tagInt("SYNC_CD_MINUS",0);if(cd>0)s.syncCountdown=Math.max(0,s.syncCountdown-cd);
        int pn=m.tagInt("TEAM_PHYSICAL_NEXT",0);if(pn>0)for(int j=0;j<3;j++)s.physicalNext[j]=Math.min(10,s.physicalNext[j]+pn);
        int sn=m.tagInt("TEAM_SPECIAL_NEXT",0);if(sn>0)for(int j=0;j<3;j++)s.specialNext[j]=Math.min(10,s.specialNext[j]+sn);
        int yn=m.tagInt("TEAM_SYNC_NEXT",0);if(yn>0)for(int j=0;j<3;j++)s.syncNext[j]=Math.min(10,s.syncNext[j]+yn);
        double hs=m.tagDouble("HEAL_SELF",0);if(hs>0)s.hp[i]=Math.min(1,s.hp[i]+hs);
        double ha=m.tagDouble("HEAL_ALLY",0);if(ha>0&&a.targetIndex>=0)s.hp[a.targetIndex]=Math.min(1,s.hp[a.targetIndex]+ha);
        if(m.has("PARALYZE"))s.enemyParalyzed=true;if(m.has("FLINCH"))s.enemyFlinched=true;if(m.has("TRAP"))s.enemyTrapped=true;if(m.has("CONFUSE"))s.enemyConfused=true;
        if(m.has("TERASTALLIZE"))s.terastallized[i]=true;
    }

    private static void addRank(int[] a,int i,int n){if(n!=0)a[i]=BattleState.clampRank(a[i]+n);}
    private static void addTeamRank(int[] a,int n){if(n!=0)for(int i=0;i<3;i++)a[i]=BattleState.clampRank(a[i]+n);}

    private double nextMoveMultiplier(BattleState s,int pair,MoveSpec m){
        if(m.kind==MoveSpec.Kind.SYNC)return 1.0+.30*Math.max(0,s.syncNext[pair]);
        if("PHYSICAL".equalsIgnoreCase(m.category))return 1.0+.30*Math.max(0,s.physicalNext[pair]);
        if("SPECIAL".equalsIgnoreCase(m.category))return 1.0+.30*Math.max(0,s.specialNext[pair]);
        return 1.0;
    }

    private static double statRankDamageMultiplier(int r){
        r=Math.max(-6,Math.min(6,r));
        if(r>=0)return (2.0+r)/2.0;
        return 2.0/(2.0-r);
    }

    private double superAwakeningMultiplier(PairDefinition d,PairBuild b,MoveSpec m){
        double x=1.0;
        if((roleIs(d,"STRIKE")||roleIs(d,"SPRINT")) && b.superAwakening>=2 && m.kind==MoveSpec.Kind.MOVE)x+=.06;
        if((roleIs(d,"TECH")||roleIs(d,"FIELD")) && b.superAwakening>=3 && m.kind==MoveSpec.Kind.MOVE)x+=.06;
        if(m.kind==MoveSpec.Kind.SYNC && b.superAwakening>=2 && (roleIs(d,"TECH")||roleIs(d,"FIELD")))x+=.06;
        if(m.kind==MoveSpec.Kind.SYNC && b.superAwakening>=3 && (roleIs(d,"STRIKE")||roleIs(d,"SPRINT")))x+=.06;
        return x;
    }

    private int firstSyncExtraBuffs(PairDefinition d,PairBuild b){
        int n=0;
        if(b.sixStarEx && roleIs(d,"SUPPORT"))n++;
        if(b.exRoleUnlocked && "SUPPORT".equalsIgnoreCase(d.exRole))n++;
        return n;
    }
    private int firstSyncCountdownReduction(PairDefinition d,PairBuild b){
        int n=0;
        if(b.sixStarEx && roleIs(d,"SPRINT"))n+=3;
        if(b.exRoleUnlocked && "SPRINT".equalsIgnoreCase(d.exRole))n+=3;
        return n;
    }
    private String firstSyncField(PairDefinition d,PairBuild b,MoveSpec sync){
        boolean field=(b.sixStarEx&&roleIs(d,"FIELD"))||(b.exRoleUnlocked&&"FIELD".equalsIgnoreCase(d.exRole));
        for(String tag:d.passiveTags)if(tag.startsWith("SYNC_FIELD:"))return tag.substring("SYNC_FIELD:".length());
        String explicit=sync==null?null:sync.tagValue("EXROLE_FIELD");
        if(explicit!=null)return explicit;
        if(sync!=null&&sync.has("EXROLE_FIELD_ELECTRIC"))return "Electric Terrain";
        if(!field)return null;
        return fieldForType(sync!=null?sync.type:d.type);
    }

    private String fieldForType(String type){
        if(type==null)return null;
        if(type.equalsIgnoreCase("Electric"))return "Electric Terrain";
        if(type.equalsIgnoreCase("Psychic"))return "Psychic Terrain";
        if(type.equalsIgnoreCase("Grass"))return "Grassy Terrain";
        if(type.equalsIgnoreCase("Fairy"))return "Fairy Zone";
        if(type.equalsIgnoreCase("Fire"))return "Sunny Weather";
        if(type.equalsIgnoreCase("Water"))return "Rainy Weather";
        if(type.equalsIgnoreCase("Ice"))return "Ice Zone";
        return type+" Zone";
    }

    private static double targetDamagePriority(BattleState s,int target){
        if(target<0||target>=3)return 1.0;
        double hp=s.enemyHp[target];
        double x=target==1?1.015:1.0;
        if(hp>=0){
            if(hp<=.01)return .05;
            if(hp<.08)x*=1.22;
            else if(hp<.22)x*=1.12;
        }
        return x;
    }

    private double fieldMultiplier(BattleState s,PairDefinition d,MoveSpec m){
        double x=1.0;
        if("Electric".equalsIgnoreCase(m.type)&&s.hasField("Electric Terrain"))x*=1.5;
        if("Fighting".equalsIgnoreCase(m.type)&&s.hasField("Fighting Zone"))x*=1.5;
        String powered=m.tagValue("POWERED_BY_FIELD");if(powered!=null&&s.hasField(powered))x*=1.5;
        // Conservative generic type-zone/terrain support for imported current/future pairs.
        for(String key:s.fieldSteps.keySet()){
            if(key.toLowerCase(Locale.US).contains(m.type.toLowerCase(Locale.US)) && (key.contains("zone")||key.contains("terrain"))) {x*=1.5;break;}
        }
        if(m.has("SYNC_TERRAIN_POWER")&&s.hasField("Electric Terrain"))x*=1.25;
        return x;
    }

    private double fieldSetupValue(String field,BattleState s,TeamBuild team,PairDefinition[] defs){
        int remaining=s.fieldRemaining(field);if(remaining>=5)return 18;
        String type=fieldType(field);int damaging=0,gated=0,strong=0;
        for(PairDefinition d:defs)if(d!=null){
            for(MoveSpec x:d.moves){
                if(x.power1>0&&type.equalsIgnoreCase(x.type)){damaging++;if(x.power1>=180)strong++;}
                if(field.equalsIgnoreCase(x.tagValue("REQUIRES_FIELD")))gated++;
                if("Electric Terrain".equalsIgnoreCase(field)&&x.has("REQUIRES_ELECTRIC_TERRAIN"))gated++;
            }
            if(d.syncMove!=null&&type.equalsIgnoreCase(d.syncMove.type))strong++;
        }
        double v=90+damaging*48+strong*62+gated*115;
        if(remaining>0)v*=.45;
        return Math.min(620,v);
    }
    private static String fieldType(String field){
        if(field==null)return "";String f=field.trim();int k=f.indexOf(' ');return k>0?f.substring(0,k):f;
    }

    private double gaugeAccelValue(TeamBuild team,PairDefinition[] defs,BattleState s){
        if(s.gaugeAccelerationSteps>=5)return 30;
        double normal=team.moveGaugeBarsPerSecond(defs,s.speed,false),fast=team.moveGaugeBarsPerSecond(defs,s.speed,true);
        return 115+(fast-normal)*380;
    }

    private boolean teamNeedsDefense(BattleState s,TeamBuild team,PairDefinition[] defs){
        double minHp=1;double avgDef=0;int n=0;
        for(int i=0;i<3;i++)if(defs[i]!=null){minHp=Math.min(minHp,s.hp[i]);avgDef+=(team.slots[i].defense(defs[i],false)+team.slots[i].defense(defs[i],true))*.5;n++;}
        if(n>0)avgDef/=n;
        return minHp<.88 || avgDef<330 || s.def[0]<4 || s.spdef[0]<4;
    }

    private double teamMasterMultiplier(PairDefinition[] defs, PairDefinition user, String category) {
        int kanto=countTheme(defs,"Kanto"), sinnoh=countTheme(defs,"Sinnoh");
        if(user.hasPassive("MASTER_KANTO_SPECIAL") && "SPECIAL".equalsIgnoreCase(category))
            return 1.0 + Math.min(.50, .20 + .15*Math.max(0,kanto-1));
        if(user.hasPassive("MASTER_KANTO"))
            return 1.0 + Math.min(.30, .10 + .10*Math.max(0,kanto-1));
        if(user.hasPassive("SINNOH_FLAG"))
            return 1.0 + Math.min(.30, .10 + .10*Math.max(0,sinnoh-1));
        // Generic custom-pack hook: MASTER_THEME:<theme>:<basePct>:<perAllyPct>:<capPct>
        for(String tag:user.passiveTags)if(tag.startsWith("MASTER_THEME:")){
            String[] p=tag.split(":");if(p.length>=5)try{
                int count=countTheme(defs,p[1]);double base=Double.parseDouble(p[2]),per=Double.parseDouble(p[3]),cap=Double.parseDouble(p[4]);
                return 1.0+Math.min(cap,base+per*Math.max(0,count-1));
            }catch(Exception ignored){}
        }
        return 1.0;
    }
    private static int countTheme(PairDefinition[] defs,String theme){int n=0;for(PairDefinition d:defs)if(d!=null&&d.hasTheme(theme))n++;return n;}
    private static boolean roleIs(PairDefinition d,String role){return d!=null&&d.role!=null&&d.role.toUpperCase(Locale.US).startsWith(role.toUpperCase(Locale.US));}

    private String reason(PlannedAction a,BattleState s,TeamBuild team,PairDefinition[] defs) {
        MoveSpec m=a.move;PairDefinition d=defs[a.pairIndex];PairBuild b=team.slots[a.pairIndex];
        if(m.has("RED1996_ORIGINAL")) return "front-load Sp. Def -6, paralysis, Electric Terrain and sync acceleration before Glorious Thunder";
        if(m.has("ASH_BUDDY")) return "activated high-power buddy move; save/use gauge so its gauge-scaling hit is not wasted";
        if(m.has("SET_ELECTRIC_TERRAIN")) return "establish Electric Terrain for the team's Electric damage and terrain-gated moves";
        if(m.tagValue("SET_FIELD")!=null) return "establish "+m.tagValue("SET_FIELD")+" because the team can exploit that field";
        if(m.has("CYNTHIA_SUPERIOR")) return "zero-gauge hit plus Move Gauge Acceleration and team Physical/Special Moves Up Next";
        if(m.has("HEAL_ALLY_40")||m.tagDouble("HEAL_ALLY",0)>0) return "heal the lowest-HP ally because it crossed the adaptive survival threshold";
        if(m.kind==MoveSpec.Kind.SYNC){
            if(!s.firstSyncUsed){int e=firstSyncExtraBuffs(d,b),cd=firstSyncCountdownReduction(d,b);String f=firstSyncField(d,b,m);return "first sync: damage + team sync buff"+(e>0?" + "+e+" extra Support buff":"")+(cd>0?" + "+cd+" countdown acceleration":"")+(f!=null?" + "+f:"");}
            return "sync is ready; convert countdown into damage and another permanent team sync buff";
        }
        if(m.kind==MoveSpec.Kind.MAX) return "one-time Max value plus pair-specific countdown/status effects";
        return String.format(Locale.US,"score %.0f from damage, setup, gauge economy, build stats and 7-ply lookahead",a.score);
    }
}
