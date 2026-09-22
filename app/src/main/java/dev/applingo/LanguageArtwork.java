package dev.applingo;
import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import com.google.android.material.color.MaterialColors;
/** Decorative, resolution-independent artwork that follows the current Material palette. */
public final class LanguageArtwork extends View {
 private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
 public LanguageArtwork(Context c,AttributeSet a){super(c,a);}
 private int color(int attr){return MaterialColors.getColor(this,attr);}
 @Override protected void onDraw(Canvas c){
  super.onDraw(c);float scale=Math.min(getWidth()/320f,getHeight()/128f);c.save();c.translate(getWidth()/2f-160*scale,getHeight()/2f-64*scale);c.scale(scale,scale);
  p.setColor(color(com.google.android.material.R.attr.colorSecondaryContainer));c.drawCircle(228,29,18,p);
  c.save();c.rotate(-11,110,67);p.setColor(color(com.google.android.material.R.attr.colorPrimaryContainer));c.drawRoundRect(57,18,161,113,30,30,p);
  p.setColor(color(com.google.android.material.R.attr.colorOnPrimaryContainer));p.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));p.setTextSize(58);p.setTextAlign(Paint.Align.CENTER);c.drawText("A",109,88,p);c.restore();
  Path bloom=new Path();for(int i=0;i<=240;i++){double a=i*Math.PI/120;float r=46+(float)Math.cos(a*8)*5;float x=199+(float)Math.cos(a)*r,y=79+(float)Math.sin(a)*r;if(i==0)bloom.moveTo(x,y);else bloom.lineTo(x,y);}bloom.close();
  p.setColor(color(com.google.android.material.R.attr.colorTertiaryContainer));c.drawPath(bloom,p);p.setColor(color(com.google.android.material.R.attr.colorOnTertiaryContainer));p.setTextSize(42);c.drawText("文",199,94,p);
  p.setColor(color(androidx.appcompat.R.attr.colorPrimary));c.drawCircle(43,102,5,p);c.restore();
 }
}
