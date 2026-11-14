package es.retalis.mazdaavisos;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.*;

/** Readable battery overview with only validated Mazda operations enabled. */
public final class BatteryDashboardActivity extends Activity {
 private static final int BG=0xFFF3F2FA,INK=0xFF252A33,MUTED=0xFF9C9DA9,WHITE=Color.WHITE,PURPLE=0xFF9393E8;
 private LinearLayout root;
 private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
 private GradientDrawable surface(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
 private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 private TextView text(String val,int size,int color,boolean bold){
   TextView t=new TextView(this);t.setText(val);t.setTextSize(size);t.setTextColor(color);
   if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;
 }
 private void center(LinearLayout parent,String val,int size,int color,boolean bold){
   TextView t=text(val,size,color,bold);t.setGravity(Gravity.CENTER);parent.addView(t);
 }
 private LinearLayout card(String title){
   LinearLayout c=col();c.setPadding(dp(19),dp(19),dp(19),dp(19));c.setBackground(surface(WHITE,25));
   LinearLayout.LayoutParams l=new LinearLayout.LayoutParams(-1,-2);l.bottomMargin=dp(13);
   root.addView(c,l);c.addView(text(title,20,INK,true));return c;
 }
 private void detailRow(LinearLayout p,String title,String description,Runnable click){
   LinearLayout line=row();line.setPadding(0,dp(15),0,dp(14));p.addView(line);
   LinearLayout body=col();line.addView(body,new LinearLayout.LayoutParams(0,-2,1));
   body.addView(text(title,16,INK,true));
   TextView caption=text(description,12,MUTED,false);
   LinearLayout.LayoutParams cap=new LinearLayout.LayoutParams(-1,-2);cap.topMargin=dp(4);body.addView(caption,cap);
   TextView arrow=text("›",28,MUTED,false);line.addView(arrow);
   line.setOnClickListener(v->click.run());
 }
 private void info(String title,String message){
   new AlertDialog.Builder(this).setTitle(title).setMessage(message)
      .setPositiveButton("Entendido",null).show();
 }
 private void remote(String action){
   startActivity(new Intent(this,VehicleControlActivity.class)
     .putExtra(VehicleControlActivity.EXTRA_ACTION,action));
 }
 @Override public void onCreate(Bundle saved){super.onCreate(saved);render();}
 @Override protected void onResume(){super.onResume();render();}
 void render(){
   ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);scroll.setFillViewport(true);
   root=col();root.setPadding(dp(17),dp(23),dp(17),dp(32));scroll.addView(root);setContentView(scroll);
   VehicleSnapshot car=VehicleSnapshot.read(this);
   LinearLayout bar=row();root.addView(bar);
   TextView back=text("‹",34,INK,false);back.setPadding(dp(4),0,dp(22),0);back.setOnClickListener(v->finish());bar.addView(back);
   bar.addView(text("Batería",24,INK,true));

   LinearLayout title=col();LinearLayout.LayoutParams t=new LinearLayout.LayoutParams(-1,-2);t.topMargin=dp(42);root.addView(title,t);
   center(title,car.socText(),66,INK,false);
   LinearLayout data=row();LinearLayout.LayoutParams dp1=new LinearLayout.LayoutParams(-1,-2);dp1.topMargin=dp(32);
   root.addView(data,dp1);
   LinearLayout state=col();state.setGravity(Gravity.CENTER);data.addView(state,new LinearLayout.LayoutParams(0,-2,1));
   center(state,car.chargeText(),19,INK,true);center(state,"Estado",13,MUTED,false);
   LinearLayout distance=col();distance.setGravity(Gravity.CENTER);data.addView(distance,new LinearLayout.LayoutParams(0,-2,1));
   center(distance,car.rangeText(),19,INK,true);center(distance,"Autonomía",13,MUTED,false);

   LinearLayout illustration=col();illustration.setPadding(dp(20),dp(28),dp(20),dp(23));
   illustration.setGravity(Gravity.CENTER);illustration.setBackground(surface(0xFFE4E5F5,28));
   LinearLayout.LayoutParams ip=new LinearLayout.LayoutParams(-1,dp(190));ip.topMargin=dp(27);ip.bottomMargin=dp(23);
   root.addView(illustration,ip);
   center(illustration,"CX-6e  ·  Sistema eléctrico",17,0xFF767E97,true);
   ProgressBar charge=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
   charge.setMax(100);charge.setProgress(car.soc==null?0:(int)Math.round(car.soc));
   charge.setProgressTintList(android.content.res.ColorStateList.valueOf(PURPLE));
   LinearLayout.LayoutParams progress=new LinearLayout.LayoutParams(-1,dp(12));progress.topMargin=dp(27);
   illustration.addView(charge,progress);
   center(illustration,car.socText()+" disponible",12,0xFF767E97,false);

   LinearLayout chargeCard=card("Carga del vehículo");
   detailRow(chargeCard,"Cable de carga",car.cableText(),()->info("Cable de carga",
     "Estado comunicado por Mazda: "+car.cableText()+". "+car.age()));
   detailRow(chargeCard,"Objetivo de carga",car.targetText()+" · Ajustable del 60 al 100 %",
     ()->remote(VehicleControlActivity.CHARGE_LIMIT));
   detailRow(chargeCard,"Carga programada","Configuración consultable en Mazda oficial",
     ()->info("Carga programada",
       "La cuenta europea aún no tiene conectado en Mazda Avisos un endpoint de lectura y escritura verificado para activar la carga programada. "+
       "No se muestra un interruptor ficticio. Consulta el estado actual en la aplicación oficial Mazda."));
   detailRow(chargeCard,"Período de carga","Horario en la aplicación oficial Mazda",
     ()->info("Período de carga",
       "El coche permite programar períodos desde Mazda oficial, pero no hemos validado aquí el protocolo europeo para establecer el horario."));

   LinearLayout preheat=card("Precalentamiento de batería");
   TextView description=text("El precalentamiento puede consumir energía de la batería. Mazda recomienda programarlo con antelación.",13,MUTED,false);
   description.setPadding(0,dp(12),0,dp(5));preheat.addView(description);
   detailRow(preheat,"Programación y hora de salida","Consulta y modifica desde Mazda oficial",
     ()->info("Precalentamiento",
       "Mazda publica una API de planes de precalentamiento que requiere identificadores y programación asociados al coche. "+
       "Todavía no están disponibles en este panel. No activamos calentamiento sin leer primero el plan real."));
   TextView footer=text("Los datos reflejan la última lectura disponible de Mazda: "+car.age()+".",12,MUTED,false);
   footer.setGravity(Gravity.CENTER);footer.setPadding(dp(9),dp(9),dp(9),dp(15));root.addView(footer);
 }
}
