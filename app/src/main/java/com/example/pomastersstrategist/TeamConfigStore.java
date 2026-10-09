package com.example.pomastersstrategist;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.pomastersstrategist.core.PairBuild;
import com.example.pomastersstrategist.core.PairDefinition;
import com.example.pomastersstrategist.core.TeamBuild;

import java.util.List;

public final class TeamConfigStore {
    private final SharedPreferences p;
    public TeamConfigStore(Context c){p=c.getSharedPreferences("team_build_v2",Context.MODE_PRIVATE);}

    public TeamBuild load(PairCatalog catalog){
        TeamBuild t=new TeamBuild(); List<PairDefinition> all=catalog.sorted();
        String[] fallback={"ash_pikachu","ss_red_thunderbolt_pikachu","ss_cynthia_aura_lucario"};
        for(int i=0;i<3;i++){
            PairBuild b=new PairBuild();
            String def=catalog.get(fallback[i])!=null?fallback[i]:(all.isEmpty()?"":all.get(Math.min(i,all.size()-1)).id);
            b.pairId=p.getString("pair"+i,def);
            b.level=p.getInt("level"+i,200); b.moveLevel=p.getInt("ml"+i,3);
            b.sixStarEx=p.getBoolean("ex"+i,true); b.exRoleUnlocked=p.getBoolean("exrole"+i,false);
            b.superAwakening=p.getInt("sa"+i,0); b.gridStyle=p.getString("grid"+i,"BALANCED");
            b.luckySkill=p.getString("lucky"+i,"");
            b.exactHp=p.getInt("hp"+i,0); b.exactAttack=p.getInt("atk"+i,0); b.exactDefense=p.getInt("def"+i,0);
            b.exactSpAttack=p.getInt("spatk"+i,0); b.exactSpDefense=p.getInt("spdef"+i,0); b.exactSpeed=p.getInt("speed"+i,0);
            t.slots[i]=b;
        }
        return t;
    }

    public void save(TeamBuild t){
        SharedPreferences.Editor e=p.edit();
        for(int i=0;i<3;i++){
            PairBuild b=t.slots[i]; e.putString("pair"+i,b.pairId).putInt("level"+i,b.level).putInt("ml"+i,b.moveLevel)
                    .putBoolean("ex"+i,b.sixStarEx).putBoolean("exrole"+i,b.exRoleUnlocked).putInt("sa"+i,b.superAwakening)
                    .putString("grid"+i,b.gridStyle).putString("lucky"+i,b.luckySkill)
                    .putInt("hp"+i,b.exactHp).putInt("atk"+i,b.exactAttack).putInt("def"+i,b.exactDefense)
                    .putInt("spatk"+i,b.exactSpAttack).putInt("spdef"+i,b.exactSpDefense).putInt("speed"+i,b.exactSpeed);
        } e.apply();
    }
}
