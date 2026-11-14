package es.retalis.mazdaavisos;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;
import java.util.Locale;

/** Only a small local, non-identifying cache of the LAST observed vehicle values. */
final class VehicleSnapshot {
 private static final String PREF="vehicle_display";
 static final int UNKNOWN=-1;
 final Double soc,temp,range,humidity,pm25,remoteTemp;
 final int charge,lock,parked,windowsOpen,windowsKnown,trunk,cable,climate,chargeTarget;
 final long lastDataAt;
 final int reports;
 private VehicleSnapshot(SharedPreferences p){
   soc=readDouble(p,"soc");temp=readDouble(p,"temp");range=readDouble(p,"range");
   humidity=readDouble(p,"humidity");pm25=readDouble(p,"pm25");remoteTemp=readDouble(p,"remote_temp");
   charge=p.getInt("charge",-1);lock=p.getInt("lock",-1);parked=p.getInt("parked",-1);
   windowsOpen=p.getInt("windows_open",0);windowsKnown=p.getInt("windows_known",0);
   trunk=p.getInt("trunk",-1);cable=p.getInt("cable",-1);
   climate=p.getInt("climate",-1);chargeTarget=p.getInt("charge_target",-1);
   lastDataAt=p.getLong("last_data_at",0L);reports=p.getInt("reports",0);
 }
 static VehicleSnapshot read(Context context){
   return new VehicleSnapshot(context.getSharedPreferences(PREF,Context.MODE_PRIVATE));
 }
 private static Double readDouble(SharedPreferences p,String key){
   return p.contains(key)?(double)p.getFloat(key,0):null;
 }
 private static Double number(JSONObject raw,String key,double min,double max){
   Object obj=raw.opt(key);
   if(obj==null||obj==JSONObject.NULL||obj instanceof Boolean)return null;
   try{double d=Double.parseDouble(String.valueOf(obj));
     return Double.isFinite(d)&&d>=min&&d<=max?d:null;
   }catch(Exception ignored){return null;}
 }
 private static Double first(JSONObject raw,double min,double max,String... keys){
   for(String key:keys){Double v=number(raw,key,min,max);if(v!=null)return v;}
   return null;
 }
 private static Integer code(JSONObject raw,String key,int low,int high){
   Double n=number(raw,key,low,high);
   return n!=null&&n==Math.rint(n)?n.intValue():null;
 }
 static void observe(Context context,JSONObject raw){
   if(raw==null||raw.length()==0)return;
   SharedPreferences p=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
   SharedPreferences.Editor e=p.edit();
   boolean fresh=false;
   Double soc=first(raw,0,100,"soc","socDsp","remainPower");
   if(soc!=null){e.putFloat("soc",soc.floatValue());fresh=true;}
   Double temp=number(raw,"vehicleTemperature",-30,80);
   if(temp!=null&&temp!=0){e.putFloat("temp",temp.floatValue());fresh=true;}
   Double range=first(raw,0,1500,"remainedPowerMile","totalResidualMileage");
   if(range!=null){e.putFloat("range",range.floatValue());fresh=true;}
   Integer trunk=code(raw,"trunk",0,1);
   if(trunk!=null){e.putInt("trunk",trunk);fresh=true;}
   Integer cable=code(raw,"chargeConStatus",0,1);
   if(cable==null)cable=code(raw,"acChargeGunConnectionState",0,1);
   if(cable!=null){e.putInt("cable",cable);fresh=true;}
   Double humidity=number(raw,"insideHumidity",0,100);
   if(humidity!=null){e.putFloat("humidity",humidity.floatValue());fresh=true;}
   Double pm25=number(raw,"insidePm25",0,1000);
   if(pm25!=null){e.putFloat("pm25",pm25.floatValue());fresh=true;}
   Double remoteTemp=number(raw,"remoteTemp",150,350);
   if(remoteTemp!=null){e.putFloat("remote_temp",(float)(remoteTemp/10));fresh=true;}
   Integer climate=code(raw,"acStatus",0,1);
   if(climate!=null){e.putInt("climate",climate);fresh=true;}
   Integer target=code(raw,"maxSocPercent",0,100);
   if(target!=null){e.putInt("charge_target",target);fresh=true;}
   Integer locked=code(raw,"driverDoorLock",0,1);
   if(locked!=null){e.putInt("lock",locked);fresh=true;}
   Integer parked=code(raw,"carStatus",1,2);
   if(parked!=null){e.putInt("parked",parked==2?1:0);fresh=true;}
   Integer charge=code(raw,"batteryChargeStatus",0,10);
   if(charge!=null){
     if(charge==0)e.putInt("charge",0);
     else if(charge==1)e.putInt("charge",1);
     else if(charge==4)e.putInt("charge",2);
     fresh=true;
   }else{
     charge=code(raw,"chargeStatus",0,10);
     if(charge!=null){
       if(charge==1)e.putInt("charge",1);
       else if(charge==4)e.putInt("charge",2);
       fresh=true;
     }
   }
   final String[] keys={"diverWindow","passengerWindow","leftRearWindow","rightRearWindow"};
   int windowsOpen=0,known=0;
   for(int i=0;i<4;i++){
     Integer v=code(raw,keys[i],0,100);
     if(v!=null){e.putInt("window_"+i,v==0?0:1);fresh=true;}
     int value=v!=null?(v==0?0:1):p.getInt("window_"+i,-1);
     if(value>=0){known++;if(value==1)windowsOpen++;}
   }
   e.putInt("windows_open",windowsOpen).putInt("windows_known",known);
   if(fresh){
     e.putLong("last_data_at",System.currentTimeMillis());
     e.putInt("reports",p.getInt("reports",0)+1);
   }
   e.apply();
 }
 /** Initial authoritative status snapshot from Mazda's EU HTTPS vehicle API. */
 static boolean observeApi(Context context,JSONObject data){
   if(data==null)return false;
   SharedPreferences prefs=context.getSharedPreferences(PREF,Context.MODE_PRIVATE);
   SharedPreferences.Editor edit=prefs.edit();
   boolean updated=false;
   JSONObject vs=data.optJSONObject("vehicleStatus");
   if(vs!=null){
     Double soc=number(vs,"soc",0,100);
     if(soc!=null){edit.putFloat("soc",soc.floatValue());updated=true;}
     Double mileage=number(vs,"drvMileage",0,1500);
     if(mileage!=null){edit.putFloat("range",mileage.floatValue());updated=true;}
     Integer parked=code(vs,"status",1,2);
     if(parked!=null){edit.putInt("parked",parked==2?1:0);updated=true;}
   }
   JSONObject hvac=data.optJSONObject("hvac");
   if(hvac!=null){
     Double humidity=number(hvac,"insideHumidity",0,100);
     if(humidity!=null){edit.putFloat("humidity",humidity.floatValue());updated=true;}
     Double pm25=number(hvac,"insidePm25",0,1000);
     if(pm25!=null){edit.putFloat("pm25",pm25.floatValue());updated=true;}
     Double remoteTemp=number(hvac,"remoteTemp",150,350);
     if(remoteTemp!=null){edit.putFloat("remote_temp",(float)(remoteTemp/10));updated=true;}
     Integer ac=code(hvac,"acStatus",0,1);
     if(ac!=null){edit.putInt("climate",ac);updated=true;}
     Double t=number(hvac,"insideTemp",-500,800);
     if(t!=null&&t!=0){edit.putFloat("temp",(float)(t/10.0));updated=true;}
   }
   JSONObject door=data.optJSONObject("door");
   if(door!=null){
     Integer trunk=code(door,"trunk",0,1);
     if(trunk!=null){edit.putInt("trunk",trunk);updated=true;}
     Integer locked=code(door,"driverLock",0,1);
     if(locked!=null){edit.putInt("lock",locked);updated=true;}
   }
   JSONObject charge=data.optJSONObject("charge");
   if(charge!=null){
     Integer cable=code(charge,"chargeConStatus",0,1);
     if(cable!=null){edit.putInt("cable",cable);updated=true;}
     Integer target=code(charge,"maxSocPercent",0,100);
     if(target!=null){edit.putInt("charge_target",target);updated=true;}
     Integer mode=code(charge,"chargeStatus",-1,10);
     if(mode!=null){
       if(mode==0)edit.putInt("charge",0);
       else if(mode==6)edit.putInt("charge",1);
       else if(mode==1)edit.putInt("charge",2);
       updated=true;
     }
   }
   JSONObject window=data.optJSONObject("window");
   if(window!=null){
     org.json.JSONArray arr=window.optJSONArray("windows");
     if(arr!=null){
       int known=0,open=0;
       for(int i=0;i<4;i++){
         Object v=arr.opt(i);
         try{
           double n=Double.parseDouble(String.valueOf(v));
           if(n>=0&&n<=100&&Double.isFinite(n)){
             int code=n==0?0:1;edit.putInt("window_"+i,code);
             known++;if(code==1)open++;
           }
         }catch(Exception ignored){}
       }
       if(known>0){edit.putInt("windows_known",known).putInt("windows_open",open);updated=true;}
     }
   }
   if(updated){edit.putLong("last_data_at",System.currentTimeMillis());edit.apply();}
   return updated;
 }
 String age(){
   if(lastDataAt<=0)return "Sin datos recibidos todavía";
   long secs=Math.max(0,(System.currentTimeMillis()-lastDataAt)/1000L);
   if(secs<60)return "Datos de hace "+secs+" s";
   if(secs<3600)return "Datos de hace "+(secs/60)+" min";
   return "Datos de hace "+(secs/3600)+" h";
 }
 boolean stale(){return lastDataAt<=0||System.currentTimeMillis()-lastDataAt>20*60*1000L;}
 String socText(){return soc==null?"—":String.format(Locale.getDefault(),"%.0f%%",soc);}
 String humidityText(){return humidity==null?"—":String.format(Locale.getDefault(),"%.0f%%",humidity);}
 String airText(){
   if(pm25==null)return "Sin datos";
   return String.format(Locale.getDefault(),"%.0f µg/m³",pm25);
 }
 String airQualityText(){
   if(pm25==null)return "Sin datos";
   if(pm25<=12)return "Excelente";
   if(pm25<=35)return "Buena";
   if(pm25<=55)return "Moderada";
   return "Alta concentración";
 }
 String remoteTempText(){return remoteTemp==null?"—":String.format(Locale.getDefault(),"%.0f°",remoteTemp);}

 String tempText(){return temp==null?"—":String.format(Locale.getDefault(),"%.0f°",temp);}
 String rangeText(){return range==null?"—":String.format(Locale.getDefault(),"%.0f km",range);}
 String chargeText(){return charge==1?"Cargando":charge==2?"En espera":charge==0?"Sin cargar":"Estado no disponible";}
 String doorText(){return lock==0?"Bloqueado":lock==1?"Desbloqueado":"Sin datos";}
 String locationText(){return parked==1?"Aparcado":parked==0?"Estado no aparcado":"Estado no disponible";}
 String windowText(){return windowsKnown==0?"Sin datos":windowsOpen+" abierta"+(windowsOpen==1?"":"s")+(windowsKnown<4?" · parcial":"");}
 String trunkText(){return trunk==0?"Cerrado":trunk==1?"Abierto":"Sin datos";}
 String cableText(){return cable==0?"Sin cable":cable==1?"Enchufado":"Cable sin datos";}
 String climateText(){return climate==1?"Climatización encendida":climate==0?"Climatización apagada":"Climatización sin datos";}
 String targetText(){return chargeTarget>0?chargeTarget+" %":"Sin objetivo confirmado";}
 boolean freshForControl(){return !stale();}
}
