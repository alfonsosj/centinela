package es.retalis.mazdaavisos;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.TreeSet;
import javax.net.ssl.HttpsURLConnection;

/** Read-only discovery. No control routes and no European credentials sent to China. */
final class FeatureDiscovery {
 private static final String EUROPE="https://ca-m.iov.changanauto.com.de/user-apigw/vot-connect-conf-center/api/device/appGetCarConfFunc";
 private static final String CHINA="https://m.iov.changan.com.cn/cm-apigw/vot-connect-conf-center/api/device/appGetCarConfFunc";
 private static final String[] NAMES={"Centinela de cámaras","Fotos interiores","Fotos exteriores / panorámica","Grabación / DVR","Aparcamiento remoto"};
 private static final String[][] MARKERS={
   {"sentrymode","sentinelmode"},
   {"incarphoto","takephoto","innercarphoto","imageincar"},
   {"outercarphoto","photograph","panorama"},
   {"dvr","dashcam","videorecord","drivingrecorder"},
   {"carapa","remoteparking","remotemove","remotevideocontrol","castingitem"}
 };
 private final TreeSet<String> codes=new TreeSet<>();
 private final TreeSet<String>[] hits;
 private int visited;
 private boolean limited;
 @SuppressWarnings("unchecked") private FeatureDiscovery(){
   hits=(TreeSet<String>[])new TreeSet<?>[NAMES.length];
   for(int i=0;i<hits.length;i++)hits[i]=new TreeSet<>();
 }
 private static String normalized(String value){return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");}
 private void code(String value){
   // Codes describe functions, never accounts, topics, identifiers or tokens.
   if(value==null||!value.matches("[#A-Za-z][A-Za-z0-9_#./:-]{0,95}"))return;
   if(codes.size()<160)codes.add(value);else limited=true;
   String key=normalized(value);
   for(int i=0;i<MARKERS.length;i++)for(String marker:MARKERS[i]){
     if(key.contains(marker)&&hits[i].size()<12){hits[i].add(value);break;}
   }
 }
 private void visit(Object node,int depth){
   if(node==null||node==JSONObject.NULL)return;
   if(depth>18||++visited>12000){limited=true;return;}
   if(node instanceof JSONArray){
     JSONArray a=(JSONArray)node;
     for(int i=0;i<a.length()&&visited<=12000;i++)visit(a.opt(i),depth+1);
   }else if(node instanceof JSONObject){
     JSONObject o=(JSONObject)node;Iterator<String> keys=o.keys();
     while(keys.hasNext()&&visited<=12000){
       String key=keys.next();Object value=o.opt(key);
       if(key.equals("applicationFuncCode")||key.equals("serviceCode")||key.equals("attrCode")||
          key.equals("functionCode")||key.equals("funcCode")){
         if(value instanceof String)code((String)value);
       }
       // Some backends return a map keyed by the capability rather than a list.
       String n=normalized(key);
       for(String[] group:MARKERS)for(String marker:group)if(n.contains(marker)){code(key);break;}
       if(value instanceof JSONObject||value instanceof JSONArray)visit(value,depth+1);
     }
   }
 }
 static String analyze(Object configuration){
   FeatureDiscovery scan=new FeatureDiscovery();scan.visit(configuration,0);
   StringBuilder out=new StringBuilder("COMPATIBILIDAD DECLARADA POR EL SERVICIO EUROPEO\n");
   out.append(DateFormat.getDateTimeInstance().format(new Date())).append("\n\n");
   for(int i=0;i<NAMES.length;i++){
     out.append(NAMES[i]).append(": ");
     if(scan.hits[i].isEmpty())out.append("no observado en esta respuesta.");
     else out.append("indicios en la configuración: ").append(String.join(", ",scan.hits[i])).append('.');
     out.append("\n\n");
   }
   out.append("Una mención no confirma que la función esté habilitada, que exista el hardware ni que admita órdenes. La ausencia tampoco demuestra que sea incompatible.\n\n");
   out.append("Códigos de funciones encontrados: ").append(scan.codes.size()).append("\n");
   if(scan.codes.isEmpty())out.append("La respuesta no contiene códigos reconocibles para este diagnóstico.\n");
   for(String code:scan.codes)out.append("• ").append(code).append('\n');
   if(scan.limited)out.append("\nLa respuesta se ha limitado; el diagnóstico puede ser parcial.\n");
   out.append("\nEsta lectura no envía órdenes al vehículo. El informe omite VIN, cuenta, identificadores de dispositivo y datos de sesión.");
   return out.toString();
 }
 static String europe(ControlSession.State session)throws Exception{
   if(session==null||ControlSession.get()!=session)throw new IOException("Autentica la cuenta Mazda antes de leer las funciones.");
   JSONObject response=request(EUROPE,new JSONObject().put("carId",session.vehicleId),session);
   if(!response.optBoolean("success",false))throw new IOException("Europa rechazó la lectura ("+safeCode(response)+").");
   if(!response.has("data")||response.isNull("data"))throw new IOException("Europa respondió sin configuración de funciones. No se puede concluir compatibilidad.");
   return analyze(response.opt("data"));
 }
 static String china()throws Exception{
   // Deliberately anonymous: different authentication, no token or car identifier.
   JSONObject response=request(CHINA,new JSONObject(),null);
   return "CONSULTA PÚBLICA A LA PLATAFORMA CHINA\n"+
     DateFormat.getDateTimeInstance().format(new Date())+"\n\n"+
     "El servidor ha respondido. Código: "+safeCode(response)+".\n\n"+
     ("40005".equals(safeCode(response))?"El servicio exige un token de autenticación. Esta prueba no ha comprobado si admite una sesión europea.\n\n":"")+
     "No se ha enviado cuenta, contraseña, PIN, VIN ni token europeo. Esta prueba comprueba la respuesta de la pasarela; no verifica que tu cuenta o tu coche europeo puedan utilizarla.\n\n"+
     "Cambiar la dirección del servidor no completa la autenticación ni activa funciones del vehículo.";
 }
 private static String safeCode(JSONObject response){
   String code=response.optString("code","sin código");
   return code.matches("[A-Za-z0-9_-]{1,64}")?code:"sin código";
 }
 private static JSONObject request(String destination,JSONObject body,ControlSession.State session)throws Exception{
   if((session==null&&!CHINA.equals(destination))||(session!=null&&!EUROPE.equals(destination)))
     throw new IOException("Destino no permitido para esta consulta.");
   HttpsURLConnection connection=(HttpsURLConnection)new URL(destination).openConnection();
   connection.setConnectTimeout(15000);connection.setReadTimeout(15000);
   connection.setInstanceFollowRedirects(false);connection.setRequestMethod("POST");connection.setDoOutput(true);
   connection.setRequestProperty("Content-Type","application/json");
   if(session!=null){
     connection.setRequestProperty("devicetype","iPhone");connection.setRequestProperty("apptype","IOS");
     connection.setRequestProperty("appid","cma");connection.setRequestProperty("appversion","V1.2.3");
     connection.setRequestProperty("language","en_US");connection.setRequestProperty("accept-language","en-US;q=1.0");
     connection.setRequestProperty("user-agent","overseas/1.2.3 (com.mazda.mazda6e; build:1; iOS 27.0.0) Alamofire/5.5.0");
     connection.setRequestProperty("deviceid",session.deviceId);connection.setRequestProperty("authorization",session.token);
     connection.setRequestProperty("X-Tsp-User-Token",session.token.substring(session.token.lastIndexOf('|')+1));
   }else connection.setRequestProperty("user-agent","MazdaAvisos-CompatibilityProbe/1.0");
   try{
     try(OutputStream output=connection.getOutputStream()){output.write(body.toString().getBytes(StandardCharsets.UTF_8));}
     int status=connection.getResponseCode();
     if(status!=200)throw new IOException("La pasarela respondió HTTP "+status+". No se ha verificado compatibilidad.");
     ByteArrayOutputStream bytes=new ByteArrayOutputStream();
     try(InputStream input=connection.getInputStream()){
       byte[] buffer=new byte[8192];int read;
       while((read=input.read(buffer))!=-1){
         bytes.write(buffer,0,read);if(bytes.size()>1024*1024)throw new IOException("Configuración demasiado grande.");
       }
     }
     return new JSONObject(new String(bytes.toByteArray(),StandardCharsets.UTF_8));
   }finally{connection.disconnect();}
 }
}
