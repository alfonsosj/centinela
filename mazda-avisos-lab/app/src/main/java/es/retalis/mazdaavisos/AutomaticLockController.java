package es.retalis.mazdaavisos;

import android.content.Context;
import android.os.SystemClock;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Automatic LOCK only. Credentials exist only in RAM during the user-started monitor. */
final class AutomaticLockController {
 private static final Object AUTH=new Object();
 private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
 private static final int NOTIFICATION_ID=6260;
 private static boolean monitoring;
 private static long authVersion;
 private static ControlSession.State state;
 private static String monitorVehicleId;
 private static char[] pin;
 private final Context context;
 private final AutoLockPolicy policy;
 private final Runnable onConfirmed;
 private final String vehicleId;
 private long observedAuthorization=-1;
 AutomaticLockController(Context context,AutoLockPolicy policy,Runnable onConfirmed,String vehicleId){
   this.context=context.getApplicationContext();this.policy=policy;this.onConfirmed=onConfirmed;this.vehicleId=vehicleId;
 }
 static boolean enabled(Context context){
   return AlertRules.enabled(context,AlertRules.PARKED_OPEN_REMINDER)&&AlertRules.enabled(context,AlertRules.AUTO_LOCK);
 }
 private static void erase(){if(pin!=null)Arrays.fill(pin,'\0');pin=null;state=null;authVersion++;}
 static void sessionCleared(){synchronized(AUTH){erase();}}
 static void monitorStopped(){synchronized(AUTH){monitoring=false;monitorVehicleId=null;erase();}}
 static void monitorStarted(Context context,String vehicleId){
   synchronized(AUTH){monitoring=true;monitorVehicleId=vehicleId;erase();}
   sessionAvailable(context);
 }
 static void settingsChanged(Context context){
   if(!enabled(context)){sessionCleared();return;}sessionAvailable(context);
 }
 static void sessionAvailable(Context context){
   if(!enabled(context)){sessionCleared();return;}
   long expected;
   synchronized(AUTH){if(!monitoring)return;expected=authVersion;}
   // Never call ControlSession while holding AUTH (logout uses the opposite order).
   ControlSession.State current=ControlSession.get();
   String value=current==null?null:ControlSession.pinFor(current);
   if(value==null)return;
   char[] candidate=value.toCharArray();
   synchronized(AUTH){
     if(!monitoring||authVersion!=expected||!enabled(context)||!current.vehicleId.equals(monitorVehicleId)){Arrays.fill(candidate,'\0');return;}
     if(state==current&&pin!=null){Arrays.fill(candidate,'\0');return;}
     erase();state=current;pin=candidate;
   }
 }
 static String authorizationStatus(Context context){
   if(!enabled(context))return "Cierre automático desactivado.";
   synchronized(AUTH){
     if(monitoring&&state!=null&&pin!=null)return "Cierre automático autorizado mientras este monitor siga activo.";
   }
   return "Para autorizar el cierre automático, conecta la cuenta Mazda e inicia el monitor.";
 }
 private static final class Credential {
   final ControlSession.State session;final char[] pin;final long version;
   Credential(ControlSession.State session,char[] pin,long version){this.session=session;this.pin=pin;this.version=version;}
 }
 private static Credential credential(){
   synchronized(AUTH){return monitoring&&state!=null&&pin!=null?new Credential(state,pin.clone(),authVersion):null;}
 }
 private static boolean current(Credential credential){
   synchronized(AUTH){return monitoring&&state==credential.session&&pin!=null&&authVersion==credential.version;}
 }
 void tick(){
   long now=SystemClock.elapsedRealtime();boolean bound=vehicleId!=null&&!vehicleId.isEmpty();
   policy.enabled(bound&&enabled(context),now);
   if(!bound)return;
   long version;synchronized(AUTH){version=authVersion;}
   if(observedAuthorization!=version){observedAuthorization=version;policy.authorizationChanged(now);}
   if(!ControlCommandGate.GLOBAL.quiet())return;
   AutoLockPolicy.Attempt attempt=policy.claim(now);if(attempt==null)return;
   WORKER.execute(()->run(attempt));
 }
 private void notifyUser(String title,String body){
   MainActivity.alert(context,title,body,NOTIFICATION_ID);AlertHistory.add(context,title,body);
 }
 private void run(AutoLockPolicy.Attempt attempt){
   if(!enabled(context)||!policy.permits(attempt,SystemClock.elapsedRealtime())){
     policy.finish(attempt,false,SystemClock.elapsedRealtime());return;
   }
   ControlCommandGate gate=ControlCommandGate.GLOBAL;
   if(!gate.beginAutomatic()){policy.release(attempt);return;}
   Credential credential=credential();
   try{
     if(credential==null||!credential.session.vehicleId.equals(vehicleId)){
       policy.finish(attempt,false,SystemClock.elapsedRealtime());
       notifyUser("No se pudo cerrar el Mazda automáticamente","Falta autorización de cierre. Autentica la cuenta Mazda y mantén el monitor activo. El coche puede seguir desbloqueado.");return;
     }
     long revision=gate.revision();
     java.util.function.BooleanSupplier permitted=()->enabled(context)&&current(credential)&&gate.unchanged(revision)&&
       policy.permits(attempt,SystemClock.elapsedRealtime());
     if(!permitted.getAsBoolean())throw new CancellationException();
     notifyUser("Cierre automático del Mazda","Ha pasado más de un minuto desbloqueado. Consultando su estado y solicitando el bloqueo sin pedir confirmación.");
     MazdaRemoteApi.Result result=new MazdaRemoteApi(context,null).lockAutomatically(credential.session,new String(credential.pin),permitted);
     boolean confirmed=policy.finish(attempt,result.confirmed,SystemClock.elapsedRealtime());
     if(confirmed){onConfirmed.run();notifyUser("Cierre automático confirmado",result.message);}
     else notifyUser("Cierre automático sin confirmar","La app no ha podido confirmar que el coche quede bloqueado. Compruébalo. "+
       (policy.attempts()>=AutoLockPolicy.MAX_ATTEMPTS?"Se ha alcanzado el límite de tres intentos en este ciclo.":"Volverá a intentarlo tras otro minuto si recibe datos recientes y siguen cumpliéndose las condiciones."));
   }catch(CancellationException cancelled){
     policy.finish(attempt,false,SystemClock.elapsedRealtime());
     notifyUser("Cierre automático aplazado","Ha cambiado el estado, la conexión, la autorización o hay un control manual. No se ha enviado una nueva orden de bloqueo.");
   }catch(Exception failure){
     policy.finish(attempt,false,SystemClock.elapsedRealtime());
     // Never include exceptions, request bodies, credentials or server response text.
     notifyUser("No se ha confirmado el cierre automático","Mazda o la conexión no han permitido confirmar el bloqueo. El coche puede seguir abierto. "+
       (policy.attempts()>=AutoLockPolicy.MAX_ATTEMPTS?"Tras tres intentos, comprueba y cierra el coche.":"Se reintentará tras otro minuto si la autorización y los datos recientes lo permiten."));
   }finally{
     if(credential!=null)Arrays.fill(credential.pin,'\0');gate.endAutomatic();
   }
 }
}
