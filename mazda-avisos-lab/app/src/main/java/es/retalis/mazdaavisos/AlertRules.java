package es.retalis.mazdaavisos;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.SystemClock;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Reglas locales para avisos de cambios en informes MQTT parciales de Mazda.
 * Un primer valor solo fija referencia; no genera alarma. No usa GPS, cámaras
 * ni presume que una apertura sea acceso no autorizado.
 */
final class AlertRules {
 static final String PREF="mazda_alert_rules";
 static final String LOCKS="locks", DOORS="doors", WINDOWS="windows", TRUNK="trunk";
 static final String CHARGE="charge", CABLE="cable", SOC="soc", TEMP="temp";
 static final String SENTINEL="sentinel", PARK_DROP="park_drop";
 static final String PARKED_OPEN_REMINDER="parked_open_reminder";
 static final String LOCKED_WINDOW_REMINDER="locked_window_reminder";
 static final String LOW_SOC="low_soc", STEP_SOC="step_soc", HIGH_TEMP="high_temp";
 static final String LOW_TEMP="low_temp", DROP_POINTS="drop_points";
 private static final AtomicInteger ID=new AtomicInteger(6100);
 private final Context app;
 private final Map<String,Integer> states=new HashMap<>();
 private Double lastSoc=null, lastTemp=null, socStepAnchor=null, parkedAnchor=null;
 private boolean highTriggered=false,lowTriggered=false;
 private int lockChanges=0,totalAlerts=0;
 private Integer currentCharge=null;
 private boolean parkKnown=false,parked=false;
 private boolean lockKnown=false,locked=false;
 private long lastBundleAt=0;
 // Reminders are timed with a monotonic clock, and require fresh event evidence
 // in this MQTT connection; a reconnection never reuses previous open states.
 private long parkedObservedAt=0,lockObservedAt=0,reminderStartedAt=0,lastReminderAt=0;
 private final long[] doorObservedAt=new long[4];
 private final long[] windowObservedAt=new long[4];
 private long windowReminderStartedAt=0,lastWindowReminderAt=0;
 private int windowReminderCount=0;
 private static final int WINDOW_REMINDER_NOTIFICATION_ID=6250;
 private static final long REMINDER_INTERVAL_MS=60_000L;
 private static final String[] DOOR_KEYS={"driverDoor","passengerDoor","leftRearDoor","rightRearDoor"};
 private static final String[] WINDOW_KEYS={"diverWindow","passengerWindow","leftRearWindow","rightRearWindow"};
 private static final String[] POSITIONS={"conductor","acompañante","trasera izquierda","trasera derecha"};

