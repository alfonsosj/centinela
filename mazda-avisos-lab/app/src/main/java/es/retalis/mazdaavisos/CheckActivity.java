package es.retalis.mazdaavisos;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

/** Activity transparente para iniciar una comprobación tras pulsar el tile. */
public class CheckActivity extends Activity {
 @Override public void onCreate(Bundle savedInstanceState) {
   super.onCreate(savedInstanceState);
   String enabled=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
   String wanted=new ComponentName(this,MazdaMonitor.class).flattenToString();
   boolean ok=false;
   if(enabled!=null)for(String s:enabled.split(":"))if(wanted.equalsIgnoreCase(s)){ok=true;break;}
   if(!ok){
     Toast.makeText(this,"Activa accesibilidad de Mazda Avisos Lab",Toast.LENGTH_LONG).show();
     startActivity(new Intent(this,MainActivity.class));
     finish();return;
   }
   Intent mazda=getPackageManager().getLaunchIntentForPackage("com.cma.mazda.eu");
   if(mazda==null){
     Toast.makeText(this,"No encuentro la aplicación Mazda europea",Toast.LENGTH_LONG).show();
     startActivity(new Intent(this,MainActivity.class));
     finish();return;
   }
   MazdaMonitor.requestSingleCheck();
   Data.status(this,"Comprobación rápida iniciada; abriendo Mazda...");
   startActivity(mazda);
   finish();
 }
}
