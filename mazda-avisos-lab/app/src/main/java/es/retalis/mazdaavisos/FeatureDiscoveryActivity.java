package es.retalis.mazdaavisos;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explicit read-only checks; sending a session to China requires its own disclosure. */
public final class FeatureDiscoveryActivity extends SafeAreaActivity {
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private final ArrayList<Button> checks=new ArrayList<>();
 private final String[] results=new String[4];
 private TextView report,sessionStatus;
 private boolean busy;
 @Override public void onCreate(Bundle state){
   super.onCreate(state);
   if(state!=null){String[] saved=state.getStringArray("reports");if(saved!=null&&saved.length==4)System.arraycopy(saved,0,results,0,4);}
   ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xFFF4F5FB);
   LinearLayout root=new LinearLayout(this);root.setOrientation(1);int pad=dp(18);root.setPadding(pad,pad,pad,dp(30));
   scroll.addView(root);setContentView(scroll);
   button(root,"‹ Volver",this::finish);
   TextView title=text("Diagnóstico China / Europa",23);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);root.addView(title);
   root.addView(text("Investigamos Centinela, cámaras, DVR y aparcamiento. Todas las pruebas de esta pantalla son de lectura. La app china incluye estas funciones, pero su presencia no confirma que estén habilitadas en tu CX-6e europeo.",15));
   sessionStatus=text("",14);root.addView(sessionStatus);
   button(root,"Conectar / autenticar mi cuenta Mazda",()->startActivity(new Intent(this,ConnectionTestActivity.class)));
   checks.add(button(root,"1. Leer funciones y códigos de mando",()->runCheck(0)));
   root.addView(text("Consulta europea: busca funciones, códigos de mando y marcas de disponibilidad. Los valores se muestran sin asumir que permiten activar la función.",14));
   checks.add(button(root,"2. Buscar rastros en los datos del coche",()->runCheck(1)));
   root.addView(text("Actualiza el estado por el servicio europeo y revisa los campos recibidos por HTTPS y MQTT desde la última autenticación. Deja el monitor activo unos minutos y repite esta prueba para ampliar la observación.",14));
   checks.add(button(root,"3. Comprobar China sin credenciales",()->runCheck(2)));
   root.addView(text("Consulta anónima. Comprueba si responde la pasarela china; no verifica si admite tu cuenta ni tu coche.",14));
   checks.add(button(root,"4. Probar mi sesión en China",this::confirmRegionalRead));
   root.addView(text("Prueba opcional: envía a la pasarela china el token temporal europeo y el identificador del coche para consultar compatibilidad. Verás los datos que se envían antes de aceptarla. No cambia de región ni envía órdenes.",14));
   root.addView(text("Hallazgos de la app china: Centinela utiliza setSentryMode; las fotos requieren configuración por vehículo y código de mando; el módulo de aparcamiento usa la llave Bluetooth y comunicación continua. Esos nombres no son órdenes listas para ejecutar en Europa.",14));
   button(root,"Copiar informe completo",()->{
     ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
     if(clipboard!=null){clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico Mazda",combinedReport()));Toast.makeText(this,"Informe copiado",Toast.LENGTH_SHORT).show();}
   });
   report=text(combinedReport(),15);report.setTextIsSelectable(true);root.addView(report);
 }
 @Override protected void onResume(){super.onResume();showSession();}
 private void showSession(){
   sessionStatus.setText(ControlSession.get()==null?"Sesión de lectura: autentica la cuenta y vuelve a esta pantalla.":"Sesión europea disponible. Caduca a los 15 minutos de su creación.");
 }
 private void confirmRegionalRead(){
   if(busy)return;
   if(ControlSession.get()==null){authenticate();return;}
   new AlertDialog.Builder(this).setTitle("Enviar sesión a la plataforma china")
     .setMessage("Esta prueba enviará tu token temporal de acceso europeo y el identificador del coche al servidor chino de Changan Mazda (m.iov.changan.com.cn). El token es una credencial de acceso a tu cuenta. Se harán como máximo dos consultas de configuración, usando los dos formatos de token conocidos en el servicio europeo.\n\nNo se envían la contraseña ni el PIN. No se cambia la vinculación del coche ni la región y no se envían órdenes al vehículo. El token no se incluirá en el informe. Esta prueba no garantiza que una cuenta europea sea aceptada.")
     .setPositiveButton("Enviar y consultar",(d,w)->runCheck(3)).setNegativeButton("Cancelar",null).show();
 }
 private void authenticate(){
   new AlertDialog.Builder(this).setTitle("Autenticación necesaria")
     .setMessage("Conecta tu cuenta Mazda. Después vuelve aquí y pulsa la prueba que quieras realizar.")
     .setPositiveButton("Conectar",(d,w)->startActivity(new Intent(this,ConnectionTestActivity.class)))
     .setNegativeButton("Cancelar",null).show();
 }
 private void runCheck(int kind){
   if(busy||kind<0||kind>3)return;
   ControlSession.State session=ControlSession.get();
   if(kind!=2&&session==null){authenticate();return;}
   busy=true;for(Button check:checks)check.setEnabled(false);report.setText(combinedReport()+"\n\nConsultando información…");
   worker.execute(()->{
     String value;
     try{
       if(kind==0)value=FeatureDiscovery.europe(session);
       else if(kind==1){
         String read;
         try{new MazdaRemoteApi(this,null).refresh(session);read="Lectura actual de estado europeo completada.";}
         catch(Exception error){read="No se pudo actualizar el estado ahora. "+FeatureDiscovery.connectionError(error);}
         value=FeatureEvidence.report()+"\n\n"+read;
       }else if(kind==2)value=FeatureDiscovery.china();
       else value=FeatureDiscovery.chinaAuthenticated(session);
     }catch(Exception error){
       value="No se pudo completar la consulta. "+FeatureDiscovery.connectionError(error)+"\n\nNo se ha confirmado compatibilidad ni se han enviado órdenes al coche.";
     }
     final String displayed=value.length()>42000?value.substring(0,42000)+"\nInforme limitado por tamaño.":value;
     runOnUiThread(()->{if(isFinishing()||isDestroyed())return;
       results[kind]=displayed;report.setText(combinedReport());busy=false;
       for(Button check:checks)check.setEnabled(true);showSession();
     });
   });
 }
 private String combinedReport(){
   StringBuilder out=new StringBuilder();
   for(String result:results)if(result!=null){if(out.length()>0)out.append("\n\n━━━━━━━━━━━━━━━━\n\n");out.append(result);}
   return out.length()==0?"Todavía no se ha realizado ninguna prueba. Los resultados se reúnen aquí para poder copiarlos juntos.":out.toString();
 }
 private int dp(int value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
 private TextView text(String value,int size){
   TextView view=new TextView(this);view.setText(value);view.setTextSize(size);view.setTextColor(0xFF263548);
   view.setPadding(0,dp(10),0,dp(10));view.setLineSpacing(dp(2),1);return view;
 }
 private Button button(LinearLayout root,String title,Runnable action){
   Button button=new Button(this);button.setText(title);button.setAllCaps(false);button.setMinHeight(dp(48));
   root.addView(button,new LinearLayout.LayoutParams(-1,-2));button.setOnClickListener(v->action.run());return button;
 }
 @Override protected void onSaveInstanceState(Bundle state){state.putStringArray("reports",results);super.onSaveInstanceState(state);}
 @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
