package es.retalis.mazdaavisos;

import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** Bounded, RAM-only evidence: field names and explicitly allowed numeric states. */
final class FeatureEvidence {
 private static final TreeMap<String,Integer> reports=new TreeMap<>();
 private static final TreeSet<String> fields=new TreeSet<>();
 private static final TreeMap<String,String> states=new TreeMap<>();
 private static long startedAt=System.currentTimeMillis();
 private static boolean limited;
 private static final String[] TARGETS={"sentrymodeswitchsts","sentrymodesensitivity","sentrymodeonlength",
   "sentrymodeperhourmile","sentrymodeactiveendtime","apafunctiononoffsts","apaparknotice5",
   "photolocation","powerdriverswitchstatus","dvrstatus","dvrstate","recordingstatus","camerastatus"};
 private static String norm(String value){return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]","");}
 private static boolean sensitive(String key){
   String n=norm(key);
   return n.contains("token")||n.contains("password")||n.contains("secret")||n.contains("credential")||
     n.contains("privatekey")||n.contains("publickey")||n.contains("certificate")||n.contains("authorization")||
     n.contains("session")||n.contains("account")||n.contains("phone")||n.contains("email")||
     n.contains("latitude")||n.contains("longitude")||n.contains("location")&&!n.equals("photolocation")||
     n.contains("gps")||n.contains("coordinate")||n.contains("topic")||n.contains("device")||
     n.equals("vin")||n.endsWith("vin")||n.equals("pin")||n.equals("safecode")||n.equals("securitycode")||
     n.equals("carid")||n.equals("vehicleid")||n.equals("userid")||n.equals("serialnumber")||
     n.equals("did")||n.equals("ruid")||n.equals("sn");
 }
 static synchronized void reset(){
   reports.clear();fields.clear();states.clear();limited=false;startedAt=System.currentTimeMillis();
 }
 static synchronized void observe(String source,JSONObject raw){
   if(raw==null||raw.length()==0)return;
   // The source is chosen by our caller; never accept an incoming topic as a label.
   if(!source.equals("HTTPS Europa")&&!source.equals("MQTT estados")&&!source.equals("MQTT eventos"))return;
   reports.put(source,Math.min(1000000,reports.getOrDefault(source,0)+1));
   visit(raw,"",source,0,new int[]{0});
 }
 private static void visit(Object node,String path,String source,int depth,int[] count){
   if(depth>16||++count[0]>6000){limited=true;return;}
   if(node instanceof JSONArray){
     JSONArray array=(JSONArray)node;
     for(int i=0;i<array.length()&&count[0]<6000;i++)visit(array.opt(i),path+"[]",source,depth+1,count);
   }else if(node instanceof JSONObject){
     JSONObject object=(JSONObject)node;Iterator<String> keys=object.keys();
     while(keys.hasNext()&&count[0]<6000){
       String key=keys.next();
       if(!key.matches("[A-Za-z_][A-Za-z0-9_]{0,63}")||key.matches("[A-HJ-NPR-Z0-9]{17}")||
          key.matches("[a-fA-F0-9]{24,}")||sensitive(key))continue;
       String next=path.isEmpty()?key:path+"."+key;
       if(next.length()>180){limited=true;continue;}
       Object value=object.opt(key);
       if(value instanceof JSONObject||value instanceof JSONArray){visit(value,next,source,depth+1,count);continue;}
       if(fields.size()<240)fields.add(source+" · "+next);else limited=true;
       String n=norm(key);
       for(String target:TARGETS)if(n.equals(target)){
         String number=safeNumber(value);
         if(number!=null&&states.size()<64)states.put(source+" · "+next,number);
         break;
       }
     }
   }
 }
 private static String safeNumber(Object value){
   if(value==null||value==JSONObject.NULL||value instanceof Boolean)return null;
   String text=String.valueOf(value);
   if(!text.matches("-?[0-9]{1,13}(\\.[0-9]{1,4})?"))return null;
   try{double number=Double.parseDouble(text);return Double.isFinite(number)&&number>=0&&number<=9999999999999d?text:null;}
   catch(NumberFormatException ignored){return null;}
 }
 static synchronized String report(){
   StringBuilder out=new StringBuilder("RASTRO DE FUNCIONES EN LA TELEMETRÍA EUROPEA\n");
   out.append("Observación desde: ").append(DateFormat.getDateTimeInstance().format(new Date(startedAt))).append("\n\n");
   if(reports.isEmpty())out.append("Todavía no se han recibido datos en esta ejecución. Conecta la cuenta Mazda y deja el monitor activo unos minutos.\n");
   for(Map.Entry<String,Integer> entry:reports.entrySet())out.append(entry.getKey()).append(": ").append(entry.getValue()).append(" mensajes con datos.\n");
   out.append("\nCampos específicos de Centinela / cámaras / aparcamiento: ");
   if(states.isEmpty())out.append("no observados con valor numérico en los datos recibidos.\n");
   else{
     out.append(states.size()).append("\n");
     for(Map.Entry<String,String> entry:states.entrySet())out.append("• ").append(entry.getKey()).append(" = ").append(entry.getValue()).append('\n');
   }
   out.append("\nUn campo recibido, incluso con valor 0, es una pista de protocolo; no confirma que se puedan ejecutar órdenes. Un campo ausente no demuestra que falte el hardware.\n\n");
   out.append("Nombres de campos observados: ").append(fields.size()).append("\n");
   for(String field:fields)out.append("• ").append(field).append('\n');
   if(limited)out.append("\nInventario limitado; puede ser parcial.\n");
   out.append("\nSolo se conservan en memoria nombres de campos y los estados numéricos anteriores. No se incluyen ubicaciones, VIN, cuentas, mensajes completos, PIN ni tokens. La observación se reinicia al autenticar otra sesión o cerrar el proceso.");
   return out.toString();
 }
 private FeatureEvidence(){}
}
