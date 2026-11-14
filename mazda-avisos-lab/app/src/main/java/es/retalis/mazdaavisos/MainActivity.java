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

/**
 * v0.18: Minimal vehicle dashboard styled after the visual hierarchy
 * of the official application, using original vector icons and artwork.
 */
public final class MainActivity extends Activity {
 static final String CHANNEL="mazda_lab_alerts";
 static final String EXTRA_INLINE_LOGIN="quick_control_after_login",EXTRA_QUICK_ACTION="quick_control_action";
 private static final int INK=0xFF263548, MUTED=0xFF89909B, BG=0xFFF4F5FB, WHITE=Color.WHITE;
 private static final int LILAC=0xFF8B8CEB, GREEN=0xFF1BA67F, BORDER=0xFFE8EAF1;
 private LinearLayout root;
 private boolean toolsOpen=false;
 private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
 private GradientDrawable surface(int color,int radius){
   GradientDrawable d=new GradientDrawable();
   d.setColor(color);d.setCornerRadius(dp(radius));return d;
 }
 private GradientDrawable circle(int color){
   GradientDrawable d=new GradientDrawable();d.setShape(GradientDrawable.OVAL);d.setColor(color);return d;
 }
 private TextView text(String value,int size,int color,boolean bold){
   TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);
   if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
 }
 private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 private void label(LinearLayout where,String value,int size,int color,boolean bold){where.addView(text(value,size,color,bold));}
 private LinearLayout card(){
   LinearLayout c=vertical();c.setPadding(dp(18),dp(17),dp(18),dp(17));
   c.setBackground(surface(WHITE,23));c.setElevation(dp(1));
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(12);
   root.addView(c,lp);return c;
 }
 private void action(LinearLayout parent,String title,Runnable click,boolean primary){
   TextView b=text(title,14,primary?WHITE:INK,true);b.setGravity(Gravity.CENTER);
   b.setPadding(dp(12),dp(13),dp(12),dp(13));
   b.setBackground(surface(primary?INK:BG,15));
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(10);
   parent.addView(b,lp);b.setOnClickListener(v->click.run());
 }
 private void rowAction(LinearLayout parent,String title,Runnable click){
   TextView v=text(title+"   ›",14,INK,false);v.setPadding(dp(3),dp(13),dp(3),dp(13));
   parent.addView(v);v.setOnClickListener(w->click.run());
 }
 private void rule(LinearLayout panel){
   View v=new View(this);v.setBackgroundColor(BORDER);
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.topMargin=dp(15);lp.bottomMargin=dp(15);
   panel.addView(v,lp);
 }
 private void explainState(String title,String body){
   new AlertDialog.Builder(this).setTitle(title).setMessage(body).setPositiveButton("Entendido",null).show();
 }
 static void alert(Context context,String title,String body,int id){
   NotificationManager manager=(NotificationManager)context.getSystemService(NOTIFICATION_SERVICE);
   if(manager==null)return;
   if(Build.VERSION.SDK_INT>=26)
     manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Avisos Mazda experimentales",NotificationManager.IMPORTANCE_HIGH));
   if(Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
   PendingIntent open=PendingIntent.getActivity(context,0,new Intent(context,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
   Notification n=new Notification.Builder(context,CHANNEL).setSmallIcon(android.R.drawable.ic_dialog_info)
     .setContentTitle(title).setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body))
     .setContentIntent(open).setAutoCancel(true).build();
   manager.notify(id,n);
 }
 @Override public void onCreate(Bundle b){
   super.onCreate(b);render();
   String pending=getIntent().getStringExtra(EXTRA_QUICK_ACTION);
   if(pending!=null&&!pending.isEmpty()){
     getIntent().removeExtra(EXTRA_QUICK_ACTION);
     getWindow().getDecorView().post(()->QuickControl.invoke(this,pending));
   }
 }
 @Override protected void onResume(){super.onResume();render();}
 private void remote(String action){
   startActivity(new Intent(this,VehicleControlActivity.class).putExtra(VehicleControlActivity.EXTRA_ACTION,action));
 }
 private boolean accessibility(){
   String values=Settings.Secure.getString(getContentResolver(),Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
   if(values==null)return false;
   String wanted=new ComponentName(this,MazdaMonitor.class).flattenToString();
   for(String s:values.split(":"))if(s.equalsIgnoreCase(wanted))return true;
   return false;
 }
 private void launchMazda(boolean scan){
   if(scan&&!accessibility()){
     explainState("Accesibilidad desactivada","Activa el servicio de accesibilidad de Mazda Avisos Lab para comprobar manualmente el Centro de Mensajes.");return;
   }
   Intent intent=getPackageManager().getLaunchIntentForPackage("com.cma.mazda.eu");
   if(intent==null){explainState("Mazda no instalada","No se encuentra la aplicación Mazda Europa.");return;}
   if(scan){MazdaMonitor.requestSingleCheck();Data.status(this,"Comprobación solicitada; abriendo Mazda...");}
   else MazdaMonitor.stopCheck();
   startActivity(intent);
 }
 private void showHistory(){
   JSONArray entries=AlertHistory.read(this);StringBuilder out=new StringBuilder();
   for(int i=0;i<entries.length();i++){
     JSONObject e=entries.optJSONObject(i);if(e!=null)
       out.append(AlertHistory.when(e.optLong("time"))).append(" · ")
         .append(e.optString("title")).append("\n").append(e.optString("body")).append("\n\n");
   }
   new AlertDialog.Builder(this).setTitle("Historial de avisos")
     .setMessage(out.length()==0?"Aún no hay avisos":out.toString())
     .setPositiveButton("Cerrar",null)
     .setNeutralButton("Borrar",(dialog,which)->new AlertDialog.Builder(this)
       .setTitle("Borrar avisos").setMessage("¿Eliminar el historial guardado en este teléfono?")
       .setPositiveButton("Eliminar",(d,w)->{AlertHistory.clear(this);render();})
       .setNegativeButton("Cancelar",null).show()).show();
 }
 private void controlCircle(LinearLayout bar,String label,int iconKind,int status,Runnable click){
   // status=1 active, 0 inactive, -1 unavailable. Color reflects state, NOT the next command.
   final boolean active=status==1;
   final boolean unknown=status<0;
   LinearLayout col=vertical();col.setGravity(Gravity.CENTER);
   bar.addView(col,new LinearLayout.LayoutParams(0,-2,1));
   FrameLayout ring=new FrameLayout(this);
   ring.setBackground(circle(active?LILAC:unknown?0xFFF0F1F5:WHITE));
   if(!active)ring.setElevation(dp(1));
   LinearLayout.LayoutParams circleParams=new LinearLayout.LayoutParams(dp(64),dp(64));
   col.addView(ring,circleParams);
   VehicleStateIconView image=new VehicleStateIconView(this,iconKind,active?WHITE:unknown?0xFFB7BDC8:INK);
   FrameLayout.LayoutParams iconParams=new FrameLayout.LayoutParams(dp(37),dp(37),Gravity.CENTER);
   ring.addView(image,iconParams);
   TextView tag=text(label,11,unknown?MUTED:INK,false);tag.setGravity(Gravity.CENTER);
   LinearLayout.LayoutParams nameP=new LinearLayout.LayoutParams(-1,-2);nameP.topMargin=dp(7);
   col.addView(tag,nameP);
   col.setContentDescription(label+(unknown?", sin datos":active?", activo":", inactivo"));
   col.setOnClickListener(v->click.run());
 }
 private void pairMetric(LinearLayout row,String name,String caption,String number,String foot){
   LinearLayout panel=vertical();panel.setPadding(dp(16),dp(16),dp(16),dp(14));
   panel.setBackground(surface(WHITE,24));panel.setElevation(dp(1));
   row.addView(panel,new LinearLayout.LayoutParams(0,dp(176),1));
   label(panel,name,17,INK,true);
   label(panel,caption,12,MUTED,false);
   TextView value=text(number,40,INK,false);
   LinearLayout.LayoutParams numberParam=new LinearLayout.LayoutParams(-1,0,1);numberParam.topMargin=dp(10);
   panel.addView(value,numberParam);
   label(panel,foot,12,MUTED,false);
 }
 private void choose(String title,String description,String[] labels,String[] actions){
   new AlertDialog.Builder(this).setTitle(title).setMessage(description)
     .setItems(labels,(d,index)->{
       if(index>=0&&index<actions.length&&actions[index]!=null)
         remote(actions[index]);
     }).setNegativeButton("Cerrar",null).show();
 }
 private void vehicleDetail(VehicleSnapshot v,String which){
   boolean fresh=!v.stale();
   if(which.equals("windows")){
     if(!fresh){
       explainState("Ventanas · datos antiguos","Consulta primero el estado reciente desde la cuenta Mazda. No se habilitan controles físicos con información desactualizada.");
       return;
     }
     choose("Ventanillas · Experimental",
       "Estado: "+v.windowText()+". Estos comandos pueden mover las cuatro ventanillas. Comprueba visualmente que no haya obstáculos, personas o animales. Mazda puede rechazarlos si el coche no admite la función.",
       new String[]{"Cerrar todas las ventanillas","Abrir todas las ventanillas"},
       new String[]{VehicleControlActivity.WINDOWS_CLOSE,VehicleControlActivity.WINDOWS_OPEN});
   }else if(which.equals("trunk")){
     if(!fresh||v.trunk<0){
       explainState("Maletero · estado no confirmado","Actualiza primero el vehículo para evitar una orden basada en datos antiguos.");
       return;
     }
     choose("Portón · Experimental","Estado: "+v.trunkText()+". Comprueba que el portón tiene espacio y no hay nadie cerca. Se solicitarán PIN y confirmación.",
       new String[]{"Abrir portón","Cerrar portón"},
       new String[]{VehicleControlActivity.TRUNK_OPEN,VehicleControlActivity.TRUNK_CLOSE});
   }else if(which.equals("climate")){
     choose("Climatización · Experimental",
       "Estado leído: "+v.climateText()+". Temperatura interior: "+v.tempText()+". Las órdenes requieren confirmación, pueden consumir batería y el coche puede rechazarlas.",
       new String[]{"Activar climatización y ajustar temperatura","Desactivar climatización","Desempañar parabrisas","Desactivar desempañado"},
       new String[]{VehicleControlActivity.CLIMATE_ON,VehicleControlActivity.CLIMATE_OFF,
         VehicleControlActivity.DEFROST_ON,VehicleControlActivity.DEFROST_OFF});
   }
 }
 private void extras(){
   String[] options={
     "Localizar coche (luces y sonido)",
     "Hacer sonar bocina",
     "Calefacción volante: encender",
     "Calefacción volante: apagar",
     "Calefacción asiento conductor",
     "Calefacción asiento acompañante",
     "Ventilación asiento conductor",
     "Ventilación asiento acompañante",
     "Ajustar límite de carga (60–100 %)"
   };
   String[] codes={
     VehicleControlActivity.FIND,VehicleControlActivity.HORN,
     VehicleControlActivity.WHEEL_ON,VehicleControlActivity.WHEEL_OFF,
     VehicleControlActivity.DRIVER_HEAT,VehicleControlActivity.PASSENGER_HEAT,
     VehicleControlActivity.DRIVER_VENT,VehicleControlActivity.PASSENGER_VENT,
     VehicleControlActivity.CHARGE_LIMIT
   };
   choose("Controles adicionales · Experimentales",
     "El coche puede no disponer de todas estas opciones. Cada acción se envía solo después de tu confirmación. Comprueba el resultado real; aceptar una orden no garantiza que el equipo cambie de estado.",
     options,codes);
 }
 void render(){
   ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
   root=vertical();root.setPadding(dp(18),dp(20),dp(18),dp(30));scroll.addView(root);setContentView(scroll);
   VehicleSnapshot v=VehicleSnapshot.read(this);
   String state=MqttSessionService.currentStatus(this);
   boolean connected=state.startsWith("MQTT conectado");
   boolean reconnecting=state.startsWith("Reconectando")||state.startsWith("SIN CONFIRMACIÓN");

   LinearLayout head=horizontal();
   TextView app=text("MAZDA AVISOS",21,INK,true);head.addView(app,new LinearLayout.LayoutParams(0,-2,1));
   TextView gear=text("⚙",23,INK,false);gear.setPadding(dp(10),dp(1),dp(5),dp(1));
   gear.setOnClickListener(view->startActivity(new Intent(this,AlertSettingsActivity.class)));
   head.addView(gear);root.addView(head);
   TextView small=text("CX-6e · Monitor independiente",12,MUTED,false);
   LinearLayout.LayoutParams smallP=new LinearLayout.LayoutParams(-1,-2);smallP.bottomMargin=dp(14);root.addView(small,smallP);

   LinearLayout hero=card();
   LinearLayout line=horizontal();
   label(line,"CX-6e Aero Grey",19,INK,true);
   TextView live=text(connected?" ●  En línea":reconnecting?" ◌  Reconectando":" ○  Sin conexión",12,connected?GREEN:MUTED,true);
   LinearLayout.LayoutParams liveP=new LinearLayout.LayoutParams(-2,-2);liveP.leftMargin=dp(10);line.addView(live,liveP);
   hero.addView(line);
   TextView freshness=text(v.age()+(v.stale()&&v.lastDataAt>0?" · no actualizado":""),12,v.stale()?MUTED:GREEN,false);
   LinearLayout.LayoutParams freshP=new LinearLayout.LayoutParams(-1,-2);freshP.topMargin=dp(7);
   hero.addView(freshness,freshP);
   TextView refresh=text("↻ Actualizar vehículo",12,INK,true);
   refresh.setGravity(Gravity.RIGHT);
   refresh.setPadding(dp(3),dp(8),dp(3),dp(4));
   refresh.setOnClickListener(view->remote(VehicleControlActivity.REFRESH));
   hero.addView(refresh);
   ImageView carImage=new ImageView(this);
   carImage.setImageResource(R.drawable.cx6e_aerogrey);
   carImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
   carImage.setContentDescription("Ilustración del CX-6e europeo en color Aero Grey");
   LinearLayout.LayoutParams imageP=new LinearLayout.LayoutParams(-1,dp(158));imageP.topMargin=dp(2);
   hero.addView(carImage,imageP);
   LinearLayout base=horizontal();
   TextView loc=text(v.locationText()+" · "+v.doorText(),13,INK,true);
   base.addView(loc,new LinearLayout.LayoutParams(0,-2,1));
   base.addView(text(v.rangeText(),14,INK,true));
   hero.addView(base);

   LinearLayout buttons=horizontal();
   LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(5);bp.bottomMargin=dp(19);
   root.addView(buttons,bp);
   boolean recentLock=v.lock>=0&&v.freshForControl();
   int lockStatus=recentLock?(v.lock==1?1:0):-1;
   controlCircle(buttons,"Cierre",VehicleStateIconView.LOCK,lockStatus,()->{
     VehicleSnapshot now=VehicleSnapshot.read(this);
     if(now.lock<0||!now.freshForControl()){
       new AlertDialog.Builder(this).setTitle("Actualización necesaria")
         .setMessage("La última lectura del cierre no es suficientemente reciente. Consulta Mazda antes de enviar una orden.")
         .setPositiveButton("Actualizar", (dialog,which)->QuickControl.invoke(this,VehicleControlActivity.REFRESH))
         .setNegativeButton("Cancelar",null).show();
       return;
     }
     QuickControl.invoke(this,now.lock==0?VehicleControlActivity.UNLOCK:VehicleControlActivity.LOCK);
   });
   int windowsStatus=v.windowsKnown<4||v.stale()?-1:v.windowsOpen>0?1:0;
   controlCircle(buttons,"Ventanas",VehicleStateIconView.WINDOWS,windowsStatus,()->{
     VehicleSnapshot current=VehicleSnapshot.read(this);
     if(current.stale()||current.windowsKnown!=4){
       new AlertDialog.Builder(this).setTitle("Estado de ventanillas desconocido")
         .setMessage("Actualiza la lectura Mazda antes de abrir o cerrar las cuatro ventanillas.")
         .setPositiveButton("Actualizar",(d,w)->QuickControl.invoke(this,VehicleControlActivity.REFRESH))
         .setNegativeButton("Cancelar",null).show();return;
     }
     QuickControl.invoke(this,current.windowsOpen>0?
       VehicleControlActivity.WINDOWS_CLOSE:VehicleControlActivity.WINDOWS_OPEN);
   });
   int trunkStatus=v.trunk<0||v.stale()?-1:v.trunk==1?1:0;
   controlCircle(buttons,"Maletero",VehicleStateIconView.TRUNK,trunkStatus,()->{
     VehicleSnapshot current=VehicleSnapshot.read(this);
     if(current.stale()||current.trunk<0){
       new AlertDialog.Builder(this).setTitle("Estado del maletero desconocido")
         .setMessage("Actualiza el vehículo antes de mover el maletero.")
         .setPositiveButton("Actualizar",(d,w)->QuickControl.invoke(this,VehicleControlActivity.REFRESH))
         .setNegativeButton("Cancelar",null).show();return;
     }
     QuickControl.invoke(this,current.trunk==1?
       VehicleControlActivity.TRUNK_CLOSE:VehicleControlActivity.TRUNK_OPEN);
   });
   int climateStatus=v.climate<0||v.stale()?-1:v.climate==1?1:0;
   controlCircle(buttons,"Clima",VehicleStateIconView.CLIMATE,climateStatus,()->{
     VehicleSnapshot current=VehicleSnapshot.read(this);
     if(current.stale()||current.climate<0){
       new AlertDialog.Builder(this).setTitle("Climatización sin estado actual")
         .setMessage("Actualiza el vehículo antes de activar o desactivar el clima.")
         .setPositiveButton("Actualizar",(d,w)->QuickControl.invoke(this,VehicleControlActivity.REFRESH))
         .setNegativeButton("Cancelar",null).show();return;
     }
     QuickControl.invoke(this,current.climate==1?
       VehicleControlActivity.CLIMATE_OFF:VehicleControlActivity.CLIMATE_ON,
       current.remoteTemp==null?24:(int)Math.round(current.remoteTemp));
   });

   LinearLayout metrics=horizontal();
   LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.bottomMargin=dp(14);root.addView(metrics,mp);
   pairMetric(metrics,"Temp","Dentro",v.tempText(),"Interior · °C");
   View temperatureCard=metrics.getChildAt(metrics.getChildCount()-1);
   temperatureCard.setOnClickListener(view->startActivity(
     new Intent(this,ClimateDashboardActivity.class)));
   temperatureCard.setContentDescription("Abrir detalles de climatización");
   metrics.addView(new View(this),new LinearLayout.LayoutParams(dp(12),dp(1)));
   pairMetric(metrics,"Batería",v.chargeText(),v.socText(),v.rangeText());
   View batteryCard=metrics.getChildAt(metrics.getChildCount()-1);
   batteryCard.setOnClickListener(view->startActivity(
     new Intent(this,BatteryDashboardActivity.class)));
   batteryCard.setContentDescription("Abrir detalles de batería y carga");

   LinearLayout statusCard=card();
   label(statusCard,"Estado del vehículo",17,INK,true);
   TextView details=text("Ventanas: "+v.windowText()+"  ·  Maletero: "+v.trunkText()+
     "\nCarga: "+v.chargeText()+"  ·  "+v.cableText(),13,MUTED,false);
   LinearLayout.LayoutParams detailsP=new LinearLayout.LayoutParams(-1,-2);detailsP.topMargin=dp(10);
   statusCard.addView(details,detailsP);
   rule(statusCard);
   label(statusCard,"◉  Centinela y avisos",14,INK,true);
   label(statusCard,"Avisos recibidos: "+AlertHistory.read(this).length()+". Monitor: "+
     (connected?"conectado":"desconectado")+".",12,MUTED,false);
   rowAction(statusCard,"Personalizar notificaciones",()->startActivity(new Intent(this,AlertSettingsActivity.class)));

   LinearLayout remoteExtras=card();
   label(remoteExtras,"Más controles del vehículo",17,INK,true);
   TextView extraInfo=text("Puertas, ventanas, maletero, clima, localización, asientos y límite de carga. Solo el control de puertas está verificado en tu coche.",12,MUTED,false);
   LinearLayout.LayoutParams xip=new LinearLayout.LayoutParams(-1,-2);xip.topMargin=dp(7);remoteExtras.addView(extraInfo,xip);
   action(remoteExtras,"Abrir panel completo de controles",
      ()->startActivity(new Intent(this,ControlsActivity.class)),true);
   LinearLayout activity=card();
   label(activity,"Actividad reciente",17,INK,true);
   JSONArray events=AlertHistory.read(this);
   if(events.length()==0){
     TextView none=text("Aquí aparecerán los avisos del coche.",13,MUTED,false);
     LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(8);activity.addView(none,p);
   }
   for(int i=0;i<Math.min(3,events.length());i++){
     JSONObject e=events.optJSONObject(i);if(e==null)continue;
     TextView record=text("• "+e.optString("title"),14,INK,true);
     LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(9);
     activity.addView(record,lp);
     label(activity,AlertHistory.when(e.optLong("time")),12,MUTED,false);
   }
   rowAction(activity,"Ver historial completo",this::showHistory);

   LinearLayout technical=card();
   TextView reveal=text("Conexión y herramientas   "+(toolsOpen?"▴":"▾"),16,INK,true);
   technical.addView(reveal);
   LinearLayout tools=vertical();technical.addView(tools);
   tools.setVisibility(toolsOpen?View.VISIBLE:View.GONE);
   reveal.setOnClickListener(view->{
     toolsOpen=!toolsOpen;
     tools.setVisibility(toolsOpen?View.VISIBLE:View.GONE);
     reveal.setText("Conexión y herramientas   "+(toolsOpen?"▴":"▾"));
   });
   TextView connectionText=text(connected?"Conexión MQTT activa. Los avisos dependen del servicio, la red y los mensajes de Mazda.":
     reconnecting?"Se ha perdido la conexión; el monitor está intentando recuperarla.":
     "Monitor desconectado. Abre «Conectar / autenticar Mazda» para volver a recibir avisos.",13,MUTED,false);
   LinearLayout.LayoutParams conP=new LinearLayout.LayoutParams(-1,-2);conP.topMargin=dp(11);tools.addView(connectionText,conP);
   action(tools,"Conectar / autenticar Mazda",()->startActivity(new Intent(this,ConnectionTestActivity.class)),true);
   if(connected||reconnecting)action(tools,"Detener monitor",()->{
     startService(new Intent(this,MqttSessionService.class).setAction(MqttSessionService.ACTION_STOP));render();
   },false);
   rowAction(tools,"Actualizar pantalla",this::render);
   rowAction(tools,"Detalles de conexión",()->explainState("Estado MQTT",state+"\n\n"+v.age()));
   rowAction(tools,"Compatibilidad de controles",()->explainState("Funciones y límites",
     "COMPROBADO EN TU CX-6e\n"+
     "• Lecturas de batería, temperatura, autonomía y cierre.\n"+
     "• Avisos MQTT de bloqueo y desbloqueo.\n"+
     "• Bloquear y desbloquear mediante la cuenta secundaria.\n\n"+
     "IMPLEMENTADO Y PENDIENTE DE VALIDACIÓN\n"+
     "• Ventanillas y portón.\n"+
     "• Clima y desempañado.\n"+
     "• Bocina, localización, volante y asientos.\n"+
     "• Límite de carga.\n\n"+
     "NO DISPONIBLE / NO VERIFICADO\n"+
     "• Iniciar o detener la carga desde la nube.\n"+
     "• Vídeo, cámara Centinela y eventos de ocupantes.\n"+
     "• Reinicio del monitor tras apagar el teléfono sin volver a iniciar sesión.\n\n"+
     "La API de Mazda puede cambiar. Los avisos experimentales no son una alarma de seguridad."));
   rowAction(tools,"Centro de Mensajes Mazda",()->launchMazda(true));
   rowAction(tools,"Abrir Mazda oficial",()->launchMazda(false));
   rowAction(tools,"Configurar accesibilidad",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
   if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
     rowAction(tools,"Permitir notificaciones",()->requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},11));
   rowAction(tools,"Probar notificación",()->alert(this,"Prueba Mazda Avisos","Esta es una notificación de prueba, no del vehículo.",99));
   rowAction(tools,"Copiar diagnóstico",()->{
     android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
     if(clipboard!=null)clipboard.setPrimaryClip(ClipData.newPlainText("Diagnóstico Mazda",Data.technicalDiagnostic(this)));
   });
   TextView disclaimer=text("Mazda Avisos 1.1 · Datos y alertas sujetos a conexión. Los controles adicionales no están validados en este vehículo. No sustituye el sistema oficial ni una alarma.",11,MUTED,false);
   disclaimer.setPadding(0,dp(4),0,dp(6));root.addView(disclaimer);
 }
 @Override public void onRequestPermissionsResult(int code,String[] perms,int[] results){
   super.onRequestPermissionsResult(code,perms,results);render();
 }
}
