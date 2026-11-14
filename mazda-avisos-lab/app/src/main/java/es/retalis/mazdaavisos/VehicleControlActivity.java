package es.retalis.mazdaavisos;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.concurrent.*;
import javax.crypto.Cipher;
import javax.net.ssl.HttpsURLConnection;

/** Bloqueo/desbloqueo autorizado con PIN y confirmación explícita. */
public final class VehicleControlActivity extends Activity {
 static final String EXTRA_ACTION="desired_control";
 static final String LOCK="lock",UNLOCK="unlock",
   WINDOWS_OPEN="windows_open",WINDOWS_CLOSE="windows_close",
   TRUNK_OPEN="trunk_open",TRUNK_CLOSE="trunk_close",
   CLIMATE_ON="climate_on",CLIMATE_OFF="climate_off",
   FIND="find_vehicle",HORN="honk_horn",
   DEFROST_ON="defrost_on",DEFROST_OFF="defrost_off",
   WHEEL_ON="wheel_on",WHEEL_OFF="wheel_off",
   CHARGE_LIMIT="charge_limit",
   DRIVER_HEAT="driver_heat",PASSENGER_HEAT="passenger_heat",
   DRIVER_VENT="driver_vent",PASSENGER_VENT="passenger_vent";
 private static final String ROOT="https://cma-m.iov.changanauto.com.de/cma-app-gw";
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private TextView state;
 private EditText pinInput;
 private android.widget.SeekBar climateTarget;
 private int temperature=21,chargeTarget=80,seatLevel=0;
 private Button sendButton;
 private String action;
 private volatile boolean busy=false;
 private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
 private GradientDrawable round(int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(18));return d;}
 private TextView text(String value,int size,boolean strong){
   TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(0xFF26394F);
   if(strong)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
 }
 @Override public void onCreate(Bundle saved){
   super.onCreate(saved);
   action=getIntent().getStringExtra(EXTRA_ACTION);
   if(!knownAction(action)){finish();return;}
   show();
 }
 private static boolean protectedAction(String action){
   return LOCK.equals(action)||UNLOCK.equals(action)||
     WINDOWS_OPEN.equals(action)||WINDOWS_CLOSE.equals(action)||
     TRUNK_OPEN.equals(action)||TRUNK_CLOSE.equals(action);
 }
 private static boolean knownAction(String action){
   return protectedAction(action)||CLIMATE_ON.equals(action)||CLIMATE_OFF.equals(action)||
     FIND.equals(action)||HORN.equals(action)||
     DEFROST_ON.equals(action)||DEFROST_OFF.equals(action)||
     WHEEL_ON.equals(action)||WHEEL_OFF.equals(action)||CHARGE_LIMIT.equals(action)||
     DRIVER_HEAT.equals(action)||PASSENGER_HEAT.equals(action)||
     DRIVER_VENT.equals(action)||PASSENGER_VENT.equals(action);
 }
 private static String actionName(String action){
   switch(action){
     case LOCK: return "Bloquear puertas";
     case UNLOCK: return "Desbloquear puertas";
     case WINDOWS_OPEN: return "Abrir ventanillas";
     case WINDOWS_CLOSE: return "Cerrar ventanillas";
     case TRUNK_OPEN: return "Abrir maletero";
     case TRUNK_CLOSE: return "Cerrar maletero";
     case CLIMATE_ON: return "Activar climatización";
     case CLIMATE_OFF: return "Apagar climatización";
     case FIND: return "Localizar coche";
     case HORN: return "Sonar bocina";
     case DEFROST_ON: return "Activar desempañado";
     case DEFROST_OFF: return "Desactivar desempañado";
     case WHEEL_ON: return "Activar calefacción del volante";
     case WHEEL_OFF: return "Desactivar calefacción del volante";
     case CHARGE_LIMIT: return "Ajustar límite de carga";
     case DRIVER_HEAT: return "Calefacción asiento conductor";
     case PASSENGER_HEAT: return "Calefacción asiento acompañante";
     case DRIVER_VENT: return "Ventilación asiento conductor";
     case PASSENGER_VENT: return "Ventilación asiento acompañante";
     default: return "Acción no reconocida";
   }
 }
 private void say(String s){runOnUiThread(()->{if(state!=null)state.setText(s);});}
 private void show(){
   ScrollView scroller=new ScrollView(this);scroller.setBackgroundColor(0xFFF6F7FB);
   LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(22),dp(32),dp(22),dp(24));
   scroller.addView(root);setContentView(scroller);
   root.addView(text("CONTROL REMOTO MAZDA",23,true));
   TextView caption=text("Solo cuenta Mazda compartida y autorizada. No se envía ninguna orden hasta confirmarla.",14,false);
   caption.setPadding(0,dp(12),0,dp(22));root.addView(caption);
   LinearLayout card=new LinearLayout(this);card.setOrientation(1);card.setPadding(dp(18),dp(22),dp(18),dp(22));
   card.setBackground(round(Color.WHITE));root.addView(card);
   boolean needsPin=protectedAction(action);
   card.addView(text(actionName(action),20,true));
   TextView instruction=text(needsPin?
     "Introduce el PIN de control Mazda de 6 cifras. Se utiliza solo para autorizar esta orden y nunca se almacena.":
     "Esta orden utiliza una solicitud firmada a Mazda Europa. Las funciones nuevas son experimentales y requieren confirmación.",14,false);
   instruction.setPadding(0,dp(14),0,dp(12));card.addView(instruction);
   state=text("Sin enviar órdenes al vehículo.",13,false);card.addView(state);
   ControlSession.State s=ControlSession.get();
   if(s==null){
     TextView hint=text("Necesitas iniciar sesión con la cuenta secundaria. La sesión de control solo dura 15 minutos y se guarda exclusivamente en memoria.",14,false);
     hint.setPadding(0,dp(15),0,dp(15));card.addView(hint);
     Button login=new Button(this);login.setText("Autenticar cuenta Mazda");
     card.addView(login);login.setOnClickListener(v->{
       Intent i=new Intent(this,ConnectionTestActivity.class).putExtra(EXTRA_ACTION,action);
       startActivity(i);finish();
     });
   } else {
     if(needsPin){
       pinInput=new EditText(this);
       pinInput.setHint("PIN Mazda · 6 cifras");
       pinInput.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
       pinInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
       card.addView(pinInput);
     }
     if(CLIMATE_ON.equals(action)){
       TextView target=text("Temperatura objetivo: 21 °C",15,true);
       card.addView(target);
       climateTarget=new SeekBar(this);climateTarget.setMax(28);climateTarget.setProgress(10);
       climateTarget.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
         @Override public void onProgressChanged(SeekBar bar,int progress,boolean user){
           temperature=16+progress/2;
           target.setText("Temperatura objetivo: "+temperature+" °C");
         }
         @Override public void onStartTrackingTouch(SeekBar bar){}
         @Override public void onStopTrackingTouch(SeekBar bar){}
       });
       card.addView(climateTarget);
     }
     if(CHARGE_LIMIT.equals(action)){
       TextView label=text("Límite de carga: 80 %",15,true);card.addView(label);
       SeekBar slider=new SeekBar(this);slider.setMax(40);slider.setProgress(20);
       slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
         public void onProgressChanged(SeekBar b,int v,boolean user){
           chargeTarget=60+v;label.setText("Límite de carga: "+chargeTarget+" %");
         }
         public void onStartTrackingTouch(SeekBar b){}
         public void onStopTrackingTouch(SeekBar b){}
       });card.addView(slider);
     }
     if(action.startsWith("driver_")||action.startsWith("passenger_")){
       TextView label=text("Nivel: apagado",15,true);card.addView(label);
       SeekBar slider=new SeekBar(this);slider.setMax(3);
       slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
         public void onProgressChanged(SeekBar b,int v,boolean user){
           seatLevel=v;label.setText("Nivel: "+(v==0?"apagado":v+" de 3"));
         }
         public void onStartTrackingTouch(SeekBar b){}
         public void onStopTrackingTouch(SeekBar b){}
       });card.addView(slider);
     }
     sendButton=new Button(this);
     sendButton.setText("Solicitar: "+actionName(action));
     card.addView(sendButton);
     sendButton.setOnClickListener(v->confirm());
     TextView status=text("La respuesta de Mazda se verificará antes de indicar el resultado. No se mostrará un cierre confirmado sin una lectura posterior del vehículo.",12,false);
     status.setPadding(0,dp(12),0,0);card.addView(status);
   }
   Button back=new Button(this);back.setText("Volver al vehículo");root.addView(back);
   back.setOnClickListener(v->finish());
 }
 private void confirm(){
   if(busy)return;
   boolean needsPin=protectedAction(action);
   String pin=needsPin&&pinInput!=null?pinInput.getText().toString():"";
   if(pinInput!=null)pinInput.setText("");
   if(needsPin&&!pin.matches("[0-9]{6}")){say("El PIN debe tener exactamente 6 cifras.");return;}
   ControlSession.State session=ControlSession.get();
   if(session==null){say("La sesión de control ha caducado. Inicia sesión otra vez.");return;}
   boolean unlock=UNLOCK.equals(action);
   boolean isDoors=LOCK.equals(action)||UNLOCK.equals(action);
   final int temp=temperature,limit=chargeTarget,level=seatLevel;
   String safety=action.equals(WINDOWS_OPEN)||action.equals(WINDOWS_CLOSE)||
       action.equals(TRUNK_OPEN)||action.equals(TRUNK_CLOSE)?
     "Asegúrate de que no hay personas, animales ni obstáculos cerca de las ventanillas o el portón. Esta orden puede mover elementos físicos y generar riesgos.":
     unlock?"Desbloqueará el vehículo. Confirma que estás en un lugar seguro.":
     LOCK.equals(action)?"Comprueba que nadie necesite salir del coche.":
     FIND.equals(action)||HORN.equals(action)?"La bocina y/o las luces podrían activarse. Úsalo solo cuando sea apropiado.":
     CHARGE_LIMIT.equals(action)?"Se cambiará el límite de carga, sin iniciar ni detener la carga.":
     "Comprueba que el coche esté aparcado y que nadie pueda verse afectado por la activación de sistemas eléctricos.";
   new AlertDialog.Builder(this)
     .setTitle("¿"+actionName(action)+"?")
     .setMessage(safety+" Esta operación requiere tu confirmación explícita.")
     .setNegativeButton("Cancelar",(d,w)->{})
     .setPositiveButton("Confirmar", (d,w)->{
       busy=true;sendButton.setEnabled(false);
       say("Autorizando con Mazda…");
       worker.execute(()->{
         try{DoorResult res=isDoors?execute(session,pin,unlock):executeAdvanced(session,pin,action,temp,limit,level);
           say(res.message);
         }catch(Exception e){say("No se ha podido confirmar la orden: "+safeError(e));}
         finally{runOnUiThread(()->{busy=false;if(sendButton!=null)sendButton.setEnabled(true);});}
       });
     }).show();
 }
 private static final class DoorResult{
   final String message;
   DoorResult(String message){this.message=message;}
 }
 private DoorResult execute(ControlSession.State s,String pin,boolean unlock)throws Exception{
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
   if(!accepted)return new DoorResult("La orden no terminó de procesarse. No puede confirmarse el estado del vehículo.");
   say("Mazda aceptó la operación. Consultando el cierre real…");
   for(int attempt=0;attempt<3;attempt++){
     if(attempt>0)Thread.sleep(2500);
     try{
       JSONObject snapshot=post(s,"/cma-app-car-condition/api/vehicle/condition/v2",statusRequest(s.vehicleId));
       JSONObject data=snapshot.optJSONObject("data");
       if(data!=null){
         VehicleSnapshot.observeApi(this,data);
         JSONObject doors=data.optJSONObject("door");
         if(doors!=null){
           int driver=doors.optInt("driverLock",-1);
           int passenger=doors.optInt("passengerLock",-1);
           int expected=unlock?1:0;
           if(driver==expected&&passenger==expected)
             return new DoorResult(unlock?"Puertas DESBLOQUEADAS: Mazda ha confirmado ambas cerraduras.":"Puertas BLOQUEADAS: Mazda ha confirmado ambas cerraduras.");
         }
       }
     }catch(Exception ignored){/* show awaiting status rather than inventing success */}
   }
   return new DoorResult("Mazda ha aceptado la orden, pero aún NO ha confirmado el estado de ambas puertas. Compruébalo en la aplicación oficial.");
 }

 private DoorResult executeAdvanced(ControlSession.State s,String pin,String cmd,int temp)throws Exception{
   boolean protectedCommand=protectedAction(cmd);
   String name,kind;
   JSONObject values=new JSONObject();
   switch(cmd){
     case WINDOWS_OPEN: case WINDOWS_CLOSE:
       name="windows";kind="ventanillas";values.put("open",WINDOWS_OPEN.equals(cmd));break;
     case TRUNK_OPEN: case TRUNK_CLOSE:
       name="trunk";kind="maletero";values.put("open",TRUNK_OPEN.equals(cmd));break;
     case CLIMATE_ON: case CLIMATE_OFF:
       name="air-conditioner";kind="climatización";
       values.put("enabled",CLIMATE_ON.equals(cmd));values.put("targetTemp",temp*10);values.put("runTime",15);break;
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
   JSONObject enc=post(s,"/cma-app-car-control/api/serial-no/get",new JSONObject().put("type","1"));
   String cipherText=enc.optString("data","");
   if(cipherText.isEmpty())throw new IOException("Mazda no entregó el identificador cifrado");
   Cipher cipher=Cipher.getInstance("RSA/ECB/PKCS1Padding");
   cipher.init(Cipher.DECRYPT_MODE,s.privateKey);
   String serial=new String(cipher.doFinal(Base64.decode(cipherText,Base64.DEFAULT)),StandardCharsets.UTF_8);
   values.put("seriralNo",serial).put("vehicleId",s.vehicleId);
   if(protectedCommand)values.put("rcToken",rc);
   sign(values,s.privateKey);
   say("Enviando solicitud a Mazda…");
   JSONObject sent=post(s,"/cma-app-car-control/api/control/"+name,values);
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
     if(code!=-100)throw new IOException("Mazda rechazó la operación (código "+code+")");
     Thread.sleep(1000L);
   }
   if(!accepted)return new DoorResult("No se ha confirmado la ejecución: Mazda no finalizó la orden.");
   say("Mazda ha aceptado la solicitud. Comprobando estado del coche…");
   for(int i=0;i<3;i++){
     if(i>0)Thread.sleep(2500L);
     try{
       JSONObject result=post(s,"/cma-app-car-condition/api/vehicle/condition/v2",allStatusRequest(s.vehicleId));
       JSONObject status=result.optJSONObject("data");
       if(status==null)continue;
       VehicleSnapshot.observeApi(this,status);
       if(matches(status,cmd,temp))return new DoorResult(actionName(cmd)+": CONFIRMADO en el estado Mazda.");
     }catch(Exception ignored){}
   }
   return new DoorResult("Mazda aceptó la orden, pero aún NO ha confirmado el cambio de "+kind+". Compruébalo en la app oficial.");
 }
 private static void sign(JSONObject payload,PrivateKey key)throws Exception{
   java.util.List<String> keys=new java.util.ArrayList<>();
   java.util.Iterator<String> it=payload.keys();
   while(it.hasNext()){String k=it.next();if(!k.equals("sign"))keys.add(k);}
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
 private static boolean matches(JSONObject status,String action,int temp){
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
   return false;
 }
 private static JSONObject allStatusRequest(String vehicleId)throws Exception{
   JSONObject criteria=new JSONObject().put("seat","0").put("tire","0").put("charge","1")
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
 @Override public void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
