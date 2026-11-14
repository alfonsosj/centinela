package es.retalis.mazdaavisos;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Original vector line-icons for the quick status controls.
 * Inherits the circle color from the containing FrameLayout.
 */
public final class VehicleStateIconView extends View {
 public static final int LOCK=0, WINDOWS=1, TRUNK=2, CLIMATE=3;
 private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final int kind;
 private final int ink;
 public VehicleStateIconView(Context c,int kind,int color){
   super(c);this.kind=kind;this.ink=color;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
 }
 private void line(Canvas c,float x1,float y1,float x2,float y2){c.drawLine(x1,y1,x2,y2,p);}
 @Override protected void onDraw(Canvas canvas){
   super.onDraw(canvas);
   float scale=Math.min(getWidth(),getHeight())/48f;
   canvas.save();
   canvas.translate((getWidth()-48*scale)/2f,(getHeight()-48*scale)/2f);
   canvas.scale(scale,scale);
   p.setShader(null);p.setColor(ink);p.setStyle(Paint.Style.STROKE);
   p.setStrokeWidth(2.8f);p.setStrokeJoin(Paint.Join.ROUND);p.setStrokeCap(Paint.Cap.ROUND);
   switch(kind){
    case LOCK:
      canvas.drawRoundRect(new RectF(10,22,38,41),4,4,p);
      canvas.drawArc(new RectF(16,7,32,32),180,180,false,p);
      line(canvas,24,30,24,34);
      break;
    case WINDOWS:
      Path window=new Path();
      window.moveTo(10,11);window.lineTo(37,11);window.lineTo(40,37);
      window.lineTo(11,37);window.close();
      canvas.drawPath(window,p);
      line(canvas,17,17,23,30);
      line(canvas,26,17,32,30);
      break;
    case TRUNK:
      Path trunk=new Path();
      trunk.moveTo(7,31);trunk.lineTo(11,21);trunk.lineTo(35,21);trunk.lineTo(41,31);
      trunk.lineTo(41,38);trunk.lineTo(7,38);trunk.close();
      canvas.drawPath(trunk,p);
      canvas.drawArc(new RectF(10,27,18,35),0,360,false,p);
      canvas.drawArc(new RectF(30,27,38,35),0,360,false,p);
      line(canvas,12,17,20,10);
      line(canvas,20,10,37,10);
      line(canvas,37,10,40,17);
      break;
    case CLIMATE:
      canvas.drawCircle(24,24,3.2f,p);
      for(int i=0;i<4;i++){
        canvas.save();canvas.rotate(i*90,24,24);
        Path blade=new Path();
        blade.moveTo(24,19);
        blade.cubicTo(20,12,20,8,27,7);
        blade.cubicTo(34,7,34,14,28,19);
        canvas.drawPath(blade,p);
        canvas.restore();
      }
      break;
    default:break;
   }
   canvas.restore();
 }
}
