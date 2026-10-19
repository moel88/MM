package dev.sangwoo.celestialfall.client.gacha;

import dev.sangwoo.celestialfall.client.title.GachaCinema;
import dev.sangwoo.celestialfall.gacha.GachaRules.Grade;
import net.minecraft.client.gui.DrawContext;
import java.util.List;

/**
 * The pull scenes drawn flat (2.39.0), where the title cinema's renderer cannot run (no desktop OpenGL 3.3, mobile
 * launchers): the same timing and colours, with hourglass marks in the dark (one large, or ten on two shelves).
 */
final class GachaFlat {
    private GachaFlat(){}
    static float[] at(int i,int w,int h,boolean one){
        if(one)return new float[]{w/2f,h*.48f};
        float cell=Math.min(w*.13f,h*.2f);int row=i/5,col=i%5;
        return new float[]{w/2f+(col-2)*cell*1.1f,h*.36f+row*cell*1.35f};
    }
    static void draw(DrawContext d,GachaScreen screen,GachaCinema.Frame f,List<Grade> grades,float age,boolean one){
        int w=screen.width,h=screen.height;d.fill(0,0,w,h,0xff04050c);
        var m=d.getMatrices().peek().getPositionMatrix();
        float cell=one?Math.min(w,h)*.22f:Math.min(w*.13f,h*.2f)*.4f;
        if(!one){var b=GachaArt.begin(d,false);float x0=w/2f-cell*7.2f,x1=w/2f+cell*7.2f,y0=h*.36f-cell*1.8f,y1=h*.36f+cell*5.2f;
            GachaArt.fill(b,m,new float[]{x0,y0,x1,y0,x1,y1,x0,y1},0xff2a1a12,0xff1c120c);
            for(int r=0;r<2;r++){float y=h*.36f+r*cell*3.38f+cell*1.08f;GachaArt.fill(b,m,new float[]{x0,y,x1,y,x1,y+cell*.16f,x0,y+cell*.16f},0xff4b2f21);}
            GachaArt.end(b);}
        var g=GachaArt.begin(d,true);
        for(int i=0;i<grades.size();i++){
            float[] p=at(i,w,h,one);float glow=f.glow[i];int c=rgb(f.accent[i]);
            GachaScreen.hourglass(g,m,p[0],p[1],cell,GachaArt.alpha(GachaArt.mix(0xff8b94b8,c,Math.min(1,glow)),230),GachaArt.alpha(c,(int)(120+120*Math.min(1,glow))),.7f);
            float[] disc=new float[32];for(int k=0;k<16;k++){double a=k*Math.PI/8;disc[k*2]=p[0]+(float)Math.cos(a)*cell*.3f;disc[k*2+1]=p[1]+(float)Math.sin(a)*cell*.3f;}
            float a=Math.min(1.4f,glow*.6f);
            GachaArt.fill(g,m,disc,GachaArt.alpha(c,255*.6f*a));
            GachaArt.glow(g,m,disc,cell*(.8f+glow*.5f),GachaArt.alpha(c,255),a);
        }
        GachaArt.end(g);
    }
    static int rgb(float[] linear){
        int r=Math.round(255*(float)Math.pow(Math.min(1,linear[0]),1/2.2)),gg=Math.round(255*(float)Math.pow(Math.min(1,linear[1]),1/2.2)),b=Math.round(255*(float)Math.pow(Math.min(1,linear[2]),1/2.2));
        return 0xff000000|r<<16|gg<<8|b;
    }
}
