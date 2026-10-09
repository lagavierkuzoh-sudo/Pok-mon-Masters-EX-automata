import com.example.pomastersstrategist.core.*;
import java.util.*;

public class PlannerHarness {
  static Set<String> s(String...x){return new LinkedHashSet<>(Arrays.asList(x));}
  static MoveSpec move(String id,String name,int slot,String type,int gauge,double p,String cat,MoveSpec.Target target,String...tags){
    return new MoveSpec(id,name,MoveSpec.Kind.MOVE,slot,type,cat,gauge,-1,p,p,target,s(tags));
  }
  static PairDefinition ash(){return new PairDefinition("ash_pikachu","Ash","Pikachu","STRIKE_SPECIAL","NONE","Electric",
      new int[]{719,288,155,487,155,271},new int[]{819,328,195,527,195,311},Arrays.asList(
      move("thunder","Thunder",1,"Electric",1,192,"SPECIAL",MoveSpec.Target.ENEMY,"DAMAGE","PARALYZE"),
      new MoveSpec("buff","Going All Out!",MoveSpec.Kind.MOVE,4,"None","STATUS",0,1,0,0,MoveSpec.Target.SELF,s("BUFF_SPATK_4_6","BUFF_CRIT_3","BUFF_SPEED_IF_HP50")),
      new MoveSpec("buddy","Best Buddies Thunderbolt",MoveSpec.Kind.MOVE,3,"Electric","SPECIAL",0,1,480,480,MoveSpec.Target.ENEMY,s("DAMAGE","ASH_BUDDY","GAUGE_POWER"))
    ),new MoveSpec("sync","Ash Sync",MoveSpec.Kind.SYNC,0,"Electric","SPECIAL",0,-1,300,300,MoveSpec.Target.ENEMY,s("DAMAGE","SYNC_EX_AOE")),Collections.emptyList(),s("MASTER_KANTO_SPECIAL"),s("Electric","Kanto","Main Character"),"");}
  static PairDefinition ssred(){return new PairDefinition("ss_red_thunderbolt_pikachu","SS Red","Pikachu","TECH","FIELD","Electric",
      new int[]{694,336,190,336,190,402},new int[]{854,376,250,376,250,482},Arrays.asList(
      new MoveSpec("terrain","Electric Terrain",MoveSpec.Kind.MOVE,2,"Electric","STATUS",2,2,0,0,MoveSpec.Target.FIELD,s("SET_ELECTRIC_TERRAIN")),
      move("ovt","Origin Volt Tackle",3,"Electric",2,180,"PHYSICAL",MoveSpec.Target.ALL_ENEMIES,"DAMAGE","REQUIRES_ELECTRIC_TERRAIN")
    ),new MoveSpec("rsync","Red Sync",MoveSpec.Kind.SYNC,0,"Electric","PHYSICAL",0,-1,240,240,MoveSpec.Target.ENEMY,s("DAMAGE","SYNC_EX_POWER_1_5","EXROLE_FIELD_ELECTRIC")),Collections.emptyList(),s("MASTER_KANTO","RED_ET_ACCEL_HEAL","CLUTCH_CRIT"),s("Electric","Kanto","Main Character"),"");}
  static PairDefinition cynthia(){return new PairDefinition("ss_cynthia_aura_lucario","SS Cynthia Aura","Lucario","SUPPORT","SPRINT","Fighting",
      new int[]{689,237,307,326,307,286},new int[]{849,297,347,386,347,366},Arrays.asList(
      new MoveSpec("def","It Won't End Here!",MoveSpec.Kind.MOVE,4,"None","STATUS",0,1,0,0,MoveSpec.Target.ALL_ALLIES,s("TEAM_DEF_SPDEF_4_6")),
      move("vw","Vacuum Wave",1,"Fighting",1,24,"SPECIAL",MoveSpec.Target.ENEMY,"DAMAGE")
    ),new MoveSpec("csync","Cynthia Sync",MoveSpec.Kind.SYNC,0,"Fighting","SPECIAL",0,-1,192,192,MoveSpec.Target.ENEMY,s("DAMAGE","SET_FIGHTING_ZONE")),Collections.emptyList(),s("FIGHTING_ZONE_ENTRY_SYNC","SINNOH_FLAG"),s("Fighting","Sinnoh","Champion","Sygna Suit"),"");}
  static PairDefinition red1996(){return new PairDefinition("red_1996_pikachu","Red (1996)","Pikachu","TECH","SUPPORT","Electric",
      new int[]{671,312,146,336,147,335},new int[]{831,352,226,376,227,375},Arrays.asList(
      move("ever","Everlasting Thunderbolt",1,"Electric",2,300,"SPECIAL",MoveSpec.Target.ENEMY,"DAMAGE","RED1996_REQUIRES_SPATK","PARALYZE"),
      new MoveSpec("orig","The Original Thunder",MoveSpec.Kind.MOVE,3,"Electric","SPECIAL",2,1,600,600,MoveSpec.Target.ENEMY,s("DAMAGE","RED1996_REQUIRES_SPATK","RED1996_ORIGINAL","DEBUFF_SPDEF_6","PARALYZE","SET_ELECTRIC_TERRAIN","SYNC_CD_MINUS_3")),
      new MoveSpec("glory","Glorious Thunder",MoveSpec.Kind.MOVE,3,"Electric","SPECIAL",2,1,1200,1200,MoveSpec.Target.ENEMY,s("DAMAGE","RED1996_REQUIRES_SPATK","RED1996_GLORIOUS"))
    ),new MoveSpec("r96sync","Red 1996 Sync",MoveSpec.Kind.SYNC,0,"Electric","SPECIAL",0,-1,240,240,MoveSpec.Target.ENEMY,s("DAMAGE","SYNC_EX_POWER_1_5")),Collections.emptyList(),s("MASTER_KANTO_SPECIAL","ENTRY_SPATK6","CRIT_ALWAYS","RED1996_FIRST_SYNC"),s("Electric","Kanto","Main Character"),"");}

