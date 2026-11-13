package es.retalis.mazdaavisos;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/** Solo observa la app europea. Un clic automático requiere una orden manual reciente. */
public class MazdaMonitor extends AccessibilityService {
 static final String PACKAGE="com.cma.mazda.eu";
 static final Pattern DATE=Pattern.compile("\\b\\d{1,2}/\\d{1,2}(?:/\\d{4})?(?:\\s+\\d{2}:\\d{2})?\\b");
 private static volatile long scanUntil=0;
 private static volatile boolean clickAttempted=false;
 long previous=0;

 static void requestSingleCheck(){
   scanUntil=SystemClock.elapsedRealtime()+30000;
   clickAttempted=false;
 }
 static void stopCheck(){scanUntil=0;clickAttempted=false;}

 @Override public void onAccessibilityEvent(AccessibilityEvent e) {
  if(e==null||e.getPackageName()==null||!PACKAGE.contentEquals(e.getPackageName()))return;
  long now=SystemClock.elapsedRealtime();if(now-previous<600)return;previous=now;
  AccessibilityNodeInfo root=getRootInActiveWindow();if(root==null)return;
  try{
   if(root.getPackageName()==null||!PACKAGE.contentEquals(root.getPackageName()))return;
   ArrayList<String> lines=new ArrayList<>();
   collect(root,lines,0);
   if(lines.isEmpty())return;
   boolean center=false;
   for(String s:lines)if(normal(s).contains("centro de mensajes")||normal(s).contains("message center"))center=true;
   if(center){
     scanUntil=0;
     parseMessages(lines);
     return;
   }
   if(scanUntil>0) {
     if(now>scanUntil){
        Data.status(this,"El acceso automático no encontró el Centro de Mensajes. Abre Mazda manualmente.");
        stopCheck();
     } else {
        Data.diagnostic(this,snapshot(root));
        if(!clickAttempted) {
          AccessibilityNodeInfo button=findMessageButton(root,0);
          if(button!=null){
            clickAttempted=true;
            boolean clicked=clickOrParent(button);
            Data.status(this,clicked
                ?"Icono de mensajes pulsado. Esperando pantalla..."
                :"Se encontró el icono, pero Android no permitió pulsarlo.");
            if(!clicked) clickAttempted=false;
          } else {
            Data.status(this,"Mazda abierto. No se ha identificado el botón de mensajes; consulta el diagnóstico.");
          }
        }
     }
   }
  } finally {root.recycle();}
 }

 private void parseMessages(List<String> lines){
   StringBuilder diagnostic=new StringBuilder();
   for(String s:lines)if(diagnostic.length()+s.length()<4300)diagnostic.append(s).append("\n");
   Data.diagnostic(this,diagnostic.toString());
   int discovered=0;boolean first=!Data.initialized(this);
   for(int i=0;i<lines.size();i++){
     String title=lines.get(i);if(!title(title))continue;
     String date="";String body="";
     for(int j=Math.max(0,i-2);j<Math.min(lines.size(),i+5);j++){
       Matcher m=DATE.matcher(lines.get(j));if(m.find()){date=m.group();break;}
     }
     for(int j=i+1;j<Math.min(lines.size(),i+7);j++){
       String t=lines.get(j);if(title(t)&&!similar(title,t))break;
       if(t.length()>35&&!similar(title,t)){body=t;break;}
     }
     String identity=normal(title)+"|"+date+"|"+normal(body);
     String description=title+(date.isEmpty()?"":" · "+date)+(body.isEmpty()?"":"\n"+body);
     if(Data.save(this,identity,description)){
       discovered++;
       if(!first)MainActivity.alert(this,"Nuevo mensaje Mazda (experimental)",description,1000+Math.abs(identity.hashCode()%300000));
     }
   }
   if(first)Data.initialized(this,true);
   Data.status(this,"Centro detectado · "+lines.size()+" textos · "+discovered+
      " mensajes nuevos · "+new SimpleDateFormat("dd/MM HH:mm:ss",Locale.getDefault()).format(new Date()));
 }

