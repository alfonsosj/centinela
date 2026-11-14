package es.retalis.mazdaavisos;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.text.InputFilter;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Execute the previously verified command implementation in place (without
 * navigating to VehicleControlActivity). No optimistic UI updates, no stored PIN.
 */
final class QuickControl {
 private static final AtomicBoolean BUSY=new AtomicBoolean(false);
 private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
 private QuickControl(){}

 static void invoke(Activity activity,String command){
   invoke(activity,command,24);
 }
 static void invoke(Activity activity,String command,int temp){
   if(activity.isFinishing())return;
   ControlSession.State session=ControlSession.get();
   if(session==null){
     new AlertDialog.Builder(activity).setTitle("Autenticación Mazda")
       .setMessage("La cuenta secundaria necesita autenticarse para enviar el control. Después de iniciar sesión se volverá a esta pantalla para completar la orden.")
       .setNegativeButton("Cancelar",null)
       .setPositiveButton("Autenticar",(d,w)->{
         activity.startActivity(new Intent(activity,ConnectionTestActivity.class)
           .putExtra(VehicleControlActivity.EXTRA_ACTION,command)
           .putExtra(MainActivity.EXTRA_INLINE_LOGIN,true));
       }).show();
     return;
   }
   if(BUSY.get()){
     new AlertDialog.Builder(activity).setMessage("Ya hay otra orden Mazda en curso.")
       .setPositiveButton("Entendido",null).show();
     return;
   }
   VehicleSnapshot vehicle=VehicleSnapshot.read(activity);
   boolean hardware=command.equals(VehicleControlActivity.WINDOWS_OPEN)||
     command.equals(VehicleControlActivity.WINDOWS_CLOSE)||
     command.equals(VehicleControlActivity.TRUNK_OPEN)||
     command.equals(VehicleControlActivity.TRUNK_CLOSE);
   if(hardware&&(vehicle.stale()||vehicle.parked!=1)){
     new AlertDialog.Builder(activity).setTitle("Actualización necesaria")
       .setMessage("Para mover ventanillas o maletero Mazda debe confirmar recientemente que el coche está aparcado. Actualiza su estado antes.")
       .setNegativeButton("Cancelar",null)
       .setPositiveButton("Actualizar",(d,w)->invoke(activity,VehicleControlActivity.REFRESH)).show();
     return;
   }
   if(command.equals(VehicleControlActivity.REFRESH)){
     send(activity,session,command,"",temp);
     return;
   }
   boolean pinRequired=command.equals(VehicleControlActivity.LOCK)||
     command.equals(VehicleControlActivity.UNLOCK)||hardware;
   final EditText pin=new EditText(activity);
   pin.setHint("PIN Mazda de seis cifras");
   pin.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_VARIATION_PASSWORD);
   pin.setFilters(new InputFilter[]{new InputFilter.LengthFilter(6)});
   LinearLayout layout=new LinearLayout(activity);
   layout.setOrientation(1);int pad=dp(activity,20);
   layout.setPadding(pad,0,pad,0);
   TextView help=new TextView(activity);
   String title=actionLabel(command);
   String warning=hardware?"Asegúrate de que no hay personas, animales u obstáculos cerca de las partes móviles.":
     command.equals(VehicleControlActivity.UNLOCK)?"Comprueba que el vehículo puede quedar desbloqueado con seguridad.":
     "La orden se envía a Mazda y puede tardar varios segundos en confirmarse.";
   help.setText(warning);
   layout.addView(help);
   if(pinRequired)layout.addView(pin);
   final AlertDialog confirm=new AlertDialog.Builder(activity)
     .setTitle(title).setView(layout)
     .setNegativeButton("Cancelar",null)
     .setPositiveButton("Enviar orden",null).create();
   confirm.setOnShowListener(d->confirm.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
     String code=pinRequired?pin.getText().toString():"";
     if(pinRequired&&!code.matches("[0-9]{6}")){
       pin.setError("Introduce exactamente 6 cifras");
       return;
     }
     pin.setText("");
     confirm.dismiss();
     send(activity,session,command,code,temp);
   }));
   confirm.show();
 }
 private static int dp(Activity a,int x){return (int)(x*a.getResources().getDisplayMetrics().density+.5f);}
 private static String actionLabel(String c){
   if(c.equals(VehicleControlActivity.LOCK))return "Bloquear puertas";
   if(c.equals(VehicleControlActivity.UNLOCK))return "Desbloquear puertas";
   if(c.equals(VehicleControlActivity.WINDOWS_OPEN))return "Bajar las cuatro ventanillas";
   if(c.equals(VehicleControlActivity.WINDOWS_CLOSE))return "Subir las cuatro ventanillas";
   if(c.equals(VehicleControlActivity.TRUNK_OPEN))return "Abrir maletero";
   if(c.equals(VehicleControlActivity.TRUNK_CLOSE))return "Cerrar maletero";
   if(c.equals(VehicleControlActivity.CLIMATE_ON))return "Encender climatización";
   if(c.equals(VehicleControlActivity.CLIMATE_OFF))return "Apagar climatización";
   if(c.equals(VehicleControlActivity.CHARGE_LIMIT))return "Cambiar límite de carga";
   if(c.equals(VehicleControlActivity.REFRESH))return "Actualizar el vehículo";
   return "Control remoto Mazda";
 }
 private static void send(Activity activity,ControlSession.State session,String command,String pin,int temp){
   if(!BUSY.compareAndSet(false,true))return;
   final ProgressDialog progress=new ProgressDialog(activity);
   progress.setTitle(actionLabel(command));progress.setMessage("Conectando con Mazda…");
   progress.setCancelable(false);progress.show();
   WORKER.execute(()->{
     String result;
     try{
       MazdaRemoteApi api=new MazdaRemoteApi(activity,message->activity.runOnUiThread(()->{
         if(!activity.isFinishing()&&progress.isShowing())progress.setMessage(message);
       }));
       if(command.equals(VehicleControlActivity.REFRESH)){
         api.refresh(session);
         VehicleSnapshot car=VehicleSnapshot.read(activity);
         result="Actualizado: "+car.doorText()+" · "+car.socText()+" · "+car.rangeText();
       }else if(command.equals(VehicleControlActivity.LOCK)||command.equals(VehicleControlActivity.UNLOCK)){
         result=api.execute(session,pin,command.equals(VehicleControlActivity.UNLOCK)).message;
       }else{
         result=api.executeAdvanced(session,pin,command,temp,80,0).message;
       }
       AlertHistory.add(activity,actionLabel(command),result);
     }catch(Exception e){
       result="Mazda no ha confirmado la orden: "+MazdaRemoteApi.displayError(e);
       AlertHistory.add(activity,actionLabel(command),result);
     }finally{BUSY.set(false);}
     final String outcome=result;
     activity.runOnUiThread(()->{
       if(progress.isShowing())progress.dismiss();
       if(activity.isFinishing()||activity.isDestroyed())return;
       if(activity instanceof MainActivity)((MainActivity)activity).render();
       new AlertDialog.Builder(activity).setTitle(actionLabel(command))
         .setMessage(outcome)
         .setPositiveButton("Aceptar",null).show();
     });
   });
 }
}
