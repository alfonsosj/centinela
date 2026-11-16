package es.retalis.mazdaavisos;
import android.content.Context;
import org.json.JSONObject;
import java.security.PublicKey;
import javax.crypto.Cipher;

final class ConnectionTestActivity {
 static final String SERVER_RSA=java.util.Base64.getEncoder().encodeToString(RemoteLockChecks.SERVER.getPublic().getEncoded());
 static String encrypt(PublicKey key,String text)throws Exception{
   Cipher cipher=Cipher.getInstance("RSA/ECB/PKCS1Padding");cipher.init(Cipher.ENCRYPT_MODE,key);
   return java.util.Base64.getEncoder().encodeToString(cipher.doFinal(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
 }
}
final class VehicleControlActivity {
 static final String WINDOWS_OPEN="windows_open",WINDOWS_CLOSE="windows_close",TRUNK_OPEN="trunk_open",TRUNK_CLOSE="trunk_close",
   CLIMATE_ON="climate_on",CLIMATE_OFF="climate_off",FIND="find_vehicle",HORN="honk_horn",DEFROST_ON="defrost_on",DEFROST_OFF="defrost_off",
   WHEEL_ON="wheel_on",WHEEL_OFF="wheel_off",CHARGE_LIMIT="charge_limit",DRIVER_HEAT="driver_heat",PASSENGER_HEAT="passenger_heat",
   DRIVER_VENT="driver_vent",PASSENGER_VENT="passenger_vent";
}
final class VehicleSnapshot {static boolean observeApi(Context c,JSONObject data){return data!=null;}}
final class AlertRules {
 static final String PARKED_OPEN_REMINDER="reminder",AUTO_LOCK="automatic";
 static boolean enabled(Context c,String key){return c.options.getOrDefault(key,true);}
}
final class MainActivity {static void alert(Context c,String title,String body,int id){}}
final class AlertHistory {static void add(Context c,String title,String body){}}
