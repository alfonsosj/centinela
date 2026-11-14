package es.retalis.mazdaavisos;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.view.View;

/**
 * All Mazda EU cloud-control actions in a discoverable, one-screen catalogue.
 * Displaying an action does not certify vehicle-specific compatibility.
 */
public final class ControlsActivity extends Activity {
  private static final int INK=0xFF263548, MUTED=0xFF818C9D,
    BG=0xFFF5F6FA, WHITE=Color.WHITE, BLUE=0xFF898BEA, BORDER=0xFFE8EAF1;
  private LinearLayout root;
  private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+0.5f);}
  private GradientDrawable shape(int color,int radius){
    GradientDrawable result=new GradientDrawable();
    result.setColor(color);result.setCornerRadius(dp(radius));return result;
  }
  private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
  private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
  private TextView label(String text,int size,int color,boolean strong){
    TextView t=new TextView(this);t.setText(text);t.setTextSize(size);t.setTextColor(color);
    if(strong)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
    return t;
  }
  private LinearLayout section(String heading,String note){
    LinearLayout card=column();card.setPadding(dp(18),dp(17),dp(18),dp(16));
    card.setBackground(shape(WHITE,24));card.setElevation(dp(1));
    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);
    params.bottomMargin=dp(13);root.addView(card,params);
    card.addView(label(heading,17,INK,true));
    if(note!=null&&!note.isEmpty()){
      TextView desc=label(note,12,MUTED,false);desc.setPadding(0,dp(6),0,dp(7));card.addView(desc);
    }
    return card;
  }
  private void action(LinearLayout card,String icon,String title,String subtitle,String command,boolean primary){
    LinearLayout r=row();r.setPadding(dp(12),dp(11),dp(10),dp(11));
    r.setBackground(shape(primary?0xFFEEF0FE:BG,15));
    LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(8);
    card.addView(r,rp);
    TextView symbol=label(icon,21,INK,false);symbol.setGravity(Gravity.CENTER);
    r.addView(symbol,new LinearLayout.LayoutParams(dp(38),dp(42)));
    LinearLayout names=column();LinearLayout.LayoutParams nameParams=new LinearLayout.LayoutParams(0,-2,1);
    nameParams.leftMargin=dp(8);r.addView(names,nameParams);
    names.addView(label(title,14,INK,true));
    if(subtitle!=null&&!subtitle.isEmpty())names.addView(label(subtitle,11,MUTED,false));
    TextView arrow=label("›",25,BLUE,false);r.addView(arrow);
    r.setContentDescription(title+". "+subtitle);
    r.setOnClickListener(view->open(command));
  }
  private void open(String command){
    startActivity(new Intent(this,VehicleControlActivity.class)
      .putExtra(VehicleControlActivity.EXTRA_ACTION,command));
  }
  private void small(String message){
    TextView t=label(message,12,MUTED,false);
    LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(13);
    root.addView(t,p);
  }
  @Override public void onCreate(Bundle state){super.onCreate(state);render();}
  @Override protected void onResume(){super.onResume();render();}
  private void render(){
    ScrollView scroller=new ScrollView(this);
    scroller.setFillViewport(true);scroller.setBackgroundColor(BG);
    root=column();root.setPadding(dp(17),dp(24),dp(17),dp(28));
    scroller.addView(root);setContentView(scroller);
    LinearLayout heading=row();root.addView(heading);
    TextView back=label("‹",30,INK,false);back.setPadding(dp(5),0,dp(16),0);
    back.setOnClickListener(v->finish());heading.addView(back);
    heading.addView(label("Controles del CX-6e",22,INK,true));
    TextView intro=label("Cuenta Mazda compartida · Sin Home Assistant\n" +
      "Las órdenes distintas del cierre son experimentales y dependen de los permisos y el equipamiento del vehículo.",
      12,MUTED,false);intro.setPadding(0,dp(13),0,dp(19));root.addView(intro);
    VehicleSnapshot v=VehicleSnapshot.read(this);
    LinearLayout information=section("Estado actual",v.age()+(v.stale()?" · posible lectura antigua":""));
    information.addView(label("Puertas: "+v.doorText()+"     Ventanas: "+v.windowText(),13,INK,false));
    information.addView(label("Maletero: "+v.trunkText()+"     Clima: "+v.climateText(),13,INK,false));
    information.addView(label("Batería: "+v.socText()+"     "+v.chargeText(),13,INK,false));
    action(information,"↻","Actualizar estado Mazda",
      "Consulta de solo lectura. Puede requerir nueva autenticación.",
      VehicleControlActivity.REFRESH,true);
    LinearLayout doors=section("Puertas","Bloqueo y desbloqueo: función probada en tu CX-6e. Requiere PIN de seis cifras.");
    if(v.lock==0&&!v.stale()){
      action(doors,"🔓","Desbloquear puertas","Solicitar apertura de las cerraduras",VehicleControlActivity.UNLOCK,true);
    }else if(v.lock==1&&!v.stale()){
      action(doors,"🔒","Bloquear puertas","Solicitar cierre de las cerraduras",VehicleControlActivity.LOCK,true);
    }else{
      action(doors,"🔒","Bloquear puertas","Si has verificado visualmente que procede",VehicleControlActivity.LOCK,false);
      action(doors,"🔓","Desbloquear puertas","Si has verificado visualmente que procede",VehicleControlActivity.UNLOCK,false);
    }
    LinearLayout windows=section("Ventanillas","Estado: "+v.windowText()+" · Movimiento remoto experimental, con PIN.");
    action(windows,"▣","Cerrar todas","Comprueba que nadie pueda quedar atrapado",VehicleControlActivity.WINDOWS_CLOSE,true);
    action(windows,"▢","Abrir todas","Comprueba el entorno del vehículo",VehicleControlActivity.WINDOWS_OPEN,false);
    LinearLayout trunk=section("Maletero","Estado: "+v.trunkText()+" · Movimiento remoto experimental, con PIN.");
    action(trunk,"▤","Abrir maletero","Necesita espacio libre y coche aparcado",VehicleControlActivity.TRUNK_OPEN,true);
    action(trunk,"▥","Cerrar maletero","No debe haber obstáculos ni personas",VehicleControlActivity.TRUNK_CLOSE,false);
    LinearLayout climate=section("Climatización","Estado: "+v.climateText()+" · Control sin PIN; requiere confirmación.");
    action(climate,"❄","Encender clima","Permite elegir temperatura entre 16 y 30 °C",VehicleControlActivity.CLIMATE_ON,true);
    action(climate,"○","Apagar clima","Desconectar climatización remota",VehicleControlActivity.CLIMATE_OFF,false);
    action(climate,"≋","Activar desempañado","Control experimental del parabrisas",VehicleControlActivity.DEFROST_ON,false);
    action(climate,"≋","Desactivar desempañado","Control experimental del parabrisas",VehicleControlActivity.DEFROST_OFF,false);
    LinearLayout find=section("Localización y señales","Pueden activarse luces o bocina y causar molestias.");
    action(find,"◉","Localizar coche","Luces y sonido para localizarlo",VehicleControlActivity.FIND,true);
    action(find,"♫","Hacer sonar bocina","Úsalo solo donde esté permitido",VehicleControlActivity.HORN,false);
    LinearLayout thermal=section("Volante y asientos","Controles experimentales. Cada asiento permite nivel 0 a 3.");
    action(thermal,"◉","Calentar volante","Encender",VehicleControlActivity.WHEEL_ON,true);
    action(thermal,"○","Apagar calefacción volante","Apagar",VehicleControlActivity.WHEEL_OFF,false);
    action(thermal,"♨","Calefacción asiento conductor","Apagado, bajo, medio o alto",VehicleControlActivity.DRIVER_HEAT,false);
    action(thermal,"♨","Calefacción asiento acompañante","Apagado, bajo, medio o alto",VehicleControlActivity.PASSENGER_HEAT,false);
    action(thermal,"♧","Ventilar asiento conductor","Apagado, bajo, medio o alto",VehicleControlActivity.DRIVER_VENT,false);
    action(thermal,"♧","Ventilar asiento acompañante","Apagado, bajo, medio o alto",VehicleControlActivity.PASSENGER_VENT,false);
    LinearLayout charge=section("Carga y batería",
      "Batería: "+v.socText()+" · "+v.chargeText()+" · "+v.cableText());
    action(charge,"▰","Límite máximo de carga","Ajustar entre 60 % y 100 %. No inicia la carga.",VehicleControlActivity.CHARGE_LIMIT,true);
    small("No hay un comando de inicio/parada de carga verificado para esta versión. "+
      "Tampoco hay grabación Centinela ni eventos de ocupantes confirmados por MQTT.\n\n"+
      "La confirmación de una petición por la API no siempre demuestra que una acción se haya ejecutado. "+
      "Comprueba el vehículo físicamente y consulta el historial de resultados.");
  }
}
