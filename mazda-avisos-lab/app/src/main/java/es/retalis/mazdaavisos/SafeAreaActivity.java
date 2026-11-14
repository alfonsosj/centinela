package es.retalis.mazdaavisos;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.DisplayCutout;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.FrameLayout;

/** Keeps every screen inside the system bars, cutout and on-screen keyboard. */
public abstract class SafeAreaActivity extends Activity {
 @Override protected void onCreate(Bundle state){
   super.onCreate(state);
   Window window=getWindow();
   window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
   window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
   window.setStatusBarColor(Color.TRANSPARENT);
   window.setNavigationBarColor(Color.TRANSPARENT);
   int lightBars=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
   if(Build.VERSION.SDK_INT>=30){
     window.getDecorView().setSystemUiVisibility(lightBars);
     window.setDecorFitsSystemWindows(false);
   }else{
     window.getDecorView().setSystemUiVisibility(lightBars|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|
       View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
   }
   if(Build.VERSION.SDK_INT>=29)window.setNavigationBarContrastEnforced(false);
 }

 @Override public void setContentView(View content){
   setContentView(content,new ViewGroup.LayoutParams(-1,-1));
 }
 @Override public void setContentView(int layoutId){
   View content=getLayoutInflater().inflate(layoutId,null,false);
   setContentView(content);
 }
 @Override public void setContentView(View content,ViewGroup.LayoutParams params){
   // A separate container preserves each screen's own spacing. Rebuilding a
   // dashboard replaces the container too, so system padding never accumulates.
   FrameLayout safe=new FrameLayout(this);
   safe.setBackgroundColor(0xFFF4F5FB);
   safe.setOnApplyWindowInsetsListener((view,insets)->{
     int left,top,right,bottom;
     if(Build.VERSION.SDK_INT>=30){
       Insets bars=insets.getInsetsIgnoringVisibility(
         WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
       Insets keyboard=insets.getInsets(WindowInsets.Type.ime());
       left=Math.max(bars.left,keyboard.left);top=bars.top;
       right=Math.max(bars.right,keyboard.right);bottom=Math.max(bars.bottom,keyboard.bottom);
       WindowInsetsController controller=getWindow().getInsetsController();
       if(controller!=null)controller.setSystemBarsAppearance(
         WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
         WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
     }else{
       left=insets.getSystemWindowInsetLeft();top=insets.getSystemWindowInsetTop();
       right=insets.getSystemWindowInsetRight();bottom=insets.getSystemWindowInsetBottom();
       if(Build.VERSION.SDK_INT>=28){
         DisplayCutout cutout=insets.getDisplayCutout();
         if(cutout!=null){
           left=Math.max(left,cutout.getSafeInsetLeft());top=Math.max(top,cutout.getSafeInsetTop());
           right=Math.max(right,cutout.getSafeInsetRight());bottom=Math.max(bottom,cutout.getSafeInsetBottom());
         }
       }
     }
     if(view.getPaddingLeft()!=left||view.getPaddingTop()!=top||
        view.getPaddingRight()!=right||view.getPaddingBottom()!=bottom)
       view.setPadding(left,top,right,bottom);
     // Only this outer container handles system insets. Children must not add
     // the same bar heights a second time via fitsSystemWindows.
     if(Build.VERSION.SDK_INT>=30)return WindowInsets.CONSUMED;
     WindowInsets consumed=insets.consumeSystemWindowInsets().consumeStableInsets();
     return Build.VERSION.SDK_INT>=28?consumed.consumeDisplayCutout():consumed;
   });
   safe.addView(content,new FrameLayout.LayoutParams(params));
   super.setContentView(safe,new ViewGroup.LayoutParams(-1,-1));
   safe.requestApplyInsets();
   safe.post(safe::requestApplyInsets);
 }
}
