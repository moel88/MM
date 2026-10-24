package dev.sangwoo.celestialfall.client.menu;
import java.util.*;
/** Shared UI scene with original atlas sprites and code-native vectors; also consumed by the offline review renderer. */
public final class UiCanvas {
    public record Shape(double[] xy,int top,int bottom){}
    public record Label(String text,double x,double y,double size,int color,double maxWidth,boolean centered){}
    public record Sprite(double x,double y,double w,double h,int u,int v,int sw,int sh){}
    public final List<Sprite> sprites=new ArrayList<>();
    public final List<Shape> shapes=new ArrayList<>();
    public final List<Label> labels=new ArrayList<>();
    public void polygon(int color,double... xy){shapes.add(new Shape(xy,color,color));}
    public void rect(double x,double y,double w,double h,int c){polygon(c,x,y,x+w,y,x+w,y+h,x,y+h);}
    public void panel(double x,double y,double w,double h,double bevel,int top,int bottom){shapes.add(new Shape(new double[]{x+bevel,y,x+w-bevel,y,x+w,y+bevel,x+w,y+h-bevel,x+w-bevel,y+h,x+bevel,y+h,x,y+h-bevel,x,y+bevel},top,bottom));}
    public void framed(double x,double y,double w,double h,int top,int bottom){panel(x,y+3,w,h,10,0x44102d58,0x44102d58);panel(x,y,w,h,10,0xff0d4580,0xff0d4580);panel(x+2,y+2,w-4,h-4,9,0xffb5edff,0xff53bdfa);panel(x+5,y+5,w-10,h-10,7,top,bottom);}
    public void circle(double cx,double cy,double r,int c){ellipse(cx,cy,r,r,c);}
    public void ellipse(double cx,double cy,double rx,double ry,int c){double[] p=new double[40];for(int i=0;i<20;i++){double a=i*Math.PI/10;p[i*2]=cx+Math.cos(a)*rx;p[i*2+1]=cy+Math.sin(a)*ry;}polygon(c,p);}
    public void line(double x1,double y1,double x2,double y2,double width,int c){double len=Math.hypot(x2-x1,y2-y1);if(len==0)return;double dx=-(y2-y1)*width/len/2,dy=(x2-x1)*width/len/2;polygon(c,x1+dx,y1+dy,x2+dx,y2+dy,x2-dx,y2-dy,x1-dx,y1-dy);}
    public void text(String text,double x,double y,double size,int color,double max){labels.add(new Label(text,x,y,size,color,max,false));}
    public void center(String text,double x,double y,double size,int color,double max){labels.add(new Label(text,x,y,size,color,max,true));}
    public void star(double x,double y,double r,int c){polygon(c,x,y-r,x+r*.25,y-r*.25,x+r,y,x+r*.25,y+r*.25,x,y+r,x-r*.25,y+r*.25,x-r,y,x-r*.25,y-r*.25);}
    public void flower(double x,double y,double r){for(int i=0;i<5;i++){double a=i*Math.PI*2/5-Math.PI/2;circle(x+Math.cos(a)*r*.57,y+Math.sin(a)*r*.57,r*.5,0xff176abe);}for(int i=0;i<5;i++){double a=i*Math.PI*2/5-Math.PI/2;circle(x+Math.cos(a)*r*.57,y+Math.sin(a)*r*.57,r*.39,0xfff7fdff);}circle(x,y,r*.25,0xff66ccf9);circle(x,y,r*.09,0xffeaffff);}
    public void ribbon(double x,double y,double size){polygon(0xff287bc4,x,y,x-size,y-size*.65,x-size*.9,y+size*.5,x,y+size*.2);polygon(0xff75d6ff,x,y,x+size*.9,y-size*.6,x+size,y+size*.5,x,y+size*.2);polygon(0xffb7eeff,x-3,y,x-size*.7,y+size*1.65,x-size*.22,y+size*1.38,x+2,y+size*.3);flower(x,y,size*.43);}
    public void bunny(double x,double y,double r,int c){ellipse(x-r*.44,y-r*1.08,r*.21,r*.65,c);ellipse(x+r*.44,y-r*1.08,r*.21,r*.65,c);ellipse(x,y,r,r*.8,c);circle(x-r*.33,y-r*.05,r*.09,0xff52afe8);circle(x+r*.33,y-r*.05,r*.09,0xff52afe8);line(x-r*.10,y+r*.24,x,y+r*.32,r*.06,0xff52afe8);line(x,y+r*.32,x+r*.10,y+r*.24,r*.06,0xff52afe8);}
}
