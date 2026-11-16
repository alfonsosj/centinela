package es.retalis.mazdaavisos;

import org.json.JSONObject;
import java.util.Arrays;

/** Pure, synchronized eligibility policy. It can request only a lock, never an unlock. */
final class AutoLockPolicy {
 static final long WAIT_MS=60_000L,SETTLE_MS=10_000L,FRESH_MS=120_000L;
 static final int MAX_ATTEMPTS=3;
 private static final String[] CLOSURES={"driverDoor","passengerDoor","leftRearDoor","rightRearDoor","trunk"};
 private final int[] closures={-1,-1,-1,-1,-1};
 private final long[] seen={-1,-1,-1,-1,-1};
 private int parked=-1,lock=-1,attempts;
 private long parkedAt=-1,lockAt=-1,unlockedSince=-1,closedSince=-1,lastAttemptAt=-1,version,sequence;
 private Attempt active;
 private boolean connected,enabled;
 static final class Attempt {
   final long id,version;
   private Attempt(long id,long version){this.id=id;this.version=version;}
 }
 static Integer code(JSONObject object,String key,int low,int high){
   Object value=object==null?null:object.opt(key);
   if(value==null||value==JSONObject.NULL||value instanceof Boolean)return null;
   try{double number=Double.parseDouble(String.valueOf(value));
     return Double.isFinite(number)&&number==Math.rint(number)&&number>=low&&number<=high?(int)number:null;
   }catch(NumberFormatException ignored){return null;}
 }
 synchronized void connection(boolean online){
   connected=online;version++;active=null;parked=-1;lock=-1;parkedAt=-1;lockAt=-1;
   Arrays.fill(closures,-1);Arrays.fill(seen,-1);resetCycle();closedSince=-1;
 }
 synchronized void enabled(boolean value,long now){
   if(enabled==value)return;
   enabled=value;version++;active=null;resetCycle();closedSince=-1;
   updateTimers(now);
 }
 synchronized void authorizationChanged(long now){version++;active=null;resetCycle();updateTimers(now);}
 private void resetCycle(){unlockedSince=-1;lastAttemptAt=-1;attempts=0;}
 synchronized void observe(JSONObject raw,long now){
   Integer car=code(raw,"carStatus",1,2);
   if(car!=null){if(parked!=car){version++;if(car!=2)resetCycle();}parked=car;parkedAt=now;}
   Integer nextLock=code(raw,"driverDoorLock",0,1);
   if(nextLock!=null){if(lock!=nextLock){version++;if(nextLock==0)resetCycle();}lock=nextLock;lockAt=now;}
   for(int i=0;i<closures.length;i++){
     Integer value=code(raw,CLOSURES[i],0,100);if(value==null)continue;
     int state=value==0?0:1;
     if(state==1&&closures[i]!=1)version++;
     closures[i]=state;seen[i]=now;
   }
   updateTimers(now);
 }
 private void updateTimers(long now){
   if(enabled&&connected&&parked==2&&lock==1){if(unlockedSince<0)unlockedSince=now;}
   else unlockedSince=-1;
   boolean closed=true;for(int value:closures)closed&=value==0;
   if(closed){if(closedSince<0)closedSince=now;}else closedSince=-1;
 }
 private boolean ready(long now){
   if(!connected||!enabled||parked!=2||lock!=1||unlockedSince<0||now-unlockedSince<WAIT_MS||
      closedSince<0||now-closedSince<SETTLE_MS||now-parkedAt>FRESH_MS||now-lockAt>FRESH_MS)return false;
   for(int i=0;i<closures.length;i++)if(closures[i]!=0||seen[i]<0||now-seen[i]>FRESH_MS)return false;
   return true;
 }
 synchronized Attempt claim(long now){
   if(active!=null||!ready(now)||attempts>=MAX_ATTEMPTS||lastAttemptAt>=0&&now-lastAttemptAt<WAIT_MS)return null;
   active=new Attempt(++sequence,version);attempts++;lastAttemptAt=now;return active;
 }
 synchronized boolean permits(Attempt attempt,long now){
   return attempt!=null&&active==attempt&&attempt.version==version&&ready(now);
 }
 synchronized void release(Attempt attempt){
   if(active!=attempt)return;active=null;attempts=Math.max(0,attempts-1);lastAttemptAt=-1;
 }
 synchronized boolean finish(Attempt attempt,boolean confirmed,long now){
   if(active!=attempt)return false;
   active=null;
   if(confirmed&&(attempt.version==version||lock==0)){
     lock=0;lockAt=now;version++;resetCycle();return true;
   }
   return false;
 }
 synchronized int attempts(){return attempts;}
 synchronized String waitingReason(long now){
   if(!enabled)return "";
   if(!connected)return "Cierre automático en espera de conexión.";
   if(attempts>=MAX_ATTEMPTS&&active==null)return "Tres intentos sin confirmar cierre: comprueba el coche.";
   for(int value:closures)if(value==1)return "El cierre automático espera a que cierres las puertas y el maletero.";
   for(int i=0;i<closures.length;i++)if(closures[i]<0||seen[i]<0||now-seen[i]>FRESH_MS)
     return "Faltan datos recientes de puertas o maletero para enviar el cierre automático.";
   if(parkedAt<0||lockAt<0||now-parkedAt>FRESH_MS||now-lockAt>FRESH_MS)return "El cierre automático espera estados recientes de aparcado y desbloqueo.";
   if(active!=null)return "Cierre automático en curso; todavía no confirmado.";
   if(closedSince>=0&&now-closedSince<SETTLE_MS)return "Esperando 10 segundos tras cerrar las puertas.";
   return "Se intentará el cierre automático si siguen cumpliéndose las condiciones.";
 }
}
