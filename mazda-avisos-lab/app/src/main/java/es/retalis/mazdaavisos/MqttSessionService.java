package es.retalis.mazdaavisos;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/**
 * Sesión temporal de sincronización MQTT iniciada explícitamente por el usuario.
 * Los tokens y la configuración de conexión no se almacenan ni sobreviven
 * a la muerte del proceso. Android puede suspenderla por sus límites de FGS.
 */
public final class MqttSessionService extends Service {
 public static final String ACTION_START="es.retalis.mazdaavisos.START_MQTT";
 public static final String ACTION_STOP="es.retalis.mazdaavisos.STOP_MQTT";
 public static final String EXTRA_CONFIG="mqtt_configuration";
 public static final String EXTRA_AUTH="mqtt_credential";
 private static final String NOTIFY_CHANNEL="mazda_mqtt_monitor";
 private static final int NOTIFY_ID=7901;
 private static final int DURATION_SECONDS=90*60;
 private static final String PREF="mqtt_status";
 private final AtomicBoolean running=new AtomicBoolean(false);
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private volatile MqttProbe probe=null;
 private volatile boolean quit=false;
 private long lastNotify=0;

 public static String currentStatus(Context context){
   return context.getSharedPreferences(PREF,MODE_PRIVATE).getString("status","Sin sesión iniciada.");
 }
 private void state(String message){
   getSharedPreferences(PREF,MODE_PRIVATE).edit().putString("status",message).apply();
   long time=SystemClock.elapsedRealtime();
   if(time-lastNotify>=15000){
     lastNotify=time;
     NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
     if(nm!=null)nm.notify(NOTIFY_ID,notification("Monitor activo: "+message.replace('\n',' ')));
   }
 }
 @Override public void onCreate(){super.onCreate();
   NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
   if(nm!=null)nm.createNotificationChannel(new NotificationChannel(NOTIFY_CHANNEL,"Monitor MQTT Mazda (temporal)",NotificationManager.IMPORTANCE_LOW));
 }
 private Notification notification(String text){
   Intent home=new Intent(this,MainActivity.class);
   PendingIntent open=PendingIntent.getActivity(this,12,home,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   Intent stop=new Intent(this,MqttSessionService.class).setAction(ACTION_STOP);
   PendingIntent stopButton=PendingIntent.getService(this,13,stop,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   return new Notification.Builder(this,NOTIFY_CHANNEL)
    .setSmallIcon(android.R.drawable.stat_notify_sync)
    .setContentTitle("Mazda Avisos Lab · MQTT")
    .setContentText(text.length()>110?text.substring(0,110):text)
    .setContentIntent(open)
    .addAction(android.R.drawable.ic_menu_close_clear_cancel,"Detener",stopButton)
    .setOngoing(true).build();
 }
 @Override public int onStartCommand(Intent intent,int flags,int startId){
   String action=intent==null?null:intent.getAction();
   if(ACTION_STOP.equals(action)){shutdown("Monitor detenido por el usuario.");return START_NOT_STICKY;}
   if(!ACTION_START.equals(action))return START_NOT_STICKY;
   if(!running.compareAndSet(false,true))return START_NOT_STICKY;
   String config=intent.getStringExtra(EXTRA_CONFIG),auth=intent.getStringExtra(EXTRA_AUTH);
   if(config==null||auth==null||auth.isEmpty()){shutdown("Falta autorización MQTT.");return START_NOT_STICKY;}
   // Debe invocarse inmediatamente tras el inicio desde una Activity visible.
   try { startForeground(NOTIFY_ID,notification("Escucha de prueba iniciándose · máximo 90 min")); }
   catch(RuntimeException ex){shutdown("Android rechazó el servicio de primer plano.");return START_NOT_STICKY;}
   quit=false;
   worker.execute(()->work(config,auth));
   return START_NOT_STICKY;
 }
 private void work(String config,String auth){
   try{
     final JSONObject cfg=new JSONObject(config);
     long until=SystemClock.elapsedRealtime()+DURATION_SECONDS*1000L;
     int fail=0;
     while(!quit&&SystemClock.elapsedRealtime()<until){
       int remaining=(int)Math.max(1,(until-SystemClock.elapsedRealtime())/1000L);
       try {
         probe=new MqttProbe(this,cfg,auth);
         state("Conectando al servidor Mazda…");
         probe.run(this::state,remaining);
         fail=0;
       }catch(Exception error){
         if(quit)break;
         fail++;
         // No incluir respuestas de red ni tokens en notificaciones o preferencias.
         state("Conexión interrumpida; reintento "+fail);
       }finally{
         MqttProbe current=probe;
         if(current!=null)current.close();
         probe=null;
       }
       if(!quit&&SystemClock.elapsedRealtime()<until){
         long delay=Math.min(300000L,5000L*(1L<<Math.min(6,Math.max(0,fail-1))));
         long end=SystemClock.elapsedRealtime()+delay;
         while(!quit&&SystemClock.elapsedRealtime()<end)SystemClock.sleep(250);
       }
     }
     if(!quit)state("Sesión de 90 minutos finalizada.");
   }catch(Exception e){state("No se pudo iniciar la escucha MQTT.");}
   finally{
     quit=true;
     running.set(false);
     stopForeground(STOP_FOREGROUND_REMOVE);
     stopSelf();
   }
 }
 private void shutdown(String text){
   quit=true;
   MqttProbe current=probe;if(current!=null)current.close();
   state(text);
   stopForeground(STOP_FOREGROUND_REMOVE);
   stopSelf();
 }
 @Override public void onTimeout(int startId,int fgsType){
   shutdown("Android finalizó el tiempo permitido para sincronización.");
 }
 @Override public void onDestroy(){
   quit=true;
   MqttProbe current=probe;if(current!=null)current.close();
   worker.shutdownNow();
   super.onDestroy();
 }
 @Override public IBinder onBind(Intent intent){return null;}
}