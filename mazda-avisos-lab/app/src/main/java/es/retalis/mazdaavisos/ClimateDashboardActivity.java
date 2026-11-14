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
import java.util.Locale;

/** Climate screen modelled after the official Mazda layout with live data only. */
public final class ClimateDashboardActivity extends Activity {
 private static final int INK=0xFF202630,MUTED=0xFF9A9DA9,PURPLE=0xFF9392E6,WHITE=Color.WHITE;
 private static final int BG=0xFFF5F4FB;
 private boolean seats=false;
 private LinearLayout root;
 private int target=24;
 private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
 private GradientDrawable bg(int color,int radius){
   GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
 }
 private TextView text(String value,int size,int color,boolean bold){
   TextView view=new TextView(this);view.setText(value);view.setTextColor(color);view.setTextSize(size);
   if(bold)view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return view;
 }
 private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(1);return l;}
 private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(0);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
 private void centre(LinearLayout c,String content,int size,int color,boolean bold){
   TextView t=text(content,size,color,bold);t.setGravity(Gravity.CENTER);
   c.addView(t);
 }
 private void button(LinearLayout parent,String icon,String title,boolean active,Runnable onClick){
   LinearLayout item=col();item.setGravity(Gravity.CENTER);
   item.setPadding(dp(4),dp(12),dp(4),dp(12));item.setBackground(bg(active?PURPLE:WHITE,23));
   TextView symbol=text(icon,29,active?WHITE:INK,false);symbol.setGravity(Gravity.CENTER);
   item.addView(symbol);
   TextView label=text(title,12,active?WHITE:INK,false);label.setGravity(Gravity.CENTER);
   LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(6);
   item.addView(label,lp);
   LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(0,dp(100),1);
   outer.leftMargin=dp(4);outer.rightMargin=dp(4);
   parent.addView(item,outer);
   item.setOnClickListener(v->onClick.run());
 }
 private void remote(String action){
   startActivity(new Intent(this,VehicleControlActivity.class)
     .putExtra(VehicleControlActivity.EXTRA_ACTION,action));
 }
 private void quick(String action){QuickControl.invoke(this,action,target);}
 @Override public void onCreate(Bundle state){super.onCreate(state);render();}
 @Override protected void onResume(){super.onResume();render();}
 void render(){
   VehicleSnapshot car=VehicleSnapshot.read(this);
   if(car.remoteTemp!=null&&target==24)target=(int)Math.round(car.remoteTemp);
   ScrollView scroll=new ScrollView(this);scroll.setBackgroundColor(BG);scroll.setFillViewport(true);
   root=col();root.setPadding(dp(16),dp(28),dp(16),dp(28));
   scroll.addView(root);setContentView(scroll);
   LinearLayout header=row();root.addView(header);
   TextView back=text("‹",34,INK,false);back.setPadding(dp(8),0,dp(18),0);
   back.setOnClickListener(v->finish());header.addView(back);
   TextView air=text("A/C",20,seats?MUTED:PURPLE,!seats);
   air.setPadding(dp(13),dp(6),dp(25),dp(6));
   air.setOnClickListener(v->{seats=false;render();});header.addView(air);
   TextView seatsTab=text("Asiento",20,seats?PURPLE:MUTED,seats);
   seatsTab.setPadding(dp(13),dp(6),dp(8),dp(6));
   seatsTab.setOnClickListener(v->{seats=true;render();});header.addView(seatsTab);
   View underline=new View(this);underline.setBackgroundColor(PURPLE);
   LinearLayout.LayoutParams u=new LinearLayout.LayoutParams(dp(46),dp(4));
   u.leftMargin=seats?dp(196):dp(72);u.topMargin=dp(5);root.addView(underline,u);
   if(!seats)drawAir(car);else drawSeats(car);
 }
 private void drawAir(VehicleSnapshot car){
   LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-1,dp(40));root.addView(new View(this),gap);
   centre(root,car.tempText(),62,INK,false);
   centre(root,"Temperatura interior",14,MUTED,false);
   LinearLayout overview=row();
   LinearLayout.LayoutParams rowP=new LinearLayout.LayoutParams(-1,-2);rowP.topMargin=dp(31);
   root.addView(overview,rowP);
   LinearLayout left=col();left.setGravity(Gravity.CENTER);
   overview.addView(left,new LinearLayout.LayoutParams(0,-2,1));
   centre(left,car.humidityText(),23,INK,true);
   centre(left,"Humedad interior",12,MUTED,false);
   LinearLayout right=col();right.setGravity(Gravity.CENTER);
   overview.addView(right,new LinearLayout.LayoutParams(0,-2,1));
   centre(right,car.airQualityText(),20,INK,true);
   centre(right,"Calidad del aire · "+car.airText(),12,MUTED,false);

   // Stylized cockpit panel built from native UI, not a screenshot or fake sensor image.
   LinearLayout cockpit=col();cockpit.setGravity(Gravity.CENTER);
   cockpit.setBackground(bg(0xFFE8EAF5,27));
   LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(176));cp.topMargin=dp(33);
   root.addView(cockpit,cp);
   centre(cockpit,"◯  ━━━━━━━━━  ◯",27,0xFF858B9D,false);
   centre(cockpit,"CX-6e · Interior",15,0xFF667181,true);
   centre(cockpit,car.climateText(),13,0xFF667181,false);

   LinearLayout control=col();control.setGravity(Gravity.CENTER);
   LinearLayout.LayoutParams ctl=new LinearLayout.LayoutParams(-1,-2);ctl.topMargin=dp(26);
   root.addView(control,ctl);
   TextView t=text(target+"°",43,INK,false);t.setGravity(Gravity.CENTER);control.addView(t);
   centre(control,"Temperatura deseada",12,MUTED,false);
   SeekBar slider=new SeekBar(this);slider.setMax(14);slider.setProgress(Math.min(14,Math.max(0,target-16)));
   slider.setProgressTintList(android.content.res.ColorStateList.valueOf(PURPLE));
   slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
     public void onProgressChanged(SeekBar bar,int v,boolean byUser){if(byUser){target=16+v;t.setText(target+"°");}}
     public void onStartTrackingTouch(SeekBar bar){}
     public void onStopTrackingTouch(SeekBar bar){}
   });
   control.addView(slider,new LinearLayout.LayoutParams(-1,dp(54)));
   centre(control,"16°                                            30°",11,MUTED,false);
   LinearLayout options=row();LinearLayout.LayoutParams opt=new LinearLayout.LayoutParams(-1,-2);opt.topMargin=dp(22);root.addView(options,opt);
   button(options,"≋","Desempañado\nfrontal",false,()->remote(VehicleControlActivity.DEFROST_ON));
   button(options,"☷","Apagar\ndesempañado",false,()->remote(VehicleControlActivity.DEFROST_OFF));
   button(options,"✦","Más controles\ny asientos",false,()->{seats=true;render();});
   TextView power=text(car.climate==1?"⏻  Apagar climatización":"⏻  Activar climatización",18,INK,true);
   power.setGravity(Gravity.CENTER);power.setBackground(bg(WHITE,24));
   LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(72));pp.topMargin=dp(25);
   root.addView(power,pp);
   power.setOnClickListener(v->quick(car.climate==1?
     VehicleControlActivity.CLIMATE_OFF:VehicleControlActivity.CLIMATE_ON));
   TextView warning=text("La climatización remota suele finalizar tras 15 minutos. La activación solicita confirmación a Mazda. Los datos se actualizan cuando están disponibles.",12,MUTED,false);
   warning.setGravity(Gravity.CENTER);
   LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,-2);wp.topMargin=dp(27);root.addView(warning,wp);
 }
 private void drawSeats(VehicleSnapshot car){
   LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-1,dp(50));root.addView(new View(this),gap);
   centre(root,"Asientos y volante",28,INK,true);
   centre(root,"Controles remotos del CX-6e",13,MUTED,false);
   LinearLayout cabin=col();cabin.setPadding(dp(19),dp(25),dp(19),dp(25));
   cabin.setBackground(bg(0xFFE9E9F4,30));
   LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,dp(260));cp.topMargin=dp(24);
   root.addView(cabin,cp);
   centre(cabin,"◯  ·  ◯",41,0xFF8B92A4,false);
   centre(cabin,"VOLANTE                   ACOMPAÑANTE",11,0xFF7B8193,false);
   LinearLayout buttons=row();LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(-1,-2);bp.topMargin=dp(24);cabin.addView(buttons,bp);
   button(buttons,"♨","Calor\nconductor",false,()->remote(VehicleControlActivity.DRIVER_HEAT));
   button(buttons,"✤","Ventilación\nconductor",false,()->remote(VehicleControlActivity.DRIVER_VENT));
   LinearLayout buttons2=row();root.addView(buttons2,new LinearLayout.LayoutParams(-1,-2));
   button(buttons2,"♨","Calor\nacompañante",false,()->remote(VehicleControlActivity.PASSENGER_HEAT));
   button(buttons2,"✤","Ventilación\nacompañante",false,()->remote(VehicleControlActivity.PASSENGER_VENT));
   LinearLayout wheel=row();LinearLayout.LayoutParams wp=new LinearLayout.LayoutParams(-1,-2);wp.topMargin=dp(17);root.addView(wheel,wp);
   button(wheel,"◉","Calentar\nvolante",false,()->remote(VehicleControlActivity.WHEEL_ON));
   button(wheel,"○","Apagar\nvolante",false,()->remote(VehicleControlActivity.WHEEL_OFF));
   centre(root,"Los mandos de asiento permiten elegir apagado o niveles 1–3.",12,MUTED,false);
   TextView power=text("⏻  Control general del clima",18,INK,true);power.setGravity(Gravity.CENTER);
   power.setBackground(bg(WHITE,24));
   LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(68));p.topMargin=dp(26);root.addView(power,p);
   power.setOnClickListener(v->{seats=false;render();});
 }
}
