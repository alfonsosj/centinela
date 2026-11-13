package es.retalis.mazdaavisos;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;

/**
 * Sesión de escucha temporal, iniciada por el usuario.
 * No almacena credenciales ni contenidos MQTT; conserva solo diagnósticos.
 * Uso especial: recepción continua de eventos de estado del vehículo en tiempo real.
 * Android puede finalizar el servicio por restricciones de batería, aunque no tenga temporizador propio.
 */
public final class MqttSessionService extends Service {
 public static final String ACTION_START="es.retalis.mazdaavisos.START_MQTT";
 public static final String ACTION_STOP="es.retalis.mazdaavisos.STOP_MQTT";
 public static final String EXTRA_CONFIG="mqtt_configuration";
 public static final String EXTRA_AUTH="mqtt_credential";
 private static final String NOTIFY_CHANNEL="mazda_mqtt_monitor";
 private static final int NOTIFY_ID=7901;
 private static final int MQTT_RECONNECT_CYCLE_SECONDS=4*60*60;
 private static final String PREF="mqtt_status";
 private final AtomicBoolean running=new AtomicBoolean(false);
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final Object changeLock=new Object();
 private volatile MqttProbe probe=null;
 private volatile boolean quit=false, networkChanged=false;
 private volatile Network activeNetwork=null;
 private ConnectivityManager connectivity;
 private ConnectivityManager.NetworkCallback networkCallback;
 private long lastNotify=0;
 private int reconnects=0;
 private Integer lastDoorLock=null;