 private AccessibilityNodeInfo findMessageButton(AccessibilityNodeInfo n,int depth){
   if(n==null||depth>30)return null;
   String id=n.getViewIdResourceName();
   if(id!=null&&(id.endsWith(":id/iv_home_msg")||id.endsWith("/iv_home_msg")))return n;
   // Solo aceptamos identificador exacto; no tocamos botones desconocidos del coche.
   for(int i=0;i<n.getChildCount();i++){
     AccessibilityNodeInfo child=n.getChild(i);
     if(child!=null){
       AccessibilityNodeInfo match=findMessageButton(child,depth+1);
       if(match!=null)return match;
       child.recycle();
     }
   }
   return null;
 }
 private boolean clickOrParent(AccessibilityNodeInfo n){
   AccessibilityNodeInfo cur=n;
   for(int i=0;i<5&&cur!=null;i++){
     if(cur.isClickable())return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK);
     cur=cur.getParent();
   }
   return n.performAction(AccessibilityNodeInfo.ACTION_CLICK);
 }

 /** Diagnóstico limitado a estructura de botones; omite textos de campos y datos privados. */
 private String snapshot(AccessibilityNodeInfo root){
   StringBuilder s=new StringBuilder("Elementos interactivos Mazda (sin textos de usuario):\n");
   appendNodes(root,s,0);
   return s.toString();
 }
 private void appendNodes(AccessibilityNodeInfo n,StringBuilder out,int depth){
   if(n==null||depth>30||out.length()>6000)return;
   String id=n.getViewIdResourceName();
   String cls=n.getClassName()==null?"":n.getClassName().toString();
   if(n.isClickable() || (id!=null&&id.contains("msg"))){
     // No copiar descripciones ni contenidos: podrían contener datos personales.
     out.append("nivel=").append(depth)
        .append(" id=").append(id==null?"(sin id)":id)
        .append(" tipo=").append(cls)
        .append(" clicable=").append(n.isClickable()).append("\n");
   }
   for(int i=0;i<n.getChildCount();i++){
     AccessibilityNodeInfo child=n.getChild(i);
     if(child!=null){appendNodes(child,out,depth+1);child.recycle();}
   }
 }

 private void collect(AccessibilityNodeInfo n,List<String> out,int depth){
  if(n==null||depth>28||out.size()>220)return;
  CharSequence s=n.getText();
  if(s!=null){
    String t=s.toString().trim().replaceAll("\\s+"," ");
    if(!t.isEmpty()&&(out.isEmpty()||!out.get(out.size()-1).equals(t)))out.add(t);
  }
  for(int i=0;i<n.getChildCount();i++){
    AccessibilityNodeInfo c=n.getChild(i);
    if(c!=null){collect(c,out,depth+1);c.recycle();}
  }
 }
 static String normal(String s){
  return Normalizer.normalize(s.toLowerCase(Locale.ROOT),Normalizer.Form.NFD)
     .replaceAll("\\p{M}","").trim();
 }
 boolean title(String s){
  String t=normal(s);if(t.length()<10||t.length()>160||t.contains("centro de mensajes"))return false;
  for(String prefix:new String[]{"fallo ","aviso ","alerta ","advertencia ","error ",
      "recordatorio ","bateria ","carga ","vehiculo ","puerta ","message ",
      "warning ","alarm ","charging "})if(t.startsWith(prefix))return true;
  return false;
 }
 boolean similar(String a,String b){
  String x=normal(a).replace("…","").replace("...",""),y=normal(b).replace("…","").replace("...","");
  int n=Math.min(14,Math.min(x.length(),y.length()));
  return n>=8&&x.substring(0,n).equals(y.substring(0,n));
 }
 @Override public void onInterrupt(){stopCheck();}
}
