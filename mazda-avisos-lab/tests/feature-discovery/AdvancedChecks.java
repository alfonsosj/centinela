package es.retalis.mazdaavisos;
import org.json.*;
import java.lang.reflect.*;
import java.io.*;
import java.net.*;
import java.security.cert.Certificate;
import java.util.*;
import javax.net.ssl.*;
public final class AdvancedChecks {
 static final List<Mock> calls=new ArrayList<>();
 static boolean failTls;
 static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);}
 static class Mock extends HttpsURLConnection {
  ByteArrayOutputStream sent=new ByteArrayOutputStream();final int index;
  Mock(URL u){super(u);index=calls.size();calls.add(this);}
  public void disconnect(){} public boolean usingProxy(){return false;} public void connect(){}
  public String getCipherSuite(){return "TEST";} public Certificate[] getLocalCertificates(){return null;}
  public Certificate[] getServerCertificates(){return null;}
  public OutputStream getOutputStream()throws IOException{if(failTls)throw new SSLException("PRIVATE-TLS-DATA");return sent;}
  public int getResponseCode(){return 200;}
  public InputStream getInputStream(){
   JSONObject reply=new JSONObject().put("code",index%2==0?"PRIVATE-TSP-TOKEN":"000000").put("success",index%2!=0);
   if(index%2!=0)reply.put("data",new JSONObject().put("token","PRIVATE-TSP-TOKEN").put("vin","PRIVATE-CAR-007")
    .put("items",new JSONArray().put(new JSONObject().put("applicationFuncCode","#sentryMode")
    .put("commandCode","setSentryMode").put("enabled",false))));
   return new ByteArrayInputStream(reply.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
 }
 static void expectGuard(Method method,Object... args)throws Exception{
  int before=calls.size();
  try{method.invoke(null,args);throw new AssertionError("Unexpected allowed route");}
  catch(InvocationTargetException expected){check(expected.getCause() instanceof IOException,"Guard rejects as IO");}
  check(before==calls.size(),"Rejected before HTTP");
 }
 public static void main(String[] args)throws Exception{
  URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){protected URLConnection openConnection(URL u){return new Mock(u);}}:null);
  String eu="https://ca-m.iov.changanauto.com.de/user-apigw/vot-connect-conf-center/api/device/appGetCarConfFunc";
  String cn="https://m.iov.changan.com.cn/cm-apigw/vot-connect-conf-center/api/device/appGetCarConfFunc";
  ControlSession.State session=new ControlSession.State();ControlSession.current=session;
  Method old=FeatureDiscovery.class.getDeclaredMethod("request",String.class,JSONObject.class,ControlSession.State.class);old.setAccessible(true);
  Method regional=FeatureDiscovery.class.getDeclaredMethod("request",String.class,JSONObject.class,ControlSession.State.class,boolean.class);regional.setAccessible(true);
  expectGuard(old,cn,new JSONObject(),session);expectGuard(old,eu,new JSONObject(),null);
  expectGuard(regional,cn,new JSONObject().put("carId",session.vehicleId).put("token",session.token),session,false);
  expectGuard(regional,"https://example.test/control",new JSONObject(),session,true);
  expectGuard(regional,cn,new JSONObject().put("carId","OTHER-CAR").put("token",session.token),session,true);
  expectGuard(regional,cn,new JSONObject().put("carId",session.vehicleId).put("token","OTHER-TOKEN"),session,true);
  expectGuard(regional,cn,new JSONObject().put("carId",session.vehicleId).put("token",session.token).put("pin","123456"),session,true);
  String result=FeatureDiscovery.chinaAuthenticated(session);check(calls.size()==2,"Two format attempts maximum");
  for(int i=0;i<2;i++){
   Mock c=calls.get(i);JSONObject body=new JSONObject(c.sent.toString("UTF-8"));
   check(c.getURL().toString().equals(cn)&&c.getRequestMethod().equals("POST"),"Only configuration endpoint");
   check(!c.getInstanceFollowRedirects(),"No redirects");check(c.getConnectTimeout()==15000&&c.getReadTimeout()==15000,"Timeout bounds");
   check(body.length()==2&&body.getString("carId").equals(session.vehicleId),"Only own car and token");
   check(body.getString("token").equals(i==0?session.token:"PRIVATE-TSP-TOKEN"),"Expected session format");
   check(c.getRequestProperty("authorization")==null&&c.getRequestProperty("deviceid")==null,"No EU authorization or device headers in China");
   check("chang-ma".equals(c.getRequestProperty("vcs-app-id")),"Official app channel");
   check(body.getString("token").equals(c.getRequestProperty("X-VCS-User-Token")),"Chinese auth header");
  }
  check(result.contains("setSentryMode")&&result.contains("enabled=false"),"Literal command metadata");
  for(String secret:new String[]{session.token,"PRIVATE-TSP-TOKEN",session.vehicleId,session.deviceId})check(!result.contains(secret),"No credentials in report");
  calls.clear();failTls=true;result=FeatureDiscovery.chinaAuthenticated(session);
  check(calls.size()==1&&!result.contains("PRIVATE-TLS-DATA"),"TLS failure stops retries and redacts exception");
  calls.clear();failTls=false;ControlSession.current=null;
  try{FeatureDiscovery.chinaAuthenticated(session);throw new AssertionError("Expired session allowed");}catch(IOException expected){}
  check(calls.isEmpty(),"No HTTP with expired session");
  JSONObject nested=new JSONObject().put("sentryModeSwitchSts",0).put("SentryModeSensitivity",2)
   .put("token","PRIVATE-TOKEN").put("deviceId","PRIVATE-DEVICE").put("vin","PRIVATE-VIN").put("latitude",38.123)
   .put("JMZ12345678901234",5).put("sub",new JSONObject().put("APA_FunctionOnOffSts",3).put("unknownText","PRIVATE-CONTENT"));
  FeatureEvidence.reset();FeatureEvidence.observe("MQTT estados",nested);FeatureEvidence.observe("HTTPS Europa",nested);
  String trace=FeatureEvidence.report();check(trace.contains("sentryModeSwitchSts = 0")&&trace.contains("APA_FunctionOnOffSts = 3"),"Received zero and APA state retained");
  for(String secret:new String[]{"PRIVATE-TOKEN","PRIVATE-DEVICE","PRIVATE-VIN","JMZ12345678901234","38.123","PRIVATE-CONTENT"})check(!trace.contains(secret),"Private values excluded");
  check(!trace.contains("latitude")&&!trace.contains("deviceId"),"Sensitive branches excluded");
  FeatureEvidence.observe("PRIVATE-TOPIC",new JSONObject().put("unknownKey",5));check(!FeatureEvidence.report().contains("unknownKey"),"Reject arbitrary source");
  FeatureEvidence.reset();check(!FeatureEvidence.report().contains("sentryModeSwitchSts ="),"Session reset");
  JSONObject huge=new JSONObject();for(int i=0;i<1000;i++)huge.put("field"+i,i);FeatureEvidence.observe("MQTT estados",huge);
  check(FeatureEvidence.report().contains("240")&&FeatureEvidence.report().contains("parcial"),"Inventory bounded");
  JSONObject config=new JSONObject().put("applicationFuncCode","#inCarPhoto").put("commandCode",new JSONArray()).put("token","PRIVATE-TOKEN");
  String report=FeatureDiscovery.analyze(config);check(!report.contains("commandCode=presente"),"Empty command is not present");
  check(report.contains("#inCarPhoto")&&!report.contains("PRIVATE-TOKEN"),"Function discovery privacy");
  JSONObject deep=new JSONObject(),cur=deep;for(int i=0;i<30;i++){JSONObject next=new JSONObject();cur.put("child",next);cur=next;}
  check(FeatureDiscovery.analyze(deep).contains("parcial"),"Bounded depth");
  System.out.println("PASS: credential routing and payload allowlist; regional path; bounded retry; TLS stop; session expiry; privacy; zero states; reset; metadata and bounds.");
 }
}
