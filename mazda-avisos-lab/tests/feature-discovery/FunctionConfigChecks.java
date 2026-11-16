package es.retalis.mazdaavisos;
import org.json.*;
import java.io.*;
import java.net.*;
import java.lang.reflect.*;
import java.security.cert.Certificate;
import java.util.*;
import javax.net.ssl.*;

/** Actual diagnostic/parser with simulated HTTPS, no account or vehicle calls. */
public final class FunctionConfigChecks {
 static final String TSP="https://ca-m.iov.changanauto.com.de/user-apigw/vot-connect-conf-center/api/device/appGetCarConfFunc";
 static final String NATIVE="https://cma-m.iov.changanauto.com.de/cma-app-gw/cma-app-user/api/vehicle/function-config";
 static final List<Mock> requests=new ArrayList<>();
 static String mode="normal";
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
 static class Mock extends HttpsURLConnection {
  final ByteArrayOutputStream sent=new ByteArrayOutputStream();
  Mock(URL url){super(url);requests.add(this);}
  public void disconnect(){} public boolean usingProxy(){return false;} public void connect(){}
  public String getCipherSuite(){return "TEST";} public Certificate[] getLocalCertificates(){return null;} public Certificate[] getServerCertificates(){return null;}
  public OutputStream getOutputStream()throws IOException{
   if(mode.equals("tsp-tls")&&url.toString().equals(TSP))throw new SSLException("PRIVATE-TLS-DATA");return sent;
  }
  public int getResponseCode(){return 200;}
  public InputStream getInputStream(){
   boolean nativeApp=url.toString().equals(NATIVE);
   JSONObject response=new JSONObject().put("success",true).put("code","000000");
   if(mode.equals("tsp-rejected")&&!nativeApp)response.put("success",false).put("code","PRIVATE-TSP-TOKEN");
   else if(mode.equals("native-malformed")&&nativeApp)response.put("data",new JSONObject().put("confList","PRIVATE-UNPARSED"));
   else if(nativeApp)response.put("data",new JSONObject().put("confList",new JSONArray().put("#common").put("#windowControl").put("#sentryMode").put("#DVR"))
    .put("token","PRIVATE-TSP-TOKEN").put("vin","JMZ12345678901234").put("other",new JSONArray().put("PRIVATE-ARRAY")));
   else response.put("data",new JSONArray().put(new JSONObject().put("applicationFuncCode","#common"))
    .put(new JSONObject().put("applicationFuncCode","#windowControl"))
    .put(new JSONObject().put("applicationFuncCode","#outerCarPhoto").put("commandCode","photograph")));
   return new ByteArrayInputStream(response.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
 }
 static void guard(Method request,String destination,JSONObject body,ControlSession.State session,boolean consent)throws Exception{
  int before=requests.size();
  try{request.invoke(null,destination,body,session,consent);throw new AssertionError("Unexpected allowed destination/body");}
  catch(InvocationTargetException expected){check(expected.getCause() instanceof IOException,"Allowlist rejection");}
  check(requests.size()==before,"Reject before HTTP");
 }
 public static void main(String[] args)throws Exception{
  URL.setURLStreamHandlerFactory(protocol->"https".equals(protocol)?new URLStreamHandler(){protected URLConnection openConnection(URL url){return new Mock(url);}}:null);
  ControlSession.State session=new ControlSession.State();ControlSession.current=session;
  String report=FeatureDiscovery.europe(session);check(requests.size()==2,"Two European reads");
  for(Mock request:requests){
   boolean nativeApp=request.getURL().toString().equals(NATIVE);JSONObject body=new JSONObject(request.sent.toString("UTF-8"));
   check(body.length()==1&&body.getString(nativeApp?"vehicleId":"carId").equals(session.vehicleId),"Correct vehicle key per source");
   check(!request.getInstanceFollowRedirects()&&request.getRequestMethod().equals("POST"),"No redirects");
   check(session.token.equals(request.getRequestProperty("authorization")),"Use European authorization");
   check(request.getRequestProperty("vcs-app-id")==null&&request.getRequestProperty("X-VCS-User-Token")==null,"No Chinese auth headers");
   check(nativeApp?request.getRequestProperty("X-Tsp-User-Token")==null:"PRIVATE-TSP-TOKEN".equals(request.getRequestProperty("X-Tsp-User-Token")),"TSP header stays at TSP host");
   check(nativeApp||request.getURL().toString().equals(TSP),"Only the two fixed European destinations");
  }
  check(report.contains("#sentryMode")&&report.contains("#DVR")&&report.contains("#outerCarPhoto"),"Both schemas contribute actual codes");
  check(report.contains("Códigos con el mismo nombre en ambas: 2")&&report.contains("Solo en funciones de la app europea: 2"),"Compare exact names without assuming equivalence");
  for(String secret:new String[]{session.token,"PRIVATE-TSP-TOKEN",session.vehicleId,session.deviceId,"JMZ12345678901234","PRIVATE-ARRAY"})check(!report.contains(secret),"No private data in report");
  mode="tsp-rejected";requests.clear();report=FeatureDiscovery.europe(session);
  check(requests.size()==2&&report.contains("rechazada")&&report.contains("#DVR")&&report.contains("falta una respuesta utilizable"),"Partial failure preserves native result");
  check(!report.contains("PRIVATE-TSP-TOKEN"),"Redact credential echoed as error code");
  mode="tsp-tls";requests.clear();report=FeatureDiscovery.europe(session);
  check(report.contains("#DVR")&&!report.contains("PRIVATE-TLS-DATA"),"Independent TLS-verified destination still works");
  mode="native-malformed";requests.clear();report=FeatureDiscovery.europe(session);
  check(report.contains("sin una lista confList reconocible")&&report.contains("#outerCarPhoto")&&!report.contains("PRIVATE-UNPARSED"),"Malformed schema is unknown, not zero functions");
  check(FeatureDiscovery.analyze(new JSONObject().put("commandCode","setSentryMode")).contains("Mando sin función asociada"),"Retain orphan command hint");
  String flat=FeatureDiscovery.analyze(new JSONObject().put("confList",new JSONArray().put("#DVR").put("JMZ12345678901234").put("aabbccddeeff00112233445566778899").put(123456)));
  check(flat.contains("#DVR")&&!flat.contains("JMZ12345678901234")&&!flat.contains("aabbccddeeff")&&!flat.contains("123456"),"Flat-list privacy filters");
  Method request=FeatureDiscovery.class.getDeclaredMethod("request",String.class,JSONObject.class,ControlSession.State.class,boolean.class);request.setAccessible(true);
  guard(request,NATIVE,new JSONObject().put("carId",session.vehicleId),session,false);
  guard(request,NATIVE,new JSONObject().put("vehicleId","ANOTHER-CAR"),session,false);
  guard(request,NATIVE,new JSONObject().put("vehicleId",session.vehicleId).put("pin","123456"),session,false);
  guard(request,NATIVE,new JSONObject().put("vehicleId",session.vehicleId),session,true);
  guard(request,"https://example.test/function-config",new JSONObject().put("vehicleId",session.vehicleId),session,false);
  mode="normal";session.vehicleId="123456789";requests.clear();FeatureDiscovery.europe(session);
  check(new JSONObject(requests.get(1).sent.toString("UTF-8")).get("vehicleId") instanceof Number,"Native numeric vehicle IDs keep numeric shape");
  ControlSession.current=null;int before=requests.size();
  try{FeatureDiscovery.europe(session);throw new AssertionError("Expired session allowed");}catch(IOException expected){}
  check(requests.size()==before,"No network with expired session");
  System.out.println("PASS: both European schemas, comparison, flat-code privacy, command hints, body/header allowlists, partial failures and session expiry. HTTPS simulated.");
 }
}
