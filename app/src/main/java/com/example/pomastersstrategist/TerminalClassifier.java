package com.example.pomastersstrategist;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;

public final class TerminalClassifier {
    private static final class T{float[]f;int r;T(float[]x,int y){f=x;r=y;}}
    private final ArrayList<T> t=new ArrayList<>(); private final File file;
    public TerminalClassifier(Context c){file=new File(c.getFilesDir(),"terminal_v1.json");load();}
    public synchronized void label(float[]f,int result){if(f==null)return;t.add(new T(f.clone(),result>0?1:-1));while(t.size()>12)t.remove(0);save();}
    public synchronized int classify(float[]f){float best=Float.MAX_VALUE;int r=0;for(T x:t){float d=BattleVision.distance(f,x.f);if(d<best){best=d;r=x.r;}}return best<=BotConfig.TERMINAL_DISTANCE?r:0;}
    public synchronized int count(){return t.size();}
    private void save(){try{JSONArray a=new JSONArray();for(T x:t){JSONObject o=new JSONObject();o.put("r",x.r);JSONArray v=new JSONArray();for(float z:x.f)v.put(z);o.put("f",v);a.put(o);}try(FileWriter w=new FileWriter(file)){w.write(a.toString());}}catch(Exception ignored){}}
    private void load(){if(!file.exists())return;try(FileReader r=new FileReader(file)){StringBuilder b=new StringBuilder();char[]c=new char[4096];int n;while((n=r.read(c))>0)b.append(c,0,n);JSONArray a=new JSONArray(b.toString());for(int i=0;i<a.length();i++){JSONObject o=a.getJSONObject(i);JSONArray v=o.getJSONArray("f");float[]f=new float[v.length()];for(int j=0;j<f.length;j++)f[j]=(float)v.getDouble(j);t.add(new T(f,o.getInt("r")));}}catch(Exception ignored){}}
}
