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

/** Read-only discovery. Regional credential use has a separate, explicit UI consent. */
final class FeatureDiscovery {
 private static final String EUROPE="https://ca-m.iov.changanauto.com.de/user-apigw/vot-connect-conf-center/api/device/appGetCarConfFunc";
 private static final String EUROPE_FUNCTIONS="https://cma-m.iov.changanauto.com.de/cma-app-gw/cma-app-user/api/vehicle/function-config";
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
 private final TreeSet<String> details=new TreeSet<>();
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
   value=symbolic(value);if(value==null)return;
   if(codes.size()<160)codes.add(value);else limited=true;
   marker(value);
 }
 private void marker(String value){
   String key=normalized(value);
   for(int i=0;i<MARKERS.length;i++)for(String marker:MARKERS[i]){
     if(key.contains(marker)&&hits[i].size()<12){hits[i].add(value);break;}
   }
 }
 private static String symbolic(Object value){
   if(!(value instanceof String))return null;
   String text=((String)value).trim();
   if(!text.matches("[#A-Za-z][A-Za-z0-9_#./:-]{0,95}")||text.matches("[A-HJ-NPR-Z0-9]{17}")||text.matches("[a-fA-F0-9]{24,}"))return null;
   return text;
 }
 private void metadata(JSONObject object){
   String label=null;
   for(String key:new String[]{"applicationFuncCode","serviceCode","attrCode","functionCode","funcCode"}){
     label=symbolic(object.opt(key));if(label!=null)break;
   }
   if(label==null){
     String orphan=symbolic(object.opt("commandCode"));
     if(orphan==null)return;
     label="Mando sin función asociada";marker(orphan);
   }
   StringBuilder entry=new StringBuilder(label);
   Object command=object.opt("commandCode");
   if(command!=null&&command!=JSONObject.NULL&&!String.valueOf(command).trim().isEmpty()&&
      (!(command instanceof JSONObject)||((JSONObject)command).length()>0)&&
      (!(command instanceof JSONArray)||((JSONArray)command).length()>0)){
     String name=symbolic(command);
     entry.append(" · commandCode=").append(name==null?"presente (contenido omitido)":name);
     if(name!=null)marker(name);
   }
   for(String key:new String[]{"enabled","enable","isEnable","isEnabled","visible","isShow","support","isSupport","available","canControl","functionStatus","status"}){
     Object value=object.opt(key);
     if(value instanceof Boolean||value!=null&&String.valueOf(value).matches("-?[0-9]{1,3}"))
       entry.append(" · ").append(key).append('=').append(value);
   }
   if(entry.length()>label.length()){
     if(details.size()<240)details.add(entry.toString());else limited=true;
   }
 }
 private void visit(Object node,int depth){
   if(node==null||node==JSONObject.NULL)return;
   if(depth>18||++visited>12000){limited=true;return;}
   if(node instanceof JSONArray){
     JSONArray a=(JSONArray)node;
     for(int i=0;i<a.length()&&visited<=12000;i++)visit(a.opt(i),depth+1);
   }else if(node instanceof JSONObject){
     JSONObject o=(JSONObject)node;metadata(o);Iterator<String> keys=o.keys();
     while(keys.hasNext()&&visited<=12000){
       String key=keys.next();Object value=o.opt(key);
       // Native function-config uses primitive codes in confList, not objects.
       // Arbitrary arrays of accounts, topics or other strings are not codes.
       if(key.equals("confList")&&value instanceof JSONArray){
         JSONArray list=(JSONArray)value;
         for(int i=0;i<list.length()&&visited<=12000;i++){
           Object item=list.opt(i);visited++;
           if(item instanceof String)code((String)item);
           else if(item instanceof JSONObject||item instanceof JSONArray)visit(item,depth+1);
         }
         if(visited>12000)limited=true;
         continue;
       }
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
   return analyze(configuration,"COMPATIBILIDAD DECLARADA POR EL SERVICIO EUROPEO");
 }
 private static String analyze(Object configuration,String title){
   FeatureDiscovery scan=new FeatureDiscovery();scan.visit(configuration,0);
   StringBuilder out=new StringBuilder(title).append('\n');
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
   out.append("\nMetadatos de mando y disponibilidad: ").append(scan.details.size()).append("\n");
   if(scan.details.isEmpty())out.append("No se han observado metadatos reconocibles. No equivale a que los mandos no existan.\n");
   for(String detail:scan.details)out.append("• ").append(detail).append('\n');
   out.append("\nLas marcas enable, status y similares se muestran tal como llegan; su significado depende del servicio. Un commandCode presente no confirma que se pueda ejecutar.\n");
   if(scan.limited)out.append("\nLa respuesta se ha limitado; el diagnóstico puede ser parcial.\n");
   out.append("\nEsta lectura no envía órdenes al vehículo. El informe omite VIN, cuenta, identificadores de dispositivo y datos de sesión.");
   return out.toString();
 }
 static String europe(ControlSession.State session)throws Exception{
   if(session==null||ControlSession.get()!=session)throw new IOException("Autentica la cuenta Mazda antes de leer las funciones.");
   Source cloud=readEurope(session,false),nativeApp=readEurope(session,true);
   StringBuilder out=new StringBuilder("COMPARACIÓN DE DOS LISTAS EUROPEAS\n");
   out.append(DateFormat.getDateTimeInstance().format(new Date())).append("\n\n");
   out.append("Se consultan por separado la configuración de comunicación del coche (TSP/MQTT) y las funciones de la aplicación europea (function-config / confList). La prueba anterior solo leía la primera.\n\n");
   out.append("1. Comunicación TSP/MQTT: ").append(cloud.status).append('\n');
   out.append("2. Funciones de la app europea: ").append(nativeApp.status).append("\n\n");
   if(cloud.data!=null&&nativeApp.data!=null){
     TreeSet<String> shared=new TreeSet<>(cloud.scan.codes);shared.retainAll(nativeApp.scan.codes);
     TreeSet<String> nativeOnly=new TreeSet<>(nativeApp.scan.codes);nativeOnly.removeAll(cloud.scan.codes);
     TreeSet<String> cloudOnly=new TreeSet<>(cloud.scan.codes);cloudOnly.removeAll(nativeApp.scan.codes);
     out.append("Códigos con el mismo nombre en ambas: ").append(shared.size()).append("\n");
     out.append("Solo en funciones de la app europea: ").append(nativeOnly.size()).append("\n");
     for(String code:nativeOnly)out.append("• ").append(code).append('\n');
     out.append("Solo en comunicación TSP/MQTT: ").append(cloudOnly.size()).append("\n\n");
     out.append("Las fuentes pueden usar vocabularios distintos. Una diferencia no demuestra bloqueo regional ni una función oculta. Los inventarios completos aparecen debajo.\n\n");
   }else out.append("No se pueden comparar las listas completas porque falta una respuesta utilizable. Una consulta fallida no equivale a cero funciones.\n\n");
   if(cloud.data!=null)out.append(analyze(cloud.data,"CONFIGURACIÓN EUROPEA DE COMUNICACIÓN TSP/MQTT")).append("\n\n");
   if(nativeApp.data!=null)out.append(analyze(nativeApp.data,"FUNCIONES DECLARADAS POR LA APP EUROPEA · confList")).append("\n\n");
   out.append("Esta comparación no cambia la configuración del coche ni envía órdenes. No envía datos a China. Una mención no habilita la función ni demuestra hardware compatible.");
   return redactSession(out.toString(),session);
 }
 private static final class Source {
   final Object data;final FeatureDiscovery scan;final String status;
   Source(Object data,String error){
     this.data=data;scan=new FeatureDiscovery();if(data!=null)scan.visit(data,0);
     status=data==null?error:"lectura correcta; "+scan.codes.size()+" códigos legibles"+(scan.limited?" (inventario parcial).":".");
   }
 }
 private static Source readEurope(ControlSession.State session,boolean nativeApp){
   try{
     if(ControlSession.get()!=session)return new Source(null,"sesión caducada; no se ha consultado.");
     Object id=session.vehicleId;
     if(nativeApp&&session.vehicleId.matches("[1-9][0-9]{0,17}|0"))id=Long.valueOf(session.vehicleId);
     JSONObject body=new JSONObject().put(nativeApp?"vehicleId":"carId",id);
     JSONObject response=request(nativeApp?EUROPE_FUNCTIONS:EUROPE,body,session);
     if(!response.optBoolean("success",false))return new Source(null,"rechazada; código "+safeCode(response)+".");
     Object data=response.opt("data");
     if(!(data instanceof JSONObject)&&!(data instanceof JSONArray))return new Source(null,"éxito sin lista de configuración utilizable.");
     if(nativeApp&&(!(data instanceof JSONObject)||!(((JSONObject)data).opt("confList") instanceof JSONArray)))
       return new Source(null,"éxito sin una lista confList reconocible; no se interpreta como ausencia de funciones.");
     return new Source(data,null);
   }catch(Exception error){return new Source(null,connectionError(error));}
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
 /** Called only after the regional disclosure dialog is accepted. Never runs on login. */
 static String chinaAuthenticated(ControlSession.State session)throws Exception{
   if(session==null||ControlSession.get()!=session)throw new IOException("La sesión europea ha caducado. Vuelve a autenticar la cuenta.");
   StringBuilder out=new StringBuilder("PRUEBA REGIONAL CON SESIÓN EUROPEA · SOLO LECTURA\n");
   out.append(DateFormat.getDateTimeInstance().format(new Date())).append("\n\n");
   out.append("Destino: pasarela china de configuración (m.iov.changan.com.cn). Canal de la app china: chang-ma.\n");
   String tail=session.token.substring(session.token.lastIndexOf('|')+1);
   String[] tokens=tail.equals(session.token)?new String[]{session.token}:new String[]{session.token,tail};
   for(int i=0;i<tokens.length;i++){
     if(ControlSession.get()!=session){out.append("\nSesión caducada; se han detenido las consultas.\n");break;}
     out.append("\nFormato de sesión ").append(i==0?"europeo completo":"TSP europeo").append(": ");
     try{
       JSONObject body=new JSONObject().put("carId",session.vehicleId).put("token",tokens[i]);
       JSONObject response=request(CHINA,body,session,true);
       out.append("código ").append(safeCode(response)).append("; success=").append(response.optBoolean("success",false)).append(".\n");
       if(response.optBoolean("success",false)){
         if(response.has("data")&&!response.isNull("data")){
           out.append("La pasarela ha devuelto configuración para esta consulta. Esto no demuestra que el coche pueda recibir órdenes chinas.\n\n");
           out.append(analyze(response.opt("data"),"CONFIGURACIÓN DEVUELTA POR LA PASARELA CHINA"));
         }else out.append("La respuesta indica éxito, pero no contiene configuración. No se ha confirmado compatibilidad.\n");
         break;
       }
       out.append("Esta respuesta no acredita una sesión válida ni compatibilidad del vehículo en esa plataforma.\n");
     }catch(Exception error){
       out.append(connectionError(error)).append('\n');
       // A failed TLS/network connection must not cause a second credential transfer.
       if(!(error instanceof GatewayException))break;
     }
   }
   out.append("\nDatos autorizados para esta prueba: token temporal e identificador del coche, con un máximo de dos formatos de token. No se envían contraseña ni PIN. No modifica la región, la vinculación del vehículo ni sus ajustes y no envía órdenes.\n");
   out.append("El informe no contiene el token, el identificador del coche ni el texto completo de las respuestas.\n");
   return redactSession(out.toString(),session);
 }
 private static String redactSession(String report,ControlSession.State session){
   for(String value:new String[]{session.token,session.token.substring(session.token.lastIndexOf('|')+1),session.vehicleId,session.deviceId})
     if(value!=null&&!value.isEmpty())report=report.replace(value,"[omitido]");
   return report;
 }
 private static final class GatewayException extends IOException{
   GatewayException(int status){super("La pasarela respondió HTTP "+status+". No se ha verificado compatibilidad.");}
 }
 static String connectionError(Exception error){
   if(error instanceof java.net.SocketTimeoutException)return "Tiempo de espera agotado.";
   if(error instanceof javax.net.ssl.SSLException)return "No se pudo verificar la conexión segura.";
   if(error instanceof java.net.UnknownHostException)return "No se pudo resolver la dirección del servicio.";
   if(error instanceof GatewayException)return error.getMessage();
   return "No se pudo completar la consulta o la respuesta no tiene el formato esperado.";
 }
 private static String safeCode(JSONObject response){
   String code=response.optString("code","sin código");
   return code.matches("[A-Za-z0-9_-]{1,64}")?code:"sin código";
 }
 private static JSONObject request(String destination,JSONObject body,ControlSession.State session)throws Exception{
   return request(destination,body,session,false);
 }
 private static JSONObject request(String destination,JSONObject body,ControlSession.State session,boolean regionalConsent)throws Exception{
   boolean european=session!=null&&!regionalConsent&&ControlSession.get()==session&&body.length()==1&&
     (EUROPE.equals(destination)&&session.vehicleId.equals(body.optString("carId"))||
      EUROPE_FUNCTIONS.equals(destination)&&session.vehicleId.equals(body.optString("vehicleId")));
   boolean anonymous=CHINA.equals(destination)&&session==null&&!regionalConsent&&body.length()==0;
   boolean regional=CHINA.equals(destination)&&session!=null&&regionalConsent&&ControlSession.get()==session&&
     body.length()==2&&session.vehicleId.equals(body.optString("carId"))&&
     (session.token.equals(body.optString("token"))||session.token.substring(session.token.lastIndexOf('|')+1).equals(body.optString("token")));
   if(!european&&!anonymous&&!regional)
     throw new IOException("Destino no permitido para esta consulta.");
   HttpsURLConnection connection=(HttpsURLConnection)new URL(destination).openConnection();
   connection.setConnectTimeout(15000);connection.setReadTimeout(15000);
   connection.setInstanceFollowRedirects(false);connection.setRequestMethod("POST");connection.setDoOutput(true);
   connection.setRequestProperty("Content-Type","application/json");
   if(european){
     connection.setRequestProperty("devicetype","iPhone");connection.setRequestProperty("apptype","IOS");
     connection.setRequestProperty("appid","cma");connection.setRequestProperty("appversion","V1.2.3");
     connection.setRequestProperty("language","en_US");connection.setRequestProperty("accept-language","en-US;q=1.0");
     connection.setRequestProperty("user-agent","overseas/1.2.3 (com.mazda.mazda6e; build:1; iOS 27.0.0) Alamofire/5.5.0");
     connection.setRequestProperty("deviceid",session.deviceId);connection.setRequestProperty("authorization",session.token);
     if(EUROPE.equals(destination))connection.setRequestProperty("X-Tsp-User-Token",session.token.substring(session.token.lastIndexOf('|')+1));
   }else{
     connection.setRequestProperty("user-agent","MazdaAvisos-ReadOnlyProbe/1.1.13");
     connection.setRequestProperty("vcs-app-id","chang-ma");
     if(regional)connection.setRequestProperty("X-VCS-User-Token",body.getString("token"));
   }
   try{
     try(OutputStream output=connection.getOutputStream()){output.write(body.toString().getBytes(StandardCharsets.UTF_8));}
     int status=connection.getResponseCode();
     if(status!=200)throw new GatewayException(status);
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
