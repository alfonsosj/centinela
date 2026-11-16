package es.retalis.mazdaavisos;
import android.content.Context;
import android.os.SystemClock;
import org.json.*;
import java.io.*;
import java.net.*;
import java.lang.reflect.Field;
import java.security.*;
import java.security.cert.Certificate;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.crypto.Cipher;
import javax.net.ssl.*;

public final class RemoteLockChecks {
 static final KeyPair SERVER=pair(),CLIENT=pair();
 static final Context CONTEXT=new Context();
 static final AtomicBoolean allowed=new AtomicBoolean(true);
 static final List<String> routes=new ArrayList<>();
 static String scenario="normal";
 static int submissions;
 static boolean signatureVerified,pinVerified;
 static KeyPair pair(){try{KeyPairGenerator generator=KeyPairGenerator.getInstance("RSA");generator.initialize(2048);return generator.generateKeyPair();}catch(Exception e){throw new AssertionError(e);}}
 static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
 static ControlSession.State session(String car){ControlSession.set("test|tsp","test-device",car,CLIENT.getPrivate(),"123456".toCharArray());return ControlSession.get();}
 static void reset(String value){scenario=value;submissions=0;signatureVerified=false;pinVerified=false;routes.clear();allowed.set(true);}
 static class Mock extends HttpsURLConnection {
  final ByteArrayOutputStream body=new ByteArrayOutputStream();
  Mock(URL url){super(url);}
  public void connect(){} public void disconnect(){} public boolean usingProxy(){return false;}
  public String getCipherSuite(){return "TEST";} public Certificate[] getLocalCertificates(){return null;} public Certificate[] getServerCertificates(){return null;}
  public OutputStream getOutputStream(){return body;}
  public int getResponseCode(){return scenario.equals("http-failure")?503:200;}
  public InputStream getInputStream()throws IOException{
   try{
    check(url.getHost().equals("cma-m.iov.changanauto.com.de"),"Commands only use Europe");
    check(!getInstanceFollowRedirects()&&getRequestMethod().equals("POST"),"HTTPS POST without redirect");
    JSONObject request=new JSONObject(body.toString("UTF-8")),response=new JSONObject().put("success",true);
    String path=url.getPath();routes.add(path);
    if(path.endsWith("/vehicle/condition/v2")){
     JSONObject doors=new JSONObject().put("driverLock",submissions>0||scenario.startsWith("already-locked")?0:1)
      .put("passengerLock",submissions>0||scenario.startsWith("already-locked")?0:1).put("trunk",scenario.contains("trunk-open")?1:0);
     if(submissions>0&&scenario.equals("unconfirmed"))doors.remove("passengerLock");
     if(submissions>0&&scenario.equals("invalid-state"))doors.put("driverLock",0.5);
     JSONObject data=new JSONObject().put("door",doors);
     if(!scenario.equals("missing-status"))data.put("vehicleStatus",new JSONObject().put("status",scenario.equals("driving")?1:2));
     response.put("data",data);
    }else if(path.endsWith("/security-code/check-code")){
     Cipher cipher=Cipher.getInstance("RSA/ECB/PKCS1Padding");cipher.init(Cipher.DECRYPT_MODE,SERVER.getPrivate());
     pinVerified=new String(cipher.doFinal(java.util.Base64.getDecoder().decode(request.getString("safeCode"))),"UTF-8").equals("123456");
     check(pinVerified,"PIN still authorized through original encrypted protocol");
     response.put("data",new JSONObject().put("rcToken","test-rc"));
     if(scenario.equals("pin-denied"))response.put("success",false).put("code","DENIED");
    }else if(path.endsWith("/serial-no/get")){
     Cipher cipher=Cipher.getInstance("RSA/ECB/PKCS1Padding");cipher.init(Cipher.ENCRYPT_MODE,CLIENT.getPublic());
     response.put("data",java.util.Base64.getEncoder().encodeToString(cipher.doFinal("test-serial".getBytes("UTF-8"))));
     if(scenario.equals("cancel-before-command"))allowed.set(false);
    }else if(path.endsWith("/control/doors")){
     submissions++;check(request.get("open").equals(false),"Automatic path cannot unlock");
     check(request.getString("vehicleId").equals("test-car"),"Targets only the session vehicle");
     String canonical="open=false&rcToken=test-rc&seriralNo=test-serial&vehicleId=test-car";
     Signature verifier=Signature.getInstance("SHA256withRSA");verifier.initVerify(CLIENT.getPublic());verifier.update(canonical.getBytes("UTF-8"));
     signatureVerified=verifier.verify(java.util.Base64.getDecoder().decode(request.getString("sign")));
     check(signatureVerified,"Signed lock payload preserved");response.put("data",new JSONObject().put("commandId","test-command"));
    }else if(path.endsWith("/control/control-result"))response.put("data",new JSONObject().put("resultCode",scenario.equals("control-denied")?999:0));
    else throw new AssertionError("Unexpected endpoint "+path);
    return new ByteArrayInputStream(response.toString().getBytes("UTF-8"));
   }catch(Exception failure){throw new IOException(failure);}
  }
 }
 static void cancel(MazdaRemoteApi api,ControlSession.State session,String which)throws Exception{
  reset(which);try{api.lockAutomatically(session,"123456",allowed::get);throw new AssertionError("Expected cancellation");}catch(CancellationException expected){}
  check(submissions==0,"No physical request for "+which);
 }
 static void credentialChecks()throws Exception{
  AutomaticLockController.monitorStopped();SystemClock.now=1000;
  ControlSession.State first=session("same-car");AutomaticLockController.monitorStarted(CONTEXT,"same-car");
  Field pin=AutomaticLockController.class.getDeclaredField("pin"),state=AutomaticLockController.class.getDeclaredField("state");pin.setAccessible(true);state.setAccessible(true);
  char[] lease=(char[])pin.get(null);check(lease!=null&&state.get(null)==first,"Monitor captures lock-only lease");
  SystemClock.now+=ControlSession.TTL_MS+1;
  check(ControlSession.get()==null&&pin.get(null)==lease,"Manual expiry does not silently disable authorized auto-lock");
  CONTEXT.options.put(AlertRules.AUTO_LOCK,false);AutomaticLockController.settingsChanged(CONTEXT);
  check(pin.get(null)==null&&state.get(null)==null,"Disable removes authorization");for(char value:lease)check(value=='\0',"PIN copy erased");
  CONTEXT.options.put(AlertRules.AUTO_LOCK,true);AutomaticLockController.settingsChanged(CONTEXT);check(pin.get(null)==null,"Expired manual PIN cannot restore lease");
  ControlSession.State renewed=session("same-car");AutomaticLockController.sessionAvailable(CONTEXT);check(state.get(null)==renewed,"Same vehicle can renew lease");
  session("another-car");AutomaticLockController.sessionAvailable(CONTEXT);check(state.get(null)==renewed,"Other vehicle cannot borrow old monitor evidence");
  ControlSession.clear();check(pin.get(null)==null&&state.get(null)==null,"Logout clears lock authorization");
  session("same-car");AutomaticLockController.sessionAvailable(CONTEXT);lease=(char[])pin.get(null);AutomaticLockController.monitorStopped();
  check(pin.get(null)==null&&state.get(null)==null,"Stopping monitor clears lease");for(char value:lease)check(value=='\0',"Stop erases PIN");
 }
 public static void main(String[] args)throws Exception{
  URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL url){return new Mock(url);}}:null);
  credentialChecks();ControlSession.State session=session("test-car");MazdaRemoteApi api=new MazdaRemoteApi(CONTEXT,null);
  reset("normal");allowed.set(false);try{api.lockAutomatically(session,"123456",allowed::get);throw new AssertionError("Expected guard");}catch(CancellationException expected){}
  check(routes.isEmpty(),"No request without current eligibility");
  for(String which:new String[]{"driving","missing-status","trunk-open","already-locked-trunk-open","cancel-before-command"})cancel(api,session,which);
  reset("already-locked");check(api.lockAutomatically(session,"123456",allowed::get).confirmed&&submissions==0&&routes.size()==1,"No repeat lock if already confirmed closed");
  reset("normal");MazdaRemoteApi.Result success=api.lockAutomatically(session,"123456",allowed::get);
  check(success.confirmed&&submissions==1&&pinVerified&&signatureVerified,"One authorized signed lock with both locks verified");
  for(String which:new String[]{"pin-denied","http-failure"}){reset(which);try{api.lockAutomatically(session,"123456",allowed::get);throw new AssertionError("Expected rejection");}catch(IOException expected){}check(submissions==0,"No lock for "+which);}
  reset("control-denied");try{api.lockAutomatically(session,"123456",allowed::get);throw new AssertionError("Expected command denial");}catch(IOException expected){}check(submissions==1,"No uncontrolled retry after command denial");
  reset("unconfirmed");check(!api.lockAutomatically(session,"123456",allowed::get).confirmed,"One lock alone does not claim success");
  reset("invalid-state");check(!api.lockAutomatically(session,"123456",allowed::get).confirmed,"Fractional lock state cannot confirm closure");
  AutomaticLockController.monitorStopped();ControlSession.clear();
  System.out.println("PASS: lock-only authorization lifecycle and vehicle binding; preflight and cancellation; encrypted PIN; signed lock-only payload; no success without both valid lock states. All HTTPS simulated.");
 }
}
