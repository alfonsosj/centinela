package es.retalis.mazdaavisos;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.widget.*;

/** Opciones locales: no se comparte ninguna configuración con servidores. */
public final class AlertSettingsActivity extends Activity {
 private LinearLayout root;
 private SharedPreferences preferences;
 @Override public void onCreate(Bundle state){
   super.onCreate(state);
   preferences=AlertRules.pref(this);
   ScrollView scroll=new ScrollView(this);
   root=new LinearLayout(this);root.setOrientation(1);root.setPadding(28,28,28,36);
   scroll.addView(root);setContentView(scroll);
   TextView title=new TextView(this);title.setText("CONFIGURAR AVISOS MAZDA");
   title.setTextSize(22);title.setTextColor(Color.rgb(22,48,75));root.addView(title);
   explain("Los avisos se envían solo cuando Mazda comunica un cambio real al monitor. No requiere Home Assistant ni envía órdenes al vehículo.");
   section("Aperturas y seguridad");
   toggle("Bloqueo y desbloqueo de puertas",AlertRules.LOCKS);
   toggle("Apertura/cierre de puertas físicas",AlertRules.DOORS);
   toggle("Recordarme cada minuto si está aparcado y hay puertas abiertas o desbloqueado",AlertRules.PARKED_OPEN_REMINDER);
   explain("Con este ajuste activo, si MQTT confirma que el coche está aparcado y permanece una puerta abierta o el vehículo desbloqueado, recibirás un recordatorio tras el primer minuto y otro cada minuto mientras siga así. Se detiene al cerrar y bloquear; necesita el monitor MQTT conectado y datos del coche. No sustituye una alarma.");
   toggle("Avisar cada minuto si el coche está bloqueado y alguna ventanilla abierta",AlertRules.LOCKED_WINDOW_REMINDER);
   explain("Recordatorio independiente: tras un minuto con el vehículo bloqueado y una o varias ventanillas abiertas, avisa cada minuto hasta que Mazda informe de que se han cerrado todas o el coche esté desbloqueado. Necesita el monitor MQTT activo y estados recibidos en la sesión actual.");
   toggle("Apertura/cierre de ventanas",AlertRules.WINDOWS);
   toggle("Apertura/cierre de maletero",AlertRules.TRUNK);
   toggle("Centinela básico (avisos destacados de aperturas)",AlertRules.SENTINEL);
   explain("Centinela básico: destaca aperturas solo cuando el coche comunica aparcado y bloqueado. Un desbloqueo se avisa al cambiar de bloqueado a desbloqueado. No detecta golpes, personas ni intrusiones y no sustituye una alarma.");
   section("Batería y carga");
   toggle("Carga iniciada, detenida o en espera",AlertRules.CHARGE);
   toggle("Cable de carga conectado o desconectado",AlertRules.CABLE);
   toggle("Avisos de porcentaje de batería",AlertRules.SOC);
   threshold("Umbral batería baja",AlertRules.LOW_SOC,5,40,5," %");
   threshold("Cambio de batería entre avisos",AlertRules.STEP_SOC,5,25,5," puntos");
   toggle("Descenso de batería estando aparcado (experimental)",AlertRules.PARK_DROP);
   threshold("Descenso aparcado",AlertRules.DROP_POINTS,2,10,1," puntos");
   explain("Los códigos de carga no distinguen con certeza todos los casos de carga completada o pausada. No generamos un aviso de «carga completada» sin confirmación.");
   section("Temperatura interior");
   toggle("Alertas por temperaturas extremas",AlertRules.TEMP);
   threshold("Temperatura alta",AlertRules.HIGH_TEMP,25,60,1," °C");
   threshold("Temperatura baja",AlertRules.LOW_TEMP,-10,15,1," °C");
   explain("La temperatura se basa en las lecturas recibidas del coche. Una temperatura extrema no confirma que haya personas en el interior. La aplicación no es un detector de ocupantes.");
   Button back=new Button(this);back.setText("Volver a Mazda Avisos Lab");root.addView(back);back.setOnClickListener(v->finish());
 }
 private void section(String title){
   TextView t=new TextView(this);t.setText(title);t.setTextSize(19);
   t.setTextColor(Color.rgb(22,48,75));t.setPadding(0,24,0,8);root.addView(t);
 }
 private void explain(String s){
   TextView t=new TextView(this);t.setText(s);t.setTextSize(13);
   t.setTextColor(Color.rgb(65,67,70));t.setPadding(0,8,0,14);root.addView(t);
 }
 private void toggle(String title,String key){
   CheckBox c=new CheckBox(this);c.setText(title);c.setTextSize(15);
   c.setChecked(AlertRules.enabled(this,key));
   c.setPadding(0,4,0,4);
   c.setOnCheckedChangeListener((v,checked)->preferences.edit().putBoolean(key,checked).apply());
   root.addView(c);
 }
 private void threshold(String title,String key,int min,int max,int increment,String suffix){
   LinearLayout box=new LinearLayout(this);box.setOrientation(1);
   TextView label=new TextView(this);
   int initial=Math.max(min,Math.min(max,AlertRules.limit(this,key)));
   label.setText(title+": "+initial+suffix);label.setTextSize(14);
   box.addView(label);
   SeekBar slider=new SeekBar(this);
   int steps=(max-min)/increment;
   slider.setMax(steps);
   slider.setProgress((initial-min)/increment);
   slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
     public void onProgressChanged(SeekBar bar,int progress,boolean user){
       int value=min+progress*increment;
       label.setText(title+": "+value+suffix);
       if(user)preferences.edit().putInt(key,value).apply();
     }
     public void onStartTrackingTouch(SeekBar bar){}
     public void onStopTrackingTouch(SeekBar bar){}
   });
   box.addView(slider);root.addView(box);
 }
}
