package es.retalis.mazdaavisos;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
 static final String CHANNEL="mazda_lab_alerts";
 private static final int NAVY=Color.rgb(25,42,62), MUTED=Color.rgb(118,128,143),
   BG=Color.rgb(246,247,251), WHITE=Color.WHITE, ACCENT=Color.rgb(91,113,203);
 private LinearLayout root;
 private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
 private GradientDrawable shape(int color,int radius){
   GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;
 }
 @Override public void onCreate(Bundle state){super.onCreate(state);show();}
 @Override public void onResume(){super.onResume();show();}
 static void alert(Context c,String title,String body,int id){
   NotificationManager nm=(NotificationManager)c.getSystemService(NOTIFICATION_SERVICE);
   if(nm==null)return;
   if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Avisos Mazda experimentales",NotificationManager.IMPORTANCE_HIGH));
   if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
   PendingIntent pi=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   Notification n=new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
     .setContentTitle(title).setContentText(body)
     .setStyle(new Notification.BigTextStyle().bigText(body))
     .setContentIntent(pi).setAutoCancel(true).build();
   nm.notify(id,n);
 }
 private TextView text(String value,int size,int color,boolean bold){
   TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
   if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
   return t;
 }
 private void addText(LinearLayout parent,String value,int size,int color,boolean bold){
   TextView t=text(value,size,color,bold);t.setPadding(0,dp(4),0,dp(4));parent.addView(t);
 }
 private LinearLayout card(String title){
   LinearLayout c=new LinearLayout(this);c.setOrientation(1);c.setPadding(dp(20),dp(18),dp(20),dp(18));
   c.setBackground(shape(WHITE,24));c.setElevation(dp(2));
   LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,0,0,dp(14));root.addView(c,p);
   if(title!=null)addText(c,title,17,NAVY,true);
   return c;
 }
 private void action(LinearLayout parent,String label,Runnable run,boolean primary){
   TextView t=text(label,15,primary?WHITE:NAVY,true);
   t.setGravity(android.view.Gravity.CENTER);t.setPadding(dp(14),dp(15),dp(14),dp(15));
   t.setBackground(shape(primary?NAVY:BG,16));
   LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(8),0,0);parent.addView(t,p);
   t.setOnClickListener(v->run.run());
 }
 private void launchMazda(boolean scan){
   if(scan&&!isAccessibilityEnabled()){new AlertDialog.Builder(this).setMessage("Activa accesibilidad para la lectura manual de mensajes.").setPositiveButton("Aceptar",null).show();return;}
   Intent i=getPackageManager().getLaunchIntentForPackage("com.cma.mazda.eu");
   if(i==null){new AlertDialog.Builder(this).setMessage("No se encuentra la aplicación Mazda Europa.").setPositiveButton("Aceptar",null).show();return;}
   if(scan){MazdaMonitor.requestSingleCheck();Data.status(this,"Comprobación solicitada; abriendo Mazda...");}
   else MazdaMonitor.stopCheck();
   startActivity(i);
 }
 private boolean isAccessibilityEnabled(){
   String current=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
   String wanted=new ComponentName(this,MazdaMonitor.class).flattenToString();
   if(current==null)return false;
   for(String s:current.split(":"))if(s.equalsIgnoreCase(wanted))return true;
   return false;
 }
 private void history(){
   JSONArray entries=AlertHistory.read(this);
   LinearLayout c=card("Actividad reciente · "+entries.length());
   if(entries.length()==0)addText(c,"Todavía no hay avisos guardados.",14,MUTED,false);
   for(int i=0;i<Math.min(8,entries.length());i++){
     JSONObject o=entries.optJSONObject(i);if(o==null)continue;
     addText(c,o.optString("title"),14,NAVY,true);
     addText(c,AlertHistory.when(o.optLong("time"))+" · "+o.optString("body"),12,MUTED,false);
     if(i<Math.min(8,entries.length())-1){View line=new View(this);line.setBackgroundColor(BG);
       LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.setMargins(0,dp(9),0,dp(9));c.addView(line,lp);}
   }
   action(c,"Ver historial completo",()->{
     StringBuilder b=new StringBuilder();
     for(int i=0;i<entries.length();i++){JSONObject o=entries.optJSONObject(i);if(o!=null)
       b.append(AlertHistory.when(o.optLong("time"))).append("  ").append(o.optString("title"))
       .append("\n").append(o.optString("body")).append("\n\n");}
     new AlertDialog.Builder(this).setTitle("Historial de avisos")
       .setMessage(b.length()==0?"Sin avisos":b.toString()).setPositiveButton("Cerrar",null)
       .setNeutralButton("Borrar",(d,w)->new AlertDialog.Builder(this).setTitle("Borrar avisos")
         .setMessage("¿Eliminar el historial local de alertas?")
         .setPositiveButton("Borrar",(d2,w2)->{AlertHistory.clear(this);show();})
         .setNegativeButton("Cancelar",null).show()).show();
   },false);
 }
 void show(){
   ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
   root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(18),dp(24),dp(18),dp(36));
   scroll.addView(root);setContentView(scroll);
   addText(root,"MAZDA AVISOS",27,NAVY,true);
   addText(root,"LAB  ·  v0.11  ·  Conexión directa al vehículo",12,MUTED,false);
   LinearLayout top=card("Tu CX-6e");
   String status=MqttSessionService.currentStatus(this);
   boolean live=status.contains("MQTT conectado")&&!status.contains("SIN CONFIRMACIÓN");
   addText(top,live?"●  Monitor conectado":"○  Estado del monitor",15,live?Color.rgb(25,136,100):MUTED,true);
   addText(top,status,13,MUTED,false);
   action(top,"Actualizar estado",this::show,false);
   LinearLayout quick=card("Control del monitor");
   action(quick,"Configurar e iniciar conexión Mazda",()->startActivity(new Intent(this,ConnectionTestActivity.class)),true);
   action(quick,"Detener monitor",()->{startService(new Intent(this,MqttSessionService.class).setAction(MqttSessionService.ACTION_STOP));show();},false);
   LinearLayout tiles=card("Avisos y Centinela");
   addText(tiles,"Puertas  ·  Ventanas  ·  Maletero",15,NAVY,true);
   addText(tiles,"Batería  ·  Carga  ·  Temperatura",15,NAVY,true);
   addText(tiles,"Centinela: aperturas cuando el coche indica aparcado y bloqueado. No detecta personas ni golpes.",13,MUTED,false);
   action(tiles,"Personalizar avisos y umbrales",()->startActivity(new Intent(this,AlertSettingsActivity.class)),true);
   history();
   LinearLayout extra=card("Herramientas");
   addText(extra,"Accesibilidad: "+(isAccessibilityEnabled()?"activada":"desactivada")+
     " (solo para leer mensajes de la app oficial)",13,MUTED,false);
   action(extra,"Activar accesibilidad",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)),false);
   if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
     action(extra,"Permitir notificaciones",()->requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11),false);
   action(extra,"Comprobar mensajes de Mazda",()->launchMazda(true),false);
   action(extra,"Abrir aplicación Mazda",()->launchMazda(false),false);
   action(extra,"Probar notificación",()->alert(this,"Prueba Mazda Avisos","Notificación de prueba; no es un aviso del vehículo.",99),false);
   action(extra,"Copiar diagnóstico técnico",()->{
     android.content.ClipboardManager cb=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
     if(cb!=null)cb.setPrimaryClip(ClipData.newPlainText("Diagnóstico Mazda",Data.technicalDiagnostic(this)));
   },false);
   addText(root,"Diseño inspirado en la aplicación Mazda. Sin Home Assistant. No sustituye alarmas ni avisos oficiales. No envía órdenes al coche.",12,MUTED,false);
 }
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
   super.onRequestPermissionsResult(request,permissions,results);show();
 }
}
