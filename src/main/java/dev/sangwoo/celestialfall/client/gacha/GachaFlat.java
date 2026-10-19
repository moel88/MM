package dev.sangwoo.celestialfall.client.gacha;

import dev.sangwoo.celestialfall.client.title.GachaCinema;
import dev.sangwoo.celestialfall.gacha.GachaRules.Grade;
import net.minecraft.client.gui.DrawContext;
import java.util.List;

/**
 * The pull scenes drawn flat (2.39.0), where the title cinema's renderer cannot run (no desktop OpenGL 3.3, mobile
 * launchers): the same timing and colours, with hourglass marks in the dark (one large, or ten on a big cupboard's two
 * shelves running off the screen on every side, the last of them set into its place as the ten-pull opens) and the
 * lamp's light from above while it shines.
 */
final class GachaFlat {
    private GachaFlat(){}
    static float[] at(int i,int w,int h,boolean one){
        if(one)return new float[]{w/2f,h*.48f};
        float cell=Math.min(w*.17f,h*.27f);int row=i/5,col=i%5;
        return new float[]{w/2f+(col-2)*cell*1.08f,h*.27f+row*cell*1.38f};
    }
    static void draw(DrawContext d,GachaScreen screen,GachaCinema.Frame f,List<Grade> grades,float age,boolean one){
        int w=screen.width,h=screen.height;d.fill(0,0,w,h,0xff04050c);
        var m=d.getMatrices().peek().getPositionMatrix();
        float cell=one?Math.min(w,h)*.22f:Math.min(w*.17f,h*.27f)*.52f;
        float lamp=Math.min(1,(f.spotColor[0]+f.spotColor[1]+f.spotColor[2])/6f);
        if(!one){
            // the huge cupboard: past the screen's edges, its two gold-edged shelves under the hourglasses, dark pilasters
            // at its sides
            var b=GachaArt.begin(d,false);float[] a=at(0,w,h,false),c=at(9,w,h,false);float gap=c[1]-a[1];
            float x0=-w*.05f,x1=w*1.05f,y1=Math.min(h*1.02f,c[1]+gap*1.1f);
            GachaArt.fill(b,m,new float[]{x0,-h*.05f,x1,-h*.05f,x1,y1,x0,y1},0xff2a1a12,0xff1c120c);
            for(int r=0;r<2;r++){float y=a[1]+r*gap+cell*1.12f;GachaArt.fill(b,m,new float[]{x0,y,x1,y,x1,y+cell*.2f,x0,y+cell*.2f},0xff4b2f21);
                GachaArt.fill(b,m,new float[]{x0,y,x1,y,x1,y+cell*.035f,x0,y+cell*.035f},0xffb08a4c);}
            for(float x:new float[]{w*.03f,w*.97f})GachaArt.fill(b,m,new float[]{x-cell*.35f,-h*.05f,x+cell*.35f,-h*.05f,x+cell*.35f,y1,x-cell*.35f,y1},0xff1c120c);
            GachaArt.end(b);}
        var g=GachaArt.begin(d,true);
        if(lamp>0){
            // the lamp's light from far above, a soft cone to the floor
            float cx=w/2f,top=-h*.1f,bottom=h*1.02f,half=Math.min(w,h)*.22f;
            GachaArt.fill(g,m,new float[]{cx-half*.08f,top,cx+half*.08f,top,cx+half,bottom,cx-half,bottom},GachaArt.alpha(0xfff2dc,90*lamp),GachaArt.alpha(0xfff2dc,30*lamp));
        }
        for(int i=0;i<grades.size();i++){
            float[] p=at(i,w,h,one);float glow=f.glow[i];int c=rgb(f.accent[i]);
            // the ten-pull's last hourglass is set into its place as the scene opens (2.41.0)
            if(!one&&i==grades.size()-1&&age<GachaTen.PUSHED)p=new float[]{p[0],p[1]+cell*.9f*(1-GachaStage.smooth(0,GachaTen.PUSHED,age))};
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