 public static String currentStatus(Context context){
   android.content.SharedPreferences p=context.getSharedPreferences(PREF,MODE_PRIVATE);
   String last=p.getString("status","Sin sesión iniciada.");
   long at=p.getLong("updated_at",0);
   boolean active=p.getBoolean("active",false);
   if(at==0)return last;
   long age=Math.max(0,System.currentTimeMillis()-at);
   String ageText=age<60000?"hace "+Math.max(1,age/1000)+" s":"hace "+(age/60000)+" min";
   String time=new SimpleDateFormat("dd/MM HH:mm",Locale.getDefault()).format(new Date(at));
   if(active&&age>90000){
     return "SIN CONFIRMACIÓN de conexión desde "+ageText+
       ". El servicio puede haberse detenido. Última señal "+time+". "+last;
   }
   return (active?"Sesión activa (":"Última sesión (")+ageText+"): "+last;
 }
 private void state(String value,boolean active){
   getSharedPreferences(PREF,MODE_PRIVATE).edit()
     .putString("status",value)
     .putBoolean("active",active)
     .putLong("updated_at",System.currentTimeMillis())
     .putInt("reconnects",reconnects)
     .apply();
   if(!active)return;
   long time=SystemClock.elapsedRealtime();
   if(time-lastNotify>=15000){
     lastNotify=time;
     NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
     if(nm!=null)nm.notify(NOTIFY_ID,notification(value.replace('\n',' ')));
   }
 }
 private void wake(){
   synchronized(changeLock){changeLock.notifyAll();}
 }
 private final class NetworkListener extends ConnectivityManager.NetworkCallback {
   @Override public void onAvailable(Network network){
     Network previous=activeNetwork;
     activeNetwork=network;
     if(previous!=null&&!previous.equals(network)){
       networkChanged=true;
       MqttProbe p=probe;if(p!=null)p.close();
     }
     wake();
   }
   @Override public void onLost(Network network){
     if(network.equals(activeNetwork)){
       activeNetwork=null;
       networkChanged=true;
       MqttProbe p=probe;if(p!=null)p.close();
       wake();
     }
   }
 }
 @Override public void onCreate(){
   super.onCreate();
   NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
   if(nm!=null)nm.createNotificationChannel(new NotificationChannel(NOTIFY_CHANNEL,
     "Monitor MQTT Mazda (temporal)",NotificationManager.IMPORTANCE_LOW));
   connectivity=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
   if(connectivity!=null){
     try {
       activeNetwork=connectivity.getActiveNetwork();
       networkCallback=new NetworkListener();
       connectivity.registerDefaultNetworkCallback(networkCallback);
     }catch(RuntimeException e){networkCallback=null;}
   }
 }
 private boolean online(){
   if(connectivity==null)return true;
   Network network=connectivity.getActiveNetwork();
   if(network==null)return false;
   NetworkCapabilities caps=connectivity.getNetworkCapabilities(network);
   return caps!=null&&caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
 }
 private String networkLabel(){
   if(connectivity==null)return "Red sin información";
   try{
     Network n=connectivity.getActiveNetwork();
     NetworkCapabilities cap=n==null?null:connectivity.getNetworkCapabilities(n);
     if(cap==null)return "Sin red";
     if(cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))return "Wi-Fi";
     if(cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))return "Datos móviles";
     if(cap.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))return "Ethernet";
     return "Otra red";
   }catch(RuntimeException ignored){return "Red desconocida";}
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
   String config=intent.getStringExtra(EXTRA_CONFIG);
   String auth=intent.getStringExtra(EXTRA_AUTH);
   if(config==null||auth==null||auth.isEmpty()){shutdown("Falta autorización MQTT.");return START_NOT_STICKY;}
   try{startForeground(NOTIFY_ID,notification("Conectando con Mazda · monitor continuo experimental"));}
   catch(RuntimeException e){shutdown("Android rechazó iniciar el monitor.");return START_NOT_STICKY;}
   quit=false;
   state("Iniciando escucha MQTT",true);
   worker.execute(()->work(config,auth));
   return START_NOT_STICKY;
 }
 private void delay(long millis){
   long until=SystemClock.elapsedRealtime()+millis;
   synchronized(changeLock){
     while(!quit&&!networkChanged&&SystemClock.elapsedRealtime()<until){
       try{changeLock.wait(Math.min(4000L,until-SystemClock.elapsedRealtime()));}
       catch(InterruptedException e){Thread.currentThread().interrupt();return;}
     }
   }
 }
 private void work(String config,String auth){
   String finishReason="Sesión finalizada.";
   try {
     JSONObject cfg=new JSONObject(config);
     // Sin límite propio de 90 minutos. El servicio continúa mientras Android lo autorice.
     // Cada 4 h se recrea la sesión MQTT con autorización aún válida; si expira el token
     // se informa de la interrupción, sin guardar contraseñas ni tokens en disco.
     int failures=0;
     while(!quit){
       if(!online()){
         state("Sin conexión de red. Esperando Wi-Fi o datos móviles...",true);
         delay(10000);
         continue;
       }
       networkChanged=false;
       try{
         MqttProbe p=new MqttProbe(this,cfg,auth);
         probe=p;
         p.setInitialDoorLock(lastDoorLock);
         state("Conectando por "+networkLabel()+"...",true);
         p.run(message->state(networkLabel()+" · "+message,true),MQTT_RECONNECT_CYCLE_SECONDS);
         failures=0;
       }catch(Exception e){
         if(quit)break;
         if(networkChanged){
           failures=0;
           state("Cambio de red detectado. Reconectando...",true);
         }else{
           failures++;
           reconnects++;
           // No exponer textos de excepciones: pueden contener datos del servidor.
           String kind=e instanceof java.net.SocketTimeoutException?"Tiempo de espera":
             e instanceof java.io.IOException?"Conexión interrumpida":"Fallo de sesión MQTT";
           state(kind+". Reintento "+reconnects+" ("+networkLabel()+")",true);
         }
       }finally{
         MqttProbe p=probe;probe=null;
         if(p!=null){lastDoorLock=p.getLastDoorLock();p.close();}
       }
       if(quit)break;
       if(networkChanged){networkChanged=false;continue;}
       long pause=Math.min(90000L,3000L*(1L<<Math.min(5,Math.max(0,failures-1))));
       delay(pause);
     }
     if(!quit)finishReason="Monitor finalizado.";
   }catch(Exception e){finishReason="No se pudo continuar la escucha MQTT.";}
   finally{
     quit=true;
     running.set(false);
     state(finishReason,false);
     stopForeground(STOP_FOREGROUND_REMOVE);
     stopSelf();
   }
 }
 private void shutdown(String message){
   quit=true;
   MqttProbe p=probe;if(p!=null)p.close();
   wake();
   state(message,false);
   stopForeground(STOP_FOREGROUND_REMOVE);
   stopSelf();
 }
 @Override public void onTimeout(int startId,int fgsType){
   shutdown("Tiempo máximo de servicio permitido por Android alcanzado.");
 }
 @Override public void onDestroy(){
   quit=true;
   MqttProbe p=probe;if(p!=null)p.close();
   wake();
   if(connectivity!=null&&networkCallback!=null){
     try{connectivity.unregisterNetworkCallback(networkCallback);}catch(RuntimeException ignored){}
   }
   worker.shutdownNow();
   if(running.get())state("Servicio finalizado por Android o cierre de la aplicación.",false);
   super.onDestroy();
 }
 @Override public IBinder onBind(Intent intent){return null;}
}
