package es.retalis.mazdaavisos;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Historial local de los últimos 100 avisos. No registra credenciales ni payloads. */
final class AlertHistory {
 private static final String PREF="alert_history", KEY="entries";
 static synchronized void add(Context c,String title,String body){
   SharedPreferences p=c.getSharedPreferences(PREF,Context.MODE_PRIVATE);
   JSONArray old;
   try{old=new JSONArray(p.getString(KEY,"[]"));}catch(Exception e){old=new JSONArray();}
   JSONArray next=new JSONArray();
   try{next.put(new JSONObject().put("time",System.currentTimeMillis())
     .put("title",title).put("body",body));}catch(Exception ignored){}
   for(int i=0;i<old.length()&&i<99;i++)next.put(old.opt(i));
   p.edit().putString(KEY,next.toString()).apply();
 }
 static JSONArray read(Context c){
   try{return new JSONArray(c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(KEY,"[]"));}
   catch(Exception e){return new JSONArray();}
 }
 static String when(long time){
   return new SimpleDateFormat("dd/MM · HH:mm",Locale.getDefault()).format(new Date(time));
 }
 static void clear(Context c){c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().remove(KEY).apply();}
}
