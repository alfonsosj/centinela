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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Explicit, read-only checks motivated by static analysis of the Chinese app. */
public final class FeatureDiscoveryActivity extends SafeAreaActivity {
 private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private TextView report,sessionStatus;
 private Button europe,china;
 private String lastReport="Todavía no se ha realizado ninguna consulta.";
 @Override public void onCreate(Bundle state){
   super.onCreate(state);
   if(state!=null)lastReport=state.getString("report",lastReport);
   ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(0xFFF4F5FB);
   LinearLayout root=new LinearLayout(this);root.setOrientation(1);int pad=dp(18);root.setPadding(pad,pad,pad,dp(30));
   scroll.addView(root);setContentView(scroll);
   button(root,"‹ Volver",this::finish);
   TextView title=text("Diagnóstico China / Europa",23);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);root.addView(title);
   root.addView(text("Buscamos indicios de Centinela de cámaras, fotos interiores y exteriores, DVR y aparcamiento remoto. Son consultas de información: esta pantalla no envía órdenes al coche.",15));
   root.addView(text("El análisis de la app china v1.0.72 ha identificado módulos de Centinela y fotografía. Su presencia en esa app no demuestra que funcionen en un CX-6e europeo.",14));
   sessionStatus=text("",14);root.addView(sessionStatus);
   button(root,"Conectar / autenticar mi cuenta Mazda",()->startActivity(new Intent(this,ConnectionTestActivity.class)));
   europe=button(root,"1. Leer funciones declaradas de mi coche",()->runCheck(false));
   root.addView(text("Utiliza tu sesión europea para consultar la configuración que el servicio asocia a tu vehículo. No cambia el servidor habitual de la app.",14));
   china=button(root,"2. Comprobar respuesta del servidor chino",()->runCheck(true));
   root.addView(text("Consulta pública sin enviar tus credenciales ni el identificador del coche. Una respuesta no confirma que la cuenta europea sea válida en China.",14));
   button(root,"Copiar resultado",()->{
     ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
     if(clipboard!=null){clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico Mazda",lastReport));Toast.makeText(this,"Resultado copiado",Toast.LENGTH_SHORT).show();}
   });
   report=text(lastReport,15);report.setTextIsSelectable(true);root.addView(report);
 }
 @Override protected void onResume(){
   super.onResume();sessionStatus.setText(ControlSession.get()==null?
     "Sesión de lectura: autentica la cuenta y vuelve a esta pantalla.":"Sesión europea disponible para consultar funciones.");
 }
 private void runCheck(boolean anonymousChina){
   ControlSession.State session=ControlSession.get();
   if(!anonymousChina&&session==null){
     new AlertDialog.Builder(this).setTitle("Autenticación necesaria")
       .setMessage("Conecta tu cuenta Mazda. Después vuelve aquí y pulsa de nuevo «Leer funciones declaradas de mi coche».")
       .setPositiveButton("Conectar",(d,w)->startActivity(new Intent(this,ConnectionTestActivity.class)))
       .setNegativeButton("Cancelar",null).show();return;
   }
   europe.setEnabled(false);china.setEnabled(false);report.setText("Consultando información…");
   worker.execute(()->{
     String result;
     try{result=anonymousChina?FeatureDiscovery.china():FeatureDiscovery.europe(session);}
     catch(Exception error){
       result="No se pudo completar la consulta. "+
         (error instanceof java.net.SocketTimeoutException?"Tiempo de espera agotado.":
          error instanceof javax.net.ssl.SSLException?"No se pudo verificar la conexión segura.":
          error instanceof java.net.UnknownHostException?"No se pudo resolver la dirección del servicio.":
          error instanceof java.io.IOException?error.getMessage():"Respuesta no reconocida.")+"\n\nNo se ha confirmado compatibilidad ni se han enviado órdenes al coche.";
     }
     final String displayed=result;
     runOnUiThread(()->{if(isFinishing()||isDestroyed())return;
       lastReport=displayed;report.setText(displayed);europe.setEnabled(true);china.setEnabled(true);
     });
   });
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
 @Override protected void onSaveInstanceState(Bundle state){state.putString("report",lastReport);super.onSaveInstanceState(state);}
 @Override protected void onDestroy(){worker.shutdownNow();super.onDestroy();}
}