 AlertRules(Context context){app=context.getApplicationContext();}
 static SharedPreferences pref(Context c){return c.getSharedPreferences(PREF,Context.MODE_PRIVATE);}
 static boolean enabled(Context c,String key){return pref(c).getBoolean(key,defaultEnabled(key));}
 static boolean defaultEnabled(String key){
   return LOCKS.equals(key)||WINDOWS.equals(key)||TRUNK.equals(key)||
     CHARGE.equals(key)||CABLE.equals(key)||SOC.equals(key)||TEMP.equals(key);
 }
 static int limit(Context c,String key){
   int fallback=LOW_SOC.equals(key)?20:STEP_SOC.equals(key)?10:HIGH_TEMP.equals(key)?40:
     LOW_TEMP.equals(key)?5:DROP_POINTS.equals(key)?3:0;
   return pref(c).getInt(key,fallback);
 }
 private void notifyUser(String title,String text){
   int id=ID.incrementAndGet();
   if(id>Integer.MAX_VALUE-100)ID.set(6100);
   MainActivity.alert(app,title,text,id);
   totalAlerts++;
   AlertHistory.add(app,title,text);
 }
 private Integer code(JSONObject values,String key,int min,int max){
   Object value=values.opt(key);
   if(value==null||value==JSONObject.NULL||value instanceof Boolean)return null;
   try{
     double d=Double.parseDouble(String.valueOf(value));
     if(!Double.isFinite(d)||d!=Math.rint(d)||d<min||d>max)return null;
     return (int)d;
   }catch(Exception ignored){return null;}
 }
 private Double number(JSONObject values,String key,double min,double max){
   Object value=values.opt(key);
   if(value==null||value==JSONObject.NULL||value instanceof Boolean)return null;
   try{double d=Double.parseDouble(String.valueOf(value));
     return Double.isFinite(d)&&d>=min&&d<=max?d:null;
   }catch(Exception ignored){return null;}
 }
 private Integer update(String key,Integer value){
   if(value==null)return null;
   Integer previous=states.put(key,value);
   return previous;
 }
 private void binary(JSONObject raw,String key,String label,String category,boolean isTrunk){
   Integer code=code(raw,key,0,100);if(code==null)return;
   int now=code==0?0:1;
   for(int i=0;i<DOOR_KEYS.length;i++){
     if(DOOR_KEYS[i].equals(key))doorObservedAt[i]=SystemClock.elapsedRealtime();
   }
   Integer before=update(key,now);
   if(before==null||before==now)return;
   boolean armed=sentinelArmed();
   String action=now==1?"abierta":"cerrada";
   if(now==1&&armed){
     notifyUser("Centinela: "+label+" abierta",
       "Apertura observada por los datos del coche. No confirma intrusión.");
   }else if(enabled(app,category)){
     notifyUser(label+" "+action,"Cambio recibido por MQTT en el Mazda.");
   }
 }
 void ingest(JSONObject raw){
   if(raw==null)return;
   VehicleSnapshot.observe(app,raw);
   Integer lock=code(raw,"driverDoorLock",0,1);
   if(lock!=null){
     Integer prior=update("driverDoorLock",lock);
     lockKnown=true;locked=lock==0;lockObservedAt=SystemClock.elapsedRealtime();
     if(prior!=null&&prior.intValue()!=lock.intValue()){
       lockChanges++;
       if(lock==1&&enabled(app,SENTINEL)&&prior==0){
         notifyUser("Centinela: vehículo desbloqueado",
           "Se ha detectado desbloqueo. Puede ser una acción autorizada.");
       }else if(enabled(app,LOCKS)){
         notifyUser(lock==0?"Puertas bloqueadas":"Puertas desbloqueadas",
           "Cambio detectado por MQTT en el Mazda.");
       }
     }
   }
   for(int i=0;i<DOOR_KEYS.length;i++)binary(raw,DOOR_KEYS[i],"Puerta "+POSITIONS[i],DOORS,false);
   binary(raw,"trunk","Maletero",TRUNK,true);
   windows(raw);
   charge(raw);
   battery(raw);
   temperature(raw);
   tickReminder();
 }
 private void windows(JSONObject raw){
   java.util.List<String> opened=new java.util.ArrayList<>();
   java.util.List<String> closed=new java.util.ArrayList<>();
   for(int i=0;i<WINDOW_KEYS.length;i++){
     Integer v=code(raw,WINDOW_KEYS[i],0,100);
     if(v==null)continue;
     windowObservedAt[i]=SystemClock.elapsedRealtime();
     int now=v==0?0:1;
     Integer old=update(WINDOW_KEYS[i],now);
     if(old==null||old==now)continue;
     if(now==1)opened.add(POSITIONS[i]);
     else closed.add(POSITIONS[i]);
   }
   boolean armed=sentinelArmed();
   if(!opened.isEmpty()&&(armed||enabled(app,WINDOWS))){
     String label=opened.size()==1?"Ventana "+opened.get(0)+" abierta":opened.size()+" ventanas abiertas";
     notifyUser((armed?"Centinela: ":"")+label,
       "Ventanas abiertas: "+String.join(", ",opened)+
       (armed?". Apertura observada, no confirma intrusión.":". Cambio recibido por MQTT."));
   }
   if(!closed.isEmpty()&&enabled(app,WINDOWS)){
     String label=closed.size()==1?"Ventana "+closed.get(0)+" cerrada":closed.size()+" ventanas cerradas";
     notifyUser(label,"Ventanas cerradas: "+String.join(", ",closed)+".");
   }
 }
 private void charge(JSONObject raw){
   Integer ac=code(raw,"acChargeGunConnectionState",0,1);
   Integer dc=code(raw,"dcDhargeGunConnectionState",0,1);
   // Ambos canales se interpretan por separado: no inferir desconexión
   // global mientras falta un dato del otro tipo de conector.
   if(ac!=null){
     Integer old=update("acPlug",ac);
     if(old!=null&&!old.equals(ac)&&enabled(app,CABLE))
       notifyUser(ac==1?"Cable de carga AC conectado":"Cable de carga AC desconectado",
         "Estado de conexión de carga recibido por MQTT.");
   }
   if(dc!=null){
     Integer old=update("dcPlug",dc);
     if(old!=null&&!old.equals(dc)&&enabled(app,CABLE))
       notifyUser(dc==1?"Cable de carga DC conectado":"Cable de carga DC desconectado",
         "Estado de conexión de carga recibido por MQTT.");
   }
   Integer rawStatus=code(raw,"batteryChargeStatus",0,10);
   boolean fromBattery=rawStatus!=null;
   if(rawStatus==null)rawStatus=code(raw,"chargeStatus",0,10);
   Integer mode=null;
   if(rawStatus!=null){
     if(fromBattery){
       if(rawStatus==0)mode=0;
       if(rawStatus==1)mode=1;
       if(rawStatus==4)mode=2;
     }else{
       if(rawStatus==1)mode=1;
       if(rawStatus==4)mode=2;
       // Raw=3 ambiguous in the community mapping: intentionally ignored.
     }
   }
   if(mode!=null){
     Integer before=currentCharge;currentCharge=mode;
     if(before!=null&&!before.equals(mode)&&enabled(app,CHARGE)){
       String label=mode==1?"Carga iniciada":mode==2?"Carga en espera":"Carga detenida";
       notifyUser(label,"Estado MQTT de carga cambiado (códigos aún en validación).");
     }
   }
   Integer carStatus=code(raw,"carStatus",1,2);
   if(carStatus!=null){parked=carStatus==2;parkKnown=true;parkedObservedAt=SystemClock.elapsedRealtime();if(!parked)parkedAnchor=null;}
 }
 private void battery(JSONObject raw){
   Double soc=number(raw,"soc",0,100);
   if(soc==null)soc=number(raw,"socDsp",0,100);
   if(soc==null)soc=number(raw,"remainPower",0,100);
   if(soc==null)return;
   Double before=lastSoc;
   lastSoc=soc;
   if(socStepAnchor==null)socStepAnchor=soc;
   if(before!=null){
     int threshold=limit(app,LOW_SOC);
     if(before>threshold&&soc<=threshold&&enabled(app,SOC))
       notifyUser("Batería baja: "+pct(soc),
         "El nivel de carga ha descendido hasta el umbral configurado.");
     int step=limit(app,STEP_SOC);
     if(enabled(app,SOC)&&Math.abs(soc-socStepAnchor)>=step){
       double delta=soc-socStepAnchor;
       notifyUser(delta>0?"Batería subiendo: "+pct(soc):"Batería bajando: "+pct(soc),
         "Variación de "+String.format(Locale.US,"%.0f",Math.abs(delta))+
         " puntos desde el último aviso. No indica por sí sola una avería.");
       socStepAnchor=soc;
     }
   }
   if(!parkKnown||!parked){
     parkedAnchor=null;
   }else{
     if(parkedAnchor==null)parkedAnchor=soc;
     int loss=limit(app,DROP_POINTS);
     if(enabled(app,PARK_DROP)&&parkedAnchor-soc>=loss){
       notifyUser("Descenso de batería aparcado: "+pct(soc),
         "Caída de "+String.format(Locale.US,"%.0f",parkedAnchor-soc)+
         " puntos con estado aparcado. Comprobar si hay consumos normales.");
       parkedAnchor=soc;
     }
     if(soc>parkedAnchor)parkedAnchor=soc;
   }
 }
 private static String pct(double value){
   return String.format(Locale.getDefault(),"%.0f %%",value);
 }
 private void temperature(JSONObject raw){
   Double c=number(raw,"vehicleTemperature",-30,80);
   if(c==null||c==0)return;
   boolean first=lastTemp==null;
   lastTemp=c;
   int upper=limit(app,HIGH_TEMP),lower=limit(app,LOW_TEMP);
   if(first){highTriggered=c>=upper;lowTriggered=c<=lower;return;}
   if(c<upper-3)highTriggered=false;
   if(c>lower+3)lowTriggered=false;
   if(!enabled(app,TEMP))return;
   if(c>=upper&&!highTriggered){
     highTriggered=true;
     notifyUser("Temperatura interior alta: "+Math.round(c)+" °C",
       "El sensor del coche supera "+upper+" °C. No indica presencia de ocupantes.");
   }
   if(c<=lower&&!lowTriggered){
     lowTriggered=true;
     notifyUser("Temperatura interior baja: "+Math.round(c)+" °C",
       "El sensor del coche está a "+lower+" °C o menos.");
   }
 }

