package es.retalis.mazdaavisos;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/**
 * Generic original electric crossover illustration, not a Mazda image.
 * It is purely decorative; telemetry is displayed in separate cards.
 */
public final class VehicleSketchView extends View {
 private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
 public VehicleSketchView(Context context){super(context);setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
 private void color(int c){p.setColor(c);p.setStyle(Paint.Style.FILL);p.setStrokeWidth(1);p.setShader(null);}
 private void path(Path x,int c){color(c);getCanvasPlaceholder();} // overridden by drawPath below
 private void getCanvasPlaceholder(){} // no-op helper, keeps paints isolated
 @Override protected void onDraw(Canvas canvas){
   super.onDraw(canvas);
   canvas.save();
   float scale=Math.min(getWidth()/360f,getHeight()/170f);
   canvas.translate((getWidth()-360f*scale)/2,(getHeight()-170f*scale)/2);
   canvas.scale(scale,scale);
   // Ground shadow.
   p.setShader(new RadialGradient(182,140,135,new int[]{0x460E2337,0x000E2337},null,Shader.TileMode.CLAMP));
   canvas.drawOval(24,128,337,152,p);p.setShader(null);
   // Body: clean crossover silhouette with sloped fastback roof.
   Path shell=new Path();
   shell.moveTo(24,105);shell.cubicTo(26,96,39,90,62,88);
   shell.lineTo(102,84);shell.cubicTo(114,70,132,50,155,44);
   shell.cubicTo(192,35,253,44,279,61);
   shell.lineTo(314,82);shell.cubicTo(335,87,345,94,345,108);
   shell.lineTo(337,123);shell.lineTo(28,124);shell.close();
   p.setShader(new LinearGradient(160,46,160,129,0xFFF2F6FA,0xFFA2B4C2,Shader.TileMode.CLAMP));
   canvas.drawPath(shell,p);p.setShader(null);
   p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(0xFF8299AA);canvas.drawPath(shell,p);p.setStyle(Paint.Style.FILL);
   // Roof glass and A/B/C pillars.
   Path glass=new Path();
   glass.moveTo(114,84);glass.lineTo(155,53);
   glass.cubicTo(186,43,237,52,268,64);glass.lineTo(302,82);glass.close();
   p.setShader(new LinearGradient(175,50,210,88,0xFF102336,0xFF4A6477,Shader.TileMode.CLAMP));canvas.drawPath(glass,p);p.setShader(null);
   p.setColor(0xFFE8EDF3);p.setStrokeWidth(3.8f);canvas.drawLine(213,52,215,84,p);canvas.drawLine(271,65,279,84,p);
   p.setStrokeWidth(1.5f);p.setColor(0xFF879FB1);
   canvas.drawLine(110,90,310,90,p);canvas.drawLine(215,89,218,114,p);
   // Chrome accent, head and tail lights.
   color(0xFFFFFFFF);canvas.drawRoundRect(22,100,52,107,3,3,p);
   color(0xFFF8E8C6);canvas.drawRoundRect(327,95,345,101,3,3,p);
   color(0xFF5F788A);canvas.drawRoundRect(32,117,338,124,3,3,p);
   // Door handles.
   color(0xFF718698);canvas.drawRoundRect(181,93,204,96,2,2,p);canvas.drawRoundRect(265,93,287,96,2,2,p);
   // Wheels with layered rims.
   wheel(canvas,91,122);
   wheel(canvas,282,122);
   canvas.restore();
 }
 private void wheel(Canvas c,float x,float y){
   color(0xFF172633);c.drawCircle(x,y,28,p);
   color(0xFF536575);c.drawCircle(x,y,21,p);
   color(0xFFE4EBF1);c.drawCircle(x,y,17,p);
   color(0xFF677C90);c.drawCircle(x,y,12,p);
   p.setColor(0xFFF9FBFF);p.setStrokeWidth(3);
   for(int i=0;i<6;i++){double a=i*Math.PI/3;
     c.drawLine(x+(float)Math.cos(a)*6,y+(float)Math.sin(a)*6,x+(float)Math.cos(a)*15,y+(float)Math.sin(a)*15,p);}
   color(0xFFB6C4CF);c.drawCircle(x,y,4,p);
 }
}
