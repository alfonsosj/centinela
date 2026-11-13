package es.retalis.mazdaavisos;

import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.service.quicksettings.TileService;

/** Acceso rápido, siempre iniciado voluntariamente por el usuario. */
public class MazdaQuickTile extends TileService {
 @Override public void onClick() {
   super.onClick();
   Runnable open = () -> {
     Intent i = new Intent(this, CheckActivity.class);
     i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
     if(Build.VERSION.SDK_INT >= 34){
       PendingIntent p = PendingIntent.getActivity(this, 301, i,
          PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
       startActivityAndCollapse(p);
     } else {
       startActivityAndCollapse(i);
     }
   };
   if(isLocked()) unlockAndRun(open); else open.run();
 }
}
