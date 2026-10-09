package com.example.pomastersstrategist;

import android.content.Context;

import com.example.pomastersstrategist.core.MoveSpec;
import com.example.pomastersstrategist.core.PairDefinition;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PairCatalog {
    private final Context context;
    private final LinkedHashMap<String, PairDefinition> pairs = new LinkedHashMap<>();
    private String gameVersion = "unknown";
    private static final String CUSTOM = "custom_pair_pack.json";

    public PairCatalog(Context c) { context=c.getApplicationContext(); reload(); }

    public synchronized void reload() {
        pairs.clear(); gameVersion="unknown";
        try (InputStream in=context.getAssets().open("pair_catalog_v273.json")) { mergeJson(readAll(in), true); } catch(Exception ignored) {}
        File f=new File(context.getFilesDir(),CUSTOM);
        if(f.exists()) try(InputStream in=new FileInputStream(f)){ mergeJson(readAll(in), false); }catch(Exception ignored){}
    }

    public String gameVersion(){return gameVersion;}
    public Collection<PairDefinition> all(){return Collections.unmodifiableCollection(pairs.values());}
    public PairDefinition get(String id){return pairs.get(id);}
    public List<PairDefinition> sorted(){
        ArrayList<PairDefinition> x=new ArrayList<>(pairs.values());
        x.sort(Comparator.comparing(p->p.displayName)); return x;
    }

    public synchronized int importJson(String json) throws Exception {
        // Validate into a temporary catalog representation first.
        JSONObject root=new JSONObject(json);
        JSONArray a=root.getJSONArray("pairs");
        if(a.length()==0) throw new IllegalArgumentException("No pairs in pack");
        File f=new File(context.getFilesDir(),CUSTOM);
        try(FileOutputStream out=new FileOutputStream(f)){out.write(json.getBytes(StandardCharsets.UTF_8));}
        reload(); return a.length();
    }

    private void mergeJson(String json, boolean bundled) throws Exception {
        JSONObject root=new JSONObject(json);
        if(bundled) gameVersion=root.optString("gameVersion","unknown");
        JSONArray arr=root.getJSONArray("pairs");
        for(int i=0;i<arr.length();i++) {
            PairDefinition p=parsePair(arr.getJSONObject(i)); pairs.put(p.id,p);
        }
    }

    private PairDefinition parsePair(JSONObject o) throws Exception {
        List<MoveSpec> moves=new ArrayList<>();
        JSONArray ma=o.optJSONArray("moves"); if(ma!=null) for(int i=0;i<ma.length();i++) moves.add(parseMove(ma.getJSONObject(i)));
        MoveSpec sync=o.has("sync")&&!o.isNull("sync")?parseMove(o.getJSONObject("sync")):null;
        List<MoveSpec> max=new ArrayList<>(); JSONArray mx=o.optJSONArray("maxMoves"); if(mx!=null) for(int i=0;i<mx.length();i++) max.add(parseMove(mx.getJSONObject(i)));
        return new PairDefinition(o.getString("id"),o.getString("trainer"),o.getString("pokemon"),
                o.optString("role","UNKNOWN"),o.optString("exRole","NONE"),o.optString("type","Unknown"),
                stats(o.optJSONArray("stats140")),stats(o.optJSONArray("stats140Max")),optionalStats(o.optJSONArray("stats200")),optionalStats(o.optJSONArray("stats200Max")),moves,sync,max,
                strings(o.optJSONArray("passives")),strings(o.optJSONArray("themes")),o.optString("sourceNote",""));
    }

    private MoveSpec parseMove(JSONObject o) throws Exception {
        MoveSpec.Kind kind=MoveSpec.Kind.valueOf(o.optString("kind","MOVE"));
        MoveSpec.Target target=MoveSpec.Target.valueOf(o.optString("target","ENEMY"));
        return new MoveSpec(o.getString("id"),o.getString("name"),kind,o.optInt("slot",0),
                o.optString("type","None"),o.optString("category","STATUS"),o.optInt("gauge",0),o.optInt("uses",-1),
                o.optDouble("power1",0),o.optDouble("power5",0),target,strings(o.optJSONArray("tags")));
    }
    private static int[] stats(JSONArray a){int[] s={600,300,200,300,200,300};if(a!=null)for(int i=0;i<Math.min(6,a.length());i++)s[i]=a.optInt(i,s[i]);return s;}
    private static int[] optionalStats(JSONArray a){if(a==null)return null;int[] s={0,0,0,0,0,0};for(int i=0;i<Math.min(6,a.length());i++)s[i]=a.optInt(i,0);return s;}
    private static Set<String> strings(JSONArray a){LinkedHashSet<String>s=new LinkedHashSet<>();if(a!=null)for(int i=0;i<a.length();i++)s.add(a.optString(i));return s;}
    private static String readAll(InputStream in)throws Exception{BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();String l;while((l=r.readLine())!=null)b.append(l).append('\n');return b.toString();}
}
