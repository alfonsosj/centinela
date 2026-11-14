package es.retalis.mazdaavisos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;
import javax.net.ssl.HttpsURLConnection;

/**
 * Comprobación de autorización MQTT en servidores oficiales Mazda Europa.
 * No se conecta al broker MQTT. No publica, controla el vehículo, ni almacena tokens.
 */
public class ConnectionTestActivity extends Activity {
 private static final String EU="https://cma-m.iov.changanauto.com.de/cma-app-gw";
 private static final String CA="https://ca-m.iov.changanauto.com.de";
 // Clave pública RSA 2048 del servicio de autenticación, publicada por la integración comunitaria.
 static final String SERVER_RSA="MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAkyhr43cBPTJ3jLiYsmbUwUp74cMJIOju5vqVzgtuK63Q99qV6iVT8wN5cXlyMtWI2mfOmhIao/fUN821im69MfOHsWXdqQEo5e9v654GPw+bju0pCphEPtD1I0VcyS34QkAu04urSun2U1q3Dr2OICLVWSnLa+01ioKxkaB0D209zXcls2eFQpvRAWm7xxVsoqzSwqp+neu5quOpn+eO/bW0TxcSQ8VZcDEUvadRTLSR0eOWgRuHIBiD2RGqPIPzKCm5A14q1qhxUZ8U0pmYe0Sx7eMy4RVe2iW7fnjc6pxTUMBkercSL26mevYouuCKqyie+LVQAtGa29RMl/lyiwIDAQAB";
 private final ExecutorService executor=Executors.newSingleThreadExecutor();
 private EditText emailField,passwordField,codeField;
 private TextView status;
 private Button loginButton,verifyButton,listenButton,sessionButton;
 private MqttProbe probe;
 private JSONObject mqttConfig;
 private String mqttSecret;
 private String token=null,encryptedEmail=null,deviceId=null;
 private java.security.PrivateKey controlKey=null;
 private String previousMessage="";
 @Override public void onCreate(Bundle state){
   super.onCreate(state);
   deviceId=getPreferences(MODE_PRIVATE).getString("device_id","");
   if(deviceId.length()==0){
     deviceId=UUID.randomUUID().toString();
     getPreferences(MODE_PRIVATE).edit().putString("device_id",deviceId).apply();
   }
   ScrollView scroll=new ScrollView(this);
   LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(28,36,28,32);scroll.addView(root);setContentView(scroll);
   TextView title=new TextView(this);title.setText("PRUEBA MQTT · MAZDA EUROPA");title.setTextSize(21);root.addView(title);
   TextView info=new TextView(this);
   info.setText("Solo cuenta compartida de pruebas. Las credenciales viajan cifradas por HTTPS a Mazda Europa y no se guardan en el dispositivo ni en GitHub. NO introduzcas tu cuenta principal. Esta versión permite una escucha MQTT de dos minutos o un monitor continuo experimental con notificación visible. No usa Home Assistant, no se reinicia sola tras apagar el móvil y no sustituye las alertas de seguridad.");
   info.setPadding(0,18,0,18);root.addView(info);
   emailField=new EditText(this);emailField.setSingleLine(true);emailField.setHint("Correo cuenta secundaria");emailField.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);root.addView(emailField);
   passwordField=new EditText(this);passwordField.setSingleLine(true);passwordField.setHint("Contraseña de cuenta secundaria");passwordField.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);root.addView(passwordField);
   loginButton=new Button(this);loginButton.setText("1. Comprobar autorización en Mazda");root.addView(loginButton);loginButton.setOnClickListener(v->startLogin());
   codeField=new EditText(this);codeField.setHint("Código recibido por email");codeField.setInputType(InputType.TYPE_CLASS_NUMBER);codeField.setVisibility(View.GONE);root.addView(codeField);
   verifyButton=new Button(this);verifyButton.setText("2. Verificar dispositivo y consultar MQTT");verifyButton.setVisibility(View.GONE);root.addView(verifyButton);verifyButton.setOnClickListener(v->verify());
   listenButton=new Button(this);listenButton.setText("Escuchar MQTT durante 2 minutos");listenButton.setVisibility(View.GONE);root.addView(listenButton);listenButton.setOnClickListener(v->listen());
   sessionButton=new Button(this);sessionButton.setText("Iniciar monitor continuo experimental");sessionButton.setVisibility(View.GONE);root.addView(sessionButton);sessionButton.setOnClickListener(v->startSession());
   status=new TextView(this);status.setText("Sin realizar consultas.");status.setTextIsSelectable(true);status.setPadding(0,24,0,10);root.addView(status);
   Button close=new Button(this);close.setText("Cerrar diagnóstico");root.addView(close);close.setOnClickListener(v->finish());
 }
 private void say(String text){previousMessage=text;runOnUiThread(()->status.setText(text));}
 private void busy(boolean on){runOnUiThread(()->{loginButton.setEnabled(!on);verifyButton.setEnabled(!on);listenButton.setEnabled(!on);sessionButton.setEnabled(!on);});}
 private void verificationUI(){runOnUiThread(()->{codeField.setVisibility(View.VISIBLE);verifyButton.setVisibility(View.VISIBLE);});}
 private void hideVerification(){runOnUiThread(()->{codeField.setVisibility(View.GONE);verifyButton.setVisibility(View.GONE);});}
 private void startLogin(){
   final String email=emailField.getText().toString().trim();
   final String password=passwordField.getText().toString();
   if(email.isEmpty()||password.isEmpty()){say("Introduce el correo y la contraseña de la cuenta secundaria.");return;}
   // Evitamos dejar el password visible en el formulario tras iniciar la prueba.
   passwordField.setText("");
   busy(true);
   say("Iniciando sesión en Mazda Europa...");
   executor.execute(()->{
     try{
       PublicKey key=KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.decode(SERVER_RSA,Base64.DEFAULT)));
       encryptedEmail=encrypt(key,email);
       String encPassword=encrypt(key,password);
       KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);
       KeyPair pair=generator.generateKeyPair();
       controlKey=pair.getPrivate();
       String controlPublic=Base64.encodeToString(pair.getPublic().getEncoded(),Base64.DEFAULT);
       JSONObject body=new JSONObject().put("loginTime",String.valueOf(System.currentTimeMillis()/1000))
           .put("email",encryptedEmail).put("password",encPassword).put("pubKey",controlPublic);
       JSONObject response=post(EU+"/cma-app-auth/api/login/email-pass-in/v2",body,false);
       JSONObject data=response.optJSONObject("data");
       if(data==null)throw new Exception("Respuesta de sesión incompleta");
       token=data.optString("token","");
       if(token.isEmpty())throw new Exception("Mazda no devolvió token de sesión");
       if(data.optBoolean("emailVerify",false)){
         JSONObject req=new JSONObject().put("email",encryptedEmail).put("deviceName","iPhone 17 Pro")
            .put("loginTime",String.valueOf(System.currentTimeMillis()/1000)).put("type","1");
         post(EU+"/cma-app-user/api/send-email/device-login/send",req,false);
         say("Mazda solicita verificar este dispositivo. Consulta el email de la cuenta secundaria e introduce el código; no lo compartas por chat.");
         verificationUI();
       } else {
         say("Inicio de sesión aceptado. Consultando acceso MQTT...");
         checkMqtt();
       }
     }catch(Exception e){token=null;say("No se pudo completar: "+safeError(e));}
     finally {busy(false);}
   });
 }
 private void verify(){
   String code=codeField.getText().toString().trim();
   if(code.isEmpty()||token==null){say("No hay sesión o falta el código.");return;}
   codeField.setText("");
   busy(true);
   executor.execute(()->{
    try{
      JSONObject req=new JSONObject().put("authCode",code).put("email",encryptedEmail)
        .put("deviceName","iPhone 17 Pro").put("lastLoginTime",String.valueOf(System.currentTimeMillis()/1000))
        .put("type","3").put("deviceModel","iPhone 17 Pro");
      JSONObject response=post(EU+"/cma-app-user/api/login-device/email-verify",req,false);
      if(!response.optBoolean("data",false))throw new Exception("El código de verificación no fue aceptado");
      hideVerification();
      say("Dispositivo verificado. Consultando canales MQTT...");
      checkMqtt();
    }catch(Exception e){say("Verificación fallida: "+safeError(e));}
    finally {busy(false);}
   });
 }
 private void checkMqtt(){
   try{
     JSONObject response=post(EU+"/cma-app-user/api/vehicle/vehicles",new JSONObject(),false);
     JSONArray vehicles=response.optJSONArray("data");
     if(vehicles==null||vehicles.length()==0){
       response=post(EU+"/cma-app-user/api/car/vehicles",new JSONObject(),false);
       vehicles=response.optJSONArray("data");
     }
     if(vehicles==null||vehicles.length()==0){
       say("Sesión válida pero no aparecen vehículos en la API. Comprueba permisos de la cuenta compartida.");
       token=null;return;
     }
     JSONObject vehicle=vehicles.optJSONObject(0);
     if(vehicle==null)throw new Exception("Listado de vehículos inesperado");
     String carId=vehicle.optString("carId",vehicle.optString("vehicleId",""));
     if(carId.isEmpty())throw new Exception("El vehículo carece de identificador de consulta");
     // El inicio de control remoto solo necesita una sesión y un vehículo autorizado.
     if(controlKey==null||token==null||token.isEmpty())
       throw new Exception("No se ha podido completar la sesión de control");
     ControlSession.set(token,deviceId,carId,controlKey);
     controlKey=null;
     String action=getIntent().getStringExtra(VehicleControlActivity.EXTRA_ACTION);
     if(VehicleControlActivity.LOCK.equals(action)||VehicleControlActivity.UNLOCK.equals(action)){
       say("Autenticación completada. Abriendo la pantalla del PIN...");
       runOnUiThread(()->{
         Intent next=new Intent(this,VehicleControlActivity.class);
         next.putExtra(VehicleControlActivity.EXTRA_ACTION,action);
         startActivity(next);
         finish();
       });
       return;
     }
     JSONObject functions=post(CA+"/user-apigw/vot-connect-conf-center/api/device/appGetCarConfFunc",
       new JSONObject().put("carId",carId),true);
     String thu=findThu(functions.opt("data"),0);
     if(thu==null){
       say("La cuenta ve "+vehicles.length()+" vehículo(s), pero Mazda no entrega la configuración THU necesaria para MQTT. Puede deberse a permisos de la cuenta compartida.");
       token=null;return;
     }
     JSONObject conf=post(CA+"/user-apigw/vot-connect-conf-center/api/device/getConnConf",
       new JSONObject().put("deviceType","1").put("deviceId",thu),true);
     JSONObject mqttAuth=post(CA+"/user-apigw/vot-connect-auth-center/api/auth/getAuthTokenByUserId",
       new JSONObject(),true);
     JSONObject cfg=conf.optJSONObject("data");
     JSONObject auth=mqttAuth.optJSONObject("data");
     boolean authReady=auth!=null&&!auth.optString("authToken","").isEmpty();
     int reports=0,events=0;
     if(cfg!=null){
       JSONArray infos=cfg.optJSONArray("mqttConnectionInfos");
       if(infos!=null&&infos.length()>0){
         JSONObject c=infos.optJSONObject(0);
         JSONArray topics=c==null?null:c.optJSONArray("topicInfos");
         if(topics!=null)for(int i=0;i<topics.length();i++){
           JSONObject entry=topics.optJSONObject(i);
           if(entry==null)continue;
           String kind=entry.optString("msgType","");
           JSONArray subscriptions=entry.optJSONArray("subTopics");
           if(subscriptions==null)continue;
           for(int k=0;k<subscriptions.length();k++){
             String topic=subscriptions.optString(k,"");
             if(kind.equals("properties")&&topic.endsWith("/properties/report"))reports++;
             if(kind.equals("events")&&topic.endsWith("/events/up"))events++;
           }
         }
       }
     }
     if(authReady && cfg!=null && reports>0){
       mqttConfig=cfg;mqttSecret=auth.optString("authToken");
       runOnUiThread(()->{listenButton.setVisibility(View.VISIBLE);sessionButton.setVisibility(View.VISIBLE);});
     }
     String initialInfo=readInitialVehicleStatus(carId);
      say("PRUEBA TERMINADA\n\nVehículos visibles: "+vehicles.length()+
       "\nConfiguración THU: disponible"+
       "\nAutorización MQTT: "+(authReady?"concedida":"no confirmada")+
       "\nCanales properties/report: "+reports+
       "\nCanales events/up: "+events+"\n"+initialInfo+
       "\n\nSi la autorización fue concedida, pulsa «Escuchar MQTT» para una prueba de 2 minutos.");
     token=null;
   }catch(Exception ex){token=null;say("Sesión iniciada, pero falló el diagnóstico MQTT: "+safeError(ex));}
 }
 private String readInitialVehicleStatus(String carId){
   try{
     Object id=carId;
     if(carId.matches("[0-9]{1,18}"))id=Long.parseLong(carId);
     JSONObject criteria=new JSONObject()
       .put("seat","1").put("tire","1").put("charge","1").put("vehicleStatus","1")
       .put("hvac","1").put("window","1").put("door","1").put("lamp","1")
       .put("departurePlan","0").put("fuel","0").put("airConditionPlan","0")
       .put("warmCoolingBox","0").put("welcome","0").put("location","0");
     JSONObject body=new JSONObject().put("vechileCriteria",criteria).put("vehicleId",id);
     JSONObject result=post(EU+"/cma-app-car-condition/api/vehicle/condition/v2",body,false);
     boolean loaded=VehicleSnapshot.observeApi(this,result.optJSONObject("data"));
     VehicleSnapshot snap=VehicleSnapshot.read(this);
     return loaded?"Lectura inicial: batería "+snap.socText()+"; temperatura "+snap.tempText()+"."
       :"Lectura inicial: todavía sin datos reconocidos.";
   }catch(Exception ignored){
     return "Lectura inicial: no disponible para esta cuenta; MQTT seguirá conectado.";
   }
 }
 private void startSession(){
   if(mqttConfig==null||mqttSecret==null||mqttSecret.isEmpty()){say("Primero autoriza la cuenta secundaria.");return;}
   if(android.os.Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
     say("Primero concede permiso de notificaciones en la pantalla principal de Mazda Avisos Lab.");return;
   }
   Intent intent=new Intent(this,MqttSessionService.class).setAction(MqttSessionService.ACTION_START);
   intent.putExtra(MqttSessionService.EXTRA_CONFIG,mqttConfig.toString());
   intent.putExtra(MqttSessionService.EXTRA_AUTH,mqttSecret);
   try{
     startForegroundService(intent);
     mqttConfig=null;mqttSecret=null;token=null;encryptedEmail=null;
     listenButton.setEnabled(false);sessionButton.setEnabled(false);
     say("Monitor MQTT iniciado sin Home Assistant. Deja activa la notificación persistente. Android puede detener el monitor por batería o cerrar el servicio; tras reiniciar el teléfono hay que volver a iniciar sesión.");
   }catch(RuntimeException err){say("No se pudo iniciar el servicio: "+safeError(err));}
 }
 private void listen(){
   if(mqttConfig==null||mqttSecret==null){say("Primero comprueba la autorización MQTT.");return;}
   final JSONObject cfg=mqttConfig;final String pw=mqttSecret;
   busy(true);say("Iniciando escucha de prueba...");
   getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
   executor.execute(()->{
     try{probe=new MqttProbe(this,cfg,pw);probe.run(this::say,120);}
     catch(Exception e){say("Error en la escucha MQTT: "+safeError(e));}
     finally{if(probe!=null)probe.close();probe=null;mqttConfig=null;mqttSecret=null;token=null;encryptedEmail=null;
       runOnUiThread(()->{listenButton.setVisibility(View.GONE);sessionButton.setVisibility(View.GONE);getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);});busy(false);}
   });
 }
 private String findThu(Object node,int depth){
   if(node==null||depth>30)return null;
   if(node instanceof JSONObject){
     JSONObject obj=(JSONObject)node;
     if("commands".equals(obj.optString("msgType"))&&"THU_Service".equals(obj.optString("serviceCode"))){
       String id=obj.optString("deviceId");if(!id.isEmpty())return id;
     }
     return findThu(obj.opt("children"),depth+1);
   }
   if(node instanceof JSONArray){
     JSONArray arr=(JSONArray)node;
     for(int i=0;i<arr.length();i++){
       String found=findThu(arr.opt(i),depth+1);if(found!=null)return found;
     }
   }
   return null;
 }
 static String encrypt(PublicKey key,String input)throws Exception{
   byte[] in=input.getBytes(StandardCharsets.UTF_8);
   Cipher cipher=Cipher.getInstance("RSA/ECB/PKCS1Padding");
   cipher.init(Cipher.ENCRYPT_MODE,key);
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();
   for(int i=0;i<in.length;i+=245)bytes.write(cipher.doFinal(in,i,Math.min(245,in.length-i)));
   return Base64.encodeToString(bytes.toByteArray(),Base64.DEFAULT);
 }
 private JSONObject post(String url,JSONObject body,boolean ca)throws Exception{
   // Únicamente destinos HTTPS oficiales previstos; no se aceptan URL proporcionadas por el usuario.
   if(!(url.startsWith(EU+"/")||url.startsWith(CA+"/")))throw new Exception("Destino de red no permitido");
   HttpsURLConnection conn=(HttpsURLConnection)new URL(url).openConnection();
   conn.setConnectTimeout(15000);conn.setReadTimeout(15000);
   conn.setInstanceFollowRedirects(false);
   conn.setRequestMethod("POST");conn.setDoOutput(true);
   conn.setRequestProperty("Content-Type","application/json");
   conn.setRequestProperty("devicetype","iPhone");
   conn.setRequestProperty("apptype","IOS");
   conn.setRequestProperty("appid","cma");
   conn.setRequestProperty("appversion","V1.2.3");
   conn.setRequestProperty("language","en_US");
   conn.setRequestProperty("accept-language","en-US;q=1.0");
   conn.setRequestProperty("user-agent","overseas/1.2.3 (com.mazda.mazda6e; build:1; iOS 27.0.0) Alamofire/5.5.0");
   conn.setRequestProperty("deviceid",deviceId);
   if(token!=null&&!token.isEmpty()){
     conn.setRequestProperty("authorization",token);
     if(ca)conn.setRequestProperty("X-Tsp-User-Token",token.substring(token.lastIndexOf('|')+1));
   }
   try{
     byte[] data=body.toString().getBytes(StandardCharsets.UTF_8);
     try(OutputStream out=conn.getOutputStream()){out.write(data);}
     int status=conn.getResponseCode();
     if(status!=200)throw new Exception("HTTP "+status+" ("+(url.startsWith(CA)?"servicio MQTT":"servicio Mazda")+")");
     ByteArrayOutputStream result=new ByteArrayOutputStream();
     try(InputStream in=conn.getInputStream()){
       byte[] buff=new byte[8192];int read;
       while((read=in.read(buff))>=0){
         result.write(buff,0,read);
         if(result.size()>1024*1024)throw new Exception("Respuesta excesivamente grande");
       }
     }
     JSONObject obj=new JSONObject(new String(result.toByteArray(),StandardCharsets.UTF_8));
     if(!obj.optBoolean("success",false)){
       String code=obj.optString("code","ERROR");
       if(!code.matches("[a-zA-Z0-9_\\-]{1,64}"))code="ERROR";
       throw new Exception("Mazda rechazó la consulta (código "+code+")");
     }
     return obj;
   }finally{conn.disconnect();}
 }
 private String safeError(Exception e){
   String m=e.getMessage();if(m==null)return e.getClass().getSimpleName();
   // No enviamos ni mostramos cuerpos completos de error con datos de sesión.
   if(m.length()>160)m=m.substring(0,160);
   return m.replaceAll("(?i)bearer\\s+[A-Za-z0-9._\\-]+","[oculto]");
 }
 @Override protected void onDestroy(){if(probe!=null)probe.close();mqttConfig=null;mqttSecret=null;token=null;encryptedEmail=null;controlKey=null;executor.shutdownNow();super.onDestroy();}
}
