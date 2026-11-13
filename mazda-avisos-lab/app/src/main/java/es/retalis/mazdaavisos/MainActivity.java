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
import android.view.View;
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
   Notification n=new Notification.Builder(c,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body)).setContentIntent(pi).setAutoCancel(true).build();
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
   TextView t=new TextView(this);t.setText(text);t.setTextSize(15);t.setTextColor(Color.rgb(35,45,55));t.setPadding(0,12,0,12);box.addView(t);
 }
 void button(String text,Runnable r){Button b=new Button(this);b.setText(text);b.setAllCaps(false);box.addView(b);b.setOnClickListener(v->r.run());}
 void show(){
   ScrollView sc=new ScrollView(this);box=new LinearLayout(this);box.setOrientation(1);box.setPadding(24,32,24,36);sc.addView(box);setContentView(sc);
   TextView h=new TextView(this);h.setText("MAZDA AVISOS LAB");h.setTextSize(24);h.setTextColor(Color.rgb(22,48,75));box.addView(h);
   line("Versión 0.2 experimental. Solo detecta mensajes cuando abres Centro de Mensajes en la app Mazda. No consulta el vehículo ni sus servidores.");
   line((enabled()?"✓":"○")+" Accesibilidad: "+(enabled()?"activada":"desactivada"));
   button("1. Activar servicio de accesibilidad",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
   if(Build.VERSION.SDK_INT>=33){
     line((checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED?"✓":"○")+" Notificaciones");
     button("2. Permitir notificaciones",()->{
       if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11);
       else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()));
     });
   }
   button("Probar notificación",()->alert(this,"Prueba Mazda Avisos Lab","Notificación de prueba, no es un aviso real del coche.",99));
   button("Abrir Mazda oficial",()->{Intent i=getPackageManager().getLaunchIntentForPackage("com.cma.mazda.eu");if(i!=null)startActivity(i);else line("La app oficial Mazda no está instalada.");});
   line("Última lectura: "+Data.last(this));
   line("Mensajes almacenados:");
   List<String> messages=Data.history(this);if(messages.isEmpty())line("Sin mensajes todavía.");
   for(String m:messages)line("• "+m);
   button("Actualizar historial",this::show);
   button("Copiar diagnóstico",()->{
     ClipboardManager cb=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
     if(cb!=null)cb.setPrimaryClip(ClipData.newPlainText("Diagnóstico Mazda",Data.diagnostic(this)));
   });
   button("Borrar historial local",()->new android.app.AlertDialog.Builder(this).setTitle("Borrar historial").setMessage("Se borrarán los mensajes y la siguiente lectura no generará alertas de los anteriores.").setPositiveButton("Borrar",(a,b)->{Data.clear(this);show();}).setNegativeButton("Cancelar",null).show());
   line("Privacidad: sin permiso de Internet. El texto se guarda solo en este teléfono. No uses esta app como único sistema de avisos de seguridad.");
 }
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);show();}
}