  static TeamBuild team(PairDefinition[] defs){TeamBuild t=new TeamBuild();for(int i=0;i<3;i++){t.slots[i]=new PairBuild(defs[i].id);t.slots[i].level=200;t.slots[i].moveLevel=5;t.slots[i].sixStarEx=true;t.slots[i].exactSpeed=defs[i].speed140Max;}return t;}
  static void assertTrue(boolean x,String msg){if(!x)throw new AssertionError(msg);}
  public static void main(String[] args){
    BattlePlanner p=new BattlePlanner();StrategyProfile sp=new StrategyProfile();

    PairDefinition[] trio={ash(),ssred(),cynthia()};TeamBuild t=team(trio);t.slots[1].exRoleUnlocked=true;t.slots[2].exRoleUnlocked=true;
    BattleState opening=BattleState.initial(t,trio);PlannedAction first=p.plan(opening,t,trio,sp,(c,k)->0);
    System.out.println("Opening: "+first+" :: "+first.reason);
    assertTrue(first!=null,"opening action missing");

    BattleState ready=BattleState.initial(t,trio);ready.syncCountdown=0;ready.gauge=4;PlannedAction fs=p.plan(ready,t,trio,sp,(c,k)->0);
    System.out.println("First-sync choice: "+fs+" :: "+fs.reason);
    assertTrue(fs!=null&&fs.move.kind==MoveSpec.Kind.SYNC,"planner should sync when first sync is ready");
    assertTrue(fs.pairIndex==2,"Ash+SS Red+Cynthia should prefer Cynthia's Support+Sprint first sync by default");

    PairDefinition[] r96team={ash(),red1996(),cynthia()};TeamBuild t2=team(r96team);t2.slots[1].exRoleUnlocked=true;t2.slots[2].exRoleUnlocked=true;
    BattleState r96=BattleState.initial(t2,r96team);r96.syncCountdown=0;r96.gauge=5;PlannedAction rfs=p.plan(r96,t2,r96team,sp,(c,k)->0);
    System.out.println("Red-1996 first-sync choice: "+rfs+" :: "+rfs.reason);
    assertTrue(rfs!=null&&rfs.move.kind==MoveSpec.Kind.SYNC,"Red 1996 variant should still take a ready sync");
    assertTrue(rfs.pairIndex==1,"Red (1996)'s Support EX role + unique first-sync passive should win first-sync value");

    double normal=t.moveGaugeBarsPerSecond(trio,new int[]{0,0,0},false);double accel=t.moveGaugeBarsPerSecond(trio,new int[]{0,0,0},true);
    System.out.printf(Locale.US,"Gauge: %.3f bars/s normal, %.3f accelerated%n",normal,accel);
    assertTrue(accel>normal,"gauge acceleration must increase simulated regeneration");

    // Imported-style activation requirement test.
    MoveSpec gated=move("gated","Buddy-like Test",3,"Ice",2,300,"PHYSICAL",MoveSpec.Target.ALL_ENEMIES,"DAMAGE","REQUIRES_SYNC_BUFF:1");
    PairDefinition imp=new PairDefinition("imported","Imported","Pair","STRIKE_PHYSICAL","TECH","Ice",new int[]{700,450,200,100,200,300},new int[]{700,450,200,100,200,300},Arrays.asList(gated),null,Collections.emptyList(),s("AUTO_TERA_ENTRY"),s("Ice","Unova"),"");
    PairDefinition[] impDefs={imp,cynthia(),ssred()};TeamBuild it=team(impDefs);BattleState is=BattleState.initial(it,impDefs);
    boolean present=false;for(PlannedAction a:p.legalActions(is,it,impDefs,sp))if(a.move.id.equals("gated"))present=true;assertTrue(!present,"sync-gated move became legal too early");
    is.syncBuff=1;for(PlannedAction a:p.legalActions(is,it,impDefs,sp))if(a.move.id.equals("gated"))present=true;assertTrue(present,"sync-gated imported move never became legal");

    // Calibrated enemy HP should expose target variants and favor a nearly defeated target enough to finish it.
    BattleState targetState=BattleState.initial(t,trio);targetState.gauge=6;targetState.enemyHp[0]=.06;targetState.enemyHp[1]=.85;targetState.enemyHp[2]=.72;
    List<PlannedAction> ranked=p.rankedImmediate(targetState,t,trio,sp,(c,k)->0);
    int targetVariants=0;for(PlannedAction a:ranked)if(a.move.id.equals("thunder"))targetVariants++;
    assertTrue(targetVariants==3,"single-target move should generate one legal action per visible enemy");
    PlannedAction bestThunder=null;for(PlannedAction a:ranked)if(a.move.id.equals("thunder")){bestThunder=a;break;}
    assertTrue(bestThunder!=null&&bestThunder.targetIndex==0,"planner should value finishing a nearly defeated enemy");

    // Sequence-aware contexts must change after an action, otherwise contextual learning cannot specialize combos.
    BattleState seq=BattleState.initial(t,trio);String c0=seq.contextBucket();PlannedAction any=p.legalActions(seq,t,trio,sp).get(0);p.apply(seq,t,trio,any);String c1=seq.contextBucket();
    assertTrue(!c0.equals(c1),"learning context should encode action/state progression");

    System.out.println("ALL CORE PLANNER TESTS PASSED");
  }
}
