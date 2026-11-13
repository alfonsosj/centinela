package es.retalis.mazdaavisos;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

final class Data {
 static SharedPreferences p(Context c){return c.getSharedPreferences("mazda_lab_v2",Context.MODE_PRIVATE);}
 static synchronized boolean save(Context c,String identity,String description){
  try{
   String key=hash(identity);SharedPreferences p=p(c);JSONArray old=new JSONArray(p.getString("history","[]"));
   for(int i=0;i<old.length();i++)if(key.equals(old.getJSONObject(i).optString("key")))return false;
   JSONObject next=new JSONObject();next.put("key",key);next.put("text",description);
   JSONArray arr=new JSONArray();arr.put(next);for(int i=0;i<Math.min(199,old.length());i++)arr.put(old.getJSONObject(i));
   p.edit().putString("history",arr.toString()).apply();return true;
  }catch(Exception e){return false;}
 }
 static boolean initialized(Context c){return p(c).getBoolean("ready",false);}
 static void initialized(Context c,boolean v){p(c).edit().putBoolean("ready",v).apply();}
 static void status(Context c,String s){p(c).edit().putString("last",s).apply();}
 static String last(Context c){return p(c).getString("last","Sin lecturas");}
 static void diagnostic(Context c,String s){p(c).edit().putString("diagnostic",s).apply();}
 static String diagnostic(Context c){return p(c).getString("diagnostic","Sin lectura de pantalla");}
 static List<String> history(Context c){
  List<String> list=new ArrayList<>();
  try{JSONArray a=new JSONArray(p(c).getString("history","[]"));for(int i=0;i<Math.min(30,a.length());i++)list.add(a.getJSONObject(i).optString("text"));}catch(Exception ignored){}
  return list;
 }
 static void clear(Context c){p(c).edit().clear().apply();}
 static String hash(String v){
  try{byte[] bytes=MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte k:bytes)b.append(String.format("%02x",k&255));return b.toString();}catch(Exception e){return v;}
 }
}
