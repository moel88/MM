package dev.sangwoo.celestialfall.client.commerce;

import dev.sangwoo.celestialfall.client.menu.MenuLayout;
import dev.sangwoo.celestialfall.client.menu.UiCanvas;

/** Sky-blue take on the original game's broad header, white grid and overlapping round close button. */
final class ShopArt {
    static final MenuLayout.Rect PRODUCT=new MenuLayout.Rect(22,126,212,96);
    /** The seller's second product (2.49.0: the meteor pickaxe) and the crafter's one recipe in the first slot. */
    static final MenuLayout.Rect PRODUCT2=new MenuLayout.Rect(250,126,212,96),RECIPE=PRODUCT;
    static final MenuLayout.Rect CLOSE=new MenuLayout.Rect(684,0,46,46);

    static void frame(UiCanvas c,String title){
        rounded(c,0,28,720,456,12,0x30152e48,0x30152e48);
        rounded(c,0,22,720,456,12,0xff4ab8f2,0xffa6e7f5);
        rounded(c,9,114,702,354,7,0xffffffff,0xffffffff);
        c.center(title,360,50,43,0xffffffff,580);
    }
    static void product(UiCanvas c,MenuLayout.Rect r,String name,String label,boolean active,boolean hover,boolean pending){
        int left=active?0xff68b7ea:0xffb9cbd6,right=active?0xff9ee1f3:0xffd1dfe6;
        rounded(c,r.x(),r.y(),r.w(),r.h(),2,left,right);
        double border=hover&&active?2.5:1.6;
        c.rect(r.x()+border,r.y()+border,r.w()-2*border,r.h()-2*border,hover&&active?0xffeefaff:0xffffffff);
        int ink=active?0xff6bc5ef:0xffb9cedc,outline=active?0xff477b9f:0xff8ea8ba;
        outlined(c,name,r.x()+r.w()/2,r.y()+16,25,ink,outline,r.w()-18);
        outlined(c,label,r.x()+r.w()/2,r.y()+55,25,ink,outline,r.w()-18);
        if(pending)c.rect(r.x()+6,r.y()+r.h()-7,r.w()-12,2,0xff64bee8);
    }
    /** One recipe ingredient: its name over how many the player carries of how many it takes, sky blue once enough. */
    static void ingredient(UiCanvas c,double x,double y,String name,int have,int need){
        boolean enough=have>=need;
        rounded(c,x,y,180,84,4,enough?0xff68b7ea:0xffc9d6de,enough?0xff9ee1f3:0xffdce6ec);
        c.rect(x+1.6,y+1.6,176.8,80.8,enough?0xfff4fbff:0xfffafcfd);
        c.center(name,x+90,y+12,15,enough?0xff3f7ea6:0xff7d93a3,164);
        int ink=enough?0xff6bc5ef:0xffc4a0a8,outline=enough?0xff477b9f:0xff9a7680;
        outlined(c,Math.min(have,999)+" / "+need,x+90,y+40,25,ink,outline,164);
    }
    static void close(UiCanvas c,MenuLayout.Rect r,boolean hover){
        double x=r.x()+r.w()/2,y=r.y()+r.h()/2;
        c.circle(x,y+2,23,0x242c648d);c.circle(x,y,23,hover?0xffeaf8ff:0xffffffff);
        int color=hover?0xff2187c6:0xff74c3e8;
        c.line(x-10,y-10,x+10,y+10,3.7,color);c.line(x+10,y-10,x-10,y+10,3.7,color);
    }
    private static void outlined(UiCanvas c,String text,double x,double y,double size,int fill,int stroke,double max){
        c.center(text,x-.65,y,size,stroke,max);c.center(text,x+.65,y,size,stroke,max);
        c.center(text,x,y-.65,size,stroke,max);c.center(text,x,y+.65,size,stroke,max);
        c.center(text,x,y,size,fill,max);
    }
    /** Vertical strips follow circular corner arcs, with horizontal color interpolation. One batched draw. */
    static void rounded(UiCanvas c,double x,double y,double w,double h,double radius,int left,int right){
        int strips=(int)Math.ceil(w);double step=w/strips;
        for(int i=0;i<strips;i++){
            double a=i*step,b=(i+1)*step;
            double ca=cut(a,w,radius),cb=cut(b,w,radius);
            c.polygon(mix(left,right,(i+.5)/strips),x+a,y+ca,x+b,y+cb,x+b,y+h-cb,x+a,y+h-ca);
        }
    }
    private static double cut(double x,double width,double r){
        double d=Math.max(0,r-Math.min(x,width-x));return r-Math.sqrt(Math.max(0,r*r-d*d));
    }
    private static int mix(int a,int b,double t){
        int color=0;for(int shift:new int[]{0,8,16,24})color|=((int)Math.round(((a>>>shift)&255)*(1-t)+((b>>>shift)&255)*t))<<shift;return color;
    }
    private ShopArt(){}
}
