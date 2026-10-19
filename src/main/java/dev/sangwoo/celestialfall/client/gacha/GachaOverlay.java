package dev.sangwoo.celestialfall.client.gacha;

import dev.sangwoo.celestialfall.client.title.GachaCinema;
import dev.sangwoo.celestialfall.gacha.GachaRules.Grade;
import net.minecraft.client.gui.DrawContext;
import java.util.List;

/**
 * Light drawn over the pull scenes (2.39.0): the fade in from black, a burst of rays where an hourglass shows its colour
 * (bigger for the rarer grades), and the flash into the results. Rays and glints face the eye; they are light, not fire.
 */
final class GachaOverlay {
    private GachaOverlay(){}
    static void draw(DrawContext d,GachaScreen screen,GachaCinema cinema,GachaCinema.Frame f,List<Grade> grades,float age,boolean one){
        int w=screen.width,h=screen.height;float len=GachaStage.length(grades),S=GachaStage.SECOND;
        var m=d.getMatrices().peek().getPositionMatrix();
        var b=GachaArt.begin(d,true);
        if(one){
            var g=grades.get(0);float at=GachaStage.reveal(g);
            float[] p=where(cinema,f,0,w,h,one);
            if(age>=at)burst(b,m,p[0],p[1],(age-at)/S,g,Math.min(w,h),age/S);
        }else for(int i=0;i<grades.size();i++){
            float at=GachaStage.lightAt(grades,i);if(age<at)continue;
            float[] p=where(cinema,f,i,w,h,one);
            burst(b,m,p[0],p[1],(age-at)/S,grades.get(i),Math.min(w,h)*.45f,age/S+i);
        }
        GachaArt.end(b);
        // the flash of the reveal, then the fade into the results
        float flash=0;int tint=0xffffff;
        if(one){var g=grades.get(0);float since=(age-GachaStage.reveal(g))/S;if(since>=0)flash=new float[]{.08f,.18f,.34f}[g.ordinal()]*(float)Math.exp(-since*7)*GachaStage.smooth(0,.03f,since);tint=GachaScreen.COLOR[g.ordinal()];}
        else for(int i=0;i<grades.size();i++){var g=grades.get(i);if(g==Grade.R)continue;float since=(age-GachaStage.lightAt(grades,i))/S;if(since>=0)flash=Math.max(flash,(g==Grade.SSR?.4f:.12f)*(float)Math.exp(-since*6)*GachaStage.smooth(0,.03f,since));if(g==Grade.SSR)tint=GachaScreen.COLOR[2];}
        float out=GachaStage.smooth(len-GachaStage.FADE,len,age);
        if(flash>0)d.fill(0,0,w,h,GachaArt.alpha(GachaArt.mix(tint,0xffffffff,.62f),255*flash));
        if(out>0)d.fill(0,0,w,h,GachaArt.alpha(0xfff4f2ff,255*out));
        float in=1-GachaStage.smooth(0,.75f*S,age);
        if(in>0)d.fill(0,0,w,h,GachaArt.alpha(0xff000000,255*in));
    }
    /** Hourglass {@code i}'s centre on screen. */
    static float[] where(GachaCinema cinema,GachaCinema.Frame f,int i,int w,int h,boolean one){
        if(cinema==null)return GachaFlat.at(i,w,h,one);
        float[] c=GachaCinema.HOURGLASS_CENTER,m=one?null:f.place[i];
        float[] p=one?BomiRig.apply(f.bones,BomiRig.BODY,c[0],c[1],c[2]):new float[]{m[0]*c[0]+m[1]*c[1]+m[2]*c[2]+m[3],m[4]*c[0]+m[5]*c[1]+m[6]*c[2]+m[7],m[8]*c[0]+m[9]*c[1]+m[10]*c[2]+m[11]};
        return cinema.project(p[0],p[1],p[2],w,h);
    }
    /** Rays and a halo bursting from {@code (x, y)} {@code t} seconds after a reveal. */
    static void burst(net.minecraft.client.render.BufferBuilder b,org.joml.Matrix4f m,float x,float y,float t,Grade g,float size,float spin){
        if(Float.isNaN(x))return;
        int gi=g.ordinal();int color=GachaScreen.GLOW[gi];
        float grow=1-(float)Math.exp(-t*7),fade=(float)Math.exp(-t*(gi==2?1.1f:gi==1?1.8f:2.6f));
        float reach=size*(gi==2?.62f:gi==1?.42f:.28f)*grow,a=255*fade*(gi==2?.9f:gi==1?.7f:.5f);
        int rays=gi==2?16:gi==1?12:8;
        for(int k=0;k<rays;k++){
            double ang=k*Math.PI*2/rays+spin*.15f+(k%2)*.2f;float len=reach*(k%2==0?1:.55f),half=size*.012f*(gi+1);
            float cx=(float)Math.cos(ang),cy=(float)Math.sin(ang);
            GachaArt.vertex(b,m,x-cy*half,y+cx*half,GachaArt.alpha(color,a));GachaArt.vertex(b,m,x+cy*half,y-cx*half,GachaArt.alpha(color,a));
            GachaArt.vertex(b,m,x+cx*len,y+cy*len,GachaArt.alpha(color,0));
        }
        float[] disc=new float[48];float r=size*.05f*(1+gi*.6f)*(.6f+.4f*grow);
        for(int k=0;k<24;k++){double ang=k*Math.PI/12;disc[k*2]=x+(float)Math.cos(ang)*r;disc[k*2+1]=y+(float)Math.sin(ang)*r;}
        GachaArt.glow(b,m,disc,size*(.12f+gi*.08f),GachaArt.alpha(color,a),1);
        GachaArt.fill(b,m,disc,GachaArt.alpha(0xffffff,a*.55f));
    }
}
