package es.retalis.mazdaavisos;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;

public class MainActivity extends Activity {
 static final String CHANNEL="mazda_lab_alerts";
 LinearLayout box;
 @Override public void onCreate(Bundle s){super.onCreate(s);show();}
 @Override public void onResume(){super.onResume();show();}
 static void alert(android.content.Context c,String title,String body,int id){
   NotificationManager nm=(NotificationManager)c.getSystemService(NOTIFICATION_SERVICE);
   if(nm==null)return;
   if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(new NotificationChannel(CHANNEL,"Avisos Mazda experimentales",NotificationManager.IMPORTANCE_HIGH));
   if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
   Intent i=new Intent(c,MainActivity.class);
   PendingIntent pi=PendingIntent.getActivity(c,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   Notification n=new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
     .setContentTitle(title).setContentText(body)
     .setStyle(new Notification.BigTextStyle().bigText(body))
     .setContentIntent(pi).setAutoCancel(true).build();
   nm.notify(id,n);
 }
 boolean enabled(){
   String current=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
   String wanted=new ComponentName(this,MazdaMonitor.class).flattenToString();
   if(current==null)return false;
   for(String s:current.split(":"))if(s.equalsIgnoreCase(wanted))return true;
   return false;
 }
 void line(String text){
   TextView t=new TextView(this);t.setText(text);t.setTextSize(15);
   t.setTextColor(Color.rgb(35,45,55));t.setPadding(0,12,0,12);box.addView(t);
 }
 void button(String text,Runnable r){
   Button b=new Button(this);b.setText(text);b.setAllCaps(false);
   box.addView(b);b.setOnClickListener(v->r.run());
 }
 private void launchMazda(boolean scan){
   if(scan&&!enabled()){line("Primero activa el permiso de accesibilidad.");return;}
   Intent i=getPackageManager().getLaunchIntentForPackage("com.cma.mazda.eu");
   if(i==null){line("La app Mazda europea no está instalada.");return;}
   if(scan){MazdaMonitor.requestSingleCheck();Data.status(this,"Comprobación solicitada; abriendo Mazda...");}
   else MazdaMonitor.stopCheck();
   startActivity(i);
 }
 void show(){
   ScrollView sc=new ScrollView(this);box=new LinearLayout(this);
   box.setOrientation(1);box.setPadding(24,32,24,36);sc.addView(box);setContentView(sc);
   TextView h=new TextView(this);h.setText("MAZDA AVISOS LAB");
   h.setTextSize(24);h.setTextColor(Color.rgb(22,48,75));box.addView(h);
   line("Versión 0.7 experimental: permite escucha MQTT durante 90 minutos con la pantalla apagada y reconexión mientras esté activa. No es vigilancia permanente ni sustituye una alarma del coche.");
   line((enabled()?"✓":"○")+" Accesibilidad: "+(enabled()?"activada":"desactivada"));
   button("1. Activar accesibilidad",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
   if(Build.VERSION.SDK_INT>=33){
     line((checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED?"✓":"○")+" Notificaciones");
     button("2. Permitir notificaciones",()->{
       if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
         requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);
       else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
     });
   }
   line("Nuevo: en el panel de ajustes rápidos de Samsung, pulsa Editar botones y añade «Comprobar Mazda». Funciona cuando tú pulsas el botón, con el móvil desbloqueado.");
   line("Monitor MQTT de prueba: "+MqttSessionService.currentStatus(this));
   button("Configurar / iniciar MQTT con cuenta secundaria",()->startActivity(new Intent(this,ConnectionTestActivity.class)));
   button("Detener monitor MQTT",()->{startService(new Intent(this,MqttSessionService.class).setAction(MqttSessionService.ACTION_STOP));line("Detención solicitada.");});
   button("Comprobar mensajes de Mazda ahora (PRUEBA)",()->launchMazda(true));
   line("Abre Mazda e intenta pulsar solo el botón de mensajes si Android identifica su ID. Si no lo encuentra, toca el botón de mensajes manualmente; la app guardará un diagnóstico.");
   button("Abrir Mazda sin automatización",()->launchMazda(false));
   button("Probar notificación",()->alert(this,"Prueba Mazda Avisos Lab","Notificación de prueba, no es un aviso real del coche.",99));
   line("Última lectura: "+Data.last(this));
   line("Mensajes almacenados:");
   List<String> messages=Data.history(this);if(messages.isEmpty())line("Sin mensajes todavía.");
   for(String m:messages)line("• "+m);
   button("Actualizar historial",this::show);
   button("Copiar diagnóstico técnico",()->{
     ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
     if(cb!=null)cb.setPrimaryClip(ClipData.newPlainText("Diagnóstico Mazda",Data.technicalDiagnostic(this)));
   });
   button("Borrar historial local",()->new android.app.AlertDialog.Builder(this).setTitle("Borrar historial")
     .setMessage("Se borrarán los mensajes. La siguiente lectura será importación sin avisos.")
     .setPositiveButton("Borrar",(a,b)->{Data.clear(this);show();})
     .setNegativeButton("Cancelar",null).show());
   line("Privacidad: MQTT se conecta a servicios Mazda Europa con cuenta secundaria. Credenciales y tokens solo permanecen en memoria durante la sesión; no se guardan. Los mensajes se almacenan localmente. La navegación automática se activa únicamente al pulsar el botón de prueba y caduca en 30 segundos. No sustituye las alertas de seguridad del vehículo.");
 }
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
   super.onRequestPermissionsResult(request,permissions,results);show();
 }
}
