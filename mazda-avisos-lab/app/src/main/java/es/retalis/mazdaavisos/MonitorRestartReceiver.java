package es.retalis.mazdaavisos;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

/**
 * A boot/package-update reminder, not a background relaunch.
 *
 * We do not store the credentials required for MQTT. After reboot or app
 * replacement the user has to authenticate the secondary Mazda account again.
 * Starting a service at boot cannot make an expired credential valid.
 */
public final class MonitorRestartReceiver extends BroadcastReceiver {
  private static final String STATUS_PREFS="mqtt_status";
  private static final String REMINDER_CHANNEL="mazda_monitor_health";
  static final int REMINDER_ID=7902;

  @Override public void onReceive(Context context,Intent intent){
    if(intent==null)return;
    String action=intent.getAction();
    if(!Intent.ACTION_BOOT_COMPLETED.equals(action)
      &&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))return;
    SharedPreferences status=context.getSharedPreferences(STATUS_PREFS,Context.MODE_PRIVATE);
    if(!status.getBoolean("wants_monitor",false))return;
    String reason=Intent.ACTION_BOOT_COMPLETED.equals(action)?
      "Teléfono reiniciado. Abre Mazda Avisos para reactivar las notificaciones.":
      "Aplicación actualizada. Comprueba el monitor y vuelve a autenticarte si se ha detenido.";
    status.edit()
      .putBoolean("active",false)
      .putString("phase","login_needed")
      .putString("status",reason)
      .putLong("updated_at",System.currentTimeMillis())
      .apply();
    if(Build.VERSION.SDK_INT>=33 &&
      context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;
    NotificationManager nm=(NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
    if(nm==null)return;
    if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(
      new NotificationChannel(REMINDER_CHANNEL,"Estado del monitor Mazda",NotificationManager.IMPORTANCE_DEFAULT));
    PendingIntent open=PendingIntent.getActivity(context,7902,
      new Intent(context,MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP),
      PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    Notification notification=new Notification.Builder(context,REMINDER_CHANNEL)
      .setSmallIcon(android.R.drawable.stat_notify_error)
      .setContentTitle("Mazda Avisos: reactivar monitor")
      .setContentText(reason)
      .setStyle(new Notification.BigTextStyle().bigText(reason))
      .setContentIntent(open).setAutoCancel(true).build();
    nm.notify(REMINDER_ID,notification);
  }
}
