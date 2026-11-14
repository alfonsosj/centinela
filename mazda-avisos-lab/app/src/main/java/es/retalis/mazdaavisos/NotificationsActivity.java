package es.retalis.mazdaavisos;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;

/** Local Mazda alert history and explicit links to this app's Android settings. */
public final class NotificationsActivity extends Activity {
 private static final int INK=0xFF263548,MUTED=0xFF596677,BG=0xFFF4F5FB;
 private LinearLayout root;
 private ScrollView scroll;

 @Override public void onCreate(Bundle state){super.onCreate(state);}
 @Override protected void onResume(){super.onResume();render();}

 private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
 private TextView text(String value,int size,boolean bold){
   TextView view=new TextView(this);view.setText(value);view.setTextSize(size);
   view.setTextColor(bold?INK:MUTED);
   if(bold)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
   return view;
 }
 private LinearLayout card(String title){
   LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);
   panel.setPadding(dp(16),dp(16),dp(16),dp(16));
   GradientDrawable background=new GradientDrawable();background.setColor(Color.WHITE);
   background.setCornerRadius(dp(20));panel.setBackground(background);
   LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(12);
   root.addView(panel,params);panel.addView(text(title,18,true));return panel;
 }
 private void explain(LinearLayout panel,String value){
   TextView view=text(value,14,false);view.setPadding(0,dp(8),0,dp(4));
   view.setLineSpacing(dp(2),1);panel.addView(view);
 }
 private void button(LinearLayout panel,String title,Runnable action){
   Button view=new Button(this);view.setText(title);view.setAllCaps(false);view.setMinHeight(dp(48));
   LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=dp(6);
   panel.addView(view,params);view.setOnClickListener(v->action.run());
 }
 private NotificationManager manager(){return (NotificationManager)getSystemService(NOTIFICATION_SERVICE);}
 private boolean permitted(){NotificationManager nm=manager();return nm!=null&&nm.areNotificationsEnabled();}
 private NotificationChannel alertChannel(){
   NotificationManager nm=manager();return nm==null?null:nm.getNotificationChannel(MainActivity.CHANNEL);
 }
 private boolean channelBlocked(){
   NotificationChannel channel=alertChannel();
   if(channel==null)return false;
   if(channel.getImportance()==NotificationManager.IMPORTANCE_NONE)return true;
   if(android.os.Build.VERSION.SDK_INT>=28&&channel.getGroup()!=null){
     android.app.NotificationChannelGroup group=manager().getNotificationChannelGroup(channel.getGroup());
     return group!=null&&group.isBlocked();
   }
   return false;
 }
 private void androidSettings(Intent intent){
   try{startActivity(intent);}
   catch(ActivityNotFoundException|SecurityException unavailable){
     try{startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));}
     catch(ActivityNotFoundException|SecurityException noDetails){
       new AlertDialog.Builder(this).setTitle("Abrir ajustes de Android")
         .setMessage("Abre Ajustes de Android > Aplicaciones > Mazda Avisos Lab para revisar las notificaciones y la batería.")
         .setPositiveButton("Entendido",null).show();
     }
   }
 }
 private void appSettings(){
   androidSettings(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
     .putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
 }
 private void channelSettings(){
   if(alertChannel()==null){appSettings();return;}
   androidSettings(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
     .putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName())
     .putExtra(Settings.EXTRA_CHANNEL_ID,MainActivity.CHANNEL));
 }
 private void testNotification(){
   if(!permitted()||channelBlocked()){
     boolean appBlocked=!permitted();
     new AlertDialog.Builder(this).setTitle("Notificaciones desactivadas")
       .setMessage(appBlocked?"Activa las notificaciones de Mazda Avisos Lab en Android y vuelve a probar.":
         "Activa la categoría de avisos del vehículo en Android y vuelve a probar.")
       .setPositiveButton("Abrir ajustes",(d,w)->{if(appBlocked)appSettings();else channelSettings();})
       .setNegativeButton("Cancelar",null).show();return;
   }
   try{
     MainActivity.alert(this,"Prueba Mazda Avisos","Notificación de prueba enviada desde los ajustes. No es un evento del vehículo.",99);
     Toast.makeText(this,"Prueba enviada. Revisa la barra de notificaciones de Android.",Toast.LENGTH_LONG).show();
   }catch(SecurityException denied){appSettings();}
   render();
 }
 private void clearHistory(){
   new AlertDialog.Builder(this).setTitle("Borrar historial de avisos")
     .setMessage("Se eliminarán los avisos guardados en este teléfono. Los mensajes de Mazda oficial y los ajustes de avisos se conservan.")
     .setPositiveButton("Eliminar",(d,w)->{AlertHistory.clear(this);render();})
     .setNegativeButton("Cancelar",null).show();
 }
 private void render(){
   int previousY=scroll==null?0:scroll.getScrollY();
   scroll=new ScrollView(this);scroll.setBackgroundColor(BG);scroll.setFillViewport(true);
   scroll.setFitsSystemWindows(true);
   root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(18),dp(18),dp(18),dp(30));
   scroll.addView(root);setContentView(scroll);
   button(root,"‹ Volver",this::finish);
   root.addView(text("Notificaciones",25,true));
   explain(root,"Avisos guardados en este teléfono y acceso a los permisos de Android.");

   LinearLayout permissions=card("Permisos y sonido");
   TextView appState=text(permitted()?"Notificaciones de la app: permitidas":"Notificaciones de la app: desactivadas",15,true);
   appState.setTextColor(permitted()?0xFF13785D:0xFFA3432B);permissions.addView(appState);
   NotificationChannel channel=alertChannel();
   explain(permissions,channelBlocked()?"Avisos del vehículo: categoría desactivada en Android.":
     channel==null?"La categoría de avisos se creará al enviar la primera notificación o la prueba.":
     "Avisos del vehículo: "+(channel.getImportance()<NotificationManager.IMPORTANCE_DEFAULT?
       "configurados sin sonido o con prioridad baja.":"categoría activada."));
   explain(permissions,"Android permite elegir sonido, vibración, avisos emergentes y pantalla de bloqueo. El modo No molestar puede silenciar los avisos.");
   button(permissions,"Configurar notificaciones de Android",this::appSettings);
   button(permissions,"Sonido y categoría de avisos del vehículo",this::channelSettings);
   button(permissions,"Enviar notificación de prueba",this::testNotification);
   explain(permissions,"La prueba solo comprueba las notificaciones del móvil. No conecta con Mazda, no envía órdenes al coche y no se añade al historial.");

   LinearLayout monitor=card("Monitor en segundo plano");
   explain(monitor,MqttSessionService.currentStatus(this));
   PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
   explain(monitor,power!=null&&power.isIgnoringBatteryOptimizations(getPackageName())?
     "Android no aplica la optimización general de batería a esta app.":
     "Revisa las restricciones de batería si los avisos se interrumpen al apagar la pantalla.");
   explain(monitor,"En los ajustes de batería de Mazda Avisos Lab, permite la actividad en segundo plano si tu móvil ofrece esa opción. Después de reiniciar el teléfono o actualizar la app, comprueba el monitor y vuelve a conectar la cuenta si hace falta.");
   button(monitor,"Abrir ajustes de batería de la app",()->androidSettings(
     new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))));
   button(monitor,"Consultar la ayuda",()->startActivity(new Intent(this,HelpActivity.class)));

   JSONArray entries=AlertHistory.read(this);
   LinearLayout history=card("Historial de avisos · "+entries.length());
   explain(history,"Últimos 100 avisos y resultados de controles, del más reciente al más antiguo. El historial no confirma que Android mostrara cada aviso.");
   button(history,"Actualizar historial",this::render);
   if(entries.length()==0)explain(history,"Aún no hay avisos guardados. Aparecerán aquí cuando la app detecte cambios del vehículo o registre un control.");
   for(int i=0;i<entries.length();i++){
     JSONObject entry=entries.optJSONObject(i);if(entry==null)continue;
     LinearLayout event=new LinearLayout(this);event.setOrientation(1);
     event.setPadding(0,dp(14),0,dp(10));history.addView(event);
     event.addView(text(entry.optString("title"),16,true));
     event.addView(text(AlertHistory.when(entry.optLong("time")),12,false));
     TextView body=text(entry.optString("body"),14,false);body.setTextIsSelectable(true);
     body.setPadding(0,dp(5),0,0);event.addView(body);
   }
   if(entries.length()>0)button(history,"Borrar historial…",this::clearHistory);
   ScrollView current=scroll;current.post(()->current.scrollTo(0,previousY));
 }
}
