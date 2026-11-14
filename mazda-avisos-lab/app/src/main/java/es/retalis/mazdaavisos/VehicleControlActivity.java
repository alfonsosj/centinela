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
 static final String LOCK="lock",UNLOCK="unlock";
 private static final String ROOT="https://cma-m.iov.changanauto.com.de/cma-app-gw";
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private TextView state;
 private EditText pinInput;
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
   if(!LOCK.equals(action)&&!UNLOCK.equals(action)){finish();return;}
   show();
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
   boolean locking=LOCK.equals(action);
   card.addView(text(locking?"🔒  Bloquear puertas":"🔓  Desbloquear puertas",20,true));
   TextView instruction=text("Introduce el PIN de control de 6 cifras configurado en Mazda. Se utiliza solo para autorizar esta orden y no se guarda.",14,false);
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
     pinInput=new EditText(this);
     pinInput.setHint("PIN Mazda · 6 cifras");
     pinInput.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
     pinInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
     card.addView(pinInput);
     sendButton=new Button(this);
     sendButton.setText(locking?"Solicitar bloqueo":"Solicitar desbloqueo");
     card.addView(sendButton);
     sendButton.setOnClickListener(v->confirm());
     TextView status=text("La respuesta de Mazda se verificará antes de indicar el resultado. No se mostrará un cierre confirmado sin una lectura posterior del vehículo.",12,false);
     status.setPadding(0,dp(12),0,0);card.addView(status);
   }
   Button back=new Button(this);back.setText("Volver al vehículo");root.addView(back);
   back.setOnClickListener(v->finish());
 }
 private void confirm(){
   if(busy||pinInput==null)return;
   String pin=pinInput.getText().toString();
   pinInput.setText("");
   if(!pin.matches("[0-9]{6}")){say("El PIN debe tener exactamente 6 cifras.");return;}
   ControlSession.State session=ControlSession.get();
   if(session==null){say("La sesión de control ha caducado. Inicia sesión otra vez.");return;}
   boolean unlock=UNLOCK.equals(action);
   new AlertDialog.Builder(this)
     .setTitle(unlock?"¿Desbloquear puertas?":"¿Bloquear puertas?")
     .setMessage(unlock?
       "Vas a desbloquear físicamente el vehículo. Asegúrate de que está en un lugar seguro y de tener control sobre quién puede acceder.":
       "Vas a solicitar el bloqueo de las puertas. Comprueba que no haya personas que necesiten salir.")
     .setNegativeButton("Cancelar",(d,w)->{})
     .setPositiveButton("Confirmar", (d,w)->{
       busy=true;sendButton.setEnabled(false);
       say("Autorizando con Mazda…");
       worker.execute(()->{
         try{DoorResult res=execute(session,pin,unlock);
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
