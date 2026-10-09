package com.example.pomastersstrategist;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.example.pomastersstrategist.core.PairBuild;
import com.example.pomastersstrategist.core.PairDefinition;
import com.example.pomastersstrategist.core.TeamBuild;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE=1001,REQ_IMPORT=1002;
    private MediaProjectionManager projectionManager; private PairCatalog catalog; private TeamConfigStore teamStore; private final PairEditor[] editors=new PairEditor[3];
    @Override protected void onCreate(Bundle b){super.onCreate(b);SharedState.init(this);catalog=SharedState.catalog(this);teamStore=new TeamConfigStore(this);projectionManager=(MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!= PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},50);buildUi();}
    private Button button(String s){Button b=new Button(this);b.setText(s);return b;}
    private TextView text(String s,float size){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(Color.DKGRAY);return v;}
    private EditText number(String hint){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);return e;}

    private void buildUi(){
        ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(28,32,28,40);root.setBackgroundColor(0xFFF4F6F8);scroll.addView(root);
        TextView title=text("Pokémon Masters EX — Autonomous Strategist v2.2",23);title.setTextColor(Color.BLACK);root.addView(title);
        root.addView(text("Knowledge pack: game Ver. "+catalog.gameVersion()+". This build uses a semantic battle planner first, then SELF mode learns stage/team preferences entirely from visual battle outcomes. No +/− rewards and no manually labelled victory/defeat screens are required.",14));
        TextView warning=text("Important: automation may violate a game's terms or fair-play rules. This project uses screen capture + Android Accessibility only; it does not inject into the game, read game memory, bypass anti-cheat, or hide automation.",12);warning.setPadding(0,8,0,12);root.addView(warning);

        TeamBuild current=teamStore.load(catalog);
        for(int i=0;i<3;i++){editors[i]=new PairEditor(i,current.slots[i]);root.addView(editors[i].view);}
        Button save=button("SAVE TEAM / BUILD AND RESET BATTLE MODEL");save.setOnClickListener(v->{try{TeamBuild t=new TeamBuild();for(int i=0;i<3;i++)t.slots[i]=editors[i].read();teamStore.save(t);SharedState.runtime(this).refreshTeam();toast("Team/build saved");}catch(Exception e){toast("Check numeric fields: "+e.getMessage());}});root.addView(save);

        TextView explain=text("Level handling: set Lv. 1–200, move level 1–5, 6★ EX, EX Role and Superawakening 0–5. For the most accurate planning, optionally enter all six current in-game stats. Attack/Sp. Atk improve damage ranking, Speed improves move-gauge timing, and HP/Defense/Sp. Def improve survival estimates. If left blank, the planner estimates them from the bundled Lv.140 data and level/build multipliers.",12);explain.setPadding(0,8,0,12);root.addView(explain);

        Button imp=button("IMPORT / REPLACE CUSTOM PAIR KNOWLEDGE PACK (.json)");imp.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("application/json");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,REQ_IMPORT);});root.addView(imp);
        root.addView(text("The bundled high-detail v2.73 pack contains Ash & Pikachu, Sygna Suit Red (Thunderbolt) & Pikachu, Red (1996) & Pikachu, and Sygna Suit Cynthia (Aura) & Lucario. The JSON format is documented in KNOWLEDGE_PACK_FORMAT.md so more current/future pairs can be added without changing planner code.",12));

        Button access=button("1. ENABLE ACCESSIBILITY SERVICE");access.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));root.addView(access);
        Button overlay=button("2. GRANT OVERLAY / SHOW STRATEGIST");overlay.setOnClickListener(v->{if(!Settings.canDrawOverlays(this))startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+getPackageName())));else startService(new Intent(this,FloatingOverlayService.class));});root.addView(overlay);
        Button capture=button("3. START SCREEN CAPTURE + PLANNER");capture.setOnClickListener(v->startActivityForResult(projectionManager.createScreenCaptureIntent(),REQ_CAPTURE));root.addView(capture);
        Button stop=button("STOP BOT SERVICES");stop.setOnClickListener(v->{stopService(new Intent(this,ScreenCaptureService.class));stopService(new Intent(this,FloatingOverlayService.class));});root.addView(stop);
        Button resetBrain=button("LONG-PRESS: RESET LEARNED OUTCOME BRAIN");resetBrain.setOnLongClickListener(v->{SharedState.brain(this).reset();toast("Outcome memory reset; expert knowledge kept");return true;});root.addView(resetBrain);
        TextView tips=text("SELF mode is autonomous after one-time Android/setup work. Configure the three Sync Pairs/builds and calibrate PAIR-1/2/3 + MOVE-1..4 + SYNC/MAX if your phone layout differs. HP bars and move gauge now have automatic visual fallbacks; manual region calibration is optional but improves reliability. In SELF, the bot generates its own reward from enemy damage/KOs versus ally damage, infers wins/losses, anneals exploration on each stage/team, and automatically advances/replays result screens. You do not press +/− or teach terminal screens.",13);tips.setPadding(0,16,0,0);root.addView(tips);
        setContentView(scroll);
    }

    private final class PairEditor {
        final LinearLayout view; final Spinner pair,grid; final EditText level,ml,sa,hp,atk,def,spatk,spdef,speed,lucky; final CheckBox ex,exRole; final List<PairDefinition> list;
        PairEditor(int slot,PairBuild build){
            view=new LinearLayout(MainActivity.this);view.setOrientation(LinearLayout.VERTICAL);view.setPadding(0,14,0,14);TextView h=text("TEAM SLOT "+(slot+1),17);h.setTextColor(Color.BLACK);view.addView(h);
            list=catalog.sorted();pair=new Spinner(MainActivity.this);ArrayList<String> names=new ArrayList<>();for(PairDefinition p:list)names.add(p.displayName+"  ["+p.role+("NONE".equals(p.exRole)?"":" / EX "+p.exRole)+"]");pair.setAdapter(new ArrayAdapter<>(MainActivity.this,android.R.layout.simple_spinner_dropdown_item,names));int selected=0;for(int i=0;i<list.size();i++)if(list.get(i).id.equals(build.pairId)){selected=i;break;}if(!list.isEmpty())pair.setSelection(selected);view.addView(pair);
            LinearLayout nums=new LinearLayout(MainActivity.this);level=number("Lv");ml=number("Move Lv");sa=number("SA 0-5");level.setText(String.valueOf(build.level));ml.setText(String.valueOf(build.moveLevel));sa.setText(String.valueOf(build.superAwakening));nums.addView(level,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));nums.addView(ml,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));nums.addView(sa,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));view.addView(nums);
            LinearLayout flags=new LinearLayout(MainActivity.this);ex=new CheckBox(MainActivity.this);ex.setText("6★ EX");ex.setChecked(build.sixStarEx);exRole=new CheckBox(MainActivity.this);exRole.setText("EX Role unlocked");exRole.setChecked(build.exRoleUnlocked);flags.addView(ex);flags.addView(exRole);view.addView(flags);
            grid=new Spinner(MainActivity.this);String[] grids={"BALANCED","DAMAGE","SYNC","GAUGE","SURVIVAL"};grid.setAdapter(new ArrayAdapter<>(MainActivity.this,android.R.layout.simple_spinner_dropdown_item,grids));for(int i=0;i<grids.length;i++)if(grids[i].equals(build.gridStyle))grid.setSelection(i);view.addView(grid);
            TextView st=text("Exact current stats (optional; strongly recommended at Lv.150–200)",12);view.addView(st);
            LinearLayout stats1=new LinearLayout(MainActivity.this);hp=number("HP");atk=number("Atk");def=number("Def");if(build.exactHp>0)hp.setText(String.valueOf(build.exactHp));if(build.exactAttack>0)atk.setText(String.valueOf(build.exactAttack));if(build.exactDefense>0)def.setText(String.valueOf(build.exactDefense));stats1.addView(hp,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));stats1.addView(atk,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));stats1.addView(def,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));view.addView(stats1);
            LinearLayout stats2=new LinearLayout(MainActivity.this);spatk=number("Sp.Atk");spdef=number("Sp.Def");speed=number("Speed");if(build.exactSpAttack>0)spatk.setText(String.valueOf(build.exactSpAttack));if(build.exactSpDefense>0)spdef.setText(String.valueOf(build.exactSpDefense));if(build.exactSpeed>0)speed.setText(String.valueOf(build.exactSpeed));stats2.addView(spatk,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));stats2.addView(spdef,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));stats2.addView(speed,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));view.addView(stats2);
            lucky=new EditText(MainActivity.this);lucky.setHint("Lucky skill / note (optional)");lucky.setSingleLine(true);lucky.setText(build.luckySkill);view.addView(lucky);
        }
        PairBuild read(){if(list.isEmpty())throw new IllegalStateException("No pair definitions");PairBuild b=new PairBuild(list.get(pair.getSelectedItemPosition()).id);b.level=clamp(parse(level,200),1,200);b.moveLevel=clamp(parse(ml,3),1,5);b.superAwakening=clamp(parse(sa,0),0,5);b.sixStarEx=ex.isChecked();b.exRoleUnlocked=exRole.isChecked();b.gridStyle=(String)grid.getSelectedItem();b.luckySkill=lucky.getText().toString();b.exactHp=parse(hp,0);b.exactAttack=parse(atk,0);b.exactDefense=parse(def,0);b.exactSpAttack=parse(spatk,0);b.exactSpDefense=parse(spdef,0);b.exactSpeed=parse(speed,0);return b;}
        int parse(EditText e,int d){String s=e.getText().toString().trim();return s.isEmpty()?d:Integer.parseInt(s);}int clamp(int v,int a,int b){return Math.max(a,Math.min(b,v));}
    }

    private String readUri(Uri uri)throws Exception{try(BufferedReader r=new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(uri), StandardCharsets.UTF_8))){StringBuilder b=new StringBuilder();String l;while((l=r.readLine())!=null)b.append(l).append('\n');return b.toString();}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onResume(){super.onResume();if(Settings.canDrawOverlays(this))startService(new Intent(this,FloatingOverlayService.class));}
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);try{
        if(requestCode==REQ_CAPTURE&&resultCode==RESULT_OK&&data!=null){Intent i=new Intent(this,ScreenCaptureService.class);i.putExtra(ScreenCaptureService.EXTRA_RESULT_CODE,resultCode);i.putExtra(ScreenCaptureService.EXTRA_DATA,data);if(Build.VERSION.SDK_INT>=26)startForegroundService(i);else startService(i);if(Settings.canDrawOverlays(this))startService(new Intent(this,FloatingOverlayService.class));toast("Strategist running in SELF mode — autonomous learning enabled");}
        else if(requestCode==REQ_IMPORT&&resultCode==RESULT_OK&&data!=null){String json=readUri(data.getData());int n=catalog.importJson(json);SharedState.reloadCatalogAndTeam(this);toast("Imported "+n+" pair definitions. Reopening setup UI.");buildUi();}
    }catch(Exception e){toast("Import/start failed: "+e.getMessage());}}
}
