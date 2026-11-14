package es.retalis.mazdaavisos;

import android.content.Context;
import android.util.Base64;
import org.json.JSONObject;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.crypto.Cipher;
import javax.net.ssl.HttpsURLConnection;

/** Reuses the already tested Mazda Europe command protocol for in-place controls. */
final class MazdaRemoteApi {
 private static final String ROOT="https://cma-m.iov.changanauto.com.de/cma-app-gw";
 private final Context context;
 private final Consumer<String> update;
 MazdaRemoteApi(Context c,Consumer<String> update){
   this.context=c.getApplicationContext();
   this.update=update;
 }
 private void say(String message){if(update!=null)update.accept(message);}
 static final class Result{
   final String message;
   Result(String message){this.message=message;}
 }
 Result execute(ControlSession.State s,String pin,boolean unlock)throws Exception{
   JSONObject check=post(s,"/cma-app-car-control/api/security-code/check-code",
     new JSONObject().put("safeCode",ConnectionTestActivity.encrypt(
       KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.decode(ConnectionTestActivity.SERVER_RSA,Base64.DEFAULT))),pin)));
   JSONObject checkData=check.optJSONObject("data");
   String rc=checkData==null?"":checkData.optString("rcToken","");
   if(rc.isEmpty())throw new IOException("Mazda no autorizó el PIN de control");
   say("PIN autorizado. Solicitando número de serie cifrado…");
   JSONObject encrypted=post(s,"/cma-app-car-control/api/serial-no/get",new JSONObject().put("type","1"));
   String serialEnc=encrypted.optString("data","");
   if(serialEnc.isEmpty())throw new IOException("Mazda no entregó el identificador de control");
   Cipher cipher=Cipher.getInstance("RSA/ECB/PKCS1Padding");
   cipher.init(Cipher.DECRYPT_MODE,s.privateKey);
   String serial=new String(cipher.doFinal(Base64.decode(serialEnc,Base64.DEFAULT)),StandardCharsets.UTF_8);
   JSONObject payload=new JSONObject()
     .put("open",unlock).put("rcToken",rc).put("seriralNo",serial).put("vehicleId",s.vehicleId);
   Signature signature=Signature.getInstance("SHA256withRSA");
   signature.initSign(s.privateKey);
   String canonical="open="+(unlock?"true":"false")+
     "&rcToken="+rc+"&seriralNo="+serial+"&vehicleId="+s.vehicleId;
   signature.update(canonical.getBytes(StandardCharsets.UTF_8));
   payload.put("sign",Base64.encodeToString(signature.sign(),Base64.DEFAULT));
   say("Enviando orden firmada de "+(unlock?"desbloqueo":"bloqueo")+"…");
   JSONObject submitted=post(s,"/cma-app-car-control/api/control/doors",payload);
   JSONObject resultData=submitted.optJSONObject("data");
   String commandId=resultData==null?"":resultData.optString("commandId","");
   if(commandId.isEmpty())throw new IOException("Mazda no confirmó la recepción de la orden");
   boolean accepted=false;
   for(int i=0;i<15;i++){
     if(Thread.currentThread().isInterrupted())throw new InterruptedException("Operación detenida");
     JSONObject response=post(s,"/cma-app-car-control/api/control/control-result",
       new JSONObject().put("commandId",commandId).put("vehicleId",s.vehicleId));
     JSONObject data=response.optJSONObject("data");
     if(data==null||!data.has("resultCode")||data.opt("resultCode") instanceof Boolean)
       throw new IOException("Mazda devolvió un resultado de control no reconocido");
     int code=data.optInt("resultCode",Integer.MIN_VALUE);
     if(code==0||(!unlock&&code==1015)){accepted=true;break;}
     if(code!=-100)throw new IOException("Mazda rechazó la orden (código "+code+")");
     Thread.sleep(1000);
   }
   if(!accepted)return new Result("La orden no terminó de procesarse. No puede confirmarse el estado del vehículo.");
   say("Mazda aceptó la operación. Consultando el cierre real…");
   for(int attempt=0;attempt<3;attempt++){
     if(attempt>0)Thread.sleep(2500);
     try{
       JSONObject snapshot=post(s,"/cma-app-car-condition/api/vehicle/condition/v2",statusRequest(s.vehicleId));
       JSONObject data=snapshot.optJSONObject("data");
       if(data!=null){
         VehicleSnapshot.observeApi(context,data);
         JSONObject doors=data.optJSONObject("door");
         if(doors!=null){
           int driver=doors.optInt("driverLock",-1);
           int passenger=doors.optInt("passengerLock",-1);
           int expected=unlock?1:0;
           if(driver==expected&&passenger==expected)
             return new Result(unlock?"Puertas DESBLOQUEADAS: Mazda ha confirmado ambas cerraduras.":"Puertas BLOQUEADAS: Mazda ha confirmado ambas cerraduras.");
         }
       }
     }catch(Exception ignored){/* show awaiting status rather than inventing success */}
   }
   return new Result("Mazda ha aceptado la orden, pero aún NO ha confirmado el estado de ambas puertas. Compruébalo en la aplicación oficial.");
 }

 Result executeAdvanced(ControlSession.State s,String pin,String cmd,int temp,int chargeLimit,int seat)throws Exception{
   boolean protectedCommand=protectedAction(cmd);
   String name,kind;
   boolean chargeControl=CHARGE_LIMIT.equals(cmd);
   String serialType=chargeControl?"2":"1";
   JSONObject values=new JSONObject();
   switch(cmd){
     case WINDOWS_OPEN: case WINDOWS_CLOSE:
       name="windows";kind="ventanillas";values.put("open",WINDOWS_OPEN.equals(cmd));break;
     case TRUNK_OPEN: case TRUNK_CLOSE:
       name="trunk";kind="maletero";values.put("open",TRUNK_OPEN.equals(cmd));break;
     case CLIMATE_ON: case CLIMATE_OFF:
       name="air-conditioner";kind="climatización";
       values.put("enabled",CLIMATE_ON.equals(cmd));values.put("targetTemp",temp*10);values.put("runTime",15);break;
     case FIND: case HORN:
       name="flashing-honking";kind="localización";
       values.put("type",FIND.equals(cmd)?1:3);break;
     case DEFROST_ON: case DEFROST_OFF:
       name="defrost";kind="desempañado";values.put("enabled",DEFROST_ON.equals(cmd));break;
     case WHEEL_ON: case WHEEL_OFF:
       name="steering-wheel/heat";kind="volante";values.put("open",WHEEL_ON.equals(cmd));break;
     case DRIVER_HEAT: case PASSENGER_HEAT: case DRIVER_VENT: case PASSENGER_VENT:
       boolean heat=DRIVER_HEAT.equals(cmd)||PASSENGER_HEAT.equals(cmd);
       boolean driver=DRIVER_HEAT.equals(cmd)||DRIVER_VENT.equals(cmd);
       name="seats/"+(heat?"heat":"wind");kind="asiento";
       values.put((driver?"master":"copilot")+"Switch",seat>0?1:0);
       values.put((driver?"master":"copilot")+"Level",Math.max(1,Math.min(3,seat)));break;
     case CHARGE_LIMIT:
       name="charge/percentage";kind="límite de carga";
       if(chargeLimit<60||chargeLimit>100)throw new IOException("Límite de carga fuera de rango");
       values.put("chargePercentageMax",chargeLimit).put("command","charge_max").put("rcToken","");
       break;
     default:throw new IOException("Control no permitido");
   }
   String rc="";
   if(protectedCommand){
     JSONObject checked=post(s,"/cma-app-car-control/api/security-code/check-code",
       new JSONObject().put("safeCode",ConnectionTestActivity.encrypt(
         KeyFactory.getInstance("RSA").generatePublic(
           new X509EncodedKeySpec(Base64.decode(ConnectionTestActivity.SERVER_RSA,Base64.DEFAULT))),pin)));
     JSONObject data=checked.optJSONObject("data");
     rc=data==null?"":data.optString("rcToken","");
     if(rc.isEmpty())throw new IOException("El PIN de control no fue autorizado");
   }
   say("Solicitando autorización para "+kind+"…");
   JSONObject enc=post(s,"/cma-app-car-control/api/serial-no/get",new JSONObject().put("type",serialType));
   String cipherText=enc.optString("data","");
   if(cipherText.isEmpty())throw new IOException("Mazda no entregó el identificador cifrado");
   Cipher cipher=Cipher.getInstance("RSA/ECB/PKCS1Padding");
   cipher.init(Cipher.DECRYPT_MODE,s.privateKey);
   String serial=new String(cipher.doFinal(Base64.decode(cipherText,Base64.DEFAULT)),StandardCharsets.UTF_8);
   values.put("seriralNo",serial).put("vehicleId",s.vehicleId);
   if(protectedCommand)values.put("rcToken",rc);
   sign(values,s.privateKey,chargeControl);
   say("Enviando solicitud a Mazda…");
   JSONObject sent=post(s,chargeControl?
     "/cma-app-car-control/api/charge/percentage":
     "/cma-app-car-control/api/control/"+name,values);
   JSONObject data=sent.optJSONObject("data");
   String id=data==null?"":data.optString("commandId","");
   if(id.isEmpty())throw new IOException("Mazda no devolvió identificador de la orden");
   boolean accepted=false;
   for(int i=0;i<15;i++){
     JSONObject response=post(s,"/cma-app-car-control/api/control/control-result",
       new JSONObject().put("commandId",id).put("vehicleId",s.vehicleId));
     JSONObject details=response.optJSONObject("data");
     if(details==null||!details.has("resultCode"))throw new IOException("Respuesta de control sin resultado");
     int code=details.optInt("resultCode",Integer.MIN_VALUE);
     if(code==0||((CLIMATE_ON.equals(cmd)||CLIMATE_OFF.equals(cmd))&&code==1015)){accepted=true;break;}
     if(chargeControl&&code==1000){
       JSONObject current=post(s,"/cma-app-car-condition/api/vehicle/condition/v2",allStatusRequest(s.vehicleId));
       JSONObject currentData=current.optJSONObject("data");
       JSONObject charge=currentData==null?null:currentData.optJSONObject("charge");
       if(charge!=null&&charge.optInt("maxSocPercent",-1)==chargeLimit){
         VehicleSnapshot.observeApi(context,currentData);
         return new Result("Límite de carga confirmado: "+chargeLimit+" %.");
       }
       throw new IOException("La API respondió 1000 pero no se ha verificado el nuevo límite");
     }
     if(code!=-100)throw new IOException("Mazda rechazó la operación (código "+code+")");
     Thread.sleep(1000L);
   }
   if(!accepted)return new Result("No se ha confirmado la ejecución: Mazda no finalizó la orden.");
   say("Mazda ha aceptado la solicitud. Comprobando estado del coche…");
   for(int i=0;i<3;i++){
     if(i>0)Thread.sleep(2500L);
     try{
       JSONObject result=post(s,"/cma-app-car-condition/api/vehicle/condition/v2",allStatusRequest(s.vehicleId));
       JSONObject status=result.optJSONObject("data");
       if(status==null)continue;
       VehicleSnapshot.observeApi(context,status);
       if(matches(status,cmd,temp,chargeLimit,seat))return new Result(actionName(cmd)+": CONFIRMADO en el estado Mazda.");
     }catch(Exception ignored){}
   }
   return new Result(FIND.equals(cmd)||HORN.equals(cmd)?
     "Mazda aceptó la señal. No es posible verificar físicamente su ejecución desde la API.":
     "Mazda aceptó la orden, pero aún NO se ha confirmado el cambio de "+kind+". Compruébalo en la app oficial.");
 }
 private static void sign(JSONObject payload,PrivateKey key,boolean specialCharge)throws Exception{
   java.util.List<String> keys=new java.util.ArrayList<>();
   java.util.Iterator<String> it=payload.keys();
   while(it.hasNext()){
     String k=it.next();
     if(!k.equals("sign")&&!(specialCharge&&(k.equals("command")||k.equals("rcToken"))))keys.add(k);
   }
   java.util.Collections.sort(keys);
   StringBuilder canonical=new StringBuilder();
   for(String k:keys){
     if(canonical.length()>0)canonical.append("&");
     Object value=payload.get(k);
     canonical.append(k).append("=").append(value instanceof Boolean?String.valueOf(value).toLowerCase(java.util.Locale.ROOT):String.valueOf(value));
   }
   Signature signature=Signature.getInstance("SHA256withRSA");
   signature.initSign(key);signature.update(canonical.toString().getBytes(StandardCharsets.UTF_8));
   payload.put("sign",Base64.encodeToString(signature.sign(),Base64.DEFAULT));
 }
 private static boolean matches(JSONObject status,String action,int temp,int chargeLimit,int seat){
   if(action.startsWith("windows")){
     JSONObject windows=status.optJSONObject("window");
     org.json.JSONArray a=windows==null?null:windows.optJSONArray("windows");
     if(a==null||a.length()<4)return false;
     boolean expected=WINDOWS_OPEN.equals(action);
     for(int i=0;i<4;i++){
       Object value=a.opt(i);
       if(value==null||value==JSONObject.NULL)return false;
       int n;
       try{n=Integer.parseInt(value.toString());}catch(Exception e){return false;}
       if(expected?(n<=0):(n!=0))return false;
     }
     return true;
   }
   if(action.startsWith("trunk")){
     JSONObject door=status.optJSONObject("door");
     if(door==null)return false;
     int trunk=door.optInt("trunk",-1);
     return trunk==(TRUNK_OPEN.equals(action)?1:0);
   }
   if(action.startsWith("climate")){
     JSONObject hvac=status.optJSONObject("hvac");
     if(hvac==null)return false;
     int ac=hvac.optInt("acStatus",-1);
     if(ac!=(CLIMATE_ON.equals(action)?1:0))return false;
     if(CLIMATE_ON.equals(action)){
       int target=hvac.optInt("remoteTemp",-10000);
       return target!=-10000&&Math.abs(target-temp*10)<=5;
     }
     return true;
   }
   if(CHARGE_LIMIT.equals(action)){
     JSONObject charge=status.optJSONObject("charge");
     return charge!=null&&charge.optInt("maxSocPercent",-1)==chargeLimit;
   }
   if(DEFROST_ON.equals(action)||DEFROST_OFF.equals(action)){
     JSONObject hvac=status.optJSONObject("hvac");
     return hvac!=null&&hvac.optInt("defrostStatus",-1)==(DEFROST_ON.equals(action)?1:0);
   }
   if(WHEEL_ON.equals(action)||WHEEL_OFF.equals(action)){
     JSONObject hvac=status.optJSONObject("hvac");
     return hvac!=null&&hvac.optInt("steeringWheelHeat",-1)==(WHEEL_ON.equals(action)?1:0);
   }
   if(action.startsWith("driver_")||action.startsWith("passenger_")){
     JSONObject seats=status.optJSONObject("seat");
     JSONObject position=seats==null?null:seats.optJSONObject(action.startsWith("driver_")?"leftFront":"rightFront");
     return position!=null&&position.optInt(action.endsWith("_heat")?"heatStatus":"ventStatus",-1)==seat;
   }
   return false;
 }
 private static JSONObject allStatusRequest(String vehicleId)throws Exception{
   JSONObject criteria=new JSONObject().put("seat","1").put("tire","0").put("charge","1")
     .put("vehicleStatus","1").put("hvac","1").put("departurePlan","0").put("fuel","0")
     .put("window","1").put("door","1").put("airConditionPlan","0").put("lamp","0")
     .put("warmCoolingBox","0").put("welcome","0").put("location","0");
   Object id=vehicleId.matches("[0-9]{1,18}")?Long.valueOf(vehicleId):vehicleId;
   return new JSONObject().put("vechileCriteria",criteria).put("vehicleId",id);
 }
 private static JSONObject statusRequest(String vehicleId)throws Exception{
   JSONObject criteria=new JSONObject().put("seat","0").put("tire","0").put("charge","0")
    .put("vehicleStatus","0").put("hvac","0").put("departurePlan","0").put("fuel","0")
    .put("window","0").put("door","1").put("airConditionPlan","0").put("lamp","0")
    .put("warmCoolingBox","0").put("welcome","0").put("location","0");
   Object id=vehicleId.matches("[0-9]{1,18}")?Long.valueOf(vehicleId):vehicleId;
   return new JSONObject().put("vechileCriteria",criteria).put("vehicleId",id);
 }
 private static JSONObject post(ControlSession.State s,String route,JSONObject request)throws Exception{
   if(!route.startsWith("/cma-app-car-control/api/")&&!route.equals("/cma-app-car-condition/api/vehicle/condition/v2"))
     throw new IOException("Ruta no autorizada");
   HttpsURLConnection c=(HttpsURLConnection)new URL(ROOT+route).openConnection();
   c.setConnectTimeout(15000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);
   c.setRequestMethod("POST");c.setDoOutput(true);
   c.setRequestProperty("Content-Type","application/json");
   c.setRequestProperty("devicetype","iPhone");c.setRequestProperty("apptype","IOS");
   c.setRequestProperty("appid","cma");c.setRequestProperty("appversion","V1.2.3");
   c.setRequestProperty("deviceid",s.deviceId);c.setRequestProperty("authorization",s.token);
   c.setRequestProperty("language","en_US");c.setRequestProperty("accept-language","en-US;q=1.0");
   c.setRequestProperty("user-agent","overseas/1.2.3 (com.mazda.mazda6e; build:1; iOS 27.0.0) Alamofire/5.5.0");
   try{
     byte[] body=request.toString().getBytes(StandardCharsets.UTF_8);
     try(OutputStream out=c.getOutputStream()){out.write(body);}
     if(c.getResponseCode()!=200)throw new IOException("Mazda HTTP "+c.getResponseCode());
     ByteArrayOutputStream bytes=new ByteArrayOutputStream();
     try(InputStream in=c.getInputStream()){
       byte[] buf=new byte[8192];int n;
       while((n=in.read(buf))!=-1){bytes.write(buf,0,n);if(bytes.size()>1024*1024)throw new IOException("Respuesta excesiva");}
     }
     JSONObject res=new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
     if(!res.optBoolean("success",false)){
       String code=res.optString("code","");
       if(!code.matches("[A-Za-z0-9_-]{1,50}"))code="no disponible";
       throw new IOException("Mazda rechazó la solicitud ("+code+")");
     }
     return res;
   }finally{c.disconnect();}
 }
 private static String safeError(Exception e){
   String s=e.getMessage();
   if(e instanceof InterruptedException)return "Operación interrumpida";
   if(s==null)return "Error de comunicación";
   if(s.length()>125)s=s.substring(0,125);
   return s.replaceAll("[\\r\\n]"," ");
 }

 JSONObject refresh(ControlSession.State session)throws Exception{
   JSONObject response=post(session,"/cma-app-car-condition/api/vehicle/condition/v2",
     allStatusRequest(session.vehicleId));
   JSONObject data=response.optJSONObject("data");
   if(data==null||!VehicleSnapshot.observeApi(context,data))throw new IOException("Mazda no devolvió datos actuales");
   return data;
 }
 static String displayError(Exception e){return safeError(e);}
}
