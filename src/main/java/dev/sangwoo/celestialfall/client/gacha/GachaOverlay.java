package dev.sangwoo.celestialfall.client.gacha;

import dev.sangwoo.celestialfall.client.title.GachaCinema;
import dev.sangwoo.celestialfall.gacha.GachaRules.Grade;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import org.joml.Matrix4f;
import java.util.List;

/**
 * Light drawn over the pull scenes (2.39.0; 2.40.0 stronger, user request): the fade in from black between cinema bars,
 * motes of light drawn into the hourglass as it gathers, and where an hourglass shows its colour a burst of rays, a ring
 * rushing out, glints flung wide and (for the rarer grades) a long level streak and a flash; then the white into the
 * results. Rays and glints face the eye; they are light, not fire.
 */
final class GachaOverlay {
    private GachaOverlay(){}
    static void draw(DrawContext d,GachaScreen screen,GachaCinema cinema,GachaCinema.Frame f,List<Grade> grades,float age,boolean one){
        int w=screen.width,h=screen.height;float len=GachaStage.length(grades),S=GachaStage.SECOND,unit=Math.min(w,h);
        Matrix4f m=d.getMatrices().peek().getPositionMatrix();
        if(one){
            var g=grades.get(0);float at=GachaStage.reveal(g);
            float[] p=where(cinema,f,0,w,h,true);
            if(age<at)gather(d,m,p[0],p[1],unit,age,at);
            else burst(d,m,p[0],p[1],(age-at)/S,g,unit,age/S,true);
        }else for(int i=0;i<grades.size();i++){
            float at=GachaStage.lightAt(grades,i);var g=grades.get(i);
            float[] p=where(cinema,f,i,w,h,false);
            float wake=GachaStage.smooth(at-GachaStage.ramp(g),at,age);
            if(wake>0&&age<at+.5f*S)halo(d,m,p[0],p[1],unit*(.07f+.03f*g.ordinal()),wake*(age<at?1:1-(age-at)/(.5f*S)),GachaScreen.GLOW[g.ordinal()]);
            if(age>=at)burst(d,m,p[0],p[1],(age-at)/S,g,unit*(g==Grade.SSR?.9f:.42f),age/S+i,g==Grade.SSR);
        }
        // the flash of a reveal, then the white into the results
        float flash=0;int tint=0xffffff;
        if(one){var g=grades.get(0);float since=(age-GachaStage.reveal(g))/S;if(since>=0)flash=new float[]{.1f,.24f,.46f}[g.ordinal()]*(float)Math.exp(-since*6)*GachaStage.smooth(0,.04f,since);tint=GachaScreen.COLOR[g.ordinal()];}
        else for(int i=0;i<grades.size();i++){var g=grades.get(i);if(g==Grade.R)continue;float since=(age-GachaStage.lightAt(grades,i))/S;if(since>=0)flash=Math.max(flash,(g==Grade.SSR?.5f:.14f)*(float)Math.exp(-since*5.5f)*GachaStage.smooth(0,.04f,since));if(g==Grade.SSR)tint=GachaScreen.COLOR[2];}
        float out=GachaStage.smooth(len-GachaStage.FADE,len,age);
        d.draw();
        if(flash>0)d.fill(0,0,w,h,GachaArt.alpha(GachaArt.mix(tint,0xffffffff,.6f),255*flash));
        // cinema bars: in as the scene opens, out as it whitens
        float bars=GachaStage.smooth(0,.6f*S,age)*(1-out),bh=h*.075f*bars;
        if(bh>.5f){d.fill(0,0,w,Math.round(bh),0xff000000);d.fill(0,h-Math.round(bh),w,h,0xff000000);}
        if(out>0)d.fill(0,0,w,h,GachaArt.alpha(0xfff6f3ff,255*out));
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
    /** Motes of light drawn in from all round toward the hourglass as its light gathers before the reveal at {@code at}. */
    private static void gather(DrawContext d,Matrix4f m,float x,float y,float unit,float age,float at){
        if(Float.isNaN(x))return;
        float S=GachaStage.SECOND,k=GachaStage.smooth(at-GachaStage.GATHER,at,age);if(k<=0)return;
        BufferBuilder b=GachaArt.sprites(d,GachaArt.SPARK,true);
        for(int i=0;i<36;i++){
            float life=.9f+GachaArt.hash(i*3+1)*.7f,ph=((age/S+GachaArt.hash(i*3)*life)%life)/life;
            float ang=GachaArt.hash(i*3+2)*6.283f+ph*1.4f,r=unit*(.42f-.38f*ph*ph);
            float px=x+(float)Math.cos(ang)*r,py=y+(float)Math.sin(ang)*r*.8f,size=unit*(.006f+.006f*GachaArt.hash(i*7));
            float a=k*(float)Math.sin(Math.PI*ph);
            GachaArt.sprite(b,m,px,py,size*4,size*4,0,GachaArt.fade(0xffbcd8ff,a*.7f));
            GachaArt.sprite(b,m,px,py,size*1.4f,size*1.4f,0,GachaArt.fade(0xffffffff,a));
        }
        GachaArt.endSprites(b);
    }
    /** A soft glow where an hourglass is filling with light. */
    private static void halo(DrawContext d,Matrix4f m,float x,float y,float r,float k,int color){
        if(Float.isNaN(x)||k<=0)return;
        var b=GachaArt.sprites(d,GachaArt.SPARK,true);
        GachaArt.sprite(b,m,x,y,r*5,r*5,0,GachaArt.fade(color,k*.45f));GachaArt.sprite(b,m,x,y,r*1.6f,r*1.6f,0,GachaArt.fade(0xffffffff,k*.35f));
        GachaArt.endSprites(b);
    }
    /** Rays, a halo, a ring rushing out and glints bursting from {@code (x, y)} {@code t} seconds after a reveal. */
    static void burst(DrawContext d,Matrix4f m,float x,float y,float t,Grade g,float size,float spin,boolean grand){
        if(Float.isNaN(x))return;
        int gi=g.ordinal();int color=GachaScreen.GLOW[gi],light=GachaArt.mix(GachaScreen.COLOR[gi],0xffffffff,.45f);
        float grow=1-(float)Math.exp(-t*7),fade=(float)Math.exp(-t*(gi==2?.9f:gi==1?1.6f:2.4f));
        float reach=size*(gi==2?.75f:gi==1?.5f:.34f)*grow,a=fade*(gi==2?1f:gi==1?.8f:.6f);
        var b=GachaArt.begin(d,true);
        GachaResults.rays(b,m,x,y,reach,gi==2?20:gi==1?14:10,spin*.12f,GachaArt.fade(color,a*.75f),size*.022f*(gi+1));
        // the ring of the reveal rushing out, soft, for the rarer grades
        if(gi>0&&t<.6f){float k=t/.6f,r=size*(.06f+(.45f+.3f*gi)*GachaArt.ease(k)),wide=size*.05f*(1-k*.5f);
            GachaArt.band(b,m,GachaArt.circle(x,y,r,48),wide,GachaArt.fade(light,(1-k)*.45f),GachaArt.fade(color,0));
            GachaArt.band(b,m,GachaArt.circle(x,y,r,48),-wide,GachaArt.fade(light,(1-k)*.45f),GachaArt.fade(color,0));}
        // a long level streak through the light, for the rarer grades
        if(gi>0){float sk=(float)Math.exp(-t*(gi==2?1.2f:2.2f))*GachaStage.smooth(0,.05f,t),len=size*(gi==2?1.6f:.9f)*(.7f+.3f*grow),th=size*.012f*(gi+1);
            GachaArt.fillAcross(b,m,new float[]{x-len,y,x,y-th,x,y+th},GachaArt.fade(color,0),GachaArt.fade(light,sk*.8f));
            GachaArt.fillAcross(b,m,new float[]{x,y-th,x+len,y,x,y+th},GachaArt.fade(light,sk*.8f),GachaArt.fade(color,0));}
        GachaArt.end(b);
        // the core: a soft round light, white at its heart
        BufferBuilder c=GachaArt.sprites(d,GachaArt.SPARK,true);
        float core=size*(.16f+.1f*gi)*(.7f+.3f*grow);
        GachaArt.sprite(c,m,x,y,core*2.6f,core*2.6f,0,GachaArt.fade(color,a*.8f));GachaArt.sprite(c,m,x,y,core,core,0,GachaArt.fade(0xffffffff,a*.75f));
        GachaArt.endSprites(c);
        // glints flung outward
        int n=gi==2?(grand?26:18):gi==1?12:7;
        BufferBuilder s=GachaArt.sprites(d,GachaArt.GLINT,true);
        for(int i=0;i<n;i++){
            float ang=GachaArt.hash(i*5+gi*97)*6.283f,speed=.35f+GachaArt.hash(i*5+1+gi*97)*.65f,life=.6f+GachaArt.hash(i*5+2)*.7f;
            if(t>life)continue;float k=t/life,r=size*(.08f+.6f*speed*GachaArt.ease(k)),gs=size*(.05f+.05f*GachaArt.hash(i*5+3))*(1-k*.6f);
            GachaArt.sprite(s,m,x+(float)Math.cos(ang)*r,y+(float)Math.sin(ang)*r,gs,gs,k*1.5f,GachaArt.fade(light,(1-k)*.95f));
        }
        GachaArt.endSprites(s);
    }
}