 /**
  * Called after a new MQTT broker/app handshake. Last state before a network
  * interruption must not be reused to trigger repeated open-door reminders.
  */
 void onMqttConnected(){
   parkedObservedAt=0;lockObservedAt=0;
   java.util.Arrays.fill(doorObservedAt,0);
   java.util.Arrays.fill(windowObservedAt,0);
   windowReminderStartedAt=0;lastWindowReminderAt=0;windowReminderCount=0;
   reminderStartedAt=0;lastReminderAt=0;
   parkKnown=false;lockKnown=false;
 }
 /**
  * Called on the connected socket loop, even without any new MQTT reports.
  * First reminder follows one continuous minute of parked + open/unlocked.
  */
 void tickReminder(){
   tickParkedOpenReminder();
   tickLockedWindowReminder();
 }
 private void tickParkedOpenReminder(){
   if(!enabled(app,PARKED_OPEN_REMINDER)||parkedObservedAt==0||!parked){
     reminderStartedAt=0;lastReminderAt=0;return;
   }
   boolean unlocked=lockObservedAt>0&&lockKnown&&!locked;
   java.util.List<String> openDoors=new java.util.ArrayList<>();
   for(int i=0;i<DOOR_KEYS.length;i++){
     if(doorObservedAt[i]>0&&Integer.valueOf(1).equals(states.get(DOOR_KEYS[i])))
       openDoors.add(POSITIONS[i]);
   }
   if(!unlocked&&openDoors.isEmpty()){
     reminderStartedAt=0;lastReminderAt=0;return;
   }
   long now=SystemClock.elapsedRealtime();
   if(reminderStartedAt==0){reminderStartedAt=now;return;}
   if(now-reminderStartedAt<REMINDER_INTERVAL_MS||
      lastReminderAt!=0&&now-lastReminderAt<REMINDER_INTERVAL_MS)return;
   String reason=unlocked?"El vehículo sigue desbloqueado":"";
   if(!openDoors.isEmpty()){
     if(!reason.isEmpty())reason+=" y ";
     reason+=openDoors.size()==1?"puerta "+openDoors.get(0)+" abierta":
       openDoors.size()+" puertas abiertas ("+String.join(", ",openDoors)+")";
   }
   notifyUser("Recordatorio: Mazda aparcado sin asegurar",
     reason+". Según la última lectura MQTT; comprueba y asegura el coche.");
   lastReminderAt=now;
 }
 /**
  * Independent of parked/unlocked reminders. Starts when MQTT has actually
  * observed both a locked vehicle and at least one open window in this session.
  * No new MQTT message is needed for the periodic interval to elapse.
  */
 private void tickLockedWindowReminder(){
   if(!enabled(app,LOCKED_WINDOW_REMINDER)||!lockKnown||!locked||lockObservedAt==0){
     resetLockedWindowReminder();return;
   }
   java.util.List<String> opened=new java.util.ArrayList<>();
   for(int i=0;i<WINDOW_KEYS.length;i++){
     if(windowObservedAt[i]>0&&Integer.valueOf(1).equals(states.get(WINDOW_KEYS[i])))
       opened.add(POSITIONS[i]);
   }
   if(opened.isEmpty()){resetLockedWindowReminder();return;}
   long now=SystemClock.elapsedRealtime();
   if(windowReminderStartedAt==0){windowReminderStartedAt=now;return;}
   if(now-windowReminderStartedAt<REMINDER_INTERVAL_MS||
      lastWindowReminderAt!=0&&now-lastWindowReminderAt<REMINDER_INTERVAL_MS)return;
   windowReminderCount++;
   String which=opened.size()==1?"la ventanilla del "+opened.get(0):
     opened.size()+" ventanillas ("+String.join(", ",opened)+")";
   String title="Mazda bloqueado con ventanillas abiertas";
   String body="El coche sigue bloqueado, pero "+(opened.size()==1?"tiene abierta ":"tiene abiertas ")+which+
     ". Recordatorio "+windowReminderCount+". Comprueba el coche y cierra las ventanillas.";
   // Reuse one notification slot to avoid dozens of identical notifications in
   // Android's notification shade; the content changes on every reminder.
   MainActivity.alert(app,title,body,WINDOW_REMINDER_NOTIFICATION_ID);
   AlertHistory.add(app,title,body);
   totalAlerts++;
   lastWindowReminderAt=now;
 }
 private void resetLockedWindowReminder(){
   windowReminderStartedAt=0;
   lastWindowReminderAt=0;
   windowReminderCount=0;
 }
 private boolean sentinelArmed(){return enabled(app,SENTINEL)&&lockKnown&&locked&&parkKnown&&parked;}
 int getLockChanges(){return lockChanges;}
 Integer getLastDoorLock(){return states.get("driverDoorLock");}
 void setInitialDoorLock(Integer old){if(old!=null&&(old==0||old==1))states.put("driverDoorLock",old);}
 String summary(){
   return "Avisos generados: "+totalAlerts+
    "\nBatería: "+(lastSoc==null?"sin dato":pct(lastSoc))+
    "\nTemperatura interior: "+(lastTemp==null?"sin dato":Math.round(lastTemp)+" °C")+
    "\nCentinela básico: "+(sentinelArmed()?"armado (aparcado y cerrado)":enabled(app,SENTINEL)?"en espera de aparcado y cierre":"desactivado");
 }
}
