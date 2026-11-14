package es.retalis.mazdaavisos;

import android.os.SystemClock;
import java.security.PrivateKey;
import java.util.Arrays;

/**
 * Sesión de control de Mazda en RAM, con expiración a los 15 minutos.
 * El PIN se borra de memoria al caducar o cerrar la sesión.
 * Nunca se escribe en preferencias, archivos, registros ni GitHub.
 */
final class ControlSession {
 static final long TTL_MS=15L*60L*1000L;
 static final class State {
   final String token,deviceId,vehicleId;
   final PrivateKey privateKey;
   final long createdAt;
   private final char[] controlPin;
   private State(String token,String deviceId,String vehicleId,PrivateKey key,char[] pin){
     this.token=token;this.deviceId=deviceId;this.vehicleId=vehicleId;
     privateKey=key;createdAt=SystemClock.elapsedRealtime();
     controlPin=pin.clone();
   }
   private void erase(){Arrays.fill(controlPin,'\0');}
 }
 private static State current;
 private static boolean validPin(char[] pin){
   if(pin==null||pin.length!=6)return false;
   for(char c:pin)if(c<'0'||c>'9')return false;
   return true;
 }
 private static void clearCurrent(){
   if(current!=null)current.erase();
   current=null;
 }
 static synchronized void set(String token,String deviceId,String vehicleId,PrivateKey key,char[] pin){
   if(token==null||token.isEmpty()||deviceId==null||deviceId.isEmpty()
      ||vehicleId==null||vehicleId.isEmpty()||key==null||!validPin(pin))
      throw new IllegalArgumentException("Sesión incompleta o PIN no válido");
   clearCurrent();
   current=new State(token,deviceId,vehicleId,key,pin);
 }
 static synchronized State get(){
   if(current!=null&&SystemClock.elapsedRealtime()-current.createdAt>=TTL_MS)clearCurrent();
   return current;
 }
 static synchronized String pinFor(State session){
   State active=get();
   return active!=null&&active==session?new String(active.controlPin):null;
 }
 static synchronized void clear(){clearCurrent();}
 private ControlSession(){}
}
