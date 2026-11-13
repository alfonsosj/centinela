package es.retalis.mazdaavisos;

import android.content.Context;
import android.os.SystemClock;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.*;

final class MqttProbe {
 interface Status { void update(String value); }
 private final JSONObject cfg; private final String pass; private final Context app;
 private final List<String> subs=new ArrayList<>(); private String loginIn,loginOut,identity,host,secret;
 private SSLSocket socket; private int reports=0,events=0,doorChanges=0; private Integer lock=null;
 private final AlertRules alerts;
 private boolean warnedEvent=false; private volatile boolean stopped=false;
 MqttProbe(Context ctx,JSONObject config,String auth){this(ctx,config,auth,null);}
 MqttProbe(Context ctx,JSONObject config,String auth,AlertRules shared){
   app=ctx.getApplicationContext();cfg=config;pass=auth;
   alerts=shared!=null?shared:new AlertRules(app);
 }
 void setInitialDoorLock(Integer value){if(value!=null&&(value==0||value==1))alerts.setInitialDoorLock(value);}
 Integer getLastDoorLock(){return alerts.getLastDoorLock();}
 void close(){stopped=true;try{if(socket!=null)socket.close();}catch(Exception ignored){}}
 private static String str(byte[] data){return new String(data,StandardCharsets.UTF_8);}
 private static void utf(ByteArrayOutputStream b,String s)throws IOException{
   byte[] data=s.getBytes(StandardCharsets.UTF_8);if(data.length>65535)throw new IOException("Campo MQTT largo");
   b.write(data.length>>8);b.write(data.length&255);b.write(data);
 }
 private static void send(OutputStream out,int type,byte[] b)throws IOException{
   out.write(type);int size=b.length;
   do{int digit=size%128;size/=128;if(size>0)digit|=128;out.write(digit);}while(size>0);
   out.write(b);out.flush();
 }
 private static Frame read(InputStream in)throws IOException{
   int h=in.read();if(h<0)throw new EOFException("Conexión MQTT cerrada");
   int size=0,shift=1;boolean end=false;
   try{
     for(int n=0;n<4;n++){int b=in.read();if(b<0)throw new EOFException("Trama incompleta");
       size+=(b&127)*shift;if((b&128)==0){end=true;break;}shift*=128;}
   }catch(java.net.SocketTimeoutException e){throw new IOException("Cabecera MQTT incompleta",e);}
   if(!end||size>1024*1024)throw new IOException("Trama MQTT inválida");
   byte[] bytes=new byte[size];
   try{new DataInputStream(in).readFully(bytes);}
   catch(java.net.SocketTimeoutException e){throw new IOException("Trama MQTT incompleta; reconexión necesaria",e);}
   return new Frame(h,bytes);
 }
 private static class Frame{int header;byte[] data;Frame(int h,byte[] d){header=h;data=d;}int type(){return header>>4;}}
 private static class Msg{String topic;JSONObject json;int packetId;boolean retained;}
 private static int[] var(byte[] d,int p)throws IOException{
   int val=0,m=1;for(int i=0;i<4;i++){if(p>=d.length)throw new IOException("Propiedades MQTT incompletas");
     int b=d[p++]&255;val+=(b&127)*m;if((b&128)==0)return new int[]{val,p};m*=128;}
   throw new IOException("Propiedades MQTT inválidas");
 }
 private static Msg parse(Frame f)throws Exception{
   byte[] d=f.data;if(d.length<3)throw new IOException("PUBLISH vacío");
   int count=((d[0]&255)<<8)|(d[1]&255),p=2;if(p+count>d.length)throw new IOException("Topic truncado");
   Msg m=new Msg();m.topic=new String(d,p,count,StandardCharsets.UTF_8);p+=count;
   int qos=(f.header>>1)&3;if(qos>1)throw new IOException("QoS no soportado");
   if(qos!=0){if(p+2>d.length)throw new IOException("Paquete incompleto");m.packetId=((d[p]&255)<<8)|(d[p+1]&255);p+=2;}
   int[] prop=var(d,p);p=prop[1]+prop[0];if(p>d.length)throw new IOException("Propiedades corruptas");
   m.retained=(f.header&1)!=0;m.json=new JSONObject(new String(d,p,d.length-p,StandardCharsets.UTF_8));
   return m;
 }
 private static void ack(OutputStream out,Msg m)throws IOException{
   if(m.packetId!=0){out.write(new byte[]{0x40,0x04,(byte)(m.packetId>>8),(byte)m.packetId,0,0});out.flush();}
 }
 private void prepare()throws Exception{
   JSONObject info=cfg.getJSONArray("mqttConnectionInfos").getJSONObject(0);
   JSONObject broker=info.getJSONArray("clusterInfos").getJSONObject(0);
   host=broker.optString("brokerUrl").replaceFirst("^ssl://","");
   if(broker.optInt("brokerPort",8883)!=8883||!host.matches("[A-Za-z0-9.-]{4,200}"))throw new IOException("Broker inválido");
   JSONArray topics=info.getJSONArray("topicInfos");LinkedHashSet<String> chosen=new LinkedHashSet<>();
   for(int i=0;i<topics.length();i++){
     JSONObject e=topics.optJSONObject(i);if(e==null)continue;
     String kind=e.optString("msgType");
     if(kind.equals("loginout")){loginIn=find(e.optJSONArray("pubTopics"),"/loginout/req");loginOut=find(e.optJSONArray("subTopics"),"/loginout/res");}
     if(kind.equals("events")||kind.equals("properties")){
       JSONArray a=e.optJSONArray("subTopics");if(a==null)continue;
       for(int j=0;j<a.length();j++){String v=a.optString(j);
         if((kind.equals("events")&&v.endsWith("/events/up"))||(kind.equals("properties")&&v.endsWith("/properties/report")))chosen.add(v);}
     }
   }
   if(loginIn==null||loginOut==null||chosen.isEmpty())throw new IOException("Canales incompletos");
   String[] ids=loginIn.split("/");if(ids.length<3||!ids[0].equals("$vdp"))throw new IOException("Identidad inválida");
   identity=ids[1];subs.add(loginOut);subs.addAll(chosen);
 }
 private static String find(JSONArray a,String ending){if(a==null)return null;
   for(int i=0;i<a.length();i++){String s=a.optString(i);if(s.endsWith(ending))return s;}return null;}
 private byte[] connect()throws Exception{
   ByteArrayOutputStream b=new ByteArrayOutputStream();
   utf(b,"MQTT");b.write(new byte[]{5,(byte)0xC2,0,60,0});utf(b,identity);utf(b,identity);utf(b,pass);return b.toByteArray();
 }
 private byte[] subscribe()throws Exception{
   ByteArrayOutputStream b=new ByteArrayOutputStream();b.write(new byte[]{0,1,0});
   for(String topic:subs){utf(b,topic);b.write(1);}return b.toByteArray();
 }
 private byte[] appLogin(String request)throws Exception{
   JSONObject p=new JSONObject().put("did",identity).put("r",request).put("v","v1.0.0")
     .put("mt","loginout").put("e",0).put("z","unzip").put("tf",0)
     .put("dt",java.time.Instant.now().toString())
     .put("sers",new JSONArray().put(new JSONObject().put("service_code","login")
       .put("params",new JSONObject().put("encryptEnable",1).put("zipType","gzip").put("ts",System.currentTimeMillis()))))
     .put("b",new JSONObject().put("ruid",identity));
   ByteArrayOutputStream b=new ByteArrayOutputStream();utf(b,loginIn);b.write(0);b.write(p.toString().getBytes(StandardCharsets.UTF_8));return b.toByteArray();
 }
 private String loginSecret(JSONObject o){
   JSONArray a=o.optJSONArray("rs");if(a==null)return null;
   for(int i=0;i<a.length();i++){JSONObject s=a.optJSONObject(i);if(s==null)continue;
     for(String key:new String[]{"params","data"}){JSONObject v=s.optJSONObject(key);if(v!=null&&!v.optString("secretKey").isEmpty())return v.optString("secretKey");}}
   return null;
 }
 private JSONObject values(JSONObject p){
   JSONObject combined=new JSONObject();
   String request=p.optString("r","");
   for(String field:new String[]{"rs","sers"}){
     Object raw=p.opt(field);JSONArray services=null;
     if(raw instanceof JSONArray)services=(JSONArray)raw;
     if(raw instanceof String&&secret!=null&&!request.isEmpty()){
       try{
         byte[] cipherBytes=Base64.decode((String)raw,Base64.DEFAULT);
         byte[] iv=MessageDigest.getInstance("MD5").digest(request.getBytes(StandardCharsets.UTF_8));
         Cipher aes=Cipher.getInstance("AES/CBC/PKCS5Padding");
         aes.init(Cipher.DECRYPT_MODE,new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"AES"),new IvParameterSpec(iv));
         byte[] packed=Base64.decode(str(aes.doFinal(cipherBytes)),Base64.DEFAULT);
         try(GZIPInputStream gzip=new GZIPInputStream(new ByteArrayInputStream(packed))){
           ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[2048];int n;
           while((n=gzip.read(buf))!=-1){out.write(buf,0,n);if(out.size()>32768)throw new IOException("Payload demasiado grande");}
           services=new JSONArray(str(out.toByteArray()));
         }
       }catch(Exception ignored){}
     }
     if(services==null)continue;
     for(int i=0;i<services.length();i++){
       JSONObject service=services.optJSONObject(i);if(service==null||service.opt("success")==Boolean.FALSE)continue;
       for(String key:new String[]{"data","params"}){JSONObject o=service.optJSONObject(key);
         if(o!=null){JSONArray names=o.names();if(names!=null)for(int j=0;j<names.length();j++){String name=names.optString(j);try{combined.put(name,o.opt(name));}catch(JSONException ignored){}}}}
     }
   }
   return combined;
 }
 private void handle(Msg m){
   if(m.retained)return;
   if(m.topic.endsWith("/events/up")){
     events++;
     if(!warnedEvent){warnedEvent=true;
       MainActivity.alert(app,"Evento MQTT Mazda (sin clasificar)","El servidor ha emitido un evento. Todavía no sabemos si corresponde a un aviso del Centro de Mensajes.",2401);
     }
   }
   if(m.topic.endsWith("/properties/report")){
     reports++;
     JSONObject parsed=values(m.json);
     alerts.ingest(parsed);
     doorChanges=alerts.getLockChanges();
   }
 }
 private String status(){return "MQTT conectado\nInformes properties/report: "+reports+
   "\nEventos events/up: "+events+"\nCambios de cierre confirmados: "+doorChanges+
   "\nAviso de evento genérico: "+(warnedEvent?"enviado":"ninguno")+
   "\n"+alerts.summary()+"\nMonitor experimental: no es una alarma de seguridad.";}
 void run(Status feedback,int seconds)throws Exception{
   prepare();feedback.update("Conectando a MQTT Europa...");
   try{
     SSLSocket s=(SSLSocket)SSLSocketFactory.getDefault().createSocket(host,8883);socket=s;
     SSLParameters params=s.getSSLParameters();params.setEndpointIdentificationAlgorithm("HTTPS");s.setSSLParameters(params);
     s.setSoTimeout(15000);s.startHandshake();
     InputStream in=s.getInputStream();OutputStream out=s.getOutputStream();
     send(out,0x10,connect());Frame ack=read(in);
     if(ack.type()!=2||ack.data.length<3||(ack.data[1]&255)!=0)throw new IOException("MQTT CONNECT rechazado");
     send(out,0x82,subscribe());
     boolean subscribed=false;long limit=SystemClock.elapsedRealtime()+25000;
     while(!stopped&&SystemClock.elapsedRealtime()<limit){
       Frame f=read(in);
       if(f.type()==3)ack(out,parse(f));
       if(f.type()==9){subscribed=true;break;}
     }
     if(!subscribed)throw new IOException("MQTT SUBACK no recibido");
     String request=identity+"_"+(System.currentTimeMillis()*1000L);
     send(out,0x30,appLogin(request));
     limit=SystemClock.elapsedRealtime()+25000;
     while(!stopped&&SystemClock.elapsedRealtime()<limit&&secret==null){
       Frame f=read(in);
       if(f.type()!=3)continue;Msg m=parse(f);ack(out,m);
       if(m.topic.equals(loginOut)&&request.equals(m.json.optString("r")))secret=loginSecret(m.json);
     }
     if(secret==null)throw new IOException("Login MQTT de aplicación no confirmado");
     feedback.update("MQTT conectado. Recibiendo informes...");
     s.setSoTimeout(4000);
     long until=SystemClock.elapsedRealtime()+seconds*1000L;
     long ping=SystemClock.elapsedRealtime()+20000,update=SystemClock.elapsedRealtime()+10000;
     long pongDeadline=0;
     while(!stopped&&SystemClock.elapsedRealtime()<until){
       long t=SystemClock.elapsedRealtime();
       if(pongDeadline>0&&t>pongDeadline)throw new IOException("Sin respuesta al heartbeat MQTT");
       if(t>ping&&pongDeadline==0){
         out.write(new byte[]{(byte)0xc0,0});out.flush();
         pongDeadline=t+35000;ping=t+20000;
       }
       try{Frame f=read(in);if(f.type()==13)pongDeadline=0;
          if(f.type()==3){Msg m=parse(f);ack(out,m);
          if(subs.contains(m.topic))handle(m);}
          if(f.type()==14)throw new IOException("El broker cortó la conexión");
       }catch(java.net.SocketTimeoutException expected){}
       t=SystemClock.elapsedRealtime();
       if(t>update){feedback.update(status());update=t+10000;}
     }
     feedback.update("Prueba finalizada\n"+status());
   }finally{close();}
 }
}