package es.retalis.mazdaavisos;

import android.os.SystemClock;
import java.security.PrivateKey;

/**
 * Credenciales de control de Mazda guardadas solo en RAM y por 15 minutos.
 * No guardamos PIN, token, clave privada ni VIN en preferencias o archivos.
 */
final class ControlSession {
 static final long TTL_MS=15L*60L*1000L;
 static final class State {
   final String token,deviceId,vehicleId;
   final PrivateKey privateKey;
   final long createdAt;
   private State(String token,String deviceId,String vehicleId,PrivateKey key){
     this.token=token;this.deviceId=deviceId;this.vehicleId=vehicleId;
     this.privateKey=key;createdAt=SystemClock.elapsedRealtime();
   }
 }
 private static State current;
 static synchronized void set(String token,String deviceId,String vehicleId,PrivateKey key){
   if(token==null||token.isEmpty()||deviceId==null||deviceId.isEmpty()
      ||vehicleId==null||vehicleId.isEmpty()||key==null)throw new IllegalArgumentException("Sesión incompleta");
   current=new State(token,deviceId,vehicleId,key);
 }
 static synchronized State get(){
   if(current!=null&&SystemClock.elapsedRealtime()-current.createdAt>=TTL_MS)current=null;
   return current;
 }
 static synchronized void clear(){current=null;}
 private ControlSession(){}
}
