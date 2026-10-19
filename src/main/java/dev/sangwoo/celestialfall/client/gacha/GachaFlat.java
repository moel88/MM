package dev.sangwoo.celestialfall.client.gacha;

import dev.sangwoo.celestialfall.client.title.GachaCinema;
import dev.sangwoo.celestialfall.gacha.GachaRules.Grade;
import net.minecraft.client.gui.DrawContext;
import java.util.List;

/**
 * The pull scenes drawn flat (2.39.0), where the title cinema's renderer cannot run (no desktop OpenGL 3.3, mobile
 * launchers): the same timing and colours, with hourglass marks in the dark (one large, or ten on a large cupboard's two
 * shelves) and the lamp's light from above while it shines.
 */
final class GachaFlat {
    private GachaFlat(){}
    static float[] at(int i,int w,int h,boolean one){
        if(one)return new float[]{w/2f,h*.48f};
        float cell=Math.min(w*.15f,h*.24f);int row=i/5,col=i%5;
        return new float[]{w/2f+(col-2)*cell*1.05f,h*.33f+row*cell*1.25f};
    }
    static void draw(DrawContext d,GachaScreen screen,GachaCinema.Frame f,List<Grade> grades,float age,boolean one){
        int w=screen.width,h=screen.height;d.fill(0,0,w,h,0xff04050c);
        var m=d.getMatrices().peek().getPositionMatrix();
        float cell=one?Math.min(w,h)*.22f:Math.min(w*.15f,h*.24f)*.4f;
        float lamp=Math.min(1,(f.spotColor[0]+f.spotColor[1]+f.spotColor[2])/6f);
        if(!one){var b=GachaArt.begin(d,false);float x0=w/2f-cell*6.6f,x1=w/2f+cell*6.6f,y0=h*.33f-cell*1.9f,y1=h*.33f+cell*5.3f;
            GachaArt.fill(b,m,new float[]{x0,y0,x1,y0,x1,y1,x0,y1},0xff2a1a12,0xff1c120c);
            for(int r=0;r<2;r++){float y=h*.33f+r*cell*3.12f+cell*1.08f;GachaArt.fill(b,m,new float[]{x0,y,x1,y,x1,y+cell*.16f,x0,y+cell*.16f},0xff4b2f21);}
            GachaArt.end(b);}
        var g=GachaArt.begin(d,true);
        if(lamp>0){
            // the lamp's light from far above, a soft cone to the floor
            float cx=w/2f,top=-h*.1f,bottom=h*1.02f,half=Math.min(w,h)*.22f;
            GachaArt.fill(g,m,new float[]{cx-half*.08f,top,cx+half*.08f,top,cx+half,bottom,cx-half,bottom},GachaArt.alpha(0xfff2dc,90*lamp),GachaArt.alpha(0xfff2dc,30*lamp));
        }
        for(int i=0;i<grades.size();i++){
            float[] p=at(i,w,h,one);float glow=f.glow[i];int c=rgb(f.accent[i]);
            GachaScreen.hourglass(g,m,p[0],p[1],cell,GachaArt.alpha(GachaArt.mix(0xff8b94b8,c,Math.min(1,glow)),230),GachaArt.alpha(c,(int)(120+120*Math.min(1,glow))),.7f);
            float a=Math.min(1.4f,glow*.6f);
            GachaArt.fill(g,m,GachaArt.circle(p[0],p[1],cell*.3f,16),GachaArt.alpha(c,255*.6f*a));
            GachaArt.glow(g,m,GachaArt.circle(p[0],p[1],cell*.3f,16),cell*(.8f+glow*.5f),GachaArt.alpha(c,255),a);
        }
        GachaArt.end(g);
    }
    static int rgb(float[] linear){
        int r=Math.round(255*(float)Math.pow(Math.min(1,linear[0]),1/2.2)),gg=Math.round(255*(float)Math.pow(Math.min(1,linear[1]),1/2.2)),b=Math.round(255*(float)Math.pow(Math.min(1,linear[2]),1/2.2));
        return 0xff000000|r<<16|gg<<8|b;
    }
}
