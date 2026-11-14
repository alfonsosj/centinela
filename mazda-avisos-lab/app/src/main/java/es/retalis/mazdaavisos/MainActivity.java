package es.retalis.mazdaavisos;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;

public class MainActivity extends Activity {
 static final String CHANNEL="mazda_lab_alerts";
 private static final int INK=0xFF23354A, MUTED=0xFF79869A, BG=0xFFF6F7FB, WHITE=Color.WHITE,
   GREEN=0xFF15966B, PURPLE=0xFF797CE3, EDGE=0xFFE6EAF1;
 private LinearLayout root;
 private final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
 private final Runnable liveTick=()->{if(!isFinishing())render();};
 private int dp(float value){return (int)(getResources().getDisplayMetrics().density*value+.5f);}
 private GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 @Override public void onCreate(Bundle b){super.onCreate(b);render();}
 @Override protected void onResume(){super.onResume();handler.postDelayed(liveTick,4000);}
 @Override protected void onPause(){handler.removeCallbacks(liveTick);super.onPause();}
 static void alert(Context context,String title,String body,int id){
   NotificationManager manager=(NotificationManager)context.getSystemService(NOTIFICATION_SERVICE);
   if(manager==null)return;
   if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Avisos Mazda experimentales",NotificationManager.IMPORTANCE_HIGH));
   if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
   PendingIntent open=PendingIntent.getActivity(context,0,new Intent(context,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   Notification notice=new Notification.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
      .setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
      .setContentIntent(open).setAutoCancel(true).build();
   manager.notify(id,notice);
 }
 private TextView text(String value,int size,int color,boolean bold){
   TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
   if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
 }
 private void label(LinearLayout container,String value,int size,int color,boolean bold){
   TextView t=text(value,size,color,bold);container.addView(t);
 }
 private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 private LinearLayout card(){
   LinearLayout c=vertical();c.setPadding(dp(18),dp(16),dp(18),dp(16));
   c.setBackground(bg(WHITE,24));c.setElevation(dp(1));
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
   lp.setMargins(0,0,0,dp(12));root.addView(c,lp);return c;
 }
 private void button(LinearLayout parent,String title,Runnable click,boolean strong){
   TextView b=text(title,14,strong?WHITE:INK,true);b.setGravity(Gravity.CENTER);
   b.setPadding(dp(16),dp(13),dp(16),dp(13));b.setBackground(bg(strong?INK:BG,15));
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(9);
   parent.addView(b,lp);b.setOnClickListener(v->click.run());
 }
 private void rowButton(LinearLayout parent,String title,Runnable click){
   TextView b=text(title+"   ›",14,INK,true);
   b.setPadding(dp(5),dp(11),dp(4),dp(11));parent.addView(b);b.setOnClickListener(v->click.run());
 }
 private void divider(LinearLayout box){
   View v=new View(this);v.setBackgroundColor(EDGE);
   LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(1));p.topMargin=dp(12);p.bottomMargin=dp(12);box.addView(v,p);
 }
 private void summaryTile(LinearLayout row,String heading,String big,String foot,int color){
   LinearLayout tile=vertical();tile.setPadding(dp(15),dp(14),dp(12),dp(14));
   tile.setBackground(bg(WHITE,22));tile.setElevation(dp(1));
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(133),1);
   row.addView(tile,lp);
   label(tile,heading,14,INK,true);
   TextView number=text(big,35,color,true);
   LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,0,1);np.topMargin=dp(7);tile.addView(number,np);
   label(tile,foot,12,MUTED,false);
 }
 private void remoteButton(LinearLayout row,String label,String glyph,String action){
   LinearLayout wrapper=vertical();
   wrapper.setGravity(Gravity.CENTER);
   wrapper.setPadding(dp(8),dp(12),dp(8),dp(12));
   wrapper.setBackground(bg(BG,20));
   row.addView(wrapper,new LinearLayout.LayoutParams(0,-2,1));
   TextView icon=text(glyph,28,INK,false);icon.setGravity(Gravity.CENTER);wrapper.addView(icon);
   TextView title=text(label,13,INK,true);title.setGravity(Gravity.CENTER);wrapper.addView(title);
   wrapper.setContentDescription(label+" puertas del vehículo");
   wrapper.setOnClickListener(v->startActivity(new Intent(this,VehicleControlActivity.class)
     .putExtra(VehicleControlActivity.EXTRA_ACTION,action)));
 }
 private void remote(String action){
   startActivity(new Intent(this,VehicleControlActivity.class)
     .putExtra(VehicleControlActivity.EXTRA_ACTION,action));
 }
 private void launchMazda(boolean scan){
   if(scan&&!accessibility()){
     new AlertDialog.Builder(this).setMessage("Activa accesibilidad para la lectura manual de mensajes de Mazda.")
       .setPositiveButton("Aceptar",null).show();return;
   }
   Intent i=getPackageManager().getLaunchIntentForPackage("com.cma.mazda.eu");
   if(i==null){new AlertDialog.Builder(this).setMessage("No se encuentra la aplicación oficial Mazda Europa.")
     .setPositiveButton("Aceptar",null).show();return;}
   if(scan){MazdaMonitor.requestSingleCheck();Data.status(this,"Comprobación solicitada; abriendo Mazda...");}
   else MazdaMonitor.stopCheck();
   startActivity(i);
 }
 private boolean accessibility(){
   String list=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
   String wanted=new ComponentName(this,MazdaMonitor.class).flattenToString();
   if(list==null)return false;
   for(String service:list.split(":"))if(service.equalsIgnoreCase(wanted))return true;
   return false;
 }
 private void showHistory(){
   JSONArray entries=AlertHistory.read(this);
   StringBuilder b=new StringBuilder();
   for(int i=0;i<entries.length();i++){
     JSONObject item=entries.optJSONObject(i);
     if(item!=null)b.append(AlertHistory.when(item.optLong("time"))).append(" · ").append(item.optString("title"))
       .append("\n").append(item.optString("body")).append("\n\n");
   }
   new AlertDialog.Builder(this).setTitle("Historial de avisos")
     .setMessage(b.length()==0?"Todavía no hay avisos":b.toString())
     .setPositiveButton("Cerrar",null)
     .setNeutralButton("Borrar",(dialog,which)->new AlertDialog.Builder(this).setTitle("Borrar historial")
       .setMessage("¿Eliminar los avisos guardados en este teléfono?")
       .setPositiveButton("Borrar",(d,w)->{AlertHistory.clear(this);render();})
       .setNegativeButton("Cancelar",null).show()).show();
 }
 void render(){
   ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
   root=vertical();root.setPadding(dp(18),dp(22),dp(18),dp(32));scroll.addView(root);setContentView(scroll);
   LinearLayout header=horizontal();
   TextView name=text("MAZDA AVISOS",24,INK,true);
   header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
   TextView gear=text("⚙",23,INK,false);gear.setPadding(dp(12),dp(3),dp(6),dp(3));
   header.addView(gear);gear.setContentDescription("Configurar avisos");
   gear.setOnClickListener(v->startActivity(new Intent(this,AlertSettingsActivity.class)));
   root.addView(header);
   TextView sub=text("Tu coche · monitor independiente",12,MUTED,false);
   LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(-1,-2);sp.bottomMargin=dp(14);root.addView(sub,sp);
   VehicleSnapshot car=VehicleSnapshot.read(this);
   String connection=MqttSessionService.currentStatus(this);
   boolean connected=connection.contains("MQTT conectado")&&!connection.contains("SIN CONFIRMACIÓN")&&!connection.contains("Última sesión");
   LinearLayout hero=card();
   LinearLayout top=horizontal();
   TextView vehicle=text("CX-6e",20,INK,true);
   top.addView(vehicle,new LinearLayout.LayoutParams(0,-2,1));
   TextView status=text(connected?"●  Conectado":"○  Sin conexión confirmada",12,connected?GREEN:MUTED,true);
   top.addView(status);hero.addView(top);
   TextView freshness=text(car.age()+(car.stale()&&car.lastDataAt>0?" · verificar actualización":""),12,car.stale()?MUTED:GREEN,false);
   LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2);fp.topMargin=dp(7);hero.addView(freshness,fp);
   ImageView vehicleImage=new ImageView(this);
   vehicleImage.setImageResource(R.drawable.cx6e_aerogrey);
   vehicleImage.setContentDescription("Ilustración de Mazda CX-6e europeo Aero Grey");
   vehicleImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
   vehicleImage.setAdjustViewBounds(true);
   LinearLayout.LayoutParams drawing=new LinearLayout.LayoutParams(-1,dp(156));
   drawing.topMargin=dp(5);hero.addView(vehicleImage,drawing);
   LinearLayout bottom=horizontal();
   TextView carInfo=text(car.locationText()+"  ·  "+car.doorText(),13,INK,true);
   bottom.addView(carInfo,new LinearLayout.LayoutParams(0,-2,1));
   TextView range=text(car.rangeText(),13,MUTED,true);bottom.addView(range);hero.addView(bottom);
   LinearLayout metrics=horizontal();
   LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.bottomMargin=dp(12);root.addView(metrics,mp);
   LinearLayout.LayoutParams blank=new LinearLayout.LayoutParams(dp(12),dp(1));
   // Add a visual gutter after the first metric.
   summaryTile(metrics,"Temperatura",""+car.tempText(),"Interior · °C",INK);
   metrics.addView(new View(this),blank);
   summaryTile(metrics,"Batería",car.socText(),car.chargeText(),INK);
   LinearLayout batteryDetails=card();
   label(batteryDetails,"Energía y carga",17,INK,true);
   label(batteryDetails,"Batería: "+car.socText()+"  ·  Autonomía: "+car.rangeText(),14,INK,true);
   label(batteryDetails,"Estado: "+car.chargeText()+"  ·  "+car.cableText(),13,MUTED,false);
   label(batteryDetails,"Objetivo de carga: "+car.targetText(),13,MUTED,false);
   label(batteryDetails,"El inicio/parada de carga no tiene aún una orden verificada para esta versión.",11,MUTED,false);
   LinearLayout quick=card();
   label(quick,"Estado del vehículo",17,INK,true);
   LinearLayout key=horizontal();LinearLayout.LayoutParams kp=new LinearLayout.LayoutParams(-1,-2);kp.topMargin=dp(9);quick.addView(key,kp);
   TextView win=text("▤  Ventanas\n"+car.windowText(),13,INK,false);
   key.addView(win,new LinearLayout.LayoutParams(0,-2,1));
   TextView lock=text("♧  Cierre\n"+car.doorText(),13,INK,false);
   key.addView(lock,new LinearLayout.LayoutParams(0,-2,1));
   divider(quick);
   label(quick,"Controles remotos",15,INK,true);
   if(car.lock>=0&&car.freshForControl()){
     boolean unlocked=car.lock==1;
     button(quick,unlocked?"🔒  Bloquear puertas":"🔓  Desbloquear puertas",
       ()->remote(unlocked?VehicleControlActivity.LOCK:VehicleControlActivity.UNLOCK),true);
   }else{
     TextView warning=text("Cierre sin estado reciente. Autentica o actualiza primero para evitar órdenes basadas en datos antiguos.",12,MUTED,false);
     quick.addView(warning);
     button(quick,"Actualizar estado desde Mazda",()->startActivity(new Intent(this,ConnectionTestActivity.class)),false);
   }
   TextView controlNote=text("Un solo botón según el último estado del coche. Cada orden pide confirmación y PIN cuando corresponda.",11,MUTED,false);
   LinearLayout.LayoutParams cn=new LinearLayout.LayoutParams(-1,-2);cn.topMargin=dp(8);quick.addView(controlNote,cn);
   divider(quick);
   label(quick,"Ventanas y maletero",15,INK,true);
   TextView trunkInfo=text("Maletero: "+car.trunkText()+"   ·   Ventanas: "+car.windowText(),13,MUTED,false);
   quick.addView(trunkInfo);
   label(quick,"Ventanillas: "+car.windowText()+". Maletero: "+car.trunkText()+".",12,MUTED,false);
   TextView safetyInfo=text("La app puede consultar y avisar de cambios, pero las órdenes de ventanillas y maletero aún no están verificadas para el CX-6e europeo. Por seguridad no se ofrecen botones de movimiento.",12,MUTED,false);
   LinearLayout.LayoutParams safetyParams=new LinearLayout.LayoutParams(-1,-2);
   safetyParams.topMargin=dp(8);quick.addView(safetyInfo,safetyParams);
   divider(quick);
   label(quick,"Climatización",15,INK,true);
   label(quick,car.climateText()+"  ·  Temperatura interior: "+car.tempText(),13,MUTED,false);
   label(quick,"Control remoto de clima pendiente de validar con Mazda Europa. Seguimos mostrando el estado real cuando está disponible.",12,MUTED,false);
   divider(quick);
   label(quick,"Carga y batería",15,INK,true);
   label(quick,"Batería: "+car.socText()+"  ·  "+car.chargeText(),13,INK,true);
   label(quick,car.cableText()+"  ·  Objetivo: "+car.targetText(),12,MUTED,false);
   label(quick,"Inicio/parada remota de carga pendiente de confirmar. Puedes configurar avisos de carga y batería en Ajustes.",12,MUTED,false);
   divider(quick);
   int events=AlertHistory.read(this).length();
   TextView info=text("◉  Centinela configurable    ·    "+events+" avisos guardados",13,INK,true);
   quick.addView(info);
   button(quick,"Personalizar avisos y Centinela",()->startActivity(new Intent(this,AlertSettingsActivity.class)),false);
   LinearLayout monitor=card();
   label(monitor,"Monitor Mazda MQTT",17,INK,true);
   TextView desc=text(connected?"Conectado directamente a Mazda. No utiliza Home Assistant.":"Para recibir avisos, inicia el monitor con tu cuenta Mazda secundaria.",13,MUTED,false);
   LinearLayout.LayoutParams dp=new LinearLayout.LayoutParams(-1,-2);dp.topMargin=dp(7);monitor.addView(desc,dp);
   if(connected){
     button(monitor,"Actualizar información",this::render,true);
     button(monitor,"Detener monitor",()->{
       startService(new Intent(this,MqttSessionService.class).setAction(MqttSessionService.ACTION_STOP));render();
     },false);
   }else{
     button(monitor,"Conectar mi coche",()->startActivity(new Intent(this,ConnectionTestActivity.class)),true);
     button(monitor,"Actualizar estado",this::render,false);
   }
   LinearLayout activity=card();
   label(activity,"Actividad reciente",17,INK,true);
   JSONArray recent=AlertHistory.read(this);
   if(recent.length()==0){
     TextView empty=text("Los eventos registrados aparecerán aquí.",13,MUTED,false);
     LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);activity.addView(empty,p);
   }
   for(int i=0;i<Math.min(3,recent.length());i++){
     JSONObject event=recent.optJSONObject(i);if(event==null)continue;
     TextView t=text("•  "+event.optString("title"),14,INK,true);
     LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);activity.addView(t,p);
     label(activity,AlertHistory.when(event.optLong("time")),12,MUTED,false);
   }
   rowButton(activity,"Ver todos los avisos",this::showHistory);
   LinearLayout extra=card();
   TextView toggle=text("Herramientas avanzadas   ▾",15,INK,true);extra.addView(toggle);
   LinearLayout tools=vertical();tools.setVisibility(View.GONE);extra.addView(tools);
   toggle.setOnClickListener(v->{tools.setVisibility(tools.getVisibility()==View.VISIBLE?View.GONE:View.VISIBLE);});
   rowButton(tools,"Detalles de conexión",()->new AlertDialog.Builder(this).setTitle("Diagnóstico MQTT")
     .setMessage(connection+"\n\n"+car.age()+"\n\nAccesibilidad: "+(accessibility()?"activada":"desactivada"))
     .setPositiveButton("Cerrar",null).show());
   rowButton(tools,"Conectar otra cuenta",()->startActivity(new Intent(this,ConnectionTestActivity.class)));
   rowButton(tools,"Comprobar Centro de Mensajes",()->launchMazda(true));
   rowButton(tools,"Abrir aplicación Mazda",()->launchMazda(false));
   rowButton(tools,"Configurar accesibilidad",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
   if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
     rowButton(tools,"Permitir notificaciones",()->requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11));
   rowButton(tools,"Probar notificación",()->alert(this,"Prueba Mazda Avisos","Notificación local de prueba, no es una alerta del coche.",99));
   rowButton(tools,"Copiar diagnóstico",()->{
     android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
     if(clipboard!=null)clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico Mazda",Data.technicalDiagnostic(this)));
   });
   TextView disclaimer=text("Lab v0.17 · Datos reales recibidos por MQTT. Las lecturas pueden estar desactualizadas. No sustituye los avisos oficiales ni es una alarma de seguridad.",11,MUTED,false);
   disclaimer.setPadding(0,dp(4),0,dp(8));root.addView(disclaimer);
 }
 @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);render();}
}
